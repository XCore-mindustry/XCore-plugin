package org.xcore.plugin.service.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedisTransportHealthTest {

    @Test
    @DisplayName("snapshot starts unavailable and disconnected")
    void snapshotStartsUnavailableAndDisconnected() {
        // Arrange
        RedisTransportHealth health = new RedisTransportHealth();

        // Act
        RedisTransportHealth.Snapshot snapshot = health.snapshot();

        // Assert
        assertThat(snapshot.available()).isFalse();
        assertThat(snapshot.lifecycleState()).isEqualTo(RedisTransportHealth.LifecycleState.DISCONNECTED);
        assertThat(snapshot.activeSubscriberThreads()).isZero();
        assertThat(snapshot.lastConnectAttemptAt()).isZero();
        assertThat(snapshot.lastConnectedAt()).isZero();
        assertThat(snapshot.lastDisconnectedAt()).isZero();
    }

    @Test
    @DisplayName("mark connecting records attempt without reporting availability")
    void markConnectingRecordsAttemptWithoutReportingAvailability() {
        // Arrange
        RedisTransportHealth health = new RedisTransportHealth();
        long before = System.currentTimeMillis();

        // Act
        health.markConnecting();
        long after = System.currentTimeMillis();
        RedisTransportHealth.Snapshot snapshot = health.snapshot();

        // Assert
        assertThat(snapshot.available()).isFalse();
        assertThat(snapshot.lifecycleState()).isEqualTo(RedisTransportHealth.LifecycleState.CONNECTING);
        assertThat(snapshot.lastConnectAttemptAt()).isBetween(before, after);
        assertThat(snapshot.lastConnectedAt()).isZero();
        assertThat(snapshot.lastDisconnectedAt()).isZero();
    }

    @Test
    @DisplayName("mark connected reports availability and preserves connect attempt metadata")
    void markConnectedReportsAvailabilityAndPreservesConnectAttemptMetadata() {
        // Arrange
        RedisTransportHealth health = new RedisTransportHealth();
        health.markConnecting();
        long connectAttemptAt = health.snapshot().lastConnectAttemptAt();
        long before = System.currentTimeMillis();

        // Act
        health.markConnected();
        long after = System.currentTimeMillis();
        RedisTransportHealth.Snapshot snapshot = health.snapshot();

        // Assert
        assertThat(snapshot.available()).isTrue();
        assertThat(snapshot.lifecycleState()).isEqualTo(RedisTransportHealth.LifecycleState.CONNECTED);
        assertThat(snapshot.lastConnectAttemptAt()).isEqualTo(connectAttemptAt);
        assertThat(snapshot.lastConnectedAt()).isBetween(before, after);
        assertThat(snapshot.lastConnectedAt()).isGreaterThanOrEqualTo(snapshot.lastConnectAttemptAt());
        assertThat(snapshot.lastDisconnectedAt()).isZero();
    }

    @Test
    @DisplayName("mark unavailable resets lifecycle without recording a disconnect timestamp")
    void markUnavailableResetsLifecycleWithoutRecordingDisconnectTimestamp() {
        // Arrange
        RedisTransportHealth health = new RedisTransportHealth();
        health.markConnecting();
        health.markConnected();

        // Act
        health.markUnavailable();
        RedisTransportHealth.Snapshot snapshot = health.snapshot();

        // Assert
        assertThat(snapshot.available()).isFalse();
        assertThat(snapshot.lifecycleState()).isEqualTo(RedisTransportHealth.LifecycleState.DISCONNECTED);
        assertThat(snapshot.lastConnectAttemptAt()).isPositive();
        assertThat(snapshot.lastConnectedAt()).isPositive();
        assertThat(snapshot.lastDisconnectedAt()).isZero();
    }

    @Test
    @DisplayName("mark disconnected records disconnect timestamp and becomes unavailable")
    void markDisconnectedRecordsDisconnectTimestampAndBecomesUnavailable() {
        // Arrange
        RedisTransportHealth health = new RedisTransportHealth();
        health.markConnecting();
        health.markConnected();
        long before = System.currentTimeMillis();

        // Act
        health.markDisconnected();
        long after = System.currentTimeMillis();
        RedisTransportHealth.Snapshot snapshot = health.snapshot();

        // Assert
        assertThat(snapshot.available()).isFalse();
        assertThat(snapshot.lifecycleState()).isEqualTo(RedisTransportHealth.LifecycleState.DISCONNECTED);
        assertThat(snapshot.lastConnectedAt()).isPositive();
        assertThat(snapshot.lastDisconnectedAt()).isBetween(before, after);
    }

    @Test
    @DisplayName("active subscriber threads snapshot clamps negative values to zero")
    void activeSubscriberThreadsSnapshotClampsNegativeValuesToZero() {
        // Arrange
        RedisTransportHealth health = new RedisTransportHealth();
        health.setActiveSubscriberThreads(4);

        // Act
        RedisTransportHealth.Snapshot positiveSnapshot = health.snapshot();
        health.setActiveSubscriberThreads(-3);
        RedisTransportHealth.Snapshot clampedSnapshot = health.snapshot();

        // Assert
        assertThat(positiveSnapshot.activeSubscriberThreads()).isEqualTo(4);
        assertThat(clampedSnapshot.activeSubscriberThreads()).isZero();
    }

    @Test
    @DisplayName("snapshot captures a point in time instead of a live view")
    void snapshotCapturesPointInTimeInsteadOfLiveView() {
        // Arrange
        RedisTransportHealth health = new RedisTransportHealth();
        RedisTransportHealth.Snapshot initialSnapshot = health.snapshot();

        // Act
        health.markConnecting();
        health.markConnected();
        health.setActiveSubscriberThreads(2);
        RedisTransportHealth.Snapshot updatedSnapshot = health.snapshot();

        // Assert
        assertThat(initialSnapshot.available()).isFalse();
        assertThat(initialSnapshot.lifecycleState()).isEqualTo(RedisTransportHealth.LifecycleState.DISCONNECTED);
        assertThat(initialSnapshot.activeSubscriberThreads()).isZero();
        assertThat(updatedSnapshot.available()).isTrue();
        assertThat(updatedSnapshot.lifecycleState()).isEqualTo(RedisTransportHealth.LifecycleState.CONNECTED);
        assertThat(updatedSnapshot.activeSubscriberThreads()).isEqualTo(2);
    }
}
