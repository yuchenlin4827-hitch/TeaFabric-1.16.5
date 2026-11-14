package org.lwjgl.opengl;

import teafabric.web.gl.GLEmu;

/** Core-profile GL 3.1: buffer copies (WebGL 2 copyBufferSubData). */
public final class GL31C {
    private GL31C() {
    }

    public static void glCopyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset,
            long size) {
        GLEmu.copyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size);
    }
}
