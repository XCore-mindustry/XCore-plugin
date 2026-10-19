package org.xcore.plugin.service.map.inspect;

import jakarta.inject.Singleton;

import java.util.concurrent.locks.ReentrantLock;

/** Serializes expensive worker reads with preview generation. Never acquired by the game thread. */
@Singleton
public final class DetachedMapReadLock {
    private final ReentrantLock lock = new ReentrantLock();

    public void lock() { lock.lock(); }
    public void unlock() { lock.unlock(); }
}
