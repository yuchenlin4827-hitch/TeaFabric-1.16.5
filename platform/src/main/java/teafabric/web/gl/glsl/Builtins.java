package teafabric.web.gl.glsl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Tables of desktop GLSL built-ins and of names GLSL ES 3.00 reserves. */
final class Builtins {
    private Builtins() {
    }

    /** Float-only "genType" functions: integer arguments are converted. */
    static final Set<String> GEN_FLOAT = set("radians", "degrees", "sin", "cos", "tan", "asin", "acos", "atan",
            "sinh", "cosh", "tanh", "asinh", "acosh", "atanh", "pow", "exp", "log", "exp2", "log2", "sqrt",
            "inversesqrt", "floor", "ceil", "fract", "trunc", "round", "roundEven", "mod", "step", "smoothstep",
            "normalize", "faceforward", "reflect", "dFdx", "dFdy", "fwidth", "mix");
    /** genType functions that also have integer overloads. */
    static final Set<String> GEN_INT_OK = set("abs", "sign", "min", "max", "clamp");
    static final Set<String> RELATIONAL = set("lessThan", "lessThanEqual", "greaterThan", "greaterThanEqual",
            "equal", "notEqual");

    /** Desktop texture functions and their GLSL ES 3.00 names; shadow lookups return vec4 on the desktop. */
    static final Map<String, String> TEXTURE = new HashMap<>();
    static final Set<String> LEGACY_SHADOW = set("shadow2D", "shadow2DLod", "shadow2DProj", "shadow2DProjLod",
            "shadow2DGradARB", "shadow2DEXT", "shadow2DProjEXT");

    static {
        String[][] pairs = {
            {"texture", "texture"}, {"textureLod", "textureLod"}, {"textureProj", "textureProj"},
            {"textureProjLod", "textureProjLod"}, {"textureGrad", "textureGrad"}, {"textureProjGrad", "textureProjGrad"},
            {"textureOffset", "textureOffset"}, {"textureLodOffset", "textureLodOffset"},
            {"textureGradOffset", "textureGradOffset"}, {"textureProjOffset", "textureProjOffset"},
            {"texture2D", "texture"}, {"texture2DLod", "textureLod"}, {"texture2DProj", "textureProj"},
            {"texture2DProjLod", "textureProjLod"}, {"texture2DGrad", "textureGrad"},
            {"texture2DGradARB", "textureGrad"}, {"texture2DGradEXT", "textureGrad"},
            {"texture2DLodEXT", "textureLod"}, {"texture2DLodARB", "textureLod"},
            {"texture2DProjLodARB", "textureProjLod"}, {"texture3D", "texture"}, {"texture3DLod", "textureLod"},
            {"texture3DProj", "textureProj"}, {"textureCube", "texture"}, {"textureCubeLod", "textureLod"},
            {"textureCubeGradARB", "textureGrad"}, {"texture2DRect", "texture"},
            {"shadow2D", "texture"}, {"shadow2DLod", "textureLod"}, {"shadow2DProj", "textureProj"},
            {"shadow2DProjLod", "textureProjLod"}, {"shadow2DGradARB", "textureGrad"}, {"shadow2DEXT", "texture"},
            {"shadow2DProjEXT", "textureProj"},
        };
        for (String[] p : pairs) {
            TEXTURE.put(p[0], p[1]);
        }
    }

    /** Functions whose last argument is an integer offset (not converted). */
    static final Set<String> OFFSET_LAST = set("textureOffset", "textureLodOffset", "textureGradOffset",
            "textureProjOffset", "texelFetchOffset");

    /**
     * Names a desktop shader may use as identifiers but GLSL ES 3.00 reserves or defines as built-in functions;
     * user declarations with these names are renamed.
     */
    static final Set<String> ES_RESERVED = set(
            // keywords reserved by GLSL ES 3.00
            "attribute", "varying", "coherent", "volatile", "restrict", "readonly", "writeonly", "resource",
            "atomic_uint", "noperspective", "patch", "sample", "subroutine", "common", "partition", "active", "asm",
            "class", "union", "enum", "typedef", "template", "this", "goto", "inline", "noinline", "public",
            "static", "extern", "external", "interface", "long", "short", "double", "half", "fixed", "unsigned",
            "superp", "input", "output", "hvec2", "hvec3", "hvec4", "dvec2", "dvec3", "dvec4", "fvec2", "fvec3",
            "fvec4", "sampler3DRect", "filter", "sizeof", "cast", "namespace", "using", "precise", "buffer",
            "shared", "image2D", "image3D",
            // built-in functions of GLSL ES 3.00 that desktop GLSL 1.10/1.20 does not have
            "texture", "textureLod", "textureProj", "textureProjLod", "textureGrad", "textureProjGrad",
            "textureOffset", "textureLodOffset", "textureGradOffset", "textureProjOffset", "textureProjLodOffset",
            "textureProjGradOffset", "texelFetch", "texelFetchOffset", "textureSize", "inverse", "transpose",
            "determinant", "outerProduct", "round", "roundEven", "trunc", "isnan", "isinf", "sinh", "cosh",
            "tanh", "asinh", "acosh", "atanh", "modf", "floatBitsToInt", "floatBitsToUint", "intBitsToFloat",
            "uintBitsToFloat", "packSnorm2x16", "unpackSnorm2x16", "packUnorm2x16", "unpackUnorm2x16",
            "packHalf2x16", "unpackHalf2x16");

    static Set<String> set(String... s) {
        return new HashSet<>(Arrays.asList(s));
    }
}
