package org.xcore.plugin.infra.commands.interceptor;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.infra.commands.annotation.MuteCheck;
import org.xcore.plugin.infra.commands.context.ClientContext;
import org.xcore.plugin.infra.commands.context.CommandContext;
import org.xcore.plugin.utils.SecurityService;

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
