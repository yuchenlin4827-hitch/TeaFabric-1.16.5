package org.teavm.classlib.java.lang.management;

import java.util.Collections;
import java.util.List;

public interface TRuntimeMXBean {
    default List<String> getInputArguments() {
        return Collections.emptyList();
    }

    default String getName() {
        return "teavm@browser";
    }

    default String getVmName() {
        return "TeaVM (WebAssembly GC)";
    }

    default String getVmVendor() {
        return "TeaVM";
    }

    default String getVmVersion() {
        return "0.16.0";
    }

    default long getUptime() {
        return System.currentTimeMillis() - START;
    }

    default long getStartTime() {
        return START;
    }

    long START = System.currentTimeMillis();
}
