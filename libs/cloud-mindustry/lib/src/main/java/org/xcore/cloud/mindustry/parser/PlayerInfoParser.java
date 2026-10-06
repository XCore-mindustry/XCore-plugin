package org.xcore.cloud.mindustry.parser;

import mindustry.Vars;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Administration;
import mindustry.net.Administration.PlayerInfo;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.caption.Caption;
import org.incendo.cloud.caption.CaptionVariable;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.exception.parsing.ParserException;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;
import org.xcore.cloud.mindustry.MindustryCommandManager;
import org.xcore.cloud.mindustry.MindustrySender;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Parses a player the server has a record of, online or not, from Mindustry's local
 * {@link Administration} state. In order:
 * <ol>
 *     <li>{@code #id} — the entity id of an online player;</li>
 *     <li>a UUID;</li>
 *     <li>a name the player has used, compared case-insensitively with and without color tags;</li>
 *     <li>an IP the player has connected from, for the console only.</li>
 * </ol>
 * A name or IP that matches more than one record is an error; the parser never picks one. The
 * argument is a single token, so a name containing spaces has to be given as a UUID or {@code #id}.
 * <p>
 * Whether the sender may act on the player is the command's business, not the parser's.
 */
public final class PlayerInfoParser<C> implements ArgumentParser<C, PlayerInfo>, BlockingSuggestionProvider.Strings<C> {

    private static final int MAX_SUGGESTIONS = 20;
    private static final Pattern IP_LIKE = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}|[0-9a-fA-F]*:[0-9a-fA-F:.]*");

    private final Supplier<Administration> administration;

    public PlayerInfoParser() {
        this(() -> Vars.netServer == null ? null : Vars.netServer.admins);
    }

    public PlayerInfoParser(@NonNull Supplier<Administration> administration) {
        this.administration = administration;
    }

    @Override
    public @NonNull ArgumentParseResult<PlayerInfo> parse(@NonNull CommandContext<C> context, @NonNull CommandInput input) {
        String token = input.readString();
        Administration admins = administration.get();
        if (admins == null) {
            return failure(Reason.NOT_FOUND, token, 0, context);
        }

        if (token.startsWith("#")) {
            Player player = arc.util.Strings.canParseInt(token.substring(1))
                    ? Groups.player.getByID(arc.util.Strings.parseInt(token.substring(1)))
                    : null;
            return player == null
                    ? failure(Reason.NOT_FOUND, token, 0, context)
                    : ArgumentParseResult.success(admins.getInfo(player.uuid()));
        }

        PlayerInfo byUuid = admins.getInfoOptional(token);
        if (byUuid != null) {
            return ArgumentParseResult.success(byUuid);
        }

        List<PlayerInfo> matches = new ArrayList<>();
        for (PlayerInfo info : admins.playerInfo.values()) {
            if (hasName(info, token)) matches.add(info);
        }

        if (matches.isEmpty() && IP_LIKE.matcher(token).matches()) {
            // Refused by the shape of the input alone, so a player cannot probe which IPs are known.
            if (!isConsole(context)) {
                return failure(Reason.IP_DENIED, token, 0, context);
            }
            for (PlayerInfo info : admins.playerInfo.values()) {
                if (token.equals(info.lastIP) || info.ips.contains(token, false)) matches.add(info);
            }
        }

        if (matches.size() == 1) {
            return ArgumentParseResult.success(matches.get(0));
        }
        return failure(matches.isEmpty() ? Reason.NOT_FOUND : Reason.AMBIGUOUS, token, matches.size(), context);
    }

    private static boolean hasName(PlayerInfo info, String name) {
        if (matchesName(info.lastName, name)) return true;
        for (String used : info.names) {
            if (matchesName(used, name)) return true;
        }
        return false;
    }

    private static boolean matchesName(String used, String name) {
        return used != null && (used.equalsIgnoreCase(name) || arc.util.Strings.stripColors(used).trim().equalsIgnoreCase(name));
    }

    private boolean isConsole(CommandContext<C> context) {
        MindustrySender sender = context.getOrDefault(MindustryCommandManager.MINDUSTRY_SENDER, null);
        if (sender == null && context.sender() instanceof MindustrySender direct) {
            sender = direct;
        }
        return sender != null && sender.isConsole();
    }

    private ArgumentParseResult<PlayerInfo> failure(Reason reason, String input, int count, CommandContext<C> context) {
        return ArgumentParseResult.failure(new PlayerInfoParseException(reason, input, count, context));
    }

    /**
     * Only the names of online players, which everyone can see anyway; the stored history is
     * never listed.
     */
    @Override
    public @NonNull Iterable<@NonNull String> stringSuggestions(@NonNull CommandContext<C> context, @NonNull CommandInput input) {
        List<String> names = new ArrayList<>();
        for (Player player : Groups.player) {
            if (names.size() >= MAX_SUGGESTIONS) break;
            String name = player.plainName();
            names.add(name.isEmpty() || name.contains(" ") ? "#" + player.id : name);
        }
        return names;
    }

    public enum Reason {
        NOT_FOUND(MindustryCaptionKeys.ARGUMENT_PARSE_FAILURE_PLAYER_INFO),
        AMBIGUOUS(MindustryCaptionKeys.ARGUMENT_PARSE_FAILURE_PLAYER_INFO_AMBIGUOUS),
        IP_DENIED(MindustryCaptionKeys.ARGUMENT_PARSE_FAILURE_PLAYER_INFO_IP_DENIED);

        private final Caption caption;

        Reason(Caption caption) {
            this.caption = caption;
        }
    }

    public static final class PlayerInfoParseException extends ParserException {

        private final Reason reason;
        private final String input;

        public PlayerInfoParseException(@NonNull Reason reason, @NonNull String input, int count,
                                        @NonNull CommandContext<?> context) {
            super(
                    PlayerInfoParser.class,
                    context,
                    reason.caption,
                    CaptionVariable.of("input", input),
                    CaptionVariable.of("count", String.valueOf(count))
            );
            this.reason = reason;
            this.input = input;
        }

        public @NonNull Reason reason() {
            return reason;
        }

        public @NonNull String input() {
            return input;
        }
    }
}
