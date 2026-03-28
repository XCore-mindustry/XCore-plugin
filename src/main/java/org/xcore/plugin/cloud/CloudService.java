package org.xcore.plugin.cloud;

import arc.util.Log;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Getter;
import mindustry.Vars;
import mindustry.server.ServerControl;
import org.incendo.cloud.Command;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.help.HelpHandler;
import org.incendo.cloud.key.CloudKey;
import org.xcore.cloud.mindustry.MindustryCommandManager;
import org.xcore.plugin.cloud.config.CloudExceptionConfigurer;
import org.xcore.plugin.cloud.config.CloudGuardConfigurer;
import org.xcore.plugin.cloud.config.CloudManagerFactory;
import org.xcore.plugin.cloud.config.CloudParserConfigurer;
import org.xcore.plugin.cloud.config.DisabledCommandPolicy;
import org.xcore.plugin.cloud.annotation.*;
import org.xcore.plugin.cloud.exception.XCoreCommandException;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class CloudService {
    private static final String ERROR_COMMAND_DISABLED = "error-command-disabled";
    private static final CloudKey<Boolean> REQUIRES_MUTE_CHECK_META = CloudKey.of("xcore.requiresMuteCheck", Boolean.class);
    private static final CloudKey<PlayTimeLimit> REQUIRES_PLAY_TIME_META = CloudKey.of("xcore.requiresPlayTime", PlayTimeLimit.class);

    private final CloudManagerFactory cloudManagerFactory;
    private final CloudParserConfigurer cloudParserConfigurer;
    private final CloudGuardConfigurer cloudGuardConfigurer;
    private final DisabledCommandPolicy disabledCommandPolicy;
    private final CloudExceptionConfigurer cloudExceptionConfigurer;

    @Getter private MindustryCommandManager<XCoreSender> clientManager;
    @Getter private MindustryCommandManager<XCoreSender> serverManager;
    private AnnotationParser<XCoreSender> clientAnnotationParser;
    private AnnotationParser<XCoreSender> serverAnnotationParser;

    @Inject
    public CloudService(CloudManagerFactory cloudManagerFactory,
                        CloudParserConfigurer cloudParserConfigurer,
                        CloudGuardConfigurer cloudGuardConfigurer,
                        DisabledCommandPolicy disabledCommandPolicy,
                        CloudExceptionConfigurer cloudExceptionConfigurer) {
        this.cloudManagerFactory = cloudManagerFactory;
        this.cloudParserConfigurer = cloudParserConfigurer;
        this.cloudGuardConfigurer = cloudGuardConfigurer;
        this.disabledCommandPolicy = disabledCommandPolicy;
        this.cloudExceptionConfigurer = cloudExceptionConfigurer;
    }

    @PostConstruct
    public void init() {
        Log.info("[XCore] Initializing Cloud Command Infrastructure...");

        this.clientManager = cloudManagerFactory.createManager(Vars.netServer.clientCommands);
        this.serverManager = cloudManagerFactory.createManager(ServerControl.instance.handler);

        configureManager(clientManager);
        configureManager(serverManager);

        this.clientAnnotationParser = new AnnotationParser<>(clientManager, XCoreSender.class);
        this.serverAnnotationParser = new AnnotationParser<>(serverManager, XCoreSender.class);

        configureAnnotationGuards(clientAnnotationParser);
        configureAnnotationGuards(serverAnnotationParser);
    }

    private void configureManager(MindustryCommandManager<XCoreSender> manager) {
        cloudParserConfigurer.configure(manager);
        cloudGuardConfigurer.configure(manager, this::throwDisabledCommandException);
        cloudExceptionConfigurer.configure(manager);
    }

    public boolean isCommandDisabled(Command<XCoreSender> command) {
        return disabledCommandPolicy.isCommandDisabled(command);
    }

    public boolean isCommandDisabled(String commandName) {
        return disabledCommandPolicy.isCommandDisabled(commandName);
    }

    private void throwDisabledCommandException(String commandName) {
        throw new XCoreCommandException(
                ERROR_COMMAND_DISABLED,
                args("command", commandName)
        );
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
