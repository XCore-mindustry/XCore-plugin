package org.xcore.cloud.mindustry.parser;

import mindustry.game.Team;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.caption.CaptionVariable;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.exception.parsing.ParserException;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a {@link Team} by name (case-insensitive) or numeric id.
 * <p>
 * By default only the base teams (derelict, sharded, crux, malis, green, blue) are accepted;
 * {@code allTeams} opens up all 256 team ids.
 */
public final class TeamParser<C> implements ArgumentParser<C, Team>, BlockingSuggestionProvider.Strings<C> {

    private final boolean allTeams;

    public TeamParser(boolean allTeams) {
        this.allTeams = allTeams;
    }

    @Override
    public @NonNull ArgumentParseResult<Team> parse(@NonNull CommandContext<C> context, @NonNull CommandInput input) {
        String token = input.readString();
        Team[] available = allTeams ? Team.all : Team.baseTeams;

        for (Team team : available) {
            if (team != null && team.name.equalsIgnoreCase(token)) {
                return ArgumentParseResult.success(team);
            }
        }

        try {
            int id = Integer.parseInt(token);
            if (id >= 0 && id < Team.all.length) {
                Team team = Team.get(id);
                if (allTeams || team.id < Team.baseTeams.length) {
                    return ArgumentParseResult.success(team);
                }
            }
        } catch (NumberFormatException ignored) {
        }

        return ArgumentParseResult.failure(new TeamParseException(token, context));
    }

    @Override
    public @NonNull Iterable<@NonNull String> stringSuggestions(@NonNull CommandContext<C> context, @NonNull CommandInput input) {
        Team[] available = allTeams ? Team.all : Team.baseTeams;
        List<String> names = new ArrayList<>(available.length);
        for (Team team : available) {
            if (team != null) {
                names.add(team.name);
            }
        }
        return names;
    }

    public static final class TeamParseException extends ParserException {

        private final String input;

        public TeamParseException(@NonNull String input, @NonNull CommandContext<?> context) {
            super(TeamParser.class, context, MindustryCaptionKeys.ARGUMENT_PARSE_FAILURE_TEAM, CaptionVariable.of("input", input));
            this.input = input;
        }

        public @NonNull String input() {
            return input;
        }
    }
}
