package org.lwjgl.opengl;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import teafabric.web.gl.GLEmu;

/**
 * Core-profile GL 3.0 as Iris and Sodium call it: vertex array objects (emulated by GLEmu) and the GL 2.0 / 3.0
 * functions under their GL30C names, delegating to the same implementations as GL20C and GL11.
 */
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

    public static void glBindAttribLocation(int program, int index, CharSequence name) {
        GLEmu.bindAttribLocation(program, index, name);
    }

    public static void glBlitFramebuffer(int sx0, int sy0, int sx1, int sy1, int dx0, int dy0, int dx1, int dy1,
            int mask, int filter) {
        GLEmu.blitFramebuffer(sx0, sy0, sx1, sy1, dx0, dy0, dx1, dy1, mask, filter);
    }

    public static void glBufferData(int target, float[] data, int usage) {
        ByteBuffer b = org.lwjgl.BufferUtils.createByteBuffer(data.length * 4);
        b.asFloatBuffer().put(data);
        GL20C.glBufferData(target, b, usage);
    }

    public static void glCopyTexImage2D(int target, int level, int internal, int x, int y, int w, int h, int border) {
        GLEmu.copyTexImage2D(target, level, internal, x, y, w, h, border);
    }

    public static void glDetachShader(int program, int shader) {
        GLEmu.detachShader(program, shader);
    }

    public static void glDrawBuffers(int[] buffers) {
        GLEmu.drawBuffers(buffers);
    }

    public static void glDrawBuffers(int buffer) {
        GLEmu.drawBuffers(new int[] {buffer});
    }

    public static void glGenerateMipmap(int target) {
        GLEmu.generateMipmap(target);
    }

    public static String glGetActiveUniform(int program, int index, int maxLength, IntBuffer size, IntBuffer type) {
        return GLEmu.getActiveUniform(program, index, size, type);
    }

    public static int glGetAttribLocation(int program, CharSequence name) {
        return GLEmu.getAttribLocation(program, name);
    }

    public static String glGetProgramInfoLog(int program) {
        return GL20C.glGetProgramInfoLog(program);
    }

    public static String glGetShaderInfoLog(int shader) {
        return GL20C.glGetShaderInfoLog(shader);
    }

    public static int glGetUniformLocation(int program, CharSequence name) {
        return GL20C.glGetUniformLocation(program, name);
    }

    public static void glReadBuffer(int mode) {
        GLEmu.readBuffer(mode);
    }

    public static void glReadPixels(int x, int y, int w, int h, int format, int type, float[] pixels) {
        ByteBuffer b = org.lwjgl.BufferUtils.createByteBuffer(pixels.length * 4);
        GL11.glReadPixels(x, y, w, h, format, type, b);
        b.asFloatBuffer().get(pixels);
    }

    public static void glTexImage2D(int target, int level, int internal, int width, int height, int border, int format,
            int type, ByteBuffer pixels) {
        GL11.glTexImage2D(target, level, internal, width, height, border, format, type, pixels);
    }

    public static void glUniform1f(int location, float v0) {
        GLEmu.uniform1f(location, v0);
    }

    public static void glUniform1i(int location, int v0) {
        GLEmu.uniform1i(location, v0);
    }

    public static void glUniform2f(int location, float v0, float v1) {
        GLEmu.uniform2f(location, v0, v1);
    }

    public static void glUniform2i(int location, int v0, int v1) {
        GLEmu.uniform2i(location, v0, v1);
    }

    public static void glUniform3f(int location, float v0, float v1, float v2) {
        GLEmu.uniform3f(location, v0, v1, v2);
    }

    public static void glUniform3i(int location, int v0, int v1, int v2) {
        GLEmu.uniform3i(location, v0, v1, v2);
    }

    public static void glUniform4f(int location, float v0, float v1, float v2, float v3) {
        GLEmu.uniform4f(location, v0, v1, v2, v3);
    }

    public static void glUniform4i(int location, int v0, int v1, int v2, int v3) {
        GLEmu.uniform4i(location, v0, v1, v2, v3);
    }

    public static void glUniformMatrix4fv(int location, boolean transpose, FloatBuffer value) {
        GLEmu.uniformMatrixfv(4, location, transpose, value);
    }

    public static void glVertexAttrib4f(int index, float x, float y, float z, float w) {
        GLEmu.genericAttrib4f(index, x, y, z, w);
    }
}
