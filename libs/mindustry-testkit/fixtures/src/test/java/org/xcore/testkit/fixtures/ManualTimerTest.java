package org.xcore.testkit.fixtures;

import arc.util.Timer;
import org.junit.jupiter.api.Test;
import org.xcore.testkit.fixtures.junit.WithHeadlessWorld;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WithHeadlessWorld
class ManualTimerTest {

    @Test
    void scheduledTasksRunOnlyWhenTimeIsAdvancedPastThem(HeadlessWorld world) {
        ManualTimer timer = world.timer();
        List<String> ran = new ArrayList<>();
        Timer.schedule(() -> ran.add("ten"), 10f);
        Timer.schedule(() -> ran.add("two"), 2f);

        assertThat(timer.pending()).isEqualTo(2);
        assertThat(timer.nextIn()).isEqualTo(2f);
        timer.advance(1.999f);
        assertThat(ran).isEmpty();
        timer.advance(0.001f);
        assertThat(ran).containsExactly("two");
        timer.advance(60f);
        assertThat(ran).containsExactly("two", "ten");
        assertThat(timer.pending()).isZero();
        assertThat(timer.nextIn()).isEqualTo(-1f);
        assertThat(timer.now()).isEqualTo(62f);
    }

    @Test
    void repeatingTasksRunOncePerIntervalCrossedAndStopWhenCancelled(HeadlessWorld world) {
        ManualTimer timer = world.timer();
        List<Float> at = new ArrayList<>();
        Timer.Task task = Timer.schedule(() -> at.add(timer.now()), 5f, 5f);

        timer.advance(17f);
        assertThat(at).containsExactly(5f, 10f, 15f);
        assertThat(task.isScheduled()).isTrue();

        task.cancel();
        timer.advance(60f);
        assertThat(at).hasSize(3);
        assertThat(task.isScheduled()).isFalse();
    }

    @Test
    void aTaskCancellingAnotherAtTheSameInstantDoesNotBreakTheWalk(HeadlessWorld world) {
        ManualTimer timer = world.timer();
        List<String> ran = new ArrayList<>();
        Timer.Task[] victim = new Timer.Task[1];
        Timer.schedule(() -> { ran.add("first"); victim[0].cancel(); }, 1f);
        victim[0] = Timer.schedule(() -> ran.add("victim"), 1f, 1f);
        Timer.schedule(() -> ran.add("third"), 1f);

        timer.advance(1f);
        // Arc decides what is due before any of it runs, so the victim still runs once. Its task
        // list is unordered, so tasks due at one instant run in no promised order.
        assertThat(ran).containsExactlyInAnyOrder("first", "victim", "third");
        timer.advance(10f);
        assertThat(ran).hasSize(3);
    }

    @Test
    void tasksScheduledByATaskStartFromItsInstant(HeadlessWorld world) {
        ManualTimer timer = world.timer();
        List<Float> at = new ArrayList<>();
        Timer.schedule(() -> {
            at.add(timer.now());
            Timer.schedule(() -> at.add(timer.now()), 3f);
        }, 2f);

        timer.advance(10f);
        assertThat(at).containsExactly(2f, 5f);
    }

    @Test
    void zeroDelayTasksWaitForRunDue(HeadlessWorld world) {
        ManualTimer timer = world.timer();
        List<String> ran = new ArrayList<>();
        Timer.schedule(() -> ran.add("now"), 0f);
        assertThat(ran).isEmpty();
        timer.runDue();
        assertThat(ran).containsExactly("now");
    }

    @Test
    void aZeroIntervalRepeatFailsInsteadOfHanging(HeadlessWorld world) {
        ManualTimer timer = world.timer();
        Timer.schedule(() -> {}, 0f, 0f);
        assertThatThrownBy(() -> timer.advance(1f)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("zero interval");
    }

    @Test
    void closingGivesTimerInstanceBackAndDropsPendingTasks() {
        Timer before = Timer.instance();
        Timer.Task task;
        try (var world = HeadlessWorld.create(8, 8)) {
            ManualTimer timer = world.timer();
            assertThat(Timer.instance()).isSameAs(timer);
            task = Timer.schedule(() -> {}, 5f);
        }
        assertThat(Timer.instance()).isSameAs(before);
        assertThat(task.isScheduled()).isFalse();
    }
}
