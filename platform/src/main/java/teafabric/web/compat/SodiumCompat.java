package teafabric.web.compat;

import net.minecraft.class_5365;
import org.teavm.jso.JSBody;

/**
 * Sodium's additions that need a browser version (call-site redirects in META-INF/teafabric/redirects.txt;
 * unused when Sodium is not installed).
 */
public final class SodiumCompat {
    private SodiumCompat() {
    }

    /**
     * Sodium's F3 line for memory outside the Java heap (java.lang.management on a JVM). Here it shows the
     * browser's JavaScript heap, where the game's objects live, when the browser reports it (Chromium).
     */
    public static String getNativeMemoryString() {
        double bytes = jsHeapUsed();
        return bytes > 0 ? "JS heap: " + (long) (bytes / (1024 * 1024)) + "MB" : "";
    }

    /**
     * The values Sodium's cycling options step through (see ModCompat in the baker): Fabulous! is left out,
     * as on vanilla's video settings screen (GraphicsCompat).
     */
    public static Enum<?>[] allowedValues(Enum<?>[] values) {
        int n = 0;
        for (Enum<?> v : values) {
            if (v != class_5365.field_25429) {
                n++;
            }
        }
        if (n == values.length) {
            return values;
        }
        Enum<?>[] result = (Enum<?>[]) java.lang.reflect.Array.newInstance(values.getClass().getComponentType(), n);
        int i = 0;
        for (Enum<?> v : values) {
            if (v != class_5365.field_25429) {
                result[i++] = v;
            }
        }
        return result;
    }

    /** How long Sodium's chunk worker may run before the page gets a turn to draw a frame. */
    private static final long WORKER_BURST_MS = 6;
    private static long workerBurstStart;

    /**
     * Called by Sodium's chunk worker after each job (see ModCompat in the baker). The worker is a green thread
     * on the page's only thread: Thread.yield only switches after 30 calls and a 100 ms slice, and one chunk
     * takes milliseconds to build, so frames waited up to a second. Here the worker sleeps (letting the browser
     * run the next animation frame) whenever it has worked for WORKER_BURST_MS.
     */
    public static void chunkWorkerYield() {
        long now = System.currentTimeMillis();
        if (now - workerBurstStart < WORKER_BURST_MS) {
            return;
        }
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        workerBurstStart = System.currentTimeMillis();
    }

    @JSBody(script = "return (self.performance && performance.memory && performance.memory.usedJSHeapSize) || 0;")
    private static native double jsHeapUsed();
}
