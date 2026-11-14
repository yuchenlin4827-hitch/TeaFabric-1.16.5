package org.lwjgl.opengl;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import teafabric.web.gl.GLEmu;

/** LWJGL GL11 front-end over the WebGL 2 emulation in {@link GLEmu}. */
public class GL11 {
    protected GL11() {
    }

    public static void glEnable(int cap) {
        GLEmu.enable(cap);
    }

    public static void glDisable(int cap) {
        GLEmu.disable(cap);
    }

    public static void glEnableClientState(int array) {
        GLEmu.enableClientState(array);
    }

    public static void glDisableClientState(int array) {
        GLEmu.disableClientState(array);
    }

    public static void glAlphaFunc(int func, float ref) {
        GLEmu.alphaFunc(func, ref);
    }

    public static void glBindTexture(int target, int texture) {
        GLEmu.bindTexture(target, texture);
    }

    public static void glBlendFunc(int src, int dst) {
        GLEmu.blendFunc(src, dst);
    }

    public static void glClear(int mask) {
        WebGLAccess.gl().clear(mask);
    }

    public static void glClearColor(float r, float g, float b, float a) {
        WebGLAccess.gl().clearColor(r, g, b, a);
    }

    public static void glClearDepth(double depth) {
        WebGLAccess.gl().clearDepth((float) depth);
    }

    public static void glClearStencil(int s) {
        WebGLAccess.gl().clearStencil(s);
    }

    public static void glColor4f(float r, float g, float b, float a) {
        GLEmu.color4f(r, g, b, a);
    }

    public static void glColor3f(float r, float g, float b) {
        GLEmu.color4f(r, g, b, 1);
    }

    public static void glColorMask(boolean r, boolean g, boolean b, boolean a) {
        WebGLAccess.gl().colorMask(r, g, b, a);
    }

    public static void glColorMaterial(int face, int mode) {
    }

    public static void glColorPointer(int size, int type, int stride, long pointer) {
        GLEmu.colorPointer(size, type, stride, pointer);
    }

    public static void glColorPointer(int size, int type, int stride, ByteBuffer pointer) {
        GLEmu.colorPointer(size, type, stride, teafabric.web.mem.NativeMemory.address(pointer));
    }

    public static void glCullFace(int mode) {
        WebGLAccess.gl().cullFace(mode);
    }

    public static void glDeleteTextures(int texture) {
        GLEmu.deleteTextures(texture);
    }

    public static void glDeleteTextures(int[] textures) {
        for (int t : textures) {
            GLEmu.deleteTextures(t);
        }
    }

    public static void glDeleteTextures(IntBuffer textures) {
        for (int i = textures.position(); i < textures.limit(); i++) {
            GLEmu.deleteTextures(textures.get(i));
        }
    }

    public static void glDepthFunc(int func) {
        WebGLAccess.gl().depthFunc(func);
    }

    public static void glDepthMask(boolean flag) {
        WebGLAccess.gl().depthMask(flag);
    }

    public static void glDrawArrays(int mode, int first, int count) {
        GLEmu.drawArrays(mode, first, count);
    }

    public static void glFogf(int pname, float param) {
        GLEmu.fogf(pname, param);
    }

    public static void glFogi(int pname, int param) {
        GLEmu.fogi(pname, param);
    }

    public static void glFogfv(int pname, float[] params) {
        GLEmu.fogfv(pname, params);
    }

    public static void glFogfv(int pname, FloatBuffer params) {
        GLEmu.fogfv(pname, GLEmu.readFloats(params, 4));
    }

    public static void glFinish() {
        WebGLAccess.gl().finish();
    }

    public static void glFlush() {
        WebGLAccess.gl().flush();
    }

    public static int glGenTextures() {
        return GLEmu.genTextures();
    }

    public static void glGenTextures(int[] textures) {
        for (int i = 0; i < textures.length; i++) {
            textures[i] = GLEmu.genTextures();
        }
    }

    public static void glGenTextures(IntBuffer textures) {
        for (int i = textures.position(); i < textures.limit(); i++) {
            textures.put(i, GLEmu.genTextures());
        }
    }

    public static int glGetError() {
        return 0;
    }

    public static void glGetFloatv(int pname, FloatBuffer params) {
        GLEmu.getFloatv(pname, params);
    }

    public static float glGetFloat(int pname) {
        FloatBuffer b = java.nio.FloatBuffer.allocate(16);
        GLEmu.getFloatv(pname, b);
        return b.get(0);
    }

    public static int glGetInteger(int pname) {
        return GLEmu.getInteger(pname);
    }

    public static void glGetIntegerv(int pname, IntBuffer params) {
        params.put(params.position(), GLEmu.getInteger(pname));
    }

    public static boolean glGetBoolean(int pname) {
        return GLEmu.getInteger(pname) != 0;
    }

    public static String glGetString(int name) {
        return GLEmu.getString(name);
    }

    public static void glGetTexImage(int target, int level, int format, int type, long pixels) {
        GLEmu.getTexImage(target, level, format, type, pixels);
    }

    public static void glGetTexImage(int target, int level, int format, int type, ByteBuffer pixels) {
        GLEmu.getTexImage(target, level, format, type, teafabric.web.mem.NativeMemory.address(pixels));
    }

    public static void glGetTexImage(int target, int level, int format, int type, IntBuffer pixels) {
        GLEmu.getTexImage(target, level, format, type, teafabric.web.mem.NativeMemory.address(pixels));
    }

    public static int glGetTexLevelParameteri(int target, int level, int pname) {
        return GLEmu.getTexLevelParameteri(target, level, pname);
    }

    public static void glHint(int target, int mode) {
    }

    public static boolean glIsEnabled(int cap) {
        return GLEmu.isEnabled(cap);
    }

    public static void glLightModelfv(int pname, FloatBuffer params) {
        GLEmu.lightModelfv(pname, GLEmu.readFloats(params, 4));
    }

    public static void glLightModelfv(int pname, float[] params) {
        GLEmu.lightModelfv(pname, params);
    }

    public static void glLightfv(int light, int pname, FloatBuffer params) {
        GLEmu.lightfv(light, pname, GLEmu.readFloats(params, 4));
    }

    public static void glLightfv(int light, int pname, float[] params) {
        GLEmu.lightfv(light, pname, params);
    }

    public static void glLineWidth(float width) {
        WebGLAccess.gl().lineWidth(width);
    }

    public static void glLoadIdentity() {
        GLEmu.loadIdentity();
    }

    public static void glLogicOp(int op) {
        GLEmu.logicOp(op);
    }

    public static void glMatrixMode(int mode) {
        GLEmu.matrixMode(mode);
    }

    public static void glMultMatrixf(FloatBuffer m) {
        GLEmu.multMatrixf(GLEmu.readFloats(m, 16));
    }

    public static void glMultMatrixf(float[] m) {
        GLEmu.multMatrixf(m);
    }

    public static void glNormal3f(float x, float y, float z) {
        GLEmu.normal3f(x, y, z);
    }

    public static void glNormalPointer(int type, int stride, long pointer) {
        GLEmu.normalPointer(type, stride, pointer);
    }

    public static void glNormalPointer(int type, int stride, ByteBuffer pointer) {
        GLEmu.normalPointer(type, stride, teafabric.web.mem.NativeMemory.address(pointer));
    }

    public static void glOrtho(double l, double r, double b, double t, double n, double f) {
        GLEmu.ortho(l, r, b, t, n, f);
    }

    public static void glPixelStorei(int pname, int param) {
        GLEmu.pixelStorei(pname, param);
    }

    public static void glPixelTransferf(int pname, float param) {
    }

    public static void glPolygonMode(int face, int mode) {
    }

    public static void glPolygonOffset(float factor, float units) {
        WebGLAccess.gl().polygonOffset(factor, units);
    }

    public static void glPopAttrib() {
        GLEmu.popAttrib();
    }

    public static void glPushAttrib(int mask) {
        GLEmu.pushAttrib(mask);
    }

    public static void glPopMatrix() {
        GLEmu.popMatrix();
    }

    public static void glPushMatrix() {
        GLEmu.pushMatrix();
    }

    public static void glReadPixels(int x, int y, int w, int h, int format, int type, ByteBuffer pixels) {
        GLEmu.readPixels(x, y, w, h, format, type, pixels);
    }

    public static void glRotatef(float angle, float x, float y, float z) {
        GLEmu.rotatef(angle, x, y, z);
    }

    public static void glScaled(double x, double y, double z) {
        GLEmu.scalef((float) x, (float) y, (float) z);
    }

    public static void glScalef(float x, float y, float z) {
        GLEmu.scalef(x, y, z);
    }

    public static void glScissor(int x, int y, int w, int h) {
        WebGLAccess.gl().scissor(x, y, w, h);
    }

    public static void glShadeModel(int mode) {
    }

    public static void glStencilFunc(int func, int ref, int mask) {
        WebGLAccess.gl().stencilFunc(func, ref, mask);
    }

    public static void glStencilMask(int mask) {
        WebGLAccess.gl().stencilMask(mask);
    }

    public static void glStencilOp(int fail, int zfail, int zpass) {
        WebGLAccess.gl().stencilOp(fail, zfail, zpass);
    }

    public static void glTexCoordPointer(int size, int type, int stride, long pointer) {
        GLEmu.texCoordPointer(size, type, stride, pointer);
    }

    public static void glTexCoordPointer(int size, int type, int stride, ByteBuffer pointer) {
        GLEmu.texCoordPointer(size, type, stride, teafabric.web.mem.NativeMemory.address(pointer));
    }

    public static void glTexEnvi(int target, int pname, int param) {
        GLEmu.texEnvi(target, pname, param);
    }

    public static void glTexEnvfv(int target, int pname, FloatBuffer params) {
        GLEmu.texEnvfv(target, pname, GLEmu.readFloats(params, 4));
    }

    public static void glTexGeni(int coord, int pname, int param) {
        GLEmu.texGeni(coord, pname, param);
    }

    public static void glTexGenfv(int coord, int pname, FloatBuffer params) {
        GLEmu.texGenfv(coord, pname, GLEmu.readFloats(params, 4));
    }

    public static void glTexImage2D(int target, int level, int internal, int width, int height, int border,
            int format, int type, IntBuffer pixels) {
        GLEmu.texImage2D(target, level, internal, width, height, border, format, type, pixels);
    }

    public static void glTexImage2D(int target, int level, int internal, int width, int height, int border,
            int format, int type, ByteBuffer pixels) {
        GLEmu.texImage2D(target, level, internal, width, height, border, format, type, pixels);
    }

    public static void glTexImage2D(int target, int level, int internal, int width, int height, int border,
            int format, int type, long pixels) {
        GLEmu.texImage2D(target, level, internal, width, height, border, format, type,
                pixels == 0 ? null : teafabric.web.mem.NativeMemory.wrap(pixels, 4));
    }

    public static void glTexParameterf(int target, int pname, float param) {
        GLEmu.texParameterf(target, pname, param);
    }

    public static void glTexParameteri(int target, int pname, int param) {
        GLEmu.texParameteri(target, pname, param);
    }

    public static void glTexSubImage2D(int target, int level, int x, int y, int w, int h, int format, int type,
            long pixels) {
        GLEmu.texSubImage2D(target, level, x, y, w, h, format, type, pixels);
    }

    public static void glTexSubImage2D(int target, int level, int x, int y, int w, int h, int format, int type,
            ByteBuffer pixels) {
        GLEmu.texSubImage2D(target, level, x, y, w, h, format, type, teafabric.web.mem.NativeMemory.address(pixels));
    }

    public static void glTexSubImage2D(int target, int level, int x, int y, int w, int h, int format, int type,
            IntBuffer pixels) {
        GLEmu.texSubImage2D(target, level, x, y, w, h, format, type, teafabric.web.mem.NativeMemory.address(pixels));
    }

    public static void glTranslated(double x, double y, double z) {
        GLEmu.translatef((float) x, (float) y, (float) z);
    }

    public static void glTranslatef(float x, float y, float z) {
        GLEmu.translatef(x, y, z);
    }

    public static void glVertexPointer(int size, int type, int stride, long pointer) {
        GLEmu.vertexPointer(size, type, stride, pointer);
    }

    public static void glVertexPointer(int size, int type, int stride, ByteBuffer pointer) {
        GLEmu.vertexPointer(size, type, stride, teafabric.web.mem.NativeMemory.address(pointer));
    }

    public static void glViewport(int x, int y, int w, int h) {
        GLEmu.viewport(x, y, w, h);
    }

    public static void glCopyTexSubImage2D(int target, int level, int xoff, int yoff, int x, int y, int w, int h) {
        GLEmu.copyTexSubImage2D(target, level, xoff, yoff, x, y, w, h);
    }
}
