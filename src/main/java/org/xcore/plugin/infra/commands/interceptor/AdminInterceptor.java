package org.xcore.plugin.infra.commands.interceptor;

import org.xcore.plugin.infra.commands.annotation.AdminOnly;
import org.xcore.plugin.infra.commands.context.ClientContext;
import org.xcore.plugin.infra.commands.context.CommandContext;

import java.lang.reflect.Method;

public class AdminInterceptor implements CommandInterceptor {
    @Override
    public boolean intercept(CommandContext ctx, Method method) {
        if (!ctx.isClientContext()) {
            return true;
        }

        if (method.isAnnotationPresent(AdminOnly.class) || method.getDeclaringClass().isAnnotationPresent(AdminOnly.class)) {
            ClientContext clientCtx = (ClientContext) ctx;
            if (!clientCtx.player().admin) {
                clientCtx.send("error-access-denied");
                return false;
            }
        }
        return true;
    }
}