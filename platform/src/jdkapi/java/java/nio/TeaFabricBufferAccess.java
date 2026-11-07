package java.nio;

/**
 * Compile-time declaration only (never on the TeaVM class path): lets platform code call the
 * class-library bridge {@code org.teavm.classlib.java.nio.TTeaFabricBufferAccess} through its
 * JDK-style name, which is how TeaVM resolves class-library substitutes. Referencing the T-name
 * directly would load a second, unrenamed copy of TeaVM's NIO classes.
 */
public final class TeaFabricBufferAccess {
    private TeaFabricBufferAccess() {
    }

    public static int address(Buffer buffer) {
        throw new UnsupportedOperationException();
    }

    public static int baseAddress(Buffer buffer) {
        throw new UnsupportedOperationException();
    }

    public static ByteBuffer wrap(int address, int capacity) {
        throw new UnsupportedOperationException();
    }
}
