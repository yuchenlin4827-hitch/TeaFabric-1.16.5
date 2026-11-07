package org.lwjgl.glfw;

public abstract class GLFWCharCallback implements GLFWCharCallbackI, AutoCloseable {
    public static GLFWCharCallback create(GLFWCharCallbackI instance) {
        return instance instanceof GLFWCharCallback ? (GLFWCharCallback) instance : new GLFWCharCallback() {
            @Override
            public void invoke(long window, int codepoint) {
                instance.invoke(window, codepoint);
            }
        };
    }

    public void free() {
    }

    @Override
    public void close() {
    }
}
