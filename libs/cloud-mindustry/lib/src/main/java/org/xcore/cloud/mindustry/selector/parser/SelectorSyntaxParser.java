package org.xcore.cloud.mindustry.selector.parser;

import mindustry.Vars;
import mindustry.game.Team;
import mindustry.type.UnitType;
import org.xcore.cloud.mindustry.selector.SelectorKind;
import org.xcore.cloud.mindustry.selector.SortOrder;
import org.xcore.cloud.mindustry.selector.TargetSelectorSpec;
import org.xcore.cloud.mindustry.selector.exception.SelectorSyntaxException;

public final class SelectorSyntaxParser {

    private SelectorSyntaxParser() {}

    public static TargetSelectorSpec parse(String input) {
        if (input == null || input.isBlank()) {
            throw new SelectorSyntaxException(input == null ? "" : input, "Empty selector input", 0);
        }

        String trimmed = input.trim();
        if (!trimmed.startsWith("@")) {
            // Literal player identifier (ID or name)
            return new TargetSelectorSpec(
                    SelectorKind.LITERAL_PLAYER,
                    trimmed,
                    null,
                    false,
                    null,
                    false,
                    0f,
                    Float.MAX_VALUE,
                    Float.NaN,
                    Float.NaN,
                    false,
                    false,
                    null,
                    null,
                    false,
                    1,
                    SortOrder.ARBITRARY
            );
        }

        SelectorScanner scanner = new SelectorScanner(trimmed);
        scanner.require('@', "Expected '@'");

        char kindChar = scanner.next();
        SelectorKind kind = switch (kindChar) {
            case 'a' -> SelectorKind.ALL_PLAYERS;
            case 'p' -> SelectorKind.NEAREST_PLAYER;
            case 's' -> SelectorKind.SELF;
            case 'r' -> SelectorKind.RANDOM_PLAYER;
            case 'e' -> SelectorKind.ALL_ENTITIES;
            default -> throw scanner.error("Unknown selector kind '@" + kindChar + "'");
        };

        scanner.skipWhitespace();

        Team team = null;
        boolean invertTeam = false;
        UnitType unitType = null;
        boolean invertType = false;
        float distanceMin = 0f;
        float distanceMax = Float.MAX_VALUE;
        float originX = Float.NaN;
        float originY = Float.NaN;
        boolean relativeX = false;
        boolean relativeY = false;
        Boolean admin = null;
        String name = null;
        boolean invertName = false;
        int limit = kind.defaultMultiple() ? (kind == SelectorKind.ALL_ENTITIES ? TargetSelectorSpec.DEFAULT_UNIT_LIMIT : TargetSelectorSpec.DEFAULT_PLAYER_LIMIT) : 1;
        SortOrder sort = switch (kind) {
            case NEAREST_PLAYER -> SortOrder.NEAREST;
            case RANDOM_PLAYER -> SortOrder.RANDOM;
            default -> SortOrder.ARBITRARY;
        };

        if (scanner.expect('[')) {
            while (scanner.hasRemaining()) {
                scanner.skipWhitespace();
                if (scanner.expect(']')) {
                    break;
                }

                String key = scanner.parseIdentifier().toLowerCase();
                scanner.require('=', "Expected '=' after key '" + key + "'");

                switch (key) {
                    case "team" -> {
                        String val = scanner.parseValue();
                        invertTeam = val.startsWith("!");
                        String teamName = invertTeam ? val.substring(1) : val;
                        team = resolveTeam(teamName, trimmed, scanner.cursor());
                    }
                    case "type" -> {
                        String val = scanner.parseValue();
                        invertType = val.startsWith("!");
                        String typeName = invertType ? val.substring(1) : val;
                        unitType = resolveUnitType(typeName, trimmed, scanner.cursor());
                    }
                    case "distance", "r", "radius" -> {
                        String val = scanner.parseValue();
                        float[] range = parseDistanceRange(val, trimmed, scanner.cursor());
                        distanceMin = range[0];
                        distanceMax = range[1];
                    }
                    case "limit", "c" -> {
                        limit = scanner.parseInt();
                        if (limit <= 0) {
                            throw scanner.error("Limit must be greater than zero");
                        }
                    }
                    case "sort" -> {
                        String val = scanner.parseValue();
                        try {
                            sort = SortOrder.fromString(val);
                        } catch (IllegalArgumentException e) {
                            throw scanner.error("Unknown sort order: " + val);
                        }
                    }
                    case "admin" -> {
                        String val = scanner.parseValue();
                        if (val.equalsIgnoreCase("true")) {
                            admin = true;
                        } else if (val.equalsIgnoreCase("false")) {
                            admin = false;
                        } else {
                            throw scanner.error("Expected 'true' or 'false' for admin filter: '" + val + "'");
                        }
                    }
                    case "name" -> {
                        String val = scanner.parseValue();
                        invertName = val.startsWith("!");
                        name = invertName ? val.substring(1) : val;
                    }
                    case "x" -> {
                        scanner.skipWhitespace();
                        if (scanner.peek() == '~') {
                            scanner.next();
                            relativeX = true;
                            originX = scanner.hasRemaining() && (scanner.peek() == '+' || scanner.peek() == '-' || Character.isDigit(scanner.peek()))
                                    ? scanner.parseFloat() * Vars.tilesize
                                    : 0f;
                        } else {
                            relativeX = false;
                            originX = scanner.parseFloat() * Vars.tilesize;
                        }
                    }
                    case "y" -> {
                        scanner.skipWhitespace();
                        if (scanner.peek() == '~') {
                            scanner.next();
                            relativeY = true;
                            originY = scanner.hasRemaining() && (scanner.peek() == '+' || scanner.peek() == '-' || Character.isDigit(scanner.peek()))
                                    ? scanner.parseFloat() * Vars.tilesize
                                    : 0f;
                        } else {
                            relativeY = false;
                            originY = scanner.parseFloat() * Vars.tilesize;
                        }
                    }
                    default -> throw scanner.error("Unknown selector argument key: '" + key + "'");
                }

                scanner.skipWhitespace();
                if (scanner.expect(',')) {
                    continue;
                }
                if (scanner.expect(']')) {
                    break;
                }
                throw scanner.error("Expected ',' or ']'");
            }
        }

        scanner.skipWhitespace();
        if (scanner.hasRemaining()) {
            throw scanner.error("Unexpected trailing characters after selector");
        }

        return new TargetSelectorSpec(
                kind,
                null,
                team,
                invertTeam,
                unitType,
                invertType,
                distanceMin,
                distanceMax,
                originX,
                originY,
                relativeX,
                relativeY,
                admin,
                name,
                invertName,
                limit,
                sort
        );
    }

    private static Team resolveTeam(String name, String input, int cursor) {
        if (name.isEmpty()) {
            throw new SelectorSyntaxException(input, "Team name cannot be empty", cursor);
        }
        for (Team t : Team.baseTeams) {
            if (t.name.equalsIgnoreCase(name)) {
                return t;
            }
        }
        for (Team t : Team.all) {
            if (t != null && t.name.equalsIgnoreCase(name)) {
                return t;
            }
        }
        try {
            int id = Integer.parseInt(name);
            Team t = Team.get(id);
            if (t != null) {
                return t;
            }
        } catch (NumberFormatException ignored) {}

        throw new SelectorSyntaxException(input, "Unknown team: '" + name + "'", cursor);
    }

    private static UnitType resolveUnitType(String name, String input, int cursor) {
        if (name.isEmpty()) {
            throw new SelectorSyntaxException(input, "Unit type name cannot be empty", cursor);
        }
        UnitType found = Vars.content.unit(name);
        if (found != null) {
            return found;
        }
        // Try fuzzy or lowercase
        for (UnitType ut : Vars.content.units()) {
            if (ut.name.equalsIgnoreCase(name)) {
                return ut;
            }
        }
        throw new SelectorSyntaxException(input, "Unknown unit type: '" + name + "'", cursor);
    }

    private static float[] parseDistanceRange(String val, String input, int cursor) {
        if (val.isEmpty()) {
            throw new SelectorSyntaxException(input, "Distance range cannot be empty", cursor);
        }
        try {
            if (val.startsWith("..")) {
                float max = Float.parseFloat(val.substring(2)) * Vars.tilesize;
                return new float[]{0f, max};
            } else if (val.endsWith("..")) {
                float min = Float.parseFloat(val.substring(0, val.length() - 2)) * Vars.tilesize;
                return new float[]{min, Float.MAX_VALUE};
            } else if (val.contains("..")) {
                int dotIdx = val.indexOf("..");
                float min = Float.parseFloat(val.substring(0, dotIdx)) * Vars.tilesize;
                float max = Float.parseFloat(val.substring(dotIdx + 2)) * Vars.tilesize;
                if (min > max) {
                    throw new SelectorSyntaxException(input, "Invalid distance range: min (" + min / Vars.tilesize + ") cannot exceed max (" + max / Vars.tilesize + ")", cursor);
                }
                return new float[]{min, max};
            } else {
                float max = Float.parseFloat(val) * Vars.tilesize;
                return new float[]{0f, max};
            }
        } catch (NumberFormatException e) {
            throw new SelectorSyntaxException(input, "Malformed distance value: '" + val + "'", cursor);
        }
    }
}
