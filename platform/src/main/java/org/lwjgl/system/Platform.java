package org.lwjgl.system;

public enum Platform {
    LINUX("Linux"), MACOSX("macOS"), WINDOWS("Windows");

    private final String name;

    Platform(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static Platform get() {
        return LINUX;
    }
}
