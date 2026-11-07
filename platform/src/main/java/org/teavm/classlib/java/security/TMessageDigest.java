package org.teavm.classlib.java.security;

import java.nio.ByteBuffer;

/** MessageDigest with pure-Java MD5, SHA-1 and SHA-256 (enough for Guava hashing and Minecraft). */
public abstract class TMessageDigest implements Cloneable {
    private final String algorithm;
    private long length;
    private final byte[] block;
    private int blockLen;

    protected TMessageDigest(String algorithm) {
        this.algorithm = algorithm;
        this.block = new byte[64];
    }

    public static TMessageDigest getInstance(String algorithm) throws java.security.NoSuchAlgorithmException {
        switch (algorithm.toUpperCase()) {
            case "MD5":
                return new Md5();
            case "SHA-1":
            case "SHA1":
            case "SHA":
                return new Sha1();
            case "SHA-256":
            case "SHA256":
                return new Sha256();
            default:
                throw new java.security.NoSuchAlgorithmException(algorithm + " MessageDigest not available");
        }
    }

    public static TMessageDigest getInstance(String algorithm, String provider) throws java.security.NoSuchAlgorithmException {
        return getInstance(algorithm);
    }

    public final String getAlgorithm() {
        return algorithm;
    }

    public abstract int getDigestLength();

    protected abstract void processBlock(byte[] b, int off);

    protected abstract void resetState();

    protected abstract byte[] finishState(long bitLength);

    public void update(byte input) {
        block[blockLen++] = input;
        length++;
        if (blockLen == 64) {
            processBlock(block, 0);
            blockLen = 0;
        }
    }

    public void update(byte[] input, int offset, int len) {
        for (int i = 0; i < len; i++) {
            update(input[offset + i]);
        }
    }

    public void update(byte[] input) {
        update(input, 0, input.length);
    }

    public final void update(ByteBuffer input) {
        while (input.hasRemaining()) {
            update(input.get());
        }
    }

    public byte[] digest() {
        long bits = length * 8;
        byte[] result = finishState(bits);
        reset();
        return result;
    }

    public int digest(byte[] buf, int offset, int len) {
        byte[] d = digest();
        System.arraycopy(d, 0, buf, offset, Math.min(len, d.length));
        return d.length;
    }

    public byte[] digest(byte[] input) {
        update(input);
        return digest();
    }

    public void reset() {
        length = 0;
        blockLen = 0;
        resetState();
    }

    /** Pads the pending block per MD-style construction; {@code bigEndian} selects the length encoding. */
    protected final void pad(long bitLength, boolean bigEndian) {
        update((byte) 0x80);
        length--; // padding is not message data
        while (blockLen != 56) {
            block[blockLen++] = 0;
            if (blockLen == 64) {
                processBlock(block, 0);
                blockLen = 0;
            }
        }
        for (int i = 0; i < 8; i++) {
            block[56 + i] = (byte) (bigEndian ? bitLength >>> (56 - 8 * i) : bitLength >>> (8 * i));
        }
        processBlock(block, 0);
        blockLen = 0;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        TMessageDigest copy = copyState();
        copy.length = length;
        System.arraycopy(block, 0, copy.block, 0, blockLen);
        copy.blockLen = blockLen;
        return copy;
    }

    protected abstract TMessageDigest copyState();

    @Override
    public String toString() {
        return algorithm + " Message Digest";
    }

    public static boolean isEqual(byte[] a, byte[] b) {
        return java.util.Arrays.equals(a, b);
    }

    static final class Md5 extends TMessageDigest {
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

        private int a0;
        private int b0;
        private int c0;
        private int d0;
        private final int[] m = new int[16];

        Md5() {
            super("MD5");
            resetState();
        }

        @Override
        public int getDigestLength() {
            return 16;
        }

        @Override
        protected void resetState() {
            a0 = 0x67452301;
            b0 = 0xefcdab89;
            c0 = 0x98badcfe;
            d0 = 0x10325476;
        }

        @Override
        protected void processBlock(byte[] p, int off) {
            for (int i = 0; i < 16; i++) {
                int j = off + i * 4;
                m[i] = (p[j] & 0xff) | (p[j + 1] & 0xff) << 8 | (p[j + 2] & 0xff) << 16 | (p[j + 3] & 0xff) << 24;
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

        @Override
        protected byte[] finishState(long bitLength) {
            pad(bitLength, false);
            byte[] out = new byte[16];
            int[] w = {a0, b0, c0, d0};
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    out[i * 4 + j] = (byte) (w[i] >>> (8 * j));
                }
            }
            return out;
        }

        @Override
        protected TMessageDigest copyState() {
            Md5 c = new Md5();
            c.a0 = a0;
            c.b0 = b0;
            c.c0 = c0;
            c.d0 = d0;
            return c;
        }
    }

    static final class Sha1 extends TMessageDigest {
        private final int[] h = new int[5];
        private final int[] w = new int[80];

        Sha1() {
            super("SHA-1");
            resetState();
        }

        @Override
        public int getDigestLength() {
            return 20;
        }

        @Override
        protected void resetState() {
            h[0] = 0x67452301;
            h[1] = 0xEFCDAB89;
            h[2] = 0x98BADCFE;
            h[3] = 0x10325476;
            h[4] = 0xC3D2E1F0;
        }

        @Override
        protected void processBlock(byte[] p, int off) {
            for (int i = 0; i < 16; i++) {
                int j = off + i * 4;
                w[i] = (p[j] & 0xff) << 24 | (p[j + 1] & 0xff) << 16 | (p[j + 2] & 0xff) << 8 | (p[j + 3] & 0xff);
            }
            for (int i = 16; i < 80; i++) {
                w[i] = Integer.rotateLeft(w[i - 3] ^ w[i - 8] ^ w[i - 14] ^ w[i - 16], 1);
            }
            int a = h[0];
            int b = h[1];
            int c = h[2];
            int d = h[3];
            int e = h[4];
            for (int i = 0; i < 80; i++) {
                int f;
                int k;
                if (i < 20) {
                    f = (b & c) | (~b & d);
                    k = 0x5A827999;
                } else if (i < 40) {
                    f = b ^ c ^ d;
                    k = 0x6ED9EBA1;
                } else if (i < 60) {
                    f = (b & c) | (b & d) | (c & d);
                    k = 0x8F1BBCDC;
                } else {
                    f = b ^ c ^ d;
                    k = 0xCA62C1D6;
                }
                int t = Integer.rotateLeft(a, 5) + f + e + k + w[i];
                e = d;
                d = c;
                c = Integer.rotateLeft(b, 30);
                b = a;
                a = t;
            }
            h[0] += a;
            h[1] += b;
            h[2] += c;
            h[3] += d;
            h[4] += e;
        }

        @Override
        protected byte[] finishState(long bitLength) {
            pad(bitLength, true);
            byte[] out = new byte[20];
            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 4; j++) {
                    out[i * 4 + j] = (byte) (h[i] >>> (24 - 8 * j));
                }
            }
            return out;
        }

        @Override
        protected TMessageDigest copyState() {
            Sha1 c = new Sha1();
            System.arraycopy(h, 0, c.h, 0, 5);
            return c;
        }
    }

    static final class Sha256 extends TMessageDigest {
        private static final int[] K = {
            0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
            0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
            0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
            0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2};
        private final int[] h = new int[8];
        private final int[] w = new int[64];

        Sha256() {
            super("SHA-256");
            resetState();
        }

        @Override
        public int getDigestLength() {
            return 32;
        }

        @Override
        protected void resetState() {
            int[] init = {0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab,
                0x5be0cd19};
            System.arraycopy(init, 0, h, 0, 8);
        }

        @Override
        protected void processBlock(byte[] p, int off) {
            for (int i = 0; i < 16; i++) {
                int j = off + i * 4;
                w[i] = (p[j] & 0xff) << 24 | (p[j + 1] & 0xff) << 16 | (p[j + 2] & 0xff) << 8 | (p[j + 3] & 0xff);
            }
            for (int i = 16; i < 64; i++) {
                int s0 = Integer.rotateRight(w[i - 15], 7) ^ Integer.rotateRight(w[i - 15], 18) ^ (w[i - 15] >>> 3);
                int s1 = Integer.rotateRight(w[i - 2], 17) ^ Integer.rotateRight(w[i - 2], 19) ^ (w[i - 2] >>> 10);
                w[i] = w[i - 16] + s0 + w[i - 7] + s1;
            }
            int a = h[0];
            int b = h[1];
            int c = h[2];
            int d = h[3];
            int e = h[4];
            int f = h[5];
            int g = h[6];
            int hh = h[7];
            for (int i = 0; i < 64; i++) {
                int s1 = Integer.rotateRight(e, 6) ^ Integer.rotateRight(e, 11) ^ Integer.rotateRight(e, 25);
                int ch = (e & f) ^ (~e & g);
                int t1 = hh + s1 + ch + K[i] + w[i];
                int s0 = Integer.rotateRight(a, 2) ^ Integer.rotateRight(a, 13) ^ Integer.rotateRight(a, 22);
                int maj = (a & b) ^ (a & c) ^ (b & c);
                int t2 = s0 + maj;
                hh = g;
                g = f;
                f = e;
                e = d + t1;
                d = c;
                c = b;
                b = a;
                a = t1 + t2;
            }
            h[0] += a;
            h[1] += b;
            h[2] += c;
            h[3] += d;
            h[4] += e;
            h[5] += f;
            h[6] += g;
            h[7] += hh;
        }

        @Override
        protected byte[] finishState(long bitLength) {
            pad(bitLength, true);
            byte[] out = new byte[32];
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 4; j++) {
                    out[i * 4 + j] = (byte) (h[i] >>> (24 - 8 * j));
                }
            }
            return out;
        }

        @Override
        protected TMessageDigest copyState() {
            Sha256 c = new Sha256();
            System.arraycopy(h, 0, c.h, 0, 8);
            return c;
        }
    }
}
