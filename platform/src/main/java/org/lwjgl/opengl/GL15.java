package org.lwjgl.opengl;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import teafabric.web.gl.GLEmu;

/** LWJGL GL15 front-end over the WebGL 2 emulation in {@link GLEmu}. */
public class GL15 extends GL14 {
    protected GL15() {
    }

    public static void glBindBuffer(int target, int buffer) {
        GLEmu.bindBuffer(target, buffer);
    }

    public static void glBufferData(int target, ByteBuffer data, int usage) {
        GLEmu.bufferData(target, data, usage);
    }

    public static void glBufferData(int target, long size, int usage) {
        GLEmu.bufferData(target, size, usage);
    }

    public static void glDeleteBuffers(int buffer) {
        GLEmu.deleteBuffers(buffer);
    }

    public static int glGenBuffers() {
        return GLEmu.genBuffers();
    }
}
