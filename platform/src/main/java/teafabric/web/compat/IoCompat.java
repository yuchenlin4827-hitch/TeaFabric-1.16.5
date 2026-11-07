package teafabric.web.compat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.teavm.classlib.java.nio.channels.TFileChannel;

/** Redirect targets for java.io / java.nio members missing from TeaVM's class library. */
public final class IoCompat {
    private IoCompat() {
    }

    public static Path toPath(File file) {
        return Paths.get(file.getPath());
    }

    public static FileChannel getChannel(RandomAccessFile raf) {
        return (FileChannel) (Object) TFileChannel.of(raf, true);
    }

    /** Stream-backed channel: TeaVM's FileInputStream does not expose its file. */
    public static FileChannel getChannel(FileInputStream in) {
        return (FileChannel) (Object) new StreamChannel(in, null);
    }

    public static FileChannel getChannel(FileOutputStream out) {
        return (FileChannel) (Object) new StreamChannel(null, out);
    }

    /** com.ibm.icu.impl.ICUBinary#mapFile without memory mapping. */
    public static ByteBuffer mapFile(File path) {
        try (InputStream in = new FileInputStream(path)) {
            return ByteBuffer.wrap(in.readAllBytes());
        } catch (IOException e) {
            return null;
        }
    }

    static final class StreamChannel extends TFileChannel {
        private final InputStream in;
        private final OutputStream out;
        private long position;

        StreamChannel(InputStream in, OutputStream out) {
            this.in = in;
            this.out = out;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            if (in == null) {
                throw new java.nio.channels.NonReadableChannelException();
            }
            int len = dst.remaining();
            if (len == 0) {
                return 0;
            }
            byte[] tmp = new byte[Math.min(len, 65536)];
            int n = in.read(tmp, 0, tmp.length);
            if (n > 0) {
                dst.put(tmp, 0, n);
                position += n;
            }
            return n;
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            if (out == null) {
                throw new java.nio.channels.NonWritableChannelException();
            }
            int len = src.remaining();
            byte[] tmp = new byte[len];
            src.get(tmp);
            out.write(tmp);
            position += len;
            return len;
        }

        @Override
        public long position() {
            return position;
        }

        @Override
        public TFileChannel position(long newPosition) throws IOException {
            if (in != null && newPosition >= position) {
                long skip = newPosition - position;
                while (skip > 0) {
                    long n = in.skip(skip);
                    if (n <= 0) {
                        break;
                    }
                    skip -= n;
                    position += n;
                }
                return this;
            }
            throw new IOException("Stream channel cannot seek backwards");
        }

        @Override
        public long size() throws IOException {
            return in != null ? position + in.available() : position;
        }

        @Override
        public TFileChannel truncate(long size) throws IOException {
            throw new IOException("Cannot truncate a stream channel");
        }

        @Override
        protected void implCloseChannel() throws IOException {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
        }
    }
}
