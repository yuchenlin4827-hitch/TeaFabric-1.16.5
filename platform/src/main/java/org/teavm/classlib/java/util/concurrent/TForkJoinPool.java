package org.teavm.classlib.java.util.concurrent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * ForkJoinPool on green threads: a FIFO shared by up to {@code parallelism} worker fibers created
 * through the configured factory (Minecraft names its workers through it).
 */
public class TForkJoinPool extends TAbstractExecutorService {
    public interface ForkJoinWorkerThreadFactory {
        TForkJoinWorkerThread newThread(TForkJoinPool pool);
    }

    public interface ManagedBlocker {
        boolean block() throws InterruptedException;

        boolean isReleasable();
    }

    public static final ForkJoinWorkerThreadFactory defaultForkJoinWorkerThreadFactory =
            pool -> new TForkJoinWorkerThread(pool) { };

    private static TForkJoinPool common;

    private final ArrayDeque<TForkJoinTask<?>> queue = new ArrayDeque<>();
    private final List<TForkJoinWorkerThread> workers = new ArrayList<>();
    private final int parallelism;
    private final ForkJoinWorkerThreadFactory factory;
    private final Thread.UncaughtExceptionHandler handler;
    private final boolean asyncMode;
    private int idle;
    private int nextIndex;
    private boolean shutdown;

    public TForkJoinPool() {
        this(Math.max(1, Runtime.getRuntime().availableProcessors()));
    }

    public TForkJoinPool(int parallelism) {
        this(parallelism, defaultForkJoinWorkerThreadFactory, null, false);
    }

    public TForkJoinPool(int parallelism, ForkJoinWorkerThreadFactory factory,
            Thread.UncaughtExceptionHandler handler, boolean asyncMode) {
        if (parallelism <= 0) {
            throw new IllegalArgumentException();
        }
        this.parallelism = Math.min(parallelism, 64);
        this.factory = factory;
        this.handler = handler;
        this.asyncMode = asyncMode;
    }

    public static synchronized TForkJoinPool commonPool() {
        if (common == null) {
            common = new TForkJoinPool(Math.max(1, Runtime.getRuntime().availableProcessors()));
        }
        return common;
    }

    public static int getCommonPoolParallelism() {
        return commonPool().parallelism;
    }

    public static void managedBlock(ManagedBlocker blocker) throws InterruptedException {
        while (!blocker.isReleasable()) {
            if (blocker.block()) {
                return;
            }
        }
    }

    public int getParallelism() {
        return parallelism;
    }

    public boolean getAsyncMode() {
        return asyncMode;
    }

    public ForkJoinWorkerThreadFactory getFactory() {
        return factory;
    }

    public Thread.UncaughtExceptionHandler getUncaughtExceptionHandler() {
        return handler;
    }

    public synchronized int getPoolSize() {
        return workers.size();
    }

    public synchronized int getActiveThreadCount() {
        return workers.size() - idle;
    }

    public synchronized boolean isQuiescent() {
        return queue.isEmpty() && idle == workers.size();
    }

    public synchronized long getQueuedTaskCount() {
        return queue.size();
    }

    public synchronized int getQueuedSubmissionCount() {
        return queue.size();
    }

    void enqueue(TForkJoinTask<?> task) {
        TForkJoinWorkerThread spawn = null;
        synchronized (this) {
            if (shutdown) {
                throw new TRejectedExecutionException("ForkJoinPool is shut down");
            }
            queue.add(task);
            notifyAll();
            if (idle == 0 && workers.size() < parallelism) {
                spawn = factory.newThread(this);
                spawn.index = nextIndex++;
                workers.add(spawn);
            }
        }
        if (spawn != null) {
            spawn.start();
        }
    }

    synchronized boolean tryRemove(TForkJoinTask<?> task) {
        return queue.remove(task);
    }

    void runWorker(TForkJoinWorkerThread worker) throws InterruptedException {
        while (true) {
            TForkJoinTask<?> task;
            synchronized (this) {
                idle++;
                try {
                    while (queue.isEmpty()) {
                        if (shutdown) {
                            return;
                        }
                        wait();
                    }
                } finally {
                    idle--;
                }
                task = queue.poll();
            }
            task.doExec();
            if (task.isCompletedAbnormally() && handler != null && task instanceof TForkJoinTask.AdaptedCallable) {
                Throwable t = task.getException();
                if (t != null && !(t instanceof java.util.concurrent.CancellationException)) {
                    handler.uncaughtException(worker, t);
                }
            }
        }
    }

    synchronized void workerExited(TForkJoinWorkerThread worker, Throwable failure) {
        workers.remove(worker);
        notifyAll();
        if (failure != null && handler != null) {
            handler.uncaughtException(worker, failure);
        }
    }

    @Override
    public void execute(Runnable task) {
        enqueue(task instanceof TForkJoinTask ? (TForkJoinTask<?>) task : TForkJoinTask.adapt(task));
    }

    public void execute(TForkJoinTask<?> task) {
        enqueue(task);
    }

    public <T> T invoke(TForkJoinTask<T> task) {
        enqueue(task);
        return task.join();
    }

    public <T> TForkJoinTask<T> submit(TForkJoinTask<T> task) {
        enqueue(task);
        return task;
    }

    @Override
    public TForkJoinTask<?> submit(Runnable task) {
        TForkJoinTask<?> t = TForkJoinTask.adapt(task);
        enqueue(t);
        return t;
    }

    @Override
    public <T> TForkJoinTask<T> submit(Runnable task, T result) {
        TForkJoinTask<T> t = TForkJoinTask.adapt(task, result);
        enqueue(t);
        return t;
    }

    @Override
    public <T> TForkJoinTask<T> submit(Callable<T> task) {
        TForkJoinTask<T> t = TForkJoinTask.adapt(task);
        enqueue(t);
        return t;
    }

    @Override
    public synchronized void shutdown() {
        if (this != common) {
            shutdown = true;
            notifyAll();
        }
    }

    @Override
    public synchronized List<Runnable> shutdownNow() {
        shutdown();
        List<Runnable> pending = new ArrayList<>();
        queue.clear();
        return pending;
    }

    @Override
    public synchronized boolean isShutdown() {
        return shutdown;
    }

    @Override
    public synchronized boolean isTerminated() {
        return shutdown && workers.isEmpty();
    }

    public synchronized boolean isTerminating() {
        return shutdown && !workers.isEmpty();
    }

    @Override
    public synchronized boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (!isTerminated()) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) {
                return false;
            }
            wait(left);
        }
        return true;
    }

    public boolean awaitQuiescence(long timeout, TimeUnit unit) {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        synchronized (this) {
            while (!isQuiescent()) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) {
                    return false;
                }
                try {
                    wait(left);
                } catch (InterruptedException e) {
                    return false;
                }
            }
        }
        return true;
    }
}
