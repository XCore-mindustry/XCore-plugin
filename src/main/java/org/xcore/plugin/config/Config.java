package org.xcore.plugin.config;

import mindustry.gen.Groups;
import org.xcore.plugin.model.enums.Feature;

import java.util.HashSet;
import java.util.Set;

public class Config {
    public String server = "server";
    public long discordChannelId = 0L;
    public TransportType transportType = TransportType.SOCK;
    public NodeRole nodeRole = NodeRole.AUTO;
    public SockType sockType = SockType.CLIENT;
    public boolean redisShadowPublishEnabled = false;
    public boolean redisConsumeEnabled = false;
    public boolean redisMutatingConsumeEnabled = false;
    public boolean redisRpcEnabled = false;
    public boolean redisReclaimEnabled = true;
    public long redisReclaimMinIdleMs = 15000;
    public int redisReclaimBatch = 50;
    public boolean redisDlqEnabled = true;
    public int redisMaxDeliveryAttempts = 3;
    public String redisDlqPrefix = "xcore:dlq";
    public long redisCanaryMaxDlqRouted = 0;
    public long redisCanaryMaxRpcTimeouts = 0;
    public long redisCanaryMaxConsumeFailures = 0;
    public String redisUrl = "redis://127.0.0.1:6379";
    public String redisGroupPrefix = "xcore:cg";
    public String redisConsumerName = "xcore-node";
    public boolean consoleEnabled = true;

    public int playerLimit = 30;
    public String globalConfigDirectory = null;
    public boolean gameStartedTimer = true;
    public Set<String> disabledCommands = new HashSet<>();
    public Set<String> disabledFeatures = new HashSet<>();

    public boolean isFeatureDisabled(Feature feature) {
        return disabledFeatures != null && disabledFeatures.contains(feature.key());
    }

    public int getNoAdminPlayerLimit() {
        return this.playerLimit + Groups.player.count(p -> p.admin);
    }

    public boolean isMiniPvP() {
        return server.equals("mini-pvp");
    }

    public boolean isMiniHexed() {
        return server.equals("mini-hexed");
    }

    public boolean isLastStanding() {
        return server.equals("the-last-standing");
    }

    public boolean isEvent() {
        return server.equals("event");
    }

    public boolean isEventHubMap = false;
    public String eventHubMapID = "";

    public enum SockType {
        CLIENT, SERVER
    }

    public enum TransportType {
        SOCK, DUAL, REDIS
    }

    public enum NodeRole {
        PRIMARY, WORKER, AUTO
    }
}
