package org.teavm.classlib.java.util.concurrent.locks;

import java.util.concurrent.TimeUnit;

/**
 * java.util.concurrent.locks.StampedLock, which TeaVM's class library lacks (Phosphor's light engine
 * uses it). It lives here, under the class library's naming, because the class library's name for a
 * java.* class takes precedence over a class of the plain name elsewhere on the class path.
 * Threads here are green threads that only switch at blocking calls, so the lock never needs to park: a
 * contended acquire yields until the lock is free. Stamps follow the JDK contract: never zero, and an
 * optimistic read stamp stops validating once a write lock has been taken.
 */
public class TStampedLock implements java.io.Serializable {
    private static final long serialVersionUID = -6001602636862214147L;

    /** Increases when a write lock is taken and again when it is released. */
    private long sequence = 256;
    private boolean writing;
    private int readers;

    public TStampedLock() {
    }

    public long writeLock() {
        while (writing || readers > 0) {
            Thread.yield();
        }
        return acquireWrite();
    }

    public long tryWriteLock() {
        return writing || readers > 0 ? 0L : acquireWrite();
    }

    public long tryWriteLock(long time, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(time);
        while (writing || readers > 0) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            if (System.nanoTime() - deadline >= 0) {
                return 0L;
            }
            Thread.yield();
        }
        return acquireWrite();
    }

    public long writeLockInterruptibly() throws InterruptedException {
        while (writing || readers > 0) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            Thread.yield();
        }
        return acquireWrite();
    }

    private long acquireWrite() {
        writing = true;
        return ++sequence;
    }

    public long readLock() {
        while (writing) {
            Thread.yield();
        }
        readers++;
        return sequence;
    }

    public long tryReadLock() {
        if (writing) {
            return 0L;
        }
        readers++;
        return sequence;
    }

    public long tryReadLock(long time, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(time);
        while (writing) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            if (System.nanoTime() - deadline >= 0) {
                return 0L;
            }
            Thread.yield();
        }
        readers++;
        return sequence;
    }

    public long readLockInterruptibly() throws InterruptedException {
        while (writing) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            Thread.yield();
        }
        readers++;
        return sequence;
    }

    public long tryOptimisticRead() {
        return writing ? 0L : sequence;
    }

    public boolean validate(long stamp) {
        return stamp != 0L && !writing && stamp == sequence;
    }

    public void unlockWrite(long stamp) {
        if (!writing || stamp != sequence) {
            throw new IllegalMonitorStateException();
        }
        writing = false;
        sequence++;
    }

    public void unlockRead(long stamp) {
        if (readers == 0 || writing || stamp != sequence) {
            throw new IllegalMonitorStateException();
        }
        readers--;
    }

    public void unlock(long stamp) {
        if (writing) {
            unlockWrite(stamp);
        } else {
            unlockRead(stamp);
        }
    }

    public long tryConvertToWriteLock(long stamp) {
        if (stamp == 0L || stamp != sequence) {
            return 0L;
        }
        if (writing) {
            return stamp;
        }
        if (readers == 1) { // the caller's own read lock
            readers = 0;
            return acquireWrite();
        }
        return readers == 0 ? acquireWrite() : 0L;
    }

    public long tryConvertToReadLock(long stamp) {
        if (stamp == 0L) {
            return 0L;
        }
        if (writing) {
            if (stamp != sequence) {
                return 0L;
            }
            unlockWrite(stamp);
            readers++;
            return sequence;
        }
        if (stamp != sequence) {
            return 0L;
        }
        return readers > 0 ? stamp : tryReadLock();
    }

    public long tryConvertToOptimisticRead(long stamp) {
        if (stamp == 0L || stamp != sequence) {
            return 0L;
        }
        if (writing) {
            unlockWrite(stamp);
        } else if (readers > 0) {
            readers--;
        }
        return sequence;
    }

    public boolean tryUnlockWrite() {
        if (!writing) {
            return false;
        }
        writing = false;
        sequence++;
        return true;
    }

    public boolean tryUnlockRead() {
        if (readers == 0) {
            return false;
        }
        readers--;
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
        return stamp != 0L && (stamp & 1L) != 0L;
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
