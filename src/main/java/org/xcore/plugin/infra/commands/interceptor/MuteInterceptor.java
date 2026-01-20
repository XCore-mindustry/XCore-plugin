package org.xcore.plugin.infra.commands.interceptor;

import org.xcore.plugin.infra.commands.annotation.MuteCheck;
import org.xcore.plugin.infra.commands.context.CommandContext;
import org.xcore.plugin.utils.Security;

import java.lang.reflect.Method;

public class MuteInterceptor implements CommandInterceptor {
    @Override
    public boolean intercept(CommandContext<?> ctx, Method method) {
        if (method.isAnnotationPresent(MuteCheck.class)) {
            return !Security.isMuted(ctx.player());
        }
        return true;
    }
}