package org.teavm.classlib.java.lang.management;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Only what Minecraft's crash reports and debug screen, Sodium and BetterF3 query. */
public final class TManagementFactory {
    private static final TRuntimeMXBean RUNTIME = new TRuntimeMXBean() { };
    private static final TBufferPoolMXBean DIRECT = new TBufferPoolMXBean() { };
    private static final TGarbageCollectorMXBean GC = new TGarbageCollectorMXBean() { };

    private TManagementFactory() {
    }

    public static TRuntimeMXBean getRuntimeMXBean() {
        return RUNTIME;
    }

    /** One collector: the browser's, of which nothing is known (counts are 0). */
    public static List<TGarbageCollectorMXBean> getGarbageCollectorMXBeans() {
        return Collections.singletonList(GC);
    }

    /** The "direct" buffer pool for {@link TBufferPoolMXBean}; nothing else is available. */
    @SuppressWarnings("unchecked")
    public static <T extends TPlatformManagedObject> List<T> getPlatformMXBeans(Class<T> type) {
        List<T> list = new ArrayList<>();
        if (type == TBufferPoolMXBean.class) {
            list.add((T) DIRECT);
        } else if (type == TGarbageCollectorMXBean.class) {
            list.add((T) GC);
        }
        return list;
    }
}
