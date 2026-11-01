package org.xcore.ui.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SlotPrunerTest {

    @Test
    void dropsDescendantsWhenAncestorIsDirty() {
        SlotKey<Object> root = SlotKey.of("tab");
        SlotKey<Object> sub1 = SlotKey.of("tab/header");
        SlotKey<Object> sub2 = SlotKey.of("tab/body/button");
        SlotKey<Object> unrelated = SlotKey.of("sidebar");

        List<SlotKey<Object>> pruned = SlotPruner.prune(List.of(sub1, root, sub2, unrelated));

        assertThat(pruned).containsExactlyInAnyOrder(root, unrelated);
    }

    @Test
    void preservesIndependentParallelSlots() {
        SlotKey<Object> a = SlotKey.of("col_a/item1");
        SlotKey<Object> b = SlotKey.of("col_b/item2");
        SlotKey<Object> c = SlotKey.of("footer");

        List<SlotKey<Object>> pruned = SlotPruner.prune(List.of(a, b, c));

        assertThat(pruned).containsExactlyInAnyOrder(a, b, c);
    }

    @Test
    void singleOrEmptyListReturnsAsIs() {
        assertThat(SlotPruner.prune(List.of())).isEmpty();
        SlotKey<Object> s = SlotKey.of("only");
        assertThat(SlotPruner.prune(List.of(s))).containsExactly(s);
    }

    @Test
    void handlesMultiLevelNestingProperly() {
        SlotKey<Object> l1 = SlotKey.of("main");
        SlotKey<Object> l2 = SlotKey.of("main/sub");
        SlotKey<Object> l3 = SlotKey.of("main/sub/deep");

        List<SlotKey<Object>> pruned = SlotPruner.prune(List.of(l3, l2, l1));

        assertThat(pruned).containsExactly(l1);
    }
}
