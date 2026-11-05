package org.xcore.ui.runtime;

import mindustry.ui.builder.UiBuilder;
import mindustry.ui.builder.UiDslWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xcore.ui.LocalizerResolver;
import org.xcore.ui.Text;
import org.xcore.ui.Ui;
import org.xcore.ui.UiPackets;
import org.xcore.ui.VNode;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A patch goes only where the client has the slot; elsewhere the whole dialog is sent. */
class UiSessionSlotTrackingTest {

    static final SlotKey<Object> OUTER = SlotKey.of("outer");
    static final SlotKey<Object> INNER = SlotKey.of("inner");
    static final SlotKey<Object> DETAILS = SlotKey.of("details");
    static final SlotKey<Object> ELSEWHERE = SlotKey.of("elsewhere");
    static final SlotKey<Object> DEEP = SlotKey.of("deep");
    /** A slot whose key names a parent the view does not have. */
    static final SlotKey<Object> GROUP = SlotKey.of("group");
    static final SlotKey<Object> GROUP_ITEM = SlotKey.of("group/item");

    /**
     * What the view shows: the inner slot inside the outer one and the deep slot inside the inner
     * one, the details slot beside them.
     */
    record View(int n, boolean inner, boolean details, int filler, boolean deep) {
        View(int n, boolean inner, boolean details, int filler) {
            this(n, inner, details, filler, false);
        }

        View next() {
            return new View(n + 1, inner, details, filler, deep);
        }

        View withInner(boolean inner) {
            return new View(n + 1, inner, details, filler, deep);
        }

        View withDetails(boolean details) {
            return new View(n + 1, inner, details, filler, deep);
        }

        View withFiller(int filler) {
            return new View(n + 1, inner, details, filler, deep);
        }

        View withDeep(boolean deep) {
            return new View(n + 1, inner, details, filler, deep);
        }
    }

    record Patch(View next, List<SlotKey<?>> slots) {
    }

    static final class Controller implements UiController<View, Patch> {
        @Override
        public UpdateResult<View> update(View model, Patch event, ControllerContext ctx) {
            return UpdateResult.patch(event.next(), event.slots());
        }

        @Override
        public VNode render(View view) {
            return Ui.table(t -> {
                t.slot(OUTER, outer -> {
                    outer.label(Text.raw("outer " + view.n()));
                    if (view.inner()) {
                        outer.row();
                        outer.slot(INNER, inner -> {
                            inner.label(Text.raw("inner " + view.n()));
                            if (view.deep()) {
                                inner.row();
                                inner.slot(DEEP, deep -> deep.label(Text.raw("deep " + view.n())));
                            }
                        });
                    }
                });
                t.row();
                t.slot(GROUP_ITEM, item -> item.label(Text.raw("item " + view.n())));
                t.row();
                if (view.details()) {
                    t.slot(DETAILS, details -> {
                        details.label(Text.raw("details " + view.n()));
                        for (int i = 0; i < view.filler(); i++) {
                            details.row();
                            details.label(Text.raw("filler line " + i + " ".repeat(40)));
                        }
                    });
                }
            });
        }
    }

    record Sent(String type, String target, String dsl) {
    }

    static final class Gateway implements UiSession.DeliveryGateway {
        final List<Sent> sent = new ArrayList<>();

        @Override
        public void show(String playerId, long token, UiBuilder.NodeBuilder<?> ui) {
            sent.add(new Sent("show", null, UiDslWriter.write(ui)));
        }

        @Override
        public void update(String playerId, long token, String elementId, UiBuilder.NodeBuilder<?> ui) {
            sent.add(new Sent("update", elementId, UiDslWriter.write(ui)));
        }

        @Override
        public void hide(String playerId) {
            sent.add(new Sent("hide", null, null));
        }
    }

    static final ControllerContext CTX = new ControllerContext() {
        @Override
        public String playerId() {
            return "player";
        }

        @Override
        public void close() {
        }
    };

    Gateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new Gateway();
    }

    UiSession<View, Patch> open(View view) {
        UiSession<View, Patch> session = UiSession.start(new Controller(), view, CTX, gateway, LocalizerResolver.IDENTITY);
        session.open();
        gateway.sent.clear();
        return session;
    }

    @Test
    void patchesASlotTheClientHas() {
        UiSession<View, Patch> session = open(new View(0, false, true, 0));

        session.dispatch(new Patch(session.model().next(), List.of(DETAILS)));

        assertThat(gateway.sent).singleElement().satisfies(sent -> {
            assertThat(sent.type()).isEqualTo("update");
            assertThat(sent.target()).isEqualTo("details");
            assertThat(sent.dsl()).contains("details 1");
        });
    }

    @Test
    void slotTheClientLacksSendsTheWholeDialog() {
        UiSession<View, Patch> session = open(new View(0, false, false, 0));

        // The details slot comes into the view with this update; the client has no table to fill.
        session.dispatch(new Patch(session.model().withDetails(true), List.of(DETAILS)));

        assertThat(gateway.sent).singleElement().satisfies(sent -> {
            assertThat(sent.type()).isEqualTo("show");
            assertThat(sent.dsl()).contains("details 1");
        });
    }

    @Test
    void slotTheViewDroppedSendsTheWholeDialog() {
        UiSession<View, Patch> session = open(new View(0, false, true, 0));

        // The client still shows the details; patching them away is not possible.
        session.dispatch(new Patch(session.model().withDetails(false), List.of(DETAILS)));

        assertThat(gateway.sent).singleElement().satisfies(sent -> {
            assertThat(sent.type()).isEqualTo("show");
            assertThat(sent.dsl()).doesNotContain("details");
        });
    }

    @Test
    void slotOfNoViewIsPassedOver() {
        UiSession<View, Patch> session = open(new View(0, false, true, 0));

        session.dispatch(new Patch(session.model().next(), List.of(ELSEWHERE, DETAILS)));

        assertThat(gateway.sent).extracting(Sent::type, Sent::target)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("update", "details"));
    }

    @Test
    void slotNestedInAnotherSlotOfThePatchIsNotSentAgain() {
        UiSession<View, Patch> session = open(new View(0, true, false, 0));

        session.dispatch(new Patch(session.model().next(), List.of(INNER, OUTER)));

        assertThat(gateway.sent).singleElement().satisfies(sent -> {
            assertThat(sent.type()).isEqualTo("update");
            assertThat(sent.target()).isEqualTo("outer");
            assertThat(sent.dsl()).contains("inner 1");
        });
    }

    @Test
    void slotAPatchBroughtCanBePatchedItself() {
        UiSession<View, Patch> session = open(new View(0, false, false, 0));

        // The inner slot arrives inside the patched outer one...
        session.dispatch(new Patch(session.model().withInner(true), List.of(OUTER)));
        // ...so the client has it from then on.
        session.dispatch(new Patch(session.model().next(), List.of(INNER)));

        assertThat(gateway.sent).extracting(Sent::type, Sent::target).containsExactly(
                org.assertj.core.groups.Tuple.tuple("update", "outer"),
                org.assertj.core.groups.Tuple.tuple("update", "inner"));
    }

    @Test
    void slotAPatchRemovedIsNoLongerThere() {
        UiSession<View, Patch> session = open(new View(0, true, false, 0));

        session.dispatch(new Patch(session.model().withInner(false), List.of(OUTER)));
        // Bringing the inner slot back cannot be a patch of it: the client dropped its table.
        session.dispatch(new Patch(session.model().withInner(true), List.of(INNER)));

        assertThat(gateway.sent).extracting(Sent::type).containsExactly("update", "show");
    }

    @Test
    void bodyOverThePacketLimitSendsNoneOfThePatch() {
        UiSession<View, Patch> session = open(new View(0, false, true, 0));

        assertThatThrownBy(() -> session.dispatch(
                new Patch(session.model().withFiller(600), List.of(OUTER, DETAILS))))
                .isInstanceOf(UiPackets.TooLargeException.class);

        assertThat(gateway.sent).isEmpty();
    }

    @Test
    void newSlotInsideAPatchedSlotComesWithIt() {
        UiSession<View, Patch> session = open(new View(0, false, false, 0));

        // The inner slot is new, but the outer patch carries it: no need for the whole dialog.
        session.dispatch(new Patch(session.model().withInner(true), List.of(OUTER, INNER)));

        assertThat(gateway.sent).singleElement().satisfies(sent -> {
            assertThat(sent.type()).isEqualTo("update");
            assertThat(sent.target()).isEqualTo("outer");
            assertThat(sent.dsl()).contains("inner 1");
        });
    }

    @Test
    void slotDroppedFromInsideAPatchedSlotGoesWithIt() {
        UiSession<View, Patch> session = open(new View(0, true, false, 0));

        session.dispatch(new Patch(session.model().withInner(false), List.of(OUTER, INNER)));

        assertThat(gateway.sent).extracting(Sent::type, Sent::target)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("update", "outer"));
    }

    @Test
    void slotAnEarlierPatchPutDeeperDownGoesWithItsAncestor() {
        UiSession<View, Patch> session = open(new View(0, true, false, 0));

        session.dispatch(new Patch(session.model().withDeep(true), List.of(INNER)));     // deep arrives inside inner
        session.dispatch(new Patch(session.model().withInner(false), List.of(OUTER)));   // inner and deep go
        // Deep is back in the view, but the client lost its table with the outer patch.
        session.dispatch(new Patch(session.model().withInner(true), List.of(DEEP)));

        assertThat(gateway.sent).extracting(Sent::type).containsExactly("update", "update", "show");
    }

    @Test
    void slotOfNoViewDoesNotTakeTheSlotsUnderItsPath() {
        UiSession<View, Patch> session = open(new View(0, false, false, 0));

        session.dispatch(new Patch(session.model().next(), List.of(GROUP, GROUP_ITEM)));

        assertThat(gateway.sent).singleElement().satisfies(sent -> {
            assertThat(sent.target()).isEqualTo("group/item");
            assertThat(sent.dsl()).contains("item 1");
        });
    }
}
