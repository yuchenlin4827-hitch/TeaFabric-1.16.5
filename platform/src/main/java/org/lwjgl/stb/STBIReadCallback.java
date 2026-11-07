package org.lwjgl.stb;

public abstract class STBIReadCallback implements STBIReadCallbackI, AutoCloseable {
    public static STBIReadCallback create(STBIReadCallbackI instance) {
        return new STBIReadCallback() {
            @Override
            public int invoke(long user, long data, int size) {
                return instance.invoke(user, data, size);
            }
        };
    }

    public void free() {
    }

    @Override
    public void close() {
    }
}
