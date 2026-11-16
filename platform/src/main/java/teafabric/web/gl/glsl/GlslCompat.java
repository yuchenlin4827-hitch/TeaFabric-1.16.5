package teafabric.web.gl.glsl;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import teafabric.web.gl.glsl.Ast.*;

/**
 * Translates desktop GLSL (1.10 to 4.x, compatibility profile included: what Iris shader packs, Minecraft
 * and Sodium use) to GLSL ES 3.00 for WebGL 2.
 *
 * <p>Compatibility built-ins become declarations the GL emulation feeds: attributes {@code tf_Vertex},
 * {@code tf_Color}, {@code tf_Normal}, {@code tf_MultiTexCoordN}; uniforms {@code tf_ModelViewMatrix},
 * {@code tf_ProjectionMatrix}, {@code tf_TextureMatrix[8]}, fog parameters; varyings {@code tf_FrontColor},
 * {@code tf_TexCoordN}; fragment outputs {@code tf_FragDataN} at location N. Fragment shaders get the
 * fixed-function alpha test (uniforms {@code tf_AlphaFunc}, {@code tf_AlphaRef}) applied to output 0.
 */
public final class GlslCompat {
    public static final String ALPHA_FUNC = "tf_AlphaFunc";
    public static final String ALPHA_REF = "tf_AlphaRef";

    private static final String HEADER = "#version 300 es\n"
            + "precision highp float;\nprecision highp int;\nprecision highp sampler2D;\n"
            + "precision highp sampler3D;\nprecision highp samplerCube;\nprecision highp sampler2DShadow;\n"
            + "precision highp samplerCubeShadow;\nprecision highp sampler2DArray;\n"
            + "precision highp sampler2DArrayShadow;\nprecision highp isampler2D;\nprecision highp usampler2D;\n"
            + "precision highp isampler3D;\nprecision highp usampler3D;\n";

    public static final class Result {
        public String source;
        /** Uses compatibility built-ins (fixed-function state must be supplied). */
        public boolean compat;
        /** Built-in declarations by name, e.g. "tf_Vertex" -> "in vec4". */
        public Map<String, String> builtins;
        /** Varyings: outputs of a vertex shader, inputs of a fragment shader (name -> "[interp] type"). */
        public Map<String, String> varyings;
        /** Interface variables renamed because GLSL ES reserves their names (original -> new). */
        public Map<String, String> renamed;
        /** Fragment shaders: number of colour outputs (highest location + 1). */
        public int colorOutputs;
    }

    private GlslCompat() {
    }

    /**
     * @param extraOutputs for a vertex shader: varyings the fragment shader reads but this shader does not write
     *     (name -> "[interp] type"), declared so the program links; may be null
     */
    public static Result translate(String source, boolean vertex, Map<String, String> extraOutputs) {
        Unit unit = Parser.parse(Preprocessor.process(source));
        Translator t = new Translator(unit, vertex);
        t.run();
        StringBuilder head = new StringBuilder(HEADER);
        for (Map.Entry<String, String> b : t.builtinDecls.entrySet()) {
            String decl = b.getValue();
            String name = b.getKey();
            if (decl.endsWith("]")) { // "uniform mat4[8]"
                int k = decl.indexOf('[');
                head.append(decl, 0, k).append(' ').append(name).append(decl.substring(k)).append(";\n");
            } else {
                head.append(decl).append(' ').append(name).append(";\n");
            }
        }
        if (extraOutputs != null) {
            for (Map.Entry<String, String> e : extraOutputs.entrySet()) {
                String interp = e.getValue();
                String type = interp;
                String q = "";
                int sp = interp.lastIndexOf(' ');
                if (sp >= 0) {
                    q = interp.substring(0, sp + 1);
                    type = interp.substring(sp + 1);
                }
                int br = type.indexOf('[');
                String dims = br >= 0 ? type.substring(br) : "";
                head.append(q).append("out ").append(br >= 0 ? type.substring(0, br) : type).append(' ')
                        .append(e.getKey()).append(dims).append(";\n");
            }
        }
        int colorOutputs = 0;
        String output0 = null;
        if (!vertex) {
            for (Map.Entry<Integer, String> f : t.fragData.entrySet()) {
                head.append("layout(location = ").append(f.getKey()).append(") out vec4 ").append(f.getValue())
                        .append(";\n");
                colorOutputs = Math.max(colorOutputs, f.getKey() + 1);
                if (f.getKey() == 0) {
                    output0 = f.getValue();
                }
            }
            // user outputs (GLSL 1.30+): GLSL ES needs explicit locations when there are several
            int count = 0;
            for (Decl d : t.userOutputs) {
                count += d.vars.size();
            }
            boolean[] used = new boolean[16];
            for (Decl d : t.userOutputs) {
                Integer loc = location(d.q.layout);
                if (loc != null && loc < 16) {
                    used[loc] = true;
                }
            }
            for (Decl d : t.userOutputs) {
                Integer loc = location(d.q.layout);
                if (loc == null && count > 1) {
                    if (d.vars.size() != 1) {
                        throw new GlslException(d.line, "several fragment outputs in one declaration");
                    }
                    int l = 0;
                    while (used[l]) {
                        l++;
                    }
                    used[l] = true;
                    loc = l;
                    d.q.layout = d.q.layout == null ? "location = " + l : d.q.layout + ", location = " + l;
                } else if (loc == null) {
                    loc = 0;
                }
                Declarator v = d.vars.get(0);
                int size = v.type.isArray() ? Math.max(1, v.type.array) : 1;
                colorOutputs = Math.max(colorOutputs, loc + size);
                if (loc == 0) {
                    output0 = v.name + (v.type.isArray() ? "[0]" : "");
                }
            }
        }
        Printer p = new Printer();
        boolean alphaTest = !vertex && output0 != null;
        for (Stmt s : unit.decls) {
            if (s instanceof Verbatim && ((Verbatim) s).text.startsWith("precision")) {
                continue; // replaced by the header's (highp everywhere, so both stages agree)
            }
            if (alphaTest && s instanceof Function && ((Function) s).name.equals("main")) {
                ((Function) s).name = "tf_userMain";
            }
            p.topLevel(s);
        }
        StringBuilder src = head.append('\n').append(p.result());
        if (alphaTest) {
            // Desktop GL applies the fixed-function alpha test after the fragment shader (cutout blocks, and
            // shader packs' alphaTest directives rely on it); WebGL has none, so it is added here.
            src.append("uniform int ").append(ALPHA_FUNC).append(";\nuniform float ").append(ALPHA_REF).append(";\n")
                    .append("void main() {\n    tf_userMain();\n    float tf_a = ").append(output0).append(".a;\n")
                    .append("    bool tf_pass = ").append(ALPHA_FUNC).append(" == 0 || ").append(ALPHA_FUNC)
                    .append(" == 519\n")
                    .append("        || (").append(ALPHA_FUNC).append(" == 516 && tf_a > ").append(ALPHA_REF).append(")\n")
                    .append("        || (").append(ALPHA_FUNC).append(" == 518 && tf_a >= ").append(ALPHA_REF).append(")\n")
                    .append("        || (").append(ALPHA_FUNC).append(" == 513 && tf_a < ").append(ALPHA_REF).append(")\n")
                    .append("        || (").append(ALPHA_FUNC).append(" == 515 && tf_a <= ").append(ALPHA_REF).append(")\n")
                    .append("        || (").append(ALPHA_FUNC).append(" == 514 && tf_a == ").append(ALPHA_REF).append(")\n")
                    .append("        || (").append(ALPHA_FUNC).append(" == 517 && tf_a != ").append(ALPHA_REF).append(");\n")
                    .append("    if (!tf_pass) discard;\n}\n");
        }
        Result r = new Result();
        r.source = src.toString();
        r.compat = t.compat;
        r.builtins = t.builtinDecls;
        Map<String, String> varyings = new LinkedHashMap<>(t.varyings);
        for (Map.Entry<String, String> b : t.builtinDecls.entrySet()) {
            String d = b.getValue();
            if (d.startsWith(vertex ? "out " : "in ")) {
                varyings.put(b.getKey(), d.substring(d.indexOf(' ') + 1));
            }
        }
        r.varyings = varyings;
        r.renamed = t.renamedInterface;
        r.colorOutputs = colorOutputs;
        return r;
    }

    private static Integer location(String layout) {
        if (layout == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("location\\s*=\\s*(\\d+)").matcher(layout);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    /** Varyings the fragment shader reads that the vertex shader does not write. */
    public static Map<String, String> missingVaryings(Result vs, Result fs) {
        Map<String, String> missing = new HashMap<>();
        for (Map.Entry<String, String> e : fs.varyings.entrySet()) {
            if (!vs.varyings.containsKey(e.getKey())) {
                missing.put(e.getKey(), e.getValue());
            }
        }
        return missing;
    }

}
