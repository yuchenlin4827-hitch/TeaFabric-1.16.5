package teafabric.web.compat;

import java.util.AbstractSet;
import java.util.Comparator;
import java.util.Iterator;
import java.util.SortedSet;

final class UnmodifiableSortedSet<E> extends AbstractSet<E> implements SortedSet<E> {
    private final SortedSet<E> s;

    UnmodifiableSortedSet(SortedSet<E> s) {
        this.s = s;
    }

    @Override
    public Iterator<E> iterator() {
        Iterator<E> it = s.iterator();
        return new Iterator<E>() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public E next() {
                return it.next();
            }
        };
    }

    @Override
    public int size() {
        return s.size();
    }

    @Override
    public boolean contains(Object o) {
        return s.contains(o);
    }

    @Override
    public Comparator<? super E> comparator() {
        return s.comparator();
    }

    @Override
    public SortedSet<E> subSet(E from, E to) {
        return new UnmodifiableSortedSet<>(s.subSet(from, to));
    }

    @Override
    public SortedSet<E> headSet(E to) {
        return new UnmodifiableSortedSet<>(s.headSet(to));
    }

    @Override
    public SortedSet<E> tailSet(E from) {
        return new UnmodifiableSortedSet<>(s.tailSet(from));
    }

    @Override
    public E first() {
        return s.first();
    }

    @Override
    public E last() {
        return s.last();
    }
}
