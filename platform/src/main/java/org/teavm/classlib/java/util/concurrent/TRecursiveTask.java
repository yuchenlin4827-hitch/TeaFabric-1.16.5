package org.teavm.classlib.java.util.concurrent;

public abstract class TRecursiveTask<V> extends TForkJoinTask<V> {
    private V result;

    protected abstract V compute();

    @Override
    public final V getRawResult() {
        return result;
    }

    @Override
    protected final void setRawResult(V value) {
        result = value;
    }

    @Override
    protected final boolean exec() {
        result = compute();
        return true;
    }
}
