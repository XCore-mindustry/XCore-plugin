package tilelogger;

import arc.Events;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Nullable;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Player;
import mindustry.gen.RotateBlockCallPacket;
import mindustry.mod.Plugin;
import mindustry.net.Administration.PlayerInfo;
import mindustry.world.Block;
import org.xcore.plugin.modules.votes.VoteKick;
import org.xcore.plugin.utils.Find;
import org.xcore.plugin.utils.models.PlayerData;
import useful.Bundle;

import static mindustry.Vars.netServer;
import static org.xcore.plugin.PluginVars.database;
import static useful.Bundle.send;

@SuppressWarnings("unused")
public class TileLoggerPlugin extends Plugin {

    private void sendBundled(@Nullable Player player, String msg) {
        if (player == null)
            Log.info(msg);
        else
            send(player, msg);
    }

    @Override
    public void init() {
        Bundle.load(TileLoggerPlugin.class);

        VoteKick.setOnKick(player -> TileLogger.rollback(null, player.getInfo(), -1, -180, new Rect((short) 0, (short) 0, (short) (Vars.world.width() - 1), (short) (Vars.world.height() - 1))));

        Events.on(EventType.BlockBuildBeginEvent.class, event -> {
            if (event.unit == null) return; // rollback recursion
            event.tile.block().iterateTaken(event.tile.x, event.tile.y, (x, y) -> {
                if (!event.breaking && event.tile.x == x && event.tile.y == y)
                    TileLogger.build(event.tile, TileLogger.unitToPlayerInfo(event.unit));
                else
                    TileLogger.destroy(Vars.world.tile(x, y), TileLogger.unitToPlayerInfo(event.unit));
            });
        });
        Events.on(EventType.BlockBuildEndEvent.class, event -> {
            if (event.breaking) return; // already handled by BlockBuildBeginEvent
            if (event.unit == null) return; // rollback recursion
            TileLogger.build(event.tile, TileLogger.unitToPlayerInfo(event.unit));
        });
        Events.on(EventType.ConfigEvent.class, event -> {
            TileLogger.build(event.tile.tile, event.player == null ? null : event.player.getInfo());
        });
        Events.on(EventType.PickupEvent.class, event -> {
            if (event.build == null) return; // payload is unit
            TileLogger.destroy(event.build.tile, TileLogger.unitToPlayerInfo(event.carrier));
        });
        Events.on(EventType.PayloadDropEvent.class, event -> {
            if (event.build == null) return; // payload is unit
            TileLogger.build(event.build.tile, TileLogger.unitToPlayerInfo(event.carrier));
        });
        Events.on(EventType.BlockDestroyEvent.class, event -> {
            TileLogger.destroy(event.tile, null);
        });
        Vars.net.handleServer(RotateBlockCallPacket.class, (con, packet) -> {
            packet.handled();
            packet.handleServer(con);
            TileLogger.build(packet.build.tile, con.player.getInfo());
        });

        Events.on(EventType.PlayEvent.class, event -> TileLogger.resetHistory(null, true));
        Events.on(EventType.TapEvent.class, event -> {
            if (event.tile == null) return;

            PlayerConfig config = PlayerConfig.get(event.player);
            if (config.history_size > 0) {
                TileLogger.showHistory(event.player, (short) event.tile.centerX(), (short) event.tile.centerY(), config.history_size);
            } else if (database.getCached(event.player.uuid()).adminModVersion != null && !event.player.con.mobile) {
                TileLogger.sendTileHistory((short) event.tile.centerX(), (short) event.tile.centerY(), event.player);
            }

            switch (config.select) {
                case 1 -> {
                    config.rect.x1 = event.tile.x;
                    config.rect.y1 = event.tile.y;
                    config.select++;
                }
                case 2 -> {
                    config.rect.x2 = event.tile.x;
                    config.rect.y2 = event.tile.y;
                    config.select++;
                    config.rect.normalize();
                    TileLogger.sendMessage(event.player, "Selected tiles: " + config.rect.area());
                }
                default -> {
                }
            }
        });
    }

    public void registerCommands(CommandHandler handler) {
        handler.<Player>register("tl", "[args...]", "TileLogger commands handler.", (args, player) -> {
            if (args.length == 0) {
                TileLogger.showInfo(player);
                return;
            }
            Seq<String> args_seq = new Seq<>(args[0].split(" ")).reverse();
            PlayerConfig config = PlayerConfig.get(player);
            switch (args_seq.pop()) {
                case "memory", "m" -> TileLogger.showMemoryUsage(player);
                case "select", "s" -> {
                    if (player == null) {
                        sendBundled(player, "error.not-allowed-from-console");
                        return;
                    }
                    config.select = 1;
                }
                case "fill", "f" -> {
                    if (player == null) {
                        sendBundled(player, "error.not-allowed-from-console");
                        return;
                    }
                    if (!player.admin) {
                        sendBundled(player, "error.access-denied");
                        return;
                    }
                    if (args_seq.size == 0) {
                        sendBundled(player, "error.not-enough-params");
                        return;
                    }
                    Block block = Vars.content.block(args_seq.pop());
                    if (block == null) {
                        sendBundled(player, "error.block-not-found");
                        return;
                    }
                    TileLogger.fill(player, null, block, config.rect);
                }
                case "file" -> {
                    if (player != null) {
                        sendBundled(player, "error.not-allowed-from-player");
                        return;
                    }
                    if (args_seq.size == 0) {
                        sendBundled(player, "error.not-enough-params");
                        return;
                    }
                    TileLogger.resetHistory(args_seq.pop(), args_seq.pop(String::new).equals("w"));
                }
                default -> sendBundled(player, "error.unknown-command");
            }
        });
        handler.<Player>register("history", "[size] [uuid/x] [y]", "Shows tile history.", (args, player) -> {
            try {
                PlayerConfig config = PlayerConfig.get(player);

                if (args.length == 2) {
                    long size = Strings.parseLong(args[0], 0);
                    if (config.history_size > 0) {
                        TileLogger.showHistory(player, Find.playerInfo(args[1]), size);
                    } else if (database.getCached(player.uuid()).adminModVersion != null && !player.con.mobile) {
                        TileLogger.sendTileHistory(Find.playerInfo(args[1]), player);
                    }
                    return;
                } else if (args.length == 3) {
                    long size = Strings.parseLong(args[0], 0);
                    short x = Short.parseShort(args[1]);
                    short y = Short.parseShort(args[2]);
                    TileLogger.showHistory(player, x, y, size);
                    return;
                }

                if (args.length > 0) {
                    config.history_size = Strings.parseInt(args[0], 0);
                } else if (config.history_size == 0) {
                    config.history_size = 6;
                } else {
                    config.history_size = 0;
                }

                send(player, "commands.history.success", config.history_size);
            } catch (NumberFormatException e) {
                sendBundled(player, "error.wrong-number");
            }
        });

        handler.<Player>register("rollback", "<name/uuid> [time] [flags]", "Rolls back tiles.", (args, player) -> {
            if (player != null && !player.admin) {
                sendBundled(player, "error.access-denied");
                return;
            }
            try {
                PlayerConfig config = PlayerConfig.get(player);
                Rect rect = new Rect((short) 0, (short) 0, (short) (Vars.world.width() - 1), (short) (Vars.world.height() - 1));
                int time = 0;
                if (args.length > 1) {
                    String[] times = args[1].split(":", 3);
                    for (int i = 0; i < times.length; i++) {
                        time += Math.abs(Integer.parseInt(times[times.length - 1 - i])) * Math.pow(60, i);
                    }
                    if (args[1].startsWith("-"))
                        time *= -1;
                }
                String uuid = args[0].equals("all") ? null : args[0];
                if (args.length > 2) {
                    rect = config.rect;
                }

                PlayerInfo target = null;
                if (uuid != null) {
                    if (uuid.equals("self")) {
                        if (player != null) {
                            target = player.getInfo();
                        }
                    } else {
                        PlayerData data = database.getCachedOrDb(Strings.parseInt(uuid));
                        target = data != null ? netServer.admins.getInfoOptional(data.uuid) : null;
                    }

                    if (target == null) {
                        sendBundled(player, "error.player-not-found");
                        return;
                    }
                }
                TileLogger.rollback(player, target, -1, time, rect);
            } catch (NumberFormatException e) {
                sendBundled(player, "error.wrong-number");
            }
        });
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        registerCommands(handler);
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        registerCommands(handler);
    }
}
