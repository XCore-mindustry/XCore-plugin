package tilelogger;

/** IDs are part of the v2 journal and client wire contract. */
public enum ActionKind {
    BASELINE(0),
    BUILD_STARTED(1),
    BUILD_COMPLETED(2),
    DESTROY_STARTED(3),
    DESTROY_COMPLETED(4),
    ROTATE(5),
    CONFIGURE(6),
    PICKUP(7),
    DROP(8),
    ADMIN_FILL(9),
    LEGACY(255);
    public final int id;
    ActionKind(int id) {
        this.id = id;
    }
    public static ActionKind fromId(int id) {
        for (var kind : values())
            if (kind.id == id)
                return kind;
        throw new IllegalArgumentException("Unknown action kind: " + id);
    }
    public String messageKey() {
        return "tilelogger-action-" + name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }
}
