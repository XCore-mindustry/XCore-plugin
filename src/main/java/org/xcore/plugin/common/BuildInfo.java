package org.xcore.plugin.common;

import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.Setter;

@Singleton
public class BuildInfo {
    @Setter
    @Getter
    private String version = "Unknown";
}