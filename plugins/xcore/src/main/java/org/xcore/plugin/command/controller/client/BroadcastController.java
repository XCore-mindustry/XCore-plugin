package org.xcore.plugin.command.controller.client;

import arc.struct.Seq;
import arc.util.Nullable;
import com.ospx.flubundle.Bundle;
import com.ospx.flubundle.mindustry.Messenger;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import mindustry.gen.Call;
import mindustry.gen.Player;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.xcore.cloud.mindustry.selector.TargetSelector.MultiplePlayerSelector;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.permission.PermissionNodes;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.service.AnnouncementService;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class BroadcastController implements CloudClientController {

    private final AnnouncementService announcementService;
    private final Messenger messenger;

    @Inject
    public BroadcastController(AnnouncementService announcementService, Messenger messenger) {
        this.announcementService = announcementService;
        this.messenger = messenger;
    }

    public BroadcastController() {
        this(null, Messenger.of(Bundle.INSTANCE));
    }

    @Command("alert <targets> <message>")
    @CommandDescription("Displays a prominent announcement banner to target players.")
    @Permission(PermissionNodes.ADMIN_BROADCAST)
    public void alert(
            XCoreSender sender,
            @Argument("targets") MultiplePlayerSelector targets,
            @Argument("message") @Greedy String message
    ) {
        Seq<Player> list = targets.resolve(sender.getHandle()).select(p -> p.con != null);
        // The banner prefix is in each target's language; the message is the admin's own text.
        messenger.to(list).announce("admin-cmd-alert-message", args("message", message));
        sender.send("admin-cmd-alert-done", args("count", list.size));
    }

    @Command("toast <targets> <message>")
    @CommandDescription("Displays a warning toast notification to target players.")
    @Permission(PermissionNodes.ADMIN_BROADCAST)
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
        sender.send("admin-cmd-toast-done", args("count", count));
    }

    @Command("announcement [key]")
    @CommandDescription("Broadcasts a periodic announcement by key, or the next one in rotation.")
    @Permission(PermissionNodes.ADMIN_BROADCAST)
    public void announcement(
            XCoreSender sender,
            @Argument("key") @Nullable String key
    ) {
        if (announcementService == null) {
            sender.send("admin-cmd-announcement-unavailable");
            return;
        }

        if (key != null && !key.isBlank()) {
            if ("list".equalsIgnoreCase(key.trim())) {
                List<String> active = announcementService.getActiveMessages();
                sender.send("admin-cmd-announcement-list", args("count", active.size(),
                        "keys", String.join(", ", active),
                        "custom", String.valueOf(announcementService.isCustomListEnabled())));
                return;
            }

            String resolvedKey = key.startsWith("announcement-") ? key : "announcement-" + key;
            int sent = announcementService.broadcast(resolvedKey);
            sender.send("admin-cmd-announcement-sent", args("key", resolvedKey, "count", sent));
        } else {
            String sentKey = announcementService.broadcastNext();
            if (sentKey != null) {
                sender.send("admin-cmd-announcement-rotated", args("key", sentKey));
            } else {
                sender.send("admin-cmd-announcement-skipped");
            }
        }
    }
}
