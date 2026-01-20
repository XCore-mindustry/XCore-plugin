package org.xcore.plugin.infra.commands;

import arc.util.CommandHandler;
import arc.util.Log;
import lombok.Getter;
import mindustry.gen.Player;
import org.xcore.plugin.PluginVars;
import org.xcore.plugin.infra.commands.interceptor.AdminInterceptor;
import org.xcore.plugin.infra.commands.interceptor.CommandInterceptor;
import org.xcore.plugin.infra.commands.interceptor.MuteInterceptor;
import org.xcore.plugin.infra.commands.interceptor.PlayTimeInterceptor;

import org.xcore.plugin.infra.commands.annotation.*;
import org.xcore.plugin.infra.commands.context.CommandContext;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;


public class CommandBus {
    private final CommandHandler handler;
    private final String bundlePrefix = "commands-";

    private final List<CommandInterceptor> interceptors = new ArrayList<>();

    @Getter
    private int totalCommands = 0;

    public CommandBus(CommandHandler handler) {
        this.handler = handler;

        interceptors.add(new AdminInterceptor());
        interceptors.add(new MuteInterceptor());
        interceptors.add(new PlayTimeInterceptor());
    }

    public void register(Object... controllers) {
        for (Object controller : controllers) {
            for (Method method : controller.getClass().getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Command.class)) continue;

                Command meta = method.getAnnotation(Command.class);
                registerMethod(controller, method, meta);
            }
        }
    }

    private void registerMethod(Object controller, Method method, Command meta) {
        String commandName = meta.name();

        String description = PluginVars.bundle.format(
                PluginVars.bundle.defaultLocale,
                bundlePrefix + commandName + "-description",
                com.ospx.flubundle.Bundle.args()
        );

        CommandHandler.CommandRunner<Player> runner = (args, player) -> {
            try {
                CommandContext<Player> ctx = new CommandContext<>(player, args);

                for (CommandInterceptor interceptor : interceptors) {
                    if (!interceptor.intercept(ctx, method)) return;
                }

                method.invoke(controller, ctx);
            } catch (Exception e) {
                Log.err("Critical error executing command: " + commandName, e);
                player.sendMessage("[scarlet]Internal plugin error occurred.");
            }
        };

        handler.register(commandName, meta.params(), description, runner);
        totalCommands++;

        for (String alias : meta.aliases()) {
            handler.register(alias, meta.params(), description, runner);
        }
    }
}