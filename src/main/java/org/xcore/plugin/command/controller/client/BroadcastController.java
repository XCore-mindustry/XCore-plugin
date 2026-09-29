package org.xcore.plugin.command.controller.client;

import arc.struct.Seq;
import arc.util.Nullable;
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
import org.xcore.plugin.service.AnnouncementService;

@Singleton
public class BroadcastController implements CloudClientController {

    private final AnnouncementService announcementService;

    @Inject
    public BroadcastController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    public BroadcastController() {
        this(null);
    }

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

    @Command("announcement [key]")
    @CommandDescription("Broadcasts a periodic announcement by key, or the next one in rotation.")
    @Permission("xcore.admin.broadcast")
    public void announcement(
            XCoreSender sender,
            @Argument("key") @Nullable String key
    ) {
        if (announcementService == null) {
            sender.sendMessage("[scarlet]Announcement service is not available.");
            return;
        }

        if (key != null && !key.isBlank()) {
            int sent = announcementService.broadcast(key);
            sender.sendMessage("[accent]Announcement [white]'" + key + "'[accent] sent to [green]" + sent + " [accent]player(s).");
        } else {
            String sentKey = announcementService.broadcastNext();
            if (sentKey != null) {
                sender.sendMessage("[accent]Rotated announcement [white]'" + sentKey + "'[accent] sent.");
            } else {
                sender.sendMessage("[scarlet]Announcement skipped (disabled, no players, or not in game).");
            }
        }
    }
}
