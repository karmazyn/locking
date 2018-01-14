package com.github.karmazyn.locking;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    @Test
    public void shouldIdentifyEqualObjects() {
        //given
        TestObject testSubject = new TestObject("test");
        //when&then
        assertThat(testSubject.equals(null)).isFalse();
        assertThat(testSubject.equals(testSubject)).isTrue();
        assertThat(testSubject.equals(new TestObject("test"))).isTrue();
        assertThat(testSubject.equals(new TestObject("other"))).isFalse();
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

        //when
        executor.execute(() -> {
            Mutex lock = locker.lock(new TestObject("test"));
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
        assertLockReleased(new TestObject("test"));
    }

    @Test
    public void shouldNotBlockIfLockAcquiredFromSameThread() throws InterruptedException {
        //given
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean verifier = new AtomicBoolean(false);

        //when
        executor.execute(() -> {
            Mutex firstLock = locker.lock(new TestObject("test"));
            try {
                Mutex secondLock = locker.lock(new TestObject("test"));
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


        //assert lock released
        assertLockReleased(new TestObject("test"));

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
                lock = locker.lock(new TestObject("test"));

                verifier.incrementAndGet();
                afterIncrement.countDown();

                beforeRelease.await(AWAIT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);

            } catch (InterruptedException e) {
                e.printStackTrace();
                fail("timed out in runnable");
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
        assertLockReleased(new TestObject("test"));
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
            Mutex lock = locker.lock(new TestObject("first"));
            try {
                verifier.incrementAndGet();
                afterIncrement.countDown();
                beforeRelease.await(AWAIT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                fail("timed out");
            } finally {
                lock.release();
                finished.countDown();
            }
        });
        executor.execute(() -> {
            Mutex lock = locker.lock(new TestObject("second"));
            try {
                verifier.incrementAndGet();
                afterIncrement.countDown();
                beforeRelease.await(AWAIT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                fail("timed out");
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
        assertLockReleased(new TestObject("first"));
        assertLockReleased(new TestObject("second"));

    }

    private void assertLockReleased(TestObject testObject) throws InterruptedException {
        AtomicBoolean verifiedLocked = new AtomicBoolean(false);
        CountDownLatch finishedRelease = new CountDownLatch(1);
        executor.execute(() -> {
            Mutex lock = locker.lock(testObject);
            try {
                verifiedLocked.set(true);
            } finally {
                lock.release();
                finishedRelease.countDown();
            }

        });

        finishedRelease.await(AWAIT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
        assertThat(verifiedLocked).isTrue();
    }

    class TestObject {

        private final String value;

        TestObject(String value) {
            this.value = value;
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || !(obj instanceof TestObject)) {
                return false;
            }
            return Objects.equals(value, ((TestObject) obj).value);
        }
    }

}