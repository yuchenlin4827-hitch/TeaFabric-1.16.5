package org.apache.logging.log4j;

/** Implements log4j's "{}" parameter substitution. */
final class LogFormat {
    private LogFormat() {
    }

    static int countPlaceholders(String format) {
        if (format == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i + 1 < format.length(); i++) {
            char c = format.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '{' && format.charAt(i + 1) == '}') {
                count++;
                i++;
            }
        }
        return count;
    }

    static String format(String format, Object[] params) {
        if (format == null) {
            return "null";
        }
        if (params == null || params.length == 0) {
            return format;
        }
        StringBuilder sb = new StringBuilder(format.length() + 16 * params.length);
        int p = 0;
        for (int i = 0; i < format.length(); i++) {
            char c = format.charAt(i);
            if (c == '\\' && i + 1 < format.length()) {
                sb.append(format.charAt(++i));
            } else if (c == '{' && i + 1 < format.length() && format.charAt(i + 1) == '}' && p < params.length) {
                sb.append(deepToString(params[p++]));
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String deepToString(Object o) {
        if (o instanceof Object[]) {
            return java.util.Arrays.deepToString((Object[]) o);
        }
        if (o instanceof int[]) {
            return java.util.Arrays.toString((int[]) o);
        }
        if (o instanceof long[]) {
            return java.util.Arrays.toString((long[]) o);
        }
        if (o instanceof byte[]) {
            return java.util.Arrays.toString((byte[]) o);
        }
        try {
            return String.valueOf(o);
        } catch (RuntimeException e) {
            return "[" + o.getClass().getName() + ".toString() threw " + e + "]";
        }
    }
}
