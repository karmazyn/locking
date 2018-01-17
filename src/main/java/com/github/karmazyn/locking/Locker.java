package com.github.karmazyn.locking;

public interface Locker {
        Mutex lock(Object obj);
}
