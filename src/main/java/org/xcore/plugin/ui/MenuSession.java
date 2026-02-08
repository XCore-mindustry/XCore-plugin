package org.xcore.plugin.ui;

import java.util.ArrayList;
import java.util.*;
import java.util.function.Consumer;

public class MenuSession {
    public final List<Runnable> actions = new ArrayList<>();

    private final Map<Class<?>, Object> drafts = new HashMap<>();

    public Consumer<String> textHandler;

    public String add(String buttonName, Runnable action) {
        actions.add(action);
        return buttonName;
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
}