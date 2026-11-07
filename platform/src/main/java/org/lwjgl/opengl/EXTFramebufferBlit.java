package org.lwjgl.opengl;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import teafabric.web.gl.GLEmu;

/** LWJGL EXTFramebufferBlit front-end over the WebGL 2 emulation in {@link GLEmu}. */
public class EXTFramebufferBlit {
    protected EXTFramebufferBlit() {
    }

    public static void glBlitFramebufferEXT(int sx0, int sy0, int sx1, int sy1, int dx0, int dy0, int dx1,
            int dy1, int mask, int filter) {
        GLEmu.blitFramebuffer(sx0, sy0, sx1, sy1, dx0, dy0, dx1, dy1, mask, filter);
    }
}
