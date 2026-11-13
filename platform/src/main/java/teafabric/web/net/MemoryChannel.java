package teafabric.web.net;

import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.util.ReferenceCountUtil;
import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import teafabric.web.log.LogSink;

/**
 * In-memory replacement for Netty's local transport, which connects Minecraft's client to its integrated
 * (singleplayer) server. Netty's {@code LocalChannel} needs event-loop threads blocking on task queues;
 * here both ends are {@link EmbeddedChannel}s: a write on one end queues the message (a packet object:
 * local connections are not serialised) for the other, and {@link #pumpAll()} delivers queued messages,
 * once per frame from the render thread, like {@link WebSocketChannel}.
 */
public final class MemoryChannel extends EmbeddedChannel {
    /** Integrated servers listening: address -> the server bootstrap's child handler. */
    private static final Map<LocalAddress, ChannelHandler> SERVERS = new HashMap<>();
    private static final List<MemoryChannel> CHANNELS = new ArrayList<>();
    private static int nextId;
    private static boolean pumping;

    private final SocketAddress local;
    private final SocketAddress remote;
    private final ArrayDeque<Object> inbound = new ArrayDeque<>();
    private MemoryChannel peer;
    private boolean peerClosed;

    private MemoryChannel(SocketAddress local, SocketAddress remote) {
        this.local = local;
        this.remote = remote;
        config().setAllocator(new UnpooledByteBufAllocator(false));
    }

    // ------------------------------------------------------------------ server side

    /** {@code ServerBootstrap.bind()} for a {@link LocalAddress}: registers the integrated server. */
    public static ChannelFuture bind(ChannelHandler childHandler) {
        LocalAddress address = new LocalAddress("teafabric-integrated-" + (++nextId));
        SERVERS.put(address, childHandler);
        LogSink.log(1, "INFO", "LocalNet", "Integrated server listening on " + address, null);
        return new ServerChannel(address).newSucceededFuture();
    }

    /** The bound "server channel": only its address and its close() (server shutdown) matter. */
    private static final class ServerChannel extends EmbeddedChannel {
        private final LocalAddress address;

        ServerChannel(LocalAddress address) {
            this.address = address;
        }

        @Override
        public SocketAddress localAddress() {
            return address;
        }

        @Override
        protected SocketAddress localAddress0() {
            return address;
        }

        @Override
        protected void doClose() throws Exception {
            SERVERS.remove(address);
            super.doClose();
        }
    }

    // ------------------------------------------------------------------ client side

    /** {@code Bootstrap.connect(localAddress)}: a connected pair, both pipelines built by their initialisers. */
    public static ChannelFuture connect(ChannelHandler clientHandler, LocalAddress address) {
        LocalAddress clientAddress = new LocalAddress("teafabric-client-" + (++nextId));
        MemoryChannel client = new MemoryChannel(clientAddress, address);
        ChannelHandler child = SERVERS.get(address);
        if (child == null) {
            ChannelFuture failed = client.newFailedFuture(new java.net.ConnectException("No integrated server at " + address));
            client.close();
            return failed;
        }
        MemoryChannel server = new MemoryChannel(address, clientAddress);
        client.peer = server;
        server.peer = client;
        // Both channels are registered already, so the ChannelInitializers run right here.
        server.pipeline().addLast(child);
        server.pipeline().fireChannelActive();
        if (clientHandler != null) {
            client.pipeline().addLast(clientHandler);
        }
        client.pipeline().fireChannelActive();
        CHANNELS.add(server);
        CHANNELS.add(client);
        return client.newSucceededFuture();
    }

    // ------------------------------------------------------------------ channel

    @Override
    public SocketAddress localAddress() {
        return local;
    }

    @Override
    public SocketAddress remoteAddress() {
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
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        for (;;) {
            Object msg = in.current();
            if (msg == null) {
                break;
            }
            MemoryChannel target = peer;
            if (target != null && target.isOpen()) {
                // in.remove() releases the message once; the peer owns the extra reference.
                target.inbound.add(ReferenceCountUtil.retain(msg));
            }
            in.remove();
        }
    }

    @Override
    protected void doClose() throws Exception {
        super.doClose();
        for (Object msg : inbound) {
            ReferenceCountUtil.release(msg);
        }
        inbound.clear();
        if (peer != null) {
            peer.peerClosed = true; // closed by its own pump: not from inside this pipeline
        }
    }

    private boolean pump() {
        try {
            Object msg;
            while (isOpen() && (msg = inbound.poll()) != null) {
                writeInbound(msg);
            }
            if (isOpen() && peerClosed) {
                close(); // channelInactive: "Connection closed" on that side
            }
            if (isOpen()) {
                runScheduledPendingTasks();
            }
        } catch (Throwable t) {
            LogSink.log(3, "ERROR", "LocalNet", "Integrated server connection failed", t);
            try {
                pipeline().fireExceptionCaught(t);
                close();
            } catch (Throwable ignored) {
                // already closing
            }
        }
        return isOpen() || !inbound.isEmpty();
    }

    /** Delivers queued messages on both ends of every pair. Called by the render thread once per frame. */
    public static void pumpAll() {
        if (pumping || CHANNELS.isEmpty()) {
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
