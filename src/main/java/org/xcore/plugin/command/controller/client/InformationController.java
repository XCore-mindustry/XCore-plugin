package org.xcore.plugin.command.controller.client;

import arc.math.Mathf;
import arc.util.CommandHandler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Call;
import mindustry.ui.Menus;
import org.xcore.plugin.command.core.annotation.Command;
import org.xcore.plugin.command.core.context.ClientContext;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.common.BuildInfo;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class InformationController {

    private CommandHandler handler;
    private int infoMenuId;

    private final Config config;
    private final GlobalConfig globalConfig;
    private final BuildInfo buildInfo;

    @Inject
    public InformationController(Config config, GlobalConfig globalConfig, BuildInfo buildInfo) {
        this.config = config;
        this.globalConfig = globalConfig;
        this.buildInfo = buildInfo;
    }

    public void initMenu() {
        this.infoMenuId = Menus.registerMenu((player, option) -> {
            switch (option) {
                case 0 -> Call.openURI(player.con, globalConfig.discordUrl);
                case 1 -> Call.openURI(player.con, globalConfig.githubUrl);
                case 2 -> Call.openURI(player.con, globalConfig.donatelloUrl);
                case 3 -> Call.openURI(player.con, globalConfig.discordRedVSBlueUrl);
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
                ctx.format("commands-info-title", args("xcorServerName", config.server)),
                ctx.format("commands-info-text", args("xcoreVersion", buildInfo.getVersion())),
                new String[][]{
                        {"Discord", "GitHub", "Donatello"},
                        {"RedVSBlue"},
                        {ctx.format("close", args())}
                }
        );
    }
}
