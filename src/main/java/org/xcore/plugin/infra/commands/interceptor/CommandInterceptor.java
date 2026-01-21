package org.xcore.plugin.infra.commands.interceptor;

import org.xcore.plugin.infra.commands.context.CommandContext;
import java.lang.reflect.Method;

public interface CommandInterceptor {
    boolean intercept(CommandContext ctx, Method method);
}