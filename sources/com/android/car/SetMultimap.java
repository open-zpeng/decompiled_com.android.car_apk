package com.android.car;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
/* loaded from: classes3.dex */
public class SetMultimap<K, V> {
    private Map<K, Set<V>> mMap = new HashMap();

    public Set<V> get(K key) {
        return Collections.unmodifiableSet(this.mMap.getOrDefault(key, Collections.emptySet()));
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ Set lambda$put$0(Object k) {
        return new HashSet();
    }

    public boolean put(K key, V value) {
        return this.mMap.computeIfAbsent(key, new Function() { // from class: com.android.car.-$$Lambda$SetMultimap$OMBxrvtyKKubylfBxJSLiIJKNek
            @Override // java.util.function.Function
            public final Object apply(Object obj) {
                return SetMultimap.lambda$put$0(obj);
            }
        }).add(value);
    }

    public boolean containsEntry(K key, V value) {
        Set<V> set = this.mMap.get(key);
        return set != null && set.contains(value);
    }

    public boolean remove(K key, V value) {
        Set<V> set = this.mMap.get(key);
        if (set == null) {
            return false;
        }
        boolean removed = set.remove(value);
        if (set.isEmpty()) {
            this.mMap.remove(key);
        }
        return removed;
    }

    public void clear() {
        this.mMap.clear();
    }

    public Set<K> keySet() {
        return Collections.unmodifiableSet(this.mMap.keySet());
    }
}
