package teafabric.web.mem;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import org.teavm.classlib.java.nio.TTeaFabricBufferAccess;
import org.teavm.interop.Address;
import org.teavm.interop.Import;
import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.Float32Array;
import org.teavm.jso.typedarrays.Int32Array;
import org.teavm.jso.typedarrays.Uint8Array;
import org.teavm.runtime.heap.Heap;

/**
 * Emulates LWJGL's native memory on top of TeaVM's Wasm linear-memory heap. "Pointers" are
 * offsets into the module's memory, so WebGL and WebAudio read them through typed-array views
 * without copying.
 */
public final class NativeMemory {
    private NativeMemory() {
    }

    @Import(module = "teavmMemory", name = "linearMemory")
    private static native ArrayBuffer linearMemory();

    public static long malloc(long size) {
        Address a = Heap.alloc((int) Math.max(1, size));
        if (a == null) {
            throw new OutOfMemoryError("Native heap exhausted allocating " + size + " bytes");
        }
        return Integer.toUnsignedLong(a.toInt());
    }

    public static long calloc(long count, long size) {
        long bytes = count * size;
        long p = malloc(bytes);
        fill(p, 0, bytes);
        return p;
    }

    public static long realloc(long ptr, long size) {
        if (ptr == 0) {
            return malloc(size);
        }
        Address a = Heap.realloc(Address.fromInt((int) ptr), (int) Math.max(1, size));
        if (a == null) {
            throw new OutOfMemoryError("Native heap exhausted reallocating " + size + " bytes");
        }
        return Integer.toUnsignedLong(a.toInt());
    }

    public static void free(long ptr) {
        if (ptr != 0) {
            Heap.release(Address.fromInt((int) ptr));
        }
    }

    public static java.nio.ByteOrder isLittleEndian() {
        return java.nio.ByteOrder.LITTLE_ENDIAN;
    }

    public static ByteBuffer wrap(long address, int capacity) {
        return TTeaFabricBufferAccess.wrap((int) address, capacity);
    }

    /** Address of a direct buffer's current position (LWJGL's memAddress semantics). */
    public static long address(Buffer buffer) {
        if (buffer == null) {
            return 0;
        }
        int a = TTeaFabricBufferAccess.address(buffer);
        if (a == 0) {
            throw new IllegalArgumentException("Not a direct buffer: " + buffer);
        }
        return Integer.toUnsignedLong(a);
    }

    public static long baseAddress(Buffer buffer) {
        return Integer.toUnsignedLong(TTeaFabricBufferAccess.baseAddress(buffer));
    }

    public static boolean isNative(Buffer buffer) {
        return buffer != null && TTeaFabricBufferAccess.baseAddress(buffer) != 0;
    }

    public static byte getByte(long p) {
        return Address.fromInt((int) p).getByte();
    }

    public static short getShort(long p) {
        return Address.fromInt((int) p).getShort();
    }

    public static int getInt(long p) {
        return Address.fromInt((int) p).getInt();
    }

    public static long getLong(long p) {
        return Address.fromInt((int) p).getLong();
    }

    public static float getFloat(long p) {
        return Address.fromInt((int) p).getFloat();
    }

    public static double getDouble(long p) {
        return Address.fromInt((int) p).getDouble();
    }

    public static void putByte(long p, byte v) {
        Address.fromInt((int) p).putByte(v);
    }

    public static void putShort(long p, short v) {
        Address.fromInt((int) p).putShort(v);
    }

    public static void putInt(long p, int v) {
        Address.fromInt((int) p).putInt(v);
    }

    public static void putLong(long p, long v) {
        Address.fromInt((int) p).putLong(v);
    }

    public static void putFloat(long p, float v) {
        Address.fromInt((int) p).putFloat(v);
    }

    public static void putDouble(long p, double v) {
        Address.fromInt((int) p).putDouble(v);
    }

    public static void copy(long src, long dst, long bytes) {
        if (bytes <= 0) {
            return;
        }
        copyWithin(linearMemory(), (int) dst, (int) src, (int) bytes);
    }

    public static void fill(long p, int value, long bytes) {
        if (bytes <= 0) {
            return;
        }
        fillJs(linearMemory(), (int) p, value & 0xff, (int) bytes);
    }

    @JSBody(params = {"mem", "dst", "src", "len"}, script = "new Uint8Array(mem).copyWithin(dst >>> 0, "
            + "src >>> 0, (src >>> 0) + len);")
    private static native void copyWithin(ArrayBuffer mem, int dst, int src, int len);

    @JSBody(params = {"mem", "p", "value", "len"}, script = "new Uint8Array(mem, p >>> 0, len).fill(value);")
    private static native void fillJs(ArrayBuffer mem, int p, int value, int len);

    public static ArrayBuffer memory() {
        return linearMemory();
    }

    /** Live views of linear memory (re-created per call: memory growth detaches old buffers). */
    public static Uint8Array u8(long p, int length) {
        return Uint8Array.create(linearMemory(), (int) p, length);
    }

    public static Float32Array f32(long p, int count) {
        return Float32Array.create(linearMemory(), (int) p, count);
    }

    public static Int32Array i32(long p, int count) {
        return Int32Array.create(linearMemory(), (int) p, count);
    }

    public static void copyToArray(long p, byte[] dst, int off, int len) {
        for (int i = 0; i < len; i++) {
            dst[off + i] = getByte(p + i);
        }
    }

    public static void copyFromArray(byte[] src, int off, long p, int len) {
        for (int i = 0; i < len; i++) {
            putByte(p + i, src[off + i]);
        }
    }

    public static String utf8(long p) {
        int len = 0;
        while (getByte(p + len) != 0) {
            len++;
        }
        byte[] b = new byte[len];
        copyToArray(p, b, 0, len);
        return new String(b, java.nio.charset.StandardCharsets.UTF_8);
    }
}
