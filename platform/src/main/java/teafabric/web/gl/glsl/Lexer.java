package teafabric.web.gl.glsl;

import java.util.ArrayList;
import java.util.List;

/** GLSL tokens. Comments are dropped; preprocessor lines become single DIRECTIVE tokens. */
final class Lexer {
    static final int IDENT = 0;
    static final int INT = 1;
    static final int FLOAT = 2;
    static final int PUNCT = 3;
    static final int DIRECTIVE = 4;
    static final int EOF = 5;

    static final class Tok {
        final int kind;
        final String text;
        final int line;

        Tok(int kind, String text, int line) {
            this.kind = kind;
            this.text = text;
            this.line = line;
        }

        boolean is(String s) {
            return (kind == PUNCT || kind == IDENT) && text.equals(s);
        }

        @Override
        public String toString() {
            return text;
        }
    }

    private static final String[] PUNCTS = {
        "<<=", ">>=", "++", "--", "<<", ">>", "<=", ">=", "==", "!=", "&&", "||", "^^", "+=", "-=", "*=", "/=",
        "%=", "&=", "^=", "|=", "+", "-", "*", "/", "%", "<", ">", "&", "^", "|", "!", "~", "?", ":", "=", "(",
        ")", "[", "]", "{", "}", ".", ",", ";",
    };

    private Lexer() {
    }

    static List<Tok> lex(String src) {
        List<Tok> out = new ArrayList<>();
        int i = 0;
        int n = src.length();
        int line = 1;
        boolean lineStart = true;
        while (i < n) {
            char c = src.charAt(i);
            if (c == '\n') {
                line++;
                i++;
                lineStart = true;
                continue;
            }
            if (c == ' ' || c == '\t' || c == '\r' || c == '\f' || c == 0x0b) {
                i++;
                continue;
            }
            if (c == '\\' && i + 1 < n && (src.charAt(i + 1) == '\n' || src.charAt(i + 1) == '\r')) {
                i += src.charAt(i + 1) == '\r' && i + 2 < n && src.charAt(i + 2) == '\n' ? 3 : 2;
                line++;
                continue;
            }
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                int end = src.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                for (int k = i; k < end; k++) {
                    if (src.charAt(k) == '\n') {
                        line++;
                    }
                }
                i = end;
                continue;
            }
            if (c == '#' && lineStart) {
                StringBuilder d = new StringBuilder();
                int startLine = line;
                while (i < n && src.charAt(i) != '\n') {
                    char ch = src.charAt(i);
                    if (ch == '\\' && i + 1 < n && src.charAt(i + 1) == '\n') {
                        i += 2;
                        line++;
                        d.append(' ');
                        continue;
                    }
                    if (ch == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                        while (i < n && src.charAt(i) != '\n') {
                            i++;
                        }
                        break;
                    }
                    d.append(ch);
                    i++;
                }
                out.add(new Tok(DIRECTIVE, d.toString().trim(), startLine));
                continue;
            }
            lineStart = false;
            if (Character.isLetter(c) || c == '_') {
                int s = i;
                while (i < n && (Character.isLetterOrDigit(src.charAt(i)) || src.charAt(i) == '_')) {
                    i++;
                }
                out.add(new Tok(IDENT, src.substring(s, i), line));
                continue;
            }
            if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(src.charAt(i + 1)))) {
                int s = i;
                boolean isFloat = false;
                if (c == '0' && i + 1 < n && (src.charAt(i + 1) == 'x' || src.charAt(i + 1) == 'X')) {
                    i += 2;
                    while (i < n && Character.digit(src.charAt(i), 16) >= 0) {
                        i++;
                    }
                } else {
                    while (i < n && Character.isDigit(src.charAt(i))) {
                        i++;
                    }
                    if (i < n && src.charAt(i) == '.') {
                        isFloat = true;
                        i++;
                        while (i < n && Character.isDigit(src.charAt(i))) {
                            i++;
                        }
                    }
                    if (i < n && (src.charAt(i) == 'e' || src.charAt(i) == 'E')) {
                        int save = i;
                        i++;
                        if (i < n && (src.charAt(i) == '+' || src.charAt(i) == '-')) {
                            i++;
                        }
                        if (i < n && Character.isDigit(src.charAt(i))) {
                            isFloat = true;
                            while (i < n && Character.isDigit(src.charAt(i))) {
                                i++;
                            }
                        } else {
                            i = save;
                        }
                    }
                }
                // suffixes: u/U (uint), f/F, lf/LF (double)
                if (i < n && (src.charAt(i) == 'u' || src.charAt(i) == 'U')) {
                    i++;
                } else if (i < n && (src.charAt(i) == 'f' || src.charAt(i) == 'F')) {
                    isFloat = true;
                    i++;
                } else if (i + 1 < n && (src.charAt(i) == 'l' || src.charAt(i) == 'L')
                        && (src.charAt(i + 1) == 'f' || src.charAt(i + 1) == 'F')) {
                    isFloat = true;
                    i += 2;
                }
                out.add(new Tok(isFloat ? FLOAT : INT, src.substring(s, i), line));
                continue;
            }
            String p = null;
            for (String q : PUNCTS) {
                if (src.startsWith(q, i)) {
                    p = q;
                    break;
                }
            }
            if (p == null) {
                throw new GlslException(line, "unexpected character '" + c + "'");
            }
            out.add(new Tok(PUNCT, p, line));
            i += p.length();
        }
        out.add(new Tok(EOF, "<end>", line));
        return out;
    }
}
