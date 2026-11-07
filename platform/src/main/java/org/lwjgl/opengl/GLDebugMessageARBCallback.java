package org.lwjgl.opengl;

public abstract class GLDebugMessageARBCallback implements GLDebugMessageARBCallbackI {
    public static GLDebugMessageARBCallback create(GLDebugMessageARBCallbackI instance) {
        return new GLDebugMessageARBCallback() {
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
