package sun.misc;

import java.lang.reflect.Field;

/**
 * Placeholder so libraries that probe for {@code sun.misc.Unsafe} (Guava, Netty) compile; the probe
 * fails at run time and they take their pure-Java fallbacks.
 */
public final class Unsafe {
    private Unsafe() {
    }

    public static Unsafe getUnsafe() {
        throw new SecurityException("Unsafe is not available in the browser");
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("sun.misc.Unsafe");
    }

    public long objectFieldOffset(Field f) {
        throw unsupported();
    }

    public int arrayBaseOffset(Class<?> c) {
        throw unsupported();
    }

    public int arrayIndexScale(Class<?> c) {
        throw unsupported();
    }

    public boolean compareAndSwapInt(Object o, long offset, int expected, int x) {
        throw unsupported();
    }

    public boolean compareAndSwapLong(Object o, long offset, long expected, long x) {
        throw unsupported();
    }

    public boolean compareAndSwapObject(Object o, long offset, Object expected, Object x) {
        throw unsupported();
    }

    public long getLong(Object o, long offset) {
        throw unsupported();
    }

    public int getInt(Object o, long offset) {
        throw unsupported();
    }

    public byte getByte(Object o, long offset) {
        throw unsupported();
    }

    public Object getObject(Object o, long offset) {
        throw unsupported();
    }

    public void putLong(Object o, long offset, long x) {
        throw unsupported();
    }

    public void putInt(Object o, long offset, int x) {
        throw unsupported();
    }

    public void putObject(Object o, long offset, Object x) {
        throw unsupported();
    }

    public long getLongVolatile(Object o, long offset) {
        throw unsupported();
    }

    public Object getObjectVolatile(Object o, long offset) {
        throw unsupported();
    }

    public void putObjectVolatile(Object o, long offset, Object x) {
        throw unsupported();
    }

    public void putOrderedObject(Object o, long offset, Object x) {
        throw unsupported();
    }

    public void putOrderedLong(Object o, long offset, long x) {
        throw unsupported();
    }

    public void putOrderedInt(Object o, long offset, int x) {
        throw unsupported();
    }

    public int addressSize() {
        throw unsupported();
    }

    public int pageSize() {
        return 4096;
    }
}
