package org.xcore.plugin.command.core.interceptor;

import jakarta.inject.Singleton;
import org.xcore.plugin.command.core.annotation.AdminOnly;
import org.xcore.plugin.command.core.context.ClientContext;
import org.xcore.plugin.command.core.context.CommandContext;

import java.lang.reflect.Method;

@Singleton
public class AdminInterceptor implements CommandInterceptor {

    @Override
    public boolean intercept(CommandContext ctx, Method method) {
        if (!ctx.isClientContext()) {
            return true;
        }

        if (method.isAnnotationPresent(AdminOnly.class) ||
                method.getDeclaringClass().isAnnotationPresent(AdminOnly.class)) {
            ClientContext clientCtx = (ClientContext) ctx;
            if (!clientCtx.player().admin) {
                clientCtx.send("error-access-denied");
                return false;
            }
        }
        return true;
    }
}
