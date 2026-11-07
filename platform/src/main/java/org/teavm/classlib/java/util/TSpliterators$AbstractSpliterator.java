package org.teavm.classlib.java.util;

import java.util.Spliterator;
import java.util.function.Consumer;

/** java.util.Spliterators.AbstractSpliterator (TeaVM's TSpliterators has no nested classes). */
public abstract class TSpliterators$AbstractSpliterator<T> implements Spliterator<T> {
    private final int characteristics;
    private long est;

    protected TSpliterators$AbstractSpliterator(long est, int additionalCharacteristics) {
        this.est = est;
        this.characteristics = (additionalCharacteristics & Spliterator.SIZED) != 0
                ? additionalCharacteristics | Spliterator.SUBSIZED : additionalCharacteristics;
    }

    @Override
    public Spliterator<T> trySplit() {
        return null;
    }

    @Override
    public long estimateSize() {
        return est;
    }

    @Override
    public int characteristics() {
        return characteristics;
    }

    @Override
    public void forEachRemaining(Consumer<? super T> action) {
        while (tryAdvance(action)) {
            // drain
        }
    }
}
