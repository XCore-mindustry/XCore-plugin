package org.xcore.plugin.command.controller.client;

import arc.struct.Seq;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.xcore.cloud.mindustry.selector.TargetSelector.MultiplePlayerSelector;
import org.xcore.cloud.mindustry.selector.TargetSelector.MultipleUnitSelector;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudClientController;

@Singleton
public class EntityAdminController implements CloudClientController {

    @Inject
    public EntityAdminController() {}

    @Command("heal [targets]")
    @CommandDescription("Heals target units (or self if unspecified).")
    @Permission("xcore.admin.heal")
    public void heal(
            XCoreSender sender,
            @Argument("targets") MultipleUnitSelector targets
    ) {
        if (targets == null) {
            if (!sender.isPlayer() || sender.player().unit() == null) {
                sender.sendMessage("[scarlet]You must have an active unit to heal.");
                return;
            }
            Unit u = sender.player().unit();
            u.health(u.maxHealth);
            u.clearStatuses();
            sender.sendMessage("[accent]Your unit was fully healed.");
            return;
        }

        Seq<Unit> list = targets.resolve(sender.getHandle());
        int count = 0;
        for (Unit u : list) {
            if (u.isAdded() && !u.dead) {
                u.health(u.maxHealth);
                u.clearStatuses();
                count++;
            }
        }
        sender.sendMessage("[accent]Healed [green]" + count + " [accent]unit(s).");
    }

    @Command("killunits <targets>")
    @CommandDescription("Kills target units.")
    @Permission("xcore.admin.kill")
    public void killUnits(
            XCoreSender sender,
            @Argument("targets") MultipleUnitSelector targets
    ) {
        Seq<Unit> list = targets.resolve(sender.getHandle());
        int count = 0;
        for (Unit u : list) {
            if (u.isAdded() && !u.dead) {
                u.kill();
                count++;
            }
        }
        sender.sendMessage("[accent]Killed [green]" + count + " [accent]unit(s).");
    }

    @Command("kill <targets>")
    @CommandDescription("Kills target players.")
    @Permission("xcore.admin.kill")
    public void killPlayers(
            XCoreSender sender,
            @Argument("targets") MultiplePlayerSelector targets
    ) {
        Seq<Player> list = targets.resolve(sender.getHandle());
        int count = 0;
        for (Player p : list) {
            if (p.unit() != null && !p.unit().dead) {
                p.unit().kill();
                count++;
            }
        }
        sender.sendMessage("[accent]Killed [green]" + count + " [accent]player(s).");
    }

    @Command("suicide")
    @CommandDescription("Destroys your current unit if you are stuck.")
    public void suicide(XCoreSender sender) {
        if (!sender.isPlayer() || sender.player().unit() == null) {
            sender.sendMessage("[scarlet]You must have an active unit to suicide.");
            return;
        }

        sender.player().unit().kill();
        sender.sendMessage("[accent]You killed your unit.");
    }
}
