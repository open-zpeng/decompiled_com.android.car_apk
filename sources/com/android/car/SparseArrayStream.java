package com.android.car;

import android.util.Pair;
import android.util.SparseArray;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;
/* loaded from: classes3.dex */
public class SparseArrayStream {
    public static <E> IntStream keyStream(final SparseArray<E> array) {
        IntStream range = IntStream.range(0, array.size());
        Objects.requireNonNull(array);
        return range.map(new IntUnaryOperator() { // from class: com.android.car.-$$Lambda$q72-uw-mQt7s7qER6SYQZJ9GG9o
            @Override // java.util.function.IntUnaryOperator
            public final int applyAsInt(int i) {
                return array.keyAt(i);
            }
        });
    }

    public static <E> Stream<E> valueStream(final SparseArray<E> array) {
        IntStream range = IntStream.range(0, array.size());
        Objects.requireNonNull(array);
        return range.mapToObj(new IntFunction() { // from class: com.android.car.-$$Lambda$xzPthtWZsfpNCV7Z2aMfljhAjQ0
            @Override // java.util.function.IntFunction
            public final Object apply(int i) {
                return array.valueAt(i);
            }
        });
    }

    public static <E> Stream<Pair<Integer, E>> pairStream(final SparseArray<E> array) {
        return IntStream.range(0, array.size()).mapToObj(new IntFunction() { // from class: com.android.car.-$$Lambda$SparseArrayStream$lRGBHhGP4jz5dfHErtvBMS158NU
            @Override // java.util.function.IntFunction
            public final Object apply(int i) {
                return SparseArrayStream.lambda$pairStream$0(array, i);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ Pair lambda$pairStream$0(SparseArray array, int i) {
        return new Pair(Integer.valueOf(array.keyAt(i)), array.valueAt(i));
    }
}
