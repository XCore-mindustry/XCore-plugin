package org.xcore.ui;

import org.xcore.ui.component.Component;

import java.util.List;
import java.util.Objects;

/**
 * A {@link Component} instance embedded into the virtual DOM. The component's
 * subtree is expanded eagerly at construction time (components are pure), so
 * the compiled wire format is uniform and the compiler stays trivial.
 */
public record VComponentNode(Component<?> component, List<VNode> children) implements VNode {

    public VComponentNode {
        Objects.requireNonNull(component, "component");
        children = VNode.copyChildren(children);
    }

    /** Creates an expanded node from a component and its props. */
    public static <P> VComponentNode of(Component<P> component, P props) {
        return new VComponentNode(component, component.render(props));
    }

    @Override
    public String id() {
        return null;
    }

    @Override
    public UiLayout layout() {
        return UiLayout.DEFAULT;
    }

    @Override
    public Kind kind() {
        return Kind.COMPONENT;
    }

    @Override
    public List<VNode> children() {
        return children;
    }
}
