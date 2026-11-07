package io.netty.util.concurrent;

import io.netty.util.internal.InternalThreadLocalMap;

/**
 * Netty 4.1 FastThreadLocalThread without the ThreadGroup super constructors, which TeaVM's
 * Thread does not provide (thread groups do not exist for green threads).
 */
public class FastThreadLocalThread extends Thread {
    private final boolean cleanupFastThreadLocals;
    private InternalThreadLocalMap threadLocalMap;

    public FastThreadLocalThread() {
        cleanupFastThreadLocals = false;
    }

    public FastThreadLocalThread(Runnable target) {
        super(FastThreadLocalRunnable.wrap(target));
        cleanupFastThreadLocals = true;
    }

    public FastThreadLocalThread(ThreadGroup group, Runnable target) {
        this(target);
    }

    public FastThreadLocalThread(String name) {
        super(name);
        cleanupFastThreadLocals = false;
    }

    public FastThreadLocalThread(ThreadGroup group, String name) {
        this(name);
    }

    public FastThreadLocalThread(Runnable target, String name) {
        super(FastThreadLocalRunnable.wrap(target), name);
        cleanupFastThreadLocals = true;
    }

    public FastThreadLocalThread(ThreadGroup group, Runnable target, String name) {
        this(target, name);
    }

    public FastThreadLocalThread(ThreadGroup group, Runnable target, String name, long stackSize) {
        this(target, name);
    }

    public final InternalThreadLocalMap threadLocalMap() {
        return threadLocalMap;
    }

    public final void setThreadLocalMap(InternalThreadLocalMap threadLocalMap) {
        this.threadLocalMap = threadLocalMap;
    }

    public boolean willCleanupFastThreadLocals() {
        return cleanupFastThreadLocals;
    }

    public static boolean willCleanupFastThreadLocals(Thread thread) {
        return thread instanceof FastThreadLocalThread
                && ((FastThreadLocalThread) thread).willCleanupFastThreadLocals();
    }
}
