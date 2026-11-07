package org.teavm.classlib.java.util.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public interface TScheduledExecutorService extends TExecutorService {
    TScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit);

    <V> TScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit);

    TScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);

    TScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);
}
