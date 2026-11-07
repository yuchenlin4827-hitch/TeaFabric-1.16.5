package org.lwjgl.glfw;

public abstract class GLFWWindowCloseCallback implements GLFWWindowCloseCallbackI, AutoCloseable {
    public static GLFWWindowCloseCallback create(GLFWWindowCloseCallbackI instance) {
        return instance instanceof GLFWWindowCloseCallback ? (GLFWWindowCloseCallback) instance : new GLFWWindowCloseCallback() {
            @Override
            public void invoke(long window) {
                instance.invoke(window);
            }
        };
    }

    public void free() {
    }

    @Override
    public void close() {
    }
}
