package org.xcore.testkit.fixtures;

import mindustry.Vars;
import mindustry.content.UnitTypes;
import mindustry.game.Team;
import mindustry.gen.Unit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.xcore.testkit.fixtures.junit.WithHeadlessWorld;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code @WithHeadlessWorld} on its own registers the extension. */
@WithHeadlessWorld(width = 40, height = 24, generating = true)
class WithHeadlessWorldAnnotationTest {

    @Test
    void classAnnotationConfiguresTheWorld(HeadlessWorld world) {
        assertThat(world.width()).isEqualTo(40);
        assertThat(world.height()).isEqualTo(24);
        assertThat(Vars.world.isGenerating()).isTrue();
    }

    @Test
    @WithHeadlessWorld(width = 8, height = 8)
    void methodAnnotationWins(HeadlessWorld world) {
        assertThat(world.width()).isEqualTo(8);
        assertThat(Vars.world.isGenerating()).isFalse();
    }

    @Test
    void spawnUnitReturnsTheUnitThePlayerControls(HeadlessWorld world) {
        MockPlayer player = world.addPlayer(b -> b.name("Pilot").team(Team.sharded).position(40f, 56f));
        Unit unit = player.spawnUnit(UnitTypes.dagger);
        assertThat(unit).isSameAs(player.unit());
        assertThat(unit.x).isEqualTo(40f);
        assertThat(unit.y).isEqualTo(56f);
    }

    @Test
    void sendListenersSeeEveryRecordedPacketInOrder(HeadlessWorld world) {
        MockPlayer player = world.addPlayer("Listener", Team.sharded);
        List<Object> seen = new ArrayList<>();
        player.con().onSend(seen::add);

        player.player().sendMessage("hello");
        mindustry.gen.Call.announce("broadcast");
        player.con().close();
        player.player().sendMessage("after close");

        assertThat(seen).hasSize(2).containsExactlyElementsOf(player.sentPackets());
    }

    @Test
    void sendListenersSeeThePacketAlreadyInTheTypedTranscript(HeadlessWorld world) {
        MockPlayer player = world.addPlayer("Listener", Team.sharded);
        List<String> lastSeen = new ArrayList<>();
        player.con().onSend(packet -> lastSeen.add(player.lastReceivedMessage() + "|" + player.clientPackets("ping")));

        player.player().sendMessage("hello");
        mindustry.gen.Call.clientPacketReliable(player.con(), "ping", "1");

        assertThat(lastSeen).containsExactly("hello|[]", "hello|[1]");
    }

    @Nested
    class Inner {
        @Test
        void nestedClassUsesTheEnclosingAnnotation(HeadlessWorld world) {
            assertThat(world.width()).isEqualTo(40);
            assertThat(Vars.world.isGenerating()).isTrue();
        }
    }
}
