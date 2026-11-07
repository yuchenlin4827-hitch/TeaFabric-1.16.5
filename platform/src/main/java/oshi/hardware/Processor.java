package oshi.hardware;

import org.teavm.jso.JSBody;

public class Processor {
    @JSBody(script = "return (typeof navigator !== 'undefined' && navigator.hardwareConcurrency) || 1;")
    private static native int cores();

    public String getName() {
        return "Browser (" + cores() + " logical cores)";
    }

    @Override
    public String toString() {
        return getName();
    }
}
