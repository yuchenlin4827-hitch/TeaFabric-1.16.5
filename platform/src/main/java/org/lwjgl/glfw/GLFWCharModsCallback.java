package org.lwjgl.glfw;

public abstract class GLFWCharModsCallback implements GLFWCharModsCallbackI, AutoCloseable {
    public static GLFWCharModsCallback create(GLFWCharModsCallbackI instance) {
        return instance instanceof GLFWCharModsCallback ? (GLFWCharModsCallback) instance : new GLFWCharModsCallback() {
            @Override
            public void invoke(long window, int codepoint, int mods) {
                instance.invoke(window, codepoint, mods);
            }
        };
    }

    public void free() {
    }

    @Override
    public void close() {
    }
}
