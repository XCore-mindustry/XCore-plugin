package org.xcore.plugin.commands.controllers.client;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.infra.commands.annotation.Command;
import org.xcore.plugin.infra.commands.context.ClientContext;
import org.xcore.plugin.listeners.SocketEvents;
import org.xcore.plugin.modules.Config;
import org.xcore.plugin.modules.database.DatabaseService;
import org.xcore.plugin.modules.network.NetworkService;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.netServer;

@Singleton
public class AuthController {

    private final DatabaseService database;
    private final NetworkService network;
    private final Config config;

    @Inject
    public AuthController(DatabaseService database, NetworkService network, Config config) {
        this.database = database;
        this.network = network;
        this.config = config;
    }

    @Command(name = "login", params = "<password>")
    public void login(ClientContext ctx) {
        String password = ctx.args()[0];

        if (password.length() < 4) {
            ctx.send("error-admin-password-too-short", args());
            return;
        }

        var data = database.getCached(ctx.player().uuid());
        var adminData = database.getAdminDataRepository().findByUuid(data.uuid);

        if (adminData.password.isEmpty()) {
            adminData.hashPassword(password);
            database.getAdminDataRepository().save(adminData);
            ctx.send("commands-login-admin-password-created", args());
        }

        if (adminData.verifyPassword(password)) {
            if (adminData.adminConfirmed) {
                ctx.player().admin(true);
                netServer.admins.adminPlayer(ctx.player().uuid(), ctx.player().getInfo().adminUsid);
                ctx.send("commands-login-success", args());
            } else {
                ctx.send("commands-login-request-approval-discord", args());
                network.post(new SocketEvents.AdminRequestEvent(data.pid, config.server));
            }
        } else {
            ctx.send("error-wrong-admin-password", args());
        }
    }

    @Command(name = "logout")
    public void logout(ClientContext ctx) {
        if (ctx.player().admin) {
            ctx.player().admin(false);
            netServer.admins.unAdminPlayer(ctx.player().uuid());
            ctx.send("commands-logout-successful", args());
        }
    }
}
