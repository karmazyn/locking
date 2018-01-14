package com.github.karmazyn.locking;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class LockerImpl implements Locker {

    private final ConcurrentHashMap<Object, MutexImpl> locks = new ConcurrentHashMap<>();

    @Override
    public Mutex lock(Object object) {
        MutexImpl mutex = Optional.ofNullable(object)
                .map(o -> locks.computeIfAbsent(o, (key) -> new MutexImpl()))
                .orElseThrow(() -> new IllegalArgumentException());
        mutex.lockInternal();
        return mutex;
    }

    private static class MutexImpl implements Mutex {

        private final ReentrantLock internalLock;

        public MutexImpl() {
            this.internalLock = new ReentrantLock();
        }

        private void lockInternal() {
            internalLock.lock();
        }

        @Override
        public void release() {
            internalLock.unlock();
        }
    }
}
