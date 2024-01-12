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
import mindustry.mod.Plugin;
import mindustry.world.Block;
import mindustry.world.blocks.power.PowerNode.PowerNodeBuild;

public class TileLoggerPlugin extends Plugin {

    private void send(@Nullable Player player, String msg, Object... values) {
        if (player == null)
            Log.info(msg, values);
        else
            //XCoreIntegration.sendBundled(player, msg, values);
            player.sendMessage(String.format(msg, values));
    }

    @Override
    public void init() {
        XCoreIntegration.init();
        
        Events.on(EventType.BuildSelectEvent.class, event -> {
            if (event.builder == null) return; // rollback recursion
            if (event.breaking) return; // handled by BlockBuildBeginEvent 
            TileLogger.build(event.tile, TileLogger.unitToPlayerInfo(event.builder));
        });
        Events.on(EventType.BlockBuildBeginEvent.class, event -> {
            // prevent duplicate build/destroy events when muiltiple player acting on the same building
            if (event.unit == null) return; // rollback recursion
            if (!event.breaking) return; // handled by BuildSelectEvent, prevent initial config duplication
            TileLogger.destroy(event.tile, TileLogger.unitToPlayerInfo(event.unit));
        });
        Events.on(EventType.BlockBuildEndEvent.class, event -> {
            // need for low build time blocks
            if (event.unit == null) return; // rollback recursion
            if (event.breaking) return; // handled by BlockBuildBeginEvent
            TileLogger.build(event.tile, TileLogger.unitToPlayerInfo(event.unit));
        });
        Events.on(EventType.BuildRotateEvent.class, event -> {
            // need for rotate with replace
            if (event.unit == null) return; // rollback recursion
            TileLogger.build(event.build.tile, TileLogger.unitToPlayerInfo(event.unit));
        });
        Events.on(EventType.ConfigEvent.class, event -> {
            if (event.tile == null) return; // rollback recursion
            if (event.tile instanceof PowerNodeBuild && event.value instanceof Integer i)
                TileLogger.build(Vars.world.tile(i), event.player == null ? null : event.player.getInfo());
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

        Events.on(EventType.PlayEvent.class, event -> TileLogger.resetHistory("", true));
        Events.on(EventType.TapEvent.class, event -> {
            if (event.tile == null) return;

            PlayerConfig config = PlayerConfig.get(event.player);
            if (config.history_size > 0) {
                TileLogger.showHistory(event.player, (short) event.tile.centerX(), (short) event.tile.centerY(), config.history_size);
            }
            if (XCoreIntegration.useAdminTools(event.player)) {
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
                    if (player == null) { send(player, "error.not-allowed-from-console"); return; }
                    config.select = 1;
                }
                case "fill", "f" -> {
                    if (player == null) { send(player, "error.not-allowed-from-console"); return; }
                    if (!player.admin) { send(player, "error.access-denied"); return; }
                    if (args_seq.size == 0) { send(player, "error.not-enough-params"); return; }
                    Block block = Vars.content.block(args_seq.pop());
                    if (block == null) { send(player, "error.block-not-found"); return; }
                    TileLogger.fill(player, null, block, config.rect);
                }
                case "file" -> {
                    if (player != null) { send(player, "error.not-allowed-from-player"); return; }
                    if (args_seq.size == 0) { send(player, "error.not-enough-params"); return; }
                    TileLogger.resetHistory(args_seq.pop(), args_seq.pop(String::new).equals("w"));
                }
                case "subnet" -> {
                    if (args_seq.size == 0) { send(player, "error.not-enough-params"); return; }
                    TileLogger.showSubnetInfo(player, args_seq.pop());
                }
                default -> send(player, "error.unknown-command");
            }
        });
        handler.<Player>register("history", "[size] [name/id/uuid/x] [y]", "Shows tile history.", (args, player) -> {
            try {
                Seq<String> args_seq = new Seq<>(args).reverse();
                PlayerConfig config = PlayerConfig.get(player);

                switch (args_seq.size) {
                    case 0: {
                        if (config.history_size == 0) {
                            config.history_size = 6;
                        } else {
                            config.history_size = 0;
                        }
                        send(player, "commands.history.success", config.history_size);
                        break;
                    }
                    case 1: {
                        config.history_size = Integer.parseInt(args_seq.pop());
                        send(player, "commands.history.success", config.history_size);
                        break;
                    }
                    case 2: {
                        long size = Long.parseLong(args_seq.pop());
                        PlayerDescriptor target = XCoreIntegration.findPlayer(args_seq.pop());
                        if (target == null) { send(player, "error.player-not-found"); return; }
                        if (size > 0) {
                            TileLogger.showHistory(player, target, size);
                        }
                        if (XCoreIntegration.useAdminTools(player)) {
                            TileLogger.sendTileHistory(target, player);
                        }
                        break;
                    }
                    case 3: {
                        long size = Long.parseLong(args_seq.pop());
                        short x = Short.parseShort(args_seq.pop());
                        short y = Short.parseShort(args_seq.pop());
                        TileLogger.showHistory(player, x, y, size);
                        break;
                    }
                    default:
                        send(player, "error.too-many-params");
                        return;
                }
            } catch (NumberFormatException e) {
                send(player, "error.wrong-number");
            }
        });

        handler.<Player>register("rollback", "<name/id/uuid> [time] [flags]", "Rolls back tiles.", (args, player) -> {
            if (player != null && !player.admin) {
                send(player, "error.access-denied");
                return;
            }
            try {
                Seq<String> args_seq = new Seq<>(args).reverse();
                
                String name = args_seq.pop();
                PlayerDescriptor target = player != null && name.equals("self") ? XCoreIntegration.findPlayerUuid(player.uuid()) : XCoreIntegration.findPlayer(name);
                if (target == null) { send(player, "error.player-not-found"); return; }

                int time = 0;
                if (args_seq.size > 0) {
                    String time_arg = args_seq.pop();
                    String[] times = time_arg.split(":", 3);
                    for (int i = 0; i < times.length; i++) {
                        time += Math.abs(Integer.parseInt(times[times.length - 1 - i])) * Math.pow(60, i);
                    }
                    if (time_arg.startsWith("-"))
                        time *= -1;
                }

                Rect rect = new Rect((short) 0, (short) 0, (short) (Vars.world.width() - 1), (short) (Vars.world.height() - 1));
                if (args_seq.size > 0) {
                    if (args_seq.pop().equals("s")) {
                        PlayerConfig config = PlayerConfig.get(player);
                        rect = config.rect;
                    }
                    else { send(player, "error.unknown-command"); return; }
                }

                TileLogger.rollback(player, target, -1, time, rect);
            } catch (NumberFormatException e) {
                send(player, "error.wrong-number");
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
