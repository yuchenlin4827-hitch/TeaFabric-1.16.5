package org.lwjgl.glfw;

public abstract class GLFWKeyCallback implements GLFWKeyCallbackI, AutoCloseable {
    public static GLFWKeyCallback create(GLFWKeyCallbackI instance) {
        return instance instanceof GLFWKeyCallback ? (GLFWKeyCallback) instance : new GLFWKeyCallback() {
            @Override
            public void invoke(long window, int key, int scancode, int action, int mods) {
                instance.invoke(window, key, scancode, action, mods);
            }
        };
    }

    public void free() {
    }

    @Override
    public void close() {
    }
}
