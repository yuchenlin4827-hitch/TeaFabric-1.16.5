package io.netty.channel;

import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;

/**
 * Replaces Netty's reflective factory (Class.getConstructor().newInstance() needs run-time
 * reflection metadata) with direct construction of the channel types Minecraft uses.
 */
public class ReflectiveChannelFactory<T extends Channel> implements ChannelFactory<T> {
    private final Class<? extends T> clazz;

    public ReflectiveChannelFactory(Class<? extends T> clazz) {
        if (clazz == null) {
            throw new NullPointerException("clazz");
        }
        this.clazz = clazz;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T newChannel() {
        Object channel;
        if (clazz == LocalChannel.class) {
            channel = new LocalChannel();
        } else if (clazz == LocalServerChannel.class) {
            channel = new LocalServerChannel();
        } else if (clazz == io.netty.channel.socket.nio.NioSocketChannel.class) {
            channel = new io.netty.channel.socket.nio.NioSocketChannel();
        } else if (clazz == io.netty.channel.socket.nio.NioServerSocketChannel.class) {
            channel = new io.netty.channel.socket.nio.NioServerSocketChannel();
        } else {
            throw new ChannelException("Unable to create Channel from class " + clazz
                    + " (no reflection in the browser build)");
        }
        return (T) channel;
    }

    @Override
    public String toString() {
        return clazz.getSimpleName() + ".class";
    }
}
