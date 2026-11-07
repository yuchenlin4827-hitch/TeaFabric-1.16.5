package org.teavm.classlib.java.util.concurrent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Thread pool on TeaVM green threads. Workers are created on demand up to the maximum pool size and
 * take tasks from an internal FIFO; the BlockingQueue passed to the constructor is accepted for API
 * compatibility only.
 */
public class TThreadPoolExecutor extends TAbstractExecutorService {
    private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    private final List<Thread> workers = new ArrayList<>();
    private final TThreadFactory threadFactory;
    private int corePoolSize;
    private int maximumPoolSize;
    private long keepAliveMillis;
    private int idle;
    private boolean shutdown;

    public TThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, TExecutors.defaultThreadFactory());
    }

    public TThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue, TThreadFactory threadFactory) {
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = Math.max(1, Math.min(maximumPoolSize, 64));
        this.keepAliveMillis = unit.toMillis(keepAliveTime);
        this.threadFactory = threadFactory;
    }

    @Override
    public void execute(Runnable command) {
        if (command == null) {
            throw new NullPointerException();
        }
        boolean spawn;
        synchronized (this) {
            if (shutdown) {
                throw new TRejectedExecutionException("Executor has been shut down");
            }
            tasks.add(command);
            spawn = idle == 0 && workers.size() < maximumPoolSize;
            notifyAll();
        }
        if (spawn) {
            Thread t = threadFactory.newThread(this::workerLoop);
            synchronized (this) {
                workers.add(t);
            }
            t.start();
        }
    }

    private void workerLoop() {
        try {
            while (true) {
                Runnable task;
                synchronized (this) {
                    idle++;
                    try {
                        long deadline = System.currentTimeMillis() + Math.max(keepAliveMillis, 1000);
                        while (tasks.isEmpty() && !shutdown) {
                            if (workers.size() > corePoolSize && System.currentTimeMillis() >= deadline) {
                                return;
                            }
                            wait(Math.max(1, deadline - System.currentTimeMillis()));
                        }
                    } catch (InterruptedException e) {
                        return;
                    } finally {
                        idle--;
                    }
                    task = tasks.poll();
                    if (task == null) {
                        return;
                    }
                }
                try {
                    task.run();
                } catch (Throwable t) {
                    Thread.UncaughtExceptionHandler h = Thread.currentThread().getUncaughtExceptionHandler();
                    if (h != null) {
                        h.uncaughtException(Thread.currentThread(), t);
                    } else {
                        t.printStackTrace();
                    }
                }
            }
        } finally {
            synchronized (this) {
                workers.remove(Thread.currentThread());
                notifyAll();
            }
        }
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public void setMaximumPoolSize(int maximumPoolSize) {
        this.maximumPoolSize = maximumPoolSize;
    }

    public synchronized int getPoolSize() {
        return workers.size();
    }

    public synchronized int getActiveCount() {
        return workers.size() - idle;
    }

    @Override
    public synchronized void shutdown() {
        shutdown = true;
        notifyAll();
    }

    @Override
    public synchronized List<Runnable> shutdownNow() {
        shutdown = true;
        List<Runnable> pending = new ArrayList<>(tasks);
        tasks.clear();
        for (Thread t : workers) {
            t.interrupt();
        }
        notifyAll();
        return pending;
    }

    @Override
    public synchronized boolean isShutdown() {
        return shutdown;
    }

    @Override
    public synchronized boolean isTerminated() {
        return shutdown && workers.isEmpty() && tasks.isEmpty();
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
}
