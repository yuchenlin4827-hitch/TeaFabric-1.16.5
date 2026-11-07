package org.lwjgl.system;

import teafabric.web.mem.NativeMemory;

/** Base of the few LWJGL structs Minecraft uses, laid out in emulated native memory. */
public abstract class Struct extends Pointer.Default {
    protected Struct(long address) {
        super(address);
    }

    public abstract int sizeof();

    protected static long allocate(int size, MemoryStack stack) {
        return stack == null ? NativeMemory.calloc(1, size) : stack.ncalloc(8, size, 1);
    }

    public void free() {
        NativeMemory.free(address);
    }
}
