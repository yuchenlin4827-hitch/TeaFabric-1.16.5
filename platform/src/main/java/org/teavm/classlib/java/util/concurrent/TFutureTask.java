package org.teavm.classlib.java.util.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class TFutureTask<V> implements TRunnableFuture<V> {
    private static final int NEW = 0;
    private static final int RUNNING = 1;
    private static final int DONE = 2;
    private static final int FAILED = 3;
    private static final int CANCELLED = 4;

    private Callable<V> callable;
    private int state;
    private V result;
    private Throwable failure;
    private Thread runner;

    public TFutureTask(Callable<V> callable) {
        if (callable == null) {
            throw new NullPointerException();
        }
        this.callable = callable;
    }

    public TFutureTask(Runnable runnable, V result) {
        this(TExecutors.callable(runnable, result));
    }

    @Override
    public void run() {
        synchronized (this) {
            if (state != NEW) {
                return;
            }
            state = RUNNING;
            runner = Thread.currentThread();
        }
        V v = null;
        Throwable t = null;
        try {
            v = callable.call();
        } catch (Throwable e) {
            t = e;
        }
        synchronized (this) {
            runner = null;
            if (state == RUNNING) {
                if (t != null) {
                    failure = t;
                    state = FAILED;
                } else {
                    result = v;
                    state = DONE;
                }
            }
            notifyAll();
        }
        done();
    }

    protected boolean runAndReset() {
        synchronized (this) {
            if (state != NEW) {
                return false;
            }
            state = RUNNING;
        }
        try {
            callable.call();
            synchronized (this) {
                if (state == RUNNING) {
                    state = NEW;
                    return true;
                }
                return false;
            }
        } catch (Throwable e) {
            setException(e);
            return false;
        }
    }

    protected void done() {
    }

    protected void set(V v) {
        synchronized (this) {
            if (state > RUNNING) {
                return;
            }
            result = v;
            state = DONE;
            notifyAll();
        }
        done();
    }

    protected void setException(Throwable t) {
        synchronized (this) {
            if (state > RUNNING) {
                return;
            }
            failure = t;
            state = FAILED;
            notifyAll();
        }
        done();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        Thread r;
        synchronized (this) {
            if (state > RUNNING) {
                return false;
            }
            state = CANCELLED;
            r = runner;
            notifyAll();
        }
        if (mayInterruptIfRunning && r != null) {
            r.interrupt();
        }
        done();
        return true;
    }

    @Override
    public synchronized boolean isCancelled() {
        return state == CANCELLED;
    }

    @Override
    public synchronized boolean isDone() {
        return state > RUNNING;
    }

    @Override
    public synchronized V get() throws InterruptedException, ExecutionException {
        while (state <= RUNNING) {
            wait();
        }
        return report();
    }

    @Override
    public synchronized V get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TTimeoutException {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (state <= RUNNING) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) {
                throw new TTimeoutException();
            }
            wait(left);
        }
        return report();
    }

    private V report() throws ExecutionException {
        if (state == CANCELLED) {
            throw new CancellationException();
        }
        if (state == FAILED) {
            throw new ExecutionException(failure);
        }
        return result;
    }
}
