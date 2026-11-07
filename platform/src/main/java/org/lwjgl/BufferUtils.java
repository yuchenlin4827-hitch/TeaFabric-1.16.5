package org.lwjgl;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import teafabric.web.mem.NativeMemory;

/** Garbage-collected direct buffers (TeaVM frees them through a FinalizationRegistry). */
public final class BufferUtils {
    private BufferUtils() {
    }

    public static ByteBuffer createByteBuffer(int capacity) {
        return ByteBuffer.allocateDirect(capacity).order(NativeMemory.isLittleEndian());
    }

    public static ShortBuffer createShortBuffer(int capacity) {
        return createByteBuffer(capacity * 2).asShortBuffer();
    }

    public static IntBuffer createIntBuffer(int capacity) {
        return createByteBuffer(capacity * 4).asIntBuffer();
    }

    public static LongBuffer createLongBuffer(int capacity) {
        return createByteBuffer(capacity * 8).asLongBuffer();
    }

    public static FloatBuffer createFloatBuffer(int capacity) {
        return createByteBuffer(capacity * 4).asFloatBuffer();
    }

    public static PointerBuffer createPointerBuffer(int capacity) {
        return PointerBuffer.allocateDirect(capacity);
    }
}
