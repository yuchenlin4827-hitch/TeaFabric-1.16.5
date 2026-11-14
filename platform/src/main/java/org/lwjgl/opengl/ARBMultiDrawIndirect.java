package org.lwjgl.opengl;

/** GL_ARB_multi_draw_indirect: absent (see GL43C). */
public final class ARBMultiDrawIndirect {
    private ARBMultiDrawIndirect() {
    }

    public static void glMultiDrawArraysIndirect(int mode, long indirect, int drawcount, int stride) {
        GL43C.glMultiDrawArraysIndirect(mode, indirect, drawcount, stride);
    }
}
