package org.lwjgl.opengl;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import teafabric.web.gl.GLEmu;

/** LWJGL ARBFramebufferObject front-end over the WebGL 2 emulation in {@link GLEmu}. */
public class ARBFramebufferObject {
    protected ARBFramebufferObject() {
    }

    public static void glBindFramebuffer(int target, int framebuffer) {
        GLEmu.bindFramebuffer(target, framebuffer);
    }

    public static int glCheckFramebufferStatus(int target) {
        return GLEmu.checkFramebufferStatus(target);
    }

    public static void glDeleteFramebuffers(int framebuffer) {
        GLEmu.deleteFramebuffers(framebuffer);
    }

    public static void glFramebufferTexture2D(int target, int attachment, int textarget, int texture,
            int level) {
        GLEmu.framebufferTexture2D(target, attachment, textarget, texture, level);
    }

    public static int glGenFramebuffers() {
        return GLEmu.genFramebuffers();
    }

    public static int glGetFramebufferAttachmentParameteri(int target, int attachment, int pname) {
        return GLEmu.getFramebufferAttachmentParameteri(target, attachment, pname);
    }

    public static void glBlitFramebuffer(int sx0, int sy0, int sx1, int sy1, int dx0, int dy0, int dx1,
            int dy1, int mask, int filter) {
        GLEmu.blitFramebuffer(sx0, sy0, sx1, sy1, dx0, dy0, dx1, dy1, mask, filter);
    }
}
