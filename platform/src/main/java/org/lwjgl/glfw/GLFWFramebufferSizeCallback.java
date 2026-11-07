package org.lwjgl.glfw;

public abstract class GLFWFramebufferSizeCallback implements GLFWFramebufferSizeCallbackI, AutoCloseable {
    public static GLFWFramebufferSizeCallback create(GLFWFramebufferSizeCallbackI instance) {
        return instance instanceof GLFWFramebufferSizeCallback ? (GLFWFramebufferSizeCallback) instance : new GLFWFramebufferSizeCallback() {
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
