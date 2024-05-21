package com.android.car;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
/* JADX INFO: Access modifiers changed from: package-private */
/* loaded from: classes3.dex */
public class SlidingWindow<T> implements Iterable<T> {
    private final ArrayDeque<T> mElements;
    private final int mMaxSize;

    public SlidingWindow(int size) {
        this.mMaxSize = size;
        this.mElements = new ArrayDeque<>(this.mMaxSize);
    }

    public void add(T sample) {
        if (this.mElements.size() == this.mMaxSize) {
            this.mElements.removeFirst();
        }
        this.mElements.addLast(sample);
    }

    public void addAll(Iterable<T> elements) {
        elements.forEach(new Consumer() { // from class: com.android.car.-$$Lambda$SdrTrmIMzFfe9jslxET-9slhemo
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                SlidingWindow.this.add(obj);
            }
        });
    }

    @Override // java.lang.Iterable
    public Iterator<T> iterator() {
        return this.mElements.iterator();
    }

    public Stream<T> stream() {
        return this.mElements.stream();
    }

    public int size() {
        return this.mElements.size();
    }

    /* JADX WARN: Multi-variable type inference failed */
    public int count(Predicate<T> predicate) {
        return (int) stream().filter(predicate).count();
    }
}
