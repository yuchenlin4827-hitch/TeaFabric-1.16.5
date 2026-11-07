package org.teavm.classlib.java.util.concurrent;

import java.util.AbstractCollection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/** Thread-safe queue for green threads; iterators work on a snapshot like the JDK's weakly consistent ones. */
public class TConcurrentLinkedQueue<E> extends AbstractCollection<E> implements java.util.Queue<E>, java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final ArrayDeque<E> items = new ArrayDeque<>();

    public TConcurrentLinkedQueue() {
    }

    public TConcurrentLinkedQueue(Collection<? extends E> c) {
        for (E e : c) {
            add(e);
        }
    }

    private static void check(Object e) {
        if (e == null) {
            throw new NullPointerException();
        }
    }

    @Override
    public synchronized boolean add(E e) {
        check(e);
        return items.add(e);
    }

    @Override
    public synchronized boolean offer(E e) {
        check(e);
        return items.offer(e);
    }

    @Override
    public synchronized E remove() {
        return items.remove();
    }

    @Override
    public synchronized E poll() {
        return items.poll();
    }

    @Override
    public synchronized E element() {
        return items.element();
    }

    @Override
    public synchronized E peek() {
        return items.peek();
    }

    @Override
    public synchronized boolean remove(Object o) {
        return items.remove(o);
    }

    @Override
    public synchronized boolean contains(Object o) {
        return items.contains(o);
    }

    @Override
    public synchronized int size() {
        return items.size();
    }

    @Override
    public synchronized boolean isEmpty() {
        return items.isEmpty();
    }

    @Override
    public synchronized void clear() {
        items.clear();
    }

    @Override
    public synchronized Object[] toArray() {
        return items.toArray();
    }

    @Override
    public synchronized <T> T[] toArray(T[] a) {
        return items.toArray(a);
    }

    @Override
    public Iterator<E> iterator() {
        ArrayList<E> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(items);
        }
        Iterator<E> it = snapshot.iterator();
        return new Iterator<E>() {
            E last;

            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public E next() {
                last = it.next();
                return last;
            }

            @Override
            public void remove() {
                TConcurrentLinkedQueue.this.removeIdentity(last);
            }
        };
    }

    synchronized void removeIdentity(Object o) {
        Iterator<E> it = items.iterator();
        while (it.hasNext()) {
            if (it.next() == o) {
                it.remove();
                return;
            }
        }
    }
}
