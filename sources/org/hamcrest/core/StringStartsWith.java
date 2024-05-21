package org.hamcrest.core;

import org.hamcrest.Matcher;
/* loaded from: classes3.dex */
public class StringStartsWith extends SubstringMatcher {
    public StringStartsWith(boolean ignoringCase, String substring) {
        super("starting with", ignoringCase, substring);
    }

    @Override // org.hamcrest.core.SubstringMatcher
    protected boolean evalSubstringOf(String s) {
        return converted(s).startsWith(converted(this.substring));
    }

    public static Matcher<String> startsWith(String prefix) {
        return new StringStartsWith(false, prefix);
    }

    public static Matcher<String> startsWithIgnoringCase(String prefix) {
        return new StringStartsWith(true, prefix);
    }
}
