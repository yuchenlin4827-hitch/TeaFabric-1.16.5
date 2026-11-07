package org.teavm.classlib.java.util.concurrent;

public abstract class TRecursiveAction extends TForkJoinTask<Void> {
    protected abstract void compute();

    @Override
    public final Void getRawResult() {
        return null;
    }

    @Override
    protected final void setRawResult(Void value) {
    }

    @Override
    protected final boolean exec() {
        compute();
        return true;
    }
}
