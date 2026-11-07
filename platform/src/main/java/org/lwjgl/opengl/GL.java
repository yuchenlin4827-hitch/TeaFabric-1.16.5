package org.lwjgl.opengl;

/** Capability bootstrap: the WebGL 2 context is created by GLFW (canvas) and installed here. */
public final class GL {
    private static GLCapabilities capabilities;

    private GL() {
    }

    public static GLCapabilities createCapabilities() {
        capabilities = new GLCapabilities();
        return capabilities;
    }

    public static GLCapabilities createCapabilities(boolean forwardCompatible) {
        return createCapabilities();
    }

    public static GLCapabilities getCapabilities() {
        if (capabilities == null) {
            capabilities = new GLCapabilities();
        }
        return capabilities;
    }

    public static void setCapabilities(GLCapabilities caps) {
        capabilities = caps;
    }
}
