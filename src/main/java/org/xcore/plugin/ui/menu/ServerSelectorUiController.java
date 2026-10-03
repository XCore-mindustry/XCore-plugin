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
import org.xcore.ui.responsive.DialogMetrics;
import org.xcore.ui.responsive.Responsive;
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

    /**
     * View state for the server browser.
     *
     * <p>No device or orientation flags live here. The server cannot know either — {@code ConnectPacket}
     * carries only {@code mobile}, and the camera dimensions in {@code clientSnapshot} describe the world
     * view rather than the screen — so anything the dialog needs to adapt is expressed in the tree and
     * resolved by the client.
     */
    public record ServerSelectorModel(
            String currentServerId,
            Category selectedCategory,
            List<ServerStatus> allServers,
            int totalOnlinePlayers,
            int totalOnlineServers,
            int totalServersCount
    ) {
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
                registry.totalServersCount()
        );
    }

    @Override
    public ServerSelectorModel initialModel(Object context) {
        Category category = context instanceof Category cat ? cat : Category.ALL;
        return createModel(registryService, category);
    }

    @Override
    public VNode render(ServerSelectorModel model) {
        return render(model, metrics());
    }

    /** Caps only; the client resolves the actual width and body height. */
    private DialogMetrics metrics() {
        return DialogMetrics.standard();
    }

    private VNode render(ServerSelectorModel model, DialogMetrics metrics) {
        return Ui.table(root -> {
            root.background("pane");
            root.margin(8f);
            root.layout(l -> l.growX().maxWidth(metrics.maxDialogWidth()).pad(4f));

            // 1. Header: title, network status summary, and close button
            root.add(Ui.table(header -> {
                header.layout(l -> l.growX().padBottom(4f));
                Text title = Text.join(
                        Text.raw("[accent]" + Iconc.host + "[] [white]"),
                        Text.t("player-servers-title"),
                        Text.raw("[]")
                );
                Text summary = Text.t("player-servers-online-summary",
                        Text.args("players", model.totalOnlinePlayers(), "servers", model.totalOnlineServers()));

                // Narrow screens stack the summary under the title; wide ones keep it on the
                // same line. That is a structural difference, not a size one, so it goes through
                // a native client-side condition rather than a server-side guess.
                Responsive.portrait(header, p -> {
                    p.add(Ui.table(topRow -> {
                        topRow.layout(l -> l.growX());
                        topRow.label(title, l -> l.align("left").growX());
                        topRow.button(Text.raw("[scarlet]" + Iconc.cancel + "[]"), "action:close", b -> b
                                .style("cleart")
                                .layout(l -> l.size(32f)));
                    })).row();
                    p.label(summary, l -> l.align("left").growX().padTop(2f));
                });
                Responsive.landscape(header, w -> {
                    w.label(title, l -> l.align("left").growX());
                    w.label(summary, l -> l.align("right").padRight(8f));
                    w.button(Text.raw("[scarlet]" + Iconc.cancel + "[]"), "action:close", b -> b
                            .style("cleart")
                            .layout(l -> l.size(34f)));
                });
            })).row();

            // Divider
            root.image("whiteui", l -> l.growX().height(2f).padTop(4f).padBottom(6f).color("3b4252")).row();

            // 2. Category Filter Tabs
            root.add(Ui.table(tabs -> {
                // WrapTable picks the tab columns from the width the client gives it, so the
                // same tab set fits a narrow phone and a wide desktop without a server guess
                tabs.wrap();
                tabs.layout(l -> l.growX().padBottom(6f));
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
            root.image("whiteui", l -> l.growX().height(2f).padTop(2f).padBottom(6f).color("3b4252")).row();

            // 3. Dynamic Server List Slot inside ScrollPane
            root.slot(SLOT_SERVERS.path(), slot -> {
                slot.layout(l -> l.growX());
                slot.pane(pane -> {
                    pane.layout(l -> l.growX().growY().maxHeight(metrics.maxBodyHeight()));
                    pane.table(serversTable -> renderServerList(serversTable, model, metrics));
                });
            }).row();

            // Divider
            root.image("whiteui", l -> l.growX().height(2f).padTop(6f).padBottom(4f).color("3b4252")).row();

            // 4. Split Footer: Hint and Refresh Button
            root.add(Ui.table(footer -> {
                footer.layout(l -> l.growX().padTop(2f));
                Text hintText = Text.join(
                        Text.raw("[gray]" + Iconc.info + "[] "),
                        Text.t("player-servers-hint")
                );
                Text refreshText = Text.join(
                        Text.raw(Iconc.refresh + " "),
                        Text.t("player-servers-refresh")
                );

                // A narrow screen has no room for hint + button side by side, so the hint
                // takes its own row. Structural, not a size guess: the client decides.
                Responsive.portrait(footer, p -> {
                    p.label(hintText, l -> l.align("center").growX().padBottom(4f)).row();
                    p.button(refreshText, "action:refresh", b -> b
                            .style("cleart")
                            .layout(l -> l.height(32f).growX()));
                });
                Responsive.landscape(footer, w -> {
                    w.label(hintText, l -> l.align("left").growX().padLeft(4f).padRight(8f));
                    w.button(refreshText, "action:refresh", b -> b
                            .style("cleart")
                            .layout(l -> l.height(34f).padRight(2f)));
                });
            })).row();
        });
    }

    private void renderServerList(Ui.TableBuilder table, ServerSelectorModel model, DialogMetrics metrics) {
        table.layout(l -> l.growX());
        List<ServerStatus> servers = model.filteredServers();

        if (servers.isEmpty()) {
            table.add(Ui.table(empty -> {
                empty.layout(l -> l.growX().pad(24f));
                empty.label(Text.raw("[gray]" + Iconc.info + "[]"), l -> l.align("center").padBottom(6f)).row();
                empty.label(Text.t("player-servers-empty-category"), l -> l.align("center"));
            })).row();
            return;
        }

        for (ServerStatus server : servers) {
            String clickAction = "action:connect:" + server.template().id();

            table.buttonTable(clickAction, card -> {
                card.layout(l -> l.growX().padBottom(4f));
                card.margin(8f);

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
                    inner.layout(l -> l.growX());

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
                                desc = Text.raw(formatDescription(server.description(), metrics.textBudget()));
                            } else if (!"-".equals(server.currentMap())) {
                                String cleanMap = formatDescription(server.currentMap(), metrics.textBudget());
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
                        model.totalServersCount()
                );
                yield UpdateResult.rerender(newModel);
            }
            case ServerSelectorEvent.Refresh e -> {
                ServerSelectorModel refreshed = createModel(registryService, model.selectedCategory());
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
