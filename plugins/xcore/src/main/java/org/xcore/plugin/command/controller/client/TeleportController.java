package org.xcore.plugin.command.controller.client;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.gen.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.xcore.plugin.permission.TargetHierarchy;
import org.xcore.cloud.mindustry.selector.TargetSelector.MultiplePlayerSelector;
import org.xcore.cloud.mindustry.selector.TargetSelector.SinglePlayerSelector;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.permission.PermissionNodes;
import org.xcore.plugin.command.controller.CloudClientController;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class TeleportController implements CloudClientController {

    private final TargetHierarchy hierarchy;

    @Inject
    public TeleportController(TargetHierarchy hierarchy) {
        this.hierarchy = hierarchy;
    }

    public TeleportController() {
        this(TargetHierarchy.none());
    }

    /** The console moves anybody; a player only those they outrank. */
    private boolean mayMove(XCoreSender sender, Player target) {
        return !sender.isPlayer() || hierarchy.outranks(sender.player(), target);
    }

    @Command("tp|teleport|goto <destination>")
    @CommandDescription("Teleports yourself to a destination player.")
    @Permission(PermissionNodes.ADMIN_TP)
    public void teleportSelf(
            XCoreSender sender,
            @Argument("destination") SinglePlayerSelector destination
    ) {
        if (!sender.isPlayer() || sender.player().unit() == null) {
            sender.send("admin-cmd-tp-not-in-game");
            return;
        }

        Player dest = destination.resolve(sender.getHandle());
        if (dest.unit() == null) {
            sender.send("admin-cmd-tp-target-no-unit");
            return;
        }

        if (!mayMove(sender, dest)) {
            sender.send("admin-cmd-tp-not-below");
            return;
        }

        Player self = sender.player();
        self.unit().set(dest.unit().x, dest.unit().y);
        self.snapInterpolation();
        sender.send("admin-cmd-tp-done", args("player", dest.plainName()));
    }

    @Command("bring <targets>")
    @CommandDescription("Teleports target players to your position.")
    @Permission(PermissionNodes.ADMIN_TP)
    public void bring(
            XCoreSender sender,
            @Argument("targets") MultiplePlayerSelector targets
    ) {
        if (!sender.isPlayer() || sender.player().unit() == null) {
            sender.send("admin-cmd-bring-not-in-game");
            return;
        }

        Player self = sender.player();
        float sx = self.unit().x;
        float sy = self.unit().y;

        var resolved = targets.resolve(sender.getHandle());
        int count = 0;
        for (Player p : resolved) {
            if (p != self && p.unit() != null && mayMove(sender, p)) {
                p.unit().set(sx, sy);
                p.snapInterpolation();
                count++;
            }
        }

        sender.send("admin-cmd-bring-done", args("count", count));
    }

    @Command("tpto <targets> <destination>")
    @CommandDescription("Teleports target players to a destination player.")
    @Permission(PermissionNodes.ADMIN_TP)
    public void teleportTargetsToDestination(
            XCoreSender sender,
            @Argument("targets") MultiplePlayerSelector targets,
            @Argument("destination") SinglePlayerSelector destination
    ) {
        Player dest = destination.resolve(sender.getHandle());
        if (dest.unit() == null) {
            sender.send("admin-cmd-tp-destination-no-unit");
            return;
        }

        float dx = dest.unit().x;
        float dy = dest.unit().y;

        var resolved = targets.resolve(sender.getHandle());
        int count = 0;
        for (Player p : resolved) {
            if (p.unit() != null && mayMove(sender, p)) {
                p.unit().set(dx, dy);
                p.snapInterpolation();
                count++;
            }
        }

        sender.send("admin-cmd-tpto-done", args("count", count, "player", dest.plainName()));
    }

    @Command("tppos <targets> <x> <y>")
    @CommandDescription("Teleports target players to tile coordinates.")
    @Permission(PermissionNodes.ADMIN_TP)
    public void teleportTargetsToCoords(
            XCoreSender sender,
            @Argument("targets") MultiplePlayerSelector targets,
            @Argument("x") String xCoord,
            @Argument("y") String yCoord
    ) {
        float ox = sender.isPlayer() && sender.player().unit() != null ? sender.player().unit().x : 0f;
        float oy = sender.isPlayer() && sender.player().unit() != null ? sender.player().unit().y : 0f;

        float targetX;
        float targetY;
        try {
            targetX = parseCoord(xCoord, ox);
            targetY = parseCoord(yCoord, oy);
        } catch (NumberFormatException e) {
            sender.send("admin-cmd-tp-invalid-coordinates", args("x", xCoord, "y", yCoord));
            return;
        }

        var resolved = targets.resolve(sender.getHandle());
        int count = 0;
        for (Player p : resolved) {
            if (p.unit() != null && mayMove(sender, p)) {
                p.unit().set(targetX, targetY);
                p.snapInterpolation();
                count++;
            }
        }

        // Tile coordinates as text, so they are not grouped like amounts ("1,024").
        sender.send("admin-cmd-tppos-done", args("count", count,
                "x", String.valueOf((int) (targetX / Vars.tilesize)), "y", String.valueOf((int) (targetY / Vars.tilesize))));
    }

    private float parseCoord(String val, float origin) {
        val = val.trim();
        if (val.startsWith("~")) {
            float offset = val.length() > 1 ? Float.parseFloat(val.substring(1)) * Vars.tilesize : 0f;
            return origin + offset;
        }
        return Float.parseFloat(val) * Vars.tilesize;
    }
}
