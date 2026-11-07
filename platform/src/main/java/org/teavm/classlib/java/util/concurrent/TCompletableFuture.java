package org.teavm.classlib.java.util.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * CompletableFuture for TeaVM green threads. Completion runs dependent actions synchronously (or
 * hands them to the given executor for the *Async variants); blocking getters wait on the monitor.
 */
public class TCompletableFuture<T> implements TFuture<T>, TCompletionStage<T> {
    private static final Executor ASYNC_POOL = r -> TForkJoinPool.commonPool().execute(r);

    private boolean done;
    private Object value;
    private Throwable failure; // already unwrapped; a CancellationException means cancelled
    private List<Runnable> dependents;

    public TCompletableFuture() {
    }

    // ------------------------------------------------------------------ completion core

    private boolean completeWith(Object v, Throwable t) {
        List<Runnable> toRun;
        synchronized (this) {
            if (done) {
                return false;
            }
            done = true;
            value = v;
            failure = t;
            toRun = dependents;
            dependents = null;
            notifyAll();
        }
        if (toRun != null) {
            for (Runnable r : toRun) {
                r.run();
            }
        }
        return true;
    }

    private void onDone(Runnable r) {
        synchronized (this) {
            if (!done) {
                if (dependents == null) {
                    dependents = new ArrayList<>(2);
                }
                dependents.add(r);
                return;
            }
        }
        r.run();
    }

    private static Throwable unwrap(Throwable t) {
        while (t instanceof TCompletionException && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    private static TCompletionException wrap(Throwable t) {
        return t instanceof TCompletionException ? (TCompletionException) t : new TCompletionException(t);
    }

    public boolean complete(T v) {
        return completeWith(v, null);
    }

    public boolean completeExceptionally(Throwable ex) {
        if (ex == null) {
            throw new NullPointerException();
        }
        return completeWith(null, unwrap(ex));
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return completeWith(null, new CancellationException()) || isCancelled();
    }

    public void obtrudeValue(T v) {
        synchronized (this) {
            done = true;
            value = v;
            failure = null;
            notifyAll();
        }
    }

    public void obtrudeException(Throwable ex) {
        synchronized (this) {
            done = true;
            value = null;
            failure = unwrap(ex);
            notifyAll();
        }
    }

    @Override
    public synchronized boolean isDone() {
        return done;
    }

    @Override
    public synchronized boolean isCancelled() {
        return done && failure instanceof CancellationException;
    }

    public synchronized boolean isCompletedExceptionally() {
        return done && failure != null;
    }

    public synchronized int getNumberOfDependents() {
        return dependents == null ? 0 : dependents.size();
    }

    private synchronized void waitDone() throws InterruptedException {
        while (!done) {
            wait();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get() throws InterruptedException, ExecutionException {
        waitDone();
        synchronized (this) {
            if (failure instanceof CancellationException) {
                throw (CancellationException) failure;
            }
            if (failure != null) {
                throw new ExecutionException(failure);
            }
            return (T) value;
        }
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TTimeoutException {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        synchronized (this) {
            while (!done) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) {
                    throw new TTimeoutException();
                }
                wait(left);
            }
        }
        return get();
    }

    @SuppressWarnings("unchecked")
    public T join() {
        try {
            waitDone();
        } catch (InterruptedException e) {
            throw new TCompletionException(e);
        }
        synchronized (this) {
            return (T) reportJoin();
        }
    }

    private Object reportJoin() {
        if (failure instanceof CancellationException) {
            throw (CancellationException) failure;
        }
        if (failure != null) {
            throw wrap(failure);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public synchronized T getNow(T valueIfAbsent) {
        if (!done) {
            return valueIfAbsent;
        }
        return (T) reportJoin();
    }

    public synchronized T resultNow() {
        if (!done || failure != null) {
            throw new IllegalStateException();
        }
        @SuppressWarnings("unchecked")
        T v = (T) value;
        return v;
    }

    public synchronized Throwable exceptionNow() {
        if (!done || failure == null) {
            throw new IllegalStateException();
        }
        return failure;
    }

    // ------------------------------------------------------------------ factories

    public static <U> TCompletableFuture<U> supplyAsync(Supplier<U> supplier) {
        return supplyAsync(supplier, ASYNC_POOL);
    }

    public static <U> TCompletableFuture<U> supplyAsync(Supplier<U> supplier, Executor executor) {
        TCompletableFuture<U> f = new TCompletableFuture<>();
        executor.execute(() -> {
            try {
                f.complete(supplier.get());
            } catch (Throwable t) {
                f.completeWith(null, unwrap(t));
            }
        });
        return f;
    }

    public static TCompletableFuture<Void> runAsync(Runnable runnable) {
        return runAsync(runnable, ASYNC_POOL);
    }

    public static TCompletableFuture<Void> runAsync(Runnable runnable, Executor executor) {
        TCompletableFuture<Void> f = new TCompletableFuture<>();
        executor.execute(() -> {
            try {
                runnable.run();
                f.complete(null);
            } catch (Throwable t) {
                f.completeWith(null, unwrap(t));
            }
        });
        return f;
    }

    public static <U> TCompletableFuture<U> completedFuture(U value) {
        TCompletableFuture<U> f = new TCompletableFuture<>();
        f.complete(value);
        return f;
    }

    public static <U> TCompletionStage<U> completedStage(U value) {
        return completedFuture(value);
    }

    public static <U> TCompletableFuture<U> failedFuture(Throwable ex) {
        TCompletableFuture<U> f = new TCompletableFuture<>();
        f.completeExceptionally(ex);
        return f;
    }

    public static <U> TCompletionStage<U> failedStage(Throwable ex) {
        return failedFuture(ex);
    }

    public static TCompletableFuture<Void> allOf(TCompletableFuture<?>... cfs) {
        TCompletableFuture<Void> result = new TCompletableFuture<>();
        if (cfs.length == 0) {
            result.complete(null);
            return result;
        }
        int[] remaining = {cfs.length};
        for (TCompletableFuture<?> cf : cfs) {
            cf.onDone(() -> {
                boolean last;
                synchronized (remaining) {
                    last = --remaining[0] == 0;
                }
                if (last) {
                    Throwable firstFailure = null;
                    for (TCompletableFuture<?> c : cfs) {
                        synchronized (c) {
                            if (c.failure != null) {
                                firstFailure = c.failure;
                                break;
                            }
                        }
                    }
                    result.completeWith(null, firstFailure);
                }
            });
        }
        return result;
    }

    public static TCompletableFuture<Object> anyOf(TCompletableFuture<?>... cfs) {
        TCompletableFuture<Object> result = new TCompletableFuture<>();
        for (TCompletableFuture<?> cf : cfs) {
            cf.onDone(() -> {
                Object v;
                Throwable t;
                synchronized (cf) {
                    v = cf.value;
                    t = cf.failure;
                }
                result.completeWith(v, t);
            });
        }
        return result;
    }

    public static Executor delayedExecutor(long delay, TimeUnit unit) {
        return delayedExecutor(delay, unit, ASYNC_POOL);
    }

    public static Executor delayedExecutor(long delay, TimeUnit unit, Executor executor) {
        long millis = unit.toMillis(delay);
        return r -> {
            Thread t = new Thread(() -> {
                try {
                    Thread.sleep(millis);
                } catch (InterruptedException e) {
                    return;
                }
                executor.execute(r);
            }, "CompletableFutureDelayScheduler");
            t.setDaemon(true);
            t.start();
        };
    }

    public <U> TCompletableFuture<U> newIncompleteFuture() {
        return new TCompletableFuture<>();
    }

    public Executor defaultExecutor() {
        return ASYNC_POOL;
    }

    public TCompletableFuture<T> copy() {
        return thenApply(x -> x);
    }

    public TCompletionStage<T> minimalCompletionStage() {
        return copy();
    }

    public TCompletableFuture<T> completeAsync(Supplier<? extends T> supplier, Executor executor) {
        executor.execute(() -> {
            try {
                complete(supplier.get());
            } catch (Throwable t) {
                completeWith(null, unwrap(t));
            }
        });
        return this;
    }

    public TCompletableFuture<T> completeAsync(Supplier<? extends T> supplier) {
        return completeAsync(supplier, ASYNC_POOL);
    }

    public TCompletableFuture<T> orTimeout(long timeout, TimeUnit unit) {
        delayedExecutor(timeout, unit, Runnable::run).execute(() -> completeExceptionally(new TTimeoutException()));
        return this;
    }

    public TCompletableFuture<T> completeOnTimeout(T v, long timeout, TimeUnit unit) {
        delayedExecutor(timeout, unit, Runnable::run).execute(() -> complete(v));
        return this;
    }

    // ------------------------------------------------------------------ dependent stages

    /** Registers {@code body(value, failure)} to run (inline or on {@code executor}) once this completes. */
    private void then(Executor executor, BiConsumer<Object, Throwable> body) {
        onDone(() -> {
            Object v;
            Throwable t;
            synchronized (this) {
                v = value;
                t = failure;
            }
            if (executor == null) {
                body.accept(v, t);
            } else {
                try {
                    executor.execute(() -> body.accept(v, t));
                } catch (Throwable rejected) {
                    body.accept(null, rejected);
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private <U> TCompletableFuture<U> uniApply(Executor e, Function<? super T, ? extends U> fn) {
        TCompletableFuture<U> d = new TCompletableFuture<>();
        then(e, (v, t) -> {
            if (t != null) {
                d.completeWith(null, t);
                return;
            }
            try {
                d.complete(fn.apply((T) v));
            } catch (Throwable ex) {
                d.completeWith(null, unwrap(ex));
            }
        });
        return d;
    }

    @Override
    public <U> TCompletableFuture<U> thenApply(Function<? super T, ? extends U> fn) {
        return uniApply(null, fn);
    }

    @Override
    public <U> TCompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return uniApply(ASYNC_POOL, fn);
    }

    @Override
    public <U> TCompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        return uniApply(executor, fn);
    }

    @Override
    public TCompletableFuture<Void> thenAccept(Consumer<? super T> action) {
        return uniApply(null, x -> {
            action.accept(x);
            return null;
        });
    }

    @Override
    public TCompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action) {
        return uniApply(ASYNC_POOL, x -> {
            action.accept(x);
            return null;
        });
    }

    @Override
    public TCompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return uniApply(executor, x -> {
            action.accept(x);
            return null;
        });
    }

    @Override
    public TCompletableFuture<Void> thenRun(Runnable action) {
        return uniApply(null, x -> {
            action.run();
            return null;
        });
    }

    @Override
    public TCompletableFuture<Void> thenRunAsync(Runnable action) {
        return uniApply(ASYNC_POOL, x -> {
            action.run();
            return null;
        });
    }

    @Override
    public TCompletableFuture<Void> thenRunAsync(Runnable action, Executor executor) {
        return uniApply(executor, x -> {
            action.run();
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private <U, V> TCompletableFuture<V> biApply(TCompletionStage<? extends U> other, Executor e,
            BiFunction<? super T, ? super U, ? extends V> fn) {
        TCompletableFuture<? extends U> o = other.toCompletableFuture();
        TCompletableFuture<V> d = new TCompletableFuture<>();
        then(null, (v, t) -> o.then(e, (v2, t2) -> {
            Throwable f = t != null ? t : t2;
            if (f != null) {
                d.completeWith(null, f);
                return;
            }
            try {
                d.complete(fn.apply((T) v, (U) v2));
            } catch (Throwable ex) {
                d.completeWith(null, unwrap(ex));
            }
        }));
        return d;
    }

    @Override
    public <U, V> TCompletableFuture<V> thenCombine(TCompletionStage<? extends U> other,
            BiFunction<? super T, ? super U, ? extends V> fn) {
        return biApply(other, null, fn);
    }

    @Override
    public <U, V> TCompletableFuture<V> thenCombineAsync(TCompletionStage<? extends U> other,
            BiFunction<? super T, ? super U, ? extends V> fn) {
        return biApply(other, ASYNC_POOL, fn);
    }

    @Override
    public <U, V> TCompletableFuture<V> thenCombineAsync(TCompletionStage<? extends U> other,
            BiFunction<? super T, ? super U, ? extends V> fn, Executor executor) {
        return biApply(other, executor, fn);
    }

    @Override
    public <U> TCompletableFuture<Void> thenAcceptBoth(TCompletionStage<? extends U> other,
            BiConsumer<? super T, ? super U> action) {
        return biApply(other, null, (a, b) -> {
            action.accept(a, b);
            return null;
        });
    }

    @Override
    public <U> TCompletableFuture<Void> thenAcceptBothAsync(TCompletionStage<? extends U> other,
            BiConsumer<? super T, ? super U> action) {
        return biApply(other, ASYNC_POOL, (a, b) -> {
            action.accept(a, b);
            return null;
        });
    }

    @Override
    public <U> TCompletableFuture<Void> thenAcceptBothAsync(TCompletionStage<? extends U> other,
            BiConsumer<? super T, ? super U> action, Executor executor) {
        return biApply(other, executor, (a, b) -> {
            action.accept(a, b);
            return null;
        });
    }

    @Override
    public TCompletableFuture<Void> runAfterBoth(TCompletionStage<?> other, Runnable action) {
        return biApply(other, null, (a, b) -> {
            action.run();
            return null;
        });
    }

    @Override
    public TCompletableFuture<Void> runAfterBothAsync(TCompletionStage<?> other, Runnable action) {
        return biApply(other, ASYNC_POOL, (a, b) -> {
            action.run();
            return null;
        });
    }

    @Override
    public TCompletableFuture<Void> runAfterBothAsync(TCompletionStage<?> other, Runnable action,
            Executor executor) {
        return biApply(other, executor, (a, b) -> {
            action.run();
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private <U> TCompletableFuture<U> orApply(TCompletionStage<? extends T> other, Executor e,
            Function<? super T, U> fn) {
        TCompletableFuture<U> d = new TCompletableFuture<>();
        BiConsumer<Object, Throwable> body = (v, t) -> {
            if (d.isDone()) {
                return;
            }
            if (t != null) {
                d.completeWith(null, t);
                return;
            }
            try {
                d.complete(fn.apply((T) v));
            } catch (Throwable ex) {
                d.completeWith(null, unwrap(ex));
            }
        };
        then(e, body);
        other.toCompletableFuture().then(e, body);
        return d;
    }

    @Override
    public <U> TCompletableFuture<U> applyToEither(TCompletionStage<? extends T> other, Function<? super T, U> fn) {
        return orApply(other, null, fn);
    }

    @Override
    public <U> TCompletableFuture<U> applyToEitherAsync(TCompletionStage<? extends T> other,
            Function<? super T, U> fn) {
        return orApply(other, ASYNC_POOL, fn);
    }

    @Override
    public <U> TCompletableFuture<U> applyToEitherAsync(TCompletionStage<? extends T> other,
            Function<? super T, U> fn, Executor executor) {
        return orApply(other, executor, fn);
    }

    @Override
    public TCompletableFuture<Void> acceptEither(TCompletionStage<? extends T> other, Consumer<? super T> action) {
        return orApply(other, null, x -> {
            action.accept(x);
            return null;
        });
    }

    @Override
    public TCompletableFuture<Void> acceptEitherAsync(TCompletionStage<? extends T> other,
            Consumer<? super T> action) {
        return orApply(other, ASYNC_POOL, x -> {
            action.accept(x);
            return null;
        });
    }

    @Override
    public TCompletableFuture<Void> acceptEitherAsync(TCompletionStage<? extends T> other,
            Consumer<? super T> action, Executor executor) {
        return orApply(other, executor, x -> {
            action.accept(x);
            return null;
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public TCompletableFuture<Void> runAfterEither(TCompletionStage<?> other, Runnable action) {
        return orApply((TCompletionStage<? extends T>) other, null, x -> {
            action.run();
            return null;
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public TCompletableFuture<Void> runAfterEitherAsync(TCompletionStage<?> other, Runnable action) {
        return orApply((TCompletionStage<? extends T>) other, ASYNC_POOL, x -> {
            action.run();
            return null;
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public TCompletableFuture<Void> runAfterEitherAsync(TCompletionStage<?> other, Runnable action,
            Executor executor) {
        return orApply((TCompletionStage<? extends T>) other, executor, x -> {
            action.run();
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private <U> TCompletableFuture<U> uniCompose(Executor e, Function<? super T, ? extends TCompletionStage<U>> fn) {
        TCompletableFuture<U> d = new TCompletableFuture<>();
        then(e, (v, t) -> {
            if (t != null) {
                d.completeWith(null, t);
                return;
            }
            try {
                TCompletableFuture<U> next = fn.apply((T) v).toCompletableFuture();
                next.then(null, (v2, t2) -> d.completeWith(v2, t2));
            } catch (Throwable ex) {
                d.completeWith(null, unwrap(ex));
            }
        });
        return d;
    }

    @Override
    public <U> TCompletableFuture<U> thenCompose(Function<? super T, ? extends TCompletionStage<U>> fn) {
        return uniCompose(null, fn);
    }

    @Override
    public <U> TCompletableFuture<U> thenComposeAsync(Function<? super T, ? extends TCompletionStage<U>> fn) {
        return uniCompose(ASYNC_POOL, fn);
    }

    @Override
    public <U> TCompletableFuture<U> thenComposeAsync(Function<? super T, ? extends TCompletionStage<U>> fn,
            Executor executor) {
        return uniCompose(executor, fn);
    }

    @SuppressWarnings("unchecked")
    private <U> TCompletableFuture<U> uniHandle(Executor e, BiFunction<? super T, Throwable, ? extends U> fn) {
        TCompletableFuture<U> d = new TCompletableFuture<>();
        then(e, (v, t) -> {
            try {
                d.complete(fn.apply((T) v, t == null ? null : wrapForCallback(t)));
            } catch (Throwable ex) {
                d.completeWith(null, unwrap(ex));
            }
        });
        return d;
    }

    /** Like the JDK, callbacks observe a CompletionException unless the stage was completed directly. */
    private static Throwable wrapForCallback(Throwable t) {
        return t instanceof CancellationException ? t : new TCompletionException(t);
    }

    @Override
    public <U> TCompletableFuture<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return uniHandle(null, fn);
    }

    @Override
    public <U> TCompletableFuture<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return uniHandle(ASYNC_POOL, fn);
    }

    @Override
    public <U> TCompletableFuture<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn,
            Executor executor) {
        return uniHandle(executor, fn);
    }

    @SuppressWarnings("unchecked")
    private TCompletableFuture<T> uniWhen(Executor e, BiConsumer<? super T, ? super Throwable> action) {
        TCompletableFuture<T> d = new TCompletableFuture<>();
        then(e, (v, t) -> {
            try {
                action.accept((T) v, t == null ? null : wrapForCallback(t));
            } catch (Throwable ex) {
                if (t == null) {
                    d.completeWith(null, unwrap(ex));
                    return;
                }
            }
            d.completeWith(v, t);
        });
        return d;
    }

    @Override
    public TCompletableFuture<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return uniWhen(null, action);
    }

    @Override
    public TCompletableFuture<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return uniWhen(ASYNC_POOL, action);
    }

    @Override
    public TCompletableFuture<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action,
            Executor executor) {
        return uniWhen(executor, action);
    }

    @Override
    @SuppressWarnings("unchecked")
    public TCompletableFuture<T> exceptionally(Function<Throwable, ? extends T> fn) {
        TCompletableFuture<T> d = new TCompletableFuture<>();
        then(null, (v, t) -> {
            if (t == null) {
                d.complete((T) v);
                return;
            }
            try {
                d.complete(fn.apply(wrapForCallback(t)));
            } catch (Throwable ex) {
                d.completeWith(null, unwrap(ex));
            }
        });
        return d;
    }

    @Override
    public TCompletableFuture<T> toCompletableFuture() {
        return this;
    }

    @Override
    public String toString() {
        synchronized (this) {
            String state = !done ? "[Incomplete]" : failure != null ? "[Completed exceptionally: " + failure + "]"
                    : "[Completed normally]";
            return super.toString() + state;
        }
    }
}
