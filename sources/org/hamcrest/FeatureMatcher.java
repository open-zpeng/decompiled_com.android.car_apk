package org.hamcrest;

import org.hamcrest.internal.ReflectiveTypeFinder;
/* loaded from: classes3.dex */
public abstract class FeatureMatcher<T, U> extends TypeSafeDiagnosingMatcher<T> {
    private static final ReflectiveTypeFinder TYPE_FINDER = new ReflectiveTypeFinder("featureValueOf", 1, 0);
    private final String featureDescription;
    private final String featureName;
    private final Matcher<? super U> subMatcher;

    protected abstract U featureValueOf(T t);

    public FeatureMatcher(Matcher<? super U> subMatcher, String featureDescription, String featureName) {
        super(TYPE_FINDER);
        this.subMatcher = subMatcher;
        this.featureDescription = featureDescription;
        this.featureName = featureName;
    }

    @Override // org.hamcrest.TypeSafeDiagnosingMatcher
    protected boolean matchesSafely(T actual, Description mismatch) {
        U featureValue = featureValueOf(actual);
        if (!this.subMatcher.matches(featureValue)) {
            mismatch.appendText(this.featureName).appendText(" ");
            this.subMatcher.describeMismatch(featureValue, mismatch);
            return false;
        }
        return true;
    }

    @Override // org.hamcrest.SelfDescribing
    public final void describeTo(Description description) {
        description.appendText(this.featureDescription).appendText(" ").appendDescriptionOf(this.subMatcher);
    }
}
