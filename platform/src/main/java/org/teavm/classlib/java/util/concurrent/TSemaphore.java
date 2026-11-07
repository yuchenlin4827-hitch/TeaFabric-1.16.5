package org.teavm.classlib.java.util.concurrent;

import java.util.concurrent.TimeUnit;

public class TSemaphore implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private int permits;

    public TSemaphore(int permits) {
        this.permits = permits;
    }

    public TSemaphore(int permits, boolean fair) {
        this.permits = permits;
    }

    public void acquire() throws InterruptedException {
        acquire(1);
    }

    public synchronized void acquire(int n) throws InterruptedException {
        while (permits < n) {
            wait();
        }
        permits -= n;
    }

    public void acquireUninterruptibly() {
        acquireUninterruptibly(1);
    }

    public synchronized void acquireUninterruptibly(int n) {
        boolean interrupted = false;
        while (permits < n) {
            try {
                wait();
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        permits -= n;
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    public synchronized boolean tryAcquire(int n) {
        if (permits >= n) {
            permits -= n;
            return true;
        }
        return false;
    }

    public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
        return tryAcquire(1, timeout, unit);
    }

    public synchronized boolean tryAcquire(int n, long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (permits < n) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) {
                return false;
            }
            wait(left);
        }
        permits -= n;
        return true;
    }

    public void release() {
        release(1);
    }

    public synchronized void release(int n) {
        permits += n;
        notifyAll();
    }

    public synchronized int availablePermits() {
        return permits;
    }

    public synchronized int drainPermits() {
        int p = permits;
        permits = 0;
        return p;
    }
}
