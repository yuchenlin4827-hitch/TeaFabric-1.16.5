package org.teavm.classlib.java.util.concurrent;

import java.util.AbstractCollection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/** Thread-safe queue for green threads; iterators work on a snapshot like the JDK's weakly consistent ones. */
public class TConcurrentLinkedDeque<E> extends AbstractCollection<E> implements java.util.Deque<E>, java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final ArrayDeque<E> items = new ArrayDeque<>();

    public TConcurrentLinkedDeque() {
    }

    public TConcurrentLinkedDeque(Collection<? extends E> c) {
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
                TConcurrentLinkedDeque.this.removeIdentity(last);
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

    @Override
    public synchronized void addFirst(E e) {
        check(e);
        items.addFirst(e);
    }

    @Override
    public synchronized void addLast(E e) {
        check(e);
        items.addLast(e);
    }

    @Override
    public synchronized boolean offerFirst(E e) {
        check(e);
        return items.offerFirst(e);
    }

    @Override
    public synchronized boolean offerLast(E e) {
        check(e);
        return items.offerLast(e);
    }

    @Override
    public synchronized E removeFirst() {
        return items.removeFirst();
    }

    @Override
    public synchronized E removeLast() {
        return items.removeLast();
    }

    @Override
    public synchronized E pollFirst() {
        return items.pollFirst();
    }

    @Override
    public synchronized E pollLast() {
        return items.pollLast();
    }

    @Override
    public synchronized E getFirst() {
        return items.getFirst();
    }

    @Override
    public synchronized E getLast() {
        return items.getLast();
    }

    @Override
    public synchronized E peekFirst() {
        return items.peekFirst();
    }

    @Override
    public synchronized E peekLast() {
        return items.peekLast();
    }

    @Override
    public synchronized boolean removeFirstOccurrence(Object o) {
        return items.removeFirstOccurrence(o);
    }

    @Override
    public synchronized boolean removeLastOccurrence(Object o) {
        return items.removeLastOccurrence(o);
    }

    @Override
    public synchronized void push(E e) {
        check(e);
        items.push(e);
    }

    @Override
    public synchronized E pop() {
        return items.pop();
    }

    @Override
    public Iterator<E> descendingIterator() {
        ArrayList<E> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(items);
        }
        java.util.Collections.reverse(snapshot);
        return snapshot.iterator();
    }
}
