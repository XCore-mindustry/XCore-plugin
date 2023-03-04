package org.xcore.plugin.utils;

import arc.func.Cons;
import fr.xpdustry.javelin.JavelinConfig;
import fr.xpdustry.javelin.JavelinEvent;
import fr.xpdustry.javelin.JavelinPlugin;

import static org.xcore.plugin.PluginVars.isSocketServer;

public class JavelinCommunicator {
    /**
     * Инициатор Javelin событий
     */
    public static void init() {
        isSocketServer = JavelinPlugin.getJavelinConfig().getMode() == JavelinConfig.Mode.SERVER;
    }

    /**
     * Отправка события по серверам
     *
     * @param event Событие
     * @param <E>   Класс события
     */
    public static <E extends JavelinEvent> void sendEvent(E event) {
        sendEvent(event, null);
    }

    /**
     * Отправка события по серверам с обработкой при отправке
     *
     * @param event    Событие
     * @param <E>      Класс события
     * @param callback Действие в конце отправки события
     */
    public static <E extends JavelinEvent> void sendEvent(E event, Cons<E> callback) {
        if (isSocketServer && callback != null) {
            callback.get(event);
        } else {
            JavelinPlugin.getJavelinSocket().sendEvent(event);
        }
    }
}
