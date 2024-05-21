package org.hamcrest.core;

import org.hamcrest.Description;
import org.hamcrest.DiagnosingMatcher;
import org.hamcrest.Matcher;
/* loaded from: classes3.dex */
public class IsInstanceOf extends DiagnosingMatcher<Object> {
    private final Class<?> expectedClass;
    private final Class<?> matchableClass;

    public IsInstanceOf(Class<?> expectedClass) {
        this.expectedClass = expectedClass;
        this.matchableClass = matchableClass(expectedClass);
    }

    private static Class<?> matchableClass(Class<?> expectedClass) {
        return Boolean.TYPE.equals(expectedClass) ? Boolean.class : Byte.TYPE.equals(expectedClass) ? Byte.class : Character.TYPE.equals(expectedClass) ? Character.class : Double.TYPE.equals(expectedClass) ? Double.class : Float.TYPE.equals(expectedClass) ? Float.class : Integer.TYPE.equals(expectedClass) ? Integer.class : Long.TYPE.equals(expectedClass) ? Long.class : Short.TYPE.equals(expectedClass) ? Short.class : expectedClass;
    }

    @Override // org.hamcrest.DiagnosingMatcher
    protected boolean matches(Object item, Description mismatch) {
        if (item == null) {
            mismatch.appendText("null");
            return false;
        } else if (!this.matchableClass.isInstance(item)) {
            Description appendValue = mismatch.appendValue(item);
            appendValue.appendText(" is a " + item.getClass().getName());
            return false;
        } else {
            return true;
        }
    }

    @Override // org.hamcrest.SelfDescribing
    public void describeTo(Description description) {
        description.appendText("an instance of ").appendText(this.expectedClass.getName());
    }

    public static <T> Matcher<T> instanceOf(Class<?> type) {
        return new IsInstanceOf(type);
    }

    public static <T> Matcher<T> any(Class<T> type) {
        return new IsInstanceOf(type);
    }
}
