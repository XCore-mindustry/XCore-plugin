package org.xcore.plugin.infra.commands.interceptor;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.infra.commands.annotation.MinPlayTime;
import org.xcore.plugin.infra.commands.context.ClientContext;
import org.xcore.plugin.infra.commands.context.CommandContext;
import org.xcore.plugin.modules.database.DatabaseService;

import java.lang.reflect.Method;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class PlayTimeInterceptor implements CommandInterceptor {

    private final DatabaseService database;

    @Inject
    public PlayTimeInterceptor(DatabaseService database) {
        this.database = database;
    }

    @Override
    public boolean intercept(CommandContext ctx, Method method) {
        if (!ctx.isClientContext()) {
            return true;
        }

        if (!method.isAnnotationPresent(MinPlayTime.class)) {
            return true;
        }

        MinPlayTime annotation = method.getAnnotation(MinPlayTime.class);
        ClientContext clientCtx = (ClientContext) ctx;
        var player = clientCtx.player();

        if (player.admin) {
            return true;
        }

        var data = database.getCached(player.uuid());

        if (data != null && data.totalPlayTime < annotation.minutes()) {
            clientCtx.send(annotation.errorKey(), args("time", annotation.minutes()));
            return false;
        }

        return true;
    }
}
