package org.teavm.classlib.java.util.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/** Scheduling on green threads: each scheduled task sleeps in its own fiber, then runs on the pool. */
public class TScheduledThreadPoolExecutor extends TThreadPoolExecutor implements TScheduledExecutorService {
    public TScheduledThreadPoolExecutor(int corePoolSize) {
        super(corePoolSize, Math.max(1, corePoolSize), 0, TimeUnit.MILLISECONDS, null);
    }

    public TScheduledThreadPoolExecutor(int corePoolSize, TThreadFactory factory) {
        super(corePoolSize, Math.max(1, corePoolSize), 0, TimeUnit.MILLISECONDS, null, factory);
    }

    private final class Scheduled<V> extends TFutureTask<V> implements TScheduledFuture<V> {
        private final long periodMillis;
        private final boolean fixedRate;
        private long nextRun;

        Scheduled(Callable<V> callable, long delayMillis, long periodMillis, boolean fixedRate) {
            super(callable);
            this.periodMillis = periodMillis;
            this.fixedRate = fixedRate;
            this.nextRun = System.currentTimeMillis() + delayMillis;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(nextRun - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(TDelayed o) {
            return Long.compare(getDelay(TimeUnit.MILLISECONDS), o.getDelay(TimeUnit.MILLISECONDS));
        }

        void start() {
            Thread timer = new Thread(() -> {
                try {
                    while (!isDone() && !isShutdown()) {
                        long wait = nextRun - System.currentTimeMillis();
                        if (wait > 0) {
                            Thread.sleep(wait);
                        }
                        if (isDone() || isShutdown()) {
                            return;
                        }
                        if (periodMillis <= 0) {
                            execute(this);
                            return;
                        }
                        long started = System.currentTimeMillis();
                        if (!runAndReset()) {
                            return;
                        }
                        nextRun = fixedRate ? nextRun + periodMillis : System.currentTimeMillis() + periodMillis;
                        if (nextRun < started) {
                            nextRun = started;
                        }
                    }
                } catch (InterruptedException e) {
                    // cancelled
                }
            }, "scheduler");
            timer.setDaemon(true);
            timer.start();
        }
    }

    @Override
    public TScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        Scheduled<Object> s = new Scheduled<>(TExecutors.callable(command), unit.toMillis(delay), 0, false);
        s.start();
        return s;
    }

    @Override
    public <V> TScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        Scheduled<V> s = new Scheduled<>(callable, unit.toMillis(delay), 0, false);
        s.start();
        return s;
    }

    @Override
    public TScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        Scheduled<Object> s = new Scheduled<>(TExecutors.callable(command), unit.toMillis(initialDelay),
                Math.max(1, unit.toMillis(period)), true);
        s.start();
        return s;
    }

    @Override
    public TScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay,
            TimeUnit unit) {
        Scheduled<Object> s = new Scheduled<>(TExecutors.callable(command), unit.toMillis(initialDelay),
                Math.max(1, unit.toMillis(delay)), false);
        s.start();
        return s;
    }
}
