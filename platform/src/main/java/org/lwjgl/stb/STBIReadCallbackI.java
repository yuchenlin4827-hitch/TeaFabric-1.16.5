package org.lwjgl.stb;

@FunctionalInterface
public interface STBIReadCallbackI {
    int invoke(long user, long data, int size);
}
