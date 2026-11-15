package teafabric.web.gl.glsl;

import java.util.ArrayList;
import java.util.List;

/** Syntax tree of a GLSL translation unit, as the translator needs it. */
final class Ast {
    private Ast() {
    }

    // ------------------------------------------------------------------ expressions

    abstract static class Expr {
        Type type = Type.UNKNOWN_T;
        int line;
    }

    static final class Lit extends Expr {
        String text;
        int kind; // Type.INT, UINT, FLOAT, BOOL

        Lit(String text, int kind) {
            this.text = text;
            this.kind = kind;
        }
    }

    static final class Name extends Expr {
        String name;

        Name(String name) {
            this.name = name;
        }
    }

    /** Function call or constructor; {@code receiver} is set for {@code a.length()}. */
    static final class Call extends Expr {
        String name;
        /** For array constructors such as float[3](...): the array type. */
        TypeSpec arrayCtor;
        final List<Expr> args = new ArrayList<>();
        Expr receiver;
    }

    static final class Index extends Expr {
        Expr base;
        Expr index;
    }

    static final class Member extends Expr {
        Expr base;
        String field;
    }

    static final class Unary extends Expr {
        String op;
        Expr e;
        boolean postfix;
    }

    static final class Binary extends Expr {
        String op;
        Expr l;
        Expr r;
    }

    static final class Assign extends Expr {
        String op;
        Expr l;
        Expr r;
    }

    static final class Cond extends Expr {
        Expr c;
        Expr a;
        Expr b;
    }

    static final class Comma extends Expr {
        Expr l;
        Expr r;
    }

    /** Text substituted by the translator (a renamed built-in, an inserted conversion's argument, ...). */
    static final class Raw extends Expr {
        String text;

        Raw(String text, Type type) {
            this.text = text;
            this.type = type;
        }
    }

    // ------------------------------------------------------------------ types and declarations

    static final class TypeSpec {
        String name; // built-in or struct name
        StructBody struct; // inline struct definition
        boolean hasArray;
        Expr arraySize; // null: unsized
        Type resolved = Type.UNKNOWN_T;
    }

    static final class StructBody {
        String name; // may be null (anonymous)
        final List<Decl> members = new ArrayList<>();
        boolean block; // interface block (uniform Name { ... })
    }

    static final class Declarator {
        String name;
        boolean hasArray;
        Expr arraySize;
        Expr init;
        Type type = Type.UNKNOWN_T;
    }

    static final class Qualifiers {
        String layout; // contents of layout(...), or null
        final List<String> words = new ArrayList<>(); // storage/interpolation/precision words in order

        boolean has(String w) {
            return words.contains(w);
        }

        void remove(String w) {
            words.remove(w);
        }

        void replace(String from, String to) {
            int i = words.indexOf(from);
            if (i >= 0) {
                words.set(i, to);
            }
        }
    }

    // ------------------------------------------------------------------ statements

    abstract static class Stmt {
        int line;
    }

    static final class Block extends Stmt {
        final List<Stmt> body = new ArrayList<>();
    }

    static final class Decl extends Stmt {
        Qualifiers q = new Qualifiers();
        TypeSpec type;
        final List<Declarator> vars = new ArrayList<>();
    }

    static final class ExprStmt extends Stmt {
        Expr e;
    }

    static final class If extends Stmt {
        Expr c;
        Stmt then;
        Stmt otherwise;
    }

    static final class For extends Stmt {
        Stmt init;
        Expr cond;
        Expr step;
        Stmt body;
    }

    static final class While extends Stmt {
        Expr c;
        Stmt body;
    }

    static final class DoWhile extends Stmt {
        Stmt body;
        Expr c;
    }

    static final class Switch extends Stmt {
        Expr e;
        Block body;
    }

    static final class Case extends Stmt {
        Expr value; // null: default
    }

    static final class Jump extends Stmt {
        String kind; // break, continue, discard, return
        Expr value;
    }

    static final class Empty extends Stmt {
    }

    /** A statement kept verbatim (precision statements, unknown top-level constructs). */
    static final class Verbatim extends Stmt {
        String text;

        Verbatim(String text) {
            this.text = text;
        }
    }

    // ------------------------------------------------------------------ functions

    static final class Param {
        Qualifiers q = new Qualifiers();
        TypeSpec type;
        String name; // may be null in prototypes
        boolean hasArray;
        Expr arraySize;
        Type resolved = Type.UNKNOWN_T;
    }

    static final class Function extends Stmt {
        Qualifiers q = new Qualifiers();
        TypeSpec ret;
        String name;
        final List<Param> params = new ArrayList<>();
        Block body; // null: prototype
    }

    static final class Unit {
        final List<Stmt> decls = new ArrayList<>();
        final List<String> directives = new ArrayList<>();
        int version = 110;
        String profile = "";
    }
}
