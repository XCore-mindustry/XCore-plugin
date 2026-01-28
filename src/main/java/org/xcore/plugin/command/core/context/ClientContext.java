package org.xcore.plugin.command.core.context;

import mindustry.gen.Player;
import org.xcore.plugin.service.BundleService;

import java.util.Locale;
import java.util.Map;

public final class ClientContext extends CommandContext {

    private final Player player;
    private final BundleService bundleService;

    public ClientContext(Player player, String[] args, BundleService bundleService) {
        super(args);
        this.player = player;
        this.bundleService = bundleService;
    }

    public Player player() {
        return player;
    }

    public Locale locale() {
        return bundleService.locale(player);
    }

    public void send(String key, Map<String, Object> argsMap) {
        bundleService.send(player, key, argsMap);
    }

    public void send(String key) {
        send(key, Map.of());
    }

    public String format(String key, Map<String, Object> argsMap) {
        return bundleService.format(locale(), key, argsMap);
    }
}
