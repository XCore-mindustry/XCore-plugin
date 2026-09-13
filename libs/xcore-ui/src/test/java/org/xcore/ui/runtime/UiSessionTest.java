package org.xcore.ui.runtime;

import org.junit.jupiter.api.Test;
import org.xcore.ui.LocalizerResolver;
import org.xcore.ui.Text;
import org.xcore.ui.Ui;
import org.xcore.ui.VNode;
import mindustry.ui.builder.MenuResult;
import mindustry.ui.builder.UiBuilder;
import mindustry.ui.builder.UiDslWriter;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UiSessionTest {

    record TestModel(int count, String activeTab) {}

    sealed interface TestEvent {
        record Increment() implements TestEvent {}
        record SwitchTab(String tab) implements TestEvent {}
        record Close() implements TestEvent {}
    }

    static final SlotKey<Object> SLOT_COUNTER = SlotKey.of("slot_counter");
    static final SlotKey<Object> SLOT_TAB = SlotKey.of("slot_tab");

    static class CounterController implements UiController<TestModel, TestEvent> {
        @Override
        public TestModel initialModel(Object context) {
            return new TestModel(0, "main");
        }

        @Override
        public UpdateResult<TestModel> update(TestModel model, TestEvent event, ControllerContext ctx) {
            return switch (event) {
                case TestEvent.Increment() ->
                        UpdateResult.patch(new TestModel(model.count() + 1, model.activeTab()), SLOT_COUNTER);
                case TestEvent.SwitchTab(var tab) ->
                        UpdateResult.patch(new TestModel(model.count(), tab), SLOT_TAB);
                case TestEvent.Close() ->
                        UpdateResult.close(model);
            };
        }

        @Override
        public VNode render(TestModel model) {
            return Ui.table(t -> {
                t.slot("slot_counter", s -> {
                    s.label(Text.raw("Count: " + model.count()));
                });
                t.row();
                t.slot("slot_tab", s -> {
                    s.label(Text.raw("Tab: " + model.activeTab()));
                });
                t.row();
                t.button(Text.raw("+1"), "inc", null);
                t.button(Text.raw("Switch"), "tab:stats", null);
            });
        }

        @Override
        public TestEvent parseEvent(MenuResult result) {
            if (result == null || result.result == null) return null;
            if ("inc".equals(result.result)) return new TestEvent.Increment();
            if (result.result.startsWith("tab:")) return new TestEvent.SwitchTab(result.result.substring(4));
            if ("close".equals(result.result)) return new TestEvent.Close();
            return null;
        }
    }

    record RecordedAction(String type, String targetId, String dsl) {}

    static class MockDeliveryGateway implements UiSession.DeliveryGateway {
        final List<RecordedAction> actions = new ArrayList<>();

        @Override
        public void show(String playerId, long token, UiBuilder.NodeBuilder<?> ui) {
            actions.add(new RecordedAction("SHOW", playerId, UiDslWriter.write(ui)));
        }

        @Override
        public void update(String playerId, long token, String elementId, UiBuilder.NodeBuilder<?> ui) {
            actions.add(new RecordedAction("UPDATE", elementId, UiDslWriter.write(ui)));
        }

        @Override
        public void hide(String playerId) {
            actions.add(new RecordedAction("HIDE", playerId, null));
        }
    }

    static class MockContext implements ControllerContext {
        @Override
        public String playerId() {
            return "player-uuid-123";
        }

        @Override
        public void close() {
        }
    }

    @Test
    void sessionLifecycleDeliversInitialShowAndSubsequentSlotPatches() {
        MockDeliveryGateway gateway = new MockDeliveryGateway();
        CounterController controller = new CounterController();
        MockContext ctx = new MockContext();

        UiSession<TestModel, TestEvent> session = UiSession.start(
                controller, controller.initialModel(null), ctx, gateway, LocalizerResolver.IDENTITY);

        // 1. Initial open() -> transmits full dialog
        session.open();
        assertThat(gateway.actions).hasSize(1);
        assertThat(gateway.actions.get(0).type()).isEqualTo("SHOW");
        assertThat(gateway.actions.get(0).dsl()).contains("Count: 0");
        assertThat(gateway.actions.get(0).dsl()).contains("Tab: main");

        // 2. Click "+1" -> triggers patch of only slot_counter without re-sending the whole dialog!
        MenuResult clickInc = new MenuResult("inc");
        session.handle(clickInc);

        assertThat(gateway.actions).hasSize(2);
        RecordedAction patchAction = gateway.actions.get(1);
        assertThat(patchAction.type()).isEqualTo("UPDATE");
        assertThat(patchAction.targetId()).isEqualTo("slot_counter");
        assertThat(patchAction.dsl()).contains("Count: 1");
        assertThat(patchAction.dsl()).doesNotContain("Tab: main"); // tab was NOT re-transmitted!

        // 3. Switch tab -> triggers patch of only slot_tab
        MenuResult switchTab = new MenuResult("tab:leaderboard");
        session.handle(switchTab);

        assertThat(gateway.actions).hasSize(3);
        RecordedAction tabPatch = gateway.actions.get(2);
        assertThat(tabPatch.type()).isEqualTo("UPDATE");
        assertThat(tabPatch.targetId()).isEqualTo("slot_tab");
        assertThat(tabPatch.dsl()).contains("Tab: leaderboard");

        // 4. Close action -> sends HIDE
        session.handle(new MenuResult("close"));
        assertThat(gateway.actions).hasSize(4);
        assertThat(gateway.actions.get(3).type()).isEqualTo("HIDE");
    }

    @Test
    void dispatchDirectEventUpdatesModelAndPatchesSlots() {
        MockDeliveryGateway gateway = new MockDeliveryGateway();
        CounterController controller = new CounterController();
        MockContext ctx = new MockContext();

        UiSession<TestModel, TestEvent> session = UiSession.start(
                controller, controller.initialModel(null), ctx, gateway, LocalizerResolver.IDENTITY);
        session.open();

        session.dispatch(new TestEvent.Increment());

        assertThat(gateway.actions).hasSize(2);
        RecordedAction patchAction = gateway.actions.get(1);
        assertThat(patchAction.type()).isEqualTo("UPDATE");
        assertThat(patchAction.targetId()).isEqualTo("slot_counter");
        assertThat(patchAction.dsl()).contains("Count: 1");
    }
}
