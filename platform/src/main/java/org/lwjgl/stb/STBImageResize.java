package org.lwjgl.stb;

import teafabric.web.mem.NativeMemory;

/** Box-filter resize (used for icons and texture rescaling). */
public final class STBImageResize {
    private STBImageResize() {
    }

    public static int nstbir_resize_uint8(long input, int iw, int ih, int istride, long output, int ow, int oh,
            int ostride, int channels) {
        int is = istride == 0 ? iw * channels : istride;
        int os = ostride == 0 ? ow * channels : ostride;
        for (int y = 0; y < oh; y++) {
            int sy0 = y * ih / oh;
            int sy1 = Math.max(sy0 + 1, (y + 1) * ih / oh);
            for (int x = 0; x < ow; x++) {
                int sx0 = x * iw / ow;
                int sx1 = Math.max(sx0 + 1, (x + 1) * iw / ow);
                for (int c = 0; c < channels; c++) {
                    int sum = 0;
                    int count = 0;
                    for (int sy = sy0; sy < sy1; sy++) {
                        for (int sx = sx0; sx < sx1; sx++) {
                            sum += NativeMemory.getByte(input + (long) sy * is + (long) sx * channels + c) & 0xff;
                            count++;
                        }
                    }
                    NativeMemory.putByte(output + (long) y * os + (long) x * channels + c, (byte) (sum / count));
                }
            }
        }
        return 1;
    }
}
