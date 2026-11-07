package org.lwjgl.glfw;

public abstract class GLFWMouseButtonCallback implements GLFWMouseButtonCallbackI, AutoCloseable {
    public static GLFWMouseButtonCallback create(GLFWMouseButtonCallbackI instance) {
        return instance instanceof GLFWMouseButtonCallback ? (GLFWMouseButtonCallback) instance : new GLFWMouseButtonCallback() {
            @Override
            public void invoke(long window, int button, int action, int mods) {
                instance.invoke(window, button, action, mods);
            }
        };
    }

    public void free() {
    }

    @Override
    public void close() {
    }
}
