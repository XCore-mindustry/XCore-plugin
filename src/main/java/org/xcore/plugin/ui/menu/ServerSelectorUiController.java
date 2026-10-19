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
import org.xcore.plugin.ui.kit.Accent;
import org.xcore.plugin.ui.kit.Kit;
import org.xcore.plugin.ui.kit.Screen;
import org.xcore.plugin.ui.kit.TextWidth;
import org.xcore.ui.Ui;
import org.xcore.ui.VNode;
import org.xcore.ui.runtime.ControllerContext;
import org.xcore.ui.runtime.UiController;
import org.xcore.ui.runtime.UpdateResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static com.ospx.flubundle.Bundle.args;
import static org.xcore.plugin.ui.kit.Kit.GAP;
import static org.xcore.plugin.ui.kit.Texts.t;

/**
 * The server browser ({@code /servers}, {@code /hub}, {@code /play}): a tab per kind of server and
 * every server as a row that connects to it, laid out once per {@link Screen}.
 */
public class ServerSelectorUiController implements UiController<ServerSelectorUiController.ServerSelectorModel, ServerSelectorUiController.ServerSelectorEvent> {

    private static final float STRIPE = 4f;
    private static final float REFRESH_WIDTH = 200f;

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
            int totalServersCount,
            String feedback
    ) {
        public ServerSelectorModel(String currentServerId, Category selectedCategory, List<ServerStatus> allServers,
                                   int totalOnlinePlayers, int totalOnlineServers, int totalServersCount) {
            this(currentServerId, selectedCategory, allServers, totalOnlinePlayers, totalOnlineServers, totalServersCount, "");
        }

        public ServerSelectorModel withFeedback(String message) {
            return new ServerSelectorModel(currentServerId, selectedCategory, allServers, totalOnlinePlayers,
                    totalOnlineServers, totalServersCount, message);
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
        return Screen.each(screen -> window(model, screen));
    }

    /** The window as one {@link Screen} sees it. */
    VNode window(ServerSelectorModel model, Screen screen) {
        float width = screen.width();
        Accent accent = accent(model.selectedCategory());

        return Kit.window(window -> {
            window.add(Kit.header(width, "[accent]" + Iconc.host + "[] [white]" + t(session, "player-servers-title") + "[]\n"
                    + t(session, "player-servers-online-summary",
                    args("players", model.totalOnlinePlayers(), "servers", model.totalOnlineServers())))).row();

            List<Kit.Tab> tabs = new ArrayList<>();
            for (Category category : Category.values()) {
                tabs.add(new Kit.Tab(category.icon(),
                        t(session, category.bundleKey()) + " [gray]" + model.countForCategory(category) + "[]",
                        "action:tab:" + category.name().toLowerCase(Locale.ROOT),
                        accent(category), model.selectedCategory() == category));
            }
            window.add(Kit.tabs(width, "server_tabs", tabs)).row();
            window.add(Kit.line(width, accent)).row();

            Kit.body(window, screen, list -> servers(list, model, screen));

            window.add(footer(screen)).row();
        });
    }

    private static Accent accent(Category category) {
        return switch (category) {
            case ALL -> Accent.GOLD;
            case PVP -> Accent.RED;
            case SURVIVAL -> Accent.GREEN;
            case SPECIAL -> Accent.PURPLE;
        };
    }

    private void servers(Ui.TableBuilder list, ServerSelectorModel model, Screen screen) {
        if (model.feedback() != null && !model.feedback().isBlank()) {
            list.add(Kit.note(screen.cards(), model.feedback())).row();
        }
        List<ServerStatus> servers = model.filteredServers();
        if (servers.isEmpty()) {
            list.add(Kit.note(screen.cards(), t(session, "player-servers-empty-category"))).row();
            return;
        }
        List<VNode> cards = new ArrayList<>();
        for (ServerStatus server : servers) {
            cards.add(serverCard(server, screen.card()));
        }
        list.add(Kit.columns(screen, cards)).row();
    }

    /**
     * A server as a row that connects to it: a stripe in the server's colour, then its name, how
     * full it is and what is being played. Every card has the same three lines, so two columns
     * of them stay level.
     */
    private VNode serverCard(ServerStatus server, float width) {
        return Kit.row("action:connect:" + server.template().id(), width, server.isCurrent(), server.online(),
                (row, inner) -> {
                    float text = inner - STRIPE - GAP;
                    row.add(Ui.image("whiteui", l -> l.width(STRIPE).growY().padRight(GAP)
                            .color(server.template().accentColor())));
                    row.add(Kit.text(name(server, text) + "\n"
                            + TextWidth.fit(load(server), text) + "\n"
                            + playing(server, text), text));
                });
    }

    /** The name with the server's tag, and the mark of the server the player is on; the tag goes first when both do not fit. */
    private String name(ServerStatus server, float width) {
        String name = server.template().icon() + " [white]" + server.template().name() + "[]";
        String tag = " [#" + server.template().accentColor() + "]" + server.template().badge() + "[]";
        if (!server.isCurrent()) {
            return TextWidth.fit(name + tag, width);
        }
        String here = "  " + t(session, "player-servers-badge-current");
        if (TextWidth.of(name + tag + here) <= width) {
            return name + tag + here;
        }
        return TextWidth.fit(name + here, width);
    }

    private String load(ServerStatus server) {
        if (!server.online()) {
            return t(session, "player-servers-offline-badge");
        }
        String players = server.isFull()
                ? t(session, "player-servers-capacity-full", args("players", server.onlinePlayers(), "max", server.maxPlayers()))
                : t(session, "player-servers-capacity-normal", args("players", server.onlinePlayers(),
                "max", server.maxPlayers(), "bar", server.capacityBar()));
        return players + "  [#50fa7b]" + server.tps() + " TPS[]"
                + (server.pingMs() > 0 ? " [sky]" + server.pingMs() + "ms[]" : "");
    }

    /** What is going on there, in one line; a space where there is nothing to say keeps the line. */
    private String playing(ServerStatus server, float width) {
        String wave = server.wave() != null ? " " + t(session, "player-servers-card-wave", args("wave", server.wave())) : "";
        String line;
        if (!server.online()) {
            line = "";
        } else if (server.isCurrent()) {
            line = t(session, "player-servers-card-current") + wave;
        } else if (server.onlinePlayers() == 0) {
            line = t(session, "player-servers-card-empty");
        } else if (server.description() != null && !server.description().isBlank()) {
            line = "[lightgray]" + server.description().trim() + "[]";
        } else if (server.currentMap() != null && !"-".equals(server.currentMap())) {
            line = t(session, "player-servers-card-map", args("map", server.currentMap())) + wave;
        } else if (server.mode() != null && !server.mode().isBlank()) {
            line = t(session, "player-servers-card-mode", args("mode", server.mode()));
        } else {
            line = "";
        }
        return line.isEmpty() ? " " : TextWidth.fit(line, width);
    }

    /** The hint and the button that reads the servers anew: side by side where there is room, the button under the hint where not. */
    private VNode footer(Screen screen) {
        float width = screen.width();
        String hint = "[gray]" + Iconc.info + "[] [lightgray]" + t(session, "player-servers-hint") + "[]";
        Kit.Action refresh = new Kit.Action("[sky]" + Iconc.refresh + "[] " + t(session, "player-servers-refresh"), "action:refresh");
        return Ui.table(footer -> {
            footer.layout(l -> l.padTop(GAP));
            if (screen.columns() > 1) {
                footer.add(Kit.text(hint, width - REFRESH_WIDTH - GAP));
                footer.add(Ui.table(side -> {
                    side.layout(l -> l.padLeft(GAP));
                    side.add(Kit.button(refresh, REFRESH_WIDTH));
                }));
            } else {
                footer.add(Ui.table(above -> {
                    above.layout(l -> l.padBottom(GAP));
                    above.add(Kit.centered(hint, width));
                })).row();
                footer.add(Kit.button(refresh, width)).row();
            }
        });
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
                ConnectionAttempt attempt = handleConnect(e.serverId());
                if (attempt.transferred()) {
                    ctx.close();
                    yield UpdateResult.close(model);
                }
                yield UpdateResult.rerender(createModel(registryService, model.selectedCategory()).withFeedback(attempt.feedback()));
            }
            case ServerSelectorEvent.Close e -> {
                ctx.close();
                yield UpdateResult.close(model);
            }
        };
    }

    private record ConnectionAttempt(boolean transferred, String feedback) {}

    private ConnectionAttempt rejected(String key, Map<String, Object> arguments) {
        session.locale().send(key, arguments);
        return new ConnectionAttempt(false, t(session, key, arguments));
    }

    private ConnectionAttempt handleConnect(String serverId) {
        if (session == null || session.player == null) {
            return new ConnectionAttempt(false, "");
        }

        Optional<ServerStatus> opt = registryService.findServer(serverId);
        if (opt.isEmpty()) {
            return rejected("player-servers-not-found", Bundle.args("server", serverId));
        }

        ServerStatus target = opt.get();
        if (target.isCurrent()) {
            return rejected("player-servers-already-connected", Map.of());
        }

        if (!target.online()) {
            return rejected("player-servers-offline", Bundle.args("server", target.template().name()));
        }

        boolean isAdmin = session.data != null && session.data.admin;
        if (target.isFull() && !isAdmin) {
            return rejected("player-servers-full", Bundle.args("server", target.template().name()));
        }

        if (session.player.con != null) {
            session.locale().send("player-servers-transferring", Bundle.args("server", target.template().name()));
            Log.info("Transferring player @ to @ (@:@)", session.player.plainName(), target.template().name(), target.host(), target.template().port());
            Call.connect(session.player.con, target.host(), target.template().port());
            return new ConnectionAttempt(true, "");
        }
        return new ConnectionAttempt(false, "");
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
            String catName = action.substring("action:tab:".length()).toUpperCase(Locale.ROOT);
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
