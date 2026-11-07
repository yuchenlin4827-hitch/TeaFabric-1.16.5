package io.netty.channel.epoll;

import io.netty.channel.DefaultEventLoopGroup;
import java.util.concurrent.ThreadFactory;

/** Never used in the browser ({@link Epoll#isAvailable()} is false); exists for linkage only. */
public class EpollEventLoopGroup extends DefaultEventLoopGroup {
    public EpollEventLoopGroup() {
        super(1);
    }

    public EpollEventLoopGroup(int nThreads) {
        super(1);
    }

    public EpollEventLoopGroup(int nThreads, ThreadFactory threadFactory) {
        super(1, threadFactory);
    }
}
