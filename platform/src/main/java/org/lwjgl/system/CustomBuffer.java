package org.lwjgl.system;

/** Minimal CustomBuffer: a cursor over an array of structs in native memory. */
public abstract class CustomBuffer<SELF extends CustomBuffer<SELF>> extends Pointer.Default {
    protected int position;
    protected int limit;
    protected final int capacity;

    protected CustomBuffer(long address, int capacity) {
        super(address);
        this.capacity = capacity;
        this.limit = capacity;
    }

    protected abstract SELF self();

    public int capacity() {
        return capacity;
    }

    public int position() {
        return position;
    }

    public SELF position(int position) {
        this.position = position;
        return self();
    }

    public int limit() {
        return limit;
    }

    public SELF limit(int limit) {
        this.limit = limit;
        return self();
    }

    public int remaining() {
        return limit - position;
    }

    public boolean hasRemaining() {
        return position < limit;
    }

    public SELF flip() {
        limit = position;
        position = 0;
        return self();
    }

    public SELF rewind() {
        position = 0;
        return self();
    }

    public SELF clear() {
        position = 0;
        limit = capacity;
        return self();
    }

    public void free() {
        teafabric.web.mem.NativeMemory.free(address);
    }
}
