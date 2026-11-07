package org.teavm.classlib.java.util.concurrent;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public abstract class TForkJoinTask<V> implements TFuture<V> {
    private int status; // 0 running/new, 1 done, 2 failed, 3 cancelled
    private Throwable failure;

    public abstract V getRawResult();

    protected abstract void setRawResult(V value);

    protected abstract boolean exec();

    final void doExec() {
        synchronized (this) {
            if (status != 0) {
                return;
            }
        }
        try {
            if (exec()) {
                finish(1, null);
            }
        } catch (Throwable t) {
            finish(2, t);
        }
    }

    private synchronized void finish(int s, Throwable t) {
        if (status == 0) {
            status = s;
            failure = t;
            notifyAll();
        }
    }

    public final TForkJoinTask<V> fork() {
        Thread t = Thread.currentThread();
        TForkJoinPool pool = t instanceof TForkJoinWorkerThread ? ((TForkJoinWorkerThread) t).pool
                : TForkJoinPool.commonPool();
        pool.enqueue(this);
        return this;
    }

    public final V join() {
        if (!isDone() && Thread.currentThread() instanceof TForkJoinWorkerThread) {
            // Help: run it ourselves if nobody picked it up yet (avoids starving the single worker).
            ((TForkJoinWorkerThread) Thread.currentThread()).pool.tryRemove(this);
            doExec();
        }
        try {
            awaitDone();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        synchronized (this) {
            if (status == 3) {
                throw new CancellationException();
            }
            if (status == 2) {
                if (failure instanceof RuntimeException) {
                    throw (RuntimeException) failure;
                }
                if (failure instanceof Error) {
                    throw (Error) failure;
                }
                throw new RuntimeException(failure);
            }
        }
        return getRawResult();
    }

    public final V invoke() {
        doExec();
        return join();
    }

    private synchronized void awaitDone() throws InterruptedException {
        while (status == 0) {
            wait();
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        synchronized (this) {
            if (status != 0) {
                return false;
            }
        }
        finish(3, null);
        return true;
    }

    public final void complete(V value) {
        setRawResult(value);
        finish(1, null);
    }

    public void completeExceptionally(Throwable ex) {
        finish(2, ex);
    }

    @Override
    public final synchronized boolean isDone() {
        return status != 0;
    }

    @Override
    public final synchronized boolean isCancelled() {
        return status == 3;
    }

    public final synchronized boolean isCompletedAbnormally() {
        return status >= 2;
    }

    public final synchronized boolean isCompletedNormally() {
        return status == 1;
    }

    public final synchronized Throwable getException() {
        return status == 3 ? new CancellationException() : failure;
    }

    @Override
    public final V get() throws InterruptedException, ExecutionException {
        awaitDone();
        synchronized (this) {
            if (status == 3) {
                throw new CancellationException();
            }
            if (status == 2) {
                throw new ExecutionException(failure);
            }
        }
        return getRawResult();
    }

    @Override
    public final V get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TTimeoutException {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        synchronized (this) {
            while (status == 0) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) {
                    throw new TTimeoutException();
                }
                wait(left);
            }
        }
        return get();
    }

    public static TForkJoinTask<?> adapt(Runnable runnable) {
        return new AdaptedCallable<>(TExecutors.callable(runnable, null));
    }

    public static <T> TForkJoinTask<T> adapt(Runnable runnable, T result) {
        return new AdaptedCallable<>(TExecutors.callable(runnable, result));
    }

    public static <T> TForkJoinTask<T> adapt(java.util.concurrent.Callable<? extends T> callable) {
        return new AdaptedCallable<>(callable);
    }

    static final class AdaptedCallable<T> extends TForkJoinTask<T> implements TRunnableFuture<T> {
        private final java.util.concurrent.Callable<? extends T> callable;
        private T result;

        AdaptedCallable(java.util.concurrent.Callable<? extends T> callable) {
            this.callable = callable;
        }

        @Override
        public T getRawResult() {
            return result;
        }

        @Override
        protected void setRawResult(T value) {
            result = value;
        }

        @Override
        protected boolean exec() {
            try {
                result = callable.call();
                return true;
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void run() {
            invoke();
        }
    }
}
