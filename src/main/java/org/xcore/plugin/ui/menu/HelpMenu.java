package org.xcore.plugin.ui.menu;

import arc.util.CommandHandler;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import mindustry.Vars;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.help.HelpHandler;
import org.incendo.cloud.help.result.CommandEntry;
import org.xcore.cloud.mindustry.MindustryCloudCommand;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.plugin.cloud.CloudService;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.menu.help.HelpCategory;
import org.xcore.plugin.ui.menu.help.HelpCommandItem;
import org.xcore.plugin.ui.menu.help.HelpUiController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Singleton
public class HelpMenu extends Menu {

    private static final Set<String> ADMIN_COMMANDS = Set.of(
            "ban", "unban", "kick", "mute", "unmute", "warn", "kill", "spawn",
            "fillitems", "broadcast", "alert", "toast", "announcement", "a", "admin",
            "pause", "stop", "exit", "reload", "syncauth", "ipban", "whitelist", "host"
    );
    private static final Set<String> VOTE_COMMANDS = Set.of(
            "vote", "votekick", "rtv", "surrender", "skip", "vk"
    );
    private static final Set<String> SOCIAL_COMMANDS = Set.of(
            "msg", "m", "tell", "whisper", "r", "reply", "profile", "p",
            "settings", "badge", "badges", "ignore", "unignore", "discord", "t", "team", "g", "global"
    );
    private static final Set<String> GAME_COMMANDS = Set.of(
            "hub", "servers", "play", "maps", "map", "nominate", "sync",
            "top", "leaderboard", "stats", "rank", "spectate", "join"
    );

    private final Provider<CloudService> cloud;
    private final MenuService menuService;

    @Inject
    public HelpMenu(TomlSecretsConfig secretsConfig, SessionService sessionService, Provider<CloudService> cloud, MenuService menuService) {
        super(secretsConfig, sessionService);
        this.cloud = cloud;
        this.menuService = menuService;
    }

    public void open(Session session) {
        if (session == null || session.data == null) return;
        session.clear();
        XCoreSender sender = resolveSender(session);

        if (sender == null) {
            session.locale().send("error-internal");
            return;
        }

        var controller = new HelpUiController(session, this);
        var initialModel = controller.initialModel(null);

        if (initialModel.allCommands().isEmpty()) {
            session.locale().send("empty");
            return;
        }

        menuService.openUi(session, controller, initialModel, true);
    }

    public void help(XCoreSender sender, int page) {
        if (sender != null) {
            sender(sender);
            help(getUuid(sender), page);
        }
    }

    public void help(String uuid, int page) {
        Session session = sessionService.get(uuid);
        open(session);
    }

    public static HelpCategory resolveCategory(UnifiedCommand cmd) {
        String name = cmd.name().toLowerCase(Locale.ROOT);
        if (cmd.isCloudCommand() && cmd.primaryCloudEntry() != null) {
            var entry = cmd.primaryCloudEntry();
            var perm = entry.command().commandPermission();
            if (perm != null && perm.toString().toLowerCase(Locale.ROOT).contains("admin")) {
                return HelpCategory.ADMIN;
            }
        }
        if (ADMIN_COMMANDS.contains(name)) {
            return HelpCategory.ADMIN;
        }
        if (VOTE_COMMANDS.contains(name)) {
            return HelpCategory.VOTES;
        }
        if (SOCIAL_COMMANDS.contains(name)) {
            return HelpCategory.SOCIAL;
        }
        if (GAME_COMMANDS.contains(name)) {
            return HelpCategory.GAME;
        }
        return HelpCategory.GENERAL;
    }

    public List<HelpCommandItem.ArgumentInfo> extractArgumentInfos(Session session, UnifiedCommand cmd) {
        Map<String, HelpCommandItem.ArgumentInfo> argsByName = new LinkedHashMap<>();
        if (cmd.isCloudCommand() && cmd.primaryCloudEntry() != null) {
            var command = cmd.primaryCloudEntry().command();
            for (var comp : command.components()) {
                if (comp.type() == CommandComponent.ComponentType.LITERAL || comp.type() == CommandComponent.ComponentType.FLAG) continue;
                boolean required = comp.required();
                String name = comp.name();
                String key = "commands-" + cmd.name() + "-" + comp.name() + "-description";
                String desc = session.locale().t(key);
                if (desc.equals(key)) {
                    var compDesc = comp.description().textDescription();
                    desc = (!compDesc.isEmpty()) ? compDesc : session.locale().t("help-no-arg-description");
                }
                argsByName.putIfAbsent(name, new HelpCommandItem.ArgumentInfo(name, required, desc));
            }
        } else {
            for (var variant : cmd.legacyVariants()) {
                String params = variant.params();
                if (params != null && !params.isBlank()) {
                    for (String token : params.split("\\s+")) {
                        if (token.isBlank()) continue;
                        boolean required = token.startsWith("<") && token.endsWith(">");
                        String cleanName = token.replaceAll("[<>\\[\\]]", "");
                        String key = "commands-" + cmd.name() + "-" + cleanName + "-description";
                        String desc = session.locale().t(key);
                        if (desc.equals(key)) desc = session.locale().t("help-no-arg-description");
                        argsByName.putIfAbsent(cleanName, new HelpCommandItem.ArgumentInfo(cleanName, required, desc));
                    }
                }
            }
        }
        return new ArrayList<>(argsByName.values());
    }

    public List<HelpCommandItem> buildHelpCommandItems(Session session, XCoreSender sender) {
        boolean isAdmin = (session != null && session.player != null && session.player.admin)
                || (sender != null && (!sender.isPlayer() || (sender.player() != null && sender.player().admin)));

        List<UnifiedCommand> unified = collectAllCommands(sender);
        unified.sort(java.util.Comparator.comparing(UnifiedCommand::name));
        List<HelpCommandItem> items = new ArrayList<>();
        for (UnifiedCommand cmd : unified) {
            HelpCategory category = resolveCategory(cmd);
            boolean isAdminOnly = category == HelpCategory.ADMIN;
            if (isAdminOnly && !isAdmin) {
                continue; // Do not expose admin commands to regular players!
            }
            String rawDesc = resolveDescription(session, cmd);
            List<String> aliases = extractVisibleAliases(cmd);
            List<HelpCommandItem.ArgumentInfo> args = extractArgumentInfos(session, cmd);
            boolean hasNoRequiredArgs = args.stream().noneMatch(HelpCommandItem.ArgumentInfo::required);
            items.add(new HelpCommandItem(
                    cmd.name(),
                    category,
                    cmd.primarySyntax(),
                    cmd.syntaxes(),
                    aliases,
                    rawDesc,
                    args,
                    isAdminOnly,
                    hasNoRequiredArgs
            ));
        }
        return items;
    }

    public XCoreSender resolveSender(Session session) {
        if (session == null) return null;
        if (session.sender != null) return session.sender;
        if (session.player != null) {
            CloudService cloudService = cloud != null ? cloud.get() : null;
            if (cloudService != null && cloudService.getClientManager() != null) {
                var manager = cloudService.getClientManager();
                if (manager.senderMapper() != null) {
                    XCoreSender sender = manager.senderMapper().map(new MindustrySender.PlayerSender(session.player));
                    session.sender = sender;
                    return sender;
                }
            }
        }
        return null;
    }

    List<UnifiedCommand> collectAllCommands(XCoreSender sender) {
        Map<String, UnifiedCommandBuilder> commandMap = new LinkedHashMap<>();
        var handler = Vars.netServer.clientCommands;
        CloudService cloudService = cloud.get();
        HelpHandler<XCoreSender> helpHandler = cloudService.getHelpHandler();

        Set<String> cloudNames = new HashSet<>();
        helpHandler.queryRootIndex(sender).entries().forEach(entry -> {
            var root = entry.command().rootComponent();
            String rootName = root.name();
            String rootKey = rootName.toLowerCase(Locale.ROOT);

            cloudNames.add(rootKey);
            for (String alias : root.aliases()) {
                cloudNames.add(alias.toLowerCase(Locale.ROOT));
            }

            if (cloudService.isCommandDisabled(entry.command())) {
                return;
            }

            commandMap.computeIfAbsent(rootKey, ignored -> new UnifiedCommandBuilder(rootName))
                    .addVariant(CommandVariant.fromCloud(entry));
        });

        for (var cmd : handler.getCommandList()) {
            String nameLower = cmd.text.toLowerCase(Locale.ROOT);
            if (cmd instanceof MindustryCloudCommand<?> || cloudNames.contains(nameLower) || cloudService.isCommandDisabled(cmd.text)) {
                continue;
            }

            commandMap.computeIfAbsent(nameLower, ignored -> new UnifiedCommandBuilder(cmd.text))
                    .addVariant(CommandVariant.fromLegacy(cmd));
        }

        return new ArrayList<>(commandMap.values().stream().map(UnifiedCommandBuilder::build).toList());
    }

    String resolveDescription(Session session, UnifiedCommand cmd) {
        String key = "commands-" + cmd.name() + "-description";
        String res = session.locale().t(key);
        if (!res.equals(key)) return res;
        return (cmd.rawDescription() != null && !cmd.rawDescription().isEmpty()) ? cmd.rawDescription() : session.locale().t("help-no-description");
    }

    List<String> extractVisibleAliases(UnifiedCommand cmd) {
        CommandEntry<XCoreSender> entry = cmd.primaryCloudEntry();
        if (entry == null) return List.of();

        Set<String> uniqueAliases = new LinkedHashSet<>();
        for (String alias : entry.command().rootComponent().alternativeAliases()) {
            if (!alias.equalsIgnoreCase(cmd.name())) {
                uniqueAliases.add(alias);
            }
        }
        return new ArrayList<>(uniqueAliases);
    }

    public record UnifiedCommand(String name, List<CommandVariant> variants) {
        public List<String> syntaxes() {
            return variants.stream().map(CommandVariant::syntax).toList();
        }

        public String primarySyntax() {
            return variants.isEmpty() ? name : variants.getFirst().syntax();
        }

        public String rawDescription() {
            for (CommandVariant variant : variants) {
                if (variant.rawDescription() != null && !variant.rawDescription().isBlank()) return variant.rawDescription();
            }
            return "";
        }

        public boolean isCloudCommand() {
            return variants.stream().anyMatch(CommandVariant::isCloud);
        }

        public List<CommandVariant> cloudVariants() {
            return variants.stream().filter(CommandVariant::isCloud).toList();
        }

        public List<CommandVariant> legacyVariants() {
            return variants.stream().filter(variant -> !variant.isCloud()).toList();
        }

        public CommandEntry<XCoreSender> primaryCloudEntry() {
            for (CommandVariant variant : variants) {
                if (variant.cloudEntry() != null) return variant.cloudEntry();
            }
            return null;
        }
    }

    public record CommandVariant(String syntax, String rawDescription, CommandEntry<XCoreSender> cloudEntry,
                                 CommandHandler.Command legacyCommand) {
        static CommandVariant fromCloud(CommandEntry<XCoreSender> entry) {
            String clean = entry.syntax().replaceAll("^/+", "").trim();
            return new CommandVariant(clean, extractDesc(entry), entry, null);
        }

        static CommandVariant fromLegacy(CommandHandler.Command cmd) {
            String clean = (cmd.text + (cmd.paramText.isEmpty() ? "" : " " + cmd.paramText)).replaceAll("^/+", "").trim();
            return new CommandVariant(clean, cmd.description, null, cmd);
        }

        public boolean isCloud() {
            return cloudEntry != null;
        }

        public String params() {
            return legacyCommand != null ? legacyCommand.paramText : "";
        }

        private static String extractDesc(CommandEntry<XCoreSender> entry) {
            var d = entry.command().rootComponent().description();
            if (!d.isEmpty()) return d.textDescription();
            var cd = entry.command().commandDescription().description();
            return !cd.isEmpty() ? cd.textDescription() : "";
        }
    }

    private static final class UnifiedCommandBuilder {
        private final String name;
        private final Map<String, CommandVariant> variantsBySyntax = new LinkedHashMap<>();

        private UnifiedCommandBuilder(String name) {
            this.name = name;
        }

        private UnifiedCommandBuilder addVariant(CommandVariant variant) {
            variantsBySyntax.putIfAbsent(variant.syntax(), variant);
            return this;
        }

        private UnifiedCommand build() {
            return new UnifiedCommand(name, new ArrayList<>(variantsBySyntax.values()));
        }
    }
}
