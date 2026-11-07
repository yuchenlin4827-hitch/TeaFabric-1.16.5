package org.teavm.classlib.java.nio.channels;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public final class TChannels {
    private TChannels() {
    }

    public static ReadableByteChannel newChannel(InputStream in) {
        return new ReadableByteChannel() {
            private boolean open = true;
            private final byte[] buf = new byte[8192];

            @Override
            public int read(ByteBuffer dst) throws IOException {
                int len = Math.min(dst.remaining(), buf.length);
                if (len == 0) {
                    return 0;
                }
                int n = in.read(buf, 0, len);
                if (n > 0) {
                    dst.put(buf, 0, n);
                }
                return n;
            }

            @Override
            public boolean isOpen() {
                return open;
            }

            @Override
            public void close() throws IOException {
                open = false;
                in.close();
            }
        };
    }

    public static WritableByteChannel newChannel(OutputStream out) {
        return new WritableByteChannel() {
            private boolean open = true;

            @Override
            public int write(ByteBuffer src) throws IOException {
                int len = src.remaining();
                byte[] tmp = new byte[len];
                src.get(tmp);
                out.write(tmp);
                return len;
            }

            @Override
            public boolean isOpen() {
                return open;
            }

            @Override
            public void close() throws IOException {
                open = false;
                out.close();
            }
        };
    }

    public static InputStream newInputStream(ReadableByteChannel ch) {
        return new InputStream() {
            @Override
            public int read() throws IOException {
                byte[] b = new byte[1];
                int n = read(b, 0, 1);
                return n <= 0 ? -1 : b[0] & 0xff;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (len == 0) {
                    return 0;
                }
                return ch.read(ByteBuffer.wrap(b, off, len));
            }

            @Override
            public void close() throws IOException {
                ch.close();
            }
        };
    }

    public static OutputStream newOutputStream(WritableByteChannel ch) {
        return new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                ch.write(ByteBuffer.wrap(new byte[] {(byte) b}));
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                ByteBuffer buf = ByteBuffer.wrap(b, off, len);
                while (buf.hasRemaining()) {
                    ch.write(buf);
                }
            }

            @Override
            public void close() throws IOException {
                ch.close();
            }
        };
    }
}
