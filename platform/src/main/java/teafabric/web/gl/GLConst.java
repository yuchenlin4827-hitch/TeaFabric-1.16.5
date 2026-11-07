package teafabric.web.gl;

/** OpenGL enum values used by the emulation (names as in the GL specification). */
final class GLConst {
    private GLConst() {
    }

    // primitive types
    static final int POINTS = 0;
    static final int LINES = 1;
    static final int LINE_LOOP = 2;
    static final int LINE_STRIP = 3;
    static final int TRIANGLES = 4;
    static final int TRIANGLE_STRIP = 5;
    static final int TRIANGLE_FAN = 6;
    static final int QUADS = 7;
    static final int QUAD_STRIP = 8;
    static final int POLYGON = 9;

    // data types
    static final int BYTE = 5120;
    static final int UNSIGNED_BYTE = 5121;
    static final int SHORT = 5122;
    static final int UNSIGNED_SHORT = 5123;
    static final int INT = 5124;
    static final int UNSIGNED_INT = 5125;
    static final int FLOAT = 5126;
    static final int DOUBLE = 5130;

    // capabilities
    static final int CULL_FACE = 2884;
    static final int LIGHTING = 2896;
    static final int COLOR_MATERIAL = 2903;
    static final int FOG = 2912;
    static final int DEPTH_TEST = 2929;
    static final int STENCIL_TEST = 2960;
    static final int NORMALIZE = 2977;
    static final int ALPHA_TEST = 3008;
    static final int DITHER = 3024;
    static final int BLEND = 3042;
    static final int COLOR_LOGIC_OP = 3058;
    static final int SCISSOR_TEST = 3089;
    static final int TEXTURE_GEN_S = 3168;
    static final int TEXTURE_GEN_T = 3169;
    static final int TEXTURE_GEN_R = 3170;
    static final int TEXTURE_GEN_Q = 3171;
    static final int TEXTURE_2D = 3553;
    static final int POLYGON_OFFSET_FILL = 32823;
    static final int RESCALE_NORMAL = 32826;
    static final int LIGHT0 = 16384;
    static final int LIGHT1 = 16385;

    // client arrays
    static final int VERTEX_ARRAY = 32884;
    static final int NORMAL_ARRAY = 32885;
    static final int COLOR_ARRAY = 32886;
    static final int TEXTURE_COORD_ARRAY = 32888;

    // matrices
    static final int MODELVIEW = 5888;
    static final int PROJECTION = 5889;
    static final int TEXTURE = 5890;
    static final int MODELVIEW_MATRIX = 2982;
    static final int PROJECTION_MATRIX = 2983;
    static final int TEXTURE_MATRIX = 2984;

    // texture env
    static final int TEXTURE_ENV = 8960;
    static final int TEXTURE_ENV_MODE = 8704;
    static final int TEXTURE_ENV_COLOR = 8705;
    static final int MODULATE = 8448;
    static final int DECAL = 8449;
    static final int REPLACE = 7681;
    static final int ADD = 260;
    static final int BLEND_ENV = 3042;
    static final int COMBINE = 34160;
    static final int COMBINE_RGB = 34161;
    static final int COMBINE_ALPHA = 34162;
    static final int ADD_SIGNED = 34164;
    static final int INTERPOLATE = 34165;
    static final int SUBTRACT = 34023;
    static final int CONSTANT = 34166;
    static final int PRIMARY_COLOR = 34167;
    static final int PREVIOUS = 34168;
    static final int SRC0_RGB = 34176;
    static final int SRC0_ALPHA = 34184;
    static final int OPERAND0_RGB = 34192;
    static final int OPERAND0_ALPHA = 34200;
    static final int SRC_COLOR = 768;
    static final int ONE_MINUS_SRC_COLOR = 769;
    static final int SRC_ALPHA = 770;
    static final int ONE_MINUS_SRC_ALPHA = 771;
    static final int TEXTURE0 = 33984;

    // alpha / depth functions
    static final int NEVER = 512;
    static final int LESS = 513;
    static final int EQUAL = 514;
    static final int LEQUAL = 515;
    static final int GREATER = 516;
    static final int NOTEQUAL = 517;
    static final int GEQUAL = 518;
    static final int ALWAYS = 519;

    // fog
    static final int FOG_DENSITY = 2914;
    static final int FOG_START = 2915;
    static final int FOG_END = 2916;
    static final int FOG_MODE = 2917;
    static final int FOG_COLOR = 2918;
    static final int LINEAR = 9729;
    static final int EXP = 2048;
    static final int EXP2 = 2049;

    // lighting
    static final int AMBIENT = 4608;
    static final int DIFFUSE = 4609;
    static final int SPECULAR = 4610;
    static final int POSITION = 4611;
    static final int LIGHT_MODEL_AMBIENT = 2899;

    // texgen
    static final int S = 8192;
    static final int TEXTURE_GEN_MODE = 9472;
    static final int OBJECT_PLANE = 9473;
    static final int EYE_PLANE = 9474;
    static final int EYE_LINEAR = 9216;
    static final int OBJECT_LINEAR = 9217;
    static final int SPHERE_MAP = 9218;

    // logic ops
    static final int OR_REVERSE = 5387;
    static final int INVERT = 5386;
    static final int COPY = 5379;

    // misc
    static final int ARRAY_BUFFER = 34962;
    static final int ELEMENT_ARRAY_BUFFER = 34963;
    static final int FRAMEBUFFER = 36160;
    static final int READ_FRAMEBUFFER = 36008;
    static final int DRAW_FRAMEBUFFER = 36009;
    static final int RGBA = 6408;
    static final int RGB = 6407;
    static final int DEPTH_COMPONENT = 6402;
    static final int CLAMP = 10496;
    static final int CLAMP_TO_EDGE = 33071;
}
