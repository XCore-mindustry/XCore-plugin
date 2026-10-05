package org.xcore.cloud.mindustry;

import arc.util.CommandHandler;
import arc.util.Log;
import io.leangen.geantyref.TypeToken;
import mindustry.game.Team;
import mindustry.gen.Player;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.CloudCapability;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.caption.Caption;
import org.incendo.cloud.caption.CaptionProvider;
import org.incendo.cloud.caption.CaptionVariable;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.exception.ArgumentParseException;
import org.incendo.cloud.exception.CommandExecutionException;
import org.incendo.cloud.exception.handling.ExceptionHandler;
import org.incendo.cloud.exception.parsing.ParserException;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.parser.ParserParameter;
import org.incendo.cloud.parser.ParserParameters;
import org.incendo.cloud.services.PipelineException;
import org.xcore.cloud.mindustry.parser.MindustryCaptionProvider;
import org.xcore.cloud.mindustry.parser.MindustryParsers;
import org.xcore.cloud.mindustry.parser.TeamParser;
import org.xcore.cloud.mindustry.selector.SelectorRestrictions;
import org.xcore.cloud.mindustry.selector.TargetSelector.MultiplePlayerSelector;
import org.xcore.cloud.mindustry.selector.TargetSelector.MultipleUnitSelector;
import org.xcore.cloud.mindustry.selector.TargetSelector.SinglePlayerSelector;
import org.xcore.cloud.mindustry.selector.TargetSelector.SingleUnitSelector;
import org.xcore.cloud.mindustry.selector.annotation.AllowedSelectors;
import org.xcore.cloud.mindustry.selector.annotation.DenySelectors;
import org.xcore.cloud.mindustry.selector.caption.SelectorCaptionProvider;
import org.xcore.cloud.mindustry.selector.engine.SelectorGuard;
import org.xcore.cloud.mindustry.selector.engine.SelectorResolutionBridge;
import org.xcore.cloud.mindustry.selector.engine.SpatialSelectorEngine;
import org.xcore.cloud.mindustry.selector.exception.SelectorException;
import org.xcore.cloud.mindustry.selector.parser.PlayerSelectorAdapter;
import org.xcore.cloud.mindustry.selector.parser.TargetSelectorParsers;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;

public class MindustryCommandManager<C> extends CommandManager<C> {

    /** Parser parameter that makes a {@link Team} argument accept every team id, not only the base teams. */
    public static final ParserParameter<Boolean> ALL_TEAMS =
            new ParserParameter<>("mindustry:all_teams", TypeToken.get(Boolean.class));

    private final SenderMapper<MindustrySender, C> senderMapper;
    private final SpatialSelectorEngine selectorEngine = new SpatialSelectorEngine();
    private ConflictStrategy conflictStrategy = ConflictStrategy.SKIP;
    private BiPredicate<C, String> permissionChecker = (sender, perm) -> true;

    private Supplier<String> prefixProvider = () -> "cloud";

    public MindustryCommandManager(
            CommandHandler handler,
            ExecutionCoordinator<C> coordinator,
            SenderMapper<MindustrySender, C> senderMapper
    ) {
        super(coordinator, CommandRegistrationHandler.nullCommandRegistrationHandler());
        this.senderMapper = senderMapper;

        registerCapability(CloudCapability.StandardCapabilities.ROOT_COMMAND_DELETION);

        SelectorResolutionBridge.setSimulationThread(Thread.currentThread());

        ArcCommandRegistrationHandler<C> regHandler = new ArcCommandRegistrationHandler<>(this, handler);
        this.commandRegistrationHandler(regHandler);

        registerDefaultCaptions();
        registerDefaultParsers();
        registerSelectorGuard();
        registerDefaultExceptionHandlers();
    }

    public static MindustryCommandManager<MindustrySender> create(CommandHandler handler) {
        return new MindustryCommandManager<>(
                handler,
                ExecutionCoordinator.simpleCoordinator(),
                SenderMapper.identity()
        );
    }

    @Override
    public boolean hasPermission(@NonNull C sender, @NonNull String permission) {
        if (permission.isEmpty()) return true;
        return permissionChecker.test(sender, permission);
    }

    public SenderMapper<MindustrySender, C> senderMapper() {
        return this.senderMapper;
    }

    public void setConflictStrategy(ConflictStrategy conflictStrategy) {
        this.conflictStrategy = Objects.requireNonNull(conflictStrategy);
    }

    public ConflictStrategy getConflictStrategy() {
        return conflictStrategy;
    }

    public void setPermissionChecker(BiPredicate<C, String> permissionChecker) {
        this.permissionChecker = Objects.requireNonNull(permissionChecker);
    }

    public BiPredicate<C, String> getPermissionChecker() {
        return permissionChecker;
    }

    public void setCommandPrefix(String prefix) {
        this.prefixProvider = () -> Objects.requireNonNull(prefix);
    }

    public void setCommandPrefixProvider(Supplier<String> prefixProvider) {
        this.prefixProvider = Objects.requireNonNull(prefixProvider);
    }

    public String getCommandPrefix() {
        return prefixProvider.get();
    }

    public SpatialSelectorEngine selectorEngine() {
        return selectorEngine;
    }

    public void addCaptionProvider(CaptionProvider<C> provider) {
        this.captionRegistry().registerProvider(provider);
    }

    /**
     * Wires {@link DenySelectors} and {@link AllowedSelectors} into {@code annotationParser}.
     * On a method they restrict every selector argument of the command; on a parameter they
     * restrict that argument only.
     */
    public void registerSelectorAnnotations(AnnotationParser<C> annotationParser) {
        annotationParser.registerBuilderModifier(
                DenySelectors.class,
                (annotation, builder) -> builder
                        .meta(SelectorGuard.DENY_SELECTORS_KEY, true)
                        .meta(SelectorGuard.DENY_REASON_KEY, annotation.reason())
        );

        annotationParser.registerBuilderModifier(
                AllowedSelectors.class,
                (annotation, builder) -> builder
                        .meta(SelectorGuard.ALLOWED_SELECTORS_KEY, annotation.value())
        );

        parserRegistry().registerAnnotationMapper(
                DenySelectors.class,
                (annotation, type) -> ParserParameters.single(SelectorRestrictions.DENY_PARAMETER, annotation.reason())
        );

        parserRegistry().registerAnnotationMapper(
                AllowedSelectors.class,
                (annotation, type) -> ParserParameters.single(SelectorRestrictions.ALLOWED_PARAMETER, annotation.value())
        );
    }

    /**
     * Sends an error message produced by one of the default exception handlers. The message is
     * the formatted caption; Mindustry color tags are allowed.
     */
    protected void sendErrorMessage(@NonNull C sender, @NonNull String message) {
        senderMapper.reverse(sender).sendMessage("[scarlet]" + message);
    }

    private void registerDefaultCaptions() {
        captionRegistry().registerProvider(new SelectorCaptionProvider<>());
        captionRegistry().registerProvider(new MindustryCaptionProvider<>());
    }

    private void registerDefaultParsers() {
        registerSelectorParser(SinglePlayerSelector.class,
                restrictions -> TargetSelectorParsers.singlePlayerSelector(selectorEngine, restrictions));
        registerSelectorParser(MultiplePlayerSelector.class,
                restrictions -> TargetSelectorParsers.multiplePlayerSelector(selectorEngine, restrictions));
        registerSelectorParser(SingleUnitSelector.class,
                restrictions -> TargetSelectorParsers.singleUnitSelector(selectorEngine, restrictions));
        registerSelectorParser(MultipleUnitSelector.class,
                restrictions -> TargetSelectorParsers.multipleUnitSelector(selectorEngine, restrictions));
        registerSelectorParser(Player.class,
                restrictions -> ParserDescriptor.of(new PlayerSelectorAdapter<>(this, selectorEngine, restrictions), Player.class));

        parserRegistry().registerParserSupplier(
                TypeToken.get(Team.class),
                params -> new TeamParser<>(params.get(ALL_TEAMS, false))
        );
        parserRegistry().registerParser(MindustryParsers.unitType());
        parserRegistry().registerParser(MindustryParsers.block());
        parserRegistry().registerParser(MindustryParsers.item());
        parserRegistry().registerParser(MindustryParsers.liquid());
        parserRegistry().registerParser(MindustryParsers.statusEffect());
    }

    private <T> void registerSelectorParser(
            Class<T> type,
            Function<SelectorRestrictions, ParserDescriptor<C, T>> factory
    ) {
        parserRegistry().registerParserSupplier(
                TypeToken.get(type),
                params -> factory.apply(SelectorRestrictions.from(params)).parser()
        );
    }

    private void registerSelectorGuard() {
        registerCommandPostProcessor(ctx -> SelectorGuard.enforceAll(ctx.command(), ctx.commandContext()));
    }

    /**
     * Cloud's default handlers (captioned messages, unexpected failures logged and never shown
     * verbatim), plus:
     * <ul>
     *     <li>{@link PipelineException} and {@link CommandExecutionException} are unwrapped, so a
     *     handler registered for the cause type also sees exceptions thrown from pre/postprocessors
     *     and command handlers;</li>
     *     <li>argument failures caused by a {@link ParserException} show the parser's own caption;</li>
     *     <li>{@link SelectorException}s show their caption.</li>
     * </ul>
     * Handlers registered later for the same type take precedence over these.
     */
    private void registerDefaultExceptionHandlers() {
        registerDefaultExceptionHandlers(
                triplet -> {
                    CommandContext<C> ctx = triplet.first();
                    String message = ctx.formatCaption(triplet.second(), triplet.third());
                    sendErrorMessage(ctx.sender(), message);
                },
                pair -> Log.err("[cloud] " + pair.first(), pair.second())
        );

        exceptionController().registerHandler(
                PipelineException.class,
                ExceptionHandler.unwrappingHandler(cause -> true)
        );

        exceptionController().registerHandler(
                CommandExecutionException.class,
                ExceptionHandler.unwrappingHandler(cause -> true)
        );

        exceptionController().registerHandler(ArgumentParseException.class, ctx -> {
            if (!(ctx.exception().getCause() instanceof ParserException parserException)) {
                // Fall through to Cloud's generic "invalid argument" handler.
                throw ctx.exception();
            }
            sendCaption(ctx.context(), parserException.errorCaption(), parserException.captionVariables());
        });

        exceptionController().registerHandler(SelectorException.class, ctx ->
                sendCaption(ctx.context(), ctx.exception().caption(), ctx.exception().captionVariables())
        );
    }

    private void sendCaption(CommandContext<C> context, Caption caption, CaptionVariable... variables) {
        sendErrorMessage(context.sender(), context.formatCaption(caption, variables));
    }
}
