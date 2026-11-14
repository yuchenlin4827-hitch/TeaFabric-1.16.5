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

    @JSBody(script = "return (self.performance && performance.memory && performance.memory.usedJSHeapSize) || 0;")
    private static native double jsHeapUsed();
}
