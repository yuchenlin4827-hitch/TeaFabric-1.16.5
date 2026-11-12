package teafabric.web.compat;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import net.minecraft.class_2539;
import net.minecraft.class_2889;
import net.minecraft.class_639;
import net.minecraft.class_642;
import net.minecraft.class_644;
import teafabric.web.Boot;
import teafabric.web.log.LogSink;
import teafabric.web.net.WebSocketChannel;

/**
 * Multiplayer over WebSockets. A page cannot open TCP sockets, so a server address is a WebSocket URL
 * of a WebSocket-to-TCP proxy ({@code ws://host:port/...} or {@code wss://...}) that relays the raw
 * Minecraft protocol; see {@code proxy/ws-proxy.js}.
 *
 * <ul>
 *   <li>{@code wss://proxy.example/} – the proxy decides which server it relays to;</li>
 *   <li>{@code wss://proxy.example/?target=mc.example.com:25565} – asks the proxy for that server;</li>
 *   <li>a plain {@code host:port} works when the page was opened with {@code ?proxy=wss://proxy.example/}
 *       (the address is sent to the proxy as {@code target}).</li>
 * </ul>
 */
public final class NetCompat {
    private static final int CONNECT_TIMEOUT_MILLIS = 20000;

    private NetCompat() {
    }

    /** Shown by Minecraft's "Failed to connect to the server" screen via {@code toString()}. */
    static final class ConnectFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ConnectFailure(String message) {
            super(message);
        }

        @Override
        public String toString() {
            return getMessage();
        }
    }

    public static boolean isWebSocketUrl(String address) {
        if (address == null) {
            return false;
        }
        String a = address.trim().toLowerCase();
        return a.startsWith("ws://") || a.startsWith("wss://");
    }

    /** host[:port] part of a WebSocket URL. */
    private static String authority(String url) {
        String rest = url.substring(url.indexOf("://") + 3);
        int end = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        return rest.substring(0, end);
    }

    private static String queryParameter(String url, String name) {
        int q = url.indexOf('?');
        if (q < 0) {
            return null;
        }
        String query = url.substring(q + 1);
        int hash = query.indexOf('#');
        if (hash >= 0) {
            query = query.substring(0, hash);
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return pair.substring(eq + 1).replace("%3A", ":").replace("%3a", ":");
            }
        }
        return null;
    }

    private static int parsePort(String text, int fallback) {
        try {
            int port = Integer.parseInt(text.trim());
            return port > 0 && port < 65536 ? port : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** The Minecraft server a WebSocket URL stands for: its {@code target} parameter, else the URL host. */
    private static String[] minecraftTarget(String url) {
        String target = queryParameter(url, "target");
        String hostPort = target != null && !target.isEmpty() ? target : authority(url);
        String host = hostPort;
        int port = 25565;
        int colon = hostPort.lastIndexOf(':');
        if (colon > 0 && hostPort.indexOf(']') < colon) {
            host = hostPort.substring(0, colon);
            port = target != null ? parsePort(hostPort.substring(colon + 1), 25565) : 25565;
        }
        return new String[] {host, Integer.toString(port)};
    }

    // ------------------------------------------------------------------ redirect targets

    /**
     * {@code ServerAddress.parse}: vanilla splits on ':' and would mangle a URL. A URL is passed through
     * whole (as a bracketed "host"), with a port other than 25565 so that no SRV lookup is attempted.
     */
    public static class_639 method_2950(String address) {
        if (isWebSocketUrl(address)) {
            String url = address.trim();
            if (url.indexOf(']') < 0) {
                return class_639.method_2950("[" + url + "]:" + (url.toLowerCase().startsWith("wss://") ? 443 : 80));
            }
        }
        return class_639.method_2950(address);
    }

    /** {@code IDN.toASCII}: host names are used as typed (URLs must survive unchanged). */
    public static String toASCII(String host) {
        return host;
    }

    /** {@code new HandshakeC2SPacket(address, port, state)}: tell the server its own name, not the proxy URL. */
    public static class_2889 constructclass_2889(String address, int port, class_2539 state) {
        if (isWebSocketUrl(address)) {
            String[] target = minecraftTarget(address.trim());
            return new class_2889(target[0], Integer.parseInt(target[1]), state);
        }
        return new class_2889(address, port, state);
    }

    /**
     * {@code ResourceLeakDetector.addExclusions(type, methodNames)}: only validates the names with
     * {@code getDeclaredMethods()} (no reflection data here, so it always failed in the static
     * initialiser of Netty's buffer allocators) and tunes leak reports, which are disabled.
     */
    public static void addExclusions(Class<?> type, String... methodNames) {
    }

    /**
     * {@code ObjectCleaner.register(object, cleanupTask)}: would start a daemon thread polling a
     * ReferenceQueue to clean thread-locals of dead threads. Not needed with a handful of green threads.
     */
    public static void register(Object object, Runnable cleanupTask) {
    }

    /** Legacy (pre-1.7) server list ping over a raw NIO socket: not possible here. */
    public static void method_3001(class_644 pinger, class_642 server) {
    }

    private static String resolveUrl(String host, int port) {
        if (isWebSocketUrl(host)) {
            return host.trim();
        }
        String proxy = Boot.config("proxy");
        if (isWebSocketUrl(proxy)) {
            proxy = proxy.trim();
            return proxy + (proxy.indexOf('?') >= 0 ? '&' : '?') + "target=" + host + ":" + port;
        }
        throw new ConnectFailure("A web page cannot open TCP connections. Enter the address of a WebSocket proxy"
                + " instead, e.g. wss://proxy.example.com/ (see README, \"Multiplayer\").");
    }

    /** {@code Bootstrap.connect(InetAddress, int)}: what {@code ClientConnection.connect} calls. */
    public static ChannelFuture connect(Bootstrap bootstrap, InetAddress address, int port) {
        return connect(bootstrap, address.getHostName(), port);
    }

    /** {@code Bootstrap.connect(SocketAddress)}. */
    public static ChannelFuture connect(Bootstrap bootstrap, SocketAddress address) {
        InetSocketAddress a = (InetSocketAddress) address;
        return connect(bootstrap, a.getHostString(), a.getPort());
    }

    /**
     * {@code Bootstrap.connect(host, port)}: opens the WebSocket, waits for it on the calling thread (the
     * "Server Connector" / pinger thread) and then lets the bootstrap's own channel initialiser build the
     * pipeline, exactly as it would for a TCP socket, so mods that hook it keep working.
     */
    public static ChannelFuture connect(Bootstrap bootstrap, String host, int port) {
        String url;
        try {
            url = resolveUrl(host, port);
        } catch (ConnectFailure failure) {
            return failed(host, port, failure);
        }
        LogSink.log(1, "INFO", "WebSocket", "Connecting to " + url, null);
        int socket = WebSocketChannel.open(url);
        long deadline = System.currentTimeMillis() + CONNECT_TIMEOUT_MILLIS;
        while (WebSocketChannel.state(socket) == WebSocketChannel.CONNECTING) {
            if (System.currentTimeMillis() > deadline) {
                WebSocketChannel.closeSocket(socket);
                return failed(host, port, new ConnectFailure("Connection timed out: " + url));
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                WebSocketChannel.closeSocket(socket);
                return failed(host, port, new ConnectFailure("Interrupted while connecting to " + url));
            }
        }
        if (WebSocketChannel.state(socket) != WebSocketChannel.OPEN) {
            String error = WebSocketChannel.error(socket);
            WebSocketChannel.closeSocket(socket);
            return failed(host, port,
                    new ConnectFailure("Cannot connect to " + url + (error.isEmpty() ? "" : " (" + error + ")")));
        }

        String[] target = isWebSocketUrl(host) ? minecraftTarget(host.trim()) : new String[] {host, "" + port};
        WebSocketChannel channel = new WebSocketChannel(socket, url, target[0], Integer.parseInt(target[1]));
        ChannelHandler initializer = bootstrap.config().handler();
        if (initializer != null) {
            // The channel is registered already, so a ChannelInitializer runs right here.
            channel.pipeline().addLast(initializer);
        }
        // The channel was active before the handlers existed: announce it to them now.
        channel.pipeline().fireChannelActive();
        channel.register();
        return channel.newSucceededFuture();
    }

    private static ChannelFuture failed(String host, int port, ConnectFailure failure) {
        WebSocketChannel channel = new WebSocketChannel(-1, "", String.valueOf(host), port);
        ChannelFuture future = channel.newFailedFuture(failure);
        channel.close();
        return future;
    }
}
