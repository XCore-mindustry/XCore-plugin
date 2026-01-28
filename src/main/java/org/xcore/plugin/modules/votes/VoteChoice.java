package org.xcore.plugin.modules.votes;

import org.xcore.plugin.utils.TextUtils;

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
        if (input == null || input.isBlank()) {
            return ABSTAIN;
        }

        String sanitized = TextUtils.stripFooCharacters(input.toLowerCase().trim());

        return switch (sanitized) {
            case "y", "yes", "1", "+" -> YES;
            case "n", "no", "-1", "-" -> NO;
            default -> ABSTAIN;
        };
    }
}
