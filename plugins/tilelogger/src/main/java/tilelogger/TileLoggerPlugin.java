package tilelogger;

import arc.Events;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Nullable;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.gen.RotateBlockCallPacket;
import mindustry.io.JsonIO;
import mindustry.mod.Plugin;
import mindustry.net.Administration.PlayerInfo;
import org.xcore.plugin.modules.Database;
import org.xcore.plugin.modules.votes.VoteKick;
import org.xcore.plugin.utils.Find;
import org.xcore.plugin.utils.models.PlayerData;
import useful.Bundle;

import static org.xcore.plugin.commands.ClientCommands.register;
import static useful.Bundle.send;

@SuppressWarnings("unused")
public class TileLoggerPlugin extends Plugin {

    private void SendMessage(@Nullable Player player, String msg) {
        if (player == null)
            Log.info(msg);
        else
            send(player, msg);
    }

    @Override
    public void init() {
        Bundle.load(TileLoggerPlugin.class);

        VoteKick.setOnKick(player -> TileLogger.rollback(null, player.getInfo(), -1, -180, (short)0, (short)0, (short)-1, (short)-1));

        Events.on(EventType.BlockBuildBeginEvent.class, event -> event.tile.block().iterateTaken(event.tile.x, event.tile.y, (x, y) ->
                TileLogger.destroy(Vars.world.tile(x, y), TileLogger.unitToPlayerInfo(event.unit))));

        Events.on(EventType.BlockBuildEndEvent.class, event -> {
            if (event.breaking) return; // already handled by BlockBuildBeginEvent
            TileLogger.build(event.tile, TileLogger.unitToPlayerInfo(event.unit));
        });

        Events.on(EventType.ConfigEvent.class, event -> TileLogger.build(event.tile.tile, event.player == null ? null : event.player.getInfo()));

        Events.on(EventType.PickupEvent.class, event -> {
            if (event.build == null) return; // payload is unit
            TileLogger.destroy(event.build.tile, TileLogger.unitToPlayerInfo(event.carrier));
        });

        Events.on(EventType.PayloadDropEvent.class, event -> {
            if (event.build == null) return; // payload is unit
            TileLogger.build(event.build.tile, TileLogger.unitToPlayerInfo(event.carrier));
        });

        Events.on(EventType.BlockDestroyEvent.class, event -> TileLogger.destroy(event.tile, null));

        Vars.net.handleServer(RotateBlockCallPacket.class, (con, packet) -> {
            packet.handled();
            packet.handleServer(con);
            TileLogger.build(packet.build.tile, con.player.getInfo());
        });

        Events.on(EventType.WorldLoadEvent.class, event -> TileLogger.reset());
        Events.on(EventType.TapEvent.class, event -> {
            if (event.tile == null) return;

            PlayerData data = Database.getCached(event.player.uuid());

            if (data.historySize > 0) {
                TileLogger.showHistory(event.player, (short)event.tile.centerX(), (short)event.tile.centerY(), data.historySize);
            }
            else if (data.adminMod && !event.player.con.mobile) {
                TileLogger.sendTileHistory((short)event.tile.centerX(), (short)event.tile.centerY(), event.player);
            }
        });
    }

    public void registerCommands(CommandHandler handler) {
        handler.<Player>register("tilelogger", "", "Shows general info.", (args, player) ->
                TileLogger.showInfo(player));
        handler.<Player>register("history", "[size] [uuid/x] [y]", "Shows tile history.", (args, player) -> {
            try {
                var data = player == null ? null : Database.getCached(player.uuid());

                if (args.length == 2) {
                    long size = Strings.parseLong(args[0], 0);
                    if (data == null || data.historySize > 0) {
                        TileLogger.showHistory(player, Find.playerInfo(args[1]), size);
                    }
                    else if (data.adminMod && !player.con.mobile) {
                        TileLogger.sendTileHistory(Find.playerInfo(args[1]), player);
                    }
                    return;
                }
                else if (args.length == 3) {
                    long size = Strings.parseLong(args[0], 0);
                    short x = Short.parseShort(args[1]);
                    short y = Short.parseShort(args[2]);
                    TileLogger.showHistory(player, x, y, size);
                    return;
                }
                if (data == null) return;

                if (args.length > 0) {
                    data.historySize = Strings.parseLong(args[0], 0);
                }
                else if (data.historySize == 0) {
                    data.historySize = 6L;
                }
                else {
                    data.historySize = 0L;
                }
                
                send(player, "commands.history.success", data.historySize);
                Database.setCached(data);
            }
            catch (NumberFormatException e) {
                SendMessage(player, "error.wrong-number");
            }
        });

        handler.<Player>register("rollback", "<name/uuid> [time] [x1] [y1] [x2] [y2]", "Rolls back tiles.", (args, player) -> {
            if (player != null && !player.admin) {
                SendMessage(player, "error.access-denied");
                return;
            }
            try {
                short x1 = 0, y1 = 0, x2 = (short) (Vars.world.width() - 1), y2 = (short) (Vars.world.height() - 1);
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
                    if (args.length < 6) {
                        SendMessage(player, "error.not-enough-params");
                        return;
                    }
                    x1 = Short.parseShort(args[2]);
                    y1 = Short.parseShort(args[3]);
                    x2 = Short.parseShort(args[4]);
                    y2 = Short.parseShort(args[5]);
                }
                PlayerInfo target = null;
                if (uuid != null) {
                    target = uuid.equals("self") ? (player == null ? null : player.getInfo()) : Find.playerInfo(uuid);
                    if (target == null) {
                        SendMessage(player, "error.player-not-found");
                        return;
                    }
                }
                TileLogger.rollback(player, target, -1, time, x1, y1, x2, y2);
            } catch (NumberFormatException e) {
                SendMessage(player, "error.wrong-number");
            }
        });
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        registerCommands(handler);
    }
    
    @Override
    public void registerServerCommands(CommandHandler handler){
        registerCommands(handler);
    }
}
