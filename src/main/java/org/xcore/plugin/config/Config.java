package org.xcore.plugin.config;

import mindustry.gen.Groups;
import org.xcore.plugin.model.enums.Feature;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Config {
    public String server = "server";
    public long discordChannelId = 0L;
    public boolean redisReclaimEnabled = true;
    public long redisReclaimMinIdleMs = 15000;
    public int redisReclaimBatch = 50;
    public boolean redisDlqEnabled = true;
    public int redisMaxDeliveryAttempts = 3;
    public String redisDlqPrefix = "xcore:dlq";
    public String redisUrl = "redis://127.0.0.1:6379";
    public String redisGroupPrefix = "xcore:cg";
    public String redisConsumerName = "xcore-node";
    public String publicHostOverride = null;
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
    public TranslationConfig translation = new TranslationConfig();
    public IpReputationConfig ipReputation = new IpReputationConfig();

    public void normalize() {
        if (disabledCommands == null) {
            disabledCommands = new HashSet<>();
        }

        if (disabledFeatures == null) {
            disabledFeatures = new HashSet<>();
        }

        if (translation == null) {
            translation = new TranslationConfig();
        }

        if (ipReputation == null) {
            ipReputation = new IpReputationConfig();
        }

        translation.normalize();
        ipReputation.normalize();
    }

    public static class TranslationConfig {
        public boolean enabled = true;
        public List<String> pipeline = new ArrayList<>(List.of("google"));
        public boolean preserveOriginalMessageOnFailure = true;
        public TranslationCacheConfig cache = new TranslationCacheConfig();
        public TranslationMetricsConfig metrics = new TranslationMetricsConfig();
        public LlmTranslationPolicyConfig llm = new LlmTranslationPolicyConfig();

        public void normalize() {
            if (pipeline == null || pipeline.isEmpty()) {
                pipeline = new ArrayList<>(List.of("google"));
            }

            if (cache == null) {
                cache = new TranslationCacheConfig();
            }

            if (metrics == null) {
                metrics = new TranslationMetricsConfig();
            }

            if (llm == null) {
                llm = new LlmTranslationPolicyConfig();
            }

            cache.normalize();
            metrics.normalize();
            llm.normalize();
        }
    }

    public static class TranslationCacheConfig {
        public boolean enabled = true;
        public int ttlSeconds = 1800;
        public int maxTextLength = 500;

        public void normalize() {
            if (ttlSeconds <= 0) {
                ttlSeconds = 1800;
            }

            if (maxTextLength <= 0) {
                maxTextLength = 500;
            }
        }
    }

    public static class TranslationMetricsConfig {
        public boolean enabled = true;
        public boolean minuteBucketsEnabled = true;
        public int minuteBucketTtlSeconds = 21600;

        public void normalize() {
            if (minuteBucketTtlSeconds <= 0) {
                minuteBucketTtlSeconds = 21600;
            }
        }
    }

    public static class LlmTranslationPolicyConfig {
        public boolean preserveFormattingTokens = true;
        public boolean structuredOutputRequired = true;
        public int maxInputChars = 500;
        public int maxOutputChars = 1200;
        public boolean stripControlCharacters = true;

        public void normalize() {
            if (maxInputChars <= 0) {
                maxInputChars = 500;
            }

            if (maxOutputChars <= 0) {
                maxOutputChars = 1200;
            }
        }
    }

    public static class IpReputationConfig {
        public boolean enabled = false;
        public boolean blockProxy = true;
        public boolean blockVpn = true;
        public boolean blockTor = true;
        public boolean blockHosting = false;
        public int cacheTtlSeconds = 3600;

        public void normalize() {
            if (cacheTtlSeconds <= 0) {
                cacheTtlSeconds = 3600;
            }
        }
    }

}
