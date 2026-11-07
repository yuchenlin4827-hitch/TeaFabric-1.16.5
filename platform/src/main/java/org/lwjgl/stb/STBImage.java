package org.lwjgl.stb;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import teafabric.web.image.PngCodec;
import teafabric.web.mem.NativeMemory;

/** stb_image replacement: PNG only (all of Minecraft's images), decoded in Java. */
public final class STBImage {
    private static String failure = "";

    private STBImage() {
    }

    public static String stbi_failure_reason() {
        return failure;
    }

    public static ByteBuffer stbi_load_from_memory(ByteBuffer buffer, IntBuffer x, IntBuffer y,
            IntBuffer channelsInFile, int desiredChannels) {
        byte[] data = new byte[buffer.remaining()];
        buffer.duplicate().get(data);
        PngCodec.Image img;
        try {
            img = PngCodec.decode(data);
        } catch (RuntimeException e) {
            failure = e.getMessage() == null ? "decode failed" : e.getMessage();
            return null;
        }
        int comp = desiredChannels == 0 ? img.fileChannels : desiredChannels;
        int n = img.width * img.height;
        long ptr = NativeMemory.malloc((long) n * comp);
        byte[] rgba = img.rgba;
        if (comp == 4) {
            NativeMemory.copyFromArray(rgba, 0, ptr, n * 4);
        } else {
            byte[] out = new byte[n * comp];
            for (int i = 0; i < n; i++) {
                int s = i * 4;
                int d = i * comp;
                switch (comp) {
                    case 1:
                        out[d] = (byte) (((rgba[s] & 0xff) * 77 + (rgba[s + 1] & 0xff) * 150
                                + (rgba[s + 2] & 0xff) * 29) >> 8);
                        break;
                    case 2:
                        out[d] = (byte) (((rgba[s] & 0xff) * 77 + (rgba[s + 1] & 0xff) * 150
                                + (rgba[s + 2] & 0xff) * 29) >> 8);
                        out[d + 1] = rgba[s + 3];
                        break;
                    default:
                        out[d] = rgba[s];
                        out[d + 1] = rgba[s + 1];
                        out[d + 2] = rgba[s + 2];
                        break;
                }
            }
            NativeMemory.copyFromArray(out, 0, ptr, out.length);
        }
        x.put(x.position(), img.width);
        y.put(y.position(), img.height);
        channelsInFile.put(channelsInFile.position(), img.fileChannels);
        return NativeMemory.wrap(ptr, n * comp);
    }

    public static void stbi_image_free(ByteBuffer image) {
        NativeMemory.free(NativeMemory.baseAddress(image));
    }

    public static void nstbi_image_free(long image) {
        NativeMemory.free(image);
    }

    /** Pulls the stream through the read callback and parses the PNG header. */
    public static boolean stbi_info_from_callbacks(STBIIOCallbacks callbacks, long user, IntBuffer x, IntBuffer y,
            IntBuffer comp) {
        int chunk = 4096;
        long tmp = NativeMemory.malloc(chunk);
        java.io.ByteArrayOutputStream acc = new java.io.ByteArrayOutputStream();
        try {
            while (acc.size() < 64) {
                int n = callbacks.read.invoke(user, tmp, chunk);
                if (n <= 0) {
                    break;
                }
                byte[] b = new byte[n];
                NativeMemory.copyToArray(tmp, b, 0, n);
                acc.write(b, 0, n);
            }
        } finally {
            NativeMemory.free(tmp);
        }
        int[] info = PngCodec.info(acc.toByteArray());
        if (info == null) {
            failure = "unknown image type";
            return false;
        }
        x.put(x.position(), info[0]);
        y.put(y.position(), info[1]);
        comp.put(comp.position(), info[2]);
        return true;
    }

    public static boolean stbi_info_from_memory(ByteBuffer buffer, IntBuffer x, IntBuffer y, IntBuffer comp) {
        byte[] data = new byte[Math.min(64, buffer.remaining())];
        buffer.duplicate().get(data);
        int[] info = PngCodec.info(data);
        if (info == null) {
            return false;
        }
        x.put(x.position(), info[0]);
        y.put(y.position(), info[1]);
        comp.put(comp.position(), info[2]);
        return true;
    }
}
