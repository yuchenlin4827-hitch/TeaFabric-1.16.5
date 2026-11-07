package org.lwjgl.glfw;

public abstract class GLFWWindowFocusCallback implements GLFWWindowFocusCallbackI, AutoCloseable {
    public static GLFWWindowFocusCallback create(GLFWWindowFocusCallbackI instance) {
        return instance instanceof GLFWWindowFocusCallback ? (GLFWWindowFocusCallback) instance : new GLFWWindowFocusCallback() {
            @Override
            public void invoke(long window, boolean focused) {
                instance.invoke(window, focused);
            }
        };
    }

    public void free() {
    }

    @Override
    public void close() {
    }
}
