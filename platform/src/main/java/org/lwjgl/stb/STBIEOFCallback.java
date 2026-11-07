package org.lwjgl.stb;

public abstract class STBIEOFCallback implements STBIEOFCallbackI, AutoCloseable {
    public static STBIEOFCallback create(STBIEOFCallbackI instance) {
        return new STBIEOFCallback() {
            @Override
            public int invoke(long user) {
                return instance.invoke(user);
            }
        };
    }

    public void free() {
    }

    @Override
    public void close() {
    }
}
