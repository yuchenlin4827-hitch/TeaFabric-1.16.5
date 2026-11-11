package teafabric.web.net;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.SocketChannelConfig;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.Int8Array;
import teafabric.web.log.LogSink;

/**
 * A Netty channel whose transport is a browser WebSocket carrying the raw Minecraft protocol stream
 * (binary frames; a WebSocket-to-TCP proxy sits in front of the real server).
 *
 * <p>Built on {@link EmbeddedChannel}: there is no event loop thread, handlers run on the calling
 * thread. Outbound buffers go straight to {@code WebSocket.send}; inbound frames are queued by boot.js
 * and fed into the pipeline by {@link #pumpAll()}, which the render thread calls once per frame (from
 * {@code glfwPollEvents}), so packets are handled on Minecraft's main thread without a hand-over.
 *
 * <p>It presents itself as a {@link SocketChannel}, which is what Minecraft's channel initialiser and
 * mods hooking it (protocol translators such as ViaFabric) expect of a client connection.
 */
public final class WebSocketChannel extends EmbeddedChannel implements SocketChannel {
    public static final int CONNECTING = 0;
    public static final int OPEN = 1;
    public static final int CLOSED = 3;

    private static final List<WebSocketChannel> CHANNELS = new ArrayList<>();

    private final int socket;
    private final String url;
    private final InetSocketAddress remote;
    private final InetSocketAddress local = InetSocketAddress.createUnresolved("localhost", 0);
    private WebSocketChannelConfig socketConfig; // created on first use: the super constructor already needs it
    private boolean released;

    public WebSocketChannel(int socket, String url, String remoteHost, int remotePort) {
        this.socket = socket;
        this.url = url;
        this.remote = InetSocketAddress.createUnresolved(remoteHost, remotePort);
        // Heap buffers only: no pooling arenas, no direct memory, no Unsafe.
        config().setAllocator(new UnpooledByteBufAllocator(false));
    }

    // ------------------------------------------------------------------ SocketChannel

    @Override
    public SocketChannelConfig config() {
        if (socketConfig == null) {
            socketConfig = new WebSocketChannelConfig(this);
        }
        return socketConfig;
    }

    @Override
    public ServerSocketChannel parent() {
        return null;
    }

    @Override
    public InetSocketAddress localAddress() {
        return local;
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return remote;
    }

    @Override
    protected SocketAddress localAddress0() {
        return local;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return remote;
    }

    @Override
    public boolean isInputShutdown() {
        return !isOpen();
    }

    @Override
    public boolean isOutputShutdown() {
        return !isOpen();
    }

    @Override
    public boolean isShutdown() {
        return !isOpen();
    }

    @Override
    public ChannelFuture shutdownInput() {
        return close();
    }

    @Override
    public ChannelFuture shutdownInput(ChannelPromise promise) {
        return close(promise);
    }

    @Override
    public ChannelFuture shutdownOutput() {
        return close();
    }

    @Override
    public ChannelFuture shutdownOutput(ChannelPromise promise) {
        return close(promise);
    }

    @Override
    public ChannelFuture shutdown() {
        return close();
    }

    @Override
    public ChannelFuture shutdown(ChannelPromise promise) {
        return close(promise);
    }

    // ------------------------------------------------------------------ browser side (boot.js)

    @JSBody(params = "url", script = "return teafabric.ws.open(url);")
    public static native int open(String url);

    @JSBody(params = "id", script = "return teafabric.ws.state(id);")
    public static native int state(int id);

    @JSBody(params = "id", script = "return teafabric.ws.error(id);")
    public static native String error(int id);

    @JSBody(params = "id", script = "return teafabric.ws.pending(id);")
    private static native int pending(int id);

    @JSBody(params = "id", script = "return teafabric.ws.take(id);")
    private static native Int8Array take(int id);

    @JSBody(params = {"id", "data"}, script = "teafabric.ws.send(id, data);")
    private static native void send(int id, Int8Array data);

    @JSBody(params = "id", script = "teafabric.ws.close(id);")
    public static native void closeSocket(int id);

    // ------------------------------------------------------------------ channel

    /** Starts delivering inbound data to this channel. */
    public void register() {
        CHANNELS.add(this);
    }

    public String url() {
        return url;
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        for (;;) {
            Object msg = in.current();
            if (msg == null) {
                break;
            }
            if (msg instanceof ByteBuf) {
                ByteBuf buf = (ByteBuf) msg;
                int length = buf.readableBytes();
                if (length > 0) {
                    byte[] bytes = new byte[length];
                    buf.getBytes(buf.readerIndex(), bytes);
                    send(socket, Int8Array.copyFromJavaArray(bytes));
                }
            }
            in.remove();
        }
    }

    @Override
    protected void doClose() throws Exception {
        super.doClose();
        release();
    }

    private void release() {
        if (!released) {
            released = true;
            closeSocket(socket);
        }
    }

    /** Feeds queued frames into the pipeline. Returns false when the channel is finished. */
    private boolean pump() {
        try {
            while (isOpen() && pending(socket) > 0) {
                byte[] data = take(socket).copyToJavaArray();
                if (data.length > 0) {
                    writeInbound(Unpooled.wrappedBuffer(data));
                }
            }
            if (isOpen() && state(socket) == CLOSED) {
                // Remote end closed: Minecraft shows "disconnect.endOfStream" from channelInactive.
                close();
            }
            if (isOpen()) {
                runScheduledPendingTasks(); // read timeout and other timers of the pipeline
            }
        } catch (Throwable t) {
            LogSink.log(3, "ERROR", "WebSocket", "Connection to " + url + " failed", t);
            try {
                pipeline().fireExceptionCaught(t);
                close();
            } catch (Throwable ignored) {
                // already closing
            }
        }
        if (!isOpen()) {
            release();
            return false;
        }
        return true;
    }

    private static boolean pumping;

    /**
     * Called by the render thread once per frame. Not re-entrant: a packet handler may render a frame
     * itself (joining a world does) and poll again while a packet is still being decoded.
     */
    public static void pumpAll() {
        if (pumping) {
            return;
        }
        pumping = true;
        try {
            for (int i = 0; i < CHANNELS.size(); i++) {
                if (!CHANNELS.get(i).pump()) {
                    CHANNELS.remove(i--);
                }
            }
        } finally {
            pumping = false;
        }
    }
}
