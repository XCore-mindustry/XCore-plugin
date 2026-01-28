package org.xcore.plugin.command.core.interceptor;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.command.core.annotation.MuteCheck;
import org.xcore.plugin.command.core.context.ClientContext;
import org.xcore.plugin.command.core.context.CommandContext;
import org.xcore.plugin.service.SecurityService;

import java.lang.reflect.Method;

@Singleton
public class MuteInterceptor implements CommandInterceptor {

    private final SecurityService security;

    @Inject
    public MuteInterceptor(SecurityService securityService) {
        this.security = securityService;
    }

    @Override
    public boolean intercept(CommandContext ctx, Method method) {
        if (!ctx.isClientContext()) {
            return true;
        }

        if (method.isAnnotationPresent(MuteCheck.class)) {
            ClientContext clientCtx = (ClientContext) ctx;
            return !security.isMuted(clientCtx.player());
        }
        return true;
    }
}
