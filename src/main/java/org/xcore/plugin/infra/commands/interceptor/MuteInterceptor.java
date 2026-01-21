package org.xcore.plugin.infra.commands.interceptor;

import org.xcore.plugin.infra.commands.annotation.MuteCheck;
import org.xcore.plugin.infra.commands.context.ClientContext;
import org.xcore.plugin.infra.commands.context.CommandContext;
import org.xcore.plugin.utils.Security;

import java.lang.reflect.Method;

public class MuteInterceptor implements CommandInterceptor {
    @Override
    public boolean intercept(CommandContext ctx, Method method) {
        if (!ctx.isClientContext()) {
            return true;
        }

        if (method.isAnnotationPresent(MuteCheck.class)) {
            ClientContext clientCtx = (ClientContext) ctx;
            return !Security.isMuted(clientCtx.player());
        }
        return true;
    }
}