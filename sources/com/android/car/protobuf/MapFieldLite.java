package com.android.car.protobuf;

import com.android.car.protobuf.Internal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
/* loaded from: classes3.dex */
public final class MapFieldLite<K, V> implements MutabilityOracle {
    private static final MapFieldLite EMPTY_MAP_FIELD = new MapFieldLite(Collections.emptyMap());
    private boolean isMutable = true;
    private MutatabilityAwareMap<K, V> mapData;

    private MapFieldLite(Map<K, V> mapData) {
        this.mapData = new MutatabilityAwareMap<>(this, mapData);
    }

    static {
        EMPTY_MAP_FIELD.makeImmutable();
    }

    public static <K, V> MapFieldLite<K, V> emptyMapField() {
        return EMPTY_MAP_FIELD;
    }

    public static <K, V> MapFieldLite<K, V> newMapField() {
        return new MapFieldLite<>(new LinkedHashMap());
    }

    public Map<K, V> getMap() {
        return Collections.unmodifiableMap(this.mapData);
    }

    public Map<K, V> getMutableMap() {
        return this.mapData;
    }

    public void mergeFrom(MapFieldLite<K, V> other) {
        this.mapData.putAll(copy((Map) other.mapData));
    }

    public void clear() {
        this.mapData.clear();
    }

    private static boolean equals(Object a, Object b) {
        if ((a instanceof byte[]) && (b instanceof byte[])) {
            return Arrays.equals((byte[]) a, (byte[]) b);
        }
        return a.equals(b);
    }

    /* JADX WARN: Removed duplicated region for block: B:11:0x001e  */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    static <K, V> boolean equals(java.util.Map<K, V> r6, java.util.Map<K, V> r7) {
        /*
            r0 = 1
            if (r6 != r7) goto L4
            return r0
        L4:
            int r1 = r6.size()
            int r2 = r7.size()
            r3 = 0
            if (r1 == r2) goto L10
            return r3
        L10:
            java.util.Set r1 = r6.entrySet()
            java.util.Iterator r1 = r1.iterator()
        L18:
            boolean r2 = r1.hasNext()
            if (r2 == 0) goto L43
            java.lang.Object r2 = r1.next()
            java.util.Map$Entry r2 = (java.util.Map.Entry) r2
            java.lang.Object r4 = r2.getKey()
            boolean r4 = r7.containsKey(r4)
            if (r4 != 0) goto L2f
            return r3
        L2f:
            java.lang.Object r4 = r2.getValue()
            java.lang.Object r5 = r2.getKey()
            java.lang.Object r5 = r7.get(r5)
            boolean r4 = equals(r4, r5)
            if (r4 != 0) goto L42
            return r3
        L42:
            goto L18
        L43:
            return r0
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.car.protobuf.MapFieldLite.equals(java.util.Map, java.util.Map):boolean");
    }

    public boolean equals(Object object) {
        if (!(object instanceof MapFieldLite)) {
            return false;
        }
        MapFieldLite<K, V> other = (MapFieldLite) object;
        return equals((Map) this.mapData, (Map) other.mapData);
    }

    private static int calculateHashCodeForObject(Object a) {
        if (a instanceof byte[]) {
            return Internal.hashCode((byte[]) a);
        }
        if (a instanceof Internal.EnumLite) {
            throw new UnsupportedOperationException();
        }
        return a.hashCode();
    }

    static <K, V> int calculateHashCodeForMap(Map<K, V> a) {
        int result = 0;
        for (Map.Entry<K, V> entry : a.entrySet()) {
            result += calculateHashCodeForObject(entry.getKey()) ^ calculateHashCodeForObject(entry.getValue());
        }
        return result;
    }

    public int hashCode() {
        return calculateHashCodeForMap(this.mapData);
    }

    private static Object copy(Object object) {
        if (object instanceof byte[]) {
            byte[] data = (byte[]) object;
            return Arrays.copyOf(data, data.length);
        }
        return object;
    }

    /* JADX WARN: Multi-variable type inference failed */
    static <K, V> Map<K, V> copy(Map<K, V> map) {
        LinkedHashMap linkedHashMap = new LinkedHashMap();
        for (Map.Entry<K, V> entry : map.entrySet()) {
            linkedHashMap.put(entry.getKey(), copy(entry.getValue()));
        }
        return linkedHashMap;
    }

    public MapFieldLite<K, V> copy() {
        return new MapFieldLite<>(copy((Map) this.mapData));
    }

    public void makeImmutable() {
        this.isMutable = false;
    }

    public boolean isMutable() {
        return this.isMutable;
    }

    @Override // com.android.car.protobuf.MutabilityOracle
    public void ensureMutable() {
        if (!isMutable()) {
            throw new UnsupportedOperationException();
        }
    }

    /* loaded from: classes3.dex */
    static class MutatabilityAwareMap<K, V> implements Map<K, V> {
        private final Map<K, V> delegate;
        private final MutabilityOracle mutabilityOracle;

        MutatabilityAwareMap(MutabilityOracle mutabilityOracle, Map<K, V> delegate) {
            this.mutabilityOracle = mutabilityOracle;
            this.delegate = delegate;
        }

        @Override // java.util.Map
        public int size() {
            return this.delegate.size();
        }

        @Override // java.util.Map
        public boolean isEmpty() {
            return this.delegate.isEmpty();
        }

        @Override // java.util.Map
        public boolean containsKey(Object key) {
            return this.delegate.containsKey(key);
        }

        @Override // java.util.Map
        public boolean containsValue(Object value) {
            return this.delegate.containsValue(value);
        }

        @Override // java.util.Map
        public V get(Object key) {
            return this.delegate.get(key);
        }

        @Override // java.util.Map
        public V put(K key, V value) {
            this.mutabilityOracle.ensureMutable();
            return this.delegate.put(key, value);
        }

        @Override // java.util.Map
        public V remove(Object key) {
            this.mutabilityOracle.ensureMutable();
            return this.delegate.remove(key);
        }

        @Override // java.util.Map
        public void putAll(Map<? extends K, ? extends V> m) {
            this.mutabilityOracle.ensureMutable();
            this.delegate.putAll(m);
        }

        @Override // java.util.Map
        public void clear() {
            this.mutabilityOracle.ensureMutable();
            this.delegate.clear();
        }

        @Override // java.util.Map
        public Set<K> keySet() {
            return new MutatabilityAwareSet(this.mutabilityOracle, this.delegate.keySet());
        }

        @Override // java.util.Map
        public Collection<V> values() {
            return new MutatabilityAwareCollection(this.mutabilityOracle, this.delegate.values());
        }

        @Override // java.util.Map
        public Set<Map.Entry<K, V>> entrySet() {
            return new MutatabilityAwareSet(this.mutabilityOracle, this.delegate.entrySet());
        }

        @Override // java.util.Map
        public boolean equals(Object o) {
            return this.delegate.equals(o);
        }

        @Override // java.util.Map
        public int hashCode() {
            return this.delegate.hashCode();
        }

        public String toString() {
            return this.delegate.toString();
        }
    }

    /* loaded from: classes3.dex */
    private static class MutatabilityAwareCollection<E> implements Collection<E> {
        private final Collection<E> delegate;
        private final MutabilityOracle mutabilityOracle;

        MutatabilityAwareCollection(MutabilityOracle mutabilityOracle, Collection<E> delegate) {
            this.mutabilityOracle = mutabilityOracle;
            this.delegate = delegate;
        }

        @Override // java.util.Collection
        public int size() {
            return this.delegate.size();
        }

        @Override // java.util.Collection
        public boolean isEmpty() {
            return this.delegate.isEmpty();
        }

        @Override // java.util.Collection
        public boolean contains(Object o) {
            return this.delegate.contains(o);
        }

        @Override // java.util.Collection, java.lang.Iterable
        public Iterator<E> iterator() {
            return new MutatabilityAwareIterator(this.mutabilityOracle, this.delegate.iterator());
        }

        @Override // java.util.Collection
        public Object[] toArray() {
            return this.delegate.toArray();
        }

        @Override // java.util.Collection
        public <T> T[] toArray(T[] a) {
            return (T[]) this.delegate.toArray(a);
        }

        @Override // java.util.Collection
        public boolean add(E e) {
            throw new UnsupportedOperationException();
        }

        @Override // java.util.Collection
        public boolean remove(Object o) {
            this.mutabilityOracle.ensureMutable();
            return this.delegate.remove(o);
        }

        @Override // java.util.Collection
        public boolean containsAll(Collection<?> c) {
            return this.delegate.containsAll(c);
        }

        @Override // java.util.Collection
        public boolean addAll(Collection<? extends E> c) {
            throw new UnsupportedOperationException();
        }

        @Override // java.util.Collection
        public boolean removeAll(Collection<?> c) {
            this.mutabilityOracle.ensureMutable();
            return this.delegate.removeAll(c);
        }

        @Override // java.util.Collection
        public boolean retainAll(Collection<?> c) {
            this.mutabilityOracle.ensureMutable();
            return this.delegate.retainAll(c);
        }

        @Override // java.util.Collection
        public void clear() {
            this.mutabilityOracle.ensureMutable();
            this.delegate.clear();
        }

        @Override // java.util.Collection
        public boolean equals(Object o) {
            return this.delegate.equals(o);
        }

        @Override // java.util.Collection
        public int hashCode() {
            return this.delegate.hashCode();
        }

        public String toString() {
            return this.delegate.toString();
        }
    }

    /* loaded from: classes3.dex */
    private static class MutatabilityAwareSet<E> implements Set<E> {
        private final Set<E> delegate;
        private final MutabilityOracle mutabilityOracle;

        MutatabilityAwareSet(MutabilityOracle mutabilityOracle, Set<E> delegate) {
            this.mutabilityOracle = mutabilityOracle;
            this.delegate = delegate;
        }

        @Override // java.util.Set, java.util.Collection
        public int size() {
            return this.delegate.size();
        }

        @Override // java.util.Set, java.util.Collection
        public boolean isEmpty() {
            return this.delegate.isEmpty();
        }

        @Override // java.util.Set, java.util.Collection
        public boolean contains(Object o) {
            return this.delegate.contains(o);
        }

        @Override // java.util.Set, java.util.Collection, java.lang.Iterable
        public Iterator<E> iterator() {
            return new MutatabilityAwareIterator(this.mutabilityOracle, this.delegate.iterator());
        }

        @Override // java.util.Set, java.util.Collection
        public Object[] toArray() {
            return this.delegate.toArray();
        }

        @Override // java.util.Set, java.util.Collection
        public <T> T[] toArray(T[] a) {
            return (T[]) this.delegate.toArray(a);
        }

        @Override // java.util.Set, java.util.Collection
        public boolean add(E e) {
            this.mutabilityOracle.ensureMutable();
            return this.delegate.add(e);
        }

        @Override // java.util.Set, java.util.Collection
        public boolean remove(Object o) {
            this.mutabilityOracle.ensureMutable();
            return this.delegate.remove(o);
        }

        @Override // java.util.Set, java.util.Collection
        public boolean containsAll(Collection<?> c) {
            return this.delegate.containsAll(c);
        }

        @Override // java.util.Set, java.util.Collection
        public boolean addAll(Collection<? extends E> c) {
            this.mutabilityOracle.ensureMutable();
            return this.delegate.addAll(c);
        }

        @Override // java.util.Set, java.util.Collection
        public boolean retainAll(Collection<?> c) {
            this.mutabilityOracle.ensureMutable();
            return this.delegate.retainAll(c);
        }

        @Override // java.util.Set, java.util.Collection
        public boolean removeAll(Collection<?> c) {
            this.mutabilityOracle.ensureMutable();
            return this.delegate.removeAll(c);
        }

        @Override // java.util.Set, java.util.Collection
        public void clear() {
            this.mutabilityOracle.ensureMutable();
            this.delegate.clear();
        }

        @Override // java.util.Set, java.util.Collection
        public boolean equals(Object o) {
            return this.delegate.equals(o);
        }

        @Override // java.util.Set, java.util.Collection
        public int hashCode() {
            return this.delegate.hashCode();
        }

        public String toString() {
            return this.delegate.toString();
        }
    }

    /* loaded from: classes3.dex */
    private static class MutatabilityAwareIterator<E> implements Iterator<E> {
        private final Iterator<E> delegate;
        private final MutabilityOracle mutabilityOracle;

        MutatabilityAwareIterator(MutabilityOracle mutabilityOracle, Iterator<E> delegate) {
            this.mutabilityOracle = mutabilityOracle;
            this.delegate = delegate;
        }

        @Override // java.util.Iterator
        public boolean hasNext() {
            return this.delegate.hasNext();
        }

        @Override // java.util.Iterator
        public E next() {
            return this.delegate.next();
        }

        @Override // java.util.Iterator
        public void remove() {
            this.mutabilityOracle.ensureMutable();
            this.delegate.remove();
        }

        public boolean equals(Object obj) {
            return this.delegate.equals(obj);
        }

        public int hashCode() {
            return this.delegate.hashCode();
        }

        public String toString() {
            return this.delegate.toString();
        }
    }
}
