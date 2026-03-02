package org.xcore.plugin.cloud;

import arc.util.CommandHandler;
import arc.util.Log;
import io.avaje.inject.PostConstruct;
import io.leangen.geantyref.TypeToken;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import lombok.Getter;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.Player;
import mindustry.server.ServerControl;
import org.incendo.cloud.Command;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.caption.CaptionVariable;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.exception.ArgumentParseException;
import org.incendo.cloud.exception.InvalidSyntaxException;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.exception.parsing.ParserException;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.help.HelpHandler;
import org.incendo.cloud.parser.ParserParameters;
import org.incendo.cloud.key.CloudKey;
import org.xcore.cloud.mindustry.ConflictStrategy;
import org.xcore.cloud.mindustry.MindustryCommandManager;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.plugin.cloud.annotation.*;
import org.xcore.plugin.cloud.exception.XCoreCommandException;
import org.xcore.plugin.cloud.parser.EnumValueParser;
import org.xcore.plugin.cloud.parser.MapParser;
import org.xcore.plugin.cloud.parser.PlayerParser;
import org.xcore.plugin.cloud.parser.SmartDurationParser;
import org.xcore.plugin.cloud.parser.LanguageParser;
import org.xcore.plugin.cloud.parser.TeamParser;
import org.xcore.plugin.command.transport.ToggleState;
import org.xcore.plugin.command.transport.TransportCutoverTarget;
import org.xcore.plugin.command.transport.TransportMode;
import org.xcore.plugin.command.transport.TransportStage;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.localization.TranslatorLanguagesProvider;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.localization.BundleService;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.service.SecurityService;
import org.xcore.plugin.service.TimeService;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class CloudService {
    private static final String ERROR_COMMAND_DISABLED = "error-command-disabled";
    private static final CloudKey<Boolean> REQUIRES_MUTE_CHECK_META = CloudKey.of("xcore.requiresMuteCheck", Boolean.class);
    private static final CloudKey<PlayTimeLimit> REQUIRES_PLAY_TIME_META = CloudKey.of("xcore.requiresPlayTime", PlayTimeLimit.class);

    private final BundleService bundleService;
    private final Provider<SecurityService> securityService;
    private final Provider<SessionService> sessionService;
    private final Config config;
    private final GlobalConfig globalConfig;
    private final TimeService timeService;
    private final TranslatorLanguagesProvider translatorLanguagesProvider;

    private MindustryCommandManager<XCoreSender> clientManager;
    @Getter private MindustryCommandManager<XCoreSender> serverManager;
    private AnnotationParser<XCoreSender> clientAnnotationParser;
    private AnnotationParser<XCoreSender> serverAnnotationParser;

    @Inject
    public CloudService(BundleService bundleService,
                        Provider<SecurityService> securityService,
                        Provider<SessionService> sessionService,
                        GlobalConfig globalConfig,
                        Config config,
                        TimeService timeService,
                        TranslatorLanguagesProvider translatorLanguagesProvider) {
        this.bundleService = bundleService;
        this.securityService = securityService;
        this.sessionService = sessionService;
        this.globalConfig = globalConfig;
        this.config = config;
        this.timeService = timeService;
        this.translatorLanguagesProvider = translatorLanguagesProvider;
    }

    @PostConstruct
    public void init() {
        Log.info("[XCore] Initializing Cloud Command Infrastructure...");

        this.clientManager = createManager(Vars.netServer.clientCommands);
        this.serverManager = createManager(ServerControl.instance.handler);

        this.clientAnnotationParser = new AnnotationParser<>(clientManager, XCoreSender.class);
        this.serverAnnotationParser = new AnnotationParser<>(serverManager, XCoreSender.class);

        configureAnnotationGuards(clientAnnotationParser);
        configureAnnotationGuards(serverAnnotationParser);
    }

    private MindustryCommandManager<XCoreSender> createManager(CommandHandler handler) {
        SenderMapper<MindustrySender, XCoreSender> mapper = SenderMapper.create(
                base -> new XCoreSender(base, bundleService, sessionService),
                XCoreSender::getHandle
        );

        MindustryCommandManager<XCoreSender> mgr = new MindustryCommandManager<>(
                handler,
                ExecutionCoordinator.simpleCoordinator(),
                mapper
        );

        mgr.setConflictStrategy(ConflictStrategy.OVERRIDE);

        mgr.setPermissionChecker((sender, permission) -> {
            if (permission.isEmpty()) return true;
            if (!sender.isPlayer()) return true;

            if (permission.equalsIgnoreCase("admin") || permission.startsWith("xcore.admin")) {
                return sender.player().admin;
            }
            return true;
        });

        mgr.captionRegistry().registerProvider((caption, recipient) -> {
            String key = caption.key().replace(".", "-");
            if (recipient.isPlayer()) {
                var session = sessionService.get().get(recipient.player());
                if (session != null) {
                    return session.locale().format(key, args());
                }
            }
            return bundleService.format(bundleService.getDefaultLocale(), key, args());
        });

        mgr.parserRegistry().registerAnnotationMapper(
                AllTeams.class,
                (_, _) -> ParserParameters.single(AllTeams.PARAM, true)
        );

        mgr.parserRegistry().registerParserSupplier(
                TypeToken.get(Team.class),
                params -> new TeamParser(
                        params.get(AllTeams.PARAM, false)
                )
        );

        mgr.parserRegistry().registerAnnotationMapper(
                DefaultUnit.class,
                (annotation, type) -> ParserParameters.single(DefaultUnit.PARAM, annotation.value())
        );

        mgr.parserRegistry().registerAnnotationMapper(
                AllowNegativeDuration.class,
                (_, _) -> ParserParameters.single(AllowNegativeDuration.PARAM, true)
        );

        mgr.parserRegistry().registerParserSupplier(
                TypeToken.get(Duration.class),
                params -> new SmartDurationParser(
                        timeService,
                        params.get(DefaultUnit.PARAM, TimeUnit.DAYS),
                        params.get(AllowNegativeDuration.PARAM, false)
                )
        );


        mgr.parserRegistry().registerNamedParser("language", LanguageParser.parser(translatorLanguagesProvider));

        mgr.parserRegistry().registerParser(PlayerParser.parser());
        mgr.parserRegistry().registerParser(MapParser.parser());
        mgr.parserRegistry().registerParser(EnumValueParser.parser(TransportMode.class));
        mgr.parserRegistry().registerParser(EnumValueParser.parser(TransportCutoverTarget.class));
        mgr.parserRegistry().registerParser(EnumValueParser.parser(TransportStage.class));
        mgr.parserRegistry().registerParser(EnumValueParser.parser(ToggleState.class));

        mgr.registerCommandPreProcessor(context -> {
            String disabledCommand = disabledCommandKey(context.commandInput().remainingInput());
            if (disabledCommand != null) {
                throwDisabledCommandException(disabledCommand);
            }
        });

        mgr.registerCommandPostProcessor(context -> {
            String disabledCommand = disabledCommandKeyFromCommand(context.command());
            if (disabledCommand == null) {
                return;
            }
            throwDisabledCommandException(disabledCommand);
        });

        mgr.registerCommandPostProcessor(context -> {
            var commandMeta = context.command().commandMeta();
            XCoreSender sender = context.commandContext().sender();

            if (commandMeta.getOrDefault(REQUIRES_MUTE_CHECK_META, false)
                    && sender.isPlayer()
                    && securityService.get().isMuted(sender.player())) {
                throw new XCoreCommandException(true);
            }

            var playTimeLimit = commandMeta.optional(REQUIRES_PLAY_TIME_META).orElse(null);
            if (playTimeLimit == null || !sender.isPlayer()) {
                return;
            }

            Player player = sender.player();
            if (player.admin) {
                return;
            }

            int requiredMinutes = switch (playTimeLimit) {
                case GLOBAL_CHAT -> globalConfig.minPlayTimeForGlobalChat;
                case VOTE_KICK -> globalConfig.minPlayTimeForVotekick;
                case CUSTOM -> 0;
            };

            var session = sessionService.get().get(player.uuid());
            var data = session != null ? session.data : null;
            if (data != null && data.totalPlayTime < requiredMinutes) {
                throw new XCoreCommandException("error-playtime-requirement", args("time", requiredMinutes));
            }
        });

        configureExceptions(mgr);

        return mgr;
    }

    public boolean isCommandDisabled(Command<XCoreSender> command) {
        return disabledCommandKeyFromCommand(command) != null;
    }

    public boolean isCommandDisabled(String commandName) {
        String normalized = normalizeCommandName(commandName);
        return normalized != null && isExplicitlyDisabled(normalized);
    }

    private String disabledCommandKeyFromCommand(Command<XCoreSender> command) {
        if (!hasDisabledCommands()) {
            return null;
        }

        String rootName = command.rootComponent().name();
        if (isCommandDisabled(rootName)) {
            return rootName;
        }

        for (String alias : command.rootComponent().aliases()) {
            if (isCommandDisabled(alias)) {
                return rootName;
            }
        }

        String literalSyntax = command.components().stream()
                .filter(component -> component.type() == CommandComponent.ComponentType.LITERAL)
                .map(CommandComponent::name)
                .collect(Collectors.joining(" "));

        if (!literalSyntax.equalsIgnoreCase(rootName) && isCommandDisabled(literalSyntax)) {
            return literalSyntax;
        }
        return null;
    }

    private String disabledCommandKey(String input) {
        if (!hasDisabledCommands()) {
            return null;
        }

        String normalizedInput = normalizeCommandName(input);
        if (normalizedInput == null) {
            return null;
        }

        for (String disabledCommand : config.disabledCommands) {
            String normalizedDisabled = normalizeCommandName(disabledCommand);
            if (normalizedDisabled == null) {
                continue;
            }

            if (isFullOrPrefixMatch(normalizedInput, normalizedDisabled)) {
                return normalizedDisabled;
            }
        }
        return null;
    }

    private boolean hasDisabledCommands() {
        return config.disabledCommands != null && !config.disabledCommands.isEmpty();
    }

    private boolean isExplicitlyDisabled(String normalizedCommandName) {
        if (!hasDisabledCommands()) {
            return false;
        }

        for (String disabledCommand : config.disabledCommands) {
            String normalizedDisabled = normalizeCommandName(disabledCommand);
            if (normalizedCommandName.equals(normalizedDisabled)) {
                return true;
            }
        }
        return false;
    }

    private boolean isFullOrPrefixMatch(String normalizedInput, String normalizedDisabledCommand) {
        return normalizedInput.equals(normalizedDisabledCommand)
                || normalizedInput.startsWith(normalizedDisabledCommand + " ");
    }

    private String normalizeCommandName(String commandName) {
        if (commandName == null) {
            return null;
        }
        String normalized = commandName.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        normalized = normalized.replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }

    private void configureExceptions(MindustryCommandManager<XCoreSender> mgr) {
        mgr.exceptionController().registerHandler(XCoreCommandException.class, ctx -> {
            XCoreCommandException ex = ctx.exception();
            if (ex.isSilent()) return;

            XCoreSender sender = ctx.context().sender();
            sendXCoreException(sender, ex);
        });

        mgr.exceptionController().registerHandler(InvalidSyntaxException.class, ctx -> {
            InvalidSyntaxException ex = ctx.exception();
            String correctSyntax = ex.correctSyntax();
            XCoreSender sender = ctx.context().sender();
            if (sender.isPlayer()) {
                sendToPlayer(sender.player(), "error-invalid-syntax", args("syntax", correctSyntax));
            } else {
                sender.sendMessage("Invalid Syntax. Usage: " + correctSyntax);
            }
        });

        mgr.exceptionController().registerHandler(NoPermissionException.class, ctx -> {
            XCoreSender sender = ctx.context().sender();
            String key = "error-access-denied";

            if (sender.isPlayer()) {
                sendToPlayer(sender.player(), key, args());
            } else {
                sender.sendMessage("Error: " + key);
            }
        });

        mgr.exceptionController().registerHandler(ArgumentParseException.class, ctx -> {
            Throwable exception = ctx.exception();
            XCoreSender sender = ctx.context().sender();

            XCoreCommandException xcoreEx = findCause(exception, XCoreCommandException.class);
            if (xcoreEx != null) {
                if (xcoreEx.isSilent()) return;
                sendXCoreException(sender, xcoreEx);
                return;
            }

            ParserException parserEx = findCause(exception, ParserException.class);
            if (parserEx != null) {
                String key = parserEx.errorCaption().key().replace(".", "-");

                Map<String, Object> arguments = new HashMap<>();
                for (CaptionVariable variable : parserEx.captionVariables()) {
                    arguments.put(variable.key(), variable.value());
                }

                if (sender.isPlayer()) {
                    sendToPlayer(sender.player(), key, arguments);
                } else {
                    sender.sendMessage("Parse Error (" + key + "): " + parserEx.getMessage());
                }
                return;
            }

            Throwable cause = rootCause(exception);
            String errorMsg = cause.getMessage();
            if (sender.isPlayer()) {
                sendToPlayer(sender.player(), "error-argument-parse-generic", args("error", errorMsg));
            } else {
                sender.sendMessage("Parse Error: " + errorMsg);
            }
        });

        mgr.exceptionController().registerHandler(Exception.class, ctx -> {
            Throwable exception = ctx.exception();

            XCoreCommandException xcoreEx = findCause(exception, XCoreCommandException.class);
            if (xcoreEx != null) {
                if (xcoreEx.isSilent()) return;
                XCoreSender sender = ctx.context().sender();
                sendXCoreException(sender, xcoreEx);
                return;
            }

            Throwable cause = rootCause(exception);
            String messageKey = "error-internal";

            if (findCause(exception, InvalidSyntaxException.class) != null) {
                messageKey = "error-invalid-syntax";
            }

            XCoreSender sender = ctx.context().sender();
            if (sender.isPlayer()) {
                sendToPlayer(sender.player(), messageKey, args());
            } else {
                sender.sendMessage("[red]System Error: " + cause.getMessage());
                cause.printStackTrace();
            }
        });
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }
        return null;
    }

    private void throwDisabledCommandException(String commandName) {
        throw new XCoreCommandException(
                ERROR_COMMAND_DISABLED,
                args("command", commandName)
        );
    }

    private void sendXCoreException(XCoreSender sender, XCoreCommandException ex) {
        if (sender.isPlayer()) {
            sendToPlayer(sender.player(), ex.getKey(), ex.getArgs());
            return;
        }
        sender.sendMessage(bundleService.format(bundleService.getDefaultLocale(), ex.getKey(), ex.getArgs()));
    }

    private void sendToPlayer(Player player, String key, Map<String, Object> args) {
        var session = sessionService.get().get(player);
        if (session != null) {
            session.locale().send(key, args);
            return;
        }
        player.sendMessage(bundleService.format(bundleService.getDefaultLocale(), key, args));
    }

    private void configureAnnotationGuards(AnnotationParser<XCoreSender> parser) {
        parser.registerBuilderModifier(RequiresMuteCheck.class,
                (_, builder) -> builder.meta(REQUIRES_MUTE_CHECK_META, true));

        parser.registerBuilderModifier(RequiresPlayTime.class,
                (annotation, builder) -> builder.meta(REQUIRES_PLAY_TIME_META, annotation.value()));
    }

    public HelpHandler<XCoreSender> getHelpHandler() {
        if (clientManager == null) {
            throw new IllegalStateException("CloudService not initialized");
        }
        return clientManager.createHelpHandler();
    }

    public HelpHandler<XCoreSender> getServerHelpHandler() {
        if (serverManager == null) {
            throw new IllegalStateException("CloudService not initialized");
        }
        return serverManager.createHelpHandler();
    }

    public void register(Object controller) {
        clientAnnotationParser.parse(controller);
        serverAnnotationParser.parse(controller);
    }

    public void registerClient(Object controller) {
        clientAnnotationParser.parse(controller);
    }

    public void registerServer(Object controller) {
        serverAnnotationParser.parse(controller);
    }
}
