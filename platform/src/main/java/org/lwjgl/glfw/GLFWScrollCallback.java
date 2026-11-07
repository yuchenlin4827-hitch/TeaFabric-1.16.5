package org.lwjgl.glfw;

public abstract class GLFWScrollCallback implements GLFWScrollCallbackI, AutoCloseable {
    public static GLFWScrollCallback create(GLFWScrollCallbackI instance) {
        return instance instanceof GLFWScrollCallback ? (GLFWScrollCallback) instance : new GLFWScrollCallback() {
            @Override
            public void invoke(long window, double xoffset, double yoffset) {
                instance.invoke(window, xoffset, yoffset);
            }
        };
    }

    public void free() {
    }

    @Override
    public void close() {
    }
}
