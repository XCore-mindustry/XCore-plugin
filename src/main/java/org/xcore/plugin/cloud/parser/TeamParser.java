package org.xcore.plugin.cloud.parser;

import mindustry.game.Team;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.cloud.exception.XCoreCommandException;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class TeamParser implements ArgumentParser<XCoreSender, Team>, BlockingSuggestionProvider.Strings<XCoreSender> {

    private final boolean allTeams;

    public TeamParser(boolean allTeams) {
        this.allTeams = allTeams;
    }

    @Override
    public @NonNull ArgumentParseResult<Team> parse(@NonNull CommandContext<XCoreSender> commandContext, @NonNull CommandInput commandInput) {
        String input = commandInput.readString().toLowerCase(Locale.ROOT);

        Team[] available = allTeams ? Team.all : Team.baseTeams;

        for (Team team : available) {
            if (team.name.equalsIgnoreCase(input)) {
                return ArgumentParseResult.success(team);
            }
        }

        try {
            int id = Integer.parseInt(input);
            if (id >= 0 && id < available.length) {
                Team found = Team.get(id);
                for (Team t : available) {
                    if (t == found) return ArgumentParseResult.success(found);
                }
            }
        } catch (NumberFormatException ignored) {}

        return ArgumentParseResult.failure(new XCoreCommandException("error-team-not-found"));
    }

    @Override
    public @NonNull Iterable<@NonNull String> stringSuggestions(@NonNull CommandContext<XCoreSender> commandContext, @NonNull CommandInput input) {
        Team[] available = allTeams ? Team.all : Team.baseTeams;

        return Arrays.stream(available)
                .map(t -> t.name)
                .collect(Collectors.toList());
    }
}
