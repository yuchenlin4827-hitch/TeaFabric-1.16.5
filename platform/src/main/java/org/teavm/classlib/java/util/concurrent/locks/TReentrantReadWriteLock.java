package org.teavm.classlib.java.util.concurrent.locks;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TReentrantReadWriteLock implements TReadWriteLock, java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final ReadLock readerLock = new ReadLock(this);
    private final WriteLock writerLock = new WriteLock(this);
    private transient Thread writer;
    private int writeHolds;
    private final transient Map<Thread, Integer> readers = new HashMap<>();
    private int readCount;

    public TReentrantReadWriteLock() {
    }

    public TReentrantReadWriteLock(boolean fair) {
    }

    @Override
    public ReadLock readLock() {
        return readerLock;
    }

    @Override
    public WriteLock writeLock() {
        return writerLock;
    }

    public final boolean isFair() {
        return false;
    }

    public synchronized int getReadLockCount() {
        return readCount;
    }

    public synchronized boolean isWriteLocked() {
        return writer != null;
    }

    public synchronized boolean isWriteLockedByCurrentThread() {
        return writer == Thread.currentThread();
    }

    public synchronized int getWriteHoldCount() {
        return writer == Thread.currentThread() ? writeHolds : 0;
    }

    public synchronized int getReadHoldCount() {
        Integer n = readers.get(Thread.currentThread());
        return n == null ? 0 : n;
    }

    private synchronized boolean tryRead() {
        Thread me = Thread.currentThread();
        if (writer != null && writer != me) {
            return false;
        }
        readers.merge(me, 1, Integer::sum);
        readCount++;
        return true;
    }

    private synchronized void read(long millis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + millis;
        while (!tryRead()) {
            if (millis > 0) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) {
                    throw new InterruptedException("timeout");
                }
                wait(left);
            } else {
                wait();
            }
        }
    }

    private synchronized void unread() {
        Thread me = Thread.currentThread();
        Integer n = readers.get(me);
        if (n == null) {
            throw new IllegalMonitorStateException();
        }
        if (n == 1) {
            readers.remove(me);
        } else {
            readers.put(me, n - 1);
        }
        readCount--;
        notifyAll();
    }

    private synchronized boolean tryWrite() {
        Thread me = Thread.currentThread();
        if (writer == me) {
            writeHolds++;
            return true;
        }
        if (writer != null) {
            return false;
        }
        Integer mine = readers.get(me);
        if (readCount - (mine == null ? 0 : mine) > 0) {
            return false;
        }
        writer = me;
        writeHolds = 1;
        return true;
    }

    private synchronized void write(long millis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + millis;
        while (!tryWrite()) {
            if (millis > 0) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) {
                    throw new InterruptedException("timeout");
                }
                wait(left);
            } else {
                wait();
            }
        }
    }

    private synchronized void unwrite() {
        if (writer != Thread.currentThread()) {
            throw new IllegalMonitorStateException();
        }
        if (--writeHolds == 0) {
            writer = null;
            notifyAll();
        }
    }

    public static class ReadLock implements TLock, java.io.Serializable {
        private static final long serialVersionUID = 1L;
        private final TReentrantReadWriteLock owner;

        protected ReadLock(TReentrantReadWriteLock lock) {
            this.owner = lock;
        }

        @Override
        public void lock() {
            boolean interrupted = false;
            while (true) {
                try {
                    owner.read(0);
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
        public void lockInterruptibly() throws InterruptedException {
            owner.read(0);
        }

        @Override
        public boolean tryLock() {
            return owner.tryRead();
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            try {
                owner.read(Math.max(1, unit.toMillis(time)));
                return true;
            } catch (InterruptedException e) {
                if ("timeout".equals(e.getMessage())) {
                    return false;
                }
                throw e;
            }
        }

        @Override
        public void unlock() {
            owner.unread();
        }

        @Override
        public TCondition newCondition() {
            throw new UnsupportedOperationException();
        }
    }

    public static class WriteLock implements TLock, java.io.Serializable {
        private static final long serialVersionUID = 1L;
        private final TReentrantReadWriteLock owner;

        protected WriteLock(TReentrantReadWriteLock lock) {
            this.owner = lock;
        }

        @Override
        public void lock() {
            boolean interrupted = false;
            while (true) {
                try {
                    owner.write(0);
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
        public void lockInterruptibly() throws InterruptedException {
            owner.write(0);
        }

        @Override
        public boolean tryLock() {
            return owner.tryWrite();
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            try {
                owner.write(Math.max(1, unit.toMillis(time)));
                return true;
            } catch (InterruptedException e) {
                if ("timeout".equals(e.getMessage())) {
                    return false;
                }
                throw e;
            }
        }

        @Override
        public void unlock() {
            owner.unwrite();
        }

        @Override
        public TCondition newCondition() {
            throw new UnsupportedOperationException();
        }

        public boolean isHeldByCurrentThread() {
            return owner.isWriteLockedByCurrentThread();
        }

        public int getHoldCount() {
            return owner.getWriteHoldCount();
        }
    }
}
