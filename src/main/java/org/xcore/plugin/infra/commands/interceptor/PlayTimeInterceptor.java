package org.xcore.plugin.infra.commands.interceptor;

import org.xcore.plugin.infra.commands.annotation.MinPlayTime;
import org.xcore.plugin.infra.commands.context.ClientContext;
import org.xcore.plugin.infra.commands.context.CommandContext;
import org.xcore.plugin.PluginVars;
import java.lang.reflect.Method;

public class PlayTimeInterceptor implements CommandInterceptor {

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

        var data = PluginVars.database.getCached(player.uuid());

        if (data != null && data.totalPlayTime < annotation.minutes()) {
            clientCtx.send(annotation.errorKey(), "time", annotation.minutes());
            return false;
        }

        return true;
    }
}