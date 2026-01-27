package org.xcore.plugin.modules.common;

import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.Setter;

@Singleton
public class BuildInfo {
    @Setter
    @Getter
    private String version = "Unknown";
}