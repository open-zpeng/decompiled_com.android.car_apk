package org.junit.runner.manipulation;

import java.util.Comparator;
import org.junit.runner.Description;
/* loaded from: classes3.dex */
public class Sorter implements Comparator<Description> {
    public static final Sorter NULL = new Sorter(new Comparator<Description>() { // from class: org.junit.runner.manipulation.Sorter.1
        @Override // java.util.Comparator
        public int compare(Description o1, Description o2) {
            return 0;
        }
    });
    private final Comparator<Description> comparator;

    public Sorter(Comparator<Description> comparator) {
        this.comparator = comparator;
    }

    public void apply(Object object) {
        if (object instanceof Sortable) {
            Sortable sortable = (Sortable) object;
            sortable.sort(this);
        }
    }

    @Override // java.util.Comparator
    public int compare(Description o1, Description o2) {
        return this.comparator.compare(o1, o2);
    }
}
