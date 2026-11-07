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
        }
        return header + s;
    }
}
