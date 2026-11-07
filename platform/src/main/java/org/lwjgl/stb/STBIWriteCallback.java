package org.lwjgl.stb;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import teafabric.web.mem.NativeMemory;

/** Callbacks are not native function pointers here: {@link #address()} is a registry handle. */
public abstract class STBIWriteCallback implements STBIWriteCallbackI, AutoCloseable {
    private static final List<STBIWriteCallback> REGISTRY = new ArrayList<>();
    private final long handle;

    protected STBIWriteCallback() {
        synchronized (REGISTRY) {
            REGISTRY.add(this);
            handle = REGISTRY.size();
        }
    }

    public static STBIWriteCallback create(STBIWriteCallbackI instance) {
        return new STBIWriteCallback() {
            @Override
            public void invoke(long context, long data, int size) {
                instance.invoke(context, data, size);
            }
        };
    }

    static STBIWriteCallback lookup(long handle) {
        synchronized (REGISTRY) {
            return handle > 0 && handle <= REGISTRY.size() ? REGISTRY.get((int) handle - 1) : null;
        }
    }

    public static ByteBuffer getData(long data, int size) {
        return NativeMemory.wrap(data, size);
    }

    public long address() {
        return handle;
    }

    public void free() {
        synchronized (REGISTRY) {
            REGISTRY.set((int) handle - 1, null);
        }
    }

    @Override
    public void close() {
        free();
    }
}
