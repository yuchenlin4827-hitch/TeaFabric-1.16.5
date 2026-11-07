package org.lwjgl.stb;

import teafabric.web.image.PngCodec;
import teafabric.web.mem.NativeMemory;

public final class STBImageWrite {
    private STBImageWrite() {
    }

    public static int nstbi_write_png_to_func(long func, long context, int w, int h, int comp, long data,
            int stride) {
        STBIWriteCallback cb = STBIWriteCallback.lookup(func);
        if (cb == null) {
            return 0;
        }
        int rowBytes = stride == 0 ? w * comp : stride;
        byte[] pixels = new byte[rowBytes * h];
        NativeMemory.copyToArray(data, pixels, 0, pixels.length);
        byte[] png = PngCodec.encode(pixels, w, h, comp, rowBytes);
        long out = NativeMemory.malloc(png.length);
        try {
            NativeMemory.copyFromArray(png, 0, out, png.length);
            cb.invoke(context, out, png.length);
        } finally {
            NativeMemory.free(out);
        }
        return 1;
    }
}
