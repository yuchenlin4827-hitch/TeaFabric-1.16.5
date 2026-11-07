package org.lwjgl.glfw;

public abstract class GLFWErrorCallback implements GLFWErrorCallbackI, AutoCloseable {
    public static GLFWErrorCallback create(GLFWErrorCallbackI instance) {
        return new GLFWErrorCallback() {
            @Override
            public void invoke(int error, long description) {
                instance.invoke(error, description);
            }
        };
    }

    public static GLFWErrorCallback createPrint() {
        return create((e, d) -> System.err.println("[GLFW] " + e + ": " + getDescription(d)));
    }

    public static GLFWErrorCallback createPrint(java.io.PrintStream stream) {
        return create((e, d) -> stream.println("[GLFW] " + e + ": " + getDescription(d)));
    }

    public static GLFWErrorCallback createThrow() {
        return create((e, d) -> {
            throw new IllegalStateException("GLFW error " + e + ": " + getDescription(d));
        });
    }

    public static String getDescription(long description) {
        return description == 0 ? "" : teafabric.web.mem.NativeMemory.utf8(description);
    }

    public GLFWErrorCallback set() {
        GLFW.glfwSetErrorCallback(this);
        return this;
    }

    public void free() {
    }

    @Override
    public void close() {
    }
}
