# How to build
Use gradle wrapper distributed in repo. For more verbose output use `--info`

    ./gradlew build

# Performance test
There is a very minimal performance test in the LockerImplTest. The test starts 200 concurrent threads that perform operations on the same lock. There are no assertions. The purpose is to see the execution time. The test is excluded from normal build. Test was run from IDE.
## Locker version
execution time 1s 900ms
```
@Category(PerformanceTest.class)
@Test
public void shouldHandle200Threads() throws InterruptedException {
    //given
    CountDownLatch beforeLock = new CountDownLatch(1);
    CountDownLatch finished = new CountDownLatch(200);
    AtomicInteger verifier = new AtomicInteger(0);
    String uuid = UUID.randomUUID().toString();
    ExecutorService executorService = Executors.newCachedThreadPool();

    Runnable testRunnable = () -> {
        Mutex lock = null;
        try {
            beforeLock.await(AWAIT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
            lock = locker.lock(new String(uuid));
            long accumulator = 0;
            for (int i = 0; i < 100000; i++) {
                accumulator += Math.sin(i);
            }
            verifier.addAndGet((int) (accumulator * 0 + 1));
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
    for (int i = 0; i < 200; i++) {
        executorService.execute(testRunnable);
    }
    beforeLock.countDown();

    //then
    finished.await(AWAIT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
    assertThat(verifier).hasValue(200);
    assertLockReleased(new String(uuid));
}
```
## Synchronized version
Execution time: 1s 900ms
```
@Category(PerformanceTest.class)
@Test
public void shouldHandle200Threads() throws InterruptedException {
    //given
    CountDownLatch beforeLock = new CountDownLatch(1);
    CountDownLatch finished = new CountDownLatch(200);
    AtomicInteger verifier = new AtomicInteger(0);
    Object obj = new Object();
    ExecutorService executorService = Executors.newCachedThreadPool();

    Runnable testRunnable = () -> {
        try {
            beforeLock.await(AWAIT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
            synchronized (obj) {
                long accumulator = 0;
                for (int i = 0; i < 100000; i++) {
                    accumulator += Math.sin(i);
                }
                verifier.addAndGet((int) (accumulator * 0 + 1));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            finished.countDown();
        }
    };

    //when
    for (int i = 0; i < 200; i++) {
        executorService.execute(testRunnable);
    }
    beforeLock.countDown();

    //then
    finished.await(AWAIT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
    assertThat(verifier).hasValue(200);
}
```
## ReentrantLock version
execution time:  3s 100ms
```
@Category(PerformanceTest.class)
@Test
public void shouldHandle200Threads() throws InterruptedException {
    //given
    CountDownLatch beforeLock = new CountDownLatch(1);
    CountDownLatch finished = new CountDownLatch(200);
    AtomicInteger verifier = new AtomicInteger(0);
    ReentrantLock lock = new ReentrantLock();
    ExecutorService executorService = Executors.newCachedThreadPool();

    Runnable testRunnable = () -> {
        try {
            beforeLock.await(AWAIT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
            lock.lock();
            long accumulator = 0;
            for (int i = 0; i < 100000; i++) {
                accumulator += Math.sin(i);
            }
            verifier.addAndGet((int) (accumulator * 0 + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
            finished.countDown();
        }
    };

    //when
    for (int i = 0; i < 200; i++) {
        executorService.execute(testRunnable);
    }
    beforeLock.countDown();

    //then
    finished.await(AWAIT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);

    assertThat(verifier).hasValue(200);

}
```