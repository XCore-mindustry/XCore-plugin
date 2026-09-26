package org.xcore.plugin.ui.menu;

import arc.util.Log;
import mindustry.gen.Call;
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

    private static final float DIALOG_WIDTH = 540f;
    private static final float CARD_WIDTH = 525f;
    private static final float CARD_CONTENT_WIDTH = 500f;

    @Override
    public VNode render(ServerSelectorModel model) {
        return Ui.table(root -> {
            root.background("pane");
            root.margin(12f);
            root.layout(l -> l.width(DIALOG_WIDTH).pad(4f));

            // 1. Header: title, network status summary, and close button
            root.add(Ui.table(header -> {
                header.layout(l -> l.width(DIALOG_WIDTH).padBottom(4f));
                header.label(Text.t("player-servers-title"), l -> l.align("left").growX());
                header.label(Text.t("player-servers-online-summary",
                        Text.args("players", model.totalOnlinePlayers(), "servers", model.totalOnlineServers())),
                        l -> l.align("right").padRight(8f));
                header.button(Text.raw(" [scarlet]✕[] "), "action:close", b -> b
                        .style("cleart")
                        .layout(l -> l.size(34f)));
            })).row();

            // Divider
            root.image("whiteui", l -> l.width(DIALOG_WIDTH).height(2f).padTop(4f).padBottom(6f).color("3b4252")).row();

            // 2. Category Filter Tabs
            root.add(Ui.table(tabs -> {
                tabs.layout(l -> l.width(DIALOG_WIDTH).padBottom(6f));
                for (Category cat : Category.values()) {
                    boolean checked = model.selectedCategory() == cat;
                    Text tabText = Text.join(
                            Text.raw(checked ? "[accent]" : "[lightgray]"),
                            Text.t(cat.bundleKey()),
                            Text.raw(" [gray]" + model.countForCategory(cat) + "[]")
                    );
                    tabs.button(tabText, "action:tab:" + cat.name().toLowerCase(), b -> b
                            .style(checked ? "togglet" : "cleart")
                            .checked(checked)
                            .layout(l -> l.height(34f).pad(2f).growX().uniform()));
                }
            })).row();

            // Divider
            root.image("whiteui", l -> l.width(DIALOG_WIDTH).height(2f).padTop(2f).padBottom(6f).color("3b4252")).row();

            // 3. Dynamic Server List Slot inside ScrollPane
            root.slot(SLOT_SERVERS.path(), slot -> {
                slot.layout(l -> l.width(DIALOG_WIDTH));
                slot.pane(pane -> {
                    pane.layout(l -> l.width(DIALOG_WIDTH).maxHeight(340f));
                    pane.table(serversTable -> renderServerList(serversTable, model));
                });
            }).row();

            // Divider
            root.image("whiteui", l -> l.width(DIALOG_WIDTH).height(2f).padTop(6f).padBottom(4f).color("3b4252")).row();

            // 4. Split Footer: Hint and Refresh Button
            root.add(Ui.table(footer -> {
                footer.layout(l -> l.width(DIALOG_WIDTH).padTop(2f));
                footer.label(Text.t("player-servers-hint"), l -> l.align("left").growX());
                footer.button(Text.t("player-servers-refresh"), "action:refresh", b -> b
                        .style("cleart")
                        .layout(l -> l.height(32f).width(120f)));
            })).row();
        });
    }

    private void renderServerList(Ui.TableBuilder table, ServerSelectorModel model) {
        table.layout(l -> l.growX());
        List<ServerStatus> servers = model.filteredServers();

        if (servers.isEmpty()) {
            table.label(Text.t("player-servers-empty-category"), l -> l.align("center").pad(20f)).row();
            return;
        }

        for (ServerStatus server : servers) {
            String clickAction = "action:connect:" + server.template().id();

            table.buttonTable(clickAction, card -> {
                card.layout(l -> l.width(CARD_WIDTH).padBottom(4f));
                card.margin(6f);

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
                    inner.layout(l -> l.width(CARD_CONTENT_WIDTH));

                    // Left vertical accent stripe
                    inner.image("whiteui", l -> l.width(4f).growY().padRight(8f).color(server.template().accentColor()));

                    // Content rows
                    inner.add(Ui.table(col -> {
                        col.layout(l -> l.growX());

                        // Top line: Name, Badge, "ВЫ ЗДЕСЬ", and Capacity
                        col.add(Ui.table(top -> {
                            top.layout(l -> l.growX());
                            Text title = Text.join(
                                    Text.raw(server.template().icon() + " [white]" + server.template().name() + "[]"
                                            + "  [#" + server.template().accentColor() + "]" + server.template().badge() + "[]"),
                                    server.isCurrent()
                                            ? Text.join(Text.raw(" "), Text.t("player-servers-badge-current"))
                                            : Text.empty()
                            );
                            top.label(title, l -> l.align("left").growX());

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
                            top.label(capacity, l -> l.align("right"));
                        })).row();

                        // Bottom line: Dynamic description from server/heartbeat, Map/Mode info and Telemetry
                        col.add(Ui.table(bottom -> {
                            bottom.layout(l -> l.growX());
                            Text desc;
                            if (server.isCurrent()) {
                                desc = server.wave() != null
                                        ? Text.join(Text.t("player-servers-card-current"), Text.raw(" "), Text.t("player-servers-card-wave", Text.args("wave", server.wave())))
                                        : Text.t("player-servers-card-current");
                            } else if (server.onlinePlayers() == 0) {
                                desc = Text.t("player-servers-card-empty");
                            } else if (server.description() != null && !server.description().isBlank()) {
                                desc = Text.raw(formatDescription(server.description(), 42));
                            } else if (!"-".equals(server.currentMap())) {
                                String cleanMap = formatDescription(server.currentMap(), 22);
                                desc = server.wave() != null
                                        ? Text.join(Text.t("player-servers-card-map", Text.args("map", cleanMap)), Text.raw(" "), Text.t("player-servers-card-wave", Text.args("wave", server.wave())))
                                        : Text.t("player-servers-card-map", Text.args("map", cleanMap));
                            } else if (server.mode() != null && !server.mode().isBlank()) {
                                desc = Text.t("player-servers-card-mode", Text.args("mode", server.mode()));
                            } else {
                                desc = Text.empty();
                            }
                            bottom.label(desc, l -> l.align("left").growX());

                            String telemetry = server.online()
                                    ? "[#50fa7b]" + server.tps() + " TPS[]" + (server.pingMs() > 0 ? " [sky]" + server.pingMs() + "ms[]" : "")
                                    : "[darkgray]-- TPS[]";
                            bottom.label(Text.raw(telemetry), l -> l.align("right"));
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
                yield UpdateResult.patch(newModel, SLOT_SERVERS);
            }
            case ServerSelectorEvent.Refresh e -> {
                ServerSelectorModel refreshed = createModel(registryService, model.selectedCategory());
                yield UpdateResult.patch(refreshed, SLOT_SERVERS);
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
        Optional<ServerStatus> opt = registryService.findServer(serverId);
        if (opt.isEmpty()) {
            if (session != null && session.player != null) {
                String msg = session.locale() != null
                        ? session.locale().format("player-servers-not-found", com.ospx.flubundle.Bundle.args("server", serverId))
                        : "[scarlet]Сервер '" + serverId + "' не найден.";
                session.player.sendMessage(msg);
            }
            return;
        }

        ServerStatus target = opt.get();
        if (target.isCurrent()) {
            if (session != null && session.player != null) {
                String msg = session.locale() != null
                        ? session.locale().format("player-servers-already-connected", com.ospx.flubundle.Bundle.args())
                        : "[gold]● Вы уже подключены к этому серверу!";
                session.player.sendMessage(msg);
            }
            return;
        }

        if (!target.online()) {
            if (session != null && session.player != null) {
                String msg = session.locale() != null
                        ? session.locale().format("player-servers-offline", com.ospx.flubundle.Bundle.args("server", target.template().name()))
                        : "[scarlet]Сервер " + target.template().name() + " сейчас оффлайн.";
                session.player.sendMessage(msg);
            }
            return;
        }

        boolean isAdmin = session != null && session.data != null && session.data.admin;
        if (target.isFull() && !isAdmin) {
            if (session != null && session.player != null) {
                String msg = session.locale() != null
                        ? session.locale().format("player-servers-full", com.ospx.flubundle.Bundle.args("server", target.template().name()))
                        : "[scarlet]Сервер " + target.template().name() + " заполнен! Подождите освобождения слота.";
                session.player.sendMessage(msg);
            }
            return;
        }

        if (session != null && session.player != null && session.player.con != null) {
            String msg = session.locale() != null
                    ? session.locale().format("player-servers-transferring", com.ospx.flubundle.Bundle.args("server", target.template().name()))
                    : "[accent]Переключение на сервер [white]" + target.template().name() + "[]...";
            session.player.sendMessage(msg);
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
