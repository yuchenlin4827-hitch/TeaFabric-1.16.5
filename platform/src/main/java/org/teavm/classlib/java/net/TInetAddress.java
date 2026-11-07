package org.teavm.classlib.java.net;

/**
 * Browsers cannot resolve host names; addresses keep the host name and only parse literal IPv4.
 */
public class TInetAddress implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    final String hostName;
    final byte[] address;

    TInetAddress(String hostName, byte[] address) {
        this.hostName = hostName;
        this.address = address;
    }

    public static TInetAddress getByName(String host) throws TUnknownHostException {
        if (host == null || host.isEmpty() || host.equals("localhost")) {
            return getLoopbackAddress();
        }
        byte[] literal = parseIpv4(host);
        return new TInet4Address(host, literal != null ? literal : new byte[4]);
    }

    public static TInetAddress[] getAllByName(String host) throws TUnknownHostException {
        return new TInetAddress[] {getByName(host)};
    }

    public static TInetAddress getByAddress(byte[] addr) throws TUnknownHostException {
        return getByAddress(null, addr);
    }

    public static TInetAddress getByAddress(String host, byte[] addr) throws TUnknownHostException {
        if (addr == null || (addr.length != 4 && addr.length != 16)) {
            throw new TUnknownHostException("addr is of illegal length");
        }
        return new TInet4Address(host, addr.clone());
    }

    public static TInetAddress getLoopbackAddress() {
        return new TInet4Address("localhost", new byte[] {127, 0, 0, 1});
    }

    public static TInetAddress getLocalHost() throws TUnknownHostException {
        return getLoopbackAddress();
    }

    static byte[] parseIpv4(String host) {
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return null;
        }
        byte[] b = new byte[4];
        for (int i = 0; i < 4; i++) {
            try {
                int v = Integer.parseInt(parts[i]);
                if (v < 0 || v > 255) {
                    return null;
                }
                b[i] = (byte) v;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return b;
    }

    public String getHostName() {
        return hostName != null ? hostName : getHostAddress();
    }

    public String getCanonicalHostName() {
        return getHostName();
    }

    public String getHostAddress() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < address.length; i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(address[i] & 0xff);
        }
        return sb.toString();
    }

    public byte[] getAddress() {
        return address.clone();
    }

    public boolean isLoopbackAddress() {
        return address.length == 4 && address[0] == 127;
    }

    public boolean isAnyLocalAddress() {
        for (byte b : address) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    public boolean isSiteLocalAddress() {
        int a = address[0] & 0xff;
        int b = address[1] & 0xff;
        return a == 10 || (a == 172 && (b & 0xf0) == 16) || (a == 192 && b == 168);
    }

    public boolean isLinkLocalAddress() {
        return (address[0] & 0xff) == 169 && (address[1] & 0xff) == 254;
    }

    public boolean isMulticastAddress() {
        return (address[0] & 0xf0) == 0xe0;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TInetAddress && java.util.Arrays.equals(address, ((TInetAddress) o).address);
    }

    @Override
    public int hashCode() {
        return java.util.Arrays.hashCode(address);
    }

    @Override
    public String toString() {
        return (hostName != null ? hostName : "") + "/" + getHostAddress();
    }
}
