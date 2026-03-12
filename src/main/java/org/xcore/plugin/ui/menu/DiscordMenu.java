package org.xcore.plugin.ui.menu;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Call;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.service.DiscordLinkService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class DiscordMenu extends Menu {

    private final DiscordLinkService discordLinkService;

    @Inject
    public DiscordMenu(Config config,
                       GlobalConfig globalConfig,
                       SessionService sessionService,
                       DiscordLinkService discordLinkService) {
        super(config, globalConfig, sessionService);
        this.discordLinkService = discordLinkService;
    }

    public void main(String uuid) {
        Session session = sessionService.get(uuid).clear();
        if (session == null || session.data == null) return;

        var local = session.locale();
        var status = discordLinkService.status(session);
        String statusText = status.linked()
                ? local.t("discord-menu-status-linked", args(
                "discordId", status.discordId(),
                "discordUsername", status.displayName()
        ))
                : local.t("discord-menu-status-not-linked", args());

        session.builder()
                .title("discord-menu-title")
                .content("discord-menu-content", args(
                        "status", statusText,
                        "discordUrl", globalConfig.discordUrl
                ))
                .addLocal("discord-menu-open", () -> Call.openURI(session.player.con, globalConfig.discordUrl))
                .addLocal("discord-menu-status", () -> main(uuid))
                .end()
                .ifAddLocal(!status.linked(), "discord-menu-link", () -> {
                    session.pushHistory(() -> main(uuid));
                    linking(uuid, false);
                })
                .ifAddLocal(status.linked(), "discord-menu-unlink", () -> {
                    if (!discordLinkService.unlink(session)) {
                        local.send("commands-discord-unlink-not-linked", args());
                    } else {
                        local.send("commands-discord-unlink-success", args());
                    }
                    main(uuid);
                })
                .end()
                .addNavigationRow()
                .show();
    }

    public void linking(String uuid, boolean regenerate) {
        Session session = sessionService.get(uuid).clear();
        if (session == null || session.data == null) return;

        var local = session.locale();
        var result = regenerate
                ? discordLinkService.createCode(session)
                : discordLinkService.getOrCreateActiveCode(session);

        if (!result.success()) {
            if (result.isError("already-linked")) {
                local.send("commands-discord-link-already-linked", args());
            } else {
                local.send("commands-discord-link-error", args());
            }
            main(uuid);
            return;
        }

        session.builder()
                .title("discord-link-menu-title")
                .content("discord-link-menu-content", args(
                        "code", result.code(),
                        "expireMinutes", result.remainingMinutes(System.currentTimeMillis()),
                        "discordUrl", globalConfig.discordUrl
                ))
                .addLocal("discord-menu-open", () -> Call.openURI(session.player.con, globalConfig.discordUrl))
                .addLocal("discord-link-menu-refresh", () -> linking(uuid, false))
                .end()
                .addLocal("discord-link-menu-regenerate", () -> linking(uuid, true))
                .addLocal("discord-link-menu-status", () -> main(uuid))
                .end()
                .addNavigationRow()
                .show();
    }
}
