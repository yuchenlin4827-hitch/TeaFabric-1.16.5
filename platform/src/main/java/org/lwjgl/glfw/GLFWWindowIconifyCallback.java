package org.lwjgl.glfw;

public abstract class GLFWWindowIconifyCallback implements GLFWWindowIconifyCallbackI, AutoCloseable {
    public static GLFWWindowIconifyCallback create(GLFWWindowIconifyCallbackI instance) {
        return instance instanceof GLFWWindowIconifyCallback ? (GLFWWindowIconifyCallback) instance : new GLFWWindowIconifyCallback() {
            @Override
            public void invoke(long window, boolean iconified) {
                instance.invoke(window, iconified);
            }
        };
    }

    public void free() {
    }

    @Override
    public void close() {
    }
}
