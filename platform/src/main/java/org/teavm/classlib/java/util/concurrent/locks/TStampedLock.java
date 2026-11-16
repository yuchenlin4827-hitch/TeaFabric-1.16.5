package org.teavm.classlib.java.util.concurrent.locks;

import java.util.concurrent.TimeUnit;
import teafabric.web.debug.WaitTracker;

/**
 * java.util.concurrent.locks.StampedLock, which TeaVM's class library lacks (Phosphor's light engine uses
 * it). Built on the object monitor like {@link TReentrantLock}: a contended acquire waits (TeaVM suspends the
 * green thread) until an unlock notifies, and the wait shows up in {@link WaitTracker} with {@code ?waits}.
 * Stamps follow the JDK contract: never zero, write stamps odd, and an optimistic read stamp stops
 * validating once a write lock has been taken.
 */
public class TStampedLock implements java.io.Serializable {
    private static final long serialVersionUID = -6001602636862214147L;

    /** Even while unlocked or read-locked; odd while write-locked. Increases on each write lock and unlock. */
    private long sequence = 256;
    private boolean writing;
    private int readers;

    public TStampedLock() {
    }

    private boolean writeBlocked() {
        return writing || readers > 0;
    }

    private long acquireWrite() {
        writing = true;
        return ++sequence;
    }

    private long acquireRead() {
        readers++;
        return sequence;
    }

    /** Waits until {@code blocked} is false; returns whether the thread was interrupted meanwhile. */
    private boolean awaitUninterruptibly(boolean forWrite, String what) {
        boolean interrupted = false;
        while (forWrite ? writeBlocked() : writing) {
            try {
                WaitTracker.waitOn(this, what);
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        return interrupted;
    }

    /** Waits until free or the deadline; returns false on timeout. */
    private boolean awaitUntil(boolean forWrite, long deadlineNanos) throws InterruptedException {
        while (forWrite ? writeBlocked() : writing) {
            long left = deadlineNanos - System.nanoTime();
            if (left <= 0) {
                return false;
            }
            wait(Math.max(1, TimeUnit.NANOSECONDS.toMillis(left)));
        }
        return true;
    }

    public synchronized long writeLock() {
        if (awaitUninterruptibly(true, "StampedLock.writeLock")) {
            Thread.currentThread().interrupt();
        }
        return acquireWrite();
    }

    public synchronized long tryWriteLock() {
        return writeBlocked() ? 0L : acquireWrite();
    }

    public synchronized long tryWriteLock(long time, TimeUnit unit) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        return awaitUntil(true, System.nanoTime() + unit.toNanos(time)) ? acquireWrite() : 0L;
    }

    public synchronized long writeLockInterruptibly() throws InterruptedException {
        while (writeBlocked()) {
            WaitTracker.waitOn(this, "StampedLock.writeLock");
        }
        return acquireWrite();
    }

    public synchronized long readLock() {
        if (awaitUninterruptibly(false, "StampedLock.readLock")) {
            Thread.currentThread().interrupt();
        }
        return acquireRead();
    }

    public synchronized long tryReadLock() {
        return writing ? 0L : acquireRead();
    }

    public synchronized long tryReadLock(long time, TimeUnit unit) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        return awaitUntil(false, System.nanoTime() + unit.toNanos(time)) ? acquireRead() : 0L;
    }

    public synchronized long readLockInterruptibly() throws InterruptedException {
        while (writing) {
            WaitTracker.waitOn(this, "StampedLock.readLock");
        }
        return acquireRead();
    }

    public long tryOptimisticRead() {
        return writing ? 0L : sequence;
    }

    public boolean validate(long stamp) {
        return stamp != 0L && !writing && stamp == sequence;
    }

    public synchronized void unlockWrite(long stamp) {
        if (!writing || stamp != sequence) {
            throw new IllegalMonitorStateException();
        }
        writing = false;
        sequence++;
        notifyAll();
    }

    public synchronized void unlockRead(long stamp) {
        if (readers == 0 || writing || stamp != sequence) {
            throw new IllegalMonitorStateException();
        }
        if (--readers == 0) {
            notifyAll();
        }
    }

    public synchronized void unlock(long stamp) {
        if (writing) {
            unlockWrite(stamp);
        } else {
            unlockRead(stamp);
        }
    }

    public synchronized long tryConvertToWriteLock(long stamp) {
        if (stamp == 0L || stamp != sequence) {
            return 0L;
        }
        if (writing) {
            return stamp;
        }
        if (readers <= 1) { // no readers, or only the caller's own read lock
            readers = 0;
            return acquireWrite();
        }
        return 0L;
    }

    public synchronized long tryConvertToReadLock(long stamp) {
        if (stamp == 0L || stamp != sequence) {
            return 0L;
        }
        if (writing) {
            writing = false;
            sequence++;
            readers++;
            notifyAll();
            return sequence;
        }
        return readers > 0 ? stamp : acquireRead();
    }

    public synchronized long tryConvertToOptimisticRead(long stamp) {
        if (stamp == 0L || stamp != sequence) {
            return 0L;
        }
        if (writing) {
            writing = false;
            sequence++;
            notifyAll();
        } else if (readers > 0 && --readers == 0) {
            notifyAll();
        }
        return sequence;
    }

    public synchronized boolean tryUnlockWrite() {
        if (!writing) {
            return false;
        }
        writing = false;
        sequence++;
        notifyAll();
        return true;
    }

    public synchronized boolean tryUnlockRead() {
        if (readers == 0) {
            return false;
        }
        if (--readers == 0) {
            notifyAll();
        }
        return true;
    }

    public boolean isWriteLocked() {
        return writing;
    }

    public boolean isReadLocked() {
        return readers > 0;
    }

    public int getReadLockCount() {
        return readers;
    }

    public static boolean isWriteLockStamp(long stamp) {
        return (stamp & 1L) != 0L;
    }

    public static boolean isReadLockStamp(long stamp) {
        return stamp != 0L && (stamp & 1L) == 0L;
    }

    public static boolean isOptimisticReadStamp(long stamp) {
        return stamp != 0L && (stamp & 1L) == 0L;
    }

    public static boolean isLockStamp(long stamp) {
        return stamp != 0L;
    }

    @Override
    public String toString() {
        return super.toString() + (writing ? "[Write-locked]"
                : readers > 0 ? "[Read-locks:" + readers + "]" : "[Unlocked]");
    }
}
