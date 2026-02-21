package org.xcore.plugin.common;

import jakarta.inject.Singleton;

@Singleton
public class PluginState {
    public long gameStartTime;
    public boolean restartOnGameOver = false;
}