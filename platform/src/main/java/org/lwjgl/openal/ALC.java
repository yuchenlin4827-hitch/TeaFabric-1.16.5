package org.lwjgl.openal;

public final class ALC {
    private ALC() {
    }

    public static ALCCapabilities createCapabilities(long device) {
        return new ALCCapabilities();
    }

    public static void create() {
    }

    public static void destroy() {
    }
}
