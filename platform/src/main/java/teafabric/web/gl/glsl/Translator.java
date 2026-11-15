package teafabric.web.gl.glsl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import teafabric.web.gl.glsl.Ast.*;

/**
 * Type-checks a desktop GLSL shader and rewrites it in place into GLSL ES 3.00: implicit int-to-float
 * conversions become explicit, compatibility-profile built-ins (gl_Vertex, gl_ModelViewMatrix, gl_FragData, ...)
 * become ordinary inputs, uniforms and outputs, desktop texture functions get their ES names, and identifiers
 * GLSL ES reserves are renamed. Unknown constructs are left as they are, with an unknown type.
 */
final class Translator {
    static final class Sym {
        final String name; // name in the output
        final Type type;
        final boolean isConst;

        Sym(String name, Type type, boolean isConst) {
            this.name = name;
            this.type = type;
            this.isConst = isConst;
        }
    }

    static final class Sig {
        final String name;
        final Type ret;
        final List<Type> params = new ArrayList<>();
        final List<Boolean> output = new ArrayList<>();

        Sig(String name, Type ret) {
            this.name = name;
            this.ret = ret;
        }
    }

    final boolean vertex;
    final Unit unit;
    private final Deque<Map<String, Sym>> scopes = new ArrayDeque<>();
    private final Map<String, List<Sig>> functions = new HashMap<>();
    private final Map<String, Type> structs = new HashMap<>();
    private Type returnType = Type.VOID_T;
    private final List<Stmt> globalInits = new ArrayList<>();

    // results
    /** Built-in replacements used: output name -> declaration ("in vec4", "uniform mat4", "out vec4", ...). */
    final Map<String, String> builtinDecls = new TreeMap<>();
    /** Fragment outputs written through gl_FragColor / gl_FragData: index -> output name. */
    final Map<Integer, String> fragData = new TreeMap<>();
    /** User-declared varyings: output name -> "interpolation type" (for matching the other stage). */
    final Map<String, String> varyings = new LinkedHashMap<>();
    /** Renamed user interface variables (uniforms, attributes): original -> output name. */
    final Map<String, String> renamedInterface = new HashMap<>();
    /** User fragment outputs (GLSL 1.30+), in declaration order. */
    final List<Decl> userOutputs = new ArrayList<>();
    boolean compat;

    Translator(Unit unit, boolean vertex) {
        this.unit = unit;
        this.vertex = vertex;
    }

    // ------------------------------------------------------------------ scopes

    private void push() {
        scopes.push(new HashMap<>());
    }

    private void pop() {
        scopes.pop();
    }

    private Sym lookup(String name) {
        for (Map<String, Sym> s : scopes) {
            Sym v = s.get(name);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private String declare(String name, Type type, boolean isConst) {
        String out = Builtins.ES_RESERVED.contains(name) ? "tf_" + name : name;
        scopes.peek().put(name, new Sym(out, type, isConst));
        return out;
    }

    // ------------------------------------------------------------------ entry

    void run() {
        push();
        for (Stmt s : unit.decls) {
            if (s instanceof Decl) {
                globalDecl((Decl) s);
            } else if (s instanceof Function) {
                function((Function) s);
            }
        }
        if (!globalInits.isEmpty()) {
            for (Stmt s : unit.decls) {
                if (s instanceof Function && ((Function) s).name.equals("main") && ((Function) s).body != null) {
                    ((Function) s).body.body.addAll(0, globalInits);
                    break;
                }
            }
        }
        pop();
    }

    // ------------------------------------------------------------------ types

    private Type resolve(TypeSpec t) {
        Type base;
        if (t.struct != null) {
            base = structType(t.struct);
        } else {
            base = Type.builtin(t.name);
            if (base == null) {
                base = structs.get(t.name);
            }
            if (base == null) {
                base = Type.UNKNOWN_T;
            }
        }
        if (t.hasArray) {
            base = base.arrayOf(arraySize(t.arraySize));
            if (t.arraySize != null) {
                t.arraySize = expr(t.arraySize);
            }
        }
        t.resolved = base;
        return base;
    }

    private Type structType(StructBody s) {
        Map<String, Type> fields = new HashMap<>();
        List<String> order = new ArrayList<>();
        for (Decl m : s.members) {
            stripPrecision(m.q);
            Type mt = resolve(m.type);
            for (Declarator v : m.vars) {
                Type ft = v.hasArray ? mt.arrayOf(arraySize(v.arraySize)) : mt;
                if (v.arraySize != null) {
                    v.arraySize = expr(v.arraySize);
                }
                v.type = ft;
                fields.put(v.name, ft);
                order.add(v.name);
            }
        }
        Type t = Type.struct(s.name == null ? "?" : s.name, fields, order);
        if (s.name != null && !s.block) {
            structs.put(s.name, t);
        }
        return t;
    }

    private static int arraySize(Expr e) {
        if (e instanceof Lit && ((Lit) e).kind == Type.INT) {
            try {
                return Integer.decode(((Lit) e).text);
            } catch (NumberFormatException x) {
                return 0;
            }
        }
        return 0;
    }

    private static void stripPrecision(Qualifiers q) {
        q.remove("highp");
        q.remove("mediump");
        q.remove("lowp");
        q.remove("precise");
        q.remove("noperspective");
    }

    // ------------------------------------------------------------------ declarations

    private void globalDecl(Decl d) {
        stripPrecision(d.q);
        Qualifiers q = d.q;
        boolean uniform = q.has("uniform");
        boolean input = q.has("in") || q.has("attribute") || (!vertex && q.has("varying"));
        boolean output = q.has("out") || (vertex && q.has("varying"));
        if (vertex) {
            q.replace("attribute", "in");
            q.replace("varying", "out");
        } else {
            q.replace("varying", "in");
        }
        Type t = resolve(d.type);
        if (d.type.struct != null && d.type.struct.block) {
            // interface block: members are visible directly unless the block has an instance name
            if (d.vars.isEmpty()) {
                for (Map.Entry<String, Type> f : t.fields.entrySet()) {
                    declare(f.getKey(), f.getValue(), false);
                }
            }
        }
        boolean isConst = q.has("const");
        for (Declarator v : d.vars) {
            Type vt = v.hasArray ? t.arrayOf(arraySize(v.arraySize)) : t;
            if (v.arraySize != null) {
                v.arraySize = expr(v.arraySize);
            }
            boolean constValue = isConst;
            if (v.init != null) {
                v.init = convert(expr(v.init), vt);
                if (vt.isArray() && vt.array == 0 && v.init.type.isArray()) {
                    vt = v.init.type;
                }
                if (uniform) {
                    v.init = null; // GLSL ES has no uniform initialisers
                } else if (!isConstant(v.init)) {
                    // GLSL ES only allows constant initialisers at global scope: assign at the start of main()
                    constValue = false;
                    ExprStmt init = new ExprStmt();
                    Assign a = new Assign();
                    a.op = "=";
                    a.l = new Raw(null, vt); // name filled in below
                    a.r = v.init;
                    init.e = a;
                    v.init = null;
                    globalInits.add(init);
                    if (isConst) {
                        q.remove("const");
                        isConst = false;
                    }
                }
            }
            v.type = vt;
            String out = declare(v.name, vt, constValue);
            for (Stmt s : globalInits) {
                Assign a = (Assign) ((ExprStmt) s).e;
                if (a.l instanceof Raw && ((Raw) a.l).text == null) {
                    ((Raw) a.l).text = out;
                }
            }
            if (!out.equals(v.name) && (uniform || input || output)) {
                renamedInterface.put(v.name, out);
            }
            v.name = out;
            if (output && !vertex) {
                userOutputs.add(d);
            } else if ((output && vertex) || (input && !vertex)) {
                String interp = q.has("flat") ? "flat " : q.has("centroid") ? "centroid " : "";
                varyings.put(out, interp + vt.glsl() + (vt.isArray() ? "[" + vt.array + "]" : ""));
            }
        }
    }

    private void function(Function f) {
        stripPrecision(f.q);
        Type ret = resolve(f.ret);
        Sig sig = new Sig(f.name, ret);
        for (Param p : f.params) {
            stripPrecision(p.q);
            Type pt = resolve(p.type);
            if (p.hasArray) {
                pt = pt.arrayOf(arraySize(p.arraySize));
            }
            p.resolved = pt;
            sig.params.add(pt);
            sig.output.add(p.q.has("out") || p.q.has("inout"));
        }
        List<Sig> overloads = functions.computeIfAbsent(f.name, k -> new ArrayList<>());
        Sig existing = null;
        for (Sig s : overloads) {
            if (sameParams(s.params, sig.params)) {
                existing = s;
            }
        }
        if (existing == null) {
            overloads.add(sig);
        }
        if (Builtins.ES_RESERVED.contains(f.name)) {
            f.name = "tf_" + f.name;
        }
        if (f.body == null) {
            return;
        }
        Type saved = returnType;
        returnType = ret;
        push();
        for (Param p : f.params) {
            if (p.name != null) {
                p.name = declare(p.name, p.resolved, false);
            }
        }
        for (Stmt s : f.body.body) {
            statement(s);
        }
        pop();
        returnType = saved;
    }

    private static boolean sameParams(List<Type> a, List<Type> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).same(b.get(i))) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ statements

    private void statement(Stmt s) {
        if (s instanceof Block) {
            push();
            for (Stmt x : ((Block) s).body) {
                statement(x);
            }
            pop();
        } else if (s instanceof Decl) {
            localDecl((Decl) s);
        } else if (s instanceof ExprStmt) {
            ((ExprStmt) s).e = expr(((ExprStmt) s).e);
        } else if (s instanceof If) {
            If i = (If) s;
            i.c = expr(i.c);
            scoped(i.then);
            if (i.otherwise != null) {
                scoped(i.otherwise);
            }
        } else if (s instanceof For) {
            For f = (For) s;
            push();
            if (f.init != null) {
                statement(f.init);
            }
            if (f.cond != null) {
                f.cond = expr(f.cond);
            }
            if (f.step != null) {
                f.step = expr(f.step);
            }
            scoped(f.body);
            pop();
        } else if (s instanceof While) {
            While w = (While) s;
            w.c = expr(w.c);
            scoped(w.body);
        } else if (s instanceof DoWhile) {
            DoWhile d = (DoWhile) s;
            scoped(d.body);
            d.c = expr(d.c);
        } else if (s instanceof Switch) {
            Switch w = (Switch) s;
            w.e = expr(w.e);
            statement(w.body);
        } else if (s instanceof Case) {
            Case c = (Case) s;
            if (c.value != null) {
                c.value = expr(c.value);
            }
        } else if (s instanceof Jump) {
            Jump j = (Jump) s;
            if (j.value != null) {
                j.value = convert(expr(j.value), returnType);
            }
        }
    }

    private void scoped(Stmt s) {
        push();
        statement(s);
        pop();
    }

    private void localDecl(Decl d) {
        stripPrecision(d.q);
        Type t = resolve(d.type);
        boolean isConst = d.q.has("const");
        for (Declarator v : d.vars) {
            Type vt = v.hasArray ? t.arrayOf(arraySize(v.arraySize)) : t;
            if (v.arraySize != null) {
                v.arraySize = expr(v.arraySize);
            }
            boolean constValue = isConst;
            if (v.init != null) {
                v.init = convert(expr(v.init), vt);
                if (vt.isArray() && vt.array == 0 && v.init.type.isArray()) {
                    vt = v.init.type;
                }
                if (isConst && !isConstant(v.init)) {
                    d.q.remove("const"); // desktop drivers accept const locals with run-time values; ES does not
                    isConst = false;
                    constValue = false;
                }
            }
            v.type = vt;
            v.name = declare(v.name, vt, constValue);
        }
    }

    // ------------------------------------------------------------------ constants

    private boolean isConstant(Expr e) {
        if (e instanceof Lit) {
            return true;
        }
        if (e instanceof Name) {
            Sym s = lookup(((Name) e).name);
            return s != null && s.isConst;
        }
        if (e instanceof Raw) {
            return false;
        }
        if (e instanceof Call) {
            Call c = (Call) e;
            if (c.receiver != null) {
                return true; // length() of an array is a constant
            }
            if (Builtins.TEXTURE.containsKey(c.name) || c.name.startsWith("texel") || c.name.startsWith("dF")
                    || c.name.equals("fwidth") || functions.containsKey(c.name)) {
                return false;
            }
            for (Expr a : c.args) {
                if (!isConstant(a)) {
                    return false;
                }
            }
            return true;
        }
        if (e instanceof Unary) {
            String op = ((Unary) e).op;
            return !op.equals("++") && !op.equals("--") && isConstant(((Unary) e).e);
        }
        if (e instanceof Binary) {
            return isConstant(((Binary) e).l) && isConstant(((Binary) e).r);
        }
        if (e instanceof Cond) {
            Cond c = (Cond) e;
            return isConstant(c.c) && isConstant(c.a) && isConstant(c.b);
        }
        if (e instanceof Index) {
            return isConstant(((Index) e).base) && isConstant(((Index) e).index);
        }
        if (e instanceof Member) {
            return isConstant(((Member) e).base);
        }
        return false;
    }

    // ------------------------------------------------------------------ conversions

    private static boolean intBased(Type t) {
        return t.array < 0 && (t.base == Type.INT || t.base == Type.UINT) && t.cols == 1;
    }

    /** Converts {@code e} to {@code target} where desktop GLSL would convert implicitly (int to float). */
    static Expr convert(Expr e, Type target) {
        Type t = e.type;
        if (!t.known() || !target.known()) {
            return e;
        }
        if (target.isArray() && t.isArray() && e instanceof Call && ((Call) e).arrayCtor != null) {
            return e; // array constructors convert their elements themselves
        }
        if (target.base == Type.FLOAT && target.array < 0 && intBased(t) && target.cols == 1
                && (target.rows == t.rows || t.rows == 1 && target.rows == 1)) {
            return toFloat(e, target);
        }
        return e;
    }

    private static Expr toFloat(Expr e, Type target) {
        if (e instanceof Lit && target.rows == 1) {
            Lit l = (Lit) e;
            String text = l.text;
            if (text.endsWith("u") || text.endsWith("U")) {
                text = text.substring(0, text.length() - 1);
            }
            Lit f = new Lit(text.startsWith("0x") || text.startsWith("0X") || (text.length() > 1 && text.startsWith("0"))
                    ? "float(" + text + ")" : text + ".0", Type.FLOAT);
            f.type = Type.FLOAT_T;
            f.line = e.line;
            return f;
        }
        Call c = new Call();
        c.name = target.glsl();
        c.args.add(e);
        c.type = target;
        c.line = e.line;
        return c;
    }

    /** Converts an integer operand to the float shape of {@code like} (scalar or vector of the same size). */
    private static Expr toFloatShape(Expr e) {
        Type t = e.type;
        return toFloat(e, Type.vec(Type.FLOAT, t.rows));
    }

    // ------------------------------------------------------------------ expressions

    Expr expr(Expr e) {
        if (e instanceof Lit) {
            Lit l = (Lit) e;
            if (l.kind == Type.FLOAT && (l.text.endsWith("lf") || l.text.endsWith("LF"))) {
                l.text = l.text.substring(0, l.text.length() - 2);
            }
            l.type = Type.scalar(l.kind);
            return l;
        }
        if (e instanceof Name) {
            return name((Name) e);
        }
        if (e instanceof Raw) {
            return e;
        }
        if (e instanceof Call) {
            return call((Call) e);
        }
        if (e instanceof Index) {
            return index((Index) e);
        }
        if (e instanceof Member) {
            return member((Member) e);
        }
        if (e instanceof Unary) {
            Unary u = (Unary) e;
            u.e = expr(u.e);
            u.type = u.op.equals("!") ? Type.BOOL_T : u.e.type;
            return u;
        }
        if (e instanceof Binary) {
            return binary((Binary) e);
        }
        if (e instanceof Assign) {
            Assign a = (Assign) e;
            a.l = expr(a.l);
            a.r = expr(a.r);
            Type lt = a.l.type;
            if (lt.isFloat() && intBased(a.r.type)) {
                a.r = a.r.type.rows == 1 ? toFloat(a.r, Type.FLOAT_T) : toFloatShape(a.r);
            }
            a.type = lt;
            return a;
        }
        if (e instanceof Cond) {
            Cond c = (Cond) e;
            c.c = expr(c.c);
            c.a = expr(c.a);
            c.b = expr(c.b);
            if (c.a.type.isFloat() && intBased(c.b.type)) {
                c.b = convert(c.b, c.a.type);
            } else if (c.b.type.isFloat() && intBased(c.a.type)) {
                c.a = convert(c.a, c.b.type);
            }
            c.type = c.a.type.known() ? c.a.type : c.b.type;
            return c;
        }
        if (e instanceof Comma) {
            Comma c = (Comma) e;
            c.l = expr(c.l);
            c.r = expr(c.r);
            c.type = c.r.type;
            return c;
        }
        return e;
    }

    private Expr name(Name n) {
        if (n.name.startsWith("gl_")) {
            Expr b = builtinVar(n.name, n);
            if (b != null) {
                return b;
            }
        }
        Sym s = lookup(n.name);
        if (s != null) {
            n.name = s.name;
            n.type = s.type;
        }
        return n;
    }

    private Raw use(String name, String decl, Type type) {
        builtinDecls.put(name, decl);
        compat = true;
        return new Raw(name, type);
    }

    private static final Type VEC4 = Type.vec(Type.FLOAT, 4);
    private static final Type VEC3 = Type.vec(Type.FLOAT, 3);
    private static final Type MAT4 = Type.mat(4, 4);

    /** Compatibility-profile built-in variables; null if the name is not one (or needs no change). */
    private Expr builtinVar(String name, Expr at) {
        switch (name) {
            case "gl_ModelViewMatrix": return use("tf_ModelViewMatrix", "uniform mat4", MAT4);
            case "gl_ProjectionMatrix": return use("tf_ProjectionMatrix", "uniform mat4", MAT4);
            case "gl_ModelViewProjectionMatrix": return use("tf_ModelViewProjectionMatrix", "uniform mat4", MAT4);
            case "gl_ModelViewMatrixInverse": return use("tf_ModelViewMatrixInverse", "uniform mat4", MAT4);
            case "gl_ProjectionMatrixInverse": return use("tf_ProjectionMatrixInverse", "uniform mat4", MAT4);
            case "gl_ModelViewProjectionMatrixInverse":
                return use("tf_ModelViewProjectionMatrixInverse", "uniform mat4", MAT4);
            case "gl_ModelViewMatrixTranspose":
                return derived("transpose(tf_ModelViewMatrix)", "tf_ModelViewMatrix", MAT4);
            case "gl_ProjectionMatrixTranspose":
                return derived("transpose(tf_ProjectionMatrix)", "tf_ProjectionMatrix", MAT4);
            case "gl_ModelViewMatrixInverseTranspose":
                return derived("transpose(tf_ModelViewMatrixInverse)", "tf_ModelViewMatrixInverse", MAT4);
            case "gl_NormalMatrix": return use("tf_NormalMatrix", "uniform mat3", Type.mat(3, 3));
            case "gl_TextureMatrix": return use("tf_TextureMatrix", "uniform mat4[8]", MAT4.arrayOf(8));
            case "gl_MaxTextureCoords": case "gl_MaxTextureUnits": {
                Lit l = new Lit("8", Type.INT);
                l.type = Type.INT_T;
                return l;
            }
            default:
                break;
        }
        if (vertex) {
            switch (name) {
                case "gl_Vertex": return use("tf_Vertex", "in vec4", VEC4);
                case "gl_Color": return use("tf_Color", "in vec4", VEC4);
                case "gl_SecondaryColor": return use("tf_SecondaryColor", "in vec4", VEC4);
                case "gl_Normal": return use("tf_Normal", "in vec3", VEC3);
                case "gl_FogCoord": return use("tf_FogCoord", "in float", Type.FLOAT_T);
                case "gl_FrontColor": return use("tf_FrontColor", "out vec4", VEC4);
                case "gl_FrontSecondaryColor": return use("tf_FrontSecondaryColor", "out vec4", VEC4);
                case "gl_BackColor": return use("tf_BackColor", "vec4", VEC4);
                case "gl_BackSecondaryColor": return use("tf_BackSecondaryColor", "vec4", VEC4);
                case "gl_FogFragCoord": return use("tf_FogFragCoord", "out float", Type.FLOAT_T);
                case "gl_ClipVertex": return use("tf_ClipVertex", "vec4", VEC4);
                default:
                    if (name.startsWith("gl_MultiTexCoord") && name.length() == 17) {
                        return use("tf_MultiTexCoord" + name.charAt(16), "in vec4", VEC4);
                    }
                    return null;
            }
        }
        switch (name) {
            case "gl_Color": return use("tf_FrontColor", "in vec4", VEC4);
            case "gl_SecondaryColor": return use("tf_FrontSecondaryColor", "in vec4", VEC4);
            case "gl_FogFragCoord": return use("tf_FogFragCoord", "in float", Type.FLOAT_T);
            case "gl_FragColor": return fragOut(0);
            default: return null;
        }
    }

    private Raw derived(String text, String uniform, Type type) {
        builtinDecls.put(uniform, "uniform mat4");
        compat = true;
        return new Raw(text, type);
    }

    private Raw fragOut(int index) {
        String n = "tf_FragData" + index;
        fragData.put(index, n); // (needs no fixed-function state: does not make the shader "compat")
        return new Raw(n, VEC4);
    }

    private Expr index(Index i) {
        if (i.base instanceof Name) {
            String n = ((Name) i.base).name;
            if (n.equals("gl_TexCoord") || n.equals("gl_FragData")) {
                Expr idx = expr(i.index);
                if (!(idx instanceof Lit) || ((Lit) idx).kind != Type.INT) {
                    throw new GlslException(i.line, n + " with a non-constant index");
                }
                int k = Integer.decode(((Lit) idx).text);
                if (n.equals("gl_FragData")) {
                    if (vertex) {
                        throw new GlslException(i.line, "gl_FragData in a vertex shader");
                    }
                    return fragOut(k);
                }
                return use("tf_TexCoord" + k, (vertex ? "out" : "in") + " vec4", VEC4);
            }
        }
        i.base = expr(i.base);
        i.index = expr(i.index);
        Type b = i.base.type;
        if (b.isArray()) {
            i.type = b.element();
        } else if (b.isMatrix()) {
            i.type = Type.vec(Type.FLOAT, b.rows);
        } else if (b.isVector()) {
            i.type = Type.scalar(b.base);
        }
        return i;
    }

    private Expr member(Member m) {
        if (m.base instanceof Name && ((Name) m.base).name.equals("gl_Fog")) {
            switch (m.field) {
                case "color": return use("tf_FogColor", "uniform vec4", VEC4);
                case "density": return use("tf_FogDensity", "uniform float", Type.FLOAT_T);
                case "start": return use("tf_FogStart", "uniform float", Type.FLOAT_T);
                case "end": return use("tf_FogEnd", "uniform float", Type.FLOAT_T);
                case "scale": return use("tf_FogScale", "uniform float", Type.FLOAT_T);
                default: break;
            }
        }
        m.base = expr(m.base);
        Type b = m.base.type;
        if (b.base == Type.STRUCT && !b.isArray()) {
            Type f = b.fields.get(m.field);
            if (f != null) {
                m.type = f;
            }
        } else if (b.known() && !b.isArray() && b.cols == 1 && b.base != Type.SAMPLER && isSwizzle(m.field)) {
            m.type = Type.vec(b.base, m.field.length());
        }
        return m;
    }

    private static boolean isSwizzle(String f) {
        if (f.length() > 4) {
            return false;
        }
        for (int i = 0; i < f.length(); i++) {
            if ("xyzwrgbastpq".indexOf(f.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    private Expr binary(Binary b) {
        b.l = expr(b.l);
        b.r = expr(b.r);
        Type l = b.l.type;
        Type r = b.r.type;
        String op = b.op;
        switch (op) {
            case "&&": case "||": case "^^":
                b.type = Type.BOOL_T;
                return b;
            default:
                break;
        }
        if (!l.known() || !r.known()) {
            // still make literal operands of float-only contexts float when the other side is float
            if (l.isFloat() && b.r instanceof Lit && intBased(r)) {
                b.r = toFloatShape(b.r);
            } else if (r.isFloat() && b.l instanceof Lit && intBased(l)) {
                b.l = toFloatShape(b.l);
            }
            b.type = l.known() ? l : r;
            if (isComparison(op)) {
                b.type = Type.BOOL_T;
            }
            return b;
        }
        boolean arith = op.equals("+") || op.equals("-") || op.equals("*") || op.equals("/") || isComparison(op);
        if (arith) {
            if (l.isFloat() && intBased(r)) {
                b.r = toFloatShape(b.r);
                r = b.r.type;
            } else if (r.isFloat() && intBased(l)) {
                b.l = toFloatShape(b.l);
                l = b.l.type;
            }
        }
        if (isComparison(op)) {
            b.type = Type.BOOL_T;
            return b;
        }
        if (op.equals("<<") || op.equals(">>")) {
            b.type = l;
            return b;
        }
        if (op.equals("*") && (l.isMatrix() || r.isMatrix())) {
            if (l.isMatrix() && r.isMatrix()) {
                b.type = Type.mat(r.cols, l.rows);
            } else if (l.isMatrix() && r.isVector()) {
                b.type = Type.vec(Type.FLOAT, l.rows);
            } else if (l.isVector() && r.isMatrix()) {
                b.type = Type.vec(Type.FLOAT, r.cols);
            } else {
                b.type = l.isMatrix() ? l : r;
            }
            return b;
        }
        b.type = l.isScalar() ? r : l;
        return b;
    }

    private static boolean isComparison(String op) {
        return op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=") || op.equals("==")
                || op.equals("!=");
    }

    // ------------------------------------------------------------------ calls

    private Expr call(Call c) {
        for (int i = 0; i < c.args.size(); i++) {
            c.args.set(i, expr(c.args.get(i)));
        }
        if (c.receiver != null) {
            c.receiver = expr(c.receiver);
            c.type = Type.INT_T;
            return c;
        }
        if (c.arrayCtor != null) {
            Type el = resolve(stripArray(c.arrayCtor));
            for (int i = 0; i < c.args.size(); i++) {
                c.args.set(i, convert(c.args.get(i), el));
            }
            if (c.arrayCtor.arraySize != null) {
                c.arrayCtor.arraySize = expr(c.arrayCtor.arraySize);
            }
            c.type = el.arrayOf(c.args.size());
            return c;
        }
        if (c.name.equals("ftransform") && c.args.isEmpty()) {
            builtinDecls.put("tf_ModelViewProjectionMatrix", "uniform mat4");
            builtinDecls.put("tf_Vertex", "in vec4");
            compat = true;
            return new Raw("(tf_ModelViewProjectionMatrix * tf_Vertex)", VEC4);
        }
        Type ctor = Type.builtin(c.name);
        if (ctor != null) {
            if (c.name.startsWith("d")) { // double types: no doubles in GLSL ES
                c.name = ctor.glsl();
            }
            c.type = ctor;
            return c;
        }
        Type struct = structs.get(c.name);
        List<Sig> user = functions.get(c.name);
        if (struct != null && user == null) {
            for (int i = 0; i < c.args.size() && i < struct.fieldOrder.size(); i++) {
                c.args.set(i, convert(c.args.get(i), struct.fields.get(struct.fieldOrder.get(i))));
            }
            c.type = struct;
            return c;
        }
        if (user != null) {
            Sig sig = overload(user, c.args);
            if (sig != null) {
                for (int i = 0; i < c.args.size(); i++) {
                    if (!sig.output.get(i)) {
                        c.args.set(i, convert(c.args.get(i), sig.params.get(i)));
                    }
                }
                c.type = sig.ret;
                if (Builtins.ES_RESERVED.contains(c.name)) {
                    c.name = "tf_" + c.name;
                }
                return c;
            }
        }
        return builtinCall(c);
    }

    private static TypeSpec stripArray(TypeSpec t) {
        TypeSpec s = new TypeSpec();
        s.name = t.name;
        s.struct = t.struct;
        return s;
    }

    private static Sig overload(List<Sig> sigs, List<Expr> args) {
        for (Sig s : sigs) {
            if (s.params.size() == args.size() && matches(s, args, false)) {
                return s;
            }
        }
        for (Sig s : sigs) {
            if (s.params.size() == args.size() && matches(s, args, true)) {
                return s;
            }
        }
        return null;
    }

    private static boolean matches(Sig s, List<Expr> args, boolean convert) {
        for (int i = 0; i < args.size(); i++) {
            Type a = args.get(i).type;
            Type p = s.params.get(i);
            if (!a.known() || !p.known() || a.same(p)) {
                continue;
            }
            if (convert && !s.output.get(i) && p.base == Type.FLOAT && intBased(a) && p.cols == 1 && p.rows == a.rows
                    && p.array < 0) {
                continue;
            }
            return false;
        }
        return true;
    }

    /** Largest shape among the arguments (a matrix, else the longest vector), as a float type. */
    private static Type widest(List<Expr> args, int base) {
        Type best = null;
        for (Expr a : args) {
            Type t = a.type;
            if (!t.known() || t.isArray() || t.base == Type.BOOL) {
                continue;
            }
            if (best == null || t.cols > best.cols || (t.cols == best.cols && t.rows > best.rows)) {
                best = t;
            }
        }
        if (best == null) {
            return Type.UNKNOWN_T;
        }
        return best.isMatrix() ? Type.mat(best.cols, best.rows) : Type.vec(base, best.rows);
    }

    private static void floatArgs(Call c, int from, int to) {
        for (int i = from; i < Math.min(to, c.args.size()); i++) {
            Expr a = c.args.get(i);
            if (intBased(a.type)) {
                c.args.set(i, toFloatShape(a));
            }
        }
    }

    private static boolean allIntegral(List<Expr> args) {
        for (Expr a : args) {
            if (!intBased(a.type)) {
                return false;
            }
        }
        return true;
    }

    private static boolean anyUnknown(List<Expr> args) {
        for (Expr a : args) {
            if (!a.type.known()) {
                return true;
            }
        }
        return false;
    }

    private Expr builtinCall(Call c) {
        String n = c.name;
        List<Expr> args = c.args;
        int argc = args.size();
        if (n.equals("mix") && argc == 3 && args.get(2).type.base == Type.BOOL) {
            c.type = args.get(0).type;
            return c;
        }
        if (Builtins.GEN_FLOAT.contains(n)) {
            if (n.equals("modf")) {
                floatArgs(c, 0, 1);
            } else {
                floatArgs(c, 0, argc);
            }
            c.type = widest(args, Type.FLOAT);
            return c;
        }
        if (Builtins.GEN_INT_OK.contains(n)) {
            if (anyUnknown(args)) {
                c.type = widest(args, Type.FLOAT);
                return c;
            }
            if (allIntegral(args)) {
                c.type = widest(args, args.get(0).type.base);
                return c;
            }
            floatArgs(c, 0, argc);
            c.type = widest(args, Type.FLOAT);
            return c;
        }
        if (Builtins.RELATIONAL.contains(n)) {
            if (argc == 2 && args.get(0).type.isFloat() && intBased(args.get(1).type)) {
                floatArgs(c, 1, 2);
            } else if (argc == 2 && args.get(1).type.isFloat() && intBased(args.get(0).type)) {
                floatArgs(c, 0, 1);
            }
            c.type = Type.vec(Type.BOOL, argc > 0 ? Math.max(2, args.get(0).type.rows) : 2);
            return c;
        }
        switch (n) {
            case "length": case "distance": case "dot":
                floatArgs(c, 0, argc);
                c.type = Type.FLOAT_T;
                return c;
            case "cross":
                floatArgs(c, 0, argc);
                c.type = VEC3;
                return c;
            case "refract":
                floatArgs(c, 0, argc);
                c.type = argc > 0 ? widest(args.subList(0, 1), Type.FLOAT) : Type.UNKNOWN_T;
                return c;
            case "determinant":
                c.type = Type.FLOAT_T;
                return c;
            case "transpose":
                c.type = argc > 0 && args.get(0).type.isMatrix()
                        ? Type.mat(args.get(0).type.rows, args.get(0).type.cols) : Type.UNKNOWN_T;
                return c;
            case "inverse": case "matrixCompMult":
                c.type = argc > 0 ? args.get(0).type : Type.UNKNOWN_T;
                return c;
            case "outerProduct":
                c.type = argc == 2 ? Type.mat(args.get(1).type.rows, args.get(0).type.rows) : Type.UNKNOWN_T;
                return c;
            case "any": case "all":
                c.type = Type.BOOL_T;
                return c;
            case "not":
                c.type = argc > 0 ? args.get(0).type : Type.UNKNOWN_T;
                return c;
            case "isnan": case "isinf":
                c.type = argc > 0 ? Type.vec(Type.BOOL, args.get(0).type.rows) : Type.UNKNOWN_T;
                return c;
            case "floatBitsToInt":
                c.type = argc > 0 ? Type.vec(Type.INT, args.get(0).type.rows) : Type.UNKNOWN_T;
                return c;
            case "floatBitsToUint":
                c.type = argc > 0 ? Type.vec(Type.UINT, args.get(0).type.rows) : Type.UNKNOWN_T;
                return c;
            case "intBitsToFloat": case "uintBitsToFloat":
                c.type = argc > 0 ? Type.vec(Type.FLOAT, args.get(0).type.rows) : Type.UNKNOWN_T;
                return c;
            case "packHalf2x16": case "packUnorm2x16": case "packSnorm2x16":
                c.type = Type.UINT_T;
                return c;
            case "unpackHalf2x16": case "unpackUnorm2x16": case "unpackSnorm2x16":
                c.type = Type.vec(Type.FLOAT, 2);
                return c;
            case "texelFetch": case "texelFetch2D": case "texelFetchOffset":
                if (n.equals("texelFetch2D")) {
                    c.name = "texelFetch";
                }
                c.type = sampled(args);
                return c;
            case "textureSize": case "textureSize2D": {
                c.name = "textureSize";
                String s = argc > 0 && args.get(0).type.base == Type.SAMPLER ? args.get(0).type.name : "";
                c.type = Type.vec(Type.INT, s.contains("3D") || s.contains("Array") ? 3 : 2);
                return c;
            }
            default:
                break;
        }
        String es = Builtins.TEXTURE.get(n);
        if (es != null) {
            boolean legacyShadow = Builtins.LEGACY_SHADOW.contains(n);
            c.name = es;
            floatArgs(c, 1, Builtins.OFFSET_LAST.contains(es) ? argc - 1 : argc);
            Type sampler = argc > 0 ? args.get(0).type : Type.UNKNOWN_T;
            boolean shadow = sampler.base == Type.SAMPLER && sampler.name.contains("Shadow");
            if (legacyShadow || (shadow && n.startsWith("texture2D"))) {
                // the desktop functions return vec4; GLSL ES's texture() on a shadow sampler returns float
                Call wrap = new Call();
                wrap.name = "vec4";
                wrap.args.add(c);
                c.type = Type.FLOAT_T;
                wrap.type = VEC4;
                wrap.line = c.line;
                return wrap;
            }
            c.type = shadow ? Type.FLOAT_T : sampled(args);
            return c;
        }
        c.type = Type.UNKNOWN_T;
        return c;
    }

    private static Type sampled(List<Expr> args) {
        if (!args.isEmpty() && args.get(0).type.base == Type.SAMPLER) {
            String s = args.get(0).type.name;
            if (s.startsWith("isampler")) {
                return Type.vec(Type.INT, 4);
            }
            if (s.startsWith("usampler")) {
                return Type.vec(Type.UINT, 4);
            }
        }
        return VEC4;
    }
}
