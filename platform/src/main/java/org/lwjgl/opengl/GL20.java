package org.lwjgl.opengl;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import teafabric.web.gl.GLEmu;

/** LWJGL GL20 front-end over the WebGL 2 emulation in {@link GLEmu}. */
public class GL20 extends GL15 {
    protected GL20() {
    }

    public static void glAttachShader(int program, int shader) {
        GLEmu.attachShader(program, shader);
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

    public static void glDeleteProgram(int program) {
        GLEmu.deleteProgram(program);
    }

    public static void glDeleteShader(int shader) {
        GLEmu.deleteShader(shader);
    }

    public static void glEnableVertexAttribArray(int index) {
        GLEmu.enableVertexAttribArray(index);
    }

    public static void glDisableVertexAttribArray(int index) {
        GLEmu.disableVertexAttribArray(index);
    }

    public static int glGetAttribLocation(int program, CharSequence name) {
        return GLEmu.getAttribLocation(program, name);
    }

    public static String glGetProgramInfoLog(int program, int maxLength) {
        return GLEmu.getProgramInfoLog(program);
    }

    public static String glGetProgramInfoLog(int program) {
        return GLEmu.getProgramInfoLog(program);
    }

    public static int glGetProgrami(int program, int pname) {
        return GLEmu.getProgrami(program, pname);
    }

    public static String glGetShaderInfoLog(int shader, int maxLength) {
        return GLEmu.getShaderInfoLog(shader);
    }

    public static String glGetShaderInfoLog(int shader) {
        return GLEmu.getShaderInfoLog(shader);
    }

    public static int glGetShaderi(int shader, int pname) {
        return GLEmu.getShaderi(shader, pname);
    }

    public static int glGetUniformLocation(int program, CharSequence name) {
        return GLEmu.getUniformLocation(program, name);
    }

    public static void glLinkProgram(int program) {
        GLEmu.linkProgram(program);
    }

    public static void glShaderSource(int shader, CharSequence source) {
        GLEmu.shaderSource(shader, source);
    }

    public static void glUniform1i(int location, int v0) {
        GLEmu.uniform1i(location, v0);
    }

    public static void glUniform1f(int location, float v0) {
        GLEmu.uniformfv(1, location, java.nio.FloatBuffer.wrap(new float[] {v0}));
    }

    public static void glUseProgram(int program) {
        GLEmu.useProgram(program);
    }

    public static void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride,
            long pointer) {
        GLEmu.vertexAttribPointer(index, size, type, normalized, stride, pointer);
    }

    public static void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride,
            ByteBuffer pointer) {
        GLEmu.vertexAttribPointer(index, size, type, normalized, stride, teafabric.web.mem.NativeMemory.address(pointer));
    }

    public static void glScissor(int x, int y, int w, int h) {
        GL11.glScissor(x, y, w, h);
    }

    public static void glCopyTexSubImage2D(int target, int level, int xoff, int yoff, int x, int y, int w, int h) {
        GLEmu.copyTexSubImage2D(target, level, xoff, yoff, x, y, w, h);
    }

    public static void glUniform1fv(int location, FloatBuffer value) {
        GLEmu.uniformfv(1, location, value);
    }

    public static void glUniform1iv(int location, IntBuffer value) {
        GLEmu.uniformiv(1, location, value);
    }

    public static void glUniform2fv(int location, FloatBuffer value) {
        GLEmu.uniformfv(2, location, value);
    }

    public static void glUniform2iv(int location, IntBuffer value) {
        GLEmu.uniformiv(2, location, value);
    }

    public static void glUniform3fv(int location, FloatBuffer value) {
        GLEmu.uniformfv(3, location, value);
    }

    public static void glUniform3iv(int location, IntBuffer value) {
        GLEmu.uniformiv(3, location, value);
    }

    public static void glUniform4fv(int location, FloatBuffer value) {
        GLEmu.uniformfv(4, location, value);
    }

    public static void glUniform4iv(int location, IntBuffer value) {
        GLEmu.uniformiv(4, location, value);
    }

    public static void glUniformMatrix2fv(int location, boolean transpose, FloatBuffer value) {
        GLEmu.uniformMatrixfv(2, location, transpose, value);
    }

    public static void glUniformMatrix3fv(int location, boolean transpose, FloatBuffer value) {
        GLEmu.uniformMatrixfv(3, location, transpose, value);
    }

    public static void glUniformMatrix4fv(int location, boolean transpose, FloatBuffer value) {
        GLEmu.uniformMatrixfv(4, location, transpose, value);
    }
}
