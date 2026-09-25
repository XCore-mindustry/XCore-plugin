package org.xcore.plugin.command.controller.client;

import arc.struct.Seq;
import mindustry.Vars;
import mindustry.gen.Player;
import mindustry.net.Net;
import mindustry.net.NetConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.cloud.mindustry.selector.TargetSelector.MultiplePlayerSelector;
import org.xcore.plugin.cloud.XCoreSender;

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

        verify(sender).sendMessage(argThat(s -> s.contains("Alert sent to") && s.contains("1")));
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

        verify(sender).sendMessage(argThat(s -> s.contains("Toast sent to") && s.contains("1")));
    }
}
