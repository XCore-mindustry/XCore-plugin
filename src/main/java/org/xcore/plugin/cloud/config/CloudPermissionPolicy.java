package org.xcore.plugin.cloud.config;

import jakarta.inject.Singleton;
import org.xcore.plugin.cloud.XCoreSender;

@Singleton
public class CloudPermissionPolicy {

    public boolean hasPermission(XCoreSender sender, String permission) {
        if (permission.isEmpty()) return true;
        if (!sender.isPlayer()) return true;

        if (permission.equalsIgnoreCase("admin") || permission.startsWith("xcore.admin")) {
            return sender.player().admin;
        }
        return true;
    }
}
