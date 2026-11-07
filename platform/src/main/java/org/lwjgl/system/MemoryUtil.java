package org.lwjgl.system;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import org.lwjgl.PointerBuffer;
import teafabric.web.mem.NativeMemory;

/** LWJGL 3 MemoryUtil on TeaVM's Wasm linear memory (see {@link NativeMemory}). */
public final class MemoryUtil {
    public static final long NULL = 0L;
    public static final int PAGE_SIZE = 65536;
    public static final int CACHE_LINE_SIZE = 64;

    private MemoryUtil() {
    }

    public static long nmemAlloc(long size) {
        return NativeMemory.malloc(size);
    }

    public static long nmemAllocChecked(long size) {
        return NativeMemory.malloc(size);
    }

    public static long nmemCalloc(long num, long size) {
        return NativeMemory.calloc(num, size);
    }

    public static long nmemRealloc(long ptr, long size) {
        return NativeMemory.realloc(ptr, size);
    }

    public static void nmemFree(long ptr) {
        NativeMemory.free(ptr);
    }

    public static ByteBuffer memAlloc(int size) {
        return NativeMemory.wrap(NativeMemory.malloc(size), size);
    }

    public static ByteBuffer memCalloc(int size) {
        return NativeMemory.wrap(NativeMemory.calloc(1, size), size);
    }

    public static ByteBuffer memCalloc(int num, int size) {
        return memCalloc(num * size);
    }

    public static ShortBuffer memAllocShort(int count) {
        return memAlloc(count * 2).asShortBuffer();
    }

    public static IntBuffer memAllocInt(int count) {
        return memAlloc(count * 4).asIntBuffer();
    }

    public static LongBuffer memAllocLong(int count) {
        return memAlloc(count * 8).asLongBuffer();
    }

    public static FloatBuffer memAllocFloat(int count) {
        return memAlloc(count * 4).asFloatBuffer();
    }

    public static PointerBuffer memAllocPointer(int count) {
        return PointerBuffer.create(memAlloc(count * 8));
    }

    /** Reallocates keeping position (clamped) like LWJGL; the old buffer must not be used afterwards. */
    public static ByteBuffer memRealloc(ByteBuffer buffer, int size) {
        long base = buffer == null ? 0 : NativeMemory.baseAddress(buffer);
        int pos = buffer == null ? 0 : Math.min(buffer.position(), size);
        long p = NativeMemory.realloc(base, size);
        ByteBuffer result = NativeMemory.wrap(p, size);
        result.position(pos);
        return result;
    }

    public static void memFree(Buffer buffer) {
        if (buffer != null) {
            NativeMemory.free(NativeMemory.baseAddress(buffer));
        }
    }

    public static void memFree(PointerBuffer buffer) {
        if (buffer != null) {
            memFree(buffer.backing());
        }
    }

    public static long memAddress0(Buffer buffer) {
        return NativeMemory.baseAddress(buffer);
    }

    public static long memAddress(ByteBuffer buffer) {
        return NativeMemory.address(buffer);
    }

    public static long memAddress(ShortBuffer buffer) {
        return NativeMemory.address(buffer);
    }

    public static long memAddress(IntBuffer buffer) {
        return NativeMemory.address(buffer);
    }

    public static long memAddress(LongBuffer buffer) {
        return NativeMemory.address(buffer);
    }

    public static long memAddress(FloatBuffer buffer) {
        return NativeMemory.address(buffer);
    }

    public static long memAddress(Buffer buffer) {
        return NativeMemory.address(buffer);
    }

    public static long memAddress(ByteBuffer buffer, int position) {
        return NativeMemory.baseAddress(buffer) + position;
    }

    public static long memAddressSafe(ByteBuffer buffer) {
        return buffer == null ? NULL : memAddress(buffer);
    }

    public static long memAddressSafe(IntBuffer buffer) {
        return buffer == null ? NULL : memAddress(buffer);
    }

    public static long memAddressSafe(FloatBuffer buffer) {
        return buffer == null ? NULL : memAddress(buffer);
    }

    public static ByteBuffer memByteBuffer(long address, int capacity) {
        return NativeMemory.wrap(address, capacity);
    }

    public static ByteBuffer memByteBufferSafe(long address, int capacity) {
        return address == NULL ? null : NativeMemory.wrap(address, capacity);
    }

    public static IntBuffer memIntBuffer(long address, int capacity) {
        return NativeMemory.wrap(address, capacity * 4).asIntBuffer();
    }

    public static FloatBuffer memFloatBuffer(long address, int capacity) {
        return NativeMemory.wrap(address, capacity * 4).asFloatBuffer();
    }

    public static ShortBuffer memShortBuffer(long address, int capacity) {
        return NativeMemory.wrap(address, capacity * 2).asShortBuffer();
    }

    public static LongBuffer memLongBuffer(long address, int capacity) {
        return NativeMemory.wrap(address, capacity * 8).asLongBuffer();
    }

    public static ByteBuffer memSlice(ByteBuffer buffer) {
        return memByteBuffer(memAddress(buffer), buffer.remaining());
    }

    public static ByteBuffer memSlice(ByteBuffer buffer, int offset, int capacity) {
        return memByteBuffer(memAddress(buffer) + offset, capacity);
    }

    public static void memCopy(long src, long dst, long bytes) {
        NativeMemory.copy(src, dst, bytes);
    }

    public static void memCopy(ByteBuffer src, ByteBuffer dst) {
        NativeMemory.copy(memAddress(src), memAddress(dst), src.remaining());
    }

    public static void memSet(long ptr, int value, long bytes) {
        NativeMemory.fill(ptr, value, bytes);
    }

    public static void memSet(ByteBuffer buffer, int value) {
        NativeMemory.fill(memAddress(buffer), value, buffer.remaining());
    }

    public static byte memGetByte(long ptr) {
        return NativeMemory.getByte(ptr);
    }

    public static short memGetShort(long ptr) {
        return NativeMemory.getShort(ptr);
    }

    public static int memGetInt(long ptr) {
        return NativeMemory.getInt(ptr);
    }

    public static long memGetLong(long ptr) {
        return NativeMemory.getLong(ptr);
    }

    public static float memGetFloat(long ptr) {
        return NativeMemory.getFloat(ptr);
    }

    public static double memGetDouble(long ptr) {
        return NativeMemory.getDouble(ptr);
    }

    public static long memGetAddress(long ptr) {
        return NativeMemory.getLong(ptr);
    }

    public static void memPutByte(long ptr, byte value) {
        NativeMemory.putByte(ptr, value);
    }

    public static void memPutShort(long ptr, short value) {
        NativeMemory.putShort(ptr, value);
    }

    public static void memPutInt(long ptr, int value) {
        NativeMemory.putInt(ptr, value);
    }

    public static void memPutLong(long ptr, long value) {
        NativeMemory.putLong(ptr, value);
    }

    public static void memPutFloat(long ptr, float value) {
        NativeMemory.putFloat(ptr, value);
    }

    public static void memPutDouble(long ptr, double value) {
        NativeMemory.putDouble(ptr, value);
    }

    public static void memPutAddress(long ptr, long value) {
        NativeMemory.putLong(ptr, value);
    }

    public static String memUTF8(long address) {
        return address == NULL ? null : NativeMemory.utf8(address);
    }

    public static String memUTF8Safe(long address) {
        return memUTF8(address);
    }

    public static String memUTF8(ByteBuffer buffer) {
        byte[] b = new byte[buffer.remaining()];
        buffer.duplicate().get(b);
        int len = 0;
        while (len < b.length && b[len] != 0) {
            len++;
        }
        return new String(b, 0, len, StandardCharsets.UTF_8);
    }

    public static String memUTF8(ByteBuffer buffer, int length) {
        byte[] b = new byte[length];
        buffer.duplicate().get(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    public static String memASCII(long address) {
        return memUTF8(address);
    }

    public static String memASCII(ByteBuffer buffer) {
        return memUTF8(buffer);
    }

    public static String memASCII(ByteBuffer buffer, int length) {
        byte[] b = new byte[length];
        buffer.duplicate().get(b);
        return new String(b, StandardCharsets.ISO_8859_1);
    }

    public static ByteBuffer memUTF8(CharSequence text) {
        return memUTF8(text, true);
    }

    public static ByteBuffer memUTF8(CharSequence text, boolean nullTerminated) {
        byte[] b = text.toString().getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = memAlloc(b.length + (nullTerminated ? 1 : 0));
        buf.put(b);
        if (nullTerminated) {
            buf.put((byte) 0);
        }
        buf.flip();
        return buf;
    }

    public static ByteBuffer memASCII(CharSequence text) {
        return memUTF8(text, true);
    }

    public static int memLengthUTF8(CharSequence value, boolean nullTerminated) {
        return value.toString().getBytes(StandardCharsets.UTF_8).length + (nullTerminated ? 1 : 0);
    }

    public static ByteOrder nativeOrder() {
        return ByteOrder.LITTLE_ENDIAN;
    }
}
