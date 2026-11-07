package org.lwjgl.opengl;

public abstract class GLDebugMessageCallback implements GLDebugMessageCallbackI {
    public static GLDebugMessageCallback create(GLDebugMessageCallbackI instance) {
        return new GLDebugMessageCallback() {
            @Override
            public void invoke(int source, int type, int id, int severity, int length, long message, long user) {
                instance.invoke(source, type, id, severity, length, message, user);
            }
        };
    }

    public static String getMessage(int length, long message) {
        return teafabric.web.mem.NativeMemory.utf8(message);
    }

    public void free() {
    }
}
