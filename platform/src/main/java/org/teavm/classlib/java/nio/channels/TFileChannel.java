package org.teavm.classlib.java.nio.channels;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** FileChannel over TeaVM's virtual file system (via RandomAccessFile). */
public abstract class TFileChannel implements SeekableByteChannel {
    private boolean open = true;

    protected TFileChannel() {
    }

    public static TFileChannel open(Path path, OpenOption... options) throws IOException {
        return open(path, new HashSet<>(Arrays.asList(options)));
    }

    public static TFileChannel open(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
            throws IOException {
        File file = path.toFile();
        boolean write = options.contains(StandardOpenOption.WRITE) || options.contains(StandardOpenOption.APPEND);
        if (!file.exists()) {
            if (!write || !(options.contains(StandardOpenOption.CREATE)
                    || options.contains(StandardOpenOption.CREATE_NEW))) {
                throw new java.nio.file.NoSuchFileException(path.toString());
            }
            // TeaVM's RandomAccessFile does not create files; CREATE / CREATE_NEW do (session.lock, region files).
            if (!file.createNewFile() && !file.exists()) {
                throw new java.nio.file.NoSuchFileException(path.toString());
            }
        } else if (options.contains(StandardOpenOption.CREATE_NEW)) {
            throw new java.nio.file.FileAlreadyExistsException(path.toString());
        }
        RandomAccessFile raf = new RandomAccessFile(file, write ? "rw" : "r");
        if (write && options.contains(StandardOpenOption.TRUNCATE_EXISTING)) {
            raf.setLength(0);
        }
        RafChannel ch = new RafChannel(raf, write);
        if (options.contains(StandardOpenOption.APPEND)) {
            ch.position(raf.length());
        }
        return ch;
    }

    public static TFileChannel of(RandomAccessFile raf, boolean writable) {
        return new RafChannel(raf, writable);
    }

    @Override
    public abstract int read(ByteBuffer dst) throws IOException;

    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        long total = 0;
        for (int i = offset; i < offset + length; i++) {
            int n = read(dsts[i]);
            if (n < 0) {
                return total == 0 ? -1 : total;
            }
            total += n;
            if (dsts[i].hasRemaining()) {
                break;
            }
        }
        return total;
    }

    public final long read(ByteBuffer[] dsts) throws IOException {
        return read(dsts, 0, dsts.length);
    }

    @Override
    public abstract int write(ByteBuffer src) throws IOException;

    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        long total = 0;
        for (int i = offset; i < offset + length; i++) {
            total += write(srcs[i]);
        }
        return total;
    }

    public final long write(ByteBuffer[] srcs) throws IOException {
        return write(srcs, 0, srcs.length);
    }

    @Override
    public abstract long position() throws IOException;

    @Override
    public abstract TFileChannel position(long newPosition) throws IOException;

    @Override
    public abstract long size() throws IOException;

    @Override
    public abstract TFileChannel truncate(long size) throws IOException;

    public void force(boolean metaData) throws IOException {
    }

    public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
        long saved = position();
        position(position);
        ByteBuffer buf = ByteBuffer.allocate((int) Math.min(count, 65536));
        long done = 0;
        while (done < count) {
            buf.clear();
            buf.limit((int) Math.min(buf.capacity(), count - done));
            int n = read(buf);
            if (n <= 0) {
                break;
            }
            buf.flip();
            while (buf.hasRemaining()) {
                target.write(buf);
            }
            done += n;
        }
        position(saved);
        return done;
    }

    public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
        long saved = position();
        position(position);
        ByteBuffer buf = ByteBuffer.allocate((int) Math.min(count, 65536));
        long done = 0;
        while (done < count) {
            buf.clear();
            buf.limit((int) Math.min(buf.capacity(), count - done));
            int n = src.read(buf);
            if (n <= 0) {
                break;
            }
            buf.flip();
            done += write(buf);
        }
        position(saved);
        return done;
    }

    public int read(ByteBuffer dst, long position) throws IOException {
        long saved = position();
        position(position);
        try {
            return read(dst);
        } finally {
            position(saved);
        }
    }

    public int write(ByteBuffer src, long position) throws IOException {
        long saved = position();
        position(position);
        try {
            return write(src);
        } finally {
            position(saved);
        }
    }

    /** No memory mapping in the browser: returns a heap copy of the region. */
    public ByteBuffer mapCopy(long position, long size) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate((int) size);
        read(buf, position);
        buf.flip();
        return buf;
    }

    public TFileLock lock() throws IOException {
        return new TFileLock(this, 0, Long.MAX_VALUE, false);
    }

    public TFileLock lock(long position, long size, boolean shared) throws IOException {
        return new TFileLock(this, position, size, shared);
    }

    public TFileLock tryLock() throws IOException {
        return lock();
    }

    public TFileLock tryLock(long position, long size, boolean shared) throws IOException {
        return lock(position, size, shared);
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void close() throws IOException {
        if (open) {
            open = false;
            implCloseChannel();
        }
    }

    protected abstract void implCloseChannel() throws IOException;

    static final class RafChannel extends TFileChannel {
        private final RandomAccessFile raf;
        private final boolean writable;

        RafChannel(RandomAccessFile raf, boolean writable) {
            this.raf = raf;
            this.writable = writable;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            int len = dst.remaining();
            if (len == 0) {
                return 0;
            }
            byte[] tmp = new byte[len];
            int n = raf.read(tmp, 0, len);
            if (n > 0) {
                dst.put(tmp, 0, n);
            }
            return n;
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            if (!writable) {
                throw new java.nio.channels.NonWritableChannelException();
            }
            int len = src.remaining();
            byte[] tmp = new byte[len];
            src.get(tmp);
            raf.write(tmp);
            return len;
        }

        @Override
        public long position() throws IOException {
            return raf.getFilePointer();
        }

        @Override
        public TFileChannel position(long newPosition) throws IOException {
            raf.seek(newPosition);
            return this;
        }

        @Override
        public long size() throws IOException {
            return raf.length();
        }

        @Override
        public TFileChannel truncate(long size) throws IOException {
            if (size < raf.length()) {
                raf.setLength(size);
            }
            return this;
        }

        @Override
        protected void implCloseChannel() throws IOException {
            raf.close();
        }
    }
}
