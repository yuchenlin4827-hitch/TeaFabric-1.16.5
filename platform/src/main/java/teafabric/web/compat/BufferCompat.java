package teafabric.web.compat;

import java.nio.ByteBuffer;
import teafabric.web.log.LogSink;

/**
 * Accounting for {@code ByteBuffer.allocateDirect} in game code. Direct buffers live in the Wasm linear
 * memory heap and are only released after V8 garbage-collects the buffer object, so heavy per-frame
 * allocation can exhaust the heap ("OutOfMemoryError" in TeaVM's allocateDirect).
 */
public final class BufferCompat {
    private static long total;
    private static long nextReport = 256L << 20;
    private static int largeReported;

    private BufferCompat() {
    }

    public static ByteBuffer allocateDirect(int capacity) {
        total += capacity;
        if (capacity >= (4 << 20) && largeReported < 10) {
            largeReported++;
            LogSink.log(2, "WARN", "BufferCompat", "allocateDirect(" + capacity + ")", new Throwable("allocation site"));
        }
        if (total >= nextReport) {
            nextReport += 256L << 20;
            LogSink.log(2, "WARN", "BufferCompat", "allocateDirect total " + (total >> 20) + " MiB", new Throwable("allocation site"));
        }
        try {
            return zeroed(ByteBuffer.allocateDirect(capacity));
        } catch (OutOfMemoryError e) {
            LogSink.log(3, "ERROR", "BufferCompat", "allocateDirect(" + capacity + ") failed after " + (total >> 20)
                    + " MiB in total", e);
            throw e;
        }
    }

    private static final byte[] ZEROS = new byte[65536];

    /**
     * TeaVM takes direct buffers from its linear-memory heap without clearing reused memory; the JDK hands
     * out zeroed buffers and code relies on it (a new region file's header buffer showed old chunk offsets).
     */
    private static ByteBuffer zeroed(ByteBuffer buf) {
        int capacity = buf.capacity();
        for (int i = 0; i < capacity; i += ZEROS.length) {
            buf.position(i);
            buf.put(ZEROS, 0, Math.min(ZEROS.length, capacity - i));
        }
        buf.clear();
        return buf;
    }
}
