package org.lwjgl.stb;

import org.lwjgl.system.MemoryStack;

public final class STBVorbisInfo implements AutoCloseable {
    int channels;
    int sampleRate;

    public static STBVorbisInfo mallocStack(MemoryStack stack) {
        return new STBVorbisInfo();
    }

    public static STBVorbisInfo mallocStack() {
        return new STBVorbisInfo();
    }

    public static STBVorbisInfo malloc() {
        return new STBVorbisInfo();
    }

    public int channels() {
        return channels;
    }

    public int sample_rate() {
        return sampleRate;
    }

    public void free() {
    }

    @Override
    public void close() {
    }
}
