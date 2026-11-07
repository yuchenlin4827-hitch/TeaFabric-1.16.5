package org.lwjgl.glfw;

public abstract class GLFWDropCallback implements GLFWDropCallbackI, AutoCloseable {
    public static GLFWDropCallback create(GLFWDropCallbackI instance) {
        return instance instanceof GLFWDropCallback ? (GLFWDropCallback) instance : new GLFWDropCallback() {
            @Override
            public void invoke(long window, int count, long names) {
                instance.invoke(window, count, names);
            }
        };
    }

    public void free() {
    }

    @Override
    public void close() {
    }

    public static String getName(long names, int index) {
        return teafabric.web.mem.NativeMemory.utf8(teafabric.web.mem.NativeMemory.getLong(names + 8L * index));
    }
}
