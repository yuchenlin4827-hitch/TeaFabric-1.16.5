package org.lwjgl;

public final class Version {
    public static final int VERSION_MAJOR = 3;
    public static final int VERSION_MINOR = 2;
    public static final int VERSION_REVISION = 2;

    private Version() {
    }

    public static String getVersion() {
        return "3.2.2 TeaFabric/WebGL2";
    }
}
