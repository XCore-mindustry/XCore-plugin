package org.xcore.plugin.cloud;

import arc.util.CommandHandler;
import arc.util.Log;
import io.avaje.inject.PostConstruct;
import io.leangen.geantyref.TypeToken;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Getter;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.Player;
import mindustry.server.ServerControl;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.caption.CaptionVariable;
import org.incendo.cloud.exception.ArgumentParseException;
import org.incendo.cloud.exception.InvalidSyntaxException;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.exception.parsing.ParserException;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.help.HelpHandler;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ParserParameters;
import org.xcore.cloud.mindustry.ConflictStrategy;
import org.xcore.cloud.mindustry.MindustryCommandManager;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.plugin.cloud.annotation.AllTeams;
import org.xcore.plugin.cloud.annotation.DefaultUnit;
import org.xcore.plugin.cloud.annotation.RequiresMuteCheck;
import org.xcore.plugin.cloud.annotation.RequiresPlayTime;
import org.xcore.plugin.cloud.exception.XCoreCommandException;
import org.xcore.plugin.cloud.parser.MapParser;
import org.xcore.plugin.cloud.parser.PlayerParser;
import org.xcore.plugin.cloud.parser.SmartDurationParser;
import org.xcore.plugin.cloud.parser.LanguageParser;
import org.xcore.plugin.cloud.parser.TeamParser;
import org.xcore.plugin.command.controller.client.TranslatorLanguagesProvider;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.service.BundleService;
import org.xcore.plugin.service.PlayerSessionService;
import org.xcore.plugin.service.SecurityService;
import org.xcore.plugin.service.TimeService;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class CloudService {
    private final BundleService bundleService;
    private final SecurityService securityService;
    private final PlayerSessionService playerSessionService;
    private final GlobalConfig globalConfig;
    private final TimeService timeService;
    private final TranslatorLanguagesProvider translatorLanguagesProvider;

    private MindustryCommandManager<XCoreSender> clientManager;
    @Getter private MindustryCommandManager<XCoreSender> serverManager;
    private AnnotationParser<XCoreSender> clientAnnotationParser;
    private AnnotationParser<XCoreSender> serverAnnotationParser;

    @Inject
    public CloudService(BundleService bundleService,
                        SecurityService securityService,
                        PlayerSessionService playerSessionService,
                        GlobalConfig globalConfig, TimeService timeService,
                        TranslatorLanguagesProvider translatorLanguagesProvider) {
        this.bundleService = bundleService;
        this.securityService = securityService;
        this.playerSessionService = playerSessionService;
        this.globalConfig = globalConfig;
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

        configurePreprocessors(clientAnnotationParser);
        configurePreprocessors(serverAnnotationParser);
    }

    private MindustryCommandManager<XCoreSender> createManager(CommandHandler handler) {
        SenderMapper<MindustrySender, XCoreSender> mapper = SenderMapper.create(
                base -> new XCoreSender(base, bundleService),
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
            Locale locale = recipient.isPlayer()
                    ? bundleService.locale(recipient.player())
                    : bundleService.getDefaultLocale();

            String key = caption.key().replace(".", "-");
            return bundleService.format(locale, key, args());
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
        mgr.parserRegistry().registerParserSupplier(
                TypeToken.get(Duration.class),
                params -> new SmartDurationParser(timeService,
                        params.get(DefaultUnit.PARAM, TimeUnit.DAYS))
        );

        mgr.parserRegistry().registerNamedParser("language", LanguageParser.parser(translatorLanguagesProvider));

        mgr.parserRegistry().registerParser(PlayerParser.parser());
        mgr.parserRegistry().registerParser(MapParser.parser());

        configureExceptions(mgr);

        return mgr;
    }

    private void configureExceptions(MindustryCommandManager<XCoreSender> mgr) {
        mgr.exceptionController().registerHandler(XCoreCommandException.class, ctx -> {
            XCoreCommandException ex = ctx.exception();
            if (ex.isSilent()) return;

            XCoreSender sender = ctx.context().sender();
            if (sender.isPlayer()) {
                bundleService.send(sender.player(), ex.getKey(), ex.getArgs());
            } else {
                sender.sendMessage("Error: " + ex.getKey());
            }
        });

        mgr.exceptionController().registerHandler(InvalidSyntaxException.class, ctx -> {
            InvalidSyntaxException ex = ctx.exception();
            String correctSyntax = ex.correctSyntax();
            XCoreSender sender = ctx.context().sender();
            if (sender.isPlayer()) {
                bundleService.send(sender.player(), "error-invalid-syntax", args("syntax", correctSyntax));
            } else {
                sender.sendMessage("Invalid Syntax. Usage: " + correctSyntax);
            }
        });

        mgr.exceptionController().registerHandler(NoPermissionException.class, ctx -> {
            XCoreSender sender = ctx.context().sender();
            String key = "error-access-denied";

            if (sender.isPlayer()) {
                bundleService.send(sender.player(), key, args());
            } else {
                sender.sendMessage("Error: " + key);
            }
        });

        mgr.exceptionController().registerHandler(ArgumentParseException.class, ctx -> {
            Throwable cause = ctx.exception().getCause();
            XCoreSender sender = ctx.context().sender();

            // Если парсер выбросил наше исключение
            if (cause instanceof XCoreCommandException xcoreEx) {
                if (xcoreEx.isSilent()) return;
                if (sender.isPlayer()) {
                    bundleService.send(sender.player(), xcoreEx.getKey(), xcoreEx.getArgs());
                } else {
                    sender.sendMessage("Error: " + xcoreEx.getKey());
                }
                return;
            }

            if (cause instanceof ParserException parserEx) {
                String key = parserEx.errorCaption().key().replace(".", "-");

                Map<String, Object> arguments = new HashMap<>();
                for (CaptionVariable variable : parserEx.captionVariables()) {
                    arguments.put(variable.key(), variable.value());
                }

                if (sender.isPlayer()) {
                    bundleService.send(sender.player(), key, arguments);
                } else {
                    sender.sendMessage("Parse Error (" + key + "): " + parserEx.getMessage());
                }
                return;
            }

            String errorMsg = cause.getMessage();
            if (sender.isPlayer()) {
                bundleService.send(sender.player(), "error-argument-parse-generic", args("error", errorMsg));
            } else {
                sender.sendMessage("Parse Error: " + errorMsg);
            }
        });

        mgr.exceptionController().registerHandler(Exception.class, ctx -> {
            Throwable cause = ctx.exception();

            if (cause instanceof XCoreCommandException xcoreEx) {
                if (xcoreEx.isSilent()) return;
                XCoreSender sender = ctx.context().sender();
                if (sender.isPlayer()) {
                    bundleService.send(sender.player(), xcoreEx.getKey(), xcoreEx.getArgs());
                } else {
                    sender.sendMessage("[red]Error: " + xcoreEx.getKey());
                }
                return;
            }

            String messageKey = "error-internal";

            if (cause instanceof InvalidSyntaxException) {
                messageKey = "error-invalid-syntax";
            }

            XCoreSender sender = ctx.context().sender();
            if (sender.isPlayer()) {
                bundleService.send(sender.player(), messageKey, args());
            } else {
                sender.sendMessage("[red]System Error: " + cause.getMessage());
                cause.printStackTrace();
            }
        });
    }

    private void configurePreprocessors(AnnotationParser<XCoreSender> parser) {
        parser.registerPreprocessorMapper(RequiresMuteCheck.class, _ -> (ctx, _) -> {
            XCoreSender sender = ctx.sender();
            if (sender.isPlayer() && securityService.isMuted(sender.player())) {
                return ArgumentParseResult.failure(new IllegalStateException("Player is muted"));
            }
            return ArgumentParseResult.success(true);
        });

        parser.registerPreprocessorMapper(RequiresPlayTime.class, annotation -> (ctx, _) -> {
            XCoreSender sender = ctx.sender();
            if (!sender.isPlayer()) return ArgumentParseResult.success(true);

            Player player = sender.player();
            if (player.admin) return ArgumentParseResult.success(true);

            int requiredMinutes = switch (annotation.value()) {
                case GLOBAL_CHAT -> globalConfig.minPlayTimeForGlobalChat;
                case VOTE_KICK -> globalConfig.minPlayTimeForVotekick;
                case CUSTOM -> 0;
            };

            var data = playerSessionService.get(player.uuid());
            if (data != null && data.totalPlayTime < requiredMinutes) {
                return ArgumentParseResult.failure(
                        new XCoreCommandException("error-playtime-requirement", args("time", requiredMinutes))
                );
            }
            return ArgumentParseResult.success(true);
        });
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
