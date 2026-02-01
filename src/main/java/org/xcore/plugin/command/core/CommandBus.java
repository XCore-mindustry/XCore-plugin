package org.xcore.plugin.command.core;

import arc.func.Cons;
import arc.util.CommandHandler;
import arc.util.Log;
import lombok.Getter;
import mindustry.gen.Player;
import org.xcore.plugin.command.core.annotation.Command;
import org.xcore.plugin.command.core.context.ClientContext;
import org.xcore.plugin.command.core.context.CommandContext;
import org.xcore.plugin.command.core.context.ServerContext;
import org.xcore.plugin.command.core.interceptor.CommandInterceptor;
import org.xcore.plugin.service.BundleService;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import static com.ospx.flubundle.Bundle.args;

public class CommandBus {

    private final CommandHandler handler;
    private final BundleService bundleService;
    private final List<CommandInterceptor> interceptors = new ArrayList<>();

    @Getter
    private int totalCommands = 0;

    public CommandBus(CommandHandler handler, BundleService bundleService) {
        this.handler = handler;
        this.bundleService = bundleService;
    }

    public void addInterceptor(CommandInterceptor interceptor) {
        interceptors.add(interceptor);
    }

    public void register(Object... controllers) {
        for (Object controller : controllers) {
            for (Method method : controller.getClass().getDeclaredMethods()) {
                if (method.isAnnotationPresent(Command.class)) {
                    registerMethod(controller, method);
                }
            }
        }
    }

    private void registerMethod(Object controller, Method method) {
        Command meta = method.getAnnotation(Command.class);
        Class<?> contextType = method.getParameterTypes()[0];
        boolean isClient = contextType == ClientContext.class;

        String description = meta.description();
        if (description.isEmpty() && isClient) {
            description = bundleService.format(bundleService.getDefaultLocale(),
                    "commands-" + meta.name() + "-description", args());
        }

        if (isClient) {
            CommandHandler.CommandRunner<Player> runner = (args, player) ->
                    invoke(controller, method, new ClientContext(player, args, bundleService), (ctx, e) -> {
                        Log.err("Client command error: " + meta.name(), e);
                        bundleService.send(player, "error-internal", args());
                    });
            handler.register(meta.name(), meta.params(), description, runner);
            for (String alias : meta.aliases()) {
                handler.register(alias, meta.params(), description, runner);
            }
        } else {
            Cons<String[]> runner = (args) ->
                    invoke(controller, method, new ServerContext(args), (ctx, e) ->
                            Log.err("Server command error: " + meta.name(), e));
            handler.register(meta.name(), meta.params(), description, runner);
            for (String alias : meta.aliases()) {
                handler.register(alias, meta.params(), description, runner);
            }
        }
        totalCommands++;
    }

    private void invoke(Object controller, Method method, CommandContext ctx,
                        BiConsumer<CommandContext, Exception> errorHandler) {
        try {
            for (CommandInterceptor interceptor : interceptors) {
                if (!interceptor.intercept(ctx, method)) return;
            }
            method.invoke(controller, ctx);
        } catch (Exception e) {
            errorHandler.accept(ctx, e);
        }
    }
}
