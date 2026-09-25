package org.xcore.cloud.mindustry;

import arc.util.CommandHandler;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.CloudCapability;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.caption.CaptionProvider;
import org.incendo.cloud.caption.CaptionVariable;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.xcore.cloud.mindustry.selector.annotation.AllowedSelectors;
import org.xcore.cloud.mindustry.selector.annotation.DenySelectors;
import org.xcore.cloud.mindustry.selector.caption.SelectorCaptionKeys;
import org.xcore.cloud.mindustry.selector.caption.SelectorCaptionProvider;
import org.xcore.cloud.mindustry.selector.engine.SelectorGuard;
import org.xcore.cloud.mindustry.selector.engine.SpatialSelectorEngine;
import org.xcore.cloud.mindustry.selector.exception.*;
import org.xcore.cloud.mindustry.selector.parser.PlayerSelectorAdapter;
import org.xcore.cloud.mindustry.selector.parser.TargetSelectorParsers;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

public class MindustryCommandManager<C> extends CommandManager<C> {

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

        ArcCommandRegistrationHandler<C> regHandler = new ArcCommandRegistrationHandler<>(this, handler);
        this.commandRegistrationHandler(regHandler);

        registerDefaultParsers();
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
    }

    private void registerDefaultParsers() {
        captionRegistry().registerProvider(new SelectorCaptionProvider<>());

        registerCommandPostProcessor(ctx -> {
            var spec = ctx.commandContext().getOrDefault(SelectorGuard.LAST_SELECTOR_SPEC_KEY, null);
            if (spec != null) {
                SelectorGuard.enforce(ctx.command(), spec);
            }
        });

        parserRegistry().registerParser(TargetSelectorParsers.singlePlayerSelector(selectorEngine));
        parserRegistry().registerParser(TargetSelectorParsers.multiplePlayerSelector(selectorEngine));
        parserRegistry().registerParser(TargetSelectorParsers.singleUnitSelector(selectorEngine));
        parserRegistry().registerParser(TargetSelectorParsers.multipleUnitSelector(selectorEngine));
        parserRegistry().registerParser(PlayerSelectorAdapter.playerParser(this, selectorEngine));
    }

    private void registerDefaultExceptionHandlers() {
        registerDefaultExceptionHandlers(
                triplet -> {
                    var ctx = triplet.first();
                    String message = ctx.formatCaption(triplet.second(), triplet.third());

                    MindustrySender original = senderMapper.reverse(ctx.sender());
                    original.sendMessage("[scarlet]Error: " + message);
                },
                pair -> arc.util.Log.err("Command Error: " + pair.first(), pair.second())
        );

        exceptionController().registerHandler(
                org.incendo.cloud.exception.ArgumentParseException.class,
                context -> {
                    Throwable cause = context.exception().getCause();
                    MindustrySender sender = senderMapper.reverse(context.context().sender());

                    if (cause instanceof SelectorSyntaxException ex) {
                        String msg = context.context().formatCaption(
                                SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_SYNTAX,
                                CaptionVariable.of("input", ex.input()),
                                CaptionVariable.of("reason", ex.reason())
                        );
                        sender.sendMessage(msg);
                    } else if (cause instanceof NoSuchTargetException ex) {
                        String msg = context.context().formatCaption(
                                SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_NO_SUCH_TARGET,
                                CaptionVariable.of("input", ex.selectorInput())
                        );
                        sender.sendMessage(msg);
                    } else if (cause instanceof TooManyTargetsException ex) {
                        String msg = context.context().formatCaption(
                                SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_TOO_MANY_TARGETS,
                                CaptionVariable.of("input", ex.selectorInput())
                        );
                        sender.sendMessage(msg);
                    } else if (cause instanceof SelectorDeniedException ex) {
                        String msg = context.context().formatCaption(
                                SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_DENIED,
                                CaptionVariable.of("reason", ex.getMessage())
                        );
                        sender.sendMessage(msg);
                    } else if (cause instanceof SelectorSenderRequirementException ex) {
                        String msg = context.context().formatCaption(
                                SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_SENDER_REQUIRED,
                                CaptionVariable.of("kind", ex.kind().token())
                        );
                        sender.sendMessage(msg);
                    } else if (cause instanceof SelectorLimitExceededException ex) {
                        String msg = context.context().formatCaption(
                                SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_LIMIT_EXCEEDED,
                                CaptionVariable.of("count", String.valueOf(ex.count())),
                                CaptionVariable.of("limit", String.valueOf(ex.limit()))
                        );
                        sender.sendMessage(msg);
                    } else {
                        String msg = cause != null && cause.getMessage() != null
                                ? cause.getMessage()
                                : context.exception().getMessage();
                        sender.sendMessage("[scarlet]Error: " + msg);
                    }
                }
        );

        exceptionController().registerHandler(
                org.incendo.cloud.services.PipelineException.class,
                context -> {
                    Throwable cause = context.exception().getCause();
                    MindustrySender sender = senderMapper.reverse(context.context().sender());

                    if (cause instanceof SelectorDeniedException ex) {
                        String msg = context.context().formatCaption(
                                SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_DENIED,
                                CaptionVariable.of("reason", ex.getMessage())
                        );
                        sender.sendMessage(msg);
                    } else if (cause instanceof SelectorSyntaxException ex) {
                        String msg = context.context().formatCaption(
                                SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_SYNTAX,
                                CaptionVariable.of("input", ex.input()),
                                CaptionVariable.of("reason", ex.reason())
                        );
                        sender.sendMessage(msg);
                    } else if (cause instanceof NoSuchTargetException ex) {
                        String msg = context.context().formatCaption(
                                SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_NO_SUCH_TARGET,
                                CaptionVariable.of("input", ex.selectorInput())
                        );
                        sender.sendMessage(msg);
                    } else if (cause instanceof TooManyTargetsException ex) {
                        String msg = context.context().formatCaption(
                                SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_TOO_MANY_TARGETS,
                                CaptionVariable.of("input", ex.selectorInput())
                        );
                        sender.sendMessage(msg);
                    } else if (cause != null && cause.getMessage() != null) {
                        sender.sendMessage("[scarlet]Error: " + cause.getMessage());
                    } else {
                        sender.sendMessage("[scarlet]Error: " + context.exception().getMessage());
                    }
                }
        );

        exceptionController().registerHandler(
                org.incendo.cloud.exception.CommandExecutionException.class,
                context -> {
                    Throwable cause = context.exception().getCause();
                    MindustrySender sender = senderMapper.reverse(context.context().sender());

                    if (cause instanceof SelectorDeniedException ex) {
                        String msg = context.context().formatCaption(
                                SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_DENIED,
                                CaptionVariable.of("reason", ex.getMessage())
                        );
                        sender.sendMessage(msg);
                    } else if (cause instanceof SelectorSyntaxException ex) {
                        String msg = context.context().formatCaption(
                                SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_SYNTAX,
                                CaptionVariable.of("input", ex.input()),
                                CaptionVariable.of("reason", ex.reason())
                        );
                        sender.sendMessage(msg);
                    } else if (cause instanceof NoSuchTargetException ex) {
                        String msg = context.context().formatCaption(
                                SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_NO_SUCH_TARGET,
                                CaptionVariable.of("input", ex.selectorInput())
                        );
                        sender.sendMessage(msg);
                    } else if (cause instanceof TooManyTargetsException ex) {
                        String msg = context.context().formatCaption(
                                SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_TOO_MANY_TARGETS,
                                CaptionVariable.of("input", ex.selectorInput())
                        );
                        sender.sendMessage(msg);
                    } else {
                        String msg = cause != null && cause.getMessage() != null
                                ? cause.getMessage()
                                : context.exception().getMessage();
                        sender.sendMessage("[scarlet]Error: " + msg);
                    }
                }
        );

        exceptionController().registerHandler(
                SelectorSyntaxException.class,
                context -> {
                    var ex = context.exception();
                    MindustrySender sender = senderMapper.reverse(context.context().sender());
                    String msg = context.context().formatCaption(
                            SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_SYNTAX,
                            CaptionVariable.of("input", ex.input()),
                            CaptionVariable.of("reason", ex.reason())
                    );
                    sender.sendMessage(msg);
                }
        );

        exceptionController().registerHandler(
                NoSuchTargetException.class,
                context -> {
                    var ex = context.exception();
                    MindustrySender sender = senderMapper.reverse(context.context().sender());
                    String msg = context.context().formatCaption(
                            SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_NO_SUCH_TARGET,
                            CaptionVariable.of("input", ex.selectorInput())
                    );
                    sender.sendMessage(msg);
                }
        );

        exceptionController().registerHandler(
                TooManyTargetsException.class,
                context -> {
                    var ex = context.exception();
                    MindustrySender sender = senderMapper.reverse(context.context().sender());
                    String msg = context.context().formatCaption(
                            SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_TOO_MANY_TARGETS,
                            CaptionVariable.of("input", ex.selectorInput())
                    );
                    sender.sendMessage(msg);
                }
        );

        exceptionController().registerHandler(
                SelectorDeniedException.class,
                context -> {
                    var ex = context.exception();
                    MindustrySender sender = senderMapper.reverse(context.context().sender());
                    String msg = context.context().formatCaption(
                            SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_DENIED,
                            CaptionVariable.of("reason", ex.getMessage())
                    );
                    sender.sendMessage(msg);
                }
        );

        exceptionController().registerHandler(
                SelectorSenderRequirementException.class,
                context -> {
                    var ex = context.exception();
                    MindustrySender sender = senderMapper.reverse(context.context().sender());
                    String msg = context.context().formatCaption(
                            SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_SENDER_REQUIRED,
                            CaptionVariable.of("kind", ex.kind().token())
                    );
                    sender.sendMessage(msg);
                }
        );

        exceptionController().registerHandler(
                SelectorLimitExceededException.class,
                context -> {
                    var ex = context.exception();
                    MindustrySender sender = senderMapper.reverse(context.context().sender());
                    String msg = context.context().formatCaption(
                            SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_LIMIT_EXCEEDED,
                            CaptionVariable.of("count", String.valueOf(ex.count())),
                            CaptionVariable.of("limit", String.valueOf(ex.limit()))
                    );
                    sender.sendMessage(msg);
                }
        );
    }
}
