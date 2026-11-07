package org.lwjgl.glfw;

public abstract class GLFWWindowPosCallback implements GLFWWindowPosCallbackI, AutoCloseable {
    public static GLFWWindowPosCallback create(GLFWWindowPosCallbackI instance) {
        return instance instanceof GLFWWindowPosCallback ? (GLFWWindowPosCallback) instance : new GLFWWindowPosCallback() {
            @Override
            public void invoke(long window, int xpos, int ypos) {
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
