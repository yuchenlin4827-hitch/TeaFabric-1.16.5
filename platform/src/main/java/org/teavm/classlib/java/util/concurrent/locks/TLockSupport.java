package org.teavm.classlib.java.util.concurrent.locks;

import java.util.HashSet;
import java.util.Set;

/** park/unpark over a shared monitor; permits are tracked per thread. */
public final class TLockSupport {
    private static final Object MONITOR = new Object();
    private static final Set<Thread> PERMITS = new HashSet<>();

    private TLockSupport() {
    }

    public static void unpark(Thread thread) {
        if (thread == null) {
            return;
        }
        synchronized (MONITOR) {
            PERMITS.add(thread);
            MONITOR.notifyAll();
        }
    }

    public static void park() {
        parkMillis(0);
    }

    public static void park(Object blocker) {
        parkMillis(0);
    }

    public static void parkNanos(long nanos) {
        if (nanos > 0) {
            parkMillis(Math.max(1, nanos / 1000000));
        }
    }

    public static void parkNanos(Object blocker, long nanos) {
        parkNanos(nanos);
    }

    public static void parkUntil(long deadline) {
        long left = deadline - System.currentTimeMillis();
        if (left > 0) {
            parkMillis(left);
        }
    }

    public static void parkUntil(Object blocker, long deadline) {
        parkUntil(deadline);
    }

    private static void parkMillis(long millis) {
        Thread me = Thread.currentThread();
        synchronized (MONITOR) {
            if (!PERMITS.remove(me)) {
                try {
                    if (millis > 0) {
                        MONITOR.wait(millis);
                    } else {
                        MONITOR.wait();
                    }
                } catch (InterruptedException e) {
                    me.interrupt();
                }
                PERMITS.remove(me);
            }
        }
    }

    public static Object getBlocker(Thread t) {
        return null;
    }
}
