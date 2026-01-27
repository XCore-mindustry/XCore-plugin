package org.xcore.plugin.modules.common;

import jakarta.inject.Singleton;

@Singleton
public class PluginState {
    public long gameStartTime;
    public boolean restartOnGameOver = false;
}