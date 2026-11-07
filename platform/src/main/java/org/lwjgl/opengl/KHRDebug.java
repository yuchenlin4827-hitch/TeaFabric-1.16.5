package org.lwjgl.opengl;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import teafabric.web.gl.GLEmu;

/** LWJGL KHRDebug front-end over the WebGL 2 emulation in {@link GLEmu}. */
public class KHRDebug {
    protected KHRDebug() {
    }

    public static void glDebugMessageCallback(GLDebugMessageCallbackI callback, long userParam) {
    }

    public static void glDebugMessageControl(int source, int type, int severity, int[] ids,
            boolean enabled) {
    }

    public static void glDebugMessageControl(int source, int type, int severity, IntBuffer ids,
            boolean enabled) {
    }
}
