package org.xcore.plugin.ui;

import mindustry.ui.builder.MenuResult;
import mindustry.ui.builder.UiBuilder;
import mindustry.ui.builder.UiDslWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.ui.LocalizerResolver;
import org.xcore.ui.Text;
import org.xcore.ui.Ui;
import org.xcore.ui.VNode;
import org.xcore.ui.runtime.ControllerContext;
import org.xcore.ui.runtime.SlotKey;
import org.xcore.ui.runtime.UiController;
import org.xcore.ui.runtime.UiSession;
import org.xcore.ui.runtime.UpdateResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class XcoreUiFlowTest {

    record ServerConfigModel(String activeTab, float tickRate, boolean allowSpectators) {}

    sealed interface ConfigEvent {
        record SelectTab(String tab) implements ConfigEvent {}
        record ToggleSpectators(boolean allow) implements ConfigEvent {}
        record Close() implements ConfigEvent {}
    }

    static final SlotKey<Object> SLOT_TAB_CONTENT = SlotKey.of("slot_tab_content");

    static class ServerConfigController implements UiController<ServerConfigModel, ConfigEvent> {
        @Override
        public ServerConfigModel initialModel(Object context) {
            return new ServerConfigModel("general", 60f, true);
        }

        @Override
        public UpdateResult<ServerConfigModel> update(ServerConfigModel model, ConfigEvent event, ControllerContext ctx) {
            return switch (event) {
                case ConfigEvent.SelectTab(var tab) ->
                        UpdateResult.patch(new ServerConfigModel(tab, model.tickRate(), model.allowSpectators()), SLOT_TAB_CONTENT);
                case ConfigEvent.ToggleSpectators(var allow) ->
                        UpdateResult.patch(new ServerConfigModel(model.activeTab(), model.tickRate(), allow), SLOT_TAB_CONTENT);
                case ConfigEvent.Close() ->
                        UpdateResult.close(model);
            };
        }

        @Override
        public VNode render(ServerConfigModel model) {
            return Ui.table(t -> {
                t.layout(l -> l.width(500f).height(400f).pad(10f));

                // Tabs bar
                t.add(Ui.table(tb -> {
                    tb.button(Text.raw("General"), "tab:general", b -> b.layout(l -> l.uniform()));
                    tb.button(Text.raw("Network"), "tab:network", b -> b.layout(l -> l.uniform()));
                }));
                t.row();

                // Dynamic slot: content of active tab
                t.slot("slot_tab_content", slot -> {
                    if ("general".equals(model.activeTab())) {
                        slot.label(Text.raw("Tickrate: " + model.tickRate()));
                        slot.row();
                        slot.check(Text.raw("Allow Spectators"), s -> s.checked(model.allowSpectators()));
                    } else {
                        slot.label(Text.raw("Network Bandwidth: Unlimited"));
                    }
                });
                t.row();

                t.button(Text.raw("Close"), "action:close", null);
            });
        }

        @Override
        public ConfigEvent parseEvent(MenuResult result) {
            if (result == null || result.result == null) return null;
            if (result.result.startsWith("tab:")) return new ConfigEvent.SelectTab(result.result.substring(4));
            if ("action:close".equals(result.result)) return new ConfigEvent.Close();
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
            return "player-uuid";
        }

        @Override
        public void close() {
        }
    }

    @Test
    @DisplayName("Modern xcore-ui reactive screen runs tabs and in-place slot patches inside XCore-plugin")
    void modernXcoreUiScreenLifecycle() {
        MockDeliveryGateway gateway = new MockDeliveryGateway();
        ServerConfigController controller = new ServerConfigController();
        MockContext ctx = new MockContext();

        UiSession<ServerConfigModel, ConfigEvent> session = UiSession.start(
                controller, controller.initialModel(null), ctx, gateway, LocalizerResolver.IDENTITY);

        // 1. Initial show -> renders general tab content
        session.open();
        assertThat(gateway.actions).hasSize(1);
        assertThat(gateway.actions.get(0).type()).isEqualTo("SHOW");
        assertThat(gateway.actions.get(0).dsl()).contains("Tickrate: 60");
        assertThat(gateway.actions.get(0).dsl()).contains("Allow Spectators");

        // 2. Click Network tab -> in-place slot_tab_content patch without tearing down dialog
        MenuResult switchTab = new MenuResult("tab:network");
        session.handle(switchTab);

        assertThat(gateway.actions).hasSize(2);
        RecordedAction patch = gateway.actions.get(1);
        assertThat(patch.type()).isEqualTo("UPDATE");
        assertThat(patch.targetId()).isEqualTo("slot_tab_content");
        assertThat(patch.dsl()).contains("Network Bandwidth: Unlimited");
        assertThat(patch.dsl()).doesNotContain("Tickrate: 60");

        // 3. Close dialog -> hides dialog
        session.handle(new MenuResult("action:close"));
        assertThat(gateway.actions).hasSize(3);
        assertThat(gateway.actions.get(2).type()).isEqualTo("HIDE");
    }
}
