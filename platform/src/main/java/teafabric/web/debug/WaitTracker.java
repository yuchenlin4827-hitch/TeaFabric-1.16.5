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
        final String jsStack;
        final long start = System.currentTimeMillis();
        boolean reported;

        Entry(Throwable where, String jsStack) {
            this.where = where;
            this.jsStack = jsStack;
        }
    }

    /**
     * {@code monitor.wait()}, tracked; the caller must hold the monitor. Also tells Minecraft's worker pool
     * when one of its threads blocks inside a task, so queued tasks keep running.
     */
    public static void waitOn(Object monitor, String what) throws InterruptedException {
        teafabric.web.compat.MinecraftCompat.YieldingWorker.beforeBlock();
        try {
            if (!ENABLED) {
                monitor.wait();
                return;
            }
            Entry entry = new Entry(new Throwable("thread '" + Thread.currentThread().getName() + "' blocked in "
                    + what), jsStack());
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
        } finally {
            teafabric.web.compat.MinecraftCompat.YieldingWorker.afterBlock();
        }
    }

    /** The JavaScript stack, which names the Wasm functions (Java stack traces are empty in this build). */
    @org.teavm.jso.JSBody(script = "var limit = Error.stackTraceLimit; Error.stackTraceLimit = 200;"
            + " var stack = String(new Error().stack); Error.stackTraceLimit = limit; return stack;")
    private static native String jsStack();

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
                        for (String line : entry.jsStack.split("\n")) {
                            line = line.trim();
                            // Skip the stack's own frames, call adapters and the class library's internals.
                            if (line.startsWith("at ") && !line.contains("@caller") && !line.contains("WaitTracker")
                                    && !line.contains("jsStack")) {
                                System.out.println("[WaitTracker]     " + line.replaceAll(" \\(wasm://.*", ""));
                            }
                        }
                    }
                }
            }
        }, "WaitTracker");
        watchdog.setDaemon(true);
        watchdog.start();
    }
}
