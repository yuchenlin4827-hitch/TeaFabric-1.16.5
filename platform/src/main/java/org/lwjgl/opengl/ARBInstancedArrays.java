package org.lwjgl.opengl;

/** GL_ARB_instanced_arrays, as GL33C. */
public final class ARBInstancedArrays {
    private ARBInstancedArrays() {
    }

    public static void glVertexAttribDivisorARB(int index, int divisor) {
        GL33C.glVertexAttribDivisor(index, divisor);
    }
}
