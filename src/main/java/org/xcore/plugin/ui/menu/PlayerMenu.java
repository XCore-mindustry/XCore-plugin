package org.xcore.plugin.ui.menu;

import com.ospx.flubundle.Bundle;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.GameDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
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
    private final PlayerDisplayService playerDisplayService;
    private final MenuService menuService;
    private final GameDataRepository gameDataRepository;
    private final PlayerDataRepository playerDataRepository;
    private final Async async;
    private final AuditHistoryMenu auditHistoryMenu;

    @Inject
    public PlayerMenu(TomlSecretsConfig secretsConfig,
                      SessionService sessionService,
                      GameDataRepository gameDataRepository,
                      PlayerDataRepository playerDataRepository,
                      Bundle bundle,
                      PlayerDisplayService playerDisplayService,
                      PlayerProfileSettingsService profileSettings,
                      AuditHistoryMenu auditHistoryMenu,
                      MenuService menuService,
                      Async async) {
        super(secretsConfig, sessionService);
        this.bundle = bundle;
        this.profileSettings = profileSettings;
        this.playerDisplayService = playerDisplayService;
        this.menuService = menuService;
        this.gameDataRepository = gameDataRepository;
        this.playerDataRepository = playerDataRepository;
        this.async = async;
        this.auditHistoryMenu = auditHistoryMenu;
    }

    public PlayerMenu(TomlSecretsConfig secretsConfig,
                      SessionService sessionService,
                      GameDataRepository gameDataRepository,
                      Bundle bundle,
                      PlayerDisplayService playerDisplayService,
                      PlayerProfileSettingsService profileSettings,
                      AuditHistoryMenu auditHistoryMenu,
                      MenuService menuService,
                      Async async) {
        this(secretsConfig, sessionService, gameDataRepository, null, bundle, playerDisplayService, profileSettings, auditHistoryMenu, menuService, async);
    }

    public PlayerMenu(TomlSecretsConfig secretsConfig,
                      SessionService sessionService,
                      GameDataRepository gameDataRepository,
                      Bundle bundle,
                      PlayerDisplayService playerDisplayService,
                      PlayerProfileSettingsService profileSettings,
                      AuditHistoryMenu auditHistoryMenu,
                      MenuService menuService) {
        this(secretsConfig, sessionService, gameDataRepository, null, bundle, playerDisplayService, profileSettings, auditHistoryMenu, menuService, null);
    }

    @PostConstruct
    public void init() {
    }

    public void player(String uuid, PlayerData targetData) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        if (targetData == null) {
            session.locale().send("error-player-not-found");
            return;
        }

        openProfileUi(session, targetData, PlayerProfileUiController.Tab.OVERVIEW);
    }

    public void openProfileUi(Session session, PlayerData targetData) {
        openProfileUi(session, targetData, PlayerProfileUiController.Tab.OVERVIEW, null, null);
    }

    public void openProfileUi(Session session, PlayerData targetData, PlayerProfileUiController.Tab tab) {
        openProfileUi(session, targetData, tab, null, null);
    }

    public void openProfileUi(Session session, PlayerData targetData, PlayerProfileUiController.Tab tab,
                             PlayerStatsOverview preloadedStats, Integer preloadedHexedTop) {
        if (session == null || session.player == null) return;
        session.clear();

        if (targetData == null) {
            if (session.locale() != null) {
                session.locale().send("error-player-not-found");
            }
            return;
        }

        var controller = new PlayerProfileUiController(
                this, auditHistoryMenu, sessionService, playerDisplayService,
                playerDataRepository, gameDataRepository, async, session, targetData
        );

        var initialModel = controller.createInitialModel(tab, preloadedStats, preloadedHexedTop);
        menuService.openUi(session, controller, initialModel);

        if (preloadedStats == null && async != null && session.player != null) {
            async.forPlayer(session.player, () -> {
                Integer hexedTop = playerDataRepository != null
                        ? playerDataRepository.findTopRank(TopCategory.HEXED, targetData)
                        : (session.playerDataRepository != null
                                ? session.playerDataRepository.findTopRank(TopCategory.HEXED, targetData)
                                : null);
                PlayerStatsOverview stats = gameDataRepository != null
                        ? gameDataRepository.aggregatePlayerStatsOverview(targetData.uuid)
                        : null;
                return new ProfileDataBundle(stats, hexedTop);
            }, (player, bundle) -> {
                var active = session.activeUiSession();
                if (active != null && active.model() instanceof PlayerProfileUiController.ProfileModel) {
                    @SuppressWarnings("unchecked")
                    var profileSession = (org.xcore.ui.runtime.UiSession<PlayerProfileUiController.ProfileModel, PlayerProfileUiController.ProfileEvent>) active;
                    profileSession.dispatch(new PlayerProfileUiController.ProfileEvent.StatsLoaded(
                            targetData.uuid, bundle.stats(), bundle.hexedTop()
                    ));
                }
            });
        }
    }

    private record ProfileDataBundle(PlayerStatsOverview stats, Integer hexedTop) {}

    public void players(String uuid, int page) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();
        openProfileUi(session, session.data, PlayerProfileUiController.Tab.PLAYERS);
    }

    public void settings(String uuid, PlayerData targetData) {
        openSettingsTab(uuid, targetData, PlayerSettingsUiController.Tab.PROFILE);
    }

    public void openSettingsUi(Session session, PlayerData targetData) {
        openSettingsUi(session, targetData, PlayerSettingsUiController.Tab.PROFILE);
    }

    public void openSettingsUi(Session session, PlayerData targetData, PlayerSettingsUiController.Tab tab) {
        if (session == null || session.player == null) return;
        session.clear();
        if (!canAccessSettings(session, targetData)) return;

        var controller = new PlayerSettingsUiController(this, profileSettings, session, targetData);
        var initialModel = PlayerSettingsUiController.createModel(session, targetData, tab);
        menuService.openUi(session, controller, initialModel);
    }

    public void chatSettings(String uuid, PlayerData targetData) {
        openSettingsTab(uuid, targetData, PlayerSettingsUiController.Tab.CHAT_LANG);
    }

    public void languageSelectionMenu(String uuid, PlayerData targetData, boolean isTranslator) {
        openSettingsTab(uuid, targetData, PlayerSettingsUiController.Tab.CHAT_LANG);
    }

    public void badges(String uuid, PlayerData targetData) {
        openSettingsTab(uuid, targetData, PlayerSettingsUiController.Tab.BADGES);
    }

    public void badgeSymbolColorMode(String uuid, PlayerData targetData) {
        openSettingsTab(uuid, targetData, PlayerSettingsUiController.Tab.BADGES);
    }

    public void allBadges(String uuid, PlayerData targetData) {
        openSettingsTab(uuid, targetData, PlayerSettingsUiController.Tab.BADGES);
    }

    private void openSettingsTab(String uuid, PlayerData targetData, PlayerSettingsUiController.Tab tab) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();
        if (!canAccessSettings(session, targetData)) return;

        openSettingsUi(session, targetData, tab);
    }

    private boolean canAccessSettings(Session session, PlayerData targetData) {
        if (targetData == null) {
            session.locale().send("error-player-not-found");
            return false;
        }
        if (!session.data.uuid.equals(targetData.uuid) && (session.player == null || !session.player.admin)) {
            session.locale().send("error-no-access");
            return false;
        }
        return true;
    }
}
