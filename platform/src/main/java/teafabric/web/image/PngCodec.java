package teafabric.web.image;

import java.io.ByteArrayOutputStream;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** Pure-Java PNG decoder/encoder replacing stb_image / stb_image_write for Minecraft's textures. */
public final class PngCodec {
    private PngCodec() {
    }

    public static final class Image {
        public int width;
        public int height;
        public int fileChannels;
        public byte[] rgba; // always 4 channels, row-major
    }

    private static int be32(byte[] b, int o) {
        return (b[o] & 0xff) << 24 | (b[o + 1] & 0xff) << 16 | (b[o + 2] & 0xff) << 8 | (b[o + 3] & 0xff);
    }

    public static boolean isPng(byte[] d) {
        return d.length >= 8 && (d[0] & 0xff) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G';
    }

    /** Header only: {width, height, channels} or null. */
    public static int[] info(byte[] d) {
        if (!isPng(d) || d.length < 33) {
            return null;
        }
        int colorType = d[25] & 0xff;
        return new int[] {be32(d, 16), be32(d, 20), channels(colorType, false)};
    }

    private static int channels(int colorType, boolean trns) {
        switch (colorType) {
            case 0: return trns ? 2 : 1;
            case 2: return trns ? 4 : 3;
            case 3: return trns ? 4 : 3;
            case 4: return 2;
            default: return 4;
        }
    }

    public static Image decode(byte[] d) {
        if (!isPng(d)) {
            throw new IllegalArgumentException("Not a PNG file");
        }
        int pos = 8;
        int width = 0;
        int height = 0;
        int depth = 0;
        int colorType = 0;
        int interlace = 0;
        byte[] palette = null;
        byte[] trns = null;
        ByteArrayOutputStream idat = new ByteArrayOutputStream();
        while (pos + 8 <= d.length) {
            int len = be32(d, pos);
            int type = be32(d, pos + 4);
            int data = pos + 8;
            switch (type) {
                case 0x49484452: // IHDR
                    width = be32(d, data);
                    height = be32(d, data + 4);
                    depth = d[data + 8] & 0xff;
                    colorType = d[data + 9] & 0xff;
                    interlace = d[data + 12] & 0xff;
                    break;
                case 0x504c5445: // PLTE
                    palette = java.util.Arrays.copyOfRange(d, data, data + len);
                    break;
                case 0x74524e53: // tRNS
                    trns = java.util.Arrays.copyOfRange(d, data, data + len);
                    break;
                case 0x49444154: // IDAT
                    idat.write(d, data, len);
                    break;
                default:
                    break;
            }
            pos = data + len + 4;
            if (type == 0x49454e44) { // IEND
                break;
            }
        }
        byte[] raw = inflate(idat.toByteArray());
        int samples = colorType == 2 ? 3 : colorType == 4 ? 2 : colorType == 6 ? 4 : 1;
        int bitsPerPixel = samples * depth;
        int bpp = Math.max(1, bitsPerPixel / 8);
        Image img = new Image();
        img.width = width;
        img.height = height;
        img.fileChannels = channels(colorType, trns != null);
        img.rgba = new byte[width * height * 4];
        if (interlace == 0) {
            unfilterPass(raw, 0, width, height, bitsPerPixel, bpp, img, colorType, depth, palette, trns, 0, 0, 1, 1);
        } else {
            int[][] adam = {{0, 0, 8, 8}, {4, 0, 8, 8}, {0, 4, 4, 8}, {2, 0, 4, 4}, {0, 2, 2, 4}, {1, 0, 2, 2},
                {0, 1, 1, 2}};
            int offset = 0;
            for (int[] p : adam) {
                int pw = (width - p[0] + p[2] - 1) / p[2];
                int ph = (height - p[1] + p[3] - 1) / p[3];
                if (pw <= 0 || ph <= 0) {
                    continue;
                }
                offset = unfilterPass(raw, offset, pw, ph, bitsPerPixel, bpp, img, colorType, depth, palette, trns,
                        p[0], p[1], p[2], p[3]);
            }
        }
        return img;
    }

    private static byte[] inflate(byte[] data) {
        Inflater inf = new Inflater();
        inf.setInput(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length * 4);
        byte[] buf = new byte[65536];
        try {
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0) {
                    if (inf.needsInput() || inf.needsDictionary()) {
                        break;
                    }
                }
                out.write(buf, 0, n);
            }
        } catch (DataFormatException e) {
            throw new IllegalArgumentException("Corrupt PNG data", e);
        } finally {
            inf.end();
        }
        return out.toByteArray();
    }

    private static int unfilterPass(byte[] raw, int offset, int w, int h, int bitsPerPixel, int bpp, Image img,
            int colorType, int depth, byte[] palette, byte[] trns, int x0, int y0, int dx, int dy) {
        int stride = (w * bitsPerPixel + 7) / 8;
        byte[] prev = new byte[stride];
        byte[] cur = new byte[stride];
        for (int y = 0; y < h; y++) {
            int filter = raw[offset++] & 0xff;
            System.arraycopy(raw, offset, cur, 0, stride);
            offset += stride;
            for (int i = 0; i < stride; i++) {
                int a = i >= bpp ? cur[i - bpp] & 0xff : 0;
                int b = prev[i] & 0xff;
                int c = i >= bpp ? prev[i - bpp] & 0xff : 0;
                int x = cur[i] & 0xff;
                switch (filter) {
                    case 1: x += a; break;
                    case 2: x += b; break;
                    case 3: x += (a + b) >> 1; break;
                    case 4: {
                        int p = a + b - c;
                        int pa = Math.abs(p - a);
                        int pb = Math.abs(p - b);
                        int pc = Math.abs(p - c);
                        x += pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
                        break;
                    }
                    default: break;
                }
                cur[i] = (byte) x;
            }
            int outY = y0 + y * dy;
            for (int px = 0; px < w; px++) {
                int o = ((outY * img.width) + x0 + px * dx) * 4;
                writePixel(cur, px, colorType, depth, palette, trns, img.rgba, o);
            }
            byte[] t = prev;
            prev = cur;
            cur = t;
        }
        return offset;
    }

    private static int sample(byte[] row, int index, int depth) {
        if (depth == 8) {
            return row[index] & 0xff;
        }
        if (depth == 16) {
            return row[index * 2] & 0xff; // high byte
        }
        int perByte = 8 / depth;
        int b = row[index / perByte] & 0xff;
        int shift = 8 - depth * (index % perByte + 1);
        return (b >> shift) & ((1 << depth) - 1);
    }

    private static void writePixel(byte[] row, int px, int colorType, int depth, byte[] palette, byte[] trns,
            byte[] out, int o) {
        int r;
        int g;
        int b;
        int a = 255;
        switch (colorType) {
            case 0: {
                int v = sample(row, px, depth);
                int scaled = depth < 8 ? v * 255 / ((1 << depth) - 1) : v;
                r = g = b = scaled;
                if (trns != null && trns.length >= 2 && v == (((trns[0] & 0xff) << 8 | (trns[1] & 0xff))
                        >> (depth == 16 ? 8 : 0))) {
                    a = 0;
                }
                break;
            }
            case 2:
                r = sample(row, px * 3, depth);
                g = sample(row, px * 3 + 1, depth);
                b = sample(row, px * 3 + 2, depth);
                if (trns != null && trns.length >= 6 && depth == 8 && r == (trns[1] & 0xff) && g == (trns[3] & 0xff)
                        && b == (trns[5] & 0xff)) {
                    a = 0;
                }
                break;
            case 3: {
                int i = sample(row, px, depth);
                r = palette[i * 3] & 0xff;
                g = palette[i * 3 + 1] & 0xff;
                b = palette[i * 3 + 2] & 0xff;
                a = trns != null && i < trns.length ? trns[i] & 0xff : 255;
                break;
            }
            case 4:
                r = g = b = sample(row, px * 2, depth);
                a = sample(row, px * 2 + 1, depth);
                break;
            default:
                r = sample(row, px * 4, depth);
                g = sample(row, px * 4 + 1, depth);
                b = sample(row, px * 4 + 2, depth);
                a = sample(row, px * 4 + 3, depth);
                break;
        }
        out[o] = (byte) r;
        out[o + 1] = (byte) g;
        out[o + 2] = (byte) b;
        out[o + 3] = (byte) a;
    }

    /** Encodes {@code comp}-channel 8-bit pixels (stride bytes per row) as PNG. */
    public static byte[] encode(byte[] pixels, int w, int h, int comp, int stride) {
        int colorType = comp == 1 ? 0 : comp == 2 ? 4 : comp == 3 ? 2 : 6;
        byte[] raw = new byte[(w * comp + 1) * h];
        for (int y = 0; y < h; y++) {
            raw[y * (w * comp + 1)] = 0;
            System.arraycopy(pixels, y * stride, raw, y * (w * comp + 1) + 1, w * comp);
        }
        Deflater def = new Deflater(6);
        def.setInput(raw);
        def.finish();
        ByteArrayOutputStream z = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        while (!def.finished()) {
            int n = def.deflate(buf);
            z.write(buf, 0, n);
        }
        def.end();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'}, 0, 8);
        byte[] ihdr = new byte[13];
        put32(ihdr, 0, w);
        put32(ihdr, 4, h);
        ihdr[8] = 8;
        ihdr[9] = (byte) colorType;
        chunk(out, "IHDR", ihdr);
        chunk(out, "IDAT", z.toByteArray());
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void put32(byte[] b, int o, int v) {
        b[o] = (byte) (v >>> 24);
        b[o + 1] = (byte) (v >>> 16);
        b[o + 2] = (byte) (v >>> 8);
        b[o + 3] = (byte) v;
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] len = new byte[4];
        put32(len, 0, data.length);
        out.write(len, 0, 4);
        byte[] t = type.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        out.write(t, 0, 4);
        out.write(data, 0, data.length);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(data);
        byte[] c = new byte[4];
        put32(c, 0, (int) crc.getValue());
        out.write(c, 0, 4);
    }
}
