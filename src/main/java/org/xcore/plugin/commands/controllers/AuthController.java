package org.xcore.plugin.commands.controllers;

import mindustry.gen.Player;
import org.xcore.plugin.infra.commands.annotation.Command;
import org.xcore.plugin.infra.commands.context.CommandContext;
import org.xcore.plugin.listeners.SocketEvents;
import org.xcore.plugin.utils.NetSock;
import org.xcore.plugin.utils.models.AdminData;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.netServer;
import static org.xcore.plugin.PluginVars.config;
import static org.xcore.plugin.PluginVars.database;

@SuppressWarnings("unused")
public class AuthController {

    @Command(name = "login", params = "<password>")
    public void login(CommandContext<Player> ctx) {
        String password = ctx.args()[0];

        if (password.length() < 4) {
            ctx.send("error-admin-password-too-short", args());
            return;
        }

        var data = database.getCached(ctx.player().uuid());
        AdminData adminData = data.adminData();

        if (adminData.password.isEmpty()) {
            adminData.hashPassword(password);
            adminData.save();
            ctx.send("commands-login-admin-password-created", args());
        }

        if (adminData.verifyPassword(password)) {
            if (adminData.adminConfirmed) {
                ctx.player().admin(true);
                netServer.admins.adminPlayer(ctx.player().uuid(), ctx.player().getInfo().adminUsid);

                ctx.send("commands-login-success", args());
            } else {
                ctx.send("commands-login-request-approval-discord", args());
                NetSock.post(new SocketEvents.AdminRequestEvent(data.pid, config.server));
            }
        } else {
            ctx.send("error-wrong-admin-password", args());
        }
    }

    @Command(name = "logout")
    public void logout(CommandContext<Player> ctx) {
        if (ctx.player().admin) {
            ctx.player().admin(false);
            netServer.admins.unAdminPlayer(ctx.player().uuid());

            ctx.send("commands-logout-successful", args());
        }
    }
}