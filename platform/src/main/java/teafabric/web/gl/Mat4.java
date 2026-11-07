package teafabric.web.gl;

/** Column-major 4x4 float matrices (OpenGL layout) for the fixed-function emulation. */
final class Mat4 {
    private Mat4() {
    }

    static float[] identity() {
        float[] m = new float[16];
        m[0] = 1;
        m[5] = 1;
        m[10] = 1;
        m[15] = 1;
        return m;
    }

    static void setIdentity(float[] m) {
        for (int i = 0; i < 16; i++) {
            m[i] = 0;
        }
        m[0] = 1;
        m[5] = 1;
        m[10] = 1;
        m[15] = 1;
    }

    /** m = m * n */
    static void mul(float[] m, float[] n) {
        float[] r = new float[16];
        for (int c = 0; c < 4; c++) {
            for (int row = 0; row < 4; row++) {
                r[c * 4 + row] = m[row] * n[c * 4] + m[4 + row] * n[c * 4 + 1] + m[8 + row] * n[c * 4 + 2]
                        + m[12 + row] * n[c * 4 + 3];
            }
        }
        System.arraycopy(r, 0, m, 0, 16);
    }

    /** out = a * b (out may not alias). */
    static void mul(float[] a, float[] b, float[] out) {
        for (int c = 0; c < 4; c++) {
            for (int row = 0; row < 4; row++) {
                out[c * 4 + row] = a[row] * b[c * 4] + a[4 + row] * b[c * 4 + 1] + a[8 + row] * b[c * 4 + 2]
                        + a[12 + row] * b[c * 4 + 3];
            }
        }
    }

    static void translate(float[] m, float x, float y, float z) {
        m[12] += m[0] * x + m[4] * y + m[8] * z;
        m[13] += m[1] * x + m[5] * y + m[9] * z;
        m[14] += m[2] * x + m[6] * y + m[10] * z;
        m[15] += m[3] * x + m[7] * y + m[11] * z;
    }

    static void scale(float[] m, float x, float y, float z) {
        for (int i = 0; i < 4; i++) {
            m[i] *= x;
            m[4 + i] *= y;
            m[8 + i] *= z;
        }
    }

    static void rotate(float[] m, float angleDeg, float x, float y, float z) {
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        if (len == 0) {
            return;
        }
        x /= len;
        y /= len;
        z /= len;
        float a = (float) Math.toRadians(angleDeg);
        float c = (float) Math.cos(a);
        float s = (float) Math.sin(a);
        float t = 1 - c;
        float[] r = new float[16];
        r[0] = x * x * t + c;
        r[1] = y * x * t + z * s;
        r[2] = x * z * t - y * s;
        r[4] = x * y * t - z * s;
        r[5] = y * y * t + c;
        r[6] = y * z * t + x * s;
        r[8] = x * z * t + y * s;
        r[9] = y * z * t - x * s;
        r[10] = z * z * t + c;
        r[15] = 1;
        mul(m, r);
    }

    static void ortho(float[] m, double l, double r, double b, double t, double n, double f) {
        float[] o = new float[16];
        o[0] = (float) (2 / (r - l));
        o[5] = (float) (2 / (t - b));
        o[10] = (float) (-2 / (f - n));
        o[12] = (float) (-(r + l) / (r - l));
        o[13] = (float) (-(t + b) / (t - b));
        o[14] = (float) (-(f + n) / (f - n));
        o[15] = 1;
        mul(m, o);
    }

    static void frustum(float[] m, double l, double r, double b, double t, double n, double f) {
        float[] o = new float[16];
        o[0] = (float) (2 * n / (r - l));
        o[5] = (float) (2 * n / (t - b));
        o[8] = (float) ((r + l) / (r - l));
        o[9] = (float) ((t + b) / (t - b));
        o[10] = (float) (-(f + n) / (f - n));
        o[11] = -1;
        o[14] = (float) (-2 * f * n / (f - n));
        mul(m, o);
    }

    /** General 4x4 inverse; returns false if singular (out untouched). */
    static boolean invert(float[] m, float[] out) {
        float[] inv = new float[16];
        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] + m[9] * m[7] * m[14]
                + m[13] * m[6] * m[11] - m[13] * m[7] * m[10];
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] - m[8] * m[7] * m[14]
                - m[12] * m[6] * m[11] + m[12] * m[7] * m[10];
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] + m[8] * m[7] * m[13]
                + m[12] * m[5] * m[11] - m[12] * m[7] * m[9];
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] - m[8] * m[6] * m[13]
                - m[12] * m[5] * m[10] + m[12] * m[6] * m[9];
        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] - m[9] * m[3] * m[14]
                - m[13] * m[2] * m[11] + m[13] * m[3] * m[10];
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] + m[8] * m[3] * m[14]
                + m[12] * m[2] * m[11] - m[12] * m[3] * m[10];
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] - m[8] * m[3] * m[13]
                - m[12] * m[1] * m[11] + m[12] * m[3] * m[9];
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] + m[8] * m[2] * m[13]
                + m[12] * m[1] * m[10] - m[12] * m[2] * m[9];
        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] + m[5] * m[3] * m[14]
                + m[13] * m[2] * m[7] - m[13] * m[3] * m[6];
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] - m[4] * m[3] * m[14]
                - m[12] * m[2] * m[7] + m[12] * m[3] * m[6];
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] + m[4] * m[3] * m[13]
                + m[12] * m[1] * m[7] - m[12] * m[3] * m[5];
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] - m[4] * m[2] * m[13]
                - m[12] * m[1] * m[6] + m[12] * m[2] * m[5];
        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] - m[5] * m[3] * m[10]
                - m[9] * m[2] * m[7] + m[9] * m[3] * m[6];
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] + m[4] * m[3] * m[10]
                + m[8] * m[2] * m[7] - m[8] * m[3] * m[6];
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] - m[4] * m[3] * m[9]
                - m[8] * m[1] * m[7] + m[8] * m[3] * m[5];
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] + m[4] * m[2] * m[9]
                + m[8] * m[1] * m[6] - m[8] * m[2] * m[5];
        float det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12];
        if (det == 0) {
            return false;
        }
        det = 1.0f / det;
        for (int i = 0; i < 16; i++) {
            out[i] = inv[i] * det;
        }
        return true;
    }

    /** Upper-left 3x3 of the inverse transpose (normal matrix), column-major 9 floats. */
    static void normalMatrix(float[] m, float[] out9) {
        float a = m[0];
        float b = m[4];
        float c = m[8];
        float d = m[1];
        float e = m[5];
        float f = m[9];
        float g = m[2];
        float h = m[6];
        float i = m[10];
        float det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
        if (det == 0) {
            det = 1;
        }
        float inv = 1 / det;
        // inverse transpose == cofactor matrix / det
        out9[0] = (e * i - f * h) * inv;
        out9[1] = -(b * i - c * h) * inv;
        out9[2] = (b * f - c * e) * inv;
        out9[3] = -(d * i - f * g) * inv;
        out9[4] = (a * i - c * g) * inv;
        out9[5] = -(a * f - c * d) * inv;
        out9[6] = (d * h - e * g) * inv;
        out9[7] = -(a * h - b * g) * inv;
        out9[8] = (a * e - b * d) * inv;
    }

    /** v' = m * v for a 4-vector. */
    static void transform(float[] m, float[] v, float[] out) {
        float x = v[0];
        float y = v[1];
        float z = v[2];
        float w = v[3];
        out[0] = m[0] * x + m[4] * y + m[8] * z + m[12] * w;
        out[1] = m[1] * x + m[5] * y + m[9] * z + m[13] * w;
        out[2] = m[2] * x + m[6] * y + m[10] * z + m[14] * w;
        out[3] = m[3] * x + m[7] * y + m[11] * z + m[15] * w;
    }

    /** row vector v' = v * m (used for plane transforms by the inverse modelview). */
    static void transformRow(float[] v, float[] m, float[] out) {
        for (int c = 0; c < 4; c++) {
            out[c] = v[0] * m[c * 4] + v[1] * m[c * 4 + 1] + v[2] * m[c * 4 + 2] + v[3] * m[c * 4 + 3];
        }
    }
}
