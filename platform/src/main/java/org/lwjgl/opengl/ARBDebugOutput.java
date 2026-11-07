package org.lwjgl.opengl;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import teafabric.web.gl.GLEmu;

/** LWJGL ARBDebugOutput front-end over the WebGL 2 emulation in {@link GLEmu}. */
public class ARBDebugOutput {
    protected ARBDebugOutput() {
    }

    public static void glDebugMessageCallbackARB(GLDebugMessageARBCallbackI callback, long userParam) {
    }

    public static void glDebugMessageControlARB(int source, int type, int severity, int[] ids,
            boolean enabled) {
    }

    public static void glDebugMessageControlARB(int source, int type, int severity, IntBuffer ids,
            boolean enabled) {
    }
}
