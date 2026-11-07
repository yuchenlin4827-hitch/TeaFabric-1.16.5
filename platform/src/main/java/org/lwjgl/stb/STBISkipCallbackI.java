package org.lwjgl.stb;

@FunctionalInterface
public interface STBISkipCallbackI {
    void invoke(long user, int n);
}
