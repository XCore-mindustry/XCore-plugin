package org.xcore.plugin.ui.menu;

import com.ospx.flubundle.Bundle;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.GameDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.PlayerStatsOverview;
import org.xcore.plugin.model.enums.TopCategory;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.service.PlayerProfileSettingsService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.route.MenuRoute;

@Singleton
public class PlayerMenu extends Menu {

    private final Bundle bundle;
    private final PlayerProfileSettingsService profileSettings;
    private final MenuService menuService;
    private final GameDataRepository gameDataRepository;
    private final Async async;

    @Inject
    public PlayerMenu(TomlSecretsConfig secretsConfig,
                      SessionService sessionService,
                      GameDataRepository gameDataRepository,
                      Bundle bundle,
                      PlayerDisplayService playerDisplayService,
                      PlayerProfileSettingsService profileSettings,
                      AuditHistoryMenu auditHistoryMenu,
                      MenuService menuService,
                      Async async) {
        super(secretsConfig, sessionService);
        this.bundle = bundle;
        this.profileSettings = profileSettings;
        this.menuService = menuService;
        this.gameDataRepository = gameDataRepository;
        this.async = async;

        menuService.registerRoute(new PlayerProfileFlows.PlayerFlow(this, gameDataRepository, auditHistoryMenu));
        menuService.registerRoute(new PlayerProfileFlows.PlayersFlow(this, sessionService, playerDisplayService));
    }

    public PlayerMenu(TomlSecretsConfig secretsConfig,
                      SessionService sessionService,
                      GameDataRepository gameDataRepository,
                      Bundle bundle,
                      PlayerDisplayService playerDisplayService,
                      PlayerProfileSettingsService profileSettings,
                      AuditHistoryMenu auditHistoryMenu,
                      MenuService menuService) {
        this(secretsConfig, sessionService, gameDataRepository, bundle, playerDisplayService, profileSettings, auditHistoryMenu, menuService, null);
    }

    @PostConstruct
    public void init() {
        menuService.registerRoute(new PlayerSettingsFlows.SettingsFlow(profileSettings));
        menuService.registerRoute(new PlayerSettingsFlows.ChatSettingsFlow(profileSettings));
        menuService.registerRoute(new PlayerSettingsFlows.LanguageSelectionFlow(bundle, profileSettings));
        menuService.registerRoute(new PlayerSettingsFlows.BadgeSymbolColorModeFlow(profileSettings));
        menuService.registerRoute(new PlayerSettingsFlows.BadgesFlow(profileSettings));
        menuService.registerRoute(new PlayerSettingsFlows.AllBadgesFlow(profileSettings));
    }

    public void player(String uuid, PlayerData targetData) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        if (targetData == null) {
            session.locale().send("error-player-not-found");
            return;
        }

        if (async != null && session.player != null) {
            async.forPlayer(session.player, () -> {
                Integer hexedTop = session.playerDataRepository != null
                        ? session.playerDataRepository.findTopRank(TopCategory.HEXED, targetData)
                        : null;
                PlayerStatsOverview stats = gameDataRepository != null
                        ? gameDataRepository.aggregatePlayerStatsOverview(targetData.uuid)
                        : null;
                return new ProfileDataBundle(stats, hexedTop);
            }, (player, bundle) -> {
                Session current = sessionService.get(uuid);
                if (current == null) return;
                current.setDraft(PlayerProfileFlows.PlayerState.class,
                        new PlayerProfileFlows.PlayerState(targetData.uuid, targetData, bundle.stats(), bundle.hexedTop()));
                current.menuService.renderRoute(current, MenuRoute.of(PlayerProfileFlows.ROUTE_PLAYER).withParam("targetUuid", targetData.uuid));
            });
            return;
        }

        session.setDraft(PlayerProfileFlows.PlayerState.class, new PlayerProfileFlows.PlayerState(targetData.uuid, targetData));
        session.menuService.renderRoute(session, MenuRoute.of(PlayerProfileFlows.ROUTE_PLAYER).withParam("targetUuid", targetData.uuid));
    }

    private record ProfileDataBundle(PlayerStatsOverview stats, Integer hexedTop) {}

    public void players(String uuid, int page) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();
        session.menuService.renderRoute(session, MenuRoute.of(PlayerProfileFlows.ROUTE_PLAYERS).withParam("page", String.valueOf(page)));
    }

    public void settings(String uuid, PlayerData targetData) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();
        if (targetData == null) {
            session.locale().send("error-player-not-found");
            return;
        }

        if (!session.data.uuid.equals(targetData.uuid) && !session.player.admin) {
            session.locale().send("error-no-access");
            return;
        }

        session.setDraft(PlayerSettingsFlows.SettingsState.class, new PlayerSettingsFlows.SettingsState(targetData.uuid, targetData));
        session.menuService.renderRoute(session, MenuRoute.of(PlayerSettingsFlows.ROUTE_SETTINGS).withParam("targetUuid", targetData.uuid));
    }

    public void chatSettings(String uuid, PlayerData targetData) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();
        if (targetData == null) {
            session.locale().send("error-player-not-found");
            return;
        }

        if (!session.data.uuid.equals(targetData.uuid) && !session.player.admin) {
            session.locale().send("error-no-access");
            return;
        }

        session.menuService.renderRoute(session, MenuRoute.of(PlayerSettingsFlows.ROUTE_CHAT_SETTINGS).withParam("targetUuid", targetData.uuid));
    }

    public void languageSelectionMenu(String uuid, PlayerData targetData, boolean isTranslator) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        session.menuService.renderRoute(session, MenuRoute.of(PlayerSettingsFlows.ROUTE_LANGUAGE_SELECTION)
                .withParam("targetUuid", targetData.uuid)
                .withParam("isTranslator", String.valueOf(isTranslator)));
    }

    public void badges(String uuid, PlayerData targetData) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();
        if (targetData == null) {
            session.locale().send("error-player-not-found");
            return;
        }

        if (!session.data.uuid.equals(targetData.uuid) && !session.player.admin) {
            session.locale().send("error-no-access");
            return;
        }

        session.menuService.renderRoute(session, MenuRoute.of(PlayerSettingsFlows.ROUTE_BADGES).withParam("targetUuid", targetData.uuid));
    }

    public void badgeSymbolColorMode(String uuid, PlayerData targetData) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();
        if (targetData == null) {
            session.locale().send("error-player-not-found");
            return;
        }

        if (!session.data.uuid.equals(targetData.uuid) && !session.player.admin) {
            session.locale().send("error-no-access");
            return;
        }

        session.menuService.renderRoute(session, MenuRoute.of(PlayerSettingsFlows.ROUTE_BADGE_SYMBOL_COLOR).withParam("targetUuid", targetData.uuid));
    }

    public void allBadges(String uuid, PlayerData targetData) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();
        if (targetData == null) {
            session.locale().send("error-player-not-found");
            return;
        }

        if (!session.data.uuid.equals(targetData.uuid) && !session.player.admin) {
            session.locale().send("error-no-access");
            return;
        }

        session.menuService.renderRoute(session, MenuRoute.of(PlayerSettingsFlows.ROUTE_ALL_BADGES).withParam("targetUuid", targetData.uuid));
    }
}
