package org.teavm.classlib.java.util;

import java.util.Random;

/**
 * Full java.util.UUID (shadows TeaVM's minimal TUUID, which lacks the (long, long) constructor,
 * bit accessors, comparison and name-based UUIDs that Minecraft relies on).
 */
public final class TUUID implements java.io.Serializable, Comparable<TUUID> {
    private static final long serialVersionUID = -4856846361193249489L;
    private static Random random;

    private final long mostSigBits;
    private final long leastSigBits;

    public TUUID(long mostSigBits, long leastSigBits) {
        this.mostSigBits = mostSigBits;
        this.leastSigBits = leastSigBits;
    }

    private TUUID(byte[] data) {
        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (data[i] & 0xff);
        }
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (data[i] & 0xff);
        }
        this.mostSigBits = msb;
        this.leastSigBits = lsb;
    }

    public static TUUID randomUUID() {
        if (random == null) {
            random = new Random();
        }
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        bytes[6] &= 0x0f;
        bytes[6] |= 0x40;
        bytes[8] &= 0x3f;
        bytes[8] |= (byte) 0x80;
        return new TUUID(bytes);
    }

    public static TUUID nameUUIDFromBytes(byte[] name) {
        byte[] md5 = Md5.digest(name);
        md5[6] &= 0x0f;
        md5[6] |= 0x30;
        md5[8] &= 0x3f;
        md5[8] |= (byte) 0x80;
        return new TUUID(md5);
    }

    public static TUUID fromString(String name) {
        String[] components = name.split("-");
        if (components.length != 5) {
            throw new IllegalArgumentException("Invalid UUID string: " + name);
        }
        long msb = Long.parseLong(components[0], 16);
        msb <<= 16;
        msb |= Long.parseLong(components[1], 16);
        msb <<= 16;
        msb |= Long.parseLong(components[2], 16);
        long lsb = Long.parseLong(components[3], 16);
        lsb <<= 48;
        lsb |= Long.parseLong(components[4], 16);
        return new TUUID(msb, lsb);
    }

    public long getLeastSignificantBits() {
        return leastSigBits;
    }

    public long getMostSignificantBits() {
        return mostSigBits;
    }

    public int version() {
        return (int) ((mostSigBits >> 12) & 0x0f);
    }

    public int variant() {
        return (int) ((leastSigBits >>> (64 - (leastSigBits >>> 62))) & (leastSigBits >> 63));
    }

    public long timestamp() {
        if (version() != 1) {
            throw new UnsupportedOperationException("Not a time-based UUID");
        }
        return (mostSigBits & 0x0FFFL) << 48 | ((mostSigBits >> 16) & 0x0FFFFL) << 32 | mostSigBits >>> 32;
    }

    public int clockSequence() {
        if (version() != 1) {
            throw new UnsupportedOperationException("Not a time-based UUID");
        }
        return (int) ((leastSigBits & 0x3FFF000000000000L) >>> 48);
    }

    public long node() {
        if (version() != 1) {
            throw new UnsupportedOperationException("Not a time-based UUID");
        }
        return leastSigBits & 0x0000FFFFFFFFFFFFL;
    }

    @Override
    public String toString() {
        return digits(mostSigBits >> 32, 8) + "-" + digits(mostSigBits >> 16, 4) + "-" + digits(mostSigBits, 4)
                + "-" + digits(leastSigBits >> 48, 4) + "-" + digits(leastSigBits, 12);
    }

    private static String digits(long val, int digits) {
        long hi = 1L << (digits * 4);
        return Long.toHexString(hi | (val & (hi - 1))).substring(1);
    }

    @Override
    public int hashCode() {
        long hilo = mostSigBits ^ leastSigBits;
        return ((int) (hilo >> 32)) ^ (int) hilo;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TUUID)) {
            return false;
        }
        TUUID id = (TUUID) obj;
        return mostSigBits == id.mostSigBits && leastSigBits == id.leastSigBits;
    }

    @Override
    public int compareTo(TUUID val) {
        int mostSigBits = Long.compare(this.mostSigBits, val.mostSigBits);
        return mostSigBits != 0 ? mostSigBits : Long.compare(this.leastSigBits, val.leastSigBits);
    }

    /** RFC 1321 MD5, needed for name-based (version 3) UUIDs. */
    static final class Md5 {
        private static final int[] S = {7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
            5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
            4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
            6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21};
        private static final int[] K = new int[64];

        static {
            for (int i = 0; i < 64; i++) {
                K[i] = (int) (long) ((1L << 32) * Math.abs(Math.sin(i + 1)));
            }
        }

        private Md5() {
        }

        static byte[] digest(byte[] message) {
            int originalLength = message.length;
            int paddedLength = ((originalLength + 8) / 64 + 1) * 64;
            byte[] padded = new byte[paddedLength];
            System.arraycopy(message, 0, padded, 0, originalLength);
            padded[originalLength] = (byte) 0x80;
            long bitLength = (long) originalLength * 8;
            for (int i = 0; i < 8; i++) {
                padded[paddedLength - 8 + i] = (byte) (bitLength >>> (8 * i));
            }
            int a0 = 0x67452301;
            int b0 = 0xefcdab89;
            int c0 = 0x98badcfe;
            int d0 = 0x10325476;
            int[] m = new int[16];
            for (int chunk = 0; chunk < paddedLength; chunk += 64) {
                for (int i = 0; i < 16; i++) {
                    int j = chunk + i * 4;
                    m[i] = (padded[j] & 0xff) | (padded[j + 1] & 0xff) << 8 | (padded[j + 2] & 0xff) << 16
                            | (padded[j + 3] & 0xff) << 24;
                }
                int a = a0;
                int b = b0;
                int c = c0;
                int d = d0;
                for (int i = 0; i < 64; i++) {
                    int f;
                    int g;
                    if (i < 16) {
                        f = (b & c) | (~b & d);
                        g = i;
                    } else if (i < 32) {
                        f = (d & b) | (~d & c);
                        g = (5 * i + 1) % 16;
                    } else if (i < 48) {
                        f = b ^ c ^ d;
                        g = (3 * i + 5) % 16;
                    } else {
                        f = c ^ (b | ~d);
                        g = (7 * i) % 16;
                    }
                    int tmp = d;
                    d = c;
                    c = b;
                    b = b + Integer.rotateLeft(a + f + K[i] + m[g], S[i]);
                    a = tmp;
                }
                a0 += a;
                b0 += b;
                c0 += c;
                d0 += d;
            }
            byte[] out = new byte[16];
            int[] words = {a0, b0, c0, d0};
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    out[i * 4 + j] = (byte) (words[i] >>> (8 * j));
                }
            }
            return out;
        }
    }
}
