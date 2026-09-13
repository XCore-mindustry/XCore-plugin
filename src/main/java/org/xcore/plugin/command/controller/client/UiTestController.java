package org.xcore.plugin.command.controller.client;

import arc.graphics.Color;
import arc.graphics.Pixmap;
import arc.graphics.PixmapIO;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import mindustry.ui.Menus;
import mindustry.ui.builder.MenuResult;
import mindustry.ui.builder.UiBuilder;
import org.incendo.cloud.annotations.Command;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.ui.MindustryMenuGateway;
import org.xcore.plugin.ui.XcoreImageService;
import org.xcore.ui.LocalizerResolver;
import org.xcore.ui.Text;
import org.xcore.ui.Ui;
import org.xcore.ui.VNode;
import org.xcore.ui.runtime.ControllerContext;
import org.xcore.ui.runtime.SlotKey;
import org.xcore.ui.runtime.UiController;
import org.xcore.ui.runtime.UiSession;
import org.xcore.ui.runtime.UpdateResult;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class UiTestController implements CloudClientController {

    private final MindustryMenuGateway gateway;
    private final XcoreImageService imageService;
    private final int menuBuilderId;
    private final Map<String, UiSession<DemoModel, DemoEvent>> activeSessions = new ConcurrentHashMap<>();

    @Inject
    public UiTestController(MindustryMenuGateway gateway, XcoreImageService imageService) {
        this.gateway = gateway;
        this.imageService = imageService;
        this.menuBuilderId = Menus.registerMenuBuilder((player, result) -> {
            if (player == null || player.con == null) return;
            UiSession<DemoModel, DemoEvent> session = activeSessions.get(player.uuid());
            if (session != null) {
                session.handle(result);
            }
        });
    }

    @Command("testui|uitest")
    public void testUi(XCoreSender sender) {
        if (!sender.isPlayer()) {
            sender.sendMessage("Only players can open UI menus.");
            return;
        }

        Player player = sender.player();
        byte[] defaultPng = createTestImage(Color.royal);
        String initialImage = imageService.ensureDelivered(player, defaultPng);

        DemoController controller = new DemoController(imageService, player);
        DemoModel initialModel = new DemoModel("counters", 0, true, 75f, initialImage);

        ControllerContext ctx = new ControllerContext() {
            @Override
            public String playerId() {
                return player.uuid();
            }

            @Override
            public void close() {
                activeSessions.remove(player.uuid());
                gateway.hideMenuBuilder(player, menuBuilderId);
            }
        };

        UiSession.DeliveryGateway deliveryGateway = new UiSession.DeliveryGateway() {
            @Override
            public void show(String playerId, long token, UiBuilder.NodeBuilder<?> ui) {
                gateway.menuBuilder(player, menuBuilderId, token, "XCore-UI v160 Showcase", false, true, true, ui);
            }

            @Override
            public void update(String playerId, long token, String elementId, UiBuilder.NodeBuilder<?> ui) {
                gateway.menuBuilderUpdate(player, menuBuilderId, elementId, ui);
            }

            @Override
            public void hide(String playerId) {
                gateway.hideMenuBuilder(player, menuBuilderId);
                activeSessions.remove(player.uuid());
            }
        };

        UiSession<DemoModel, DemoEvent> session = UiSession.start(
                controller, initialModel, ctx, deliveryGateway, LocalizerResolver.IDENTITY
        );
        activeSessions.put(player.uuid(), session);
        session.open();
    }

    private static byte[] createTestImage(Color color) {
        Pixmap pix = new Pixmap(64, 64);
        pix.fill(color);
        for (int i = 0; i < 64; i++) {
            pix.set(i, i, Color.white);
            pix.set(i, 63 - i, Color.white);
            pix.set(i, 0, Color.black);
            pix.set(i, 63, Color.black);
            pix.set(0, i, Color.black);
            pix.set(63, i, Color.black);
        }
        try {
            return PixmapIO.writePngBytes(pix);
        } catch (IOException e) {
            return new byte[0];
        }
    }

    public record DemoModel(
            String activeTab,
            int counter,
            boolean notifications,
            float volume,
            String imageRegion
    ) {}

    public sealed interface DemoEvent {
        record ChangeTab(String tab, boolean notifications, float volume) implements DemoEvent {}
        record IncrementCounter(boolean notifications, float volume) implements DemoEvent {}
        record StreamNewImage() implements DemoEvent {}
        record Close() implements DemoEvent {}
    }

    public static class DemoController implements UiController<DemoModel, DemoEvent> {
        private static final SlotKey<Object> SLOT_BODY = SlotKey.of("slot_body");
        private static final SlotKey<Object> SLOT_COUNTER = SlotKey.of("slot_counter");
        private static final Color[] COLORS = {Color.scarlet, Color.green, Color.gold, Color.cyan, Color.purple};
        private int colorIdx = 0;

        private final XcoreImageService imageService;
        private final Player player;

        public DemoController(XcoreImageService imageService, Player player) {
            this.imageService = imageService;
            this.player = player;
        }

        @Override
        public DemoModel initialModel(Object context) {
            return new DemoModel("counters", 0, true, 75f, "error");
        }

        @Override
        public UpdateResult<DemoModel> update(DemoModel model, DemoEvent event, ControllerContext ctx) {
            return switch (event) {
                case DemoEvent.ChangeTab(var tab, var notif, var vol) ->
                        UpdateResult.patch(new DemoModel(tab, model.counter(), notif, vol, model.imageRegion()), SLOT_BODY);

                case DemoEvent.IncrementCounter(var notif, var vol) ->
                        // Only slot_counter updates over the network! Slider and Checkbox are NOT re-rendered or reset!
                        UpdateResult.patch(new DemoModel(model.activeTab(), model.counter() + 1, notif, vol, model.imageRegion()), SLOT_COUNTER);

                case DemoEvent.StreamNewImage() -> {
                    Color nextColor = COLORS[++colorIdx % COLORS.length];
                    byte[] png = createTestImage(nextColor);
                    String newRegion = imageService.ensureDelivered(player, png);
                    yield UpdateResult.patch(new DemoModel(model.activeTab(), model.counter(), model.notifications(), model.volume(), newRegion), SLOT_BODY);
                }

                case DemoEvent.Close() -> {
                    ctx.close();
                    yield UpdateResult.close(model);
                }
            };
        }

        @Override
        public VNode render(DemoModel model) {
            return Ui.table(t -> {
                t.layout(l -> l.width(550f).height(420f).pad(10f));

                // Tabs
                t.add(Ui.table(tabs -> {
                    tabs.button(Text.raw("Interactive Controls"), "tab:counters", b -> b.layout(l -> l.uniform()));
                    tabs.button(Text.raw("Live Texture Stream"), "tab:texture", b -> b.layout(l -> l.uniform()));
                    tabs.button(Text.raw("Architecture Info"), "tab:info", b -> b.layout(l -> l.uniform()));
                }));
                t.row();

                // Dynamic slot body
                t.slot("slot_body", body -> {
                    body.layout(l -> l.grow());

                    if ("counters".equals(model.activeTab())) {
                        body.label(Text.raw("[accent]=== In-place Sub-Tree Updates (Zero Flicker) ==="));
                        body.row();

                        // Dedicated granular slot: ONLY this sub-tree updates when clicking +1!
                        body.slot("slot_counter", sc -> {
                            sc.label(Text.raw("Counter: [gold]" + model.counter() + "[]  |  Vol: [accent]" + (int) model.volume() + "%[]  |  Notif: " + (model.notifications() ? "[green]ON[]" : "[scarlet]OFF[]")));
                        });
                        body.row();

                        body.button(Text.raw("[green]+1 Click (Updates only slot_counter above)[]"), "action:inc", b -> b.layout(l -> l.growX()));
                        body.row();

                        // Checkbox and Slider with explicit IDs: client automatically sends their state in MenuResult.values
                        body.check(Text.raw("Enable notifications"), s -> s.id("check_notif").checked(model.notifications()));
                        body.row();
                        body.slider(0f, 100f, 5f, s -> s.id("slider_vol").defaultValue(model.volume()));
                    } else if ("texture".equals(model.activeTab())) {
                        body.label(Text.raw("[accent]=== Live Server Texture Streaming (PNG over TCP) ==="));
                        body.row();
                        body.image(model.imageRegion(), l -> l.size(80f));
                        body.row();
                        body.button(Text.raw("Stream Random Color PNG to Atlas"), "action:stream_png", b -> b.layout(l -> l.growX()));
                    } else {
                        body.label(Text.raw("[accent]=== XCore-UI v160 Engine ==="));
                        body.row();
                        body.label(Text.raw("[lightgray]Features:\n- Elm / MVI Reducer state machine\n- Mindustry v160 Call.menuBuilderUpdate\n- Content-addressed SHA-256 PNG streaming\n- FluBundle lazy localization\n- 100% Headless testability[]"));
                    }
                });
                t.row();

                // Footer
                t.button(Text.raw("Close Dialog"), "action:close", b -> b.layout(l -> l.growX()));
            });
        }

        @Override
        public DemoEvent parseEvent(MenuResult result) {
            if (result == null || result.result == null) return null;

            // Read client form values transmitted in the packet
            boolean notif = result.getBool("check_notif", true);
            float vol = result.getFloat("slider_vol", 75f);

            if (result.result.startsWith("tab:")) return new DemoEvent.ChangeTab(result.result.substring(4), notif, vol);
            if ("action:inc".equals(result.result)) return new DemoEvent.IncrementCounter(notif, vol);
            if ("action:stream_png".equals(result.result)) return new DemoEvent.StreamNewImage();
            if ("action:close".equals(result.result)) return new DemoEvent.Close();
            return null;
        }
    }
}
