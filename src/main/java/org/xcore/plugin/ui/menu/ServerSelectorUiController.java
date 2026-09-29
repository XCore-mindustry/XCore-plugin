package org.xcore.plugin.ui.menu;

import arc.util.Log;
import com.ospx.flubundle.Bundle;
import mindustry.gen.Call;
import mindustry.gen.Iconc;
import mindustry.ui.builder.MenuResult;
import org.xcore.plugin.service.ServerRegistryService;
import org.xcore.plugin.service.ServerRegistryService.Category;
import org.xcore.plugin.service.ServerRegistryService.ServerStatus;
import org.xcore.plugin.session.Session;
import org.xcore.ui.Text;
import org.xcore.ui.Ui;
import org.xcore.ui.VNode;
import org.xcore.ui.runtime.ControllerContext;
import org.xcore.ui.runtime.SlotKey;
import org.xcore.ui.runtime.UiController;
import org.xcore.ui.runtime.UpdateResult;

import java.util.List;
import java.util.Optional;

/**
 * Elm/MVI reactive UI controller for the server browser (/servers, /hub, /play).
 *
 * <p>Features:
 * <ul>
 *   <li>Full-width clickable server card buttons ({@code VButtonTable}) for zero-aim mobile ergonomics.</li>
 *   <li>Left gamemode accent strip with color categorization.</li>
 *   <li>Live player counters and 5-block capacity visualization bars.</li>
 *   <li>Category filter tabs with server count pills.</li>
 *   <li>Zero-flicker partial slot patching targeting {@link #SLOT_SERVERS} on category change or refresh.</li>
 * </ul>
 */
public class ServerSelectorUiController implements UiController<ServerSelectorUiController.ServerSelectorModel, ServerSelectorUiController.ServerSelectorEvent> {

    public static final SlotKey<ServerSelectorModel> SLOT_SERVERS = SlotKey.of("slot_servers");

    private final ServerRegistryService registryService;
    private final Session session;

    public ServerSelectorUiController(ServerRegistryService registryService, Session session) {
        this.registryService = registryService;
        this.session = session;
    }

    public record UiMetrics(
            float dialogWidth,
            float contentWidth,
            float cardWidth,
            float cardContentWidth,
            float paneMaxHeight,
            int descMaxLength,
            int mapMaxLength
    ) {
        public static UiMetrics of(boolean isMobile) {
            if (isMobile) {
                float dw = 680f;
                float pad = 10f;
                float cw = dw - pad * 2f;
                float cardW = cw - 26f;
                float cardInnerW = cardW - 18f;
                return new UiMetrics(dw, cw, cardW, cardInnerW, 600f, 44, 20);
            } else {
                float dw = 740f;
                float pad = 12f;
                float cw = dw - pad * 2f;
                float cardW = cw - 26f;
                float cardInnerW = cardW - 22f;
                return new UiMetrics(dw, cw, cardW, cardInnerW, 520f, 48, 24);
            }
        }
    }

    public record ServerSelectorModel(
            String currentServerId,
            Category selectedCategory,
            List<ServerStatus> allServers,
            int totalOnlinePlayers,
            int totalOnlineServers,
            int totalServersCount,
            boolean isMobile
    ) {
        public ServerSelectorModel(
                String currentServerId,
                Category selectedCategory,
                List<ServerStatus> allServers,
                int totalOnlinePlayers,
                int totalOnlineServers,
                int totalServersCount
        ) {
            this(currentServerId, selectedCategory, allServers, totalOnlinePlayers, totalOnlineServers, totalServersCount, false);
        }

        public List<ServerStatus> filteredServers() {
            if (selectedCategory == Category.ALL) return allServers;
            return allServers.stream()
                    .filter(s -> s.template().category() == selectedCategory)
                    .toList();
        }

        public int countForCategory(Category cat) {
            if (cat == Category.ALL) return allServers.size();
            return (int) allServers.stream().filter(s -> s.template().category() == cat).count();
        }
    }

    public sealed interface ServerSelectorEvent {
        record SelectCategory(Category category) implements ServerSelectorEvent {}
        record Connect(String serverId) implements ServerSelectorEvent {}
        record Refresh() implements ServerSelectorEvent {}
        record Close() implements ServerSelectorEvent {}
    }

    public static ServerSelectorModel createModel(ServerRegistryService registry, Category category) {
        return createModel(registry, category, false);
    }

    public static ServerSelectorModel createModel(ServerRegistryService registry, Category category, boolean isMobile) {
        List<ServerStatus> servers = registry.snapshot();
        String currentServer = servers.stream()
                .filter(ServerStatus::isCurrent)
                .map(s -> s.template().id())
                .findFirst()
                .orElse("");
        return new ServerSelectorModel(
                currentServer,
                category != null ? category : Category.ALL,
                servers,
                registry.totalOnlinePlayers(),
                registry.totalOnlineServers(),
                registry.totalServersCount(),
                isMobile
        );
    }

    @Override
    public ServerSelectorModel initialModel(Object context) {
        Category category = context instanceof Category cat ? cat : Category.ALL;
        boolean isMobile = session != null && session.player != null && session.player.con != null && session.player.con.mobile;
        return createModel(registryService, category, isMobile);
    }

    @Override
    public VNode render(ServerSelectorModel model) {
        UiMetrics metrics = UiMetrics.of(model.isMobile());

        return Ui.table(root -> {
            root.background("pane");
            root.margin(model.isMobile() ? 8f : 12f);
            root.layout(l -> l.width(metrics.dialogWidth()).pad(4f));

            // 1. Header: title, network status summary, and close button
            root.add(Ui.table(header -> {
                header.layout(l -> l.width(metrics.contentWidth()).padBottom(4f));
                Text title = Text.join(
                        Text.raw("[accent]" + Iconc.host + "[] [white]"),
                        Text.t("player-servers-title"),
                        Text.raw("[]")
                );
                Text summary = Text.t("player-servers-online-summary",
                        Text.args("players", model.totalOnlinePlayers(), "servers", model.totalOnlineServers()));

                if (model.isMobile()) {
                    header.add(Ui.table(topRow -> {
                        topRow.layout(l -> l.width(metrics.contentWidth()));
                        topRow.label(title, l -> l.align("left").growX());
                        topRow.button(Text.raw("[scarlet]" + Iconc.cancel + "[]"), "action:close", b -> b
                                .style("cleart")
                                .layout(l -> l.size(32f)));
                    })).row();
                    header.label(summary, l -> l.align("left").growX().padTop(2f));
                } else {
                    header.label(title, l -> l.align("left").growX());
                    header.label(summary, l -> l.align("right").padRight(8f));
                    header.button(Text.raw("[scarlet]" + Iconc.cancel + "[]"), "action:close", b -> b
                            .style("cleart")
                            .layout(l -> l.size(34f)));
                }
            })).row();

            // Divider
            root.image("whiteui", l -> l.width(metrics.contentWidth()).height(2f).padTop(4f).padBottom(6f).color("3b4252")).row();

            // 2. Category Filter Tabs
            root.add(Ui.table(tabs -> {
                tabs.layout(l -> l.width(metrics.contentWidth()).padBottom(6f));
                for (Category cat : Category.values()) {
                    boolean checked = model.selectedCategory() == cat;
                    Text tabText = Text.join(
                            Text.raw(checked ? "[accent]" : "[lightgray]"),
                            Text.raw(cat.icon() + " "),
                            Text.t(cat.bundleKey()),
                            Text.raw(" [gray]" + model.countForCategory(cat) + "[]")
                    );
                    tabs.button(tabText, "action:tab:" + cat.name().toLowerCase(), b -> b
                            .style(checked ? "togglet" : "cleart")
                            .checked(checked)
                            .layout(l -> l.height(36f).pad(2f).growX().uniform()));
                }
            })).row();

            // Divider
            root.image("whiteui", l -> l.width(metrics.contentWidth()).height(2f).padTop(2f).padBottom(6f).color("3b4252")).row();

            // 3. Dynamic Server List Slot inside ScrollPane
            root.slot(SLOT_SERVERS.path(), slot -> {
                slot.layout(l -> l.width(metrics.contentWidth()));
                slot.pane(pane -> {
                    pane.layout(l -> l.width(metrics.contentWidth()).maxHeight(metrics.paneMaxHeight()));
                    pane.table(serversTable -> renderServerList(serversTable, model, metrics));
                });
            }).row();

            // Divider
            root.image("whiteui", l -> l.width(metrics.contentWidth()).height(2f).padTop(6f).padBottom(4f).color("3b4252")).row();

            // 4. Split Footer: Hint and Refresh Button
            root.add(Ui.table(footer -> {
                footer.layout(l -> l.width(metrics.contentWidth()).padTop(2f));
                Text hintText = Text.join(
                        Text.raw("[gray]" + Iconc.info + "[] "),
                        Text.t("player-servers-hint")
                );
                Text refreshText = Text.join(
                        Text.raw(Iconc.refresh + " "),
                        Text.t("player-servers-refresh")
                );

                if (model.isMobile()) {
                    footer.label(hintText, l -> l.align("center").growX().padBottom(4f)).row();
                    footer.button(refreshText, "action:refresh", b -> b
                            .style("cleart")
                            .layout(l -> l.height(32f).growX()));
                } else {
                    footer.label(hintText, l -> l.align("left").growX().padLeft(4f).padRight(8f));
                    footer.button(refreshText, "action:refresh", b -> b
                            .style("cleart")
                            .layout(l -> l.height(34f).padRight(2f)));
                }
            })).row();
        });
    }

    private void renderServerList(Ui.TableBuilder table, ServerSelectorModel model, UiMetrics metrics) {
        table.layout(l -> l.growX());
        List<ServerStatus> servers = model.filteredServers();

        if (servers.isEmpty()) {
            table.add(Ui.table(empty -> {
                empty.layout(l -> l.width(metrics.cardWidth()).pad(24f));
                empty.label(Text.raw("[gray]" + Iconc.info + "[]"), l -> l.align("center").padBottom(6f)).row();
                empty.label(Text.t("player-servers-empty-category"), l -> l.align("center"));
            })).row();
            return;
        }

        for (ServerStatus server : servers) {
            String clickAction = "action:connect:" + server.template().id();

            table.buttonTable(clickAction, card -> {
                card.layout(l -> l.width(metrics.cardWidth()).padBottom(4f));
                card.margin(model.isMobile() ? 6f : 8f);

                if (server.isCurrent()) {
                    card.style("togglet");
                    card.checked(true);
                } else if (!server.online()) {
                    card.style("cleart");
                    card.disabled(true);
                } else {
                    card.style("default");
                }

                card.table(inner -> {
                    inner.layout(l -> l.width(metrics.cardContentWidth()));

                    // Left vertical accent stripe
                    inner.image("whiteui", l -> l.width(4f).growY().padRight(10f).color(server.template().accentColor()));

                    // Content rows
                    inner.add(Ui.table(col -> {
                        col.layout(l -> l.growX());

                        // Top line: Name, Badge, "ВЫ ЗДЕСЬ", and Capacity
                        col.add(Ui.table(top -> {
                            top.layout(l -> l.growX());
                            Text title = Text.join(
                                    Text.raw(server.template().icon() + "  [white]" + server.template().name() + "[]"
                                            + "  [#" + server.template().accentColor() + "]" + server.template().badge() + "[]"),
                                    server.isCurrent()
                                            ? Text.join(Text.raw("  "), Text.t("player-servers-badge-current"))
                                            : Text.empty()
                            );
                            top.label(title, l -> l.align("left").growX().padRight(12f));

                            Text capacity;
                            if (!server.online()) {
                                capacity = Text.t("player-servers-offline-badge");
                            } else if (server.isFull()) {
                                capacity = Text.t("player-servers-capacity-full",
                                        Text.args("players", server.onlinePlayers(), "max", server.maxPlayers()));
                            } else {
                                capacity = Text.t("player-servers-capacity-normal",
                                        Text.args("players", server.onlinePlayers(), "max", server.maxPlayers(), "bar", server.capacityBar()));
                            }
                            top.label(capacity, l -> l.align("right").padRight(4f));
                        })).row();

                        // Bottom line: Dynamic description from server/heartbeat, Map/Mode info and Telemetry
                        col.add(Ui.table(bottom -> {
                            bottom.layout(l -> l.growX().padTop(2f));
                            Text desc;
                            if (server.isCurrent()) {
                                desc = server.wave() != null
                                        ? Text.join(Text.t("player-servers-card-current"), Text.raw(" "), Text.t("player-servers-card-wave", Text.args("wave", server.wave())))
                                        : Text.t("player-servers-card-current");
                            } else if (server.onlinePlayers() == 0) {
                                desc = Text.t("player-servers-card-empty");
                            } else if (server.description() != null && !server.description().isBlank()) {
                                desc = Text.raw(formatDescription(server.description(), metrics.descMaxLength()));
                            } else if (!"-".equals(server.currentMap())) {
                                String cleanMap = formatDescription(server.currentMap(), metrics.mapMaxLength());
                                desc = server.wave() != null
                                        ? Text.join(Text.t("player-servers-card-map", Text.args("map", cleanMap)), Text.raw(" "), Text.t("player-servers-card-wave", Text.args("wave", server.wave())))
                                        : Text.t("player-servers-card-map", Text.args("map", cleanMap));
                            } else if (server.mode() != null && !server.mode().isBlank()) {
                                desc = Text.t("player-servers-card-mode", Text.args("mode", server.mode()));
                            } else {
                                desc = Text.empty();
                            }
                            bottom.label(desc, l -> l.align("left").growX().padRight(12f));

                            String telemetry = server.online()
                                    ? "[#50fa7b]" + server.tps() + " TPS[]" + (server.pingMs() > 0 ? " [sky]" + server.pingMs() + "ms[]" : "")
                                    : "[darkgray]-- TPS[]";
                            bottom.label(Text.raw(telemetry), l -> l.align("right").padRight(4f));
                        })).row();
                    }));
                });
            });
            table.row();
        }
    }

    public static String formatDescription(String raw, int maxPlainLength) {
        if (raw == null || raw.isBlank()) return "";
        String singleLine = raw.replace('\n', ' ').replace('\r', ' ').trim();
        if (arc.util.Strings.stripColors(singleLine).length() <= maxPlainLength) {
            return singleLine;
        }
        StringBuilder sb = new StringBuilder();
        int visibleCount = 0;
        boolean inTag = false;
        for (int i = 0; i < singleLine.length(); i++) {
            char c = singleLine.charAt(i);
            if (c == '[') {
                inTag = true;
                sb.append(c);
            } else if (c == ']' && inTag) {
                inTag = false;
                sb.append(c);
            } else if (inTag) {
                sb.append(c);
            } else {
                if (visibleCount >= maxPlainLength) {
                    sb.append("...[]");
                    return sb.toString();
                }
                sb.append(c);
                visibleCount++;
            }
        }
        sb.append("[]");
        return sb.toString();
    }

    @Override
    public UpdateResult<ServerSelectorModel> update(ServerSelectorModel model, ServerSelectorEvent event, ControllerContext ctx) {
        return switch (event) {
            case ServerSelectorEvent.SelectCategory e -> {
                ServerSelectorModel newModel = new ServerSelectorModel(
                        model.currentServerId(),
                        e.category(),
                        model.allServers(),
                        model.totalOnlinePlayers(),
                        model.totalOnlineServers(),
                        model.totalServersCount(),
                        model.isMobile()
                );
                yield UpdateResult.rerender(newModel);
            }
            case ServerSelectorEvent.Refresh e -> {
                ServerSelectorModel refreshed = createModel(registryService, model.selectedCategory(), model.isMobile());
                yield UpdateResult.rerender(refreshed);
            }
            case ServerSelectorEvent.Connect e -> {
                handleConnect(e.serverId());
                ctx.close();
                yield UpdateResult.close(model);
            }
            case ServerSelectorEvent.Close e -> {
                ctx.close();
                yield UpdateResult.close(model);
            }
        };
    }

    private void handleConnect(String serverId) {
        if (session == null || session.player == null) {
            return;
        }

        Optional<ServerStatus> opt = registryService.findServer(serverId);
        if (opt.isEmpty()) {
            session.locale().send("player-servers-not-found", Bundle.args("server", serverId));
            return;
        }

        ServerStatus target = opt.get();
        if (target.isCurrent()) {
            session.locale().send("player-servers-already-connected");
            return;
        }

        if (!target.online()) {
            session.locale().send("player-servers-offline", Bundle.args("server", target.template().name()));
            return;
        }

        boolean isAdmin = session.data != null && session.data.admin;
        if (target.isFull() && !isAdmin) {
            session.locale().send("player-servers-full", Bundle.args("server", target.template().name()));
            return;
        }

        if (session.player.con != null) {
            session.locale().send("player-servers-transferring", Bundle.args("server", target.template().name()));
            Log.info("Transferring player @ to @ (@:@)", session.player.plainName(), target.template().name(), target.host(), target.template().port());
            Call.connect(session.player.con, target.host(), target.template().port());
        }
    }

    @Override
    public ServerSelectorEvent parseEvent(MenuResult result) {
        if (result == null || result.wasCancelled()) {
            return new ServerSelectorEvent.Close();
        }

        String action = result.result != null ? result.result.trim() : "";
        if (action.isBlank() || "action:close".equals(action)) {
            return new ServerSelectorEvent.Close();
        }
        if ("action:refresh".equals(action)) {
            return new ServerSelectorEvent.Refresh();
        }
        if (action.startsWith("action:tab:")) {
            String catName = action.substring("action:tab:".length()).toUpperCase();
            try {
                return new ServerSelectorEvent.SelectCategory(Category.valueOf(catName));
            } catch (IllegalArgumentException ignored) {
                return new ServerSelectorEvent.SelectCategory(Category.ALL);
            }
        }
        if (action.startsWith("action:connect:")) {
            String serverId = action.substring("action:connect:".length());
            return new ServerSelectorEvent.Connect(serverId);
        }

        return new ServerSelectorEvent.Close();
    }
}
