package org.lwjgl.opengl;

/** Core-profile GL 4.3. Indirect multi-draw has no WebGL 2 equivalent; GLCapabilities reports it absent. */
public final class GL43C {
    private GL43C() {
    }

    public static void glMultiDrawArraysIndirect(int mode, long indirect, int drawcount, int stride) {
        throw new UnsupportedOperationException("glMultiDrawArraysIndirect is not available in WebGL 2");
    }
}
