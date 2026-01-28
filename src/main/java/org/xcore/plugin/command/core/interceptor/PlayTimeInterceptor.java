package org.xcore.plugin.command.core.interceptor;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.command.core.annotation.MinPlayTime;
import org.xcore.plugin.command.core.context.ClientContext;
import org.xcore.plugin.command.core.context.CommandContext;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.DatabaseService;

import java.lang.reflect.Method;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class PlayTimeInterceptor implements CommandInterceptor {

    private final DatabaseService database;
    private final GlobalConfig globalConfig;

    @Inject
    public PlayTimeInterceptor(DatabaseService database, GlobalConfig globalConfig) {
        this.database = database;
        this.globalConfig = globalConfig;
    }

    @Override
    public boolean intercept(CommandContext ctx, Method method) {
        if (!ctx.isClientContext()) return true;

        if (!method.isAnnotationPresent(MinPlayTime.class)) return true;

        MinPlayTime annotation = method.getAnnotation(MinPlayTime.class);
        ClientContext clientCtx = (ClientContext) ctx;
        var player = clientCtx.player();

        if (player.admin) return true;

        int requiredMinutes = switch (annotation.value()) {
            case GLOBAL_CHAT -> globalConfig.minPlayTimeForGlobalChat;
            case VOTE_KICK -> globalConfig.minPlayTimeForVotekick;
            case CUSTOM -> annotation.minutes();
        };

        var data = database.getCached(player.uuid());

        if (data != null && data.totalPlayTime < requiredMinutes) {
            clientCtx.send(annotation.errorKey(), args("time", requiredMinutes));
            return false;
        }

        return true;
    }
}