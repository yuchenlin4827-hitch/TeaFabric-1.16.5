package teafabric.web.gl.glsl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import teafabric.web.gl.glsl.Ast.*;
import teafabric.web.gl.glsl.Lexer.Tok;

/** Recursive-descent GLSL parser (GLSL 1.10 to 4.x syntax as used by shader packs). */
final class Parser {
    private static final Set<String> QUALIFIERS = new HashSet<>(java.util.Arrays.asList("const", "uniform", "in",
            "out", "inout", "attribute", "varying", "centroid", "flat", "smooth", "noperspective", "invariant",
            "highp", "mediump", "lowp", "precise", "buffer", "shared", "patch", "sample", "coherent", "volatile",
            "restrict", "readonly", "writeonly"));

    private final List<Tok> toks;
    private int pos;
    private final Set<String> structNames = new HashSet<>();

    private Parser(List<Tok> toks) {
        this.toks = toks;
    }

    static Unit parse(String source) {
        Parser p = new Parser(Lexer.lex(source));
        return p.unit();
    }

    // ------------------------------------------------------------------ token helpers

    private Tok peek() {
        return toks.get(pos);
    }

    private Tok peek(int k) {
        return toks.get(Math.min(pos + k, toks.size() - 1));
    }

    private Tok next() {
        return toks.get(pos++);
    }

    private boolean at(String s) {
        return peek().is(s);
    }

    private boolean accept(String s) {
        if (at(s)) {
            pos++;
            return true;
        }
        return false;
    }

    private Tok expect(String s) {
        if (!at(s)) {
            throw new GlslException(peek().line, "expected '" + s + "' but found '" + peek().text + "'");
        }
        return next();
    }

    private String ident() {
        Tok t = peek();
        if (t.kind != Lexer.IDENT) {
            throw new GlslException(t.line, "expected a name but found '" + t.text + "'");
        }
        pos++;
        return t.text;
    }

    private boolean isTypeName(Tok t) {
        return t.kind == Lexer.IDENT && (Type.builtin(t.text) != null || structNames.contains(t.text)
                || t.text.equals("struct"));
    }

    /** Qualifier words that older GLSL versions allow as identifiers (a variable named "sample" is common). */
    private static final Set<String> SOFT = new HashSet<>(java.util.Arrays.asList("sample", "patch", "buffer",
            "shared", "precise", "coherent", "volatile", "restrict", "readonly", "writeonly", "flat", "smooth",
            "noperspective"));

    private boolean isQualifierAt(int k) {
        Tok t = peek(k);
        if (t.kind != Lexer.IDENT) {
            return false;
        }
        if (t.text.equals("layout")) {
            return peek(k + 1).is("(");
        }
        if (!QUALIFIERS.contains(t.text)) {
            return false;
        }
        if (!SOFT.contains(t.text)) {
            return true;
        }
        Tok n = peek(k + 1);
        return n.kind == Lexer.IDENT && (isTypeName(n) || isQualifierAt(k + 1));
    }

    // ------------------------------------------------------------------ top level

    private Unit unit() {
        Unit u = new Unit();
        while (peek().kind != Lexer.EOF) {
            Tok t = peek();
            if (t.kind == Lexer.DIRECTIVE) {
                pos++;
                u.directives.add(t.text);
                String[] w = t.text.substring(1).trim().split("\\s+");
                if (w.length >= 2 && w[0].equals("version")) {
                    try {
                        u.version = Integer.parseInt(w[1]);
                    } catch (NumberFormatException e) {
                        // keep the default
                    }
                    u.profile = w.length >= 3 ? w[2] : "";
                }
                continue;
            }
            if (accept(";")) {
                continue;
            }
            u.decls.add(external());
        }
        return u;
    }

    private Stmt external() {
        int line = peek().line;
        if (at("precision")) {
            StringBuilder sb = new StringBuilder();
            while (!at(";")) {
                sb.append(next().text).append(' ');
            }
            next();
            Verbatim v = new Verbatim(sb.toString().trim() + ";");
            v.line = line;
            return v;
        }
        Qualifiers q = qualifiers();
        if (at(";")) { // layout(...) in;  or similar
            next();
            Verbatim v = new Verbatim((q.layout != null ? "layout(" + q.layout + ") " : "")
                    + String.join(" ", q.words) + ";");
            v.line = line;
            return v;
        }
        if (peek().kind == Lexer.IDENT && !isTypeName(peek()) && peek(1).is(";")) { // invariant gl_Position;
            String name = next().text;
            next();
            Verbatim v = new Verbatim(String.join(" ", q.words) + " " + name + ";");
            v.line = line;
            return v;
        }
        if (peek().kind == Lexer.IDENT && !isTypeName(peek()) && peek(1).is("{")) { // interface block
            Decl d = new Decl();
            d.line = line;
            d.q = q;
            d.type = new TypeSpec();
            d.type.struct = new StructBody();
            d.type.struct.block = true;
            d.type.struct.name = ident();
            d.type.name = d.type.struct.name;
            structBody(d.type.struct);
            if (!at(";")) {
                declarators(d);
            }
            expect(";");
            return d;
        }
        TypeSpec type = typeSpec();
        if (peek().kind == Lexer.IDENT && peek(1).is("(")) {
            Function f = new Function();
            f.line = line;
            f.q = q;
            f.ret = type;
            f.name = ident();
            expect("(");
            if (!at(")")) {
                if (at("void") && peek(1).is(")")) {
                    next();
                } else {
                    do {
                        f.params.add(param());
                    } while (accept(","));
                }
            }
            expect(")");
            if (accept(";")) {
                return f;
            }
            f.body = block();
            return f;
        }
        Decl d = new Decl();
        d.line = line;
        d.q = q;
        d.type = type;
        if (!at(";")) {
            declarators(d);
        }
        expect(";");
        return d;
    }

    private Qualifiers qualifiers() {
        Qualifiers q = new Qualifiers();
        while (isQualifierAt(0)) {
            if (at("layout")) {
                next();
                expect("(");
                StringBuilder sb = new StringBuilder();
                int depth = 1;
                while (true) {
                    Tok t = next();
                    if (t.is("(")) {
                        depth++;
                    } else if (t.is(")") && --depth == 0) {
                        break;
                    }
                    if (t.kind == Lexer.EOF) {
                        throw new GlslException(t.line, "unterminated layout()");
                    }
                    sb.append(t.text).append(t.is(",") ? " " : "");
                }
                q.layout = q.layout == null ? sb.toString() : q.layout + ", " + sb;
            } else {
                q.words.add(next().text);
            }
        }
        return q;
    }

    private TypeSpec typeSpec() {
        TypeSpec t = new TypeSpec();
        if (accept("struct")) {
            t.struct = new StructBody();
            if (peek().kind == Lexer.IDENT && !at("{")) {
                t.struct.name = ident();
                structNames.add(t.struct.name);
            }
            t.name = t.struct.name;
            structBody(t.struct);
        } else {
            Tok n = peek();
            if (!isTypeName(n)) {
                throw new GlslException(n.line, "expected a type but found '" + n.text + "'");
            }
            t.name = next().text;
        }
        if (at("[")) {
            next();
            t.hasArray = true;
            if (!at("]")) {
                t.arraySize = expression();
            }
            expect("]");
        }
        return t;
    }

    private void structBody(StructBody s) {
        expect("{");
        while (!accept("}")) {
            Decl m = new Decl();
            m.line = peek().line;
            m.q = qualifiers();
            m.type = typeSpec();
            declarators(m);
            expect(";");
            s.members.add(m);
        }
    }

    private void declarators(Decl d) {
        do {
            Declarator v = new Declarator();
            v.name = ident();
            if (accept("[")) {
                v.hasArray = true;
                if (!at("]")) {
                    v.arraySize = expression();
                }
                expect("]");
            }
            if (accept("=")) {
                v.init = assignment();
            }
            d.vars.add(v);
        } while (accept(","));
    }

    private Param param() {
        Param p = new Param();
        p.q = qualifiers();
        p.type = typeSpec();
        if (peek().kind == Lexer.IDENT) {
            p.name = ident();
            if (accept("[")) {
                p.hasArray = true;
                if (!at("]")) {
                    p.arraySize = expression();
                }
                expect("]");
            }
        }
        return p;
    }

    // ------------------------------------------------------------------ statements

    private Block block() {
        Block b = new Block();
        b.line = peek().line;
        expect("{");
        while (!accept("}")) {
            if (peek().kind == Lexer.EOF) {
                throw new GlslException(peek().line, "unterminated block");
            }
            Stmt s = statement();
            if (s != null) {
                b.body.add(s);
            }
        }
        return b;
    }

    private boolean startsDeclaration() {
        Tok t = peek();
        if (isQualifierAt(0) || t.is("struct")) {
            return true;
        }
        if (!isTypeName(t)) {
            return false;
        }
        Tok n = peek(1);
        if (n.kind == Lexer.IDENT) {
            return true;
        }
        if (n.is("[")) { // float[3] a;  vs  float[3](...)
            int depth = 0;
            for (int k = 1; pos + k < toks.size(); k++) {
                Tok x = peek(k);
                if (x.is("[")) {
                    depth++;
                } else if (x.is("]") && --depth == 0) {
                    return peek(k + 1).kind == Lexer.IDENT;
                }
            }
        }
        return false;
    }

    private Stmt statement() {
        Tok t = peek();
        int line = t.line;
        Stmt s;
        if (t.kind == Lexer.DIRECTIVE) {
            next();
            return null;
        }
        if (at("{")) {
            s = block();
        } else if (accept(";")) {
            s = new Empty();
        } else if (accept("if")) {
            If i = new If();
            expect("(");
            i.c = expression();
            expect(")");
            i.then = statement();
            if (accept("else")) {
                i.otherwise = statement();
            }
            s = i;
        } else if (accept("for")) {
            For f = new For();
            expect("(");
            if (accept(";")) {
                f.init = null;
            } else if (startsDeclaration()) {
                f.init = declaration();
            } else {
                ExprStmt es = new ExprStmt();
                es.e = expression();
                expect(";");
                f.init = es;
            }
            if (!at(";")) {
                f.cond = expression();
            }
            expect(";");
            if (!at(")")) {
                f.step = expression();
            }
            expect(")");
            f.body = statement();
            s = f;
        } else if (accept("while")) {
            While w = new While();
            expect("(");
            w.c = expression();
            expect(")");
            w.body = statement();
            s = w;
        } else if (accept("do")) {
            DoWhile d = new DoWhile();
            d.body = statement();
            expect("while");
            expect("(");
            d.c = expression();
            expect(")");
            expect(";");
            s = d;
        } else if (accept("switch")) {
            Switch sw = new Switch();
            expect("(");
            sw.e = expression();
            expect(")");
            sw.body = block();
            s = sw;
        } else if (accept("case")) {
            Case c = new Case();
            c.value = expression();
            expect(":");
            s = c;
        } else if (at("default") && peek(1).is(":")) {
            next();
            next();
            s = new Case();
        } else if (at("break") || at("continue") || at("discard")) {
            Jump j = new Jump();
            j.kind = next().text;
            expect(";");
            s = j;
        } else if (accept("return")) {
            Jump j = new Jump();
            j.kind = "return";
            if (!at(";")) {
                j.value = expression();
            }
            expect(";");
            s = j;
        } else if (startsDeclaration()) {
            s = declaration();
        } else {
            ExprStmt es = new ExprStmt();
            es.e = expression();
            expect(";");
            s = es;
        }
        s.line = line;
        return s;
    }

    private Decl declaration() {
        Decl d = new Decl();
        d.line = peek().line;
        d.q = qualifiers();
        d.type = typeSpec();
        if (!at(";")) {
            declarators(d);
        }
        expect(";");
        return d;
    }

    // ------------------------------------------------------------------ expressions

    Expr expression() {
        Expr e = assignment();
        while (at(",")) {
            int line = next().line;
            Comma c = new Comma();
            c.line = line;
            c.l = e;
            c.r = assignment();
            e = c;
        }
        return e;
    }

    private static boolean isAssignOp(Tok t) {
        if (t.kind != Lexer.PUNCT) {
            return false;
        }
        switch (t.text) {
            case "=": case "+=": case "-=": case "*=": case "/=": case "%=": case "<<=": case ">>=": case "&=":
            case "^=": case "|=":
                return true;
            default:
                return false;
        }
    }

    private Expr assignment() {
        Expr l = conditional();
        if (isAssignOp(peek())) {
            Tok op = next();
            Assign a = new Assign();
            a.line = op.line;
            a.op = op.text;
            a.l = l;
            a.r = assignment();
            return a;
        }
        return l;
    }

    private Expr conditional() {
        Expr c = binary(0);
        if (at("?")) {
            int line = next().line;
            Cond e = new Cond();
            e.line = line;
            e.c = c;
            e.a = expression();
            expect(":");
            e.b = assignment();
            return e;
        }
        return c;
    }

    private static final String[][] LEVELS = {
        {"||"}, {"^^"}, {"&&"}, {"|"}, {"^"}, {"&"}, {"==", "!="}, {"<", ">", "<=", ">="}, {"<<", ">>"},
        {"+", "-"}, {"*", "/", "%"},
    };

    private Expr binary(int level) {
        if (level == LEVELS.length) {
            return unary();
        }
        Expr l = binary(level + 1);
        while (true) {
            Tok t = peek();
            String op = null;
            if (t.kind == Lexer.PUNCT) {
                for (String s : LEVELS[level]) {
                    if (t.text.equals(s)) {
                        op = s;
                    }
                }
            }
            if (op == null) {
                return l;
            }
            next();
            Binary b = new Binary();
            b.line = t.line;
            b.op = op;
            b.l = l;
            b.r = binary(level + 1);
            l = b;
        }
    }

    private Expr unary() {
        Tok t = peek();
        if (t.kind == Lexer.PUNCT && (t.is("++") || t.is("--") || t.is("+") || t.is("-") || t.is("!")
                || t.is("~"))) {
            next();
            Unary u = new Unary();
            u.line = t.line;
            u.op = t.text;
            u.e = unary();
            return u;
        }
        return postfix(primary());
    }

    private Expr postfix(Expr e) {
        while (true) {
            Tok t = peek();
            if (t.is("[")) {
                next();
                Index i = new Index();
                i.line = t.line;
                i.base = e;
                i.index = expression();
                expect("]");
                e = i;
            } else if (t.is(".")) {
                next();
                String field = ident();
                if (at("(")) { // method call: only length()
                    next();
                    expect(")");
                    Call c = new Call();
                    c.line = t.line;
                    c.name = field;
                    c.receiver = e;
                    e = c;
                } else {
                    Member m = new Member();
                    m.line = t.line;
                    m.base = e;
                    m.field = field;
                    e = m;
                }
            } else if (t.is("++") || t.is("--")) {
                next();
                Unary u = new Unary();
                u.line = t.line;
                u.op = t.text;
                u.postfix = true;
                u.e = e;
                e = u;
            } else {
                return e;
            }
        }
    }

    private Expr primary() {
        Tok t = next();
        switch (t.kind) {
            case Lexer.INT: {
                boolean unsigned = t.text.endsWith("u") || t.text.endsWith("U");
                Lit l = new Lit(t.text, unsigned ? Type.UINT : Type.INT);
                l.line = t.line;
                return l;
            }
            case Lexer.FLOAT: {
                Lit l = new Lit(t.text, Type.FLOAT);
                l.line = t.line;
                return l;
            }
            case Lexer.IDENT: {
                if (t.text.equals("true") || t.text.equals("false")) {
                    Lit l = new Lit(t.text, Type.BOOL);
                    l.line = t.line;
                    return l;
                }
                if (at("[") && isTypeName(t)) { // array constructor: float[3](...)
                    int save = pos;
                    next();
                    TypeSpec spec = new TypeSpec();
                    spec.name = t.text;
                    spec.hasArray = true;
                    if (!at("]")) {
                        spec.arraySize = expression();
                    }
                    expect("]");
                    if (at("(")) {
                        Call c = new Call();
                        c.line = t.line;
                        c.name = t.text;
                        c.arrayCtor = spec;
                        args(c);
                        return c;
                    }
                    pos = save;
                }
                if (at("(")) {
                    Call c = new Call();
                    c.line = t.line;
                    c.name = t.text;
                    args(c);
                    return c;
                }
                Name n = new Name(t.text);
                n.line = t.line;
                return n;
            }
            default:
                if (t.is("(")) {
                    Expr e = expression();
                    expect(")");
                    return e;
                }
                throw new GlslException(t.line, "unexpected '" + t.text + "'");
        }
    }

    private void args(Call c) {
        expect("(");
        if (at("void") && peek(1).is(")")) {
            next();
        }
        if (!at(")")) {
            do {
                c.args.add(assignment());
            } while (accept(","));
        }
        expect(")");
    }
}
