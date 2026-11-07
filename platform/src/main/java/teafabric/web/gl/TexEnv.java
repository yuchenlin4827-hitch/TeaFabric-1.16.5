package teafabric.web.gl;

/** Per-unit glTexEnv state (GL_TEXTURE_ENV_MODE and the GL_COMBINE parameters). */
final class TexEnv {
    static final int MODE = 0;
    static final int COMBINE_RGB = 1;
    static final int COMBINE_ALPHA = 2;
    static final int SRC0_RGB = 3;
    static final int SRC0_ALPHA = 6;
    static final int OP0_RGB = 9;
    static final int OP0_ALPHA = 12;

    final int[] p = new int[15];
    final float[] color = new float[4];

    TexEnv() {
        p[MODE] = GLConst.MODULATE;
        p[COMBINE_RGB] = GLConst.MODULATE;
        p[COMBINE_ALPHA] = GLConst.MODULATE;
        p[SRC0_RGB] = GLConst.TEXTURE;
        p[SRC0_RGB + 1] = GLConst.PREVIOUS;
        p[SRC0_RGB + 2] = GLConst.CONSTANT;
        p[SRC0_ALPHA] = GLConst.TEXTURE;
        p[SRC0_ALPHA + 1] = GLConst.PREVIOUS;
        p[SRC0_ALPHA + 2] = GLConst.CONSTANT;
        p[OP0_RGB] = GLConst.SRC_COLOR;
        p[OP0_RGB + 1] = GLConst.SRC_COLOR;
        p[OP0_RGB + 2] = GLConst.SRC_ALPHA;
        p[OP0_ALPHA] = GLConst.SRC_ALPHA;
        p[OP0_ALPHA + 1] = GLConst.SRC_ALPHA;
        p[OP0_ALPHA + 2] = GLConst.SRC_ALPHA;
    }

    void copyFrom(TexEnv o) {
        System.arraycopy(o.p, 0, p, 0, p.length);
        System.arraycopy(o.color, 0, color, 0, 4);
    }

    /** Returns false for an unknown parameter. */
    boolean set(int pname, int value) {
        switch (pname) {
            case GLConst.TEXTURE_ENV_MODE: p[MODE] = value; return true;
            case GLConst.COMBINE_RGB: p[COMBINE_RGB] = value; return true;
            case GLConst.COMBINE_ALPHA: p[COMBINE_ALPHA] = value; return true;
            case GLConst.SRC0_RGB: case GLConst.SRC0_RGB + 1: case GLConst.SRC0_RGB + 2:
                p[SRC0_RGB + pname - GLConst.SRC0_RGB] = value; return true;
            case GLConst.SRC0_ALPHA: case GLConst.SRC0_ALPHA + 1: case GLConst.SRC0_ALPHA + 2:
                p[SRC0_ALPHA + pname - GLConst.SRC0_ALPHA] = value; return true;
            case GLConst.OPERAND0_RGB: case GLConst.OPERAND0_RGB + 1: case GLConst.OPERAND0_RGB + 2:
                p[OP0_RGB + pname - GLConst.OPERAND0_RGB] = value; return true;
            case GLConst.OPERAND0_ALPHA: case GLConst.OPERAND0_ALPHA + 1: case GLConst.OPERAND0_ALPHA + 2:
                p[OP0_ALPHA + pname - GLConst.OPERAND0_ALPHA] = value; return true;
            default:
                return false;
        }
    }

    void appendKey(StringBuilder sb) {
        sb.append(Integer.toHexString(p[MODE]));
        if (p[MODE] == GLConst.COMBINE) {
            for (int i = 1; i < p.length; i++) {
                sb.append(',').append(Integer.toHexString(p[i]));
            }
        }
    }
}
