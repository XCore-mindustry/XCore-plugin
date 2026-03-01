package org.xcore.plugin.command.transport;

public enum ToggleState {
    ON,
    OFF;

    public boolean enabled() {
        return this == ON;
    }
}
