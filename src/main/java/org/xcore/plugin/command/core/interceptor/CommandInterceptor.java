package org.xcore.plugin.command.core.interceptor;

import org.xcore.plugin.command.core.context.CommandContext;
import java.lang.reflect.Method;

public interface CommandInterceptor {
    boolean intercept(CommandContext ctx, Method method);
}