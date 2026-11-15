package teafabric.web.gl.glsl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The GLSL preprocessor: #define (object- and function-like), #undef, #if/#ifdef/#ifndef/#elif/#else/#endif.
 * #version and #extension lines are kept; other directives are dropped. Line count is preserved.
 * (Iris already preprocesses shader packs with JCPP; Minecraft's and Sodium's shaders arrive unprocessed.)
 */
final class Preprocessor {
    private static final class Macro {
        List<String> params; // null: object-like
        String body;
    }

    private final Map<String, Macro> macros = new HashMap<>();
    private final StringBuilder out = new StringBuilder();
    private int version = 110;

    private Preprocessor() {
    }

    static String process(String source) {
        Preprocessor p = new Preprocessor();
        p.run(stripComments(source));
        return p.out.toString();
    }

    /** Removes comments, keeping newlines (and joining backslash-continued lines). */
    static String stripComments(String s) {
        StringBuilder b = new StringBuilder(s.length());
        int n = s.length();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                while (i < n && s.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                int end = s.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                b.append(' ');
                for (int k = i; k < end; k++) {
                    if (s.charAt(k) == '\n') {
                        b.append('\n');
                    }
                }
                i = end;
            } else if (c == '\\' && i + 1 < n && (s.charAt(i + 1) == '\n'
                    || (s.charAt(i + 1) == '\r' && i + 2 < n && s.charAt(i + 2) == '\n'))) {
                i += s.charAt(i + 1) == '\r' ? 3 : 2;
            } else if (c == '\r') {
                i++;
            } else {
                b.append(c);
                i++;
            }
        }
        return b.toString();
    }

    private void run(String src) {
        String[] lines = src.split("\n", -1);
        Deque<int[]> cond = new ArrayDeque<>(); // {active, taken, parentActive}
        boolean active = true;
        StringBuilder pending = null; // a line with an unclosed macro call
        for (int li = 0; li < lines.length; li++) {
            String line = lines[li];
            String t = line.trim();
            if (t.startsWith("#")) {
                String d = t.substring(1).trim();
                String word = d.split("[\\s(]", 2)[0];
                String rest = d.substring(word.length()).trim();
                switch (word) {
                    case "ifdef":
                    case "ifndef": {
                        boolean v = macros.containsKey(rest.split("\\s+")[0]) == word.equals("ifdef");
                        cond.push(new int[] {active && v ? 1 : 0, v ? 1 : 0, active ? 1 : 0});
                        active = active && v;
                        break;
                    }
                    case "if": {
                        boolean v = active && eval(rest) != 0;
                        cond.push(new int[] {v ? 1 : 0, v ? 1 : 0, active ? 1 : 0});
                        active = v;
                        break;
                    }
                    case "elif": {
                        int[] c = cond.peek();
                        if (c == null) {
                            throw new GlslException(li + 1, "#elif without #if");
                        }
                        boolean v = c[2] == 1 && c[1] == 0 && eval(rest) != 0;
                        if (v) {
                            c[1] = 1;
                        }
                        c[0] = v ? 1 : 0;
                        active = v;
                        break;
                    }
                    case "else": {
                        int[] c = cond.peek();
                        if (c == null) {
                            throw new GlslException(li + 1, "#else without #if");
                        }
                        boolean v = c[2] == 1 && c[1] == 0;
                        c[1] = 1;
                        c[0] = v ? 1 : 0;
                        active = v;
                        break;
                    }
                    case "endif": {
                        int[] c = cond.poll();
                        if (c == null) {
                            throw new GlslException(li + 1, "#endif without #if");
                        }
                        active = c[2] == 1;
                        break;
                    }
                    default:
                        if (active) {
                            directive(word, rest, li + 1);
                            if (word.equals("version") || word.equals("extension")) {
                                out.append(t);
                            }
                        }
                        break;
                }
                out.append('\n');
                continue;
            }
            if (!active) {
                out.append('\n');
                continue;
            }
            if (pending != null) {
                pending.append(' ').append(line);
                line = pending.toString();
                pending = null;
            }
            if (unbalancedCall(line)) {
                pending = new StringBuilder(line);
                out.append('\n');
                continue;
            }
            out.append(expand(line, new HashSet<>())).append('\n');
        }
        if (pending != null) {
            out.append(expand(pending.toString(), new HashSet<>())).append('\n');
        }
    }

    private void directive(String word, String rest, int line) {
        switch (word) {
            case "define": {
                int i = 0;
                while (i < rest.length() && (Character.isLetterOrDigit(rest.charAt(i)) || rest.charAt(i) == '_')) {
                    i++;
                }
                String name = rest.substring(0, i);
                Macro m = new Macro();
                if (i < rest.length() && rest.charAt(i) == '(') {
                    int close = rest.indexOf(')', i);
                    if (close < 0) {
                        throw new GlslException(line, "bad #define");
                    }
                    m.params = new ArrayList<>();
                    for (String p : rest.substring(i + 1, close).split(",")) {
                        if (!p.trim().isEmpty()) {
                            m.params.add(p.trim());
                        }
                    }
                    m.body = rest.substring(close + 1).trim();
                } else {
                    m.body = rest.substring(i).trim();
                }
                macros.put(name, m);
                break;
            }
            case "undef":
                macros.remove(rest.trim());
                break;
            case "version":
                try {
                    version = Integer.parseInt(rest.split("\\s+")[0]);
                } catch (NumberFormatException e) {
                    // ignore
                }
                break;
            case "error":
                throw new GlslException(line, "#error " + rest);
            default:
                break; // extension, line, pragma, include: no effect here
        }
    }

    /** True when the line ends inside the argument list of a function-like macro call. */
    private boolean unbalancedCall(String line) {
        int n = line.length();
        for (int i = 0; i < n; i++) {
            char c = line.charAt(i);
            if (Character.isLetter(c) || c == '_') {
                int s = i;
                while (i < n && (Character.isLetterOrDigit(line.charAt(i)) || line.charAt(i) == '_')) {
                    i++;
                }
                Macro m = macros.get(line.substring(s, i));
                if (m != null && m.params != null) {
                    int k = i;
                    while (k < n && Character.isWhitespace(line.charAt(k))) {
                        k++;
                    }
                    if (k < n && line.charAt(k) == '(') {
                        int depth = 0;
                        for (; k < n; k++) {
                            if (line.charAt(k) == '(') {
                                depth++;
                            } else if (line.charAt(k) == ')' && --depth == 0) {
                                break;
                            }
                        }
                        if (depth > 0) {
                            return true;
                        }
                    }
                }
                i--;
            }
        }
        return false;
    }

    private String expand(String text, Set<String> disabled) {
        StringBuilder b = new StringBuilder();
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (Character.isLetter(c) || c == '_') {
                int s = i;
                while (i < n && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) {
                    i++;
                }
                String id = text.substring(s, i);
                if (id.equals("__VERSION__")) {
                    b.append(version);
                    continue;
                }
                Macro m = disabled.contains(id) ? null : macros.get(id);
                if (m == null) {
                    b.append(id);
                    continue;
                }
                if (m.params == null) {
                    Set<String> d = new HashSet<>(disabled);
                    d.add(id);
                    b.append(expand(m.body, d));
                    continue;
                }
                int k = i;
                while (k < n && Character.isWhitespace(text.charAt(k))) {
                    k++;
                }
                if (k >= n || text.charAt(k) != '(') {
                    b.append(id);
                    continue;
                }
                List<String> args = new ArrayList<>();
                int depth = 0;
                int start = k + 1;
                for (k = k + 1; k < n; k++) {
                    char ch = text.charAt(k);
                    if (ch == '(') {
                        depth++;
                    } else if (ch == ')') {
                        if (depth == 0) {
                            break;
                        }
                        depth--;
                    } else if (ch == ',' && depth == 0) {
                        args.add(text.substring(start, k));
                        start = k + 1;
                    }
                }
                String last = text.substring(start, Math.min(k, n));
                if (!last.trim().isEmpty() || !args.isEmpty()) {
                    args.add(last);
                }
                i = Math.min(k + 1, n);
                Map<String, String> bind = new HashMap<>();
                for (int a = 0; a < m.params.size(); a++) {
                    bind.put(m.params.get(a), a < args.size() ? expand(args.get(a).trim(), disabled) : "");
                }
                Set<String> d = new HashSet<>(disabled);
                d.add(id);
                b.append(expand(substitute(m.body, bind), d));
                continue;
            }
            if (Character.isDigit(c)) { // keep numbers (with suffixes) intact
                int s = i;
                while (i < n && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '.')) {
                    i++;
                }
                b.append(text, s, i);
                continue;
            }
            b.append(c);
            i++;
        }
        return b.toString();
    }

    private static String substitute(String body, Map<String, String> bind) {
        StringBuilder b = new StringBuilder();
        int n = body.length();
        int i = 0;
        while (i < n) {
            char c = body.charAt(i);
            if (c == '#' && i + 1 < n && body.charAt(i + 1) == '#') { // token pasting
                while (b.length() > 0 && Character.isWhitespace(b.charAt(b.length() - 1))) {
                    b.setLength(b.length() - 1);
                }
                i += 2;
                while (i < n && Character.isWhitespace(body.charAt(i))) {
                    i++;
                }
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int s = i;
                while (i < n && (Character.isLetterOrDigit(body.charAt(i)) || body.charAt(i) == '_')) {
                    i++;
                }
                String id = body.substring(s, i);
                String v = bind.get(id);
                b.append(v != null ? v : id);
                continue;
            }
            b.append(c);
            i++;
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ #if expressions

    private String exprText;
    private int exprPos;

    private long eval(String expr) {
        // defined(X) / defined X before expansion
        StringBuilder b = new StringBuilder();
        int n = expr.length();
        for (int i = 0; i < n; i++) {
            if (expr.startsWith("defined", i) && (i == 0 || !Character.isLetterOrDigit(expr.charAt(i - 1)))
                    && (i + 7 >= n || !(Character.isLetterOrDigit(expr.charAt(i + 7)) || expr.charAt(i + 7) == '_'))) {
                int k = i + 7;
                while (k < n && Character.isWhitespace(expr.charAt(k))) {
                    k++;
                }
                boolean paren = k < n && expr.charAt(k) == '(';
                if (paren) {
                    k++;
                }
                while (k < n && Character.isWhitespace(expr.charAt(k))) {
                    k++;
                }
                int s = k;
                while (k < n && (Character.isLetterOrDigit(expr.charAt(k)) || expr.charAt(k) == '_')) {
                    k++;
                }
                b.append(macros.containsKey(expr.substring(s, k)) ? " 1 " : " 0 ");
                if (paren) {
                    while (k < n && expr.charAt(k) != ')') {
                        k++;
                    }
                    k++;
                }
                i = k - 1;
                continue;
            }
            b.append(expr.charAt(i));
        }
        exprText = expand(b.toString(), new HashSet<>());
        exprPos = 0;
        long v = ternary();
        return v;
    }

    private void skipWs() {
        while (exprPos < exprText.length() && Character.isWhitespace(exprText.charAt(exprPos))) {
            exprPos++;
        }
    }

    private boolean take(String op) {
        skipWs();
        if (exprText.startsWith(op, exprPos)) {
            // do not split longer operators: "<" must not match "<<" or "<=", "&" not "&&", ...
            int e = exprPos + op.length();
            if (e < exprText.length()) {
                char nx = exprText.charAt(e);
                if ((op.equals("<") || op.equals(">")) && (nx == '=' || nx == op.charAt(0))) {
                    return false;
                }
                if ((op.equals("&") || op.equals("|")) && nx == op.charAt(0)) {
                    return false;
                }
                if ((op.equals("!") || op.equals("=")) && nx == '=' && op.length() == 1) {
                    return false;
                }
            }
            exprPos = e;
            return true;
        }
        return false;
    }

    private long ternary() {
        long c = binary(0);
        if (take("?")) {
            long a = ternary();
            take(":");
            long b = ternary();
            return c != 0 ? a : b;
        }
        return c;
    }

    private static final String[][] OPS = {
        {"||"}, {"&&"}, {"|"}, {"^"}, {"&"}, {"==", "!="}, {"<=", ">=", "<", ">"}, {"<<", ">>"}, {"+", "-"},
        {"*", "/", "%"},
    };

    private long binary(int level) {
        if (level == OPS.length) {
            return unary();
        }
        long l = binary(level + 1);
        outer:
        while (true) {
            for (String op : OPS[level]) {
                if (take(op)) {
                    long r = binary(level + 1);
                    switch (op) {
                        case "||": l = (l != 0 || r != 0) ? 1 : 0; break;
                        case "&&": l = (l != 0 && r != 0) ? 1 : 0; break;
                        case "|": l = l | r; break;
                        case "^": l = l ^ r; break;
                        case "&": l = l & r; break;
                        case "==": l = l == r ? 1 : 0; break;
                        case "!=": l = l != r ? 1 : 0; break;
                        case "<=": l = l <= r ? 1 : 0; break;
                        case ">=": l = l >= r ? 1 : 0; break;
                        case "<": l = l < r ? 1 : 0; break;
                        case ">": l = l > r ? 1 : 0; break;
                        case "<<": l = l << r; break;
                        case ">>": l = l >> r; break;
                        case "+": l = l + r; break;
                        case "-": l = l - r; break;
                        case "*": l = l * r; break;
                        case "/": l = r == 0 ? 0 : l / r; break;
                        default: l = r == 0 ? 0 : l % r; break;
                    }
                    continue outer;
                }
            }
            return l;
        }
    }

    private long unary() {
        if (take("!")) {
            return unary() == 0 ? 1 : 0;
        }
        if (take("-")) {
            return -unary();
        }
        if (take("+")) {
            return unary();
        }
        if (take("~")) {
            return ~unary();
        }
        if (take("(")) {
            long v = ternary();
            take(")");
            return v;
        }
        skipWs();
        int s = exprPos;
        while (exprPos < exprText.length()
                && (Character.isLetterOrDigit(exprText.charAt(exprPos)) || exprText.charAt(exprPos) == '_'
                || exprText.charAt(exprPos) == '.')) {
            exprPos++;
        }
        String tok = exprText.substring(s, exprPos);
        if (tok.isEmpty()) {
            if (exprPos < exprText.length()) {
                exprPos++;
            }
            return 0;
        }
        if (Character.isDigit(tok.charAt(0))) {
            String num = tok.replaceAll("[uUlL]+$", "");
            try {
                if (num.contains(".")) {
                    return (long) Double.parseDouble(num.replaceAll("[fF]$", ""));
                }
                return Long.decode(num);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0; // undefined identifiers are 0
    }
}
