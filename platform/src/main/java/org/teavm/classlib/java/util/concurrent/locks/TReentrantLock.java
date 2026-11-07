package org.teavm.classlib.java.util.concurrent.locks;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/** Reentrant lock built on the object monitor (TeaVM turns monitor waits into fiber suspension). */
public class TReentrantLock implements TLock, java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private transient Thread owner;
    private int holds;

    public TReentrantLock() {
    }

    public TReentrantLock(boolean fair) {
    }

    @Override
    public void lock() {
        Thread me = Thread.currentThread();
        synchronized (this) {
            boolean interrupted = false;
            while (owner != null && owner != me) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            owner = me;
            holds++;
            if (interrupted) {
                me.interrupt();
            }
        }
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        Thread me = Thread.currentThread();
        synchronized (this) {
            while (owner != null && owner != me) {
                wait();
            }
            owner = me;
            holds++;
        }
    }

    @Override
    public synchronized boolean tryLock() {
        Thread me = Thread.currentThread();
        if (owner == null || owner == me) {
            owner = me;
            holds++;
            return true;
        }
        return false;
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        Thread me = Thread.currentThread();
        long deadline = System.currentTimeMillis() + unit.toMillis(time);
        synchronized (this) {
            while (owner != null && owner != me) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) {
                    return false;
                }
                wait(left);
            }
            owner = me;
            holds++;
            return true;
        }
    }

    @Override
    public synchronized void unlock() {
        if (owner != Thread.currentThread()) {
            throw new IllegalMonitorStateException();
        }
        if (--holds == 0) {
            owner = null;
            notifyAll();
        }
    }

    /** Releases all holds, returning the count so a condition wait can restore it. */
    synchronized int fullyRelease() {
        if (owner != Thread.currentThread()) {
            throw new IllegalMonitorStateException();
        }
        int saved = holds;
        holds = 0;
        owner = null;
        notifyAll();
        return saved;
    }

    synchronized void reacquire(int saved) {
        Thread me = Thread.currentThread();
        boolean interrupted = false;
        while (owner != null && owner != me) {
            try {
                wait();
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        owner = me;
        holds = saved;
        if (interrupted) {
            me.interrupt();
        }
    }

    public synchronized boolean isHeldByCurrentThread() {
        return owner == Thread.currentThread();
    }

    public synchronized boolean isLocked() {
        return owner != null;
    }

    public synchronized int getHoldCount() {
        return owner == Thread.currentThread() ? holds : 0;
    }

    public final boolean isFair() {
        return false;
    }

    public final boolean hasQueuedThreads() {
        return false;
    }

    public final int getQueueLength() {
        return 0;
    }

    @Override
    public TCondition newCondition() {
        return new ConditionObject(this);
    }

    /**
     * Condition with a generation counter so signals are not lost between releasing the lock and
     * starting to wait (all of which happens under the condition's monitor).
     */
    static final class ConditionObject implements TCondition {
        private final TReentrantLock lock;
        private long generation;
        private int waiters;

        ConditionObject(TReentrantLock lock) {
            this.lock = lock;
        }

        private boolean doWait(long millis) throws InterruptedException {
            int saved;
            boolean signalled;
            synchronized (this) {
                long gen = generation;
                waiters++;
                saved = lock.fullyRelease();
                try {
                    long deadline = millis > 0 ? System.currentTimeMillis() + millis : 0;
                    while (generation == gen) {
                        if (millis > 0) {
                            long left = deadline - System.currentTimeMillis();
                            if (left <= 0) {
                                break;
                            }
                            wait(left);
                        } else if (millis == 0) {
                            wait();
                        } else {
                            break;
                        }
                    }
                    signalled = generation != gen;
                } finally {
                    waiters--;
                }
            }
            lock.reacquire(saved);
            return signalled;
        }

        @Override
        public void await() throws InterruptedException {
            doWait(0);
        }

        @Override
        public void awaitUninterruptibly() {
            boolean interrupted = false;
            while (true) {
                try {
                    doWait(0);
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public long awaitNanos(long nanosTimeout) throws InterruptedException {
            long start = System.nanoTime();
            long millis = Math.max(1, nanosTimeout / 1000000);
            doWait(nanosTimeout <= 0 ? -1 : millis);
            return nanosTimeout - (System.nanoTime() - start);
        }

        @Override
        public boolean await(long time, TimeUnit unit) throws InterruptedException {
            long millis = unit.toMillis(time);
            return doWait(time <= 0 ? -1 : Math.max(1, millis));
        }

        @Override
        public boolean awaitUntil(Date deadline) throws InterruptedException {
            long millis = deadline.getTime() - System.currentTimeMillis();
            return doWait(millis <= 0 ? -1 : millis);
        }

        @Override
        public synchronized void signal() {
            generation++;
            notifyAll();
        }

        @Override
        public synchronized void signalAll() {
            generation++;
            notifyAll();
        }
    }
}
