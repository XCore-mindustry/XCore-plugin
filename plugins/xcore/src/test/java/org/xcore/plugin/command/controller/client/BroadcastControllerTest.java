package org.xcore.plugin.command.controller.client;

import arc.files.Fi;
import arc.struct.Seq;
import com.ospx.flubundle.Bundle;
import com.ospx.flubundle.mindustry.Messenger;
import mindustry.Vars;
import mindustry.gen.AnnounceCallPacket;
import mindustry.gen.Player;
import mindustry.net.Net;
import mindustry.net.NetConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.cloud.mindustry.selector.TargetSelector.MultiplePlayerSelector;
import org.xcore.plugin.cloud.XCoreSender;

import java.util.Locale;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BroadcastControllerTest {

    @BeforeEach
    void setUp() {
        Vars.net = mock(Net.class);
        when(Vars.net.server()).thenReturn(true);
    }

    @Test
    @DisplayName("alert sends announcement banner to targets")
    void alert_sendsAnnouncement() {
        BroadcastController controller = new BroadcastController();

        XCoreSender sender = mock(XCoreSender.class);
        MindustrySender handle = mock(MindustrySender.class);
        when(sender.getHandle()).thenReturn(handle);

        Player p1 = mock(Player.class);
        p1.con = mock(NetConnection.class);

        MultiplePlayerSelector selector = mock(MultiplePlayerSelector.class);
        when(selector.resolve(handle)).thenReturn(Seq.with(p1));

        controller.alert(sender, selector, "Defend the core!");

        verify(sender).send(eq("admin-cmd-alert-done"), argThat(args -> Integer.valueOf(1).equals(args.get("count"))));
    }

    @Test
    @DisplayName("toast sends warning toast to targets")
    void toast_sendsWarningToast() {
        BroadcastController controller = new BroadcastController();

        XCoreSender sender = mock(XCoreSender.class);
        MindustrySender handle = mock(MindustrySender.class);
        when(sender.getHandle()).thenReturn(handle);

        Player p1 = mock(Player.class);
        p1.con = mock(NetConnection.class);

        MultiplePlayerSelector selector = mock(MultiplePlayerSelector.class);
        when(selector.resolve(handle)).thenReturn(Seq.with(p1));

        controller.toast(sender, selector, "Low power!");

        verify(sender).send(eq("admin-cmd-toast-done"), argThat(args -> Integer.valueOf(1).equals(args.get("count"))));
    }

    @Test
    @DisplayName("alert banner is rendered in the target's language around the admin's text")
    void alert_bannerIsLocalized() {
        Bundle bundle = new Bundle(Locale.ENGLISH);
        bundle.addSource(new Fi("src/main/resources/bundles"));
        BroadcastController controller = new BroadcastController(null, Messenger.of(bundle));

        XCoreSender sender = mock(XCoreSender.class);
        MindustrySender handle = mock(MindustrySender.class);
        when(sender.getHandle()).thenReturn(handle);

        Player russian = mock(Player.class);
        russian.con = mock(NetConnection.class);
        russian.locale = "ru";
        Player offline = mock(Player.class);

        MultiplePlayerSelector selector = mock(MultiplePlayerSelector.class);
        when(selector.resolve(handle)).thenReturn(Seq.with(russian, offline));

        controller.alert(sender, selector, "Defend the core!");

        verify(russian.con).send(argThat(packet -> packet instanceof AnnounceCallPacket announce
                && announce.message.equals("[gold][Оповещение][white] Defend the core!")), eq(true));
        verify(sender).send(eq("admin-cmd-alert-done"), argThat(args -> Integer.valueOf(1).equals(args.get("count"))));
    }
}
