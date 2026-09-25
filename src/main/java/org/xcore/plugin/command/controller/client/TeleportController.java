package org.xcore.plugin.command.controller.client;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.gen.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.xcore.cloud.mindustry.selector.TargetSelector.MultiplePlayerSelector;
import org.xcore.cloud.mindustry.selector.TargetSelector.SinglePlayerSelector;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudClientController;

@Singleton
public class TeleportController implements CloudClientController {

    @Inject
    public TeleportController() {}

    @Command("tp|teleport <destination>")
    @CommandDescription("Teleports yourself to a destination player.")
    @Permission("xcore.admin.tp")
    public void teleportSelf(
            XCoreSender sender,
            @Argument("destination") SinglePlayerSelector destination
    ) {
        if (!sender.isPlayer() || sender.player().unit() == null) {
            sender.sendMessage("[scarlet]You must be an active in-game player to teleport.");
            return;
        }

        Player dest = destination.resolve(sender.getHandle());
        if (dest.unit() == null) {
            sender.sendMessage("[scarlet]Target player has no active unit.");
            return;
        }

        Player self = sender.player();
        self.unit().set(dest.unit().x, dest.unit().y);
        self.snapInterpolation();
        sender.sendMessage("[accent]Teleported to [white]" + dest.plainName());
    }

    @Command("tp|teleport <targets> <destination>")
    @CommandDescription("Teleports target players to a destination player.")
    @Permission("xcore.admin.tp")
    public void teleportTargetsToDestination(
            XCoreSender sender,
            @Argument("targets") MultiplePlayerSelector targets,
            @Argument("destination") SinglePlayerSelector destination
    ) {
        Player dest = destination.resolve(sender.getHandle());
        if (dest.unit() == null) {
            sender.sendMessage("[scarlet]Destination player has no active unit.");
            return;
        }

        float dx = dest.unit().x;
        float dy = dest.unit().y;

        var resolved = targets.resolve(sender.getHandle());
        int count = 0;
        for (Player p : resolved) {
            if (p.unit() != null) {
                p.unit().set(dx, dy);
                p.snapInterpolation();
                count++;
            }
        }

        sender.sendMessage("[accent]Teleported [green]" + count + " [accent]player(s) to [white]" + dest.plainName());
    }

    @Command("tp|teleport <targets> <x> <y>")
    @CommandDescription("Teleports target players to tile coordinates.")
    @Permission("xcore.admin.tp")
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
            sender.sendMessage("[scarlet]Invalid coordinates: " + xCoord + ", " + yCoord);
            return;
        }

        var resolved = targets.resolve(sender.getHandle());
        int count = 0;
        for (Player p : resolved) {
            if (p.unit() != null) {
                p.unit().set(targetX, targetY);
                p.snapInterpolation();
                count++;
            }
        }

        sender.sendMessage("[accent]Teleported [green]" + count + " [accent]player(s) to ([white]"
                + (int) (targetX / Vars.tilesize) + ", " + (int) (targetY / Vars.tilesize) + "[accent]).");
    }

    @Command("bring <targets>")
    @CommandDescription("Teleports target players to your position.")
    @Permission("xcore.admin.tp")
    public void bring(
            XCoreSender sender,
            @Argument("targets") MultiplePlayerSelector targets
    ) {
        if (!sender.isPlayer() || sender.player().unit() == null) {
            sender.sendMessage("[scarlet]You must be an active in-game player to bring targets.");
            return;
        }

        Player self = sender.player();
        float sx = self.unit().x;
        float sy = self.unit().y;

        var resolved = targets.resolve(sender.getHandle());
        int count = 0;
        for (Player p : resolved) {
            if (p != self && p.unit() != null) {
                p.unit().set(sx, sy);
                p.snapInterpolation();
                count++;
            }
        }

        sender.sendMessage("[accent]Brought [green]" + count + " [accent]player(s) to your location.");
    }

    @Command("goto <destination>")
    @CommandDescription("Teleports yourself to a destination player.")
    @Permission("xcore.admin.tp")
    public void gotoPlayer(
            XCoreSender sender,
            @Argument("destination") SinglePlayerSelector destination
    ) {
        teleportSelf(sender, destination);
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
