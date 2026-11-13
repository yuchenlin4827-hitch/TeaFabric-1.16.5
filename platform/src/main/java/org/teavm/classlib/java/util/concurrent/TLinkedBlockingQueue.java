package org.teavm.classlib.java.util.concurrent;

import java.util.AbstractQueue;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class TLinkedBlockingQueue<E> extends AbstractQueue<E> implements BlockingQueue<E>, java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final ArrayDeque<E> items = new ArrayDeque<>();
    private final int capacity;

    public TLinkedBlockingQueue() {
        this(Integer.MAX_VALUE);
    }

    /**
     * A positive timeout below one millisecond still waits one: rounding it to 0 made Netty's event loop poll
     * without ever yielding while a scheduled task was less than 1 ms away (green threads only switch on waits).
     */
    private static long waitMillis(long timeout, TimeUnit unit) {
        if (timeout <= 0) {
            return 0;
        }
        return Math.max(1, unit.toMillis(timeout));
    }

    public TLinkedBlockingQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException();
        }
        this.capacity = capacity;
    }

    public TLinkedBlockingQueue(Collection<? extends E> c) {
        this(Integer.MAX_VALUE);
        addAll(c);
    }

    @Override
    public synchronized boolean offer(E e) {
        if (e == null) {
            throw new NullPointerException();
        }
        if (items.size() >= capacity) {
            return false;
        }
        items.add(e);
        notifyAll();
        return true;
    }

    @Override
    public synchronized void put(E e) throws InterruptedException {
        if (e == null) {
            throw new NullPointerException();
        }
        while (items.size() >= capacity) {
            wait();
        }
        items.add(e);
        notifyAll();
    }

    @Override
    public synchronized boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.currentTimeMillis() + waitMillis(timeout, unit);
        while (items.size() >= capacity) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) {
                return false;
            }
            wait(left);
        }
        items.add(e);
        notifyAll();
        return true;
    }

    @Override
    public synchronized E take() throws InterruptedException {
        while (items.isEmpty()) {
            wait();
        }
        E e = items.poll();
        notifyAll();
        return e;
    }

    @Override
    public synchronized E poll(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.currentTimeMillis() + waitMillis(timeout, unit);
        while (items.isEmpty()) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) {
                return null;
            }
            wait(left);
        }
        E e = items.poll();
        notifyAll();
        return e;
    }

    @Override
    public synchronized E poll() {
        E e = items.poll();
        if (e != null) {
            notifyAll();
        }
        return e;
    }

    @Override
    public synchronized E peek() {
        return items.peek();
    }

    @Override
    public synchronized int remainingCapacity() {
        return capacity - items.size();
    }

    @Override
    public synchronized boolean remove(Object o) {
        boolean r = items.remove(o);
        if (r) {
            notifyAll();
        }
        return r;
    }

    @Override
    public synchronized int drainTo(Collection<? super E> c) {
        return drainTo(c, Integer.MAX_VALUE);
    }

    @Override
    public synchronized int drainTo(Collection<? super E> c, int maxElements) {
        int n = 0;
        while (n < maxElements && !items.isEmpty()) {
            c.add(items.poll());
            n++;
        }
        if (n > 0) {
            notifyAll();
        }
        return n;
    }

    @Override
    public synchronized int size() {
        return items.size();
    }

    @Override
    public synchronized void clear() {
        items.clear();
        notifyAll();
    }

    @Override
    public synchronized Iterator<E> iterator() {
        Iterator<E> snapshot = new ArrayList<>(items).iterator();
        return new Iterator<E>() {
            E last;

            @Override
            public boolean hasNext() {
                return snapshot.hasNext();
            }

            @Override
            public E next() {
                last = snapshot.next();
                return last;
            }

            @Override
            public void remove() {
                TLinkedBlockingQueue.this.remove(last);
            }
        };
    }
}
