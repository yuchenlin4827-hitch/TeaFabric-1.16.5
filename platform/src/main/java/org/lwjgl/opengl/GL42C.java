package org.lwjgl.opengl;

/** GL 4.2 image load/store: not available on WebGL 2 (GLCapabilities.OpenGL42 is false, so callers skip it). */
public final class GL42C {
    private GL42C() {
    }

    public static void glBindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access,
            int format) {
        throw new UnsupportedOperationException("glBindImageTexture: image load/store is not available on WebGL 2");
    }
}
