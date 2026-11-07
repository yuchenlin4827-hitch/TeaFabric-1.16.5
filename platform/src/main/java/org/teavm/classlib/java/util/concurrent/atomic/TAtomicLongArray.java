package org.teavm.classlib.java.util.concurrent.atomic;

import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

/** Atomic array for TeaVM green threads (a fiber switch never happens inside these methods). */
public class TAtomicLongArray implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final long[] array;

    public TAtomicLongArray(int length) {
        array = new long[length];
    }

    public TAtomicLongArray(long[] array) {
        this.array = java.util.Arrays.copyOf(array, array.length);
    }

    public final int length() {
        return array.length;
    }

    @SuppressWarnings("unchecked")
    public final long get(int i) {
        return array[i];
    }

    public final void set(int i, long newValue) {
        array[i] = newValue;
    }

    public final void lazySet(int i, long newValue) {
        array[i] = newValue;
    }

    @SuppressWarnings("unchecked")
    public final long getAndSet(int i, long newValue) {
        long old = array[i];
        array[i] = newValue;
        return old;
    }

    public final boolean compareAndSet(int i, long expect, long update) {
        if (array[i] == expect) {
            array[i] = update;
            return true;
        }
        return false;
    }

    public final boolean weakCompareAndSet(int i, long expect, long update) {
        return compareAndSet(i, expect, update);
    }

    public final long getPlain(int i) {
        return get(i);
    }

    public final void setPlain(int i, long v) {
        set(i, v);
    }

    public final long getAcquire(int i) {
        return get(i);
    }

    public final void setRelease(int i, long v) {
        set(i, v);
    }

    public final long getOpaque(int i) {
        return get(i);
    }

    public final void setOpaque(int i, long v) {
        set(i, v);
    }

    public final long getAndIncrement(int i) {
        return array[i]++;
    }

    public final long getAndDecrement(int i) {
        return array[i]--;
    }

    public final long getAndAdd(int i, long delta) {
        long old = array[i];
        array[i] += delta;
        return old;
    }

    public final long incrementAndGet(int i) {
        return ++array[i];
    }

    public final long decrementAndGet(int i) {
        return --array[i];
    }

    public final long addAndGet(int i, long delta) {
        array[i] += delta;
        return array[i];
    }

    public final long getAndUpdate(int i, java.util.function.LongUnaryOperator f) {
        long prev = array[i];
        array[i] = f.applyAsLong(prev);
        return prev;
    }

    public final long updateAndGet(int i, java.util.function.LongUnaryOperator f) {
        array[i] = f.applyAsLong(array[i]);
        return array[i];
    }

    public final long getAndAccumulate(int i, long x, java.util.function.LongBinaryOperator f) {
        long prev = array[i];
        array[i] = f.applyAsLong(prev, x);
        return prev;
    }

    public final long accumulateAndGet(int i, long x, java.util.function.LongBinaryOperator f) {
        array[i] = f.applyAsLong(array[i], x);
        return array[i];
    }

    @Override
    public String toString() {
        return java.util.Arrays.toString(array);
    }
}
