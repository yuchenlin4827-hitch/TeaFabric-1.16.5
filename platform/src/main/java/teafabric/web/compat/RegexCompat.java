package teafabric.web.compat;

import java.util.regex.Matcher;

/**
 * JDK-conformant Matcher.replaceAll: a reference to a group that did not participate in the match
 * expands to the empty string (TeaVM's implementation throws IllegalArgumentException).
 */
public final class RegexCompat {
    private RegexCompat() {
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
