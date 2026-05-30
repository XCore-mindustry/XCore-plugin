package org.xcore.plugin.config;

import arc.files.Fi;

/**
 * Writes commented default TOML templates for {@code xcore.toml} and
 * {@code secrets.toml}.
 *
 * <p>Templates are maintained as inline strings rather than generated from
 * the typed DTOs because Jackson TOML writer output is not ideal for
 * hand-maintained comments and section ordering.</p>
 *
 * <p>All secret values are written as empty strings or safe defaults;
 * no credentials are embedded in the templates.</p>
 */
public final class ConfigTomlTemplateWriter {

    private ConfigTomlTemplateWriter() {
    }

    /**
     * Writes the default {@code xcore.toml} template to {@code target}.
     *
     * @param target the file to write
     */
    public static void writeDefaultXcoreToml(Fi target) {
        target.writeString(defaultXcoreTomlContent());
    }

    /**
     * Writes the default {@code secrets.toml} template to {@code target}.
     *
     * @param target the file to write
     */
    public static void writeDefaultSecretsToml(Fi target) {
        target.writeString(defaultSecretsTomlContent());
    }

    /**
     * Returns the full default content for {@code xcore.toml}.
     *
     * @return commented TOML string matching the target schema
     */
    public static String defaultXcoreTomlContent() {
        return """
        # XCore server-local configuration
        # Restart required for changes to take effect.
        version = 1

        [server]
        # Server identity used by transport and mode helpers.
        name = "server"
        # Public host/IP override (blank = auto-detect).
        public_host_override = ""
        player_limit = 30
        console_enabled = true
        game_started_timer = true

        [paths]
        # Directory for shared secrets.toml. Blank defaults to user home.
        global_config_directory = ""

        [discord]
        channel_id = 0

        [transport.redis]
        url = "redis://127.0.0.1:6379"
        group_prefix = "xcore:cg"
        consumer_name = "xcore-node"

        [transport.redis.reclaim]
        enabled = true
        min_idle_ms = 15000
        batch = 50

        [transport.redis.dlq]
        enabled = true
        max_delivery_attempts = 3
        prefix = "xcore:dlq"

        [runtime]
        disabled_commands = []
        disabled_features = []

        [event_hub]
        enabled = false
        map_id = ""

        [translation]
        enabled = true
        pipeline = ["google"]
        preserve_original_message_on_failure = true

        [translation.cache]
        enabled = true
        ttl_seconds = 1800
        max_text_length = 500

        [translation.metrics]
        enabled = true
        minute_buckets_enabled = true
        minute_bucket_ttl_seconds = 21600

        [translation.llm]
        preserve_formatting_tokens = true
        structured_output_required = true
        max_input_chars = 500
        max_output_chars = 1200
        strip_control_characters = true

        [ip_reputation]
        enabled = false
        block_proxy = true
        block_vpn = true
        block_tor = true
        block_hosting = false
        cache_ttl_seconds = 3600
        """;
    }

    /**
     * Returns the full default content for {@code secrets.toml}.
     *
     * @return commented TOML string matching the target schema
     */
    public static String defaultSecretsTomlContent() {
        return """
        # XCore shared secrets and global configuration
        # Keep this file secure. Restart required for changes.
        version = 1

        [database]
        # Required: MongoDB connection string.
        mongo_connection_string = ""
        # Required: MongoDB database name.
        name = ""
        read_only = false
        migration_enabled = false

        [external_links]
        discord_url = "https://discord.gg/RUMCCa9QAC"
        github_url = "https://github.com/XCore-mindustry/"
        donatello_url = "https://donatello.to/xcore"
        weblate_url = "https://xcore.eradication.fun/"
        discord_red_vs_blue_url = "https://discord.gg/UdnuFetcNt"

        [moderation.votekick]
        min_play_time_minutes = 60
        ban_duration_minutes = 30
        vote_duration_seconds = 60.0

        [chat.global]
        min_play_time_minutes = 240

        [maps.voting]
        switch_delay_seconds = 10

        [pagination]
        events_per_page = 10
        maps_per_page = 10
        commands_per_page = 6
        private_messages_per_page = 10

        [messages.history]
        max_history = 16

        [messages.private]
        max_length = 300
        cooldown_seconds = 10
        unread_limit = 30
        blocked_limit = 100

        [translation.providers.google]
        type = "google"
        enabled = true
        # Provider API key (keep secret).
        api_key = ""
        base_url = "https://api.openai.com/v1"
        model = "gpt-5.4"
        api_mode = ""
        organization = ""
        project = ""
        timeout_seconds = 15
        max_retries = 1
        temperature = 0.0
        supported_languages = []

        [ip_reputation.provider]
        base_url = "http://ip-api.com/json"
        timeout_seconds = 10
        max_retries = 2
        rate_limit_per_minute = 45
        """;
    }
}
