package org.teavm.classlib.java.net;

public class TInetSocketAddress extends TSocketAddress {
    private static final long serialVersionUID = 1L;
    private final String hostname;
    private final TInetAddress addr;
    private final int port;

    public TInetSocketAddress(int port) {
        this(new TInet4Address("0.0.0.0", new byte[4]), port);
    }

    public TInetSocketAddress(TInetAddress addr, int port) {
        this.addr = addr == null ? new TInet4Address("0.0.0.0", new byte[4]) : addr;
        this.hostname = null;
        this.port = checkPort(port);
    }

    public TInetSocketAddress(String hostname, int port) {
        this.hostname = hostname;
        TInetAddress a;
        try {
            a = TInetAddress.getByName(hostname);
        } catch (TUnknownHostException e) {
            a = null;
        }
        this.addr = a;
        this.port = checkPort(port);
    }

    private TInetSocketAddress(String hostname, int port, boolean unresolved) {
        this.hostname = hostname;
        this.addr = null;
        this.port = checkPort(port);
    }

    private static int checkPort(int port) {
        if (port < 0 || port > 0xFFFF) {
            throw new IllegalArgumentException("port out of range:" + port);
        }
        return port;
    }

    public static TInetSocketAddress createUnresolved(String host, int port) {
        return new TInetSocketAddress(host, port, true);
    }

    public final int getPort() {
        return port;
    }

    public final TInetAddress getAddress() {
        return addr;
    }

    public final String getHostName() {
        return hostname != null ? hostname : addr.getHostName();
    }

    public final String getHostString() {
        return hostname != null ? hostname : addr.getHostAddress();
    }

    public final boolean isUnresolved() {
        return addr == null;
    }

    @Override
    public String toString() {
        return (isUnresolved() ? hostname + "/<unresolved>" : addr.toString()) + ":" + port;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TInetSocketAddress)) {
            return false;
        }
        TInetSocketAddress s = (TInetSocketAddress) o;
        return port == s.port && getHostString().equals(s.getHostString());
    }

    @Override
    public int hashCode() {
        return getHostString().hashCode() * 31 + port;
    }
}
