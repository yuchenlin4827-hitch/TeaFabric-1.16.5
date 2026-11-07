package org.lwjgl.glfw;

public abstract class GLFWMonitorCallback implements GLFWMonitorCallbackI, AutoCloseable {
    public static GLFWMonitorCallback create(GLFWMonitorCallbackI instance) {
        return new GLFWMonitorCallback() {
            @Override
            public void invoke(long monitor, int event) {
                instance.invoke(monitor, event);
            }
        };
    }

    public void free() {
    }

    @Override
    public void close() {
    }
}
