package org.lwjgl;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import teafabric.web.mem.NativeMemory;

/** Buffer of 64-bit pointers backed by a native ByteBuffer. */
public final class PointerBuffer {
    private final ByteBuffer backing;
    private int position;
    private int limit;
    private final int capacity;

    private PointerBuffer(ByteBuffer backing) {
        this.backing = backing;
        this.capacity = backing.capacity() / 8;
        this.limit = capacity;
    }

    public static PointerBuffer create(ByteBuffer backing) {
        return new PointerBuffer(backing);
    }

    public static PointerBuffer allocateDirect(int capacity) {
        return new PointerBuffer(BufferUtils.createByteBuffer(capacity * 8));
    }

    public ByteBuffer backing() {
        return backing;
    }

    public long address() {
        return NativeMemory.baseAddress(backing) + position * 8L;
    }

    public long address0() {
        return NativeMemory.baseAddress(backing);
    }

    public int capacity() {
        return capacity;
    }

    public int position() {
        return position;
    }

    public PointerBuffer position(int p) {
        position = p;
        return this;
    }

    public int limit() {
        return limit;
    }

    public PointerBuffer limit(int l) {
        limit = l;
        return this;
    }

    public int remaining() {
        return limit - position;
    }

    public boolean hasRemaining() {
        return position < limit;
    }

    public PointerBuffer flip() {
        limit = position;
        position = 0;
        return this;
    }

    public PointerBuffer rewind() {
        position = 0;
        return this;
    }

    public PointerBuffer clear() {
        position = 0;
        limit = capacity;
        return this;
    }

    public long get() {
        return get(position++);
    }

    public long get(int index) {
        return NativeMemory.getLong(NativeMemory.baseAddress(backing) + index * 8L);
    }

    public PointerBuffer put(long value) {
        return put(position++, value);
    }

    public PointerBuffer put(int index, long value) {
        NativeMemory.putLong(NativeMemory.baseAddress(backing) + index * 8L, value);
        return this;
    }

    public PointerBuffer put(int index, ByteBuffer value) {
        return put(index, NativeMemory.address(value));
    }

    public PointerBuffer put(ByteBuffer value) {
        return put(position++, NativeMemory.address(value));
    }

    public FloatBuffer getFloatBuffer(int index, int size) {
        return NativeMemory.wrap(get(index), size * 4).asFloatBuffer();
    }

    public ByteBuffer getByteBuffer(int index, int size) {
        return NativeMemory.wrap(get(index), size);
    }

    public PointerBuffer getPointerBuffer(int size) {
        return new PointerBuffer(NativeMemory.wrap(get(position++), size * 8));
    }

    public String getStringUTF8(int index) {
        return NativeMemory.utf8(get(index));
    }

    public void free() {
        NativeMemory.free(NativeMemory.baseAddress(backing));
    }
}
