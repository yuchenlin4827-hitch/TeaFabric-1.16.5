package org.teavm.classlib.java.util.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public final class TExecutors {
    private TExecutors() {
    }

    public static <T> Callable<T> callable(Runnable task, T result) {
        if (task == null) {
            throw new NullPointerException();
        }
        return () -> {
            task.run();
            return result;
        };
    }

    public static Callable<Object> callable(Runnable task) {
        return callable(task, null);
    }

    public static TThreadFactory defaultThreadFactory() {
        return new TThreadFactory() {
            private int count;

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "pool-thread-" + (++count));
                t.setDaemon(true);
                return t;
            }
        };
    }

    public static TExecutorService newFixedThreadPool(int nThreads) {
        return new TThreadPoolExecutor(nThreads, nThreads, 0, TimeUnit.MILLISECONDS, null);
    }

    public static TExecutorService newFixedThreadPool(int nThreads, TThreadFactory factory) {
        return new TThreadPoolExecutor(nThreads, nThreads, 0, TimeUnit.MILLISECONDS, null, factory);
    }

    public static TExecutorService newCachedThreadPool() {
        return new TThreadPoolExecutor(0, 16, 60, TimeUnit.SECONDS, null);
    }

    public static TExecutorService newCachedThreadPool(TThreadFactory factory) {
        return new TThreadPoolExecutor(0, 16, 60, TimeUnit.SECONDS, null, factory);
    }

    public static TExecutorService newSingleThreadExecutor() {
        return newFixedThreadPool(1);
    }

    public static TExecutorService newSingleThreadExecutor(TThreadFactory factory) {
        return newFixedThreadPool(1, factory);
    }

    public static TExecutorService newWorkStealingPool() {
        return new TForkJoinPool();
    }

    public static TExecutorService newWorkStealingPool(int parallelism) {
        return new TForkJoinPool(parallelism);
    }

    public static TScheduledExecutorService newSingleThreadScheduledExecutor() {
        return new TScheduledThreadPoolExecutor(1);
    }

    public static TScheduledExecutorService newSingleThreadScheduledExecutor(TThreadFactory factory) {
        return new TScheduledThreadPoolExecutor(1, factory);
    }

    public static TScheduledExecutorService newScheduledThreadPool(int corePoolSize) {
        return new TScheduledThreadPoolExecutor(corePoolSize);
    }

    public static TScheduledExecutorService newScheduledThreadPool(int corePoolSize, TThreadFactory factory) {
        return new TScheduledThreadPoolExecutor(corePoolSize, factory);
    }

    public static TExecutorService unconfigurableExecutorService(TExecutorService executor) {
        return executor;
    }
}
