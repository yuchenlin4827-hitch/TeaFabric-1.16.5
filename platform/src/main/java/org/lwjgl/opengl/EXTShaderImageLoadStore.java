package org.lwjgl.opengl;

/** EXT_shader_image_load_store: not available (GLCapabilities.GL_EXT_shader_image_load_store is false). */
public final class EXTShaderImageLoadStore {
    private EXTShaderImageLoadStore() {
    }

    public static void glBindImageTextureEXT(int index, int texture, int level, boolean layered, int layer, int access,
            int format) {
        throw new UnsupportedOperationException("glBindImageTextureEXT: image load/store is not available on WebGL 2");
    }
}
