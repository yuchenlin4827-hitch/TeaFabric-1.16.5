package org.teavm.classlib.java.sql;

public class TDate extends java.util.Date {
    private static final long serialVersionUID = 1L;

    public TDate(long date) {
        super(date);
    }

    public static TDate valueOf(String s) {
        String[] p = s.split("-");
        @SuppressWarnings("deprecation")
        java.util.Date d = new java.util.Date(Integer.parseInt(p[0]) - 1900, Integer.parseInt(p[1]) - 1,
                Integer.parseInt(p[2]));
        return new TDate(d.getTime());
    }
}
