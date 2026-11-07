package org.lwjgl.glfw;

public abstract class GLFWCursorPosCallback implements GLFWCursorPosCallbackI, AutoCloseable {
    public static GLFWCursorPosCallback create(GLFWCursorPosCallbackI instance) {
        return instance instanceof GLFWCursorPosCallback ? (GLFWCursorPosCallback) instance : new GLFWCursorPosCallback() {
            @Override
            public void invoke(long window, double xpos, double ypos) {
                instance.invoke(window, xpos, ypos);
            }
        };
    }

    public void free() {
    }

    @Override
    public void close() {
    }
}
