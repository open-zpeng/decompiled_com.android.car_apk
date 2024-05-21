package org.hamcrest.core;

import org.hamcrest.Matcher;
/* loaded from: classes3.dex */
public class StringContains extends SubstringMatcher {
    public StringContains(boolean ignoringCase, String substring) {
        super("containing", ignoringCase, substring);
    }

    @Override // org.hamcrest.core.SubstringMatcher
    protected boolean evalSubstringOf(String s) {
        return converted(s).contains(converted(this.substring));
    }

    public static Matcher<String> containsString(String substring) {
        return new StringContains(false, substring);
    }

    public static Matcher<String> containsStringIgnoringCase(String substring) {
        return new StringContains(true, substring);
    }
}
