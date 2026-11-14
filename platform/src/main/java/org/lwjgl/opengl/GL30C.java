package org.lwjgl.opengl;

import teafabric.web.gl.GLEmu;

/** Core-profile GL 3.0: vertex array objects (emulated by GLEmu). */
public final class GL30C {
    private GL30C() {
    }

    public static int glGenVertexArrays() {
        return GLEmu.genVertexArrays();
    }

    public static void glBindVertexArray(int array) {
        GLEmu.bindVertexArray(array);
    }

    public static void glDeleteVertexArrays(int array) {
        GLEmu.deleteVertexArrays(array);
    }
}
