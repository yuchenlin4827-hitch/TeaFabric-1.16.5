package org.lwjgl.opengl;

/** GL_ARB_copy_buffer, as GL31C. */
public final class ARBCopyBuffer {
    private ARBCopyBuffer() {
    }

    public static void glCopyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset,
            long size) {
        GL31C.glCopyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size);
    }
}
