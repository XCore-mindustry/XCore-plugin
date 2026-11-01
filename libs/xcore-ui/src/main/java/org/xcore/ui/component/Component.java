package org.xcore.ui.component;

import org.xcore.ui.VComponentNode;
import org.xcore.ui.VNode;

import java.util.List;

/**
 * Reusable, parameterized UI unit rendering pure node subtrees.
 *
 * <p>Components are functions of props to {@link VNode}s. They carry no state
 * themselves; interactivity is expressed via action IDs handled by the
 * {@code UiController} reducer, exactly like plain nodes. Expansion into the
 * parent tree happens eagerly via {@link #with(Object)}.
 *
 * @param <P> props record type
 */
public interface Component<P> {

    /** Renders the component subtree for the given props. Pure function; must not mutate inputs. */
    List<VNode> render(P props);

    /** Binds props to this component and returns the expanded node. */
    default VNode with(P props) {
        return VComponentNode.of(this, props);
    }

    /** A component whose props are already fixed. */
    record BoundComponent<P>(Component<P> component, P props) implements Component<P> {
        @Override
        public List<VNode> render(P unused) {
            return component.render(props);
        }

        @Override
        public VNode with(P next) {
            return component.with(next);
        }
    }
}
