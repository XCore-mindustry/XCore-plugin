package org.xcore.plugin.session;

import io.avaje.inject.AssistFactory;
import io.avaje.inject.Assisted;
import lombok.Data;
import mindustry.gen.Player;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.common.StatusEnum;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.localization.BundleService;
import org.xcore.plugin.ui.MenuBuilder;
import org.xcore.plugin.ui.MenuService;

import java.util.ArrayList;
import java.util.*;
import java.util.function.Consumer;

@AssistFactory(SessionFactory.class)
@Data
public class Session {
    public final GlobalConfig globalConfig;
    public final BundleService bundle;
    public final MenuService menuService;
    public final PlayerDataRepository playerDataRepository;

    public Player player;
    public PlayerData data;
    public Localization localization;
    public XCoreSender sender;

    public final List<Runnable> actions = new ArrayList<>();
    public final Map<String, StatusEnum> sortStatus = new HashMap<>();
    public final Deque<Runnable> history = new ArrayDeque<>();
    public final Map<Class<?>, Object> drafts = new HashMap<>();
    public Consumer<String> textHandler;
    public Integer lastPrivateTargetPid;
    public long lastPrivateMessageAt;

    public Session(GlobalConfig globalConfig,
                   BundleService bundle,
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
        if (this.data.language == null) this.data.language = "auto";
        if (this.data.translatorLanguage == null) this.data.translatorLanguage = "off";

        this.localization = new Localization(bundle, this);
    }

    public Session start(XCoreSender sender) {
        actions.clear();
        this.sender = sender;
        return this;
    }

    public Session clear() {
        actions.clear();
        return this;
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

    public void resetSender() {
        sender = null;
    }

    public void clearHistory() {
        history.clear();
    }

    public void clearSortStatus() {
        sortStatus.clear();
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
