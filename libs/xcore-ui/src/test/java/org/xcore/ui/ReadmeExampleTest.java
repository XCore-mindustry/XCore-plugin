package org.xcore.ui;

import mindustry.ui.builder.MenuResult;
import mindustry.ui.builder.UiBuilder;
import org.junit.jupiter.api.Test;
import org.xcore.ui.runtime.ActionRouter;
import org.xcore.ui.runtime.ControllerContext;
import org.xcore.ui.runtime.SlotKey;
import org.xcore.ui.runtime.UiController;
import org.xcore.ui.runtime.UiSession;
import org.xcore.ui.runtime.UpdateResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The quick example of the README, kept compiling and doing what the README says. */
class ReadmeExampleTest {

    record SettingsModel(String activeTab) {
        static final Lens<SettingsModel, String> ACTIVE_TAB =
                Lens.of(SettingsModel::activeTab, (model, tab) -> new SettingsModel(tab));
    }

    sealed interface SettingsEvent {
    }

    record SelectTab(String tab) implements SettingsEvent {
    }

    record Save() implements SettingsEvent {
    }

    // ---- as in the README ----
    public static class PlayerSettingsScreen implements UiController<SettingsModel, SettingsEvent> {

        public static final SlotKey<Object> SLOT_TAB = SlotKey.of("slot_tab");

        @Override
        public UpdateResult<SettingsModel> update(SettingsModel model, SettingsEvent event, ControllerContext ctx) {
            return switch (event) {
                // Only slot_tab is sent again; the tab bar and the footer stay as they are.
                case SelectTab(var tab) -> UpdateResult.patch(SettingsModel.ACTIVE_TAB.set(model, tab), SLOT_TAB);
                case Save() -> UpdateResult.close(model);
            };
        }

        @Override
        public VNode render(SettingsModel model) {
            return Ui.table(t -> {
                t.layout(l -> l.width(500f).height(400f).pad(10f));

                t.add(Ui.table(tabs -> {
                    tabs.layout(l -> l.growX());
                    tabs.button(Text.t("tab.profile"), "tab:profile", b -> b.checked("profile".equals(model.activeTab())));
                    tabs.button(Text.t("tab.chat"), "tab:chat", b -> b.checked("chat".equals(model.activeTab())));
                }));
                t.row();

                t.slot(SLOT_TAB, tab -> tab.label(Text.t("tab." + model.activeTab() + ".body"), l -> l.grow()));
                t.row();

                t.button(Text.t("save"), "action:save", b -> b.style("green"));
            });
        }

        private static final ActionRouter<SettingsEvent> ACTIONS = ActionRouter.<SettingsEvent>create()
                .on("action:save", Save::new)
                .prefix("tab:", SelectTab::new);

        @Override
        public SettingsEvent parseEvent(MenuResult result) {
            return ACTIONS.parse(result);
        }
    }
    // ---- end of the README ----

    @Test
    void selectingATabPatchesOnlyItsSlot() {
        List<String> sent = new ArrayList<>();
        UiSession.DeliveryGateway gateway = new UiSession.DeliveryGateway() {
            @Override
            public void show(String playerId, long token, UiBuilder.NodeBuilder<?> ui) {
                sent.add("show");
            }

            @Override
            public void update(String playerId, long token, String elementId, UiBuilder.NodeBuilder<?> ui) {
                sent.add("update " + elementId);
            }

            @Override
            public void hide(String playerId) {
                sent.add("hide");
            }
        };
        ControllerContext ctx = new ControllerContext() {
            @Override
            public String playerId() {
                return "player";
            }

            @Override
            public void close() {
            }
        };
        UiSession<SettingsModel, SettingsEvent> session = UiSession.start(
                new PlayerSettingsScreen(), new SettingsModel("profile"), ctx, gateway, LocalizerResolver.IDENTITY);

        session.open();
        session.handle(new MenuResult("tab:chat"));
        session.handle(new MenuResult("action:save"));

        assertThat(sent).containsExactly("show", "update slot_tab", "hide");
        assertThat(session.model().activeTab()).isEqualTo("chat");
    }
}
