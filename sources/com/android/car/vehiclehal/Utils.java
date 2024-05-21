package com.android.car.vehiclehal;

import android.util.SparseArray;
import java.util.Iterator;
/* loaded from: classes3.dex */
class Utils {
    private Utils() {
    }

    /* loaded from: classes3.dex */
    static class SparseArrayIterator<T> implements Iterable<SparseArrayEntry<T>>, Iterator<SparseArrayEntry<T>> {
        private final SparseArray<T> mArray;
        private int mIndex = 0;

        /* JADX INFO: Access modifiers changed from: package-private */
        /* loaded from: classes3.dex */
        public static class SparseArrayEntry<U> {
            public final int key;
            public final U value;

            SparseArrayEntry(SparseArray<U> array, int index) {
                this.key = array.keyAt(index);
                this.value = array.valueAt(index);
            }
        }

        /* JADX INFO: Access modifiers changed from: package-private */
        public SparseArrayIterator(SparseArray<T> array) {
            this.mArray = array;
        }

        @Override // java.lang.Iterable
        public Iterator<SparseArrayEntry<T>> iterator() {
            return this;
        }

        @Override // java.util.Iterator
        public boolean hasNext() {
            return this.mIndex < this.mArray.size();
        }

        @Override // java.util.Iterator
        public SparseArrayEntry<T> next() {
            SparseArray<T> sparseArray = this.mArray;
            int i = this.mIndex;
            this.mIndex = i + 1;
            return new SparseArrayEntry<>(sparseArray, i);
        }
    }
}
