package org.lwjgl.glfw;

public abstract class GLFWCursorEnterCallback implements GLFWCursorEnterCallbackI, AutoCloseable {
    public static GLFWCursorEnterCallback create(GLFWCursorEnterCallbackI instance) {
        return instance instanceof GLFWCursorEnterCallback ? (GLFWCursorEnterCallback) instance : new GLFWCursorEnterCallback() {
            @Override
            public void invoke(long window, boolean entered) {
                instance.invoke(window, entered);
            }
        };
    }

    public void free() {
    }

    @Override
    public void close() {
    }
}
