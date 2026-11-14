package org.lwjgl.opengl;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import teafabric.web.gl.GLEmu;

/** LWJGL's core-profile GL 2.0 entry points (used by Sodium), on the same emulation as GL20. */
public final class GL20C {
    private GL20C() {
    }

    public static void glAttachShader(int program, int shader) {
        GLEmu.attachShader(program, shader);
    }

    public static void glBindAttribLocation(int program, int index, CharSequence name) {
        GLEmu.bindAttribLocation(program, index, name);
    }

    public static void glBindBuffer(int target, int buffer) {
        GLEmu.bindBuffer(target, buffer);
    }

    public static void glBufferData(int target, long size, int usage) {
        GLEmu.bufferData(target, size, usage);
    }

    public static void glBufferData(int target, ByteBuffer data, int usage) {
        GLEmu.bufferData(target, data, usage);
    }

    public static void glCompileShader(int shader) {
        GLEmu.compileShader(shader);
    }

    public static int glCreateProgram() {
        return GLEmu.createProgram();
    }

    public static int glCreateShader(int type) {
        return GLEmu.createShader(type);
    }

    public static void glDeleteBuffers(int buffer) {
        GLEmu.deleteBuffers(buffer);
    }

    public static void glDeleteProgram(int program) {
        GLEmu.deleteProgram(program);
    }

    public static void glDeleteShader(int shader) {
        GLEmu.deleteShader(shader);
    }

    public static void glDisableVertexAttribArray(int index) {
        GLEmu.disableVertexAttribArray(index);
    }

    public static void glEnableVertexAttribArray(int index) {
        GLEmu.enableVertexAttribArray(index);
    }

    public static int glGenBuffers() {
        return GLEmu.genBuffers();
    }

    public static boolean glGetBoolean(int pname) {
        return GL11.glGetBoolean(pname);
    }

    public static float glGetFloat(int pname) {
        return GL11.glGetFloat(pname);
    }

    public static void glGetFloatv(int pname, FloatBuffer params) {
        GLEmu.getFloatv(pname, params);
    }

    public static int glGetInteger(int pname) {
        return GL11.glGetInteger(pname);
    }

    public static String glGetProgramInfoLog(int program) {
        return GLEmu.getProgramInfoLog(program);
    }

    public static int glGetProgrami(int program, int pname) {
        return GLEmu.getProgrami(program, pname);
    }

    public static String glGetShaderInfoLog(int shader) {
        return GLEmu.getShaderInfoLog(shader);
    }

    public static int glGetShaderi(int shader, int pname) {
        return GLEmu.getShaderi(shader, pname);
    }

    public static String glGetString(int name) {
        return GL11.glGetString(name);
    }

    public static int glGetUniformLocation(int program, CharSequence name) {
        return GLEmu.getUniformLocation(program, name);
    }

    public static void glLinkProgram(int program) {
        GLEmu.linkProgram(program);
    }

    public static void glMultiDrawArrays(int mode, IntBuffer first, IntBuffer count) {
        GLEmu.multiDrawArrays(mode, first, count);
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

    public static void glUniform3f(int location, float v0, float v1, float v2) {
        GLEmu.uniform3f(location, v0, v1, v2);
    }

    public static void glUniform4fv(int location, FloatBuffer value) {
        GLEmu.uniformfv(4, location, value);
    }

    public static void glUniformMatrix4fv(int location, boolean transpose, FloatBuffer value) {
        GLEmu.uniformMatrixfv(4, location, transpose, value);
    }

    public static void glUseProgram(int program) {
        GLEmu.useProgram(program);
    }

    public static void glVertexAttrib4fv(int index, FloatBuffer v) {
        int p = v.position();
        GLEmu.genericAttrib4f(index, v.get(p), v.get(p + 1), v.get(p + 2), v.get(p + 3));
    }

    public static void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride,
            long pointer) {
        GLEmu.vertexAttribPointer(index, size, type, normalized, stride, pointer);
    }

    public static void nglShaderSource(int shader, int count, long strings, long length) {
        GLEmu.shaderSource(shader, count, strings, length);
    }
}
