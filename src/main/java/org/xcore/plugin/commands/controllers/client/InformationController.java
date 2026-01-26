package org.xcore.plugin.commands.controllers.client;

import arc.math.Mathf;
import arc.util.CommandHandler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Call;
import mindustry.ui.Menus;
import org.xcore.plugin.infra.commands.annotation.Command;
import org.xcore.plugin.infra.commands.context.ClientContext;
import org.xcore.plugin.modules.bundles.BundleService;

import static com.ospx.flubundle.Bundle.args;
import static org.xcore.plugin.PluginVars.*;

@Singleton
public class InformationController {

    private final BundleService bundle;
    private CommandHandler handler;
    private int infoMenuId;

    @Inject
    public InformationController(BundleService bundle) {
        this.bundle = bundle;
    }

    public void initMenu() {
        this.infoMenuId = Menus.registerMenu((player, option) -> {
            switch (option) {
                case 0 -> Call.openURI(player.con, discordUrl);
                case 1 -> Call.openURI(player.con, githubUrl);
                case 2 -> Call.openURI(player.con, donatelloUrl);
                case 3 -> Call.openURI(player.con, discordRedVSBlueUrl);
            }
        });
    }

    public void setHandler(CommandHandler handler) {
        this.handler = handler;
        initMenu();
    }

    @Command(name = "help", params = "[page]")
    public void help(ClientContext ctx) {
        int commandsPerPage = 6;
        int pageCount = Mathf.ceil((float) handler.getCommandList().size / commandsPerPage);
        int page = ctx.argInt(0, 1) - 1;

        if (page >= pageCount || page < 0) {
            ctx.send("error-page-between", args("totalPages", pageCount));
            return;
        }

        StringBuilder result = new StringBuilder();
        result.append(ctx.format("commands-help-start-content", args(
                "page", page + 1,
                "totalPages", pageCount
        ))).append("\n\n");

        for (int i = commandsPerPage * page; i < Math.min(commandsPerPage * (page + 1), handler.getCommandList().size); i++) {
            var command = handler.getCommandList().get(i);
            if (command.text.equals("login")) continue;

            result.append(ctx.format("commands-help-content", args(
                    "commandName", command.text,
                    "commandParams", ctx.format("commands-" + command.text + "-params", args()),
                    "commandDescription", ctx.format("commands-" + command.text + "-description", args())
            ))).append("\n");
        }

        ctx.player().sendMessage(result.toString());
    }

    @Command(name = "information", aliases = {"info"})
    public void info(ClientContext ctx) {
        Call.menu(ctx.player().con, infoMenuId,
                ctx.format("commands-info-title", args()),
                ctx.format("commands-info-text", args("xcoreVersion", xcoreVersion)),
                new String[][]{
                        {"Discord", "GitHub", "Donatello"},
                        {"RedVSBlue"},
                        {ctx.format("close", args())}
                }
        );
    }
}
