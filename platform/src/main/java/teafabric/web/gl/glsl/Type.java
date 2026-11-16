package teafabric.web.gl.glsl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A GLSL type: scalar, vector (rows), matrix (cols x rows), sampler, struct; optionally an array. */
final class Type {
    static final int VOID = 0;
    static final int BOOL = 1;
    static final int INT = 2;
    static final int UINT = 3;
    static final int FLOAT = 4;
    static final int SAMPLER = 5;
    static final int STRUCT = 6;
    static final int UNKNOWN = 7;

    final int base;
    final int cols;
    final int rows;
    /** -1: not an array; 0: size unknown or implicit; otherwise the size. */
    final int array;
    /** Sampler or struct name. */
    final String name;
    /** Struct fields in order (name -> type), for STRUCT. */
    final Map<String, Type> fields;
    final List<String> fieldOrder;

    static final Type VOID_T = new Type(VOID, 1, 1, -1, "void", null, null);
    static final Type UNKNOWN_T = new Type(UNKNOWN, 1, 1, -1, "?", null, null);
    static final Type BOOL_T = scalar(BOOL);
    static final Type INT_T = scalar(INT);
    static final Type UINT_T = scalar(UINT);
    static final Type FLOAT_T = scalar(FLOAT);

    private Type(int base, int cols, int rows, int array, String name, Map<String, Type> fields,
            List<String> fieldOrder) {
        this.base = base;
        this.cols = cols;
        this.rows = rows;
        this.array = array;
        this.name = name;
        this.fields = fields;
        this.fieldOrder = fieldOrder;
    }

    static Type scalar(int base) {
        return new Type(base, 1, 1, -1, null, null, null);
    }

    static Type vec(int base, int n) {
        return n == 1 ? scalar(base) : new Type(base, 1, n, -1, null, null, null);
    }

    static Type mat(int cols, int rows) {
        return new Type(FLOAT, cols, rows, -1, null, null, null);
    }

    static Type sampler(String name) {
        return new Type(SAMPLER, 1, 1, -1, name, null, null);
    }

    static Type struct(String name, Map<String, Type> fields, List<String> order) {
        return new Type(STRUCT, 1, 1, -1, name, fields, order);
    }

    Type arrayOf(int size) {
        return new Type(base, cols, rows, size, name, fields, fieldOrder);
    }

    Type element() {
        return new Type(base, cols, rows, -1, name, fields, fieldOrder);
    }

    Type withBase(int newBase) {
        return new Type(newBase, cols, rows, array, null, null, null);
    }

    boolean isArray() {
        return array >= 0;
    }

    boolean isScalar() {
        return array < 0 && cols == 1 && rows == 1 && base >= BOOL && base <= FLOAT;
    }

    boolean isVector() {
        return array < 0 && cols == 1 && rows > 1;
    }

    boolean isMatrix() {
        return array < 0 && cols > 1;
    }

    boolean isNumeric() {
        return array < 0 && (base == INT || base == UINT || base == FLOAT);
    }

    boolean isIntegral() {
        return array < 0 && (base == INT || base == UINT) && cols == 1;
    }

    boolean isFloat() {
        return array < 0 && base == FLOAT;
    }

    boolean known() {
        return base != UNKNOWN;
    }

    boolean same(Type o) {
        if (base != o.base || cols != o.cols || rows != o.rows || (array < 0) != (o.array < 0)) {
            return false;
        }
        if (array > 0 && o.array > 0 && array != o.array) {
            return false;
        }
        return name == null ? o.name == null : name.equals(o.name);
    }

    /** The type's name as written in GLSL (without array suffix). */
    String glsl() {
        if (base == SAMPLER || base == STRUCT || base == VOID || base == UNKNOWN) {
            return name;
        }
        if (cols > 1) {
            return cols == rows ? "mat" + cols : "mat" + cols + "x" + rows;
        }
        String prefix;
        String scalarName;
        switch (base) {
            case BOOL: prefix = "b"; scalarName = "bool"; break;
            case INT: prefix = "i"; scalarName = "int"; break;
            case UINT: prefix = "u"; scalarName = "uint"; break;
            default: prefix = ""; scalarName = "float"; break;
        }
        return rows == 1 ? scalarName : prefix + "vec" + rows;
    }

    @Override
    public String toString() {
        return glsl() + (array < 0 ? "" : "[" + (array > 0 ? array : "") + "]");
    }

    private static final Map<String, Type> BUILTIN = new HashMap<>();

    static {
        BUILTIN.put("void", VOID_T);
        BUILTIN.put("bool", BOOL_T);
        BUILTIN.put("int", INT_T);
        BUILTIN.put("uint", UINT_T);
        BUILTIN.put("float", FLOAT_T);
        BUILTIN.put("double", FLOAT_T);
        for (int n = 2; n <= 4; n++) {
            BUILTIN.put("vec" + n, vec(FLOAT, n));
            BUILTIN.put("dvec" + n, vec(FLOAT, n));
            BUILTIN.put("ivec" + n, vec(INT, n));
            BUILTIN.put("uvec" + n, vec(UINT, n));
            BUILTIN.put("bvec" + n, vec(BOOL, n));
            BUILTIN.put("mat" + n, mat(n, n));
            BUILTIN.put("dmat" + n, mat(n, n));
            for (int m = 2; m <= 4; m++) {
                BUILTIN.put("mat" + n + "x" + m, mat(n, m));
            }
        }
        for (String s : new String[] {"sampler1D", "sampler2D", "sampler3D", "samplerCube", "sampler1DShadow",
                "sampler2DShadow", "samplerCubeShadow", "sampler2DArray", "sampler2DArrayShadow", "sampler2DRect",
                "sampler2DRectShadow", "samplerBuffer", "isampler2D", "isampler3D", "isamplerCube",
                "isampler2DArray", "usampler2D", "usampler3D", "usamplerCube", "usampler2DArray", "isampler1D",
                "usampler1D", "sampler1DArray", "image2D", "image3D", "iimage2D", "uimage2D", "iimage3D",
                "uimage3D"}) {
            BUILTIN.put(s, sampler(s));
        }
    }

    /** A built-in type by name, or null. */
    static Type builtin(String name) {
        return BUILTIN.get(name);
    }
}
