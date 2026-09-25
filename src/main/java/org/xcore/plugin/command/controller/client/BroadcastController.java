package org.xcore.plugin.command.controller.client;

import arc.struct.Seq;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Call;
import mindustry.gen.Player;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.xcore.cloud.mindustry.selector.TargetSelector.MultiplePlayerSelector;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudClientController;

@Singleton
public class BroadcastController implements CloudClientController {

    @Inject
    public BroadcastController() {}

    @Command("alert <targets> <message>")
    @CommandDescription("Displays a prominent announcement banner to target players.")
    @Permission("xcore.admin.broadcast")
    public void alert(
            XCoreSender sender,
            @Argument("targets") MultiplePlayerSelector targets,
            @Argument("message") @Greedy String message
    ) {
        Seq<Player> list = targets.resolve(sender.getHandle());
        int count = 0;
        for (Player p : list) {
            if (p.con != null) {
                Call.announce(p.con, "[gold][Alert][white] " + message);
                count++;
            }
        }
        sender.sendMessage("[accent]Alert sent to [green]" + count + " [accent]player(s).");
    }

    @Command("toast <targets> <message>")
    @CommandDescription("Displays a warning toast notification to target players.")
    @Permission("xcore.admin.broadcast")
    public void toast(
            XCoreSender sender,
            @Argument("targets") MultiplePlayerSelector targets,
            @Argument("message") @Greedy String message
    ) {
        Seq<Player> list = targets.resolve(sender.getHandle());
        int count = 0;
        for (Player p : list) {
            if (p.con != null) {
                Call.warningToast(p.con, 1, message);
                count++;
            }
        }
        sender.sendMessage("[accent]Toast sent to [green]" + count + " [accent]player(s).");
    }
}
