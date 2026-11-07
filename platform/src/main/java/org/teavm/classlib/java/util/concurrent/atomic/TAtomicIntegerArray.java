package org.teavm.classlib.java.util.concurrent.atomic;

import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

/** Atomic array for TeaVM green threads (a fiber switch never happens inside these methods). */
public class TAtomicIntegerArray implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final int[] array;

    public TAtomicIntegerArray(int length) {
        array = new int[length];
    }

    public TAtomicIntegerArray(int[] array) {
        this.array = java.util.Arrays.copyOf(array, array.length);
    }

    public final int length() {
        return array.length;
    }

    @SuppressWarnings("unchecked")
    public final int get(int i) {
        return array[i];
    }

    public final void set(int i, int newValue) {
        array[i] = newValue;
    }

    public final void lazySet(int i, int newValue) {
        array[i] = newValue;
    }

    @SuppressWarnings("unchecked")
    public final int getAndSet(int i, int newValue) {
        int old = array[i];
        array[i] = newValue;
        return old;
    }

    public final boolean compareAndSet(int i, int expect, int update) {
        if (array[i] == expect) {
            array[i] = update;
            return true;
        }
        return false;
    }

    public final boolean weakCompareAndSet(int i, int expect, int update) {
        return compareAndSet(i, expect, update);
    }

    public final int getPlain(int i) {
        return get(i);
    }

    public final void setPlain(int i, int v) {
        set(i, v);
    }

    public final int getAcquire(int i) {
        return get(i);
    }

    public final void setRelease(int i, int v) {
        set(i, v);
    }

    public final int getOpaque(int i) {
        return get(i);
    }

    public final void setOpaque(int i, int v) {
        set(i, v);
    }

    public final int getAndIncrement(int i) {
        return array[i]++;
    }

    public final int getAndDecrement(int i) {
        return array[i]--;
    }

    public final int getAndAdd(int i, int delta) {
        int old = array[i];
        array[i] += delta;
        return old;
    }

    public final int incrementAndGet(int i) {
        return ++array[i];
    }

    public final int decrementAndGet(int i) {
        return --array[i];
    }

    public final int addAndGet(int i, int delta) {
        array[i] += delta;
        return array[i];
    }

    public final int getAndUpdate(int i, java.util.function.IntUnaryOperator f) {
        int prev = array[i];
        array[i] = f.applyAsInt(prev);
        return prev;
    }

    public final int updateAndGet(int i, java.util.function.IntUnaryOperator f) {
        array[i] = f.applyAsInt(array[i]);
        return array[i];
    }

    public final int getAndAccumulate(int i, int x, java.util.function.IntBinaryOperator f) {
        int prev = array[i];
        array[i] = f.applyAsInt(prev, x);
        return prev;
    }

    public final int accumulateAndGet(int i, int x, java.util.function.IntBinaryOperator f) {
        array[i] = f.applyAsInt(array[i], x);
        return array[i];
    }

    @Override
    public String toString() {
        return java.util.Arrays.toString(array);
    }
}
