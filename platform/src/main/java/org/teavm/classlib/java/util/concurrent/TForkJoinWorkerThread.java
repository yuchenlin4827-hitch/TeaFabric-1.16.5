package org.teavm.classlib.java.util.concurrent;

public class TForkJoinWorkerThread extends Thread {
    final TForkJoinPool pool;
    int index;

    protected TForkJoinWorkerThread(TForkJoinPool pool) {
        super("ForkJoinPool-worker");
        this.pool = pool;
        setDaemon(true);
    }

    public TForkJoinPool getPool() {
        return pool;
    }

    public int getPoolIndex() {
        return index;
    }

    protected void onStart() {
    }

    protected void onTermination(Throwable exception) {
    }

    @Override
    public void run() {
        Throwable failure = null;
        try {
            onStart();
            pool.runWorker(this);
        } catch (Throwable t) {
            failure = t;
        } finally {
            try {
                onTermination(failure);
            } finally {
                pool.workerExited(this, failure);
            }
        }
    }
}
