package org.lwjgl.openal;

public final class AL {
    private AL() {
    }

    public static ALCapabilities createCapabilities(ALCCapabilities alcCaps) {
        return new ALCapabilities();
    }

    public static ALCapabilities getCapabilities() {
        return new ALCapabilities();
    }
}
