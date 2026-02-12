package org.xcore.plugin.ui;

import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.config.GlobalConfig;

import java.util.ArrayList;
import java.util.*;
import java.util.function.Consumer;

public class MenuSession {
    public final MenuService service;
    private final GlobalConfig globalConfig;

    public XCoreSender sender;

    public final List<Runnable> actions = new ArrayList<>();

    public final Map<String, StatusEnum> sortStatus = new HashMap<>();

    private final Deque<Runnable> history = new ArrayDeque<>();

    private final Map<Class<?>, Object> drafts = new HashMap<>();

    public Consumer<String> textHandler;

    public MenuSession(MenuService service, GlobalConfig globalConfig) {
        this.service = service;
        this.globalConfig = globalConfig;
        this.sender = null;
    }

    public String add(String buttonName, Runnable action) {
        actions.add(action);
        return buttonName;
    }

    public MenuSession start(XCoreSender sender) {
        actions.clear();
        this.sender = sender;
        return this;
    }

    public MenuSession clear() {
        actions.clear();
        sender = null;
        return this;
    }

    public MenuBuilder builder() {
        return new MenuBuilder(service, this);
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