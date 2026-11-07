package org.teavm.classlib.java.util.concurrent.atomic;

import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

/** Atomic array for TeaVM green threads (a fiber switch never happens inside these methods). */
public class TAtomicReferenceArray<E> implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final Object[] array;

    public TAtomicReferenceArray(int length) {
        array = new Object[length];
    }

    public TAtomicReferenceArray(E[] array) {
        this.array = java.util.Arrays.copyOf(array, array.length, Object[].class);
    }

    public final int length() {
        return array.length;
    }

    @SuppressWarnings("unchecked")
    public final E get(int i) {
        return (E) array[i];
    }

    public final void set(int i, E newValue) {
        array[i] = newValue;
    }

    public final void lazySet(int i, E newValue) {
        array[i] = newValue;
    }

    @SuppressWarnings("unchecked")
    public final E getAndSet(int i, E newValue) {
        E old = (E) array[i];
        array[i] = newValue;
        return old;
    }

    public final boolean compareAndSet(int i, E expect, E update) {
        if (array[i] == expect) {
            array[i] = update;
            return true;
        }
        return false;
    }

    public final boolean weakCompareAndSet(int i, E expect, E update) {
        return compareAndSet(i, expect, update);
    }

    public final E getPlain(int i) {
        return get(i);
    }

    public final void setPlain(int i, E v) {
        set(i, v);
    }

    public final E getAcquire(int i) {
        return get(i);
    }

    public final void setRelease(int i, E v) {
        set(i, v);
    }

    public final E getOpaque(int i) {
        return get(i);
    }

    public final void setOpaque(int i, E v) {
        set(i, v);
    }

    public final E getAndUpdate(int i, UnaryOperator<E> f) {
        E prev = get(i);
        array[i] = f.apply(prev);
        return prev;
    }

    public final E updateAndGet(int i, UnaryOperator<E> f) {
        E next = f.apply(get(i));
        array[i] = next;
        return next;
    }

    public final E getAndAccumulate(int i, E x, BinaryOperator<E> f) {
        E prev = get(i);
        array[i] = f.apply(prev, x);
        return prev;
    }

    public final E accumulateAndGet(int i, E x, BinaryOperator<E> f) {
        E next = f.apply(get(i), x);
        array[i] = next;
        return next;
    }

    @Override
    public String toString() {
        return java.util.Arrays.toString(array);
    }
}
