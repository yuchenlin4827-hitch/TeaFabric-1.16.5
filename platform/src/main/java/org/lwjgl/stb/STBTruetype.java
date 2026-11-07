package org.lwjgl.stb;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * TrueType fonts are only used by resource packs that declare a "ttf" font provider; vanilla
 * 1.16.5 renders bitmap fonts. Initialisation reports failure so such providers are skipped.
 */
public final class STBTruetype {
    private STBTruetype() {
    }

    public static boolean stbtt_InitFont(STBTTFontinfo info, ByteBuffer data) {
        return false;
    }

    public static float stbtt_ScaleForPixelHeight(STBTTFontinfo info, float pixels) {
        return 0;
    }

    public static void stbtt_GetFontVMetrics(STBTTFontinfo info, IntBuffer ascent, IntBuffer descent,
            IntBuffer lineGap) {
    }

    public static int stbtt_FindGlyphIndex(STBTTFontinfo info, int codepoint) {
        return 0;
    }

    public static void stbtt_GetGlyphHMetrics(STBTTFontinfo info, int glyph, IntBuffer advance, IntBuffer bearing) {
    }

    public static void stbtt_GetGlyphBitmapBoxSubpixel(STBTTFontinfo info, int glyph, float sx, float sy, float shx,
            float shy, IntBuffer x0, IntBuffer y0, IntBuffer x1, IntBuffer y1) {
    }

    public static void nstbtt_MakeGlyphBitmapSubpixel(long info, long output, int w, int h, int stride, float sx,
            float sy, float shx, float shy, int glyph) {
    }
}
