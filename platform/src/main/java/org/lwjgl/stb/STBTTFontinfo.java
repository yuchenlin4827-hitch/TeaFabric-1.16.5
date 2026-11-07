package org.lwjgl.stb;

public final class STBTTFontinfo implements AutoCloseable {
    public static STBTTFontinfo malloc() {
        return new STBTTFontinfo();
    }

    public static STBTTFontinfo create() {
        return new STBTTFontinfo();
    }

    public long address() {
        return 0;
    }

    public void free() {
    }

    @Override
    public void close() {
    }
}
