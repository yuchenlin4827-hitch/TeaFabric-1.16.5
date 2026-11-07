package org.teavm.classlib.java.lang.management;

/** Only what Minecraft's crash reports and debug screen query. */
public final class TManagementFactory {
    private static final TRuntimeMXBean RUNTIME = new TRuntimeMXBean() { };

    private TManagementFactory() {
    }

    public static TRuntimeMXBean getRuntimeMXBean() {
        return RUNTIME;
    }
}
