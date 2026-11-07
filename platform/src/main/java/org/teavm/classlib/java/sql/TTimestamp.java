package org.teavm.classlib.java.sql;

public class TTimestamp extends java.util.Date {
    private static final long serialVersionUID = 1L;
    private int nanos;

    public TTimestamp(long time) {
        super(time);
        nanos = (int) ((time % 1000) * 1000000);
    }

    public int getNanos() {
        return nanos;
    }

    public void setNanos(int n) {
        nanos = n;
    }
}
