package teafabric.web.compat;

import java.util.regex.Matcher;

/**
 * JDK-conformant Matcher.replaceAll: a reference to a group that did not participate in the match
 * expands to the empty string (TeaVM's implementation throws IllegalArgumentException).
 *
 * <p>Also the regex syntax of Java 8 that TeaVM's (Java 6 era) engine lacks: {@code \R} (any line break; Iris
 * splits shader pack files with it) and {@code \h} (horizontal white space) are rewritten before a pattern
 * is compiled (Pattern.compile, String.split / matches / replaceAll / replaceFirst).
 */
public final class RegexCompat {
    private RegexCompat() {
    }

    private static final String LINE_BREAK = "(?:\\r\\n|[\\n\\u000B\\f\\r\\u0085\\u2028\\u2029])";
    private static final String HORIZONTAL = " \\t\\u00A0\\u1680\\u180E\\u2000-\\u200A\\u202F\\u205F\\u3000";

    /** The regex with \\R and \\h spelled out (unchanged, and not copied, when it has neither). */
    static String syntax(String regex) {
        if (regex.indexOf("\\R") < 0 && regex.indexOf("\\h") < 0) {
            return regex;
        }
        StringBuilder sb = new StringBuilder(regex.length() + 32);
        boolean inClass = false;
        boolean quoted = false;
        for (int i = 0; i < regex.length(); i++) {
            char c = regex.charAt(i);
            if (quoted) {
                if (c == '\\' && i + 1 < regex.length() && regex.charAt(i + 1) == 'E') {
                    quoted = false;
                }
                sb.append(c);
                continue;
            }
            if (c == '\\' && i + 1 < regex.length()) {
                char n = regex.charAt(++i);
                if (n == 'R' && !inClass) {
                    sb.append(LINE_BREAK);
                } else if (n == 'h') {
                    sb.append(inClass ? HORIZONTAL : "[" + HORIZONTAL + "]");
                } else {
                    if (n == 'Q') {
                        quoted = true;
                    }
                    sb.append(c).append(n);
                }
            } else {
                if (c == '[') {
                    inClass = true;
                } else if (c == ']') {
                    inClass = false;
                }
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static java.util.regex.Pattern compile(String regex) {
        return java.util.regex.Pattern.compile(syntax(regex));
    }

    public static java.util.regex.Pattern compile(String regex, int flags) {
        return java.util.regex.Pattern.compile(syntax(regex), flags);
    }

    public static boolean matches(String regex, CharSequence input) {
        return java.util.regex.Pattern.matches(syntax(regex), input);
    }

    public static String[] split(String s, String regex) {
        return s.split(syntax(regex));
    }

    public static String[] split(String s, String regex, int limit) {
        return s.split(syntax(regex), limit);
    }

    public static boolean matches(String s, String regex) {
        return s.matches(syntax(regex));
    }

    public static String replaceAll(String s, String regex, String replacement) {
        return replaceAll(matcher(java.util.regex.Pattern.compile(syntax(regex)), s), replacement);
    }

    public static String replaceFirst(String s, String regex, String replacement) {
        return replaceFirst(matcher(java.util.regex.Pattern.compile(syntax(regex)), s), replacement);
    }

    public static String replaceAll(Matcher m, String replacement) {
        m.reset();
        if (!m.find()) {
            return textOf(m);
        }
        String text = textOf(m);
        StringBuilder sb = new StringBuilder(text.length() + 16);
        int last = 0;
        do {
            sb.append(text, last, m.start());
            appendReplacement(sb, m, replacement);
            last = m.end();
        } while (m.find());
        sb.append(text, last, text.length());
        return sb.toString();
    }

    public static String replaceFirst(Matcher m, String replacement) {
        m.reset();
        String text = textOf(m);
        if (!m.find()) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length() + 16);
        sb.append(text, 0, m.start());
        appendReplacement(sb, m, replacement);
        sb.append(text, m.end(), text.length());
        return sb.toString();
    }

    /** The input of the matcher, recovered without reflection: region is the whole input after reset. */
    private static String textOf(Matcher m) {
        return INPUTS.get(m);
    }

    private static final java.util.Map<Matcher, String> INPUTS = new java.util.WeakHashMap<>();

    /** Pattern.matcher(CharSequence) redirect: remembers the input for replaceAll. */
    public static Matcher matcher(java.util.regex.Pattern p, CharSequence input) {
        Matcher m = p.matcher(input);
        INPUTS.put(m, input.toString());
        return m;
    }

    private static void appendReplacement(StringBuilder sb, Matcher m, String r) {
        for (int i = 0; i < r.length(); i++) {
            char c = r.charAt(i);
            if (c == '\\' && i + 1 < r.length()) {
                sb.append(r.charAt(++i));
            } else if (c == '$' && i + 1 < r.length() && Character.isDigit(r.charAt(i + 1))) {
                int group = r.charAt(++i) - '0';
                while (i + 1 < r.length() && Character.isDigit(r.charAt(i + 1))
                        && group * 10 + (r.charAt(i + 1) - '0') <= m.groupCount()) {
                    group = group * 10 + (r.charAt(++i) - '0');
                }
                String g = m.group(group);
                if (g != null) {
                    sb.append(g);
                }
            } else {
                sb.append(c);
            }
        }
    }
}
