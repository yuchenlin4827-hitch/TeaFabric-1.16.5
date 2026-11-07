package org.teavm.classlib.java.net;

public class TProxy {
    public enum Type { DIRECT, HTTP, SOCKS }

    public static final TProxy NO_PROXY = new TProxy();

    private final Type type;
    private final TSocketAddress sa;

    private TProxy() {
        type = Type.DIRECT;
        sa = null;
    }

    public TProxy(Type type, TSocketAddress sa) {
        if (type == Type.DIRECT || sa == null) {
            throw new IllegalArgumentException("type " + type + " is not compatible with address " + sa);
        }
        this.type = type;
        this.sa = sa;
    }

    public Type type() {
        return type;
    }

    public TSocketAddress address() {
        return sa;
    }

    @Override
    public String toString() {
        return type == Type.DIRECT ? "DIRECT" : type + " @ " + sa;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TProxy)) {
            return false;
        }
        TProxy p = (TProxy) obj;
        return p.type == type && (sa == null ? p.sa == null : sa.equals(p.sa));
    }

    @Override
    public int hashCode() {
        return type.hashCode() + (sa == null ? 0 : sa.hashCode());
    }
}
