package teafabric.web.gl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates the desktop GLSL 1.10/1.20 used by Minecraft's post-processing shaders to GLSL ES 3.00.
 * Minecraft's shaders only use attribute/varying, texture2D and gl_FragColor, so a token-level
 * rewrite is sufficient.
 */
final class GlslTranslator {
    private static final Pattern VERSION = Pattern.compile("^\\s*#version[^\\n]*\\n?", Pattern.MULTILINE);
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(void)?\\s*\\)");
    /** Uniform names GLEmu sets for every program it translated (see GLEmu.applyAlphaTest). */
    static final String ALPHA_FUNC = "tf_AlphaFunc";
    static final String ALPHA_REF = "tf_AlphaRef";
    private static final String ALPHA_TEST = "\nuniform int " + ALPHA_FUNC + ";\nuniform float " + ALPHA_REF + ";\n"
            + "void main() {\n"
            + "    tf_userMain();\n"
            + "    float tf_a = tf_FragColor.a;\n"
            + "    bool tf_pass = " + ALPHA_FUNC + " == 0 || " + ALPHA_FUNC + " == 519\n"
            + "        || (" + ALPHA_FUNC + " == 516 && tf_a > " + ALPHA_REF + ")\n"
            + "        || (" + ALPHA_FUNC + " == 518 && tf_a >= " + ALPHA_REF + ")\n"
            + "        || (" + ALPHA_FUNC + " == 513 && tf_a < " + ALPHA_REF + ")\n"
            + "        || (" + ALPHA_FUNC + " == 515 && tf_a <= " + ALPHA_REF + ")\n"
            + "        || (" + ALPHA_FUNC + " == 514 && tf_a == " + ALPHA_REF + ")\n"
            + "        || (" + ALPHA_FUNC + " == 517 && tf_a != " + ALPHA_REF + ");\n"
            + "    if (!tf_pass) discard;\n"
            + "}\n";
    private static final Pattern ATTRIBUTE = Pattern.compile("\\battribute\\s+\\w+\\s+(\\w+)\\s*;");

    private GlslTranslator() {
    }

    static String firstAttribute(String source) {
        Matcher m = ATTRIBUTE.matcher(source);
        return m.find() ? m.group(1) : null;
    }

    static String translate(String source, boolean vertex) {
        String s = VERSION.matcher(source).replaceAll("");
        s = s.replaceAll("\\btexture2DLod\\b", "textureLod");
        s = s.replaceAll("\\btexture2D\\b", "texture");
        s = s.replaceAll("\\bshadow2D\\b", "texture");
        StringBuilder header = new StringBuilder("#version 300 es\nprecision highp float;\nprecision highp int;\n");
        if (vertex) {
            s = s.replaceAll("\\battribute\\b", "in");
            s = s.replaceAll("\\bvarying\\b", "out");
        } else {
            s = s.replaceAll("\\bvarying\\b", "in");
            s = s.replaceAll("\\bgl_FragData\\s*\\[\\s*0\\s*\\]", "tf_FragColor");
            s = s.replaceAll("\\bgl_FragColor\\b", "tf_FragColor");
            header.append("out vec4 tf_FragColor;\n");
            Matcher main = MAIN.matcher(s);
            if (main.find()) {
                // Desktop GL applies the fixed-function alpha test after the fragment shader (Sodium's cutout
                // blocks rely on it: leaves, grass, flowers); WebGL has none, so it is added here.
                s = main.replaceFirst("void tf_userMain()") + ALPHA_TEST;
            }
        }
        return header + s;
    }
}
