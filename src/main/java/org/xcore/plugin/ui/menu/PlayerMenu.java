package org.xcore.plugin.ui.menu;

import com.ospx.flubundle.Bundle;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.permission.TargetHierarchy;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.GameDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.integration.profile.ProfileSectionRegistry;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.PlayerStatsOverview;
import org.xcore.plugin.model.enums.TopCategory;
import org.xcore.plugin.permission.PermissionNodes;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.service.PlayerProfileSettingsService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.route.MenuRoute;

@Singleton
public class PlayerMenu extends Menu {

    private final TargetHierarchy hierarchy;
    private final Bundle bundle;
    private final PlayerProfileSettingsService profileSettings;
    private final PlayerDisplayService playerDisplayService;
    private final MenuService menuService;
    private final GameDataRepository gameDataRepository;
    private final PlayerDataRepository playerDataRepository;
    private final Async async;
    private final AuditHistoryMenu auditHistoryMenu;
    private final ProfileSectionRegistry profileSections;

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
                      Async async,
                      ProfileSectionRegistry profileSections,
                      TargetHierarchy hierarchy) {
        super(secretsConfig, sessionService);
        this.hierarchy = hierarchy;
        this.profileSections = profileSections;
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
                      PlayerDataRepository playerDataRepository,
                      Bundle bundle,
                      PlayerDisplayService playerDisplayService,
                      PlayerProfileSettingsService profileSettings,
                      AuditHistoryMenu auditHistoryMenu,
                      MenuService menuService,
                      Async async,
                      ProfileSectionRegistry profileSections) {
        this(secretsConfig, sessionService, gameDataRepository, playerDataRepository, bundle, playerDisplayService,
                profileSettings, auditHistoryMenu, menuService, async, profileSections, TargetHierarchy.none());
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
        this(secretsConfig, sessionService, gameDataRepository, null, bundle, playerDisplayService, profileSettings, auditHistoryMenu, menuService, async, null);
    }

    public PlayerMenu(TomlSecretsConfig secretsConfig,
                      SessionService sessionService,
                      GameDataRepository gameDataRepository,
                      Bundle bundle,
                      PlayerDisplayService playerDisplayService,
                      PlayerProfileSettingsService profileSettings,
                      AuditHistoryMenu auditHistoryMenu,
                      MenuService menuService) {
        this(secretsConfig, sessionService, gameDataRepository, null, bundle, playerDisplayService, profileSettings, auditHistoryMenu, menuService, null, null);
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
        openProfileUi(session, targetData, PlayerProfileUiController.Tab.OVERVIEW, null);
    }

    public void openProfileUi(Session session, PlayerData targetData, PlayerProfileUiController.Tab tab) {
        openProfileUi(session, targetData, tab, null);
    }

    /** @param preloaded what is already known about the target; {@code null} to read it after opening */
    public void openProfileUi(Session session, PlayerData targetData, PlayerProfileUiController.Tab tab,
                             ProfileDetails preloaded) {
        if (session == null || session.player == null) return;
        session.clear();

        if (targetData == null) {
            if (session.locale() != null) {
                session.locale().send("error-player-not-found");
            }
            return;
        }

        var controller = new PlayerProfileUiController(
                this, auditHistoryMenu, sessionService, playerDisplayService, session, targetData
        );

        var initialModel = controller.createInitialModel(tab, preloaded);
        menuService.openUi(session, controller, initialModel, true);

        if (preloaded == null) {
            loadDetails(session, targetData);
        }
    }

    /**
     * Reads the storage-backed part of {@code target}'s profile off the game thread and hands
     * it to the profile dialog {@code session} has open, if it is still showing that player.
     */
    public void loadDetails(Session session, PlayerData target) {
        if (async == null || session == null || session.player == null || target == null) return;

        async.forPlayer(session.player, () -> details(session, target), (player, details) -> {
            var active = session.activeUiSession();
            if (active != null && active.model() instanceof PlayerProfileUiController.ProfileModel) {
                @SuppressWarnings("unchecked")
                var profileSession = (org.xcore.ui.runtime.UiSession<PlayerProfileUiController.ProfileModel, PlayerProfileUiController.ProfileEvent>) active;
                profileSession.dispatch(new PlayerProfileUiController.ProfileEvent.DetailsLoaded(target.uuid, details));
            }
        });
    }

    /** Blocking. */
    private ProfileDetails details(Session session, PlayerData target) {
        PlayerDataRepository players = playerDataRepository != null ? playerDataRepository : session.playerDataRepository;
        Integer hexedTop = players != null ? players.findTopRank(TopCategory.HEXED, target) : null;
        PlayerStatsOverview stats = gameDataRepository != null
                ? gameDataRepository.aggregatePlayerStatsOverview(target.uuid)
                : null;
        return new ProfileDetails(stats, hexedTop,
                profileSections != null ? profileSections.load(target) : java.util.List.of());
    }

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

        // Somebody else's settings are theirs to change only for a viewer who outranks them.
        hierarchy.whenAllowed(session, targetData.uuid, () -> {
            var controller = new PlayerSettingsUiController(this, profileSettings, session, targetData);
            var initialModel = PlayerSettingsUiController.createModel(session, targetData, tab);
            // Filling the screen is what lets the cards scroll on a screen shorter than the settings.
            menuService.openUi(session, controller, initialModel, true);
        }, () -> session.locale().send(TargetHierarchy.DENIED_KEY));
    }

    public void chatSettings(String uuid, PlayerData targetData) {
        openSettingsTab(uuid, targetData, PlayerSettingsUiController.Tab.CHAT);
    }

    public void languageSelectionMenu(String uuid, PlayerData targetData, boolean isTranslator) {
        openSettingsTab(uuid, targetData,
                isTranslator ? PlayerSettingsUiController.Tab.CHAT : PlayerSettingsUiController.Tab.LANGUAGE);
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
        if (!session.data.uuid.equals(targetData.uuid) && !session.has(PermissionNodes.PLAYERS_SETTINGS_OTHERS)) {
            session.locale().send("error-no-access");
            return false;
        }
        return true;
    }
}
