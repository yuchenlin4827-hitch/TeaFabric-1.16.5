package org.teavm.classlib.java.nio;

import org.teavm.classlib.java.nio.file.TAddressBasedBuffer;
import org.teavm.interop.Address;

/**
 * Bridge into TeaVM's NIO internals for the LWJGL MemoryUtil emulation (named T* so TeaVM's
 * class library mapping treats it as {@code java.nio.TeaFabricBufferAccess}).
 *
 * <p>In the Wasm GC backend direct buffers live in a linear-memory heap
 * ({@code org.teavm.runtime.heap.Heap}), so they have real addresses that can be handed to
 * WebGL / WebAudio as views of the module's memory, exactly like LWJGL's native pointers.
 */
public final class TTeaFabricBufferAccess {
    private TTeaFabricBufferAccess() {
    }

    /** Address of element {@code position()} of a direct buffer, or 0 for heap buffers. */
    public static int address(java.nio.Buffer buffer) {
        Object o = buffer;
        if (!(o instanceof TAddressBasedBuffer)) {
            return 0;
        }
        int base = ((TAddressBasedBuffer) o).getDataAddress().toInt();
        return base + buffer.position() * elementSize(o);
    }

    /** Address of element 0 (ignores position). */
    public static int baseAddress(java.nio.Buffer buffer) {
        Object o = buffer;
        if (!(o instanceof TAddressBasedBuffer)) {
            return 0;
        }
        return ((TAddressBasedBuffer) o).getDataAddress().toInt();
    }

    static int elementSize(Object o) {
        if (o instanceof TByteBuffer) {
            return 1;
        }
        if (o instanceof TShortBuffer || o instanceof TCharBuffer) {
            return 2;
        }
        if (o instanceof TIntBuffer || o instanceof TFloatBuffer) {
            return 4;
        }
        return 8;
    }

    /** A little-endian direct ByteBuffer viewing {@code capacity} bytes at {@code address}; never freed by GC. */
    public static java.nio.ByteBuffer wrap(int address, int capacity) {
        TByteBufferWasmGC result = new TByteBufferWasmGC(null, null, 0, null, Address.fromInt(address), capacity,
                false);
        result.limit = capacity;
        result.order(TByteOrder.LITTLE_ENDIAN);
        return (java.nio.ByteBuffer) (Object) result;
    }
}
