package org.teavm.classlib.java.nio.channels;

import java.io.IOException;

/** A page owns its storage exclusively, so locks always succeed. */
public class TFileLock implements AutoCloseable {
    private final TFileChannel channel;
    private final long position;
    private final long size;
    private final boolean shared;
    private boolean valid = true;

    protected TFileLock(TFileChannel channel, long position, long size, boolean shared) {
        this.channel = channel;
        this.position = position;
        this.size = size;
        this.shared = shared;
    }

    public final TFileChannel channel() {
        return channel;
    }

    public final long position() {
        return position;
    }

    public final long size() {
        return size;
    }

    public final boolean isShared() {
        return shared;
    }

    public boolean isValid() {
        return valid && channel.isOpen();
    }

    public void release() throws IOException {
        valid = false;
    }

    @Override
    public final void close() throws IOException {
        release();
    }
}
