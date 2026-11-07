package oshi;

import oshi.hardware.HardwareAbstractionLayer;

/** Replacement for OSHI 1.1 (native system probes) reporting the browser environment. */
public class SystemInfo {
    public HardwareAbstractionLayer getHardware() {
        return new HardwareAbstractionLayer();
    }
}
