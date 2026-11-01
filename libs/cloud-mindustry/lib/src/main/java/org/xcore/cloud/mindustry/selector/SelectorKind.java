package org.xcore.cloud.mindustry.selector;

public enum SelectorKind {
    ALL_PLAYERS("@a", true, false),
    NEAREST_PLAYER("@p", false, false),
    SELF("@s", false, false),
    RANDOM_PLAYER("@r", false, false),
    ALL_ENTITIES("@e", true, true),
    LITERAL_PLAYER("", false, false);

    private final String token;
    private final boolean defaultMultiple;
    private final boolean entityAllowed;

    SelectorKind(String token, boolean defaultMultiple, boolean entityAllowed) {
        this.token = token;
        this.defaultMultiple = defaultMultiple;
        this.entityAllowed = entityAllowed;
    }

    public String token() {
        return token;
    }

    public boolean defaultMultiple() {
        return defaultMultiple;
    }

    public boolean entityAllowed() {
        return entityAllowed;
    }

    public static SelectorKind parseKind(String input) {
        if (!input.startsWith("@") || input.length() < 2) {
            return LITERAL_PLAYER;
        }
        String prefix = input.substring(0, 2);
        return switch (prefix) {
            case "@a" -> ALL_PLAYERS;
            case "@p" -> NEAREST_PLAYER;
            case "@s" -> SELF;
            case "@r" -> RANDOM_PLAYER;
            case "@e" -> ALL_ENTITIES;
            default -> throw new IllegalArgumentException("Unknown selector prefix: " + prefix);
        };
    }
}
