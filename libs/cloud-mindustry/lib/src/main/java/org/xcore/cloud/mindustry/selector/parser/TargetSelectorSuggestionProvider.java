package org.xcore.cloud.mindustry.selector.parser;

import arc.util.Strings;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.type.UnitType;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;

import java.util.ArrayList;
import java.util.List;

public interface TargetSelectorSuggestionProvider<C> extends BlockingSuggestionProvider.Strings<C> {

    List<String> ROOT_SELECTORS = List.of("@a", "@p", "@s", "@r", "@e");
    List<String> BASE_KEYS = List.of("team=", "distance=", "limit=", "sort=", "admin=", "name=");
    List<String> ENTITY_KEYS = List.of("type=", "team=", "distance=", "limit=", "sort=", "name=");
    List<String> SORT_VALUES = List.of("nearest", "furthest", "random", "health_asc", "health_desc");

    @Override
    default @NonNull Iterable<@NonNull String> stringSuggestions(
            @NonNull CommandContext<C> context,
            @NonNull CommandInput input
    ) {
        String token = input.peekString();
        List<String> suggestions = new ArrayList<>();

        if (token.isEmpty() || (!token.startsWith("@") && !token.startsWith("#"))) {
            for (String sel : ROOT_SELECTORS) {
                if (sel.startsWith(token)) {
                    suggestions.add(sel);
                }
            }
            if (Groups.player != null) {
                Groups.player.each(p -> {
                    String clean = arc.util.Strings.stripColors(p.plainName());
                    if (clean.toLowerCase().startsWith(token.toLowerCase())) {
                        suggestions.add(clean.contains(" ") ? "\"" + clean + "\"" : clean);
                    }
                    String idStr = "#" + p.id;
                    if (idStr.startsWith(token)) {
                        suggestions.add(idStr);
                    }
                });
            }
            return suggestions;
        }

        if (token.startsWith("#")) {
            if (Groups.player != null) {
                Groups.player.each(p -> {
                    String idStr = "#" + p.id;
                    if (idStr.startsWith(token)) {
                        suggestions.add(idStr);
                    }
                });
            }
            return suggestions;
        }

        if (!token.contains("[")) {
            for (String selector : ROOT_SELECTORS) {
                if (selector.startsWith(token)) {
                    suggestions.add(selector);
                    suggestions.add(selector + "[");
                }
            }
            return suggestions;
        }

        int openBracket = token.indexOf('[');
        String prefix = token.substring(0, openBracket + 1);
        String inside = token.substring(openBracket + 1);

        int lastComma = inside.lastIndexOf(',');
        String existingProps = lastComma == -1 ? "" : inside.substring(0, lastComma + 1);
        String activeTerm = lastComma == -1 ? inside : inside.substring(lastComma + 1);

        List<String> availableKeys = token.startsWith("@e") ? ENTITY_KEYS : BASE_KEYS;

        if (!activeTerm.contains("=")) {
            for (String key : availableKeys) {
                if (key.startsWith(activeTerm)) {
                    suggestions.add(prefix + existingProps + key);
                }
            }
        } else {
            int eq = activeTerm.indexOf('=');
            String key = activeTerm.substring(0, eq + 1);
            String val = activeTerm.substring(eq + 1);

            switch (key) {
                case "team=" -> {
                    for (Team t : Team.baseTeams) {
                        if (t.name.startsWith(val)) {
                            suggestions.add(prefix + existingProps + key + t.name);
                            suggestions.add(prefix + existingProps + key + t.name + "]");
                            suggestions.add(prefix + existingProps + key + t.name + ",");
                        }
                    }
                }
                case "type=" -> {
                    if (Vars.content != null) {
                        for (UnitType u : Vars.content.units()) {
                            if (u.name.startsWith(val)) {
                                suggestions.add(prefix + existingProps + key + u.name);
                                suggestions.add(prefix + existingProps + key + u.name + "]");
                                suggestions.add(prefix + existingProps + key + u.name + ",");
                            }
                        }
                    }
                }
                case "sort=" -> {
                    for (String s : SORT_VALUES) {
                        if (s.startsWith(val)) {
                            suggestions.add(prefix + existingProps + key + s);
                            suggestions.add(prefix + existingProps + key + s + "]");
                        }
                    }
                }
                case "admin=" -> {
                    if ("true".startsWith(val)) suggestions.add(prefix + existingProps + key + "true]");
                    if ("false".startsWith(val)) suggestions.add(prefix + existingProps + key + "false]");
                }
            }
        }

        return suggestions;
    }
}
