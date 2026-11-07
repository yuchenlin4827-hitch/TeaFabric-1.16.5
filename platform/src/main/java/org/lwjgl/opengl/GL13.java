package org.lwjgl.opengl;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import teafabric.web.gl.GLEmu;

/** LWJGL GL13 front-end over the WebGL 2 emulation in {@link GLEmu}. */
public class GL13 extends GL11 {
    protected GL13() {
    }

    public static void glActiveTexture(int texture) {
        GLEmu.activeTexture(texture);
    }

    public static void glClientActiveTexture(int texture) {
        GLEmu.clientActiveTexture(texture);
    }

    public static void glMultiTexCoord2f(int target, float s, float t) {
        GLEmu.multiTexCoord2f(target, s, t);
    }
}
