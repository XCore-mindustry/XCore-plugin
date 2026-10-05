package org.xcore.example;

import mindustry.Vars;
import mindustry.mod.Plugin;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.xcore.cloud.mindustry.parser.MindustryParsers;
import org.xcore.cloud.mindustry.ConflictStrategy;
import org.xcore.cloud.mindustry.MindustryCommandManager;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.cloud.mindustry.selector.TargetSelector.SinglePlayerSelector;
import org.xcore.cloud.mindustry.selector.parser.TargetSelectorParsers;

public class ExamplePlugin extends Plugin {

    @Override
    public void init() {
        var mgr = MindustryCommandManager.create(Vars.netServer.clientCommands);

        mgr.setConflictStrategy(ConflictStrategy.OVERRIDE);
        // console and Mindustry admins get every permission
        mgr.setPermissionChecker((sender, perm) -> sender.isAdmin());

        // == common integer components ==
        var a = CommandComponent.<MindustrySender, Integer>builder("a", IntegerParser.integerParser()).build();
        var b = CommandComponent.<MindustrySender, Integer>builder("b", IntegerParser.integerParser()).build();

        // /sum <a> <b>
        mgr.command(mgr.commandBuilder("sum")
                .permission("example.math")
                .argument(a)
                .argument(b)
                .handler(ctx -> {
                    int x = ctx.get("a");
                    int y = ctx.get("b");

                    ctx.sender().sendMessage("Result: " + (x + y));
                })
        );

        // /div <a> <b>
        mgr.command(mgr.commandBuilder("div")
                .permission("example.math")
                .argument(a)
                .argument(b)
                .handler(ctx -> {
                    int x = ctx.get("a");
                    int y = ctx.get("b");

                    if (y == 0) throw new IllegalArgumentException("Cannot divide by zero");
                    ctx.sender().sendMessage("Result: " + (x / y));
                })
        );

        // /adminonly <a>
        mgr.command(mgr.commandBuilder("adminonly")
                .permission("example.admin")
                .argument(a)
                .handler(ctx -> {
                    ctx.sender().sendMessage("Admin passed! a=" + ctx.get("a"));
                })
        );

        // /heal <target> - Demonstrates target selector (@p, @s, #id, or PlayerName)
        mgr.command(mgr.commandBuilder("heal")
                .permission("example.heal")
                .argument(CommandComponent.<MindustrySender, SinglePlayerSelector>builder("target", TargetSelectorParsers.singlePlayerSelector(mgr.selectorEngine())).build())
                .handler(ctx -> {
                    SinglePlayerSelector selector = ctx.get("target");
                    var targetPlayer = selector.resolve(ctx.sender());
                    if (targetPlayer.unit() != null) {
                        targetPlayer.unit().heal();
                    }
                    ctx.sender().sendMessage("Healed " + targetPlayer.plainName());
                })
        );

        // /spawn <type> <team> - Team and content types parse out of the box
        mgr.command(mgr.commandBuilder("spawn")
                .permission("example.spawn")
                .required("type", MindustryParsers.unitType())
                .required("team", MindustryParsers.team())
                .handler(ctx -> {
                    var player = ctx.sender().player();
                    if (player == null) {
                        ctx.sender().sendMessage("Only players can spawn units.");
                        return;
                    }
                    mindustry.type.UnitType type = ctx.get("type");
                    mindustry.game.Team team = ctx.get("team");
                    type.spawn(team, player.x, player.y);
                })
        );
    }
}
