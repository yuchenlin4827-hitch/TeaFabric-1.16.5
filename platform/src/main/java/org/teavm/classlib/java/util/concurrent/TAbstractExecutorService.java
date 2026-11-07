package org.teavm.classlib.java.util.concurrent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public abstract class TAbstractExecutorService implements TExecutorService {
    protected <T> TRunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return new TFutureTask<>(runnable, value);
    }

    protected <T> TRunnableFuture<T> newTaskFor(Callable<T> callable) {
        return new TFutureTask<>(callable);
    }

    @Override
    public TFuture<?> submit(Runnable task) {
        TRunnableFuture<Void> f = newTaskFor(task, null);
        execute(f);
        return f;
    }

    @Override
    public <T> TFuture<T> submit(Runnable task, T result) {
        TRunnableFuture<T> f = newTaskFor(task, result);
        execute(f);
        return f;
    }

    @Override
    public <T> TFuture<T> submit(Callable<T> task) {
        TRunnableFuture<T> f = newTaskFor(task);
        execute(f);
        return f;
    }

    @Override
    public <T> List<TFuture<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        List<TFuture<T>> futures = new ArrayList<>();
        for (Callable<T> t : tasks) {
            futures.add(submit(t));
        }
        for (TFuture<T> f : futures) {
            try {
                f.get();
            } catch (ExecutionException | java.util.concurrent.CancellationException e) {
                // reported through the future
            }
        }
        return futures;
    }

    @Override
    public <T> List<TFuture<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        return invokeAll(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        ExecutionException last = null;
        for (Callable<T> t : tasks) {
            try {
                return submit(t).get();
            } catch (ExecutionException e) {
                last = e;
            }
        }
        throw last != null ? last : new ExecutionException(new IllegalArgumentException("no tasks"));
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TTimeoutException {
        return invokeAny(tasks);
    }
}
