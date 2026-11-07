package org.teavm.classlib.java.nio.channels;

public class TFileChannel$MapMode {
    public static final TFileChannel$MapMode READ_ONLY = new TFileChannel$MapMode("READ_ONLY");
    public static final TFileChannel$MapMode READ_WRITE = new TFileChannel$MapMode("READ_WRITE");
    public static final TFileChannel$MapMode PRIVATE = new TFileChannel$MapMode("PRIVATE");

    private final String name;

    private TFileChannel$MapMode(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
