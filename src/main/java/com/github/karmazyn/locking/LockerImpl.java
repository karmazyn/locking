package com.github.karmazyn.locking;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class LockerImpl implements Locker {

    private final ConcurrentHashMap<Object, MutexImpl> locks = new ConcurrentHashMap<>();

    @Override
    public Mutex lock(Object object) {

        if (object == null) {
            throw new IllegalArgumentException();
        }

        boolean locked = false;
        MutexImpl mutex = null;

        while (!locked) {
            mutex = locks.computeIfAbsent(object, (key) -> new MutexImpl(key));
            locked = mutex.lockInternal();
        }
        return mutex;
    }

    private boolean remove(Object key, Object expected) {
        return locks.remove(key, expected);
    }

    private class MutexImpl implements Mutex {

        private final Object key;
        private final ReentrantLock internalLock;
        private volatile boolean isRemoved = false;

        MutexImpl(Object key) {
            this.internalLock = new ReentrantLock();
            this.key = key;
        }

        private boolean lockInternal() {
            internalLock.lock();
            if (isRemoved) {
                internalLock.unlock();
                return false;
            }
            return true;
        }

        @Override
        public void release() {
            try {
                if (internalLock.getQueueLength() == 0) {
                    isRemoved = true;
                    LockerImpl.this.remove(key, this);
                }
            } finally {
                internalLock.unlock();
            }
        }

    }
}
