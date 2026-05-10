package org.xcore.plugin.session;

import io.avaje.inject.AssistFactory;
import io.avaje.inject.Assisted;
import lombok.Data;
import mindustry.gen.Player;
import com.ospx.flubundle.Bundle;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.common.StatusEnum;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.ui.MenuBuilder;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.flow.ActiveMenuPrompt;
import org.xcore.plugin.ui.flow.ActiveMenuScreen;
import org.xcore.plugin.ui.route.MenuRoute;

import java.util.ArrayList;
import java.util.*;
import java.util.function.Consumer;

@AssistFactory(SessionFactory.class)
@Data
public class Session {
    public final GlobalConfig globalConfig;
    public final Bundle bundle;
    public final MenuService menuService;
    public final PlayerDataRepository playerDataRepository;

    public Player player;
    public PlayerData data;
    public Localization localization;
    public XCoreSender sender;

    public final List<Runnable> actions = new ArrayList<>();
    public final Map<String, StatusEnum> sortStatus = new HashMap<>();
    public final Deque<Runnable> history = new ArrayDeque<>();
    public final Deque<MenuRoute> routeHistory = new ArrayDeque<>();
    public final Map<Class<?>, Object> drafts = new HashMap<>();
    public Consumer<String> textHandler;
    public Integer lastPrivateTargetPid;
    public long lastPrivateMessageAt;

    private long uiVersion = 0L;
    private ActiveMenuScreen activeScreen;
    private ActiveMenuPrompt activePrompt;

    public Session(GlobalConfig globalConfig,
                   Bundle bundle,
                   MenuService menuService,
                   PlayerDataRepository playerDataRepository,
                   @Assisted Player player,
                   @Assisted PlayerData playerData) {
        this.globalConfig = globalConfig;
        this.bundle = bundle;
        this.menuService = menuService;
        this.playerDataRepository = playerDataRepository;

        this.player = player;
        this.data = playerData;

        if (this.data.mapVotes == null) this.data.mapVotes = new HashMap<>();
        if (this.data.eventVotes == null) this.data.eventVotes = new HashMap<>();
        if (this.data.blockedPrivateUuids == null) this.data.blockedPrivateUuids = new HashSet<>();
        if (this.data.unlockedBadges == null) this.data.unlockedBadges = new HashSet<>();
        if (this.data.activeBadge == null) this.data.activeBadge = "";
        if (this.data.badgeSymbolColorMode == null || this.data.badgeSymbolColorMode.isBlank()) this.data.badgeSymbolColorMode = "default";
        if (this.data.language == null) this.data.language = "auto";
        if (this.data.translatorLanguage == null) this.data.translatorLanguage = "off";
        if (this.data.globalChatVisible == null) this.data.globalChatVisible = true;
        if (this.data.discordRelayVisible == null) this.data.discordRelayVisible = true;

        this.localization = new Localization(bundle, this);
    }

    public Session start(XCoreSender sender) {
        actions.clear();
        this.sender = sender;
        return this;
    }

    public Session clear() {
        clearUiState();
        return this;
    }

    public long nextUiVersion() {
        return ++uiVersion;
    }

    public void setActiveScreen(ActiveMenuScreen screen) {
        this.activeScreen = screen;
    }

    public ActiveMenuScreen activeScreen() {
        return activeScreen;
    }

    public void clearActiveScreen() {
        this.activeScreen = null;
    }

    public void setActivePrompt(ActiveMenuPrompt prompt) {
        this.activePrompt = prompt;
    }

    public ActiveMenuPrompt activePrompt() {
        return activePrompt;
    }

    public void clearActivePrompt() {
        this.activePrompt = null;
    }

    public void clearUiState() {
        clearActiveScreen();
        clearActivePrompt();
        actions.clear();
        textHandler = null;
    }

    public String add(String buttonName, Runnable action) {
        actions.add(action);
        return buttonName;
    }

    public Localization locale() {
        return localization;
    }

    public MenuBuilder builder() {
        return new MenuBuilder(menuService, this);
    }

    public boolean hasDraft(Class<?> clazz) {
        return drafts.containsKey(clazz);
    }

    @SuppressWarnings("unchecked")
    public <T> T getDraft(Class<T> clazz) {
        return (T) drafts.computeIfAbsent(clazz, k -> {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            }
            catch (Exception e) {
                return null;
            }
        });
    }

    public void setDraft(Object draft) {
        if (draft != null) {
            drafts.put(draft.getClass(), draft);
        }
    }

    public <T> void setDraft(Class<T> clazz, T draft) {
        if (clazz != null && draft != null) {
            drafts.put(clazz, draft);
        }
    }

    public void clearDraft(Class<?> clazz) {
        drafts.remove(clazz);
    }

    public void clearAllDrafts() {
        drafts.clear();
    }

    public void pushHistory(Runnable menuLoader) {
        if (history.size() >= globalConfig.maxHistory) {
            history.removeFirst();
        }
        history.addLast(menuLoader);
    }

    public Runnable popHistory() {
        return history.pollLast();
    }

    public boolean hasHistory() {
        return !history.isEmpty();
    }

    public void pushRouteHistory(MenuRoute route) {
        if (route == null) {
            return;
        }
        if (routeHistory.size() >= globalConfig.maxHistory) {
            routeHistory.removeFirst();
        }
        routeHistory.addLast(route);
    }

    public MenuRoute popRouteHistory() {
        return routeHistory.pollLast();
    }

    public boolean hasRouteHistory() {
        return !routeHistory.isEmpty();
    }

    public boolean canGoBack(boolean routeAwareScreen) {
        return hasHistory() || routeAwareScreen && hasRouteHistory();
    }

    public void resetSender() {
        sender = null;
    }

    public void clearHistory() {
        history.clear();
    }

    public void clearRouteHistory() {
        routeHistory.clear();
    }

    public void clearSortStatus() {
        sortStatus.clear();
    }

    public boolean updateLanguage(String language) {
        String normalized = language == null || language.isBlank() ? "auto" : language;
        data.language = normalized;
        return playerDataRepository.updateLanguage(data.uuid, normalized);
    }

    public boolean updateTranslatorLanguage(String language) {
        String normalized = language == null || language.isBlank() ? "off" : language;
        data.translatorLanguage = normalized;
        return playerDataRepository.updateTranslatorLanguage(data.uuid, normalized);
    }

    public boolean updateGlobalChatVisible(boolean visible) {
        data.globalChatVisible = visible;
        return playerDataRepository.updateGlobalChatVisible(data.uuid, visible);
    }

    public boolean updateDiscordRelayVisible(boolean visible) {
        data.discordRelayVisible = visible;
        return playerDataRepository.updateDiscordRelayVisible(data.uuid, visible);
    }

    public void setNextStatus(String key) {
        StatusEnum current = sortStatus.getOrDefault(key, StatusEnum.Neutral);
        StatusEnum next;

        if (current == StatusEnum.Neutral) {
            next = StatusEnum.Active;
        } else if (current == StatusEnum.Active) {
            next = StatusEnum.Inactive;
        } else {
            next = StatusEnum.Neutral;
        }

        sortStatus.put(key, next);
    }
}
