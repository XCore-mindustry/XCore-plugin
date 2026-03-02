package org.xcore.plugin.command.controller.server;

import arc.files.Fi;
import arc.struct.Seq;
import arc.util.Log;
import com.google.gson.Gson;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import mindustry.net.Packets;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotations.*;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudServerController;
import org.xcore.plugin.command.transport.ToggleState;
import org.xcore.plugin.command.transport.TransportCutoverTarget;
import org.xcore.plugin.command.transport.TransportStage;
import org.xcore.plugin.common.PluginState;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.model.enums.Feature;
import org.xcore.plugin.service.NetworkService;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static mindustry.Vars.netServer;

@Singleton
public class MaintainController implements CloudServerController {
    private static final Set<String> PROTECTED_DISABLE_COMMANDS = Set.of(
            "disable-cmd",
            "enable-cmd",
            "disabled-cmds"
    );

    record GoNoGoDecision(boolean goDecision, List<String> criticalFailed, List<String> advisoryFailed, String nextAction) {
    }

    record StageGateDecision(TransportStage stage, boolean ready, List<String> blockingReasons) {
    }

    private final NetworkService network;
    private final PlayerDataRepository playerDataRepository;
    private final PluginState pluginState;
    private final GlobalConfig globalConfig;
    private final Config config;
    private final Fi configFile;
    private final Gson prettyGson;

    @Inject
    public MaintainController(NetworkService network,
                              PlayerDataRepository playerDataRepository,
                              PluginState pluginState,
                              GlobalConfig globalConfig,
                              Config config,
                              @Named("xcConfigFile") Fi configFile,
                              @Named("pretty") Gson prettyGson) {
        this.network = network;
        this.playerDataRepository = playerDataRepository;
        this.pluginState = pluginState;
        this.globalConfig = globalConfig;
        this.config = config;
        this.configFile = configFile;
        this.prettyGson = prettyGson;
    }

    @Command("exit")
    @CommandDescription("Terminates the server process immediately.")
    public void exit(XCoreSender sender) {
        Log.info("Shutting down server.");
        netServer.kickAll(Packets.KickReason.serverRestarting);
        System.exit(0);
    }

    @Command("sock-restart")
    @CommandDescription("Restarts the socket service connection.")
    public void sockRestart(XCoreSender sender) {
        network.disconnect();
        network.safeConnect();
        Log.info("NetSock restarted.");
    }

    @Command("transport-status")
    @CommandDescription("Prints current transport mode and backend status.")
    public void transportStatus(XCoreSender sender) {
        Log.info("Transport config: type=@, selection=@, backend=@", config.transportType, network.backendSelectionName(), network.backendName());
        Log.info("Redis flags: shadowPublish=@, consume=@, mutatingConsume=@, rpc=@, reclaim=@", config.redisShadowPublishEnabled,
                config.redisConsumeEnabled, config.redisMutatingConsumeEnabled, config.redisRpcEnabled, config.redisReclaimEnabled);
        Log.info("Redis endpoint/group: url=@, groupPrefix=@, consumer=@", config.redisUrl, config.redisGroupPrefix, config.redisConsumerName);
        Log.info("Redis reliability: dlqEnabled=@, maxAttempts=@, dlqPrefix=@, reclaimMinIdleMs=@, reclaimBatch=@",
                config.redisDlqEnabled, config.redisMaxDeliveryAttempts, config.redisDlqPrefix,
                config.redisReclaimMinIdleMs, config.redisReclaimBatch);
    }

    @Command("transport-canary-check")
    @CommandDescription("Prints readiness checks for staged Redis cutover.")
    public void transportCanaryCheck(XCoreSender sender) {
        var metrics = network.backendMetrics();
        long dlqRouted = metrics.getOrDefault("dlq_routed", metrics.getOrDefault("shadow.dlq_routed", 0L));
        long rpcTimeouts = metrics.getOrDefault("rpc_timeouts", metrics.getOrDefault("shadow.rpc_timeouts", 0L));
        long consumeFailures = metrics.getOrDefault("consume_failures", metrics.getOrDefault("shadow.consume_failures", 0L));

        boolean publishReady = config.redisShadowPublishEnabled;
        boolean readOnlyReady = config.redisShadowPublishEnabled && config.redisConsumeEnabled;
        boolean rpcReady = config.redisShadowPublishEnabled && config.redisConsumeEnabled && config.redisRpcEnabled;
        boolean mutatingReady = config.redisShadowPublishEnabled && config.redisConsumeEnabled && config.redisMutatingConsumeEnabled;
        boolean reclaimReady = config.redisReclaimEnabled && config.redisReclaimMinIdleMs > 0 && config.redisReclaimBatch > 0;
        boolean dlqHealthy = dlqRouted <= config.redisCanaryMaxDlqRouted;
        boolean rpcHealthy = rpcTimeouts <= config.redisCanaryMaxRpcTimeouts;
        boolean consumeHealthy = consumeFailures <= config.redisCanaryMaxConsumeFailures;

        Log.info("Canary readiness [publish]: @", publishReady ? "READY" : "NOT READY");
        Log.info("Canary readiness [read-only]: @", readOnlyReady ? "READY" : "NOT READY");
        Log.info("Canary readiness [rpc]: @", rpcReady ? "READY" : "NOT READY");
        Log.info("Canary readiness [mutating]: @", mutatingReady ? "READY" : "NOT READY");
        Log.info("Canary readiness [reclaim]: @", reclaimReady ? "READY" : "NOT READY");
        Log.info("Canary health [dlq]: @ (current=@, max=@)", dlqHealthy ? "OK" : "ALERT", dlqRouted, config.redisCanaryMaxDlqRouted);
        Log.info("Canary health [rpc timeouts]: @ (current=@, max=@)", rpcHealthy ? "OK" : "ALERT", rpcTimeouts, config.redisCanaryMaxRpcTimeouts);
        Log.info("Canary health [consume failures]: @ (current=@, max=@)", consumeHealthy ? "OK" : "ALERT", consumeFailures, config.redisCanaryMaxConsumeFailures);
        Log.info("Backend now: selection=@, backend=@", network.backendSelectionName(), network.backendName());
    }

    @Command("transport-go-no-go")
    @CommandDescription("Evaluates transport cutover gates and prints go/no-go decision.")
    public void transportGoNoGo(XCoreSender sender) {
        var decision = evaluateGoNoGo(config, network.backendSelectionName(), network.backendMetrics());
        Log.info("go_decision: @", decision.goDecision());
        Log.info("critical_failed: @", decision.criticalFailed().isEmpty() ? "[]" : decision.criticalFailed());
        Log.info("advisory_failed: @", decision.advisoryFailed().isEmpty() ? "[]" : decision.advisoryFailed());
        Log.info("next_action: @", decision.nextAction());
    }

    @Command("transport-stage-gate [stage]")
    @CommandDescription("Evaluates staged cutover gate for publish|read-only|rpc|mutating or all.")
    public void transportStageGate(XCoreSender sender,
                                   @Argument(value = "stage", description = "publish|read-only|rpc|mutating|all") @Default("all") TransportStage stage) {
        var metrics = network.backendMetrics();
        String backendSelection = network.backendSelectionName();

        if (stage == TransportStage.ALL) {
            for (TransportStage stageName : List.of(TransportStage.PUBLISH, TransportStage.READ_ONLY, TransportStage.RPC, TransportStage.MUTATING)) {
                var decision = evaluateStageGate(config, backendSelection, metrics, stageName);
                Log.info("stage_gate @: @", enumToken(stageName), decision.ready() ? "READY" : "BLOCKED");
                if (!decision.ready()) {
                    Log.info("stage_gate @ blockers: @", enumToken(stageName), decision.blockingReasons());
                }
            }
            return;
        }

        var decision = evaluateStageGate(config, backendSelection, metrics, stage);
        Log.info("stage_gate @: @", enumToken(decision.stage()), decision.ready() ? "READY" : "BLOCKED");
        Log.info("stage_gate @ blockers: @", enumToken(decision.stage()), decision.blockingReasons().isEmpty() ? "[]" : decision.blockingReasons());
    }

    @Command("transport-metrics")
    @CommandDescription("Prints transport backend metrics snapshot.")
    public void transportMetrics(XCoreSender sender) {
        var metrics = network.backendMetrics();
        if (metrics.isEmpty()) {
            Log.info("No metrics available for backend @", network.backendName());
            return;
        }
        metrics.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> Log.info("transport.metric @=@", entry.getKey(), entry.getValue()));
    }

    @Command("transport-reload")
    @CommandDescription("Reloads transport backend using current config.")
    public void transportReload(XCoreSender sender) {
        if (network.reloadBackend()) {
            Log.info("Transport backend reloaded: selection=@, backend=@", network.backendSelectionName(), network.backendName());
        } else {
            Log.err("Transport backend reload failed. Previous backend remains active.");
        }
    }

    @Command("transport-cutover <target> [state]")
    @CommandDescription("Toggles redis cutover targets: publish|read-only|mutating|rpc|reclaim|all.")
    public void transportCutover(XCoreSender sender,
                                 @Argument(value = "target", description = "publish|read-only|mutating|rpc|reclaim|all") TransportCutoverTarget target,
                                 @Argument(value = "state", description = "on/off") @Default("on") ToggleState state) {
        boolean enabled = state.enabled();

        switch (target) {
            case PUBLISH -> config.redisShadowPublishEnabled = enabled;
            case READ_ONLY -> config.redisConsumeEnabled = enabled;
            case MUTATING -> config.redisMutatingConsumeEnabled = enabled;
            case RPC -> config.redisRpcEnabled = enabled;
            case RECLAIM -> config.redisReclaimEnabled = enabled;
            case ALL -> {
                config.redisShadowPublishEnabled = enabled;
                config.redisConsumeEnabled = enabled;
                config.redisMutatingConsumeEnabled = enabled;
                config.redisRpcEnabled = enabled;
                config.redisReclaimEnabled = enabled;
            }
        }

        normalizeTransportFlags();
        saveConfig();
        transportReload(sender);
    }

    @Command("transport-redis-url <url>")
    @CommandDescription("Sets Redis URL and reloads transport backend.")
    public void transportRedisUrl(XCoreSender sender,
                                  @Argument(value = "url", description = "Redis URL") String url) {
        if (url == null || url.isBlank()) {
            Log.err("Redis URL cannot be empty.");
            return;
        }
        config.redisUrl = url.trim();
        saveConfig();
        transportReload(sender);
    }

    @Command("transport-redis-consumer <name> [groupPrefix]")
    @CommandDescription("Sets Redis consumer name and optional group prefix, then reloads backend.")
    public void transportRedisConsumer(XCoreSender sender,
                                       @Argument(value = "name", description = "Consumer name") String name,
                                       @Argument(value = "groupPrefix", description = "Group prefix") @Default("") String groupPrefix) {
        if (name == null || name.isBlank()) {
            Log.err("Redis consumer name cannot be empty.");
            return;
        }

        config.redisConsumerName = name.trim();
        if (groupPrefix != null && !groupPrefix.isBlank()) {
            config.redisGroupPrefix = groupPrefix.trim();
        }
        saveConfig();
        transportReload(sender);
    }

    @Command("transport-redis-reclaim <enabled> [minIdleMs] [batch]")
    @CommandDescription("Configures Redis reclaim loop and reloads backend.")
    public void transportRedisReclaim(XCoreSender sender,
                                      @Argument(value = "enabled", description = "on/off") ToggleState enabled,
                                      @Argument(value = "minIdleMs", description = "Minimum idle ms") @Default("-1") long minIdleMs,
                                      @Argument(value = "batch", description = "Batch size") @Default("-1") int batch) {
        config.redisReclaimEnabled = enabled.enabled();
        if (minIdleMs >= 0) {
            config.redisReclaimMinIdleMs = minIdleMs;
        }
        if (batch >= 0) {
            config.redisReclaimBatch = batch;
        }

        saveConfig();
        transportReload(sender);
    }

    @Command("transport-redis-reliability <dlqEnabled> [maxAttempts] [dlqPrefix]")
    @CommandDescription("Configures Redis DLQ and retry budget, then reloads backend.")
    public void transportRedisReliability(XCoreSender sender,
                                          @Argument(value = "dlqEnabled", description = "on/off") ToggleState dlqEnabled,
                                          @Argument(value = "maxAttempts", description = "Max delivery attempts") @Default("-1") int maxAttempts,
                                          @Argument(value = "dlqPrefix", description = "DLQ prefix") @Default("") String dlqPrefix) {
        config.redisDlqEnabled = dlqEnabled.enabled();
        if (maxAttempts > 0) {
            config.redisMaxDeliveryAttempts = maxAttempts;
        }
        if (dlqPrefix != null && !dlqPrefix.isBlank()) {
            config.redisDlqPrefix = dlqPrefix.trim();
        }

        saveConfig();
        transportReload(sender);
    }

    @Command("transport-canary-thresholds [dlqMax] [rpcTimeoutMax] [consumeFailMax]")
    @CommandDescription("Sets canary alert thresholds for transport metrics.")
    public void transportCanaryThresholds(XCoreSender sender,
                                          @Argument(value = "dlqMax", description = "Max dlq_routed") @Default("-1") long dlqMax,
                                          @Argument(value = "rpcTimeoutMax", description = "Max rpc_timeouts") @Default("-1") long rpcTimeoutMax,
                                          @Argument(value = "consumeFailMax", description = "Max consume_failures") @Default("-1") long consumeFailMax) {
        if (dlqMax >= 0) {
            config.redisCanaryMaxDlqRouted = dlqMax;
        }
        if (rpcTimeoutMax >= 0) {
            config.redisCanaryMaxRpcTimeouts = rpcTimeoutMax;
        }
        if (consumeFailMax >= 0) {
            config.redisCanaryMaxConsumeFailures = consumeFailMax;
        }

        saveConfig();
        Log.info("Canary thresholds updated: dlqMax=@, rpcTimeoutMax=@, consumeFailMax=@",
                config.redisCanaryMaxDlqRouted, config.redisCanaryMaxRpcTimeouts, config.redisCanaryMaxConsumeFailures);
    }

    @Command("gg-restart [state]")
    @CommandDescription("Toggles automatic server restart on Game Over.")
    public void ggRestart(XCoreSender sender,
                          @Argument(value = "state", description = "New state (on/off)") @Default("on") ToggleState state) {
        pluginState.restartOnGameOver = state.enabled();
        Log.info("GameOver restart turned @", pluginState.restartOnGameOver ? "on" : "off");
    }

    @Command("db-delete-bots")
    @CommandDescription("Deletes players with less than 2 minutes of playtime from the database.")
    public void deleteBots(XCoreSender sender) {
        long deleted = playerDataRepository.deleteBots();
        network.post(new SocketEvents.ReloadPlayerDataCache());
        Log.info("Deleted @ bots from database.", deleted);
    }

    @Command("gcmd <command> [targets]")
    @CommandDescription("Executes a command on remote servers via socket.")
    public void gcmd(XCoreSender sender,
                     @Argument(value = "command", description = "Command to execute. Use quotes for spaces.") String command,
                     @Argument(value = "targets", description = "Target server names") String[] targets,
                     @Flag(value = "except", description = "Invert targets (execute everywhere EXCEPT targets)") boolean except) {

        if (targets == null || targets.length == 0) {
            Log.info("Dispatching '@' to [ALL]", command);
            network.post(new SocketEvents.ExecuteCommand(command, new String[0], false));
            return;
        }

        if (except) {
            Log.info("Dispatching '@' to [ALL EXCEPT @]", command, Seq.with(targets));
        } else {
            Log.info("Dispatching '@' to @", command, Seq.with(targets));
        }

        network.post(new SocketEvents.ExecuteCommand(command, targets, except));
    }

    @Command("disable-cmd <command>")
    @CommandDescription("Disables a command or command path at runtime.")
    public void disableCmd(XCoreSender sender, @Argument("command") @Greedy String command) {
        String normalized = normalizeCommandName(command);
        if (normalized == null) {
            Log.err("Command name cannot be empty.");
            return;
        }

        String rootCommand = extractRootCommand(normalized);
        if (PROTECTED_DISABLE_COMMANDS.contains(rootCommand)) {
            Log.err("Command '@' cannot be disabled.", rootCommand);
            return;
        }

        Set<String> disabledCommands = mutableDisabledCommands();
        if (!disabledCommands.add(normalized)) {
            Log.info("Command '@' is already disabled.", normalized);
            return;
        }

        saveConfig();
        Log.info("Command '@' disabled.", normalized);
    }

    @Command("enable-cmd <command>")
    @CommandDescription("Re-enables a disabled command or command path.")
    public void enableCmd(XCoreSender sender, @Argument("command") @Greedy String command) {
        String normalized = normalizeCommandName(command);
        if (normalized == null) {
            Log.err("Command name cannot be empty.");
            return;
        }

        Set<String> disabledCommands = mutableDisabledCommands();
        if (!disabledCommands.remove(normalized)) {
            Log.info("Command '@' was not disabled.", normalized);
            return;
        }

        saveConfig();
        Log.info("Command '@' enabled.", normalized);
    }

    @Command("disabled-cmds")
    @CommandDescription("Lists all disabled commands.")
    public void disabledCmds(XCoreSender sender) {
        if (config.disabledCommands == null || config.disabledCommands.isEmpty()) {
            Log.info("No commands are disabled.");
            return;
        }

        Set<String> ordered = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        ordered.addAll(config.disabledCommands);
        Log.info("Disabled commands: @", String.join(", ", ordered));
    }

    private Set<String> mutableDisabledCommands() {
        if (config.disabledCommands == null) {
            config.disabledCommands = new HashSet<>();
        } else if (!(config.disabledCommands instanceof HashSet<?>)) {
            config.disabledCommands = new HashSet<>(config.disabledCommands);
        }
        return config.disabledCommands;
    }

    @Command("disable-feature <feature>")
    @CommandDescription("Disables a feature by key (e.g. rtv) at runtime.")
    public void disableFeature(XCoreSender sender, @Argument("feature") String featureKey) {
        var feature = Feature.fromKey(featureKey);
        if (feature.isEmpty()) {
            Log.err("Unknown feature '@'. Available: @", featureKey,
                    java.util.Arrays.stream(Feature.values()).map(Feature::key).collect(java.util.stream.Collectors.joining(", ")));
            return;
        }

        Set<String> disabledFeatures = mutableDisabledFeatures();
        if (!disabledFeatures.add(feature.get().key())) {
            Log.info("Feature '@' is already disabled.", feature.get().key());
            return;
        }

        saveConfig();
        Log.info("Feature '@' disabled.", feature.get().key());
    }

    @Command("enable-feature <feature>")
    @CommandDescription("Re-enables a disabled feature by key (e.g. rtv).")
    public void enableFeature(XCoreSender sender, @Argument("feature") String featureKey) {
        var feature = Feature.fromKey(featureKey);
        if (feature.isEmpty()) {
            Log.err("Unknown feature '@'. Available: @", featureKey,
                    java.util.Arrays.stream(Feature.values()).map(Feature::key).collect(java.util.stream.Collectors.joining(", ")));
            return;
        }

        Set<String> disabledFeatures = mutableDisabledFeatures();
        if (!disabledFeatures.remove(feature.get().key())) {
            Log.info("Feature '@' was not disabled.", feature.get().key());
            return;
        }

        saveConfig();
        Log.info("Feature '@' enabled.", feature.get().key());
    }

    @Command("disabled-features")
    @CommandDescription("Lists all disabled features.")
    public void disabledFeatures(XCoreSender sender) {
        if (config.disabledFeatures == null || config.disabledFeatures.isEmpty()) {
            Log.info("No features are disabled.");
            return;
        }

        var ordered = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        ordered.addAll(config.disabledFeatures);
        Log.info("Disabled features: @", String.join(", ", ordered));
    }

    private Set<String> mutableDisabledFeatures() {
        if (config.disabledFeatures == null) {
            config.disabledFeatures = new HashSet<>();
        } else if (!(config.disabledFeatures instanceof HashSet<?>)) {
            config.disabledFeatures = new HashSet<>(config.disabledFeatures);
        }
        return config.disabledFeatures;
    }

    private String extractRootCommand(String normalizedCommand) {
        return normalizedCommand.split(" ", 2)[0];
    }

    private String normalizeCommandName(String commandName) {
        if (commandName == null) {
            return null;
        }
        String normalized = commandName.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        normalized = normalized.replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }

    private void saveConfig() {
        configFile.writeString(prettyGson.toJson(config));
    }

    private String enumToken(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private void normalizeTransportFlags() {
        if (config.redisRpcEnabled) {
            config.redisConsumeEnabled = true;
            config.redisShadowPublishEnabled = true;
        }
        if (config.redisMutatingConsumeEnabled) {
            config.redisConsumeEnabled = true;
            config.redisShadowPublishEnabled = true;
        }
    }

    static GoNoGoDecision evaluateGoNoGo(Config config, String backendSelection, Map<String, Long> metrics) {
        List<String> critical = new ArrayList<>();
        List<String> advisory = new ArrayList<>();

        if (config.transportType == Config.TransportType.REDIS && "SOCK".equalsIgnoreCase(backendSelection)) {
            critical.add("redis transport requested but backend selection fell back to SOCK");
        }

        if (config.redisMutatingConsumeEnabled && !config.redisConsumeEnabled) {
            critical.add("mutating consume requires redisConsumeEnabled=true");
        }

        if (config.redisRpcEnabled && !(config.redisConsumeEnabled && config.redisShadowPublishEnabled)) {
            critical.add("rpc requires redisConsumeEnabled=true and redisShadowPublishEnabled=true");
        }

        if (config.redisReclaimEnabled && (config.redisReclaimMinIdleMs <= 0 || config.redisReclaimBatch <= 0)) {
            critical.add("reclaim enabled but reclaim parameters are invalid");
        }

        long dlqRouted = metric(metrics, "dlq_routed");
        if (config.redisDlqEnabled && dlqRouted > config.redisCanaryMaxDlqRouted) {
            critical.add("dlq_routed exceeded threshold");
        }

        long rpcTimeouts = metric(metrics, "rpc_timeouts");
        if (config.redisRpcEnabled && rpcTimeouts > config.redisCanaryMaxRpcTimeouts) {
            critical.add("rpc_timeouts exceeded threshold");
        }

        long consumeFailures = metric(metrics, "consume_failures");
        if (config.redisConsumeEnabled && consumeFailures > config.redisCanaryMaxConsumeFailures) {
            critical.add("consume_failures exceeded threshold");
        }

        long activeSubscribers = metric(metrics, "active_subscriber_threads");
        if (config.redisConsumeEnabled && activeSubscribers <= 0) {
            critical.add("redis consume enabled but active subscriber threads is zero");
        }

        if (metric(metrics, "tracked_failures") > 0) {
            advisory.add("tracked_failures > 0");
        }

        if (metric(metrics, "pending_rpc_contexts") > 0) {
            advisory.add("pending_rpc_contexts > 0");
        }

        if (metric(metrics, "publish_failures") > 0) {
            advisory.add("publish_failures > 0");
        }

        boolean go = critical.isEmpty();
        return new GoNoGoDecision(go, critical, advisory, go ? "proceed_cutover" : "hold_and_fix");
    }

    static StageGateDecision evaluateStageGate(Config config, String backendSelection, Map<String, Long> metrics, TransportStage stage) {
        List<String> blocking = new ArrayList<>();

        long publishFailures = metric(metrics, "publish_failures");
        long consumeFailures = metric(metrics, "consume_failures");
        long rpcTimeouts = metric(metrics, "rpc_timeouts");
        long dlqRouted = metric(metrics, "dlq_routed");
        long activeSubscribers = metric(metrics, "active_subscriber_threads");

        if ((config.transportType == Config.TransportType.REDIS || config.transportType == Config.TransportType.DUAL)
                && "SOCK".equalsIgnoreCase(backendSelection)) {
            blocking.add("backend selection is SOCK while redis transport mode is requested");
        }

        switch (stage) {
            case PUBLISH -> {
                if (!config.redisShadowPublishEnabled) {
                    blocking.add("redisShadowPublishEnabled=false");
                }
                if (publishFailures > 0) {
                    blocking.add("publish_failures > 0");
                }
            }
            case READ_ONLY -> {
                if (!config.redisShadowPublishEnabled) {
                    blocking.add("redisShadowPublishEnabled=false");
                }
                if (!config.redisConsumeEnabled) {
                    blocking.add("redisConsumeEnabled=false");
                }
                if (activeSubscribers <= 0) {
                    blocking.add("active_subscriber_threads <= 0");
                }
                if (consumeFailures > config.redisCanaryMaxConsumeFailures) {
                    blocking.add("consume_failures exceeded threshold");
                }
            }
            case RPC -> {
                if (!config.redisShadowPublishEnabled) {
                    blocking.add("redisShadowPublishEnabled=false");
                }
                if (!config.redisConsumeEnabled) {
                    blocking.add("redisConsumeEnabled=false");
                }
                if (!config.redisRpcEnabled) {
                    blocking.add("redisRpcEnabled=false");
                }
                if (activeSubscribers <= 0) {
                    blocking.add("active_subscriber_threads <= 0");
                }
                if (rpcTimeouts > config.redisCanaryMaxRpcTimeouts) {
                    blocking.add("rpc_timeouts exceeded threshold");
                }
            }
            case MUTATING -> {
                if (!config.redisShadowPublishEnabled) {
                    blocking.add("redisShadowPublishEnabled=false");
                }
                if (!config.redisConsumeEnabled) {
                    blocking.add("redisConsumeEnabled=false");
                }
                if (!config.redisMutatingConsumeEnabled) {
                    blocking.add("redisMutatingConsumeEnabled=false");
                }
                if (activeSubscribers <= 0) {
                    blocking.add("active_subscriber_threads <= 0");
                }
                if (config.redisDlqEnabled && dlqRouted > config.redisCanaryMaxDlqRouted) {
                    blocking.add("dlq_routed exceeded threshold");
                }
            }
            case ALL -> blocking.add("use explicit stage for gate evaluation");
        }

        return new StageGateDecision(stage, blocking.isEmpty(), blocking);
    }

    private static long metric(Map<String, Long> metrics, String key) {
        if (metrics.containsKey(key)) {
            return metrics.get(key);
        }
        if (metrics.containsKey("shadow." + key)) {
            return metrics.get("shadow." + key);
        }
        if (metrics.containsKey("primary." + key)) {
            return metrics.get("primary." + key);
        }
        return 0L;
    }
}
