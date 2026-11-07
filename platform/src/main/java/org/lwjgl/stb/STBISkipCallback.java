package org.lwjgl.stb;

public abstract class STBISkipCallback implements STBISkipCallbackI, AutoCloseable {
    public static STBISkipCallback create(STBISkipCallbackI instance) {
        return new STBISkipCallback() {
            @Override
            public void invoke(long user, int n) {
                instance.invoke(user, n);
            }
        };
    }

    public void free() {
    }

    @Override
    public void close() {
    }
}
