package teafabric.web.gl;

import java.util.HashMap;
import java.util.Map;
import org.teavm.jso.webgl.WebGL2RenderingContext;
import org.teavm.jso.webgl.WebGLProgram;
import org.teavm.jso.webgl.WebGLShader;
import org.teavm.jso.webgl.WebGLUniformLocation;
import teafabric.web.log.LogSink;

/**
 * Just-in-time specialisation of the OpenGL 1.x fixed-function pipeline.
 *
 * <p>Minecraft 1.16.5 renders through the legacy pipeline (matrix stacks, client arrays,
 * glTexEnv combiners, alpha test, fog, two directional lights, eye-linear texgen). WebGL 2 has
 * none of it. Every distinct combination of that state is translated on first use into a
 * dedicated GLSL ES 3.00 program with all branches resolved at generation time, compiled by the
 * browser's GPU driver and cached by its state key; subsequent draws with the same state reuse
 * the program. Only the state that changes per draw (matrices, colours, fog distances, light
 * vectors) is passed as uniforms.
 */
final class ShaderJit {
    static final int A_POS = 0;
    static final int A_COLOR = 1;
    static final int A_NORMAL = 2;
    static final int A_TEX0 = 3;
    static final int UNITS = 3;

    /** Snapshot of the state that selects a program variant. */
    static final class Variant {
        int texMask;
        final TexEnv[] env = {new TexEnv(), new TexEnv(), new TexEnv()};
        int alphaFunc;
        int fogMode;
        boolean lighting;
        boolean light0;
        boolean light1;
        final int[] texGenMask = new int[UNITS];
        final int[][] texGenMode = new int[UNITS][4];

        String key() {
            StringBuilder sb = new StringBuilder(48);
            sb.append('t').append(texMask);
            for (int u = 0; u < UNITS; u++) {
                if ((texMask & (1 << u)) != 0) {
                    sb.append('|').append(u).append(':');
                    env[u].appendKey(sb);
                    if (texGenMask[u] != 0) {
                        sb.append('g').append(texGenMask[u]);
                        for (int c = 0; c < 4; c++) {
                            if ((texGenMask[u] & (1 << c)) != 0) {
                                sb.append(',').append(texGenMode[u][c]);
                            }
                        }
                    }
                }
            }
            sb.append("|a").append(alphaFunc).append("|f").append(fogMode);
            if (lighting) {
                sb.append("|l").append(light0 ? '1' : '0').append(light1 ? '1' : '0');
            }
            return sb.toString();
        }
    }

    /** A compiled variant and its uniform locations. */
    static final class Program {
        WebGLProgram program;
        WebGLUniformLocation mv;
        WebGLUniformLocation proj;
        WebGLUniformLocation normal;
        final WebGLUniformLocation[] texMat = new WebGLUniformLocation[UNITS];
        final WebGLUniformLocation[] envColor = new WebGLUniformLocation[UNITS];
        final WebGLUniformLocation[][] texGenPlane = new WebGLUniformLocation[UNITS][4];
        WebGLUniformLocation alphaRef;
        WebGLUniformLocation fogColor;
        WebGLUniformLocation fogParams;
        WebGLUniformLocation lightAmbient;
        final WebGLUniformLocation[] lightDir = new WebGLUniformLocation[2];
        final WebGLUniformLocation[] lightDiffuse = new WebGLUniformLocation[2];
        Variant variant;
        // versions of the uniform data last uploaded into this program
        int mvVersion = -1;
        int projVersion = -1;
        final int[] texVersion = {-1, -1, -1};
        // values last uploaded into this program (uniforms keep their values per program): unchanged
        // uniforms are not uploaded again. NaN never compares equal, so the first upload always happens.
        final float[] cMv = nan(16);
        final float[] cProj = nan(16);
        final float[] cNormal = nan(9);
        final float[][] cTexMat = {nan(16), nan(16), nan(16)};
        final float[][] cEnvColor = {nan(4), nan(4), nan(4)};
        final float[][][] cTexGen = {{nan(4), nan(4), nan(4), nan(4)}, {nan(4), nan(4), nan(4), nan(4)},
                {nan(4), nan(4), nan(4), nan(4)}};
        final float[] cAlphaRef = nan(1);
        final float[] cFogColor = nan(4);
        final float[] cFogParams = nan(3);
        final float[] cLightAmbient = nan(3);
        final float[][] cLightDir = {nan(3), nan(3)};
        final float[][] cLightDiffuse = {nan(3), nan(3)};

        private static float[] nan(int n) {
            float[] a = new float[n];
            java.util.Arrays.fill(a, Float.NaN);
            return a;
        }
    }

    private final WebGL2RenderingContext gl;
    private final Map<String, Program> cache = new HashMap<>();
    private int compiled;

    ShaderJit(WebGL2RenderingContext gl) {
        this.gl = gl;
    }

    int compiledCount() {
        return compiled;
    }

    Program get(Variant v) {
        String key = v.key();
        Program p = cache.get(key);
        if (p == null) {
            p = compile(v, key);
            cache.put(key, p);
        }
        return p;
    }

    private Program compile(Variant v, String key) {
        String vs = vertexSource(v);
        String fs = fragmentSource(v);
        WebGLProgram prog = gl.createProgram();
        WebGLShader vsh = shader(WebGL2RenderingContext.VERTEX_SHADER, vs, key);
        WebGLShader fsh = shader(WebGL2RenderingContext.FRAGMENT_SHADER, fs, key);
        gl.attachShader(prog, vsh);
        gl.attachShader(prog, fsh);
        gl.linkProgram(prog);
        if (!gl.getProgramParameterb(prog, WebGL2RenderingContext.LINK_STATUS)) {
            LogSink.log(3, "ERROR", "ShaderJit", "Link failed for variant " + key + ": "
                    + gl.getProgramInfoLog(prog), null);
        }
        gl.deleteShader(vsh);
        gl.deleteShader(fsh);
        Program p = new Program();
        p.program = prog;
        p.variant = copy(v);
        p.mv = gl.getUniformLocation(prog, "u_mv");
        p.proj = gl.getUniformLocation(prog, "u_proj");
        p.normal = gl.getUniformLocation(prog, "u_normal");
        p.alphaRef = gl.getUniformLocation(prog, "u_alphaRef");
        p.fogColor = gl.getUniformLocation(prog, "u_fogColor");
        p.fogParams = gl.getUniformLocation(prog, "u_fogParams");
        p.lightAmbient = gl.getUniformLocation(prog, "u_lightAmbient");
        for (int i = 0; i < 2; i++) {
            p.lightDir[i] = gl.getUniformLocation(prog, "u_lightDir" + i);
            p.lightDiffuse[i] = gl.getUniformLocation(prog, "u_lightDiffuse" + i);
        }
        gl.useProgram(prog);
        for (int u = 0; u < UNITS; u++) {
            p.texMat[u] = gl.getUniformLocation(prog, "u_texm" + u);
            p.envColor[u] = gl.getUniformLocation(prog, "u_envColor" + u);
            WebGLUniformLocation sampler = gl.getUniformLocation(prog, "u_tex" + u);
            if (sampler != null) {
                gl.uniform1i(sampler, u);
            }
            for (int c = 0; c < 4; c++) {
                p.texGenPlane[u][c] = gl.getUniformLocation(prog, "u_tg" + u + "_" + c);
            }
        }
        compiled++;
        LogSink.log(0, "DEBUG", "ShaderJit", "Compiled fixed-function variant #" + compiled + " [" + key + "]", null);
        return p;
    }

    private static Variant copy(Variant v) {
        Variant c = new Variant();
        c.texMask = v.texMask;
        for (int u = 0; u < UNITS; u++) {
            c.env[u].copyFrom(v.env[u]);
            c.texGenMask[u] = v.texGenMask[u];
            System.arraycopy(v.texGenMode[u], 0, c.texGenMode[u], 0, 4);
        }
        c.alphaFunc = v.alphaFunc;
        c.fogMode = v.fogMode;
        c.lighting = v.lighting;
        c.light0 = v.light0;
        c.light1 = v.light1;
        return c;
    }

    private WebGLShader shader(int type, String source, String key) {
        WebGLShader s = gl.createShader(type);
        gl.shaderSource(s, source);
        gl.compileShader(s);
        if (!gl.getShaderParameterb(s, WebGL2RenderingContext.COMPILE_STATUS)) {
            LogSink.log(3, "ERROR", "ShaderJit", "Compile failed for variant " + key + ": "
                    + gl.getShaderInfoLog(s) + "\n" + source, null);
        }
        return s;
    }

    // ------------------------------------------------------------------ code generation

    private static String vertexSource(Variant v) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("#version 300 es\nprecision highp float;\n");
        sb.append("layout(location=0) in vec4 a_pos;\n");
        sb.append("layout(location=1) in vec4 a_color;\n");
        sb.append("layout(location=2) in vec3 a_normal;\n");
        for (int u = 0; u < UNITS; u++) {
            sb.append("layout(location=").append(A_TEX0 + u).append(") in vec4 a_tex").append(u).append(";\n");
        }
        sb.append("uniform mat4 u_mv;\nuniform mat4 u_proj;\n");
        sb.append("out vec4 v_color;\nout float v_fogDist;\n");
        for (int u = 0; u < UNITS; u++) {
            if ((v.texMask & (1 << u)) != 0) {
                sb.append("uniform mat4 u_texm").append(u).append(";\nout vec4 v_tc").append(u).append(";\n");
                for (int c = 0; c < 4; c++) {
                    if ((v.texGenMask[u] & (1 << c)) != 0) {
                        sb.append("uniform vec4 u_tg").append(u).append('_').append(c).append(";\n");
                    }
                }
            }
        }
        if (v.lighting) {
            sb.append("uniform mat3 u_normal;\nuniform vec3 u_lightAmbient;\n");
            sb.append("uniform vec3 u_lightDir0;\nuniform vec3 u_lightDiffuse0;\n");
            sb.append("uniform vec3 u_lightDir1;\nuniform vec3 u_lightDiffuse1;\n");
        }
        sb.append("void main() {\n");
        sb.append("  vec4 eye = u_mv * a_pos;\n");
        sb.append("  gl_Position = u_proj * eye;\n");
        sb.append("  v_fogDist = abs(eye.z);\n");
        if (v.lighting) {
            // GL_COLOR_MATERIAL with GL_AMBIENT_AND_DIFFUSE: the vertex colour is the material.
            sb.append("  vec3 n = normalize(u_normal * a_normal);\n");
            sb.append("  vec3 lit = u_lightAmbient;\n");
            if (v.light0) {
                sb.append("  lit += max(dot(n, u_lightDir0), 0.0) * u_lightDiffuse0;\n");
            }
            if (v.light1) {
                sb.append("  lit += max(dot(n, u_lightDir1), 0.0) * u_lightDiffuse1;\n");
            }
            sb.append("  v_color = vec4(clamp(a_color.rgb * lit, 0.0, 1.0), a_color.a);\n");
        } else {
            sb.append("  v_color = a_color;\n");
        }
        for (int u = 0; u < UNITS; u++) {
            if ((v.texMask & (1 << u)) == 0) {
                continue;
            }
            sb.append("  vec4 tc").append(u).append(" = a_tex").append(u).append(";\n");
            String[] comp = {"x", "y", "z", "w"};
            for (int c = 0; c < 4; c++) {
                if ((v.texGenMask[u] & (1 << c)) == 0) {
                    continue;
                }
                int mode = v.texGenMode[u][c];
                String plane = "u_tg" + u + "_" + c;
                String src = mode == GLConst.OBJECT_LINEAR ? "a_pos" : "eye";
                sb.append("  tc").append(u).append('.').append(comp[c]).append(" = dot(").append(src)
                        .append(", ").append(plane).append(");\n");
            }
            sb.append("  v_tc").append(u).append(" = u_texm").append(u).append(" * tc").append(u).append(";\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    private static String fragmentSource(Variant v) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("#version 300 es\nprecision highp float;\n");
        sb.append("in vec4 v_color;\nin float v_fogDist;\nout vec4 fragColor;\n");
        for (int u = 0; u < UNITS; u++) {
            if ((v.texMask & (1 << u)) != 0) {
                sb.append("uniform sampler2D u_tex").append(u).append(";\nin vec4 v_tc").append(u).append(";\n");
                sb.append("uniform vec4 u_envColor").append(u).append(";\n");
            }
        }
        if (v.alphaFunc != 0) {
            sb.append("uniform float u_alphaRef;\n");
        }
        if (v.fogMode != 0) {
            sb.append("uniform vec4 u_fogColor;\nuniform vec3 u_fogParams;\n");
        }
        sb.append("void main() {\n  vec4 primary = v_color;\n  vec4 prev = primary;\n");
        for (int u = 0; u < UNITS; u++) {
            if ((v.texMask & (1 << u)) == 0) {
                continue;
            }
            sb.append("  vec4 t").append(u).append(" = texture(u_tex").append(u).append(", v_tc").append(u)
                    .append(".xy / v_tc").append(u).append(".w);\n");
            sb.append("  prev = ").append(stage(u, v.env[u])).append(";\n");
        }
        if (v.alphaFunc != 0) {
            String cond = alphaCondition(v.alphaFunc);
            if (cond != null) {
                sb.append("  if (!(").append(cond).append(")) discard;\n");
            }
        }
        if (v.fogMode != 0) {
            // u_fogParams = (start, end, density)
            switch (v.fogMode) {
                case GLConst.LINEAR:
                    sb.append("  float fog = clamp((u_fogParams.y - v_fogDist) / (u_fogParams.y - u_fogParams.x),"
                            + " 0.0, 1.0);\n");
                    break;
                case GLConst.EXP:
                    sb.append("  float fog = clamp(exp(-u_fogParams.z * v_fogDist), 0.0, 1.0);\n");
                    break;
                default:
                    sb.append("  float fd = u_fogParams.z * v_fogDist;\n"
                            + "  float fog = clamp(exp(-fd * fd), 0.0, 1.0);\n");
                    break;
            }
            sb.append("  prev.rgb = mix(u_fogColor.rgb, prev.rgb, fog);\n");
        }
        sb.append("  fragColor = prev;\n}\n");
        return sb.toString();
    }

    private static String alphaCondition(int func) {
        switch (func) {
            case GLConst.NEVER: return "false";
            case GLConst.LESS: return "prev.a < u_alphaRef";
            case GLConst.EQUAL: return "prev.a == u_alphaRef";
            case GLConst.LEQUAL: return "prev.a <= u_alphaRef";
            case GLConst.GREATER: return "prev.a > u_alphaRef";
            case GLConst.NOTEQUAL: return "prev.a != u_alphaRef";
            case GLConst.GEQUAL: return "prev.a >= u_alphaRef";
            default: return null;
        }
    }

    /** GLSL expression for one texture environment stage (OpenGL 1.3 + ARB_texture_env_combine). */
    private static String stage(int u, TexEnv env) {
        String t = "t" + u;
        switch (env.p[TexEnv.MODE]) {
            case GLConst.REPLACE:
                return t;
            case GLConst.DECAL:
                return "vec4(mix(prev.rgb, " + t + ".rgb, " + t + ".a), prev.a)";
            case GLConst.ADD:
                return "vec4(min(prev.rgb + " + t + ".rgb, 1.0), prev.a * " + t + ".a)";
            case GLConst.BLEND_ENV:
                return "vec4(mix(prev.rgb, u_envColor" + u + ".rgb, " + t + ".rgb), prev.a * " + t + ".a)";
            case GLConst.COMBINE:
                return "vec4(" + combine(u, env, false) + ", " + combine(u, env, true) + ")";
            case GLConst.MODULATE:
            default:
                return "prev * " + t;
        }
    }

    private static String combine(int u, TexEnv env, boolean alpha) {
        int func = env.p[alpha ? TexEnv.COMBINE_ALPHA : TexEnv.COMBINE_RGB];
        String a0 = arg(u, env, 0, alpha);
        String a1 = arg(u, env, 1, alpha);
        String a2 = arg(u, env, 2, alpha);
        String expr;
        switch (func) {
            case GLConst.REPLACE: expr = a0; break;
            case GLConst.ADD: expr = "(" + a0 + " + " + a1 + ")"; break;
            case GLConst.ADD_SIGNED: expr = "(" + a0 + " + " + a1 + " - 0.5)"; break;
            case GLConst.INTERPOLATE: expr = "mix(" + a1 + ", " + a0 + ", " + a2 + ")"; break;
            case GLConst.SUBTRACT: expr = "(" + a0 + " - " + a1 + ")"; break;
            case GLConst.MODULATE:
            default: expr = "(" + a0 + " * " + a1 + ")"; break;
        }
        return "clamp(" + expr + ", 0.0, 1.0)";
    }

    private static String arg(int u, TexEnv env, int i, boolean alpha) {
        int src = env.p[(alpha ? TexEnv.SRC0_ALPHA : TexEnv.SRC0_RGB) + i];
        int op = env.p[(alpha ? TexEnv.OP0_ALPHA : TexEnv.OP0_RGB) + i];
        String v;
        switch (src) {
            case GLConst.PRIMARY_COLOR: v = "primary"; break;
            case GLConst.PREVIOUS: v = "prev"; break;
            case GLConst.CONSTANT: v = "u_envColor" + u; break;
            case GLConst.TEXTURE: v = "t" + u; break;
            default:
                if (src >= GLConst.TEXTURE0 && src < GLConst.TEXTURE0 + UNITS) {
                    v = "t" + (src - GLConst.TEXTURE0);
                } else {
                    v = "t" + u;
                }
        }
        if (alpha) {
            return op == GLConst.ONE_MINUS_SRC_ALPHA ? "(1.0 - " + v + ".a)" : v + ".a";
        }
        switch (op) {
            case GLConst.ONE_MINUS_SRC_COLOR: return "(vec3(1.0) - " + v + ".rgb)";
            case GLConst.SRC_ALPHA: return "vec3(" + v + ".a)";
            case GLConst.ONE_MINUS_SRC_ALPHA: return "vec3(1.0 - " + v + ".a)";
            case GLConst.SRC_COLOR:
            default: return v + ".rgb";
        }
    }
}
