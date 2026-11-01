package org.xcore.testkit.fixtures;

import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import org.junit.jupiter.api.Test;
import org.xcore.testkit.fixtures.junit.WithHeadlessWorld;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WithHeadlessWorld
class EventsAndConnectionsTest {

    @Test
    void recorderSeesFiredEventsUntilClosed(HeadlessWorld world) {
        var recorder = EventRecorder.record(EventType.GameOverEvent.class);
        assertThatThrownBy(recorder::last).hasMessageContaining("GameOverEvent");

        arc.Events.fire(new EventType.GameOverEvent(Team.crux));
        arc.Events.fire(new EventType.GameOverEvent(Team.sharded));
        assertThat(recorder.count()).isEqualTo(2);
        assertThat(recorder.last().winner).isEqualTo(Team.sharded);

        recorder.close();
        arc.Events.fire(new EventType.GameOverEvent(Team.blue));
        assertThat(recorder.events()).extracting(e -> e.winner).containsExactly(Team.crux, Team.sharded);
    }

    @Test
    void joinRunsTheServersConfirmationAndLeaveItsDisconnect(HeadlessWorld world) {
        var joins = world.recordEvents(EventType.PlayerJoin.class);
        var leaves = world.recordEvents(EventType.PlayerLeave.class);

        MockPlayer alice = world.join("Alice", Team.sharded);
        assertThat(joins.last().player).isSameAs(alice.player());
        assertThat(alice.player().isAdded()).isTrue();
        assertThat(Groups.player.contains(p -> p == alice.player())).isTrue();

        world.leave(alice);
        assertThat(leaves.last().player).isSameAs(alice.player());
        assertThat(alice.player().isAdded()).isFalse();
        assertThat(world.players()).doesNotContain(alice);
        assertThat(((MockNet) Vars.net).connections()).doesNotContain(alice.con());
    }

    @Test
    void customPacketsAndRedirectsAreRecorded(HeadlessWorld world) {
        MockPlayer player = world.addPlayer("Foo", Team.sharded);

        Call.clientPacketReliable(player.con(), "fooCheck", "2.0");
        Call.clientPacketUnreliable(player.con(), "fooTransmission", "1 ping");
        Call.clientBinaryPacketReliable(player.con(), "blob", new byte[]{1, 2});
        Call.connect(player.con(), "mini.x-core.org", 6567);

        assertThat(player.clientPackets("fooCheck")).containsExactly("2.0");
        assertThat(player.con().clientPackets()).containsExactly(
                new MockNetConnection.ClientPacket("fooCheck", "2.0", true),
                new MockNetConnection.ClientPacket("fooTransmission", "1 ping", false));
        assertThat(player.con().clientBinaryPackets().get(0).contents()).containsExactly(1, 2);
        assertThat(player.lastRedirect()).isEqualTo(new MockNetConnection.Redirect("mini.x-core.org", 6567));

        player.con().clearTranscript();
        assertThat(player.con().clientPackets()).isEmpty();
        assertThat(player.lastRedirect()).isNull();
    }

    @Test
    void serverPacketsFromAPlayerReachTheRegisteredHandlers(HeadlessWorld world) {
        MockPlayer sender = world.addPlayer("Sender", Team.sharded);
        MockPlayer other = world.addPlayer("Other", Team.sharded);
        List<String> text = new ArrayList<>();
        Vars.netServer.addPacketHandler("fooTransmission", (from, contents) -> {
            text.add(from.name + ":" + contents);
            Groups.player.each(p -> p != from, p -> Call.clientPacketReliable(p.con, "fooTransmission", from.id + " " + contents));
        });
        List<byte[]> binary = new ArrayList<>();
        Vars.netServer.addBinaryPacketHandler("blob", (from, contents) -> binary.add(contents));

        sender.sendServerPacket("fooTransmission", "ping");
        sender.sendServerBinaryPacket("blob", new byte[]{7});
        sender.sendServerPacket("nobody-listens", "ignored");

        assertThat(text).containsExactly("Sender:ping");
        assertThat(binary).hasSize(1);
        assertThat(other.clientPackets("fooTransmission")).containsExactly(sender.id() + " ping");
        assertThat(sender.clientPackets("fooTransmission")).isEmpty();
    }

    @Test
    void keyedPopupsKeepTheirMargins(HeadlessWorld world) {
        MockPlayer player = world.addPlayer("Hud", Team.sharded);
        Call.infoPopup(player.con(), "scores", "leaderboard", 11f, 8, 0, 2, 50, 0);
        Call.infoPopupReliable(player.con(), null, "leaderboard", 0f, 8, 0, 2, 50, 0);

        assertThat(player.con().infoPopups()).containsExactly(
                new MockNetConnection.InfoPopup("scores", 11f, 8, "leaderboard", MockNetConnection.PopupVariant.KEYED, 0, 2, 50, 0),
                new MockNetConnection.InfoPopup(null, 0f, 8, "leaderboard", MockNetConnection.PopupVariant.KEYED_RELIABLE, 0, 2, 50, 0));
    }
}
