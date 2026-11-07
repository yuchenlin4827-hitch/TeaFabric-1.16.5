package teafabric.web.gl;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.ArrayBufferView;
import org.teavm.jso.typedarrays.Float32Array;
import org.teavm.jso.typedarrays.Int32Array;
import org.teavm.jso.webgl.WebGL2RenderingContext;
import org.teavm.jso.webgl.WebGLBuffer;
import org.teavm.jso.webgl.WebGLFramebuffer;
import org.teavm.jso.webgl.WebGLProgram;
import org.teavm.jso.webgl.WebGLShader;
import org.teavm.jso.webgl.WebGLTexture;
import org.teavm.jso.webgl.WebGLUniformLocation;
import teafabric.web.log.LogSink;
import teafabric.web.mem.NativeMemory;

/**
 * OpenGL 2.1 compatibility-profile emulation on WebGL 2, covering exactly the API surface that
 * Minecraft 1.16.5 calls through LWJGL (fixed-function matrices and client arrays, texture
 * environments, framebuffer objects, GLSL 1.10 programs).
 *
 * <p>Pointers passed to gl*Pointer / glTexSubImage2D / glReadPixels are addresses in the Wasm
 * linear memory (see {@link NativeMemory}); vertex data is streamed from there into a WebGL
 * buffer at draw time, and GL_QUADS are drawn through a shared quad index buffer.
 */
public final class GLEmu {
    private GLEmu() {
    }

    static WebGL2RenderingContext gl;
    static ShaderJit jit;

    // ------------------------------------------------------------------ object tables

    private static final List<JSObject> textures = new ArrayList<>();
    private static final List<int[]> textureSizes = new ArrayList<>(); // w, h, internalformat
    private static final List<JSObject> buffers = new ArrayList<>();
    private static final List<JSObject> framebuffers = new ArrayList<>();
    private static final List<JSObject> shaders = new ArrayList<>();
    private static final List<String> shaderLogs = new ArrayList<>();
    private static final List<int[]> shaderTypes = new ArrayList<>();
    private static final List<UserProgram> programs = new ArrayList<>();
    private static final List<WebGLUniformLocation> uniformLocations = new ArrayList<>();

    static final class UserProgram {
        WebGLProgram program;
        final List<Integer> shaders = new ArrayList<>();
        String log = "";
        boolean linked;
        String firstAttribute;
    }

    private static int alloc(List<JSObject> table, JSObject obj) {
        if (table.isEmpty()) {
            table.add(null); // id 0 is reserved
        }
        table.add(obj);
        return table.size() - 1;
    }

    private static JSObject get(List<JSObject> table, int id) {
        return id > 0 && id < table.size() ? table.get(id) : null;
    }

    // ------------------------------------------------------------------ fixed-function state

    static final class ArrayPtr {
        boolean enabled;
        int size = 4;
        int type = GLConst.FLOAT;
        int stride;
        long pointer;
        int buffer;
        boolean normalized;
    }

    private static final MatrixStack modelview = new MatrixStack(64);
    private static final MatrixStack projection = new MatrixStack(16);
    private static final MatrixStack[] textureMatrix = {new MatrixStack(16), new MatrixStack(16), new MatrixStack(16)};
    private static int matrixMode = GLConst.MODELVIEW;
    private static int activeTexture;
    private static int clientActiveTexture;

    private static final float[] currentColor = {1, 1, 1, 1};
    private static final float[] currentNormal = {0, 0, 1};
    private static final float[][] currentTexCoord = {{0, 0, 0, 1}, {0, 0, 0, 1}, {0, 0, 0, 1}};

    private static final ArrayPtr vertexArray = new ArrayPtr();
    private static final ArrayPtr colorArray = new ArrayPtr();
    private static final ArrayPtr normalArray = new ArrayPtr();
    private static final ArrayPtr[] texCoordArray = {new ArrayPtr(), new ArrayPtr(), new ArrayPtr()};
    private static final ArrayPtr[] genericArray = new ArrayPtr[16];

    static {
        for (int i = 0; i < genericArray.length; i++) {
            genericArray[i] = new ArrayPtr();
        }
    }

    private static final boolean[] texture2D = new boolean[3];
    private static final TexEnv[] texEnv = {new TexEnv(), new TexEnv(), new TexEnv()};
    private static final int[] texGenMask = new int[3];
    private static final int[][] texGenMode = {{GLConst.EYE_LINEAR, GLConst.EYE_LINEAR, GLConst.EYE_LINEAR,
        GLConst.EYE_LINEAR}, {GLConst.EYE_LINEAR, GLConst.EYE_LINEAR, GLConst.EYE_LINEAR, GLConst.EYE_LINEAR},
        {GLConst.EYE_LINEAR, GLConst.EYE_LINEAR, GLConst.EYE_LINEAR, GLConst.EYE_LINEAR}};
    private static final float[][][] texGenPlane = new float[3][4][];
    private static final int[] boundTexture = new int[3];

    private static boolean alphaTest;
    private static int alphaFunc = GLConst.ALWAYS;
    private static float alphaRef;
    private static boolean fog;
    private static int fogMode = GLConst.EXP;
    private static float fogStart;
    private static float fogEnd = 1;
    private static float fogDensity = 1;
    private static final float[] fogColor = new float[4];
    private static boolean lighting;
    private static final boolean[] lightEnabled = new boolean[2];
    private static final float[][] lightPosition = {{0, 0, 1, 0}, {0, 0, 1, 0}};
    private static final float[][] lightDiffuse = {{1, 1, 1, 1}, {0, 0, 0, 1}};
    private static final float[][] lightAmbient = {{0, 0, 0, 1}, {0, 0, 0, 1}};
    private static final float[] lightModelAmbient = {0.2f, 0.2f, 0.2f, 1};
    private static boolean colorLogicOp;
    private static int logicOp = GLConst.COPY;
    private static boolean blend;
    private static int blendSrcRgb = 1;
    private static int blendDstRgb;
    private static int blendSrcAlpha = 1;
    private static int blendDstAlpha;

    private static int arrayBufferBinding;
    private static int currentProgram;
    private static JSObject glProgramInUse;
    private static int drawFramebuffer;
    private static int readFramebuffer;
    private static final int[] viewport = new int[4];

    private static final ShaderJit.Variant variant = new ShaderJit.Variant();
    private static WebGLBuffer streamVbo;
    private static WebGLBuffer quadIbo;
    private static int quadIboQuads;
    private static final boolean[] attribEnabled = new boolean[16];

    private static final Map<Integer, Integer> intParams = new HashMap<>();
    private static int draws;

    public static void init(WebGL2RenderingContext context) {
        gl = context;
        jit = new ShaderJit(context);
        streamVbo = gl.createBuffer();
        quadIbo = gl.createBuffer();
        for (int u = 0; u < 3; u++) {
            texGenPlane[u][0] = new float[] {1, 0, 0, 0};
            texGenPlane[u][1] = new float[] {0, 1, 0, 0};
            texGenPlane[u][2] = new float[] {0, 0, 0, 0};
            texGenPlane[u][3] = new float[] {0, 0, 0, 0};
        }
        intParams.put(3379, gl.getParameteri(WebGL2RenderingContext.MAX_TEXTURE_SIZE));
    }

    public static int drawCount() {
        return draws;
    }

    public static int jitVariants() {
        return jit == null ? 0 : jit.compiledCount();
    }

    // ------------------------------------------------------------------ enable / disable

    public static void enable(int cap) {
        setCap(cap, true);
    }

    public static void disable(int cap) {
        setCap(cap, false);
    }

    private static void setCap(int cap, boolean on) {
        switch (cap) {
            case GLConst.TEXTURE_2D: texture2D[activeTexture] = on; return;
            case GLConst.ALPHA_TEST: alphaTest = on; return;
            case GLConst.FOG: fog = on; return;
            case GLConst.LIGHTING: lighting = on; return;
            case GLConst.LIGHT0: lightEnabled[0] = on; return;
            case GLConst.LIGHT1: lightEnabled[1] = on; return;
            case GLConst.TEXTURE_GEN_S: case GLConst.TEXTURE_GEN_T: case GLConst.TEXTURE_GEN_R:
            case GLConst.TEXTURE_GEN_Q: {
                int bit = 1 << (cap - GLConst.TEXTURE_GEN_S);
                texGenMask[activeTexture] = on ? texGenMask[activeTexture] | bit : texGenMask[activeTexture] & ~bit;
                return;
            }
            case GLConst.COLOR_LOGIC_OP: colorLogicOp = on; applyBlend(); return;
            case GLConst.BLEND: blend = on; applyBlend(); return;
            case GLConst.COLOR_MATERIAL: case GLConst.NORMALIZE: case GLConst.RESCALE_NORMAL: case 2848:
            case 2832: case 3024: case 10754: case 37600: case 33346: case 34370:
                return; // no WebGL equivalent / always on
            case GLConst.DEPTH_TEST: case GLConst.CULL_FACE: case GLConst.POLYGON_OFFSET_FILL:
            case GLConst.SCISSOR_TEST: case GLConst.STENCIL_TEST:
                if (on) {
                    gl.enable(cap);
                } else {
                    gl.disable(cap);
                }
                return;
            default:
                return;
        }
    }

    public static void enableClientState(int array) {
        clientState(array, true);
    }

    public static void disableClientState(int array) {
        clientState(array, false);
    }

    private static void clientState(int array, boolean on) {
        switch (array) {
            case GLConst.VERTEX_ARRAY: vertexArray.enabled = on; break;
            case GLConst.COLOR_ARRAY: colorArray.enabled = on; break;
            case GLConst.NORMAL_ARRAY: normalArray.enabled = on; break;
            case GLConst.TEXTURE_COORD_ARRAY: texCoordArray[clientActiveTexture].enabled = on; break;
            default: break;
        }
    }

    // ------------------------------------------------------------------ blending (incl. logic-op approximation)

    public static void blendFunc(int src, int dst) {
        blendSrcRgb = src;
        blendDstRgb = dst;
        blendSrcAlpha = src;
        blendDstAlpha = dst;
        applyBlend();
    }

    public static void blendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        blendSrcRgb = srcRgb;
        blendDstRgb = dstRgb;
        blendSrcAlpha = srcAlpha;
        blendDstAlpha = dstAlpha;
        applyBlend();
    }

    public static void logicOp(int op) {
        logicOp = op;
        applyBlend();
    }

    private static void applyBlend() {
        if (colorLogicOp && logicOp != GLConst.COPY) {
            // WebGL has no logic ops; Minecraft uses OR_REVERSE / INVERT for text selection highlights.
            gl.enable(WebGL2RenderingContext.BLEND);
            gl.blendFuncSeparate(WebGL2RenderingContext.ONE_MINUS_DST_COLOR, WebGL2RenderingContext.ZERO,
                    WebGL2RenderingContext.ONE, WebGL2RenderingContext.ZERO);
            return;
        }
        if (blend) {
            gl.enable(WebGL2RenderingContext.BLEND);
        } else {
            gl.disable(WebGL2RenderingContext.BLEND);
        }
        gl.blendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
    }

    // ------------------------------------------------------------------ fixed-function parameters

    public static void alphaFunc(int func, float ref) {
        alphaFunc = func;
        alphaRef = ref;
    }

    public static void color4f(float r, float g, float b, float a) {
        currentColor[0] = r;
        currentColor[1] = g;
        currentColor[2] = b;
        currentColor[3] = a;
    }

    public static void normal3f(float x, float y, float z) {
        currentNormal[0] = x;
        currentNormal[1] = y;
        currentNormal[2] = z;
    }

    public static void multiTexCoord2f(int target, float s, float t) {
        int u = target - GLConst.TEXTURE0;
        if (u >= 0 && u < 3) {
            currentTexCoord[u][0] = s;
            currentTexCoord[u][1] = t;
            currentTexCoord[u][2] = 0;
            currentTexCoord[u][3] = 1;
        }
    }

    public static void fogf(int pname, float v) {
        switch (pname) {
            case GLConst.FOG_DENSITY: fogDensity = v; break;
            case GLConst.FOG_START: fogStart = v; break;
            case GLConst.FOG_END: fogEnd = v; break;
            case GLConst.FOG_MODE: fogMode = (int) v; break;
            default: break;
        }
    }

    public static void fogi(int pname, int v) {
        if (pname == GLConst.FOG_MODE) {
            fogMode = v;
        } else {
            fogf(pname, v);
        }
    }

    public static void fogfv(int pname, float[] v) {
        if (pname == GLConst.FOG_COLOR) {
            System.arraycopy(v, 0, fogColor, 0, Math.min(4, v.length));
        } else if (v.length > 0) {
            fogf(pname, v[0]);
        }
    }

    public static void lightfv(int light, int pname, float[] v) {
        int i = light - GLConst.LIGHT0;
        if (i < 0 || i > 1) {
            return;
        }
        switch (pname) {
            case GLConst.POSITION: {
                float[] out = new float[4];
                Mat4.transform(modelview.top(), v, out); // positions are stored in eye space
                lightPosition[i] = out;
                break;
            }
            case GLConst.DIFFUSE: lightDiffuse[i] = v.clone(); break;
            case GLConst.AMBIENT: lightAmbient[i] = v.clone(); break;
            default: break;
        }
    }

    public static void lightModelfv(int pname, float[] v) {
        if (pname == GLConst.LIGHT_MODEL_AMBIENT) {
            System.arraycopy(v, 0, lightModelAmbient, 0, 4);
        }
    }

    public static void texEnvi(int target, int pname, int param) {
        if (target == GLConst.TEXTURE_ENV) {
            texEnv[activeTexture].set(pname, param);
        }
    }

    public static void texEnvfv(int target, int pname, float[] v) {
        if (target == GLConst.TEXTURE_ENV && pname == GLConst.TEXTURE_ENV_COLOR) {
            System.arraycopy(v, 0, texEnv[activeTexture].color, 0, 4);
        }
    }

    public static void texGeni(int coord, int pname, int param) {
        if (pname == GLConst.TEXTURE_GEN_MODE) {
            texGenMode[activeTexture][coord - GLConst.S] = param;
        }
    }

    public static void texGenfv(int coord, int pname, float[] plane) {
        int c = coord - GLConst.S;
        if (pname == GLConst.EYE_PLANE) {
            // Eye planes are transformed by the inverse modelview current at specification time.
            float[] inv = new float[16];
            if (!Mat4.invert(modelview.top(), inv)) {
                inv = Mat4.identity();
            }
            float[] out = new float[4];
            Mat4.transformRow(plane, inv, out);
            texGenPlane[activeTexture][c] = out;
        } else if (pname == GLConst.OBJECT_PLANE) {
            texGenPlane[activeTexture][c] = plane.clone();
        }
    }

    // ------------------------------------------------------------------ matrices

    private static MatrixStack stack() {
        switch (matrixMode) {
            case GLConst.PROJECTION: return projection;
            case GLConst.TEXTURE: return textureMatrix[activeTexture];
            default: return modelview;
        }
    }

    public static void matrixMode(int mode) {
        matrixMode = mode;
    }

    public static void pushMatrix() {
        stack().push();
    }

    public static void popMatrix() {
        stack().pop();
    }

    public static void loadIdentity() {
        MatrixStack s = stack();
        Mat4.setIdentity(s.top());
        s.changed();
    }

    public static void multMatrixf(float[] m) {
        MatrixStack s = stack();
        Mat4.mul(s.top(), m);
        s.changed();
    }

    public static void translatef(float x, float y, float z) {
        MatrixStack s = stack();
        Mat4.translate(s.top(), x, y, z);
        s.changed();
    }

    public static void scalef(float x, float y, float z) {
        MatrixStack s = stack();
        Mat4.scale(s.top(), x, y, z);
        s.changed();
    }

    public static void rotatef(float angle, float x, float y, float z) {
        MatrixStack s = stack();
        Mat4.rotate(s.top(), angle, x, y, z);
        s.changed();
    }

    public static void ortho(double l, double r, double b, double t, double n, double f) {
        MatrixStack s = stack();
        Mat4.ortho(s.top(), l, r, b, t, n, f);
        s.changed();
    }

    public static void getFloatv(int pname, FloatBuffer out) {
        float[] src;
        switch (pname) {
            case GLConst.MODELVIEW_MATRIX: src = modelview.top(); break;
            case GLConst.PROJECTION_MATRIX: src = projection.top(); break;
            case GLConst.TEXTURE_MATRIX: src = textureMatrix[activeTexture].top(); break;
            case GLConst.FOG_COLOR: src = fogColor; break;
            case 2816: src = currentColor; break; // GL_CURRENT_COLOR
            default: src = new float[16]; break;
        }
        int pos = out.position();
        for (int i = 0; i < src.length && pos + i < out.limit(); i++) {
            out.put(pos + i, src[i]);
        }
    }

    // ------------------------------------------------------------------ client arrays

    private static void pointer(ArrayPtr a, int size, int type, int stride, long pointer) {
        a.size = size;
        a.type = type;
        a.stride = stride;
        a.pointer = pointer;
        a.buffer = arrayBufferBinding;
    }

    public static void vertexPointer(int size, int type, int stride, long pointer) {
        pointer(vertexArray, size, type, stride, pointer);
    }

    public static void colorPointer(int size, int type, int stride, long pointer) {
        pointer(colorArray, size, type, stride, pointer);
        colorArray.normalized = type != GLConst.FLOAT;
    }

    public static void normalPointer(int type, int stride, long pointer) {
        pointer(normalArray, 3, type, stride, pointer);
        normalArray.normalized = type != GLConst.FLOAT;
    }

    public static void texCoordPointer(int size, int type, int stride, long pointer) {
        pointer(texCoordArray[clientActiveTexture], size, type, stride, pointer);
    }

    public static void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride,
            long pointer) {
        ArrayPtr a = genericArray[index];
        pointer(a, size, type, stride, pointer);
        a.normalized = normalized;
    }

    public static void enableVertexAttribArray(int index) {
        genericArray[index].enabled = true;
    }

    public static void disableVertexAttribArray(int index) {
        genericArray[index].enabled = false;
    }

    public static void activeTexture(int texture) {
        activeTexture = Math.max(0, Math.min(2, texture - GLConst.TEXTURE0));
        gl.activeTexture(texture);
    }

    public static void clientActiveTexture(int texture) {
        clientActiveTexture = Math.max(0, Math.min(2, texture - GLConst.TEXTURE0));
    }

    // ------------------------------------------------------------------ drawing

    private static int typeSize(int type) {
        switch (type) {
            case GLConst.BYTE: case GLConst.UNSIGNED_BYTE: return 1;
            case GLConst.SHORT: case GLConst.UNSIGNED_SHORT: return 2;
            case GLConst.DOUBLE: return 8;
            default: return 4;
        }
    }

    private static int strideOf(ArrayPtr a) {
        return a.stride != 0 ? a.stride : a.size * typeSize(a.type);
    }

    public static void drawArrays(int mode, int first, int count) {
        if (count <= 0) {
            return;
        }
        draws++;
        boolean user = currentProgram != 0;
        if (user) {
            UserProgram up = programs.get(currentProgram);
            useGlProgram(up.program);
            bindArrays(first, count, true);
        } else {
            ShaderJit.Program p = jit.get(buildVariant());
            useGlProgram(p.program);
            uploadUniforms(p);
            bindArrays(first, count, false);
        }
        issue(mode, count);
        gl.bindBuffer(WebGL2RenderingContext.ARRAY_BUFFER, (WebGLBuffer) get(buffers, arrayBufferBinding));
    }

    private static void useGlProgram(WebGLProgram program) {
        if (glProgramInUse != program) {
            gl.useProgram(program);
            glProgramInUse = program;
        }
    }

    private static ShaderJit.Variant buildVariant() {
        ShaderJit.Variant v = variant;
        int mask = 0;
        for (int u = 0; u < 3; u++) {
            if (texture2D[u]) {
                mask |= 1 << u;
                v.env[u].copyFrom(texEnv[u]);
                v.texGenMask[u] = texGenMask[u];
                System.arraycopy(texGenMode[u], 0, v.texGenMode[u], 0, 4);
            } else {
                v.texGenMask[u] = 0;
            }
        }
        v.texMask = mask;
        v.alphaFunc = alphaTest ? alphaFunc : 0;
        if (v.alphaFunc == GLConst.ALWAYS) {
            v.alphaFunc = 0;
        }
        v.fogMode = fog ? fogMode : 0;
        v.lighting = lighting;
        v.light0 = lightEnabled[0];
        v.light1 = lightEnabled[1];
        return v;
    }

    private static final float[] normal9 = new float[9];

    private static void uploadUniforms(ShaderJit.Program p) {
        if (p.mvVersion != modelview.version || p.mv == null || true) {
            gl.uniformMatrix4fv(p.mv, false, modelview.top());
        }
        gl.uniformMatrix4fv(p.proj, false, projection.top());
        ShaderJit.Variant v = p.variant;
        for (int u = 0; u < 3; u++) {
            if ((v.texMask & (1 << u)) != 0) {
                gl.uniformMatrix4fv(p.texMat[u], false, textureMatrix[u].top());
                if (p.envColor[u] != null) {
                    float[] c = texEnv[u].color;
                    gl.uniform4f(p.envColor[u], c[0], c[1], c[2], c[3]);
                }
                for (int c = 0; c < 4; c++) {
                    if (p.texGenPlane[u][c] != null) {
                        float[] pl = texGenPlane[u][c];
                        gl.uniform4f(p.texGenPlane[u][c], pl[0], pl[1], pl[2], pl[3]);
                    }
                }
            }
        }
        if (p.alphaRef != null) {
            gl.uniform1f(p.alphaRef, alphaRef);
        }
        if (p.fogColor != null) {
            gl.uniform4f(p.fogColor, fogColor[0], fogColor[1], fogColor[2], fogColor[3]);
            gl.uniform3f(p.fogParams, fogStart, fogEnd, fogDensity);
        }
        if (v.lighting) {
            Mat4.normalMatrix(modelview.top(), normal9);
            gl.uniformMatrix3fv(p.normal, false, normal9);
            float ar = lightModelAmbient[0];
            float ag = lightModelAmbient[1];
            float ab = lightModelAmbient[2];
            for (int i = 0; i < 2; i++) {
                if (lightEnabled[i]) {
                    ar += lightAmbient[i][0];
                    ag += lightAmbient[i][1];
                    ab += lightAmbient[i][2];
                    float[] pos = lightPosition[i];
                    float len = (float) Math.sqrt(pos[0] * pos[0] + pos[1] * pos[1] + pos[2] * pos[2]);
                    if (len == 0) {
                        len = 1;
                    }
                    gl.uniform3f(p.lightDir[i], pos[0] / len, pos[1] / len, pos[2] / len);
                    gl.uniform3f(p.lightDiffuse[i], lightDiffuse[i][0], lightDiffuse[i][1], lightDiffuse[i][2]);
                }
            }
            gl.uniform3f(p.lightAmbient, ar, ag, ab);
        }
    }

    /** Streams client-memory arrays into the stream VBO and wires attribute locations. */
    private static void bindArrays(int first, int count, boolean user) {
        ArrayPtr[] arrays = new ArrayPtr[16];
        int[] locations = new int[16];
        int n = 0;
        if (user) {
            boolean usedZero = false;
            for (int i = 0; i < genericArray.length; i++) {
                if (genericArray[i].enabled) {
                    arrays[n] = genericArray[i];
                    locations[n++] = i;
                    usedZero |= i == 0;
                }
            }
            if (!usedZero && vertexArray.enabled) { // compatibility-profile aliasing of gl_Vertex
                arrays[n] = vertexArray;
                locations[n++] = 0;
            }
        } else {
            arrays[n] = vertexArray;
            locations[n++] = ShaderJit.A_POS;
            arrays[n] = colorArray;
            locations[n++] = ShaderJit.A_COLOR;
            arrays[n] = normalArray;
            locations[n++] = ShaderJit.A_NORMAL;
            for (int u = 0; u < 3; u++) {
                arrays[n] = texCoordArray[u];
                locations[n++] = ShaderJit.A_TEX0 + u;
            }
        }
        long minStart = Long.MAX_VALUE;
        long maxEnd = 0;
        for (int i = 0; i < n; i++) {
            ArrayPtr a = arrays[i];
            if (a.enabled && a.buffer == 0) {
                int stride = strideOf(a);
                long start = a.pointer + (long) first * stride;
                long end = a.pointer + (long) (first + count - 1) * stride + (long) a.size * typeSize(a.type);
                minStart = Math.min(minStart, start);
                maxEnd = Math.max(maxEnd, end);
            }
        }
        if (maxEnd > 0) {
            gl.bindBuffer(WebGL2RenderingContext.ARRAY_BUFFER, streamVbo);
            gl.bufferData(WebGL2RenderingContext.ARRAY_BUFFER, NativeMemory.u8(minStart, (int) (maxEnd - minStart)),
                    WebGL2RenderingContext.STREAM_DRAW);
        }
        boolean[] used = new boolean[16];
        for (int i = 0; i < n; i++) {
            ArrayPtr a = arrays[i];
            int loc = locations[i];
            if (!a.enabled) {
                continue;
            }
            used[loc] = true;
            int stride = strideOf(a);
            long offset;
            if (a.buffer == 0) {
                gl.bindBuffer(WebGL2RenderingContext.ARRAY_BUFFER, streamVbo);
                offset = a.pointer + (long) first * stride - minStart;
            } else {
                gl.bindBuffer(WebGL2RenderingContext.ARRAY_BUFFER, (WebGLBuffer) get(buffers, a.buffer));
                offset = a.pointer + (long) first * stride;
            }
            boolean normalized = a.normalized;
            if (!user && (loc == ShaderJit.A_COLOR || loc == ShaderJit.A_NORMAL)) {
                normalized = a.type != GLConst.FLOAT;
            }
            gl.vertexAttribPointer(loc, a.size, a.type, normalized, stride, (int) offset);
            if (!attribEnabled[loc]) {
                gl.enableVertexAttribArray(loc);
                attribEnabled[loc] = true;
            }
        }
        for (int loc = 0; loc < 16; loc++) {
            if (!used[loc] && attribEnabled[loc]) {
                gl.disableVertexAttribArray(loc);
                attribEnabled[loc] = false;
            }
        }
        if (!user) {
            if (!used[ShaderJit.A_COLOR]) {
                gl.vertexAttrib4f(ShaderJit.A_COLOR, currentColor[0], currentColor[1], currentColor[2],
                        currentColor[3]);
            }
            if (!used[ShaderJit.A_NORMAL]) {
                gl.vertexAttrib4f(ShaderJit.A_NORMAL, currentNormal[0], currentNormal[1], currentNormal[2], 0);
            }
            for (int u = 0; u < 3; u++) {
                if (!used[ShaderJit.A_TEX0 + u]) {
                    float[] t = currentTexCoord[u];
                    gl.vertexAttrib4f(ShaderJit.A_TEX0 + u, t[0], t[1], t[2], t[3]);
                }
            }
        }
    }

    private static void issue(int mode, int count) {
        switch (mode) {
            case GLConst.QUADS: {
                int quads = count / 4;
                ensureQuadIndices(quads);
                gl.bindBuffer(WebGL2RenderingContext.ELEMENT_ARRAY_BUFFER, quadIbo);
                gl.drawElements(WebGL2RenderingContext.TRIANGLES, quads * 6, WebGL2RenderingContext.UNSIGNED_INT, 0);
                break;
            }
            case GLConst.QUAD_STRIP:
                gl.drawArrays(WebGL2RenderingContext.TRIANGLE_STRIP, 0, count);
                break;
            case GLConst.POLYGON:
                gl.drawArrays(WebGL2RenderingContext.TRIANGLE_FAN, 0, count);
                break;
            default:
                gl.drawArrays(mode, 0, count);
                break;
        }
    }

    private static void ensureQuadIndices(int quads) {
        if (quads <= quadIboQuads) {
            return;
        }
        int capacity = Math.max(quads, Math.max(4096, quadIboQuads * 2));
        Int32Array indices = Int32Array.create(capacity * 6); // same bytes as UNSIGNED_INT below 2^31
        for (int q = 0; q < capacity; q++) {
            int v = q * 4;
            int i = q * 6;
            indices.set(i, v);
            indices.set(i + 1, v + 1);
            indices.set(i + 2, v + 2);
            indices.set(i + 3, v);
            indices.set(i + 4, v + 2);
            indices.set(i + 5, v + 3);
        }
        gl.bindBuffer(WebGL2RenderingContext.ELEMENT_ARRAY_BUFFER, quadIbo);
        gl.bufferData(WebGL2RenderingContext.ELEMENT_ARRAY_BUFFER, indices, WebGL2RenderingContext.STATIC_DRAW);
        quadIboQuads = capacity;
    }

    // ------------------------------------------------------------------ buffers

    public static int genBuffers() {
        return alloc(buffers, gl.createBuffer());
    }

    public static void deleteBuffers(int id) {
        JSObject b = get(buffers, id);
        if (b != null) {
            gl.deleteBuffer((WebGLBuffer) b);
            buffers.set(id, null);
        }
        if (arrayBufferBinding == id) {
            arrayBufferBinding = 0;
        }
    }

    public static void bindBuffer(int target, int id) {
        if (target == GLConst.ARRAY_BUFFER) {
            arrayBufferBinding = id;
        }
        gl.bindBuffer(target, (WebGLBuffer) get(buffers, id));
    }

    public static void bufferData(int target, ByteBuffer data, int usage) {
        if (data == null) {
            return;
        }
        if (NativeMemory.isNative(data)) {
            gl.bufferData(target, NativeMemory.u8(NativeMemory.address(data), data.remaining()), usage);
        } else {
            byte[] tmp = new byte[data.remaining()];
            data.duplicate().get(tmp);
            gl.bufferData(target, org.teavm.jso.typedarrays.Int8Array.copyFromJavaArray(tmp), usage);
        }
    }

    public static void bufferData(int target, long size, int usage) {
        gl.bufferData(target, (int) size, usage);
    }

    // ------------------------------------------------------------------ textures

    public static int genTextures() {
        int id = alloc(textures, gl.createTexture());
        while (textureSizes.size() <= id) {
            textureSizes.add(new int[3]);
        }
        return id;
    }

    public static void deleteTextures(int id) {
        JSObject t = get(textures, id);
        if (t != null) {
            gl.deleteTexture((WebGLTexture) t);
            textures.set(id, null);
        }
        for (int u = 0; u < 3; u++) {
            if (boundTexture[u] == id) {
                boundTexture[u] = 0;
            }
        }
    }

    public static void bindTexture(int target, int id) {
        boundTexture[activeTexture] = id;
        gl.bindTexture(WebGL2RenderingContext.TEXTURE_2D, (WebGLTexture) get(textures, id));
    }

    public static void texParameteri(int target, int pname, int param) {
        switch (pname) {
            case 34049: // GL_TEXTURE_LOD_BIAS: not in WebGL 2
            case 33169: // GL_GENERATE_MIPMAP
                return;
            case WebGL2RenderingContext.TEXTURE_WRAP_S: case WebGL2RenderingContext.TEXTURE_WRAP_T:
                if (param == GLConst.CLAMP || param == 33069) { // GL_CLAMP, GL_CLAMP_TO_BORDER
                    param = GLConst.CLAMP_TO_EDGE;
                }
                break;
            default:
                break;
        }
        gl.texParameteri(WebGL2RenderingContext.TEXTURE_2D, pname, param);
    }

    public static void texParameterf(int target, int pname, float param) {
        switch (pname) {
            case 34049:
                return;
            case WebGL2RenderingContext.TEXTURE_MIN_LOD: case WebGL2RenderingContext.TEXTURE_MAX_LOD:
                gl.texParameterf(WebGL2RenderingContext.TEXTURE_2D, pname, param);
                return;
            default:
                texParameteri(target, pname, (int) param);
        }
    }

    /** Maps desktop internal formats to a WebGL 2 internal/format/type triple. */
    private static int[] textureFormat(int internal, int format, int type) {
        switch (internal) {
            case GLConst.DEPTH_COMPONENT: case 33189: case 33190: case 33191: case 36012:
                return type == GLConst.FLOAT ? new int[] {36012, GLConst.DEPTH_COMPONENT, GLConst.FLOAT}
                        : new int[] {33190, GLConst.DEPTH_COMPONENT, GLConst.UNSIGNED_INT};
            case 32856: case GLConst.RGBA: case 4:
                return new int[] {32856, GLConst.RGBA, GLConst.UNSIGNED_BYTE};
            case 32849: case GLConst.RGB: case 3:
                return new int[] {32849, GLConst.RGB, GLConst.UNSIGNED_BYTE};
            case 6403: case 33321: // GL_RED, GL_R8
                return new int[] {33321, 6403, GLConst.UNSIGNED_BYTE};
            case 6409: // GL_LUMINANCE
                return new int[] {6409, 6409, GLConst.UNSIGNED_BYTE};
            case 6410: // GL_LUMINANCE_ALPHA
                return new int[] {6410, 6410, GLConst.UNSIGNED_BYTE};
            case 6406: // GL_ALPHA
                return new int[] {6406, 6406, GLConst.UNSIGNED_BYTE};
            default:
                return new int[] {internal, format, type};
        }
    }

    public static void texImage2D(int target, int level, int internal, int width, int height, int border,
            int format, int type, Buffer pixels) {
        int[] f = textureFormat(internal, format, type);
        ArrayBufferView view = null;
        if (pixels != null && NativeMemory.isNative(pixels)) {
            int bytes = width * height * bytesPerPixel(f[1], f[2]);
            view = NativeMemory.u8(NativeMemory.address(pixels), bytes);
        }
        gl.texImage2D(WebGL2RenderingContext.TEXTURE_2D, level, f[0], width, height, 0, f[1], f[2], view);
        if (level == 0) {
            int id = boundTexture[activeTexture];
            if (id > 0 && id < textureSizes.size()) {
                textureSizes.set(id, new int[] {width, height, internal});
            }
        }
    }

    public static void texSubImage2D(int target, int level, int x, int y, int width, int height, int format,
            int type, long pixels) {
        int bpp = bytesPerPixel(format, type);
        int rowLength = unpackRowLength > 0 ? unpackRowLength : width;
        long bytes = ((long) (unpackSkipRows + height - 1) * rowLength + unpackSkipPixels + width) * bpp;
        gl.texSubImage2D(WebGL2RenderingContext.TEXTURE_2D, level, x, y, width, height, format, type,
                NativeMemory.u8(pixels, (int) bytes));
    }

    public static void copyTexSubImage2D(int target, int level, int xoff, int yoff, int x, int y, int w, int h) {
        gl.copyTexSubImage2D(WebGL2RenderingContext.TEXTURE_2D, level, xoff, yoff, x, y, w, h);
    }

    private static int bytesPerPixel(int format, int type) {
        int comps;
        switch (format) {
            case GLConst.RGBA: case 32993: comps = 4; break; // RGBA, BGRA
            case GLConst.RGB: comps = 3; break;
            case 6410: case 33319: comps = 2; break; // LUMINANCE_ALPHA, RG
            default: comps = 1; break;
        }
        switch (type) {
            case GLConst.UNSIGNED_BYTE: case GLConst.BYTE: return comps;
            case GLConst.UNSIGNED_SHORT: case GLConst.SHORT: return comps * 2;
            case 33639: case 32821: return 4; // UNSIGNED_INT_8_8_8_8(_REV)
            default: return comps * 4;
        }
    }

    public static int getTexLevelParameteri(int target, int level, int pname) {
        int id = boundTexture[activeTexture];
        int[] s = id > 0 && id < textureSizes.size() ? textureSizes.get(id) : new int[3];
        int shift = Math.max(0, level);
        switch (pname) {
            case 4096: return Math.max(1, s[0] >> shift);
            case 4097: return Math.max(1, s[1] >> shift);
            case 4099: return s[2];
            default: return 0;
        }
    }

    private static int unpackRowLength;
    private static int unpackSkipRows;
    private static int unpackSkipPixels;

    public static void pixelStorei(int pname, int value) {
        switch (pname) {
            case 3314: unpackRowLength = value; break;
            case 3315: unpackSkipRows = value; break;
            case 3316: unpackSkipPixels = value; break;
            case 3317: case 3333: case 3330: case 3331: case 3332: break; // alignment, pack params
            case 3312: case 3313: return; // UNPACK_SWAP_BYTES / LSB_FIRST: unsupported
            default: break;
        }
        gl.pixelStorei(pname, value);
    }

    public static void readPixels(int x, int y, int w, int h, int format, int type, ByteBuffer out) {
        int bytes = w * h * bytesPerPixel(format, type);
        if (NativeMemory.isNative(out)) {
            gl.readPixels(x, y, w, h, format, type, NativeMemory.u8(NativeMemory.address(out), bytes));
        }
    }

    private static WebGLFramebuffer readbackFbo;

    /** glGetTexImage via a temporary framebuffer (WebGL cannot read textures directly). */
    public static void getTexImage(int target, int level, int format, int type, long pixels) {
        int id = boundTexture[activeTexture];
        int[] s = id > 0 && id < textureSizes.size() ? textureSizes.get(id) : null;
        if (s == null || s[0] == 0) {
            return;
        }
        int w = Math.max(1, s[0] >> level);
        int h = Math.max(1, s[1] >> level);
        if (readbackFbo == null) {
            readbackFbo = gl.createFramebuffer();
        }
        gl.bindFramebuffer(WebGL2RenderingContext.FRAMEBUFFER, readbackFbo);
        gl.framebufferTexture2D(WebGL2RenderingContext.FRAMEBUFFER, WebGL2RenderingContext.COLOR_ATTACHMENT0,
                WebGL2RenderingContext.TEXTURE_2D, (WebGLTexture) get(textures, id), level);
        gl.readPixels(0, 0, w, h, GLConst.RGBA, GLConst.UNSIGNED_BYTE, NativeMemory.u8(pixels, w * h * 4));
        gl.bindFramebuffer(WebGL2RenderingContext.FRAMEBUFFER, (WebGLFramebuffer) get(framebuffers, drawFramebuffer));
    }

    // ------------------------------------------------------------------ framebuffers

    public static int genFramebuffers() {
        return alloc(framebuffers, gl.createFramebuffer());
    }

    public static void deleteFramebuffers(int id) {
        JSObject f = get(framebuffers, id);
        if (f != null) {
            gl.deleteFramebuffer((WebGLFramebuffer) f);
            framebuffers.set(id, null);
        }
    }

    public static void bindFramebuffer(int target, int id) {
        if (target == GLConst.FRAMEBUFFER || target == GLConst.DRAW_FRAMEBUFFER) {
            drawFramebuffer = id;
        }
        if (target == GLConst.FRAMEBUFFER || target == GLConst.READ_FRAMEBUFFER) {
            readFramebuffer = id;
        }
        gl.bindFramebuffer(target, (WebGLFramebuffer) get(framebuffers, id));
    }

    public static void framebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        gl.framebufferTexture2D(target, attachment, WebGL2RenderingContext.TEXTURE_2D,
                (WebGLTexture) get(textures, texture), level);
    }

    public static int checkFramebufferStatus(int target) {
        return gl.checkFramebufferStatus(target);
    }

    public static int getFramebufferAttachmentParameteri(int target, int attachment, int pname) {
        JSObject v = gl.getFramebufferAttachmentParameter(target, attachment, pname);
        return v == null ? 0 : jsInt(v);
    }

    @org.teavm.jso.JSBody(params = "v", script = "return typeof v === 'number' ? v : (v ? 1 : 0);")
    private static native int jsInt(JSObject v);

    public static void blitFramebuffer(int sx0, int sy0, int sx1, int sy1, int dx0, int dy0, int dx1, int dy1,
            int mask, int filter) {
        gl.blitFramebuffer(sx0, sy0, sx1, sy1, dx0, dy0, dx1, dy1, mask, filter);
    }

    // ------------------------------------------------------------------ viewport / clear / misc

    public static void viewport(int x, int y, int w, int h) {
        viewport[0] = x;
        viewport[1] = y;
        viewport[2] = w;
        viewport[3] = h;
        gl.viewport(x, y, w, h);
    }

    public static int getInteger(int pname) {
        switch (pname) {
            case 32873: return boundTexture[activeTexture]; // GL_TEXTURE_BINDING_2D
            case 36006: return drawFramebuffer;
            case 36010: return readFramebuffer;
            case 34016: return GLConst.TEXTURE0 + activeTexture;
            case 35725: return currentProgram;
            case 34964: return arrayBufferBinding;
            case 2978: return viewport[0]; // GL_VIEWPORT (first component)
            case 3379: return intParams.getOrDefault(3379, 4096);
            case 34930: return 16; // GL_MAX_TEXTURE_IMAGE_UNITS
            case 34018: return 3; // GL_MAX_TEXTURE_UNITS
            default:
                try {
                    return gl.getParameteri(pname);
                } catch (Throwable t) {
                    return 0;
                }
        }
    }

    public static String getString(int name) {
        switch (name) {
            case 7936: return "TeaFabric";
            case 7937: {
                String r = gl.getParameterString(WebGL2RenderingContext.RENDERER);
                return "WebGL 2 (" + r + ")";
            }
            case 7938: return "2.1 TeaFabric fixed-function emulation on WebGL 2.0";
            case 7939: return "GL_ARB_framebuffer_object GL_EXT_framebuffer_blit";
            case 35724: return "1.20";
            default: return "";
        }
    }

    // ------------------------------------------------------------------ attribute stack (subset)

    private static final List<Object[]> attribStack = new ArrayList<>();

    public static void pushAttrib(int mask) {
        TexEnv[] envs = new TexEnv[3];
        for (int u = 0; u < 3; u++) {
            envs[u] = new TexEnv();
            envs[u].copyFrom(texEnv[u]);
        }
        attribStack.add(new Object[] {texture2D.clone(), envs, boundTexture.clone(), activeTexture,
            new boolean[] {alphaTest, fog, lighting, lightEnabled[0], lightEnabled[1], blend, colorLogicOp},
            texGenMask.clone()});
    }

    public static void popAttrib() {
        if (attribStack.isEmpty()) {
            return;
        }
        Object[] s = attribStack.remove(attribStack.size() - 1);
        System.arraycopy((boolean[]) s[0], 0, texture2D, 0, 3);
        TexEnv[] envs = (TexEnv[]) s[1];
        int[] bound = (int[]) s[2];
        for (int u = 0; u < 3; u++) {
            texEnv[u].copyFrom(envs[u]);
            if (bound[u] != boundTexture[u]) {
                gl.activeTexture(GLConst.TEXTURE0 + u);
                boundTexture[u] = bound[u];
                gl.bindTexture(WebGL2RenderingContext.TEXTURE_2D, (WebGLTexture) get(textures, bound[u]));
            }
        }
        activeTexture = (Integer) s[3];
        gl.activeTexture(GLConst.TEXTURE0 + activeTexture);
        boolean[] f = (boolean[]) s[4];
        alphaTest = f[0];
        fog = f[1];
        lighting = f[2];
        lightEnabled[0] = f[3];
        lightEnabled[1] = f[4];
        blend = f[5];
        colorLogicOp = f[6];
        applyBlend();
        System.arraycopy((int[]) s[5], 0, texGenMask, 0, 3);
    }

    // ------------------------------------------------------------------ GLSL programs (GL 2.0)

    public static int createShader(int type) {
        int id = alloc(shaders, gl.createShader(type));
        while (shaderLogs.size() <= id) {
            shaderLogs.add("");
            shaderTypes.add(new int[1]);
        }
        shaderTypes.get(id)[0] = type;
        return id;
    }

    private static final Map<Integer, String> shaderAttributes = new HashMap<>();

    public static void shaderSource(int id, CharSequence source) {
        boolean vertex = shaderTypes.get(id)[0] == WebGL2RenderingContext.VERTEX_SHADER;
        String translated = GlslTranslator.translate(source.toString(), vertex);
        if (vertex) {
            String attr = GlslTranslator.firstAttribute(source.toString());
            if (attr != null) {
                shaderAttributes.put(id, attr);
            }
        }
        gl.shaderSource((WebGLShader) get(shaders, id), translated);
    }

    public static void compileShader(int id) {
        WebGLShader s = (WebGLShader) get(shaders, id);
        gl.compileShader(s);
        String log = gl.getShaderInfoLog(s);
        shaderLogs.set(id, log == null ? "" : log);
    }

    public static int getShaderi(int id, int pname) {
        WebGLShader s = (WebGLShader) get(shaders, id);
        if (pname == 35713) { // COMPILE_STATUS
            return gl.getShaderParameterb(s, WebGL2RenderingContext.COMPILE_STATUS) ? 1 : 0;
        }
        if (pname == 35716) { // INFO_LOG_LENGTH
            return shaderLogs.get(id).length();
        }
        return 0;
    }

    public static String getShaderInfoLog(int id) {
        return shaderLogs.get(id);
    }

    public static void deleteShader(int id) {
        JSObject s = get(shaders, id);
        if (s != null) {
            gl.deleteShader((WebGLShader) s);
            shaders.set(id, null);
        }
    }

    public static int createProgram() {
        if (programs.isEmpty()) {
            programs.add(null);
        }
        UserProgram p = new UserProgram();
        p.program = gl.createProgram();
        programs.add(p);
        return programs.size() - 1;
    }

    public static void attachShader(int program, int shader) {
        UserProgram p = programs.get(program);
        p.shaders.add(shader);
        gl.attachShader(p.program, (WebGLShader) get(shaders, shader));
        String attr = shaderAttributes.get(shader);
        if (attr != null) {
            p.firstAttribute = attr;
        }
    }

    public static void linkProgram(int program) {
        UserProgram p = programs.get(program);
        if (p.firstAttribute != null) {
            gl.bindAttribLocation(p.program, 0, p.firstAttribute);
        }
        gl.linkProgram(p.program);
        p.linked = gl.getProgramParameterb(p.program, WebGL2RenderingContext.LINK_STATUS);
        String log = gl.getProgramInfoLog(p.program);
        p.log = log == null ? "" : log;
        if (!p.linked) {
            LogSink.log(2, "WARN", "GLEmu", "Program link failed: " + p.log, null);
        }
    }

    public static int getProgrami(int program, int pname) {
        UserProgram p = programs.get(program);
        if (pname == 35714) { // LINK_STATUS
            return p.linked ? 1 : 0;
        }
        if (pname == 35716) {
            return p.log.length();
        }
        return 0;
    }

    public static String getProgramInfoLog(int program) {
        return programs.get(program).log;
    }

    public static void useProgram(int program) {
        currentProgram = program;
        if (program != 0) {
            useGlProgram(programs.get(program).program);
        }
    }

    public static void deleteProgram(int program) {
        UserProgram p = program > 0 && program < programs.size() ? programs.get(program) : null;
        if (p != null) {
            gl.deleteProgram(p.program);
            programs.set(program, null);
        }
        if (currentProgram == program) {
            currentProgram = 0;
        }
    }

    public static int getUniformLocation(int program, CharSequence name) {
        WebGLUniformLocation loc = gl.getUniformLocation(programs.get(program).program, name.toString());
        if (loc == null) {
            return -1;
        }
        uniformLocations.add(loc);
        return uniformLocations.size() - 1;
    }

    public static int getAttribLocation(int program, CharSequence name) {
        return gl.getAttribLocation(programs.get(program).program, name.toString());
    }

    private static WebGLUniformLocation loc(int location) {
        return location >= 0 && location < uniformLocations.size() ? uniformLocations.get(location) : null;
    }

    public static void uniform1i(int location, int v) {
        gl.uniform1i(loc(location), v);
    }

    private static float[] floats(FloatBuffer b) {
        float[] a = new float[b.remaining()];
        b.duplicate().get(a);
        return a;
    }

    private static int[] ints(IntBuffer b) {
        int[] a = new int[b.remaining()];
        b.duplicate().get(a);
        return a;
    }

    public static void uniformfv(int n, int location, FloatBuffer v) {
        WebGLUniformLocation l = loc(location);
        if (l == null) {
            return;
        }
        float[] a = floats(v);
        switch (n) {
            case 1: gl.uniform1fv(l, a); break;
            case 2: gl.uniform2fv(l, a); break;
            case 3: gl.uniform3fv(l, a); break;
            default: gl.uniform4fv(l, a); break;
        }
    }

    public static void uniformiv(int n, int location, IntBuffer v) {
        WebGLUniformLocation l = loc(location);
        if (l == null) {
            return;
        }
        int[] a = ints(v);
        switch (n) {
            case 1: gl.uniform1iv(l, a); break;
            case 2: gl.uniform2iv(l, a); break;
            case 3: gl.uniform3iv(l, a); break;
            default: gl.uniform4iv(l, a); break;
        }
    }

    public static void uniformMatrixfv(int n, int location, boolean transpose, FloatBuffer v) {
        WebGLUniformLocation l = loc(location);
        if (l == null) {
            return;
        }
        float[] a = floats(v);
        if (transpose) {
            a = transpose(a, n);
        }
        switch (n) {
            case 2: gl.uniformMatrix2fv(l, false, a); break;
            case 3: gl.uniformMatrix3fv(l, false, a); break;
            default: gl.uniformMatrix4fv(l, false, a); break;
        }
    }

    private static float[] transpose(float[] a, int n) {
        float[] r = new float[a.length];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                r[j * n + i] = a[i * n + j];
            }
        }
        return r;
    }

    // ------------------------------------------------------------------ helpers for LWJGL front-ends

    public static float[] readFloats(FloatBuffer b, int max) {
        int n = Math.min(max, b.remaining());
        float[] a = new float[Math.max(n, 4)];
        for (int i = 0; i < n; i++) {
            a[i] = b.get(b.position() + i);
        }
        if (n < 4 && max >= 4) {
            a[3] = n == 3 ? 1 : a[3];
        }
        return a;
    }

    @SuppressWarnings("unused")
    private static Float32Array unusedF32;
    @SuppressWarnings("unused")
    private static Int32Array unusedI32;
}
