package org.xcore.plugin.modules.hexed;

import arc.Events;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.UnitTypes;
import mindustry.game.*;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Unitc;
import mindustry.maps.MapException;
import mindustry.net.Packets;
import mindustry.net.WorldReloader;
import mindustry.world.blocks.storage.CoreBlock;
import org.xcore.plugin.XcorePlugin;
import org.xcore.plugin.listeners.SocketEvents;
import org.xcore.plugin.modules.discord.Bot;
import org.xcore.plugin.utils.Database;
import org.xcore.plugin.utils.JavelinCommunicator;
import org.xcore.plugin.utils.Utils;
import org.xcore.plugin.utils.models.HexMember;

import static mindustry.Vars.netServer;
import static mindustry.Vars.world;
import static org.xcore.plugin.PluginVars.config;

public class MiniHexed {
    public static final ObjectMap<String, HexMember> members = new ObjectMap<>();
    public static final ObjectMap<HexedRanks.HexedRank, Seq<String>> rankings = new ObjectMap<>();
    public static Schematic startBase;
    private static int greenCores = 0;

    private static int winScore = 1800;

    private static boolean gameover = false;

    public static void init() {
        if (!config.isMiniHexed()) return;

        Timer.schedule(() -> {
            if (Groups.player.isEmpty()) return;
            Groups.player.each(player -> Call.infoPopup(player.con, Utils.getHexedLeaderboard(), 5f, 8, 0, 2, 50, 0));
        }, 0f, 5f);

        startBase = Schematics.readBase64("bXNjaAF4nDWQ3W6DMAxGv/wQUpDWV+gLcLPXmXaRQap2YhgFurYvv82ONSLlJLGPbYEWvYNf0lfGy0glny75cdr2VHb0U97Gcl33Ky0Awpw+8rzBvr336Eda11yGe5pndCvd+bzQlBFHWr7zkwqOZypjHtZCn3nc+cFNN0K/0ZzKsKYlsygdh+2SyoR4W2ZKUy7o07UM5yTOE8d72rl2fuylvsBPxDvwivpZ2QyvejZCFy387w+/NUbCXrMaRVCvVSUqDopOICfrOJcXV1TdqG5E94wWrmGwLjio1/0PZAMcC6blG2d6RhTBaqbVTCeZkctFA23rNOAlcKh9uIQXs8a9huVmPcPBWYaXORteFUEmaDQzaJfAcoVVVC+oF9QL6gX5Lx0jdppa5w1S7Q8n5z8n");
        Events.on(EventType.PlayEvent.class, event -> {
            applyRules();
            Timer.schedule(() -> {
                greenCores = Team.green.cores().size;
                XcorePlugin.info("Found @ green cores.", greenCores);
            }, 5);
        });
        Events.on(EventType.PlayerLeave.class, event -> members.get(event.player.uuid()).leave());
        Events.on(EventType.GameOverEvent.class, e -> winScore = 1800);
        Events.on(EventType.BlockDestroyEvent.class, event -> {
            var team = event.tile.team();
            var block = event.tile.block();
            if (block instanceof CoreBlock && !team.data().players.isEmpty() && team != Team.derelict && team.cores().size <= 1) {
                var player = team.data().players.first();
                Call.sendMessage(player.name + "[] [accent]eliminated!");
                player.team(Team.derelict);
            }
        });
        Events.on(EventType.UnitCreateEvent.class, event -> members.values().forEach((member) -> member.handleUnit(event.unit)));
        Events.run(EventType.Trigger.update, () -> members.each((uuid, member) -> {
            var data = Database.getCached(member.uuid);

            if (member.controlled() > 1 && data != null) {
                var ranked = rankings.get(data.hexedRank());

                if (ranked == null) {
                    rankings.put(data.hexedRank(), Seq.with(member.uuid));
                } else {
                    if (!ranked.contains(member.uuid)) ranked.add(member.uuid);
                }
            }

            if (member.controlled() >= greenCores && greenCores != 0 && !gameover && !Vars.state.gameOver) {
                endGame();
            }
        }));
        Timer.schedule(() -> {
            if (!Groups.player.isEmpty() && !Vars.state.gameOver && !gameover) {
                winScore -= 1;
            }
            int sec = winScore % 60;
            int min = (winScore / 60) % 60;

            Groups.player.each(p -> Call.infoPopup(p.con(), Strings.format("[blue]@:@[] until endgame", min, sec),
                    1, Align.bottom, 0, 0, 0, 0));

            if (winScore < 1 && !gameover && !Vars.state.gameOver) {
                endGame();
            }
        }, 0f, 1);

        netServer.assigner = (player, players) -> {
            var member = new HexMember(player.uuid());

            if (members.containsKey(player.uuid())) member = members.get(player.uuid());
            members.put(player.uuid(), member);
            return member.join();
        };

        XcorePlugin.info("MiniHexed loaded.");
    }

    private static void applyRules() {
        UnitTypes.risso.flying = true;
        UnitTypes.minke.flying = true;
        UnitTypes.bryde.flying = true;
        UnitTypes.sei.flying = true;
        UnitTypes.omura.flying = true;
        UnitTypes.retusa.flying = true;
        UnitTypes.oxynoe.flying = true;
        UnitTypes.cyerce.flying = true;
        UnitTypes.aegires.flying = true;
        UnitTypes.navanax.flying = true;

        UnitTypes.crawler.flying = true;
        UnitTypes.atrax.flying = true;
        UnitTypes.spiroct.flying = true;

        Vars.state.rules.canGameOver = false;
        Vars.state.rules.waves = false;
        Vars.state.rules.pvp = true;
        Vars.state.rules.pvpAutoPause = false;
    }

    private static void endGame() {
        winScore = 1800;
        gameover = true;
        var teams = Vars.state.teams.getActive().copy().filter(t -> !t.players.isEmpty()).sort(t -> t.cores.size).reverse();
        teams.truncate(3);

        var builder = new StringBuilder();
        if (!teams.isEmpty()) {
            builder.append("GameOver. Winners:").append("\n");
            for (int i = 0; i < teams.size; i++) {
                var team = teams.get(i);
                var player = team.players.first();

                if (i == 0) {
                    var data = Database.getCached(player.uuid());

                    var ranked = rankings.get(data.hexedRank());
                    if (ranked != null && ranked.size > 1 ||
                            rankings.keys().toSeq().contains(r -> data.hexedRank().ordinal() < r.ordinal())) {
                        data.hexedPoints++;
                        if (data.hexedRank().checkNext(data.hexedPoints)) {
                            HexedRanks.updateRank(player, data);
                            data.hexedPoints = 0;
                            data.hexedRank(data.hexedRank().next);
                        }
                    }

                    Database.setPlayerData(data);
                    Database.setCached(data);
                }

                builder.append("[orange]").append(i + 1).append(". ")
                        .append(player.coloredName()).append("[][accent]: [cyan]")
                        .append(team.cores.size).append("\n");
            }
        } else {
            builder.append("GameOver. Unfortunately, I couldn't find the winning players.");
        }

        builder.append("\nNew game in 10 seconds...");
        Call.infoMessage(builder.toString());

        JavelinCommunicator.sendEvent(
                new SocketEvents.ServerActionEvent(Strings.stripColors(builder.toString()), config.server),
                e -> Bot.sendServerAction(e.message));

        Timer.schedule(MiniHexed::reloadMap, 10);
    }

    private static void reloadMap() {
        try {
            var map = Vars.maps.getNextMap(Gamemode.pvp, Vars.state.map);
            var reloader = new WorldReloader();
            netServer.kickAll(Packets.KickReason.serverRestarting);
            reloader.begin();
            world.loadMap(map, map.applyRules(Vars.state.rules.mode()));
            Vars.state.rules = Vars.state.map.applyRules(Vars.state.rules.mode());
            applyRules();
            Vars.logic.play();
            members.each((uuid, member) -> member.cancelTasks());
            members.clear();
            rankings.clear();
            reloader.end();
            gameover = false;
        } catch (MapException e) {
            Log.err("@: @", e.map.name(), e.getMessage());
        }
    }

    public static void killTeam(Team team) {
        if (team == Team.derelict || team == Team.green || !team.data().active()) return;

        if (!team.data().players.isEmpty()) {
            var player = team.data().players.first();
            Call.sendMessage(player.coloredName() + " [accent]eliminated!");
            player.team(Team.derelict);
        }

        team.data().cores.each(core -> core.tile.setNet(Blocks.coreShard, Team.green, 0));

        team.data().destroyToDerelict();

        team.data().units.each(Unitc::kill);
        team.data().plans.clear();
    }
}
