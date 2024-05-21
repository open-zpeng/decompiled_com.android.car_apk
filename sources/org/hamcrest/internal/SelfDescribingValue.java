package org.hamcrest.internal;

import org.hamcrest.Description;
import org.hamcrest.SelfDescribing;
/* loaded from: classes3.dex */
public class SelfDescribingValue<T> implements SelfDescribing {
    private T value;

    public SelfDescribingValue(T value) {
        this.value = value;
    }

    @Override // org.hamcrest.SelfDescribing
    public void describeTo(Description description) {
        description.appendValue(this.value);
    }
}
