package com.android.car.protobuf;

import java.util.ArrayList;
import java.util.List;
/* loaded from: classes3.dex */
final class ProtobufArrayList<E> extends AbstractProtobufList<E> {
    private static final ProtobufArrayList<Object> EMPTY_LIST = new ProtobufArrayList<>();
    private final List<E> list;

    static {
        EMPTY_LIST.makeImmutable();
    }

    public static <E> ProtobufArrayList<E> emptyList() {
        return (ProtobufArrayList<E>) EMPTY_LIST;
    }

    ProtobufArrayList() {
        this(new ArrayList(10));
    }

    private ProtobufArrayList(List<E> list) {
        this.list = list;
    }

    @Override // com.android.car.protobuf.Internal.ProtobufList, com.android.car.protobuf.Internal.BooleanList
    public ProtobufArrayList<E> mutableCopyWithCapacity(int capacity) {
        if (capacity < size()) {
            throw new IllegalArgumentException();
        }
        List<E> newList = new ArrayList<>(capacity);
        newList.addAll(this.list);
        return new ProtobufArrayList<>(newList);
    }

    @Override // com.android.car.protobuf.AbstractProtobufList, java.util.AbstractList, java.util.List
    public void add(int index, E element) {
        ensureIsMutable();
        this.list.add(index, element);
        this.modCount++;
    }

    @Override // java.util.AbstractList, java.util.List
    public E get(int index) {
        return this.list.get(index);
    }

    @Override // com.android.car.protobuf.AbstractProtobufList, java.util.AbstractList, java.util.List
    public E remove(int index) {
        ensureIsMutable();
        E toReturn = this.list.remove(index);
        this.modCount++;
        return toReturn;
    }

    @Override // com.android.car.protobuf.AbstractProtobufList, java.util.AbstractList, java.util.List
    public E set(int index, E element) {
        ensureIsMutable();
        E toReturn = this.list.set(index, element);
        this.modCount++;
        return toReturn;
    }

    @Override // java.util.AbstractCollection, java.util.Collection, java.util.List
    public int size() {
        return this.list.size();
    }
}
