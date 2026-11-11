package teafabric.web.net;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.SocketChannelConfig;

/** Socket options of a {@link WebSocketChannel}: accepted and remembered, a WebSocket has none of them. */
final class WebSocketChannelConfig extends DefaultChannelConfig implements SocketChannelConfig {
    private boolean tcpNoDelay = true;
    private boolean keepAlive;
    private boolean reuseAddress;
    private boolean allowHalfClosure;
    private int soLinger = -1;
    private int sendBufferSize = 65536;
    private int receiveBufferSize = 65536;
    private int trafficClass;

    WebSocketChannelConfig(Channel channel) {
        super(channel);
    }

    @Override public boolean isTcpNoDelay() { return tcpNoDelay; }
    @Override public SocketChannelConfig setTcpNoDelay(boolean value) { tcpNoDelay = value; return this; }
    @Override public int getSoLinger() { return soLinger; }
    @Override public SocketChannelConfig setSoLinger(int value) { soLinger = value; return this; }
    @Override public int getSendBufferSize() { return sendBufferSize; }
    @Override public SocketChannelConfig setSendBufferSize(int value) { sendBufferSize = value; return this; }
    @Override public int getReceiveBufferSize() { return receiveBufferSize; }
    @Override public SocketChannelConfig setReceiveBufferSize(int value) { receiveBufferSize = value; return this; }
    @Override public boolean isKeepAlive() { return keepAlive; }
    @Override public SocketChannelConfig setKeepAlive(boolean value) { keepAlive = value; return this; }
    @Override public int getTrafficClass() { return trafficClass; }
    @Override public SocketChannelConfig setTrafficClass(int value) { trafficClass = value; return this; }
    @Override public boolean isReuseAddress() { return reuseAddress; }
    @Override public SocketChannelConfig setReuseAddress(boolean value) { reuseAddress = value; return this; }
    @Override public SocketChannelConfig setPerformancePreferences(int a, int b, int c) { return this; }
    @Override public boolean isAllowHalfClosure() { return allowHalfClosure; }
    @Override public SocketChannelConfig setAllowHalfClosure(boolean value) { allowHalfClosure = value; return this; }

    @Override public SocketChannelConfig setConnectTimeoutMillis(int v) { super.setConnectTimeoutMillis(v); return this; }
    @Override @Deprecated public SocketChannelConfig setMaxMessagesPerRead(int v) { super.setMaxMessagesPerRead(v); return this; }
    @Override public SocketChannelConfig setWriteSpinCount(int v) { super.setWriteSpinCount(v); return this; }
    @Override public SocketChannelConfig setAllocator(ByteBufAllocator v) { super.setAllocator(v); return this; }
    @Override public SocketChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator v) { super.setRecvByteBufAllocator(v); return this; }
    @Override public SocketChannelConfig setAutoRead(boolean v) { super.setAutoRead(v); return this; }
    @Override public SocketChannelConfig setAutoClose(boolean v) { super.setAutoClose(v); return this; }
    @Override public SocketChannelConfig setMessageSizeEstimator(MessageSizeEstimator v) { super.setMessageSizeEstimator(v); return this; }
    @Override public SocketChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark v) { super.setWriteBufferWaterMark(v); return this; }
}
