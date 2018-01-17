package com.github.karmazyn.locking;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class LockerImpl implements Locker {

    private final ConcurrentHashMap<Object, MutexImpl> locks = new ConcurrentHashMap<>();

    @Override
    public Mutex lock(Object object) {
        MutexImpl mutex = Optional.ofNullable(object)
                .map(o -> locks.computeIfAbsent(o, (key) -> new MutexImpl(key)))
                .orElseThrow(() -> new IllegalArgumentException());
        mutex.lockInternal();
        return mutex;
    }

    private class MutexImpl implements Mutex {

        private final Object key;
        private final ReentrantLock internalLock;
        private volatile boolean isRemoved = false;

        MutexImpl(Object key) {
            this.internalLock = new ReentrantLock();
            this.key = key;
        }

        private void lockInternal() {
            internalLock.lock();
            if (isRemoved) {
                releaseInternal();
                LockerImpl.this.lock(key);
            }
        }

        @Override
        public void release() {
            if (internalLock.getQueueLength() == 0) {
                isRemoved = true;
            }

            releaseInternal();

            if (isRemoved) {
                LockerImpl.this.locks.remove(key, this);
            }
        }


        private void releaseInternal() {
            internalLock.unlock();
        }
    }
}
