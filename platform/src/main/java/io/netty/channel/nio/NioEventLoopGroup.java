package io.netty.channel.nio;

import io.netty.channel.DefaultEventLoopGroup;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

/**
 * Browser replacement: there are no NIO selectors, so the "NIO" group is a plain event loop group
 * (Minecraft only creates it for remote connections, which use WebSocket channels in the browser).
 */
public class NioEventLoopGroup extends DefaultEventLoopGroup {
    public NioEventLoopGroup() {
        super(1);
    }

    public NioEventLoopGroup(int nThreads) {
        super(Math.max(1, Math.min(nThreads, 2)));
    }

    public NioEventLoopGroup(int nThreads, ThreadFactory threadFactory) {
        super(Math.max(1, Math.min(nThreads, 2)), threadFactory);
    }

    public NioEventLoopGroup(int nThreads, Executor executor) {
        super(Math.max(1, Math.min(nThreads, 2)), executor);
    }
}
