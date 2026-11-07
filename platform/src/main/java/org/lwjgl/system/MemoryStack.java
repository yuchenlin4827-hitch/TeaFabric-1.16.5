package org.lwjgl.system;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import org.lwjgl.PointerBuffer;
import teafabric.web.mem.NativeMemory;

/**
 * LWJGL's thread-local stack allocator. One stack per green thread; frames are released on
 * {@link #close()} (try-with-resources), as in LWJGL.
 */
public final class MemoryStack implements AutoCloseable {
    private static final int SIZE = 256 * 1024;
    private static final ThreadLocal<MemoryStack> STACKS = ThreadLocal.withInitial(MemoryStack::new);

    private final long base;
    private int pointer;
    private int[] frames = new int[16];
    private int frameIndex;

    private MemoryStack() {
        base = NativeMemory.malloc(SIZE);
        pointer = SIZE;
    }

    public static MemoryStack create() {
        return new MemoryStack();
    }

    public static MemoryStack stackGet() {
        return STACKS.get();
    }

    public static MemoryStack stackPush() {
        return stackGet().push();
    }

    public static MemoryStack stackPop() {
        return stackGet().pop();
    }

    public MemoryStack push() {
        if (frameIndex == frames.length) {
            frames = java.util.Arrays.copyOf(frames, frames.length * 2);
        }
        frames[frameIndex++] = pointer;
        return this;
    }

    public MemoryStack pop() {
        pointer = frames[--frameIndex];
        return this;
    }

    @Override
    public void close() {
        pop();
    }

    public long getAddress() {
        return base;
    }

    public int getSize() {
        return SIZE;
    }

    public int getFrameIndex() {
        return frameIndex;
    }

    public long getPointerAddress() {
        return base + pointer;
    }

    public int getPointer() {
        return pointer;
    }

    public long nmalloc(int size) {
        return nmalloc(8, size);
    }

    public long nmalloc(int alignment, int size) {
        int newPointer = (pointer - size) & ~(Math.max(alignment, 1) - 1);
        if (newPointer < 0) {
            throw new OutOfMemoryError("Out of stack space.");
        }
        pointer = newPointer;
        return base + newPointer;
    }

    public long ncalloc(int alignment, int num, int size) {
        int bytes = num * size;
        long p = nmalloc(alignment, bytes);
        NativeMemory.fill(p, 0, bytes);
        return p;
    }

    public ByteBuffer malloc(int size) {
        return NativeMemory.wrap(nmalloc(8, size), size);
    }

    public ByteBuffer malloc(int alignment, int size) {
        return NativeMemory.wrap(nmalloc(alignment, size), size);
    }

    public ByteBuffer calloc(int size) {
        return NativeMemory.wrap(ncalloc(8, size, 1), size);
    }

    public ByteBuffer calloc(int alignment, int size) {
        return NativeMemory.wrap(ncalloc(alignment, size, 1), size);
    }

    public ShortBuffer mallocShort(int size) {
        return malloc(size * 2).asShortBuffer();
    }

    public IntBuffer mallocInt(int size) {
        return malloc(size * 4).asIntBuffer();
    }

    public LongBuffer mallocLong(int size) {
        return malloc(size * 8).asLongBuffer();
    }

    public FloatBuffer mallocFloat(int size) {
        return malloc(size * 4).asFloatBuffer();
    }

    public IntBuffer callocInt(int size) {
        return calloc(size * 4).asIntBuffer();
    }

    public FloatBuffer callocFloat(int size) {
        return calloc(size * 4).asFloatBuffer();
    }

    public PointerBuffer mallocPointer(int size) {
        return PointerBuffer.create(malloc(size * 8));
    }

    public PointerBuffer callocPointer(int size) {
        return PointerBuffer.create(calloc(size * 8));
    }

    public IntBuffer ints(int... values) {
        IntBuffer b = mallocInt(values.length);
        b.put(values).flip();
        return b;
    }

    public FloatBuffer floats(float... values) {
        FloatBuffer b = mallocFloat(values.length);
        b.put(values).flip();
        return b;
    }

    public ByteBuffer UTF8(CharSequence text) {
        byte[] bytes = text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ByteBuffer b = malloc(bytes.length + 1);
        b.put(bytes).put((byte) 0).flip();
        return b;
    }

    public ByteBuffer ASCII(CharSequence text) {
        return UTF8(text);
    }

    public static ByteBuffer stackMalloc(int size) {
        return stackGet().malloc(size);
    }

    public static IntBuffer stackMallocInt(int size) {
        return stackGet().mallocInt(size);
    }

    public static FloatBuffer stackMallocFloat(int size) {
        return stackGet().mallocFloat(size);
    }

    public static PointerBuffer stackMallocPointer(int size) {
        return stackGet().mallocPointer(size);
    }
}
