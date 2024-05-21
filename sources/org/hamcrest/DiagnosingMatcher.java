package org.hamcrest;
/* loaded from: classes3.dex */
public abstract class DiagnosingMatcher<T> extends BaseMatcher<T> {
    protected abstract boolean matches(Object obj, Description description);

    @Override // org.hamcrest.Matcher
    public final boolean matches(Object item) {
        return matches(item, Description.NONE);
    }

    @Override // org.hamcrest.BaseMatcher, org.hamcrest.Matcher
    public final void describeMismatch(Object item, Description mismatchDescription) {
        matches(item, mismatchDescription);
    }
}
