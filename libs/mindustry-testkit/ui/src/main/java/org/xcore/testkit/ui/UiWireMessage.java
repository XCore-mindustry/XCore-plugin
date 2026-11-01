package org.xcore.testkit.ui;

import mindustry.gen.HideMenuBuilderCallPacket;
import mindustry.gen.MenuBuilderCallPacket;
import mindustry.gen.MenuBuilderUpdateCallPacket;
import mindustry.ui.builder.MenuResult;

import java.util.Map;
import java.util.Optional;

/**
 * Immutable snapshot of one message traversing the client-server boundary.
 */
public sealed interface UiWireMessage {
    record Show(int menuId, long token, boolean hidePrevious, UiSnapshot body) implements UiWireMessage {
        /** {@link UiSnapshot#dsl()} of the body. */
        public String dsl() { return body.dsl(); }
    }

    record Update(int menuId, String targetId, UiSnapshot body) implements UiWireMessage {
        /** {@link UiSnapshot#dsl()} of the body. */
        public String dsl() { return body.dsl(); }
    }

    record Hide(int menuId) implements UiWireMessage {}

    /**
     * A client's answer to a menu. {@code values} are the form fields the client sent with it,
     * keyed by element id, as {@link MenuResult#values} carries them.
     */
    record Choose(int menuId, long token, String action, Map<String, Object> values) implements UiWireMessage {
        public Choose {
            values = Map.copyOf(values);
        }

        public Choose(int menuId, long token, String action) {
            this(menuId, token, action, Map.of());
        }

        public boolean isCancel() { return action == null; }

        /** The {@link MenuResult} a server receives for this answer: action, token and values. */
        public MenuResult toMenuResult() {
            MenuResult result = new MenuResult(action);
            result.token = token;
            values.forEach(result.values::put);
            return result;
        }
    }

    /**
     * The server-to-client message a menu packet stands for, or empty for any other packet.
     *
     * <p>The body is captured now, so later changes to the builder the packet holds do not reach
     * the message. Call this where the packet is sent, as {@link DeterministicUiLoop#sendPacket}
     * does.
     */
    static Optional<UiWireMessage> fromPacket(Object packet) {
        return Optional.ofNullable(switch (packet) {
            case MenuBuilderCallPacket p -> new Show(p.id, p.token, p.hidePrevious, UiSnapshot.capture(p.builder));
            case MenuBuilderUpdateCallPacket p -> new Update(p.id, p.tableId, UiSnapshot.capture(p.builder));
            case HideMenuBuilderCallPacket p -> new Hide(p.menuId);
            case null, default -> null;
        });
    }
}
