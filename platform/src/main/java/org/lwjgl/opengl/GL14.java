package org.lwjgl.opengl;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import teafabric.web.gl.GLEmu;

/** LWJGL GL14 front-end over the WebGL 2 emulation in {@link GLEmu}. */
public class GL14 extends GL13 {
    protected GL14() {
    }

    public static void glBlendColor(float r, float g, float b, float a) {
        WebGLAccess.gl().blendColor(r, g, b, a);
    }

    public static void glBlendEquation(int mode) {
        WebGLAccess.gl().blendEquation(mode);
    }

    public static void glBlendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        GLEmu.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }
}
