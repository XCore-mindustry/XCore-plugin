package org.xcore.plugin.vote;

import org.xcore.plugin.common.TextUtils;

public enum VoteChoice {
    YES(1),
    NO(-1),
    ABSTAIN(0);

    private final int sign;

    VoteChoice(int sign) {
        this.sign = sign;
    }

    public int sign() {
        return sign;
    }

    public boolean isValid() {
        return this != ABSTAIN;
    }

    public static VoteChoice parse(String input) {
        return switch (input) {
            case String s when s.isBlank() -> ABSTAIN;
            case String s -> switch (TextUtils.stripFooCharacters(s.toLowerCase().trim())) {
                case "y", "yes", "1", "+" -> YES;
                case "n", "no", "-1", "-" -> NO;
                default -> ABSTAIN;
            };
        };
    }
}
