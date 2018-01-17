package com.github.karmazyn.locking;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class LockerImplTest {

    private static final int AWAIT_TIMEOUT_IN_SECONDS = 5;
    private LockerImpl locker;
    private ExecutorService executor;

    @Before
    public void setUp() {
        locker = new LockerImpl();
        executor = Executors.newFixedThreadPool(2);
    }

    @After
    public void tearDown() {
        executor.shutdownNow();
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIAEWhenNullObject() {
        //when
        locker.lock(null);
    }

    @Test
    public void shouldNotBlockSingleThread() throws InterruptedException {
        //given
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean verifier = new AtomicBoolean(false);
        String testObject = new String("test");

        //when
        executor.execute(() -> {
            Mutex lock = locker.lock(testObject);
            try {
                verifier.set(true);
            } finally {
                lock.release();
                finished.countDown();
            }
        });

        //then
        finished.await(AWAIT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
        assertThat(verifier).isTrue();
        assertLockReleased(testObject);
    }

    @Test
    public void shouldNotBlockIfLockAcquiredFromSameThread() throws InterruptedException {
        //given
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean verifier = new AtomicBoolean(false);

        //when
        executor.execute(() -> {
            Mutex firstLock = locker.lock(new String("test"));
            try {
                Mutex secondLock = locker.lock(new String("test"));
                try {
                    verifier.set(true);
                } finally {
                    secondLock.release();
                }
            } finally {
                firstLock.release();
                finished.countDown();
            }
        });

        //then
        finished.await(AWAIT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
        assertThat(verifier).isTrue();
        assertLockReleased(new String("test"));
    }

    @Test
    public void shouldBlockSecondThread() throws InterruptedException {
        //given
        CountDownLatch beforeLock = new CountDownLatch(1);
        CountDownLatch afterIncrement = new CountDownLatch(1);
        CountDownLatch beforeRelease = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(2);
        AtomicInteger verifier = new AtomicInteger(0);

        Runnable testRunnable = () -> {
            Mutex lock = null;
            try {
                beforeLock.await(AWAIT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
                lock = locker.lock(new String("test"));

                verifier.incrementAndGet();
                afterIncrement.countDown();

                beforeRelease.await(AWAIT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                if (lock != null) {
                    lock.release();
                }
                finished.countDown();
            }
        };

        //when
        executor.execute(testRunnable);
        executor.execute(testRunnable);
        beforeLock.countDown();

        //then
        afterIncrement.await(AWAIT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
        assertThat(verifier).hasValue(1);

        beforeRelease.countDown();
        finished.await(AWAIT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
        assertThat(verifier).hasValue(2);
        assertLockReleased(new String("test"));
    }

    @Test
    public void shouldNotBlockSecondThreadIfFirstLocksOnDifferentObject() throws InterruptedException {
        //given
        CountDownLatch beforeRelease = new CountDownLatch(1);
        CountDownLatch afterIncrement = new CountDownLatch(2);
        CountDownLatch finished = new CountDownLatch(2);
        AtomicInteger verifier = new AtomicInteger(0);

        //when
        executor.execute(() -> {
            Mutex lock = locker.lock(new String("first"));
            try {
                verifier.incrementAndGet();
                afterIncrement.countDown();
                beforeRelease.await(AWAIT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.release();
                finished.countDown();
            }
        });
        executor.execute(() -> {
            Mutex lock = locker.lock(new String("second"));
            try {
                verifier.incrementAndGet();
                afterIncrement.countDown();
                beforeRelease.await(AWAIT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.release();
                finished.countDown();
            }
        });

        //then
        afterIncrement.await(AWAIT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
        assertThat(verifier).hasValue(2);
        beforeRelease.countDown();
        finished.await(AWAIT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
        assertLockReleased(new String("first"));
        assertLockReleased(new String("second"));
    }

    @Test
    public void shouldFailWhenTryingToReleaseSecondTime() throws InterruptedException {
        //given
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean verifier = new AtomicBoolean(false);

        //when
        Future<?> callable = executor.submit(() -> {
            try {
                Mutex lock = locker.lock(new String("test"));
                lock.release();
                verifier.set(true);
                lock.release();
            } finally {
                finished.countDown();
            }
        });

        //then
        finished.await(AWAIT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
        assertThat(verifier).isTrue();
        try {
            callable.get();
            fail("Expected exception not thrown");
        } catch (ExecutionException ex) {
            assertThat(ex.getCause()).isInstanceOf(IllegalMonitorStateException.class);
        }
    }

    @Test
    public void shouldThrowExceptionWhenTryingToReleaseNotOwnedLock() throws InterruptedException {
        //given
        CountDownLatch beforeLock = new CountDownLatch(1);
        CountDownLatch afterLock = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(2);
        AtomicBoolean verifier = new AtomicBoolean(false);

        //when
        executor.execute(() -> {
            Mutex lock = null;
            try {
                beforeLock.await(AWAIT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
                lock = locker.lock(new String("test"));
                afterLock.countDown();
                verifier.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                if (lock != null) {
                    lock.release();
                }
                finished.countDown();
            }
        });

        Future<?> second = executor.submit(() -> {
            //lock & release just to have a reference to lock
            Mutex lock = locker.lock(new String("test"));
            try {
                beforeLock.countDown();
            } finally {
                lock.release();
            }

            try {
                afterLock.await(AWAIT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
                lock.release();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                finished.countDown();
            }
        });

        //then
        finished.await(AWAIT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
        assertThat(verifier).isTrue();
        try {
            second.get();
            fail("Expected exception not thrown");
        } catch (ExecutionException ex) {
            assertThat(ex.getCause()).isInstanceOf(IllegalMonitorStateException.class);
        }
        assertLockReleased(new String("first"));

    }


    private void assertLockReleased(Object testObject) throws InterruptedException {
        AtomicBoolean verified = new AtomicBoolean(false);
        CountDownLatch finished = new CountDownLatch(1);
        executor.execute(() -> {
            Mutex lock = locker.lock(testObject);
            try {
                verified.set(true);
            } finally {
                lock.release();
                finished.countDown();
            }

        });

        finished.await(AWAIT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
        assertThat(verified).isTrue();
    }

}