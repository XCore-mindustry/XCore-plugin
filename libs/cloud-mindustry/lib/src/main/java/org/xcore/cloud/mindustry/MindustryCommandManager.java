package org.xcore.cloud.mindustry;

import arc.util.CommandHandler;
import arc.util.Log;
import io.leangen.geantyref.TypeToken;
import mindustry.game.Team;
import mindustry.gen.Player;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.CloudCapability;
import org.incendo.cloud.Command;
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
import org.incendo.cloud.key.CloudKey;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.parser.ParserParameter;
import org.incendo.cloud.parser.ParserParameters;
import org.incendo.cloud.services.PipelineException;
import org.xcore.cloud.mindustry.annotation.PlayerOnly;
import org.xcore.cloud.mindustry.exception.PlayerRequiredException;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;

public class MindustryCommandManager<C> extends CommandManager<C> {

    /** Parser parameter that makes a {@link Team} argument accept every team id, not only the base teams. */
    public static final ParserParameter<Boolean> ALL_TEAMS =
            new ParserParameter<>("mindustry:all_teams", TypeToken.get(Boolean.class));

    /**
     * Command meta that restricts a command to in-game players; set by {@link PlayerOnly} or with
     * {@code builder.meta(MindustryCommandManager.PLAYER_ONLY, true)}.
     */
    public static final CloudKey<Boolean> PLAYER_ONLY = CloudKey.of("mindustry:player_only", Boolean.class);

    /**
     * The {@link MindustrySender} behind the command sender, stored in every command context so
     * parsers can use it whatever the manager's sender type is.
     */
    public static final CloudKey<MindustrySender> MINDUSTRY_SENDER =
            CloudKey.of("mindustry:sender", MindustrySender.class);

    private final CommandHandler handler;
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
        this.handler = handler;
        this.senderMapper = senderMapper;

        registerCapability(CloudCapability.StandardCapabilities.ROOT_COMMAND_DELETION);

        SelectorResolutionBridge.setSimulationThread(Thread.currentThread());

        ArcCommandRegistrationHandler<C> regHandler = new ArcCommandRegistrationHandler<>(this, handler);
        this.commandRegistrationHandler(regHandler);

        registerCommandPreProcessor(ctx -> ctx.commandContext()
                .store(MINDUSTRY_SENDER, senderMapper.reverse(ctx.commandContext().sender())));

        registerDefaultCaptions();
        registerDefaultParsers();
        registerSelectorGuard();
        registerPlayerOnlyGuard();
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

    /**
     * @return the Arc handler this manager publishes its commands in
     */
    public CommandHandler commandHandler() {
        return handler;
    }

    /**
     * The names this manager's commands can currently be run by, keyed by the Cloud root name or
     * alias. The value differs from the key after a {@link ConflictStrategy#PREFIX} collision;
     * names that were skipped or have since been replaced by someone else are absent.
     */
    public Map<String, String> publishedNames() {
        Map<String, String> names = new HashMap<>();
        for (CommandHandler.Command command : handler.getCommandList()) {
            if (command instanceof MindustryCloudCommand<?> wrapper && wrapper.manager == this) {
                names.put(wrapper.inputName, wrapper.text);
            }
        }
        return names;
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
     * Wires every annotation of this library into {@code annotationParser}: the selector
     * annotations of {@link #registerSelectorAnnotations} and {@link PlayerOnly}.
     */
    public void registerMindustryAnnotations(AnnotationParser<C> annotationParser) {
        registerSelectorAnnotations(annotationParser);

        annotationParser.registerBuilderModifier(
                PlayerOnly.class,
                (annotation, builder) -> builder.meta(PLAYER_ONLY, true)
        );
    }

    /**
     * @return whether {@code command} is restricted to in-game players
     */
    public boolean isPlayerOnly(Command<C> command) {
        return command.commandMeta().getOrDefault(PLAYER_ONLY, false);
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
        parserRegistry().registerParser(MindustryParsers.playerInfo());
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
     * Runs once the command is known, so input that does not parse reports its parse error first.
     */
    private void registerPlayerOnlyGuard() {
        registerCommandPostProcessor(ctx -> {
            if (isPlayerOnly(ctx.command()) && !senderMapper.reverse(ctx.commandContext().sender()).isPlayer()) {
                throw new PlayerRequiredException();
            }
        });
    }

    /**
     * Cloud's default handlers (captioned messages, unexpected failures logged and never shown
     * verbatim), plus:
     * <ul>
     *     <li>{@link PipelineException} and {@link CommandExecutionException} are unwrapped, so a
     *     handler registered for the cause type also sees exceptions thrown from pre/postprocessors
     *     and command handlers;</li>
     *     <li>argument failures caused by a {@link ParserException} or a {@link SelectorException}
     *     show that exception's caption;</li>
     *     <li>{@link SelectorException}s and {@link PlayerRequiredException} show their caption.</li>
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
            for (Throwable cause = ctx.exception().getCause(); cause != null; cause = cause.getCause()) {
                if (cause instanceof ParserException parserException) {
                    sendCaption(ctx.context(), parserException.errorCaption(), parserException.captionVariables());
                    return;
                }
                if (cause instanceof SelectorException selectorException) {
                    sendCaption(ctx.context(), selectorException.caption(), selectorException.captionVariables());
                    return;
                }
                if (cause.getCause() == cause) {
                    break;
                }
            }
            // Fall through to Cloud's generic "invalid argument" handler.
            throw ctx.exception();
        });

        exceptionController().registerHandler(SelectorException.class, ctx ->
                sendCaption(ctx.context(), ctx.exception().caption(), ctx.exception().captionVariables())
        );

        exceptionController().registerHandler(PlayerRequiredException.class, ctx ->
                sendCaption(ctx.context(), ctx.exception().caption())
        );
    }

    private void sendCaption(CommandContext<C> context, Caption caption, CaptionVariable... variables) {
        sendErrorMessage(context.sender(), context.formatCaption(caption, variables));
    }
}
