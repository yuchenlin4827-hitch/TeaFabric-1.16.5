package teafabric.web.gl.glsl;

import teafabric.web.gl.glsl.Ast.*;

/** Writes a (translated) syntax tree back as GLSL source. Binary expressions are fully parenthesised. */
final class Printer {
    private final StringBuilder out = new StringBuilder();

    String result() {
        return out.toString();
    }

    void topLevel(Stmt s) {
        stmt(s, 0);
    }

    private void indent(int n) {
        for (int i = 0; i < n; i++) {
            out.append("    ");
        }
    }

    private void stmt(Stmt s, int ind) {
        if (s instanceof Block) {
            indent(ind);
            block((Block) s, ind);
            out.append('\n');
        } else if (s instanceof Decl) {
            indent(ind);
            decl((Decl) s, ind);
            out.append(";\n");
        } else if (s instanceof ExprStmt) {
            indent(ind);
            out.append(top(((ExprStmt) s).e)).append(";\n");
        } else if (s instanceof If) {
            If i = (If) s;
            indent(ind);
            out.append("if (").append(top(i.c)).append(")\n");
            body(i.then, ind);
            if (i.otherwise != null) {
                indent(ind);
                out.append("else\n");
                body(i.otherwise, ind);
            }
        } else if (s instanceof For) {
            For f = (For) s;
            indent(ind);
            out.append("for (");
            if (f.init instanceof Decl) {
                decl((Decl) f.init, ind);
            } else if (f.init instanceof ExprStmt) {
                out.append(top(((ExprStmt) f.init).e));
            }
            out.append("; ");
            if (f.cond != null) {
                out.append(top(f.cond));
            }
            out.append("; ");
            if (f.step != null) {
                out.append(top(f.step));
            }
            out.append(")\n");
            body(f.body, ind);
        } else if (s instanceof While) {
            While w = (While) s;
            indent(ind);
            out.append("while (").append(top(w.c)).append(")\n");
            body(w.body, ind);
        } else if (s instanceof DoWhile) {
            DoWhile d = (DoWhile) s;
            indent(ind);
            out.append("do\n");
            body(d.body, ind);
            indent(ind);
            out.append("while (").append(top(d.c)).append(");\n");
        } else if (s instanceof Switch) {
            Switch w = (Switch) s;
            indent(ind);
            out.append("switch (").append(top(w.e)).append(")\n");
            indent(ind);
            block(w.body, ind);
            out.append('\n');
        } else if (s instanceof Case) {
            Case c = (Case) s;
            indent(Math.max(0, ind - 1));
            out.append(c.value == null ? "default:\n" : "case " + top(c.value) + ":\n");
        } else if (s instanceof Jump) {
            Jump j = (Jump) s;
            indent(ind);
            out.append(j.kind);
            if (j.value != null) {
                out.append(' ').append(top(j.value));
            }
            out.append(";\n");
        } else if (s instanceof Empty) {
            indent(ind);
            out.append(";\n");
        } else if (s instanceof Verbatim) {
            indent(ind);
            out.append(((Verbatim) s).text).append('\n');
        } else if (s instanceof Function) {
            function((Function) s);
        }
    }

    private void body(Stmt s, int ind) {
        if (s instanceof Block) {
            stmt(s, ind);
        } else {
            stmt(s, ind + 1);
        }
    }

    private void block(Block b, int ind) {
        out.append("{\n");
        for (Stmt s : b.body) {
            stmt(s, ind + 1);
        }
        indent(ind);
        out.append('}');
    }

    private void qualifiers(Qualifiers q) {
        if (q.layout != null) {
            out.append("layout(").append(q.layout).append(") ");
        }
        for (String w : q.words) {
            out.append(w).append(' ');
        }
    }

    private void typeSpec(TypeSpec t, int ind) {
        if (t.struct != null) {
            if (!t.struct.block) {
                out.append("struct ");
            }
            if (t.struct.name != null) {
                out.append(t.struct.name).append(' ');
            }
            out.append("{\n");
            for (Decl m : t.struct.members) {
                indent(ind + 1);
                decl(m, ind + 1);
                out.append(";\n");
            }
            indent(ind);
            out.append('}');
        } else {
            Type b = Type.builtin(t.name);
            out.append(b != null && t.name.startsWith("d") && !t.name.equals("double") ? b.glsl()
                    : t.name.equals("double") ? "float" : t.name);
        }
        if (t.hasArray) {
            out.append('[');
            if (t.arraySize != null) {
                out.append(top(t.arraySize));
            }
            out.append(']');
        }
    }

    private void decl(Decl d, int ind) {
        qualifiers(d.q);
        typeSpec(d.type, ind);
        boolean first = true;
        for (Declarator v : d.vars) {
            out.append(first ? " " : ", ");
            first = false;
            out.append(v.name);
            if (v.hasArray) {
                out.append('[');
                if (v.arraySize != null) {
                    out.append(top(v.arraySize));
                }
                out.append(']');
            }
            if (v.init != null) {
                out.append(" = ").append(top(v.init));
            }
        }
    }

    private void function(Function f) {
        qualifiers(f.q);
        typeSpec(f.ret, 0);
        out.append(' ').append(f.name).append('(');
        for (int i = 0; i < f.params.size(); i++) {
            Param p = f.params.get(i);
            if (i > 0) {
                out.append(", ");
            }
            qualifiers(p.q);
            typeSpec(p.type, 0);
            if (p.name != null) {
                out.append(' ').append(p.name);
                if (p.hasArray) {
                    out.append('[');
                    if (p.arraySize != null) {
                        out.append(top(p.arraySize));
                    }
                    out.append(']');
                }
            }
        }
        out.append(')');
        if (f.body == null) {
            out.append(";\n");
        } else {
            out.append('\n');
            block(f.body, 0);
            out.append("\n\n");
        }
    }

    /** An expression in a position that needs no outer parentheses. */
    private static String top(Expr e) {
        String s = expr(e);
        if (s.startsWith("(") && (e instanceof Binary || e instanceof Assign || e instanceof Comma
                || e instanceof Cond) && balanced(s)) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /** True when the first '(' closes at the last character. */
    private static boolean balanced(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0 && i < s.length() - 1) {
                    return false;
                }
            }
        }
        return true;
    }

    static String expr(Expr e) {
        if (e instanceof Lit) {
            return ((Lit) e).text;
        }
        if (e instanceof Name) {
            return ((Name) e).name;
        }
        if (e instanceof Raw) {
            return ((Raw) e).text;
        }
        if (e instanceof Call) {
            Call c = (Call) e;
            StringBuilder sb = new StringBuilder();
            if (c.receiver != null) {
                return expr(c.receiver) + "." + c.name + "()";
            }
            if (c.arrayCtor != null) {
                Type b = Type.builtin(c.arrayCtor.name);
                sb.append(b != null ? b.glsl() : c.arrayCtor.name).append('[');
                if (c.arrayCtor.arraySize != null) {
                    sb.append(top(c.arrayCtor.arraySize));
                }
                sb.append(']');
            } else {
                sb.append(c.name);
            }
            sb.append('(');
            for (int i = 0; i < c.args.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                Expr a = c.args.get(i);
                sb.append(a instanceof Comma ? expr(a) : top(a));
            }
            return sb.append(')').toString();
        }
        if (e instanceof Index) {
            return expr(((Index) e).base) + "[" + top(((Index) e).index) + "]";
        }
        if (e instanceof Member) {
            return expr(((Member) e).base) + "." + ((Member) e).field;
        }
        if (e instanceof Unary) {
            Unary u = (Unary) e;
            return u.postfix ? "(" + expr(u.e) + u.op + ")" : "(" + u.op + expr(u.e) + ")";
        }
        if (e instanceof Binary) {
            Binary b = (Binary) e;
            return "(" + expr(b.l) + " " + b.op + " " + expr(b.r) + ")";
        }
        if (e instanceof Assign) {
            Assign a = (Assign) e;
            return "(" + expr(a.l) + " " + a.op + " " + expr(a.r) + ")";
        }
        if (e instanceof Cond) {
            Cond c = (Cond) e;
            return "(" + expr(c.c) + " ? " + expr(c.a) + " : " + expr(c.b) + ")";
        }
        if (e instanceof Comma) {
            return "(" + expr(((Comma) e).l) + ", " + expr(((Comma) e).r) + ")";
        }
        return "/*?*/";
    }
}
