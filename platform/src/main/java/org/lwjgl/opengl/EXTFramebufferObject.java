package org.lwjgl.opengl;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import teafabric.web.gl.GLEmu;

/** LWJGL EXTFramebufferObject front-end over the WebGL 2 emulation in {@link GLEmu}. */
public class EXTFramebufferObject {
    protected EXTFramebufferObject() {
    }

    public static void glBindFramebufferEXT(int target, int framebuffer) {
        GLEmu.bindFramebuffer(target, framebuffer);
    }

    public static int glCheckFramebufferStatusEXT(int target) {
        return GLEmu.checkFramebufferStatus(target);
    }

    public static void glDeleteFramebuffersEXT(int framebuffer) {
        GLEmu.deleteFramebuffers(framebuffer);
    }

    public static void glFramebufferTexture2DEXT(int target, int attachment, int textarget, int texture,
            int level) {
        GLEmu.framebufferTexture2D(target, attachment, textarget, texture, level);
    }

    public static int glGenFramebuffersEXT() {
        return GLEmu.genFramebuffers();
    }

    public static int glGetFramebufferAttachmentParameteriEXT(int target, int attachment, int pname) {
        return GLEmu.getFramebufferAttachmentParameteri(target, attachment, pname);
    }
}
