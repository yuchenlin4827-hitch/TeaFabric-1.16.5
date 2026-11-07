package teafabric.web.debug;

import java.util.ArrayList;
import java.util.List;
import teafabric.web.Boot;

/**
 * Finds green-thread deadlocks: with {@code ?waits} in the page URL, every untimed wait in the
 * concurrency shims records where it started, and a watchdog thread prints the Java stack of any wait
 * that has been blocked for more than five seconds.
 */
public final class WaitTracker {
    private static final boolean ENABLED = Boot.config("waits") != null;
    private static final List<Entry> ACTIVE = new ArrayList<>();
    private static boolean watchdogStarted;

    private WaitTracker() {
    }

    private static final class Entry {
        final Throwable where;
        final long start = System.currentTimeMillis();
        boolean reported;

        Entry(Throwable where) {
            this.where = where;
        }
    }

    /** {@code monitor.wait()}, tracked; the caller must hold the monitor. */
    public static void waitOn(Object monitor, String what) throws InterruptedException {
        if (!ENABLED) {
            monitor.wait();
            return;
        }
        Entry entry = new Entry(new Throwable("thread '" + Thread.currentThread().getName() + "' blocked in "
                + what));
        ACTIVE.add(entry);
        startWatchdog();
        try {
            monitor.wait();
        } finally {
            ACTIVE.remove(entry);
            if (entry.reported) {
                System.out.println("[WaitTracker] resumed after " + (System.currentTimeMillis() - entry.start)
                        + " ms: " + entry.where.getMessage());
            }
        }
    }

    private static void startWatchdog() {
        if (watchdogStarted) {
            return;
        }
        watchdogStarted = true;
        Thread watchdog = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
                long now = System.currentTimeMillis();
                for (Entry entry : new ArrayList<>(ACTIVE)) {
                    if (!entry.reported && now - entry.start > 5000) {
                        entry.reported = true;
                        System.out.println("[WaitTracker] " + entry.where.getMessage() + " for "
                                + (now - entry.start) + " ms, waiting since:");
                        for (StackTraceElement element : entry.where.getStackTrace()) {
                            System.out.println("[WaitTracker]     at " + element);
                        }
                    }
                }
            }
        }, "WaitTracker");
        watchdog.setDaemon(true);
        watchdog.start();
    }
}
