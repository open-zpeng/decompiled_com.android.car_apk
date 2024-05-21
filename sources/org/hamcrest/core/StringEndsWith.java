package org.hamcrest.core;

import org.hamcrest.Matcher;
/* loaded from: classes3.dex */
public class StringEndsWith extends SubstringMatcher {
    public StringEndsWith(boolean ignoringCase, String substring) {
        super("ending with", ignoringCase, substring);
    }

    @Override // org.hamcrest.core.SubstringMatcher
    protected boolean evalSubstringOf(String s) {
        return converted(s).endsWith(converted(this.substring));
    }

    public static Matcher<String> endsWith(String suffix) {
        return new StringEndsWith(false, suffix);
    }

    public static Matcher<String> endsWithIgnoringCase(String suffix) {
        return new StringEndsWith(true, suffix);
    }
}
