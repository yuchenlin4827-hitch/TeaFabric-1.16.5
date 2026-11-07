package org.lwjgl.stb;

import org.lwjgl.system.MemoryStack;

public final class STBIIOCallbacks implements AutoCloseable {
    STBIReadCallbackI read;
    STBISkipCallbackI skip;
    STBIEOFCallbackI eof;

    public static STBIIOCallbacks malloc() {
        return new STBIIOCallbacks();
    }

    public static STBIIOCallbacks mallocStack(MemoryStack stack) {
        return new STBIIOCallbacks();
    }

    public static STBIIOCallbacks mallocStack() {
        return new STBIIOCallbacks();
    }

    public STBIIOCallbacks read(STBIReadCallbackI value) {
        read = value;
        return this;
    }

    public STBIIOCallbacks skip(STBISkipCallbackI value) {
        skip = value;
        return this;
    }

    public STBIIOCallbacks eof(STBIEOFCallbackI value) {
        eof = value;
        return this;
    }

    public void free() {
    }

    @Override
    public void close() {
    }
}
