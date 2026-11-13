package teafabric.web.compat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Redirect targets for java.io / java.nio members missing from TeaVM's class library. Channels
 * are written against the public FileChannel API (TeaVM maps it to the class-library version).
 */
public final class IoCompat {
    private IoCompat() {
    }

    /** {@code Files.newByteChannel}: TeaVM has none; a FileChannel on the in-memory file system (world icon.png). */
    public static java.nio.channels.SeekableByteChannel newByteChannel(Path path,
            java.util.Set<? extends java.nio.file.OpenOption> options, java.nio.file.attribute.FileAttribute<?>... attrs)
            throws java.io.IOException {
        return FileChannel.open(path, options, attrs);
    }

    public static java.nio.channels.SeekableByteChannel newByteChannel(Path path, java.nio.file.OpenOption... options)
            throws java.io.IOException {
        return FileChannel.open(path, options);
    }

    public static Path toPath(File file) {
        return Paths.get(file.getPath());
    }

    public static FileChannel getChannel(RandomAccessFile raf) {
        return new Channel(raf, null, null);
    }

    public static FileChannel getChannel(FileInputStream in) {
        return new Channel(null, in, null);
    }

    public static FileChannel getChannel(FileOutputStream out) {
        return new Channel(null, null, out);
    }

    /** com.ibm.icu.impl.ICUBinary#mapFile without memory mapping. */
    public static ByteBuffer mapFile(File path) {
        try (InputStream in = new FileInputStream(path)) {
            return ByteBuffer.wrap(in.readAllBytes());
        } catch (IOException e) {
            return null;
        }
    }

    /** FileChannel over a RandomAccessFile, or a forward-only stream. */
    static final class Channel extends FileChannel {
        private final RandomAccessFile raf;
        private final InputStream in;
        private final OutputStream out;
        private long position;

        Channel(RandomAccessFile raf, InputStream in, OutputStream out) {
            this.raf = raf;
            this.in = in;
            this.out = out;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            int len = dst.remaining();
            if (len == 0) {
                return 0;
            }
            byte[] tmp = new byte[Math.min(len, 65536)];
            int n;
            if (raf != null) {
                n = raf.read(tmp, 0, tmp.length);
            } else if (in != null) {
                n = in.read(tmp, 0, tmp.length);
            } else {
                throw new java.nio.channels.NonReadableChannelException();
            }
            if (n > 0) {
                dst.put(tmp, 0, n);
                position += n;
            }
            return n;
        }

        @Override
        public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
            long total = 0;
            for (int i = offset; i < offset + length; i++) {
                int n = read(dsts[i]);
                if (n < 0) {
                    return total == 0 ? -1 : total;
                }
                total += n;
            }
            return total;
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            int len = src.remaining();
            byte[] tmp = new byte[len];
            src.get(tmp);
            if (raf != null) {
                raf.write(tmp);
            } else if (out != null) {
                out.write(tmp);
            } else {
                throw new java.nio.channels.NonWritableChannelException();
            }
            position += len;
            return len;
        }

        @Override
        public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
            long total = 0;
            for (int i = offset; i < offset + length; i++) {
                total += write(srcs[i]);
            }
            return total;
        }

        @Override
        public long position() throws IOException {
            return raf != null ? raf.getFilePointer() : position;
        }

        @Override
        public FileChannel position(long newPosition) throws IOException {
            if (raf != null) {
                raf.seek(newPosition);
            } else if (in != null && newPosition >= position) {
                in.skip(newPosition - position);
                position = newPosition;
            } else {
                throw new IOException("Stream channel cannot seek");
            }
            return this;
        }

        @Override
        public long size() throws IOException {
            if (raf != null) {
                return raf.length();
            }
            return in != null ? position + in.available() : position;
        }

        @Override
        public FileChannel truncate(long size) throws IOException {
            if (raf != null && size < raf.length()) {
                raf.setLength(size);
            }
            return this;
        }

        @Override
        public void force(boolean metaData) {
        }

        @Override
        public long transferTo(long pos, long count, WritableByteChannel target) throws IOException {
            long saved = position();
            position(pos);
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
            if (raf != null) {
                position(saved);
            }
            return done;
        }

        @Override
        public long transferFrom(ReadableByteChannel src, long pos, long count) throws IOException {
            long saved = position();
            position(pos);
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
            if (raf != null) {
                position(saved);
            }
            return done;
        }

        @Override
        public int read(ByteBuffer dst, long pos) throws IOException {
            long saved = position();
            position(pos);
            try {
                return read(dst);
            } finally {
                if (raf != null) {
                    position(saved);
                }
            }
        }

        @Override
        public int write(ByteBuffer src, long pos) throws IOException {
            long saved = position();
            position(pos);
            try {
                return write(src);
            } finally {
                if (raf != null) {
                    position(saved);
                }
            }
        }

        @Override
        public MappedByteBuffer map(MapMode mode, long pos, long size) throws IOException {
            throw new IOException("Memory-mapped files are not supported in the browser");
        }

        @Override
        public FileLock lock(long pos, long size, boolean shared) {
            return new FileLock(this, pos, size, shared) {
                private boolean valid = true;

                @Override
                public boolean isValid() {
                    return valid;
                }

                @Override
                public void release() {
                    valid = false;
                }
            };
        }

        @Override
        public FileLock tryLock(long pos, long size, boolean shared) {
            return lock(pos, size, shared);
        }

        @Override
        protected void implCloseChannel() throws IOException {
            if (raf != null) {
                raf.close();
            }
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
        }
    }
}
