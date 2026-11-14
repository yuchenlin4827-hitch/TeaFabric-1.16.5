package org.lwjgl.opengl;

import teafabric.web.gl.GLEmu;

/** Core-profile GL 3.3: instanced attribute divisors (WebGL 2 has them). */
public final class GL33C {
    private GL33C() {
    }

    public static void glVertexAttribDivisor(int index, int divisor) {
        GLEmu.vertexAttribDivisor(index, divisor);
    }
}
