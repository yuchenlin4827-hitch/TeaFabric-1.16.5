package org.lwjgl.glfw;

public abstract class GLFWWindowSizeCallback implements GLFWWindowSizeCallbackI, AutoCloseable {
    public static GLFWWindowSizeCallback create(GLFWWindowSizeCallbackI instance) {
        return instance instanceof GLFWWindowSizeCallback ? (GLFWWindowSizeCallback) instance : new GLFWWindowSizeCallback() {
            @Override
            public void invoke(long window, int width, int height) {
                instance.invoke(window, width, height);
            }
        };
    }

    public void free() {
    }

    @Override
    public void close() {
    }
}
