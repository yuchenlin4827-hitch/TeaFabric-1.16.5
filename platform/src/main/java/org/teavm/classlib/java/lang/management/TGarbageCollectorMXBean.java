package org.teavm.classlib.java.lang.management;

/** The browser's garbage collector, which reports nothing to the page: counts and times are 0. */
public interface TGarbageCollectorMXBean extends TPlatformManagedObject {
    default String getName() {
        return "browser";
    }

    default boolean isValid() {
        return true;
    }

    default String[] getMemoryPoolNames() {
        return new String[0];
    }

    default long getCollectionCount() {
        return 0;
    }

    default long getCollectionTime() {
        return 0;
    }
}
