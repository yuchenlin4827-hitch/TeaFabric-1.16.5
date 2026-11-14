package org.teavm.classlib.java.lang.management;

/** Direct buffers live in the Wasm/JS heap here, which is not measured separately: all values are 0. */
public interface TBufferPoolMXBean extends TPlatformManagedObject {
    default String getName() {
        return "direct";
    }

    default long getCount() {
        return 0;
    }

    default long getTotalCapacity() {
        return 0;
    }

    default long getMemoryUsed() {
        return 0;
    }
}
