package org.xcore.plugin.infra.commands.interceptor;

import org.xcore.plugin.infra.commands.annotation.AdminOnly;
import org.xcore.plugin.infra.commands.context.CommandContext;

import java.lang.reflect.Method;

public class AdminInterceptor implements CommandInterceptor {
    @Override
    public boolean intercept(CommandContext<?> ctx, Method method) {
        if (method.isAnnotationPresent(AdminOnly.class) || method.getDeclaringClass().isAnnotationPresent(AdminOnly.class)) {
            if (!ctx.player().admin) {
                ctx.send("error-access-denied");
                return false;
            }
        }
        return true;
    }
}