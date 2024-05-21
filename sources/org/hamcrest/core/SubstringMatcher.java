package org.hamcrest.core;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
/* loaded from: classes3.dex */
public abstract class SubstringMatcher extends TypeSafeMatcher<String> {
    private final boolean ignoringCase;
    private final String relationship;
    protected final String substring;

    protected abstract boolean evalSubstringOf(String str);

    /* JADX INFO: Access modifiers changed from: protected */
    public SubstringMatcher(String relationship, boolean ignoringCase, String substring) {
        this.relationship = relationship;
        this.ignoringCase = ignoringCase;
        this.substring = substring;
    }

    @Override // org.hamcrest.TypeSafeMatcher
    public boolean matchesSafely(String item) {
        return evalSubstringOf(this.ignoringCase ? item.toLowerCase() : item);
    }

    @Override // org.hamcrest.TypeSafeMatcher
    public void describeMismatchSafely(String item, Description mismatchDescription) {
        mismatchDescription.appendText("was \"").appendText(item).appendText("\"");
    }

    @Override // org.hamcrest.SelfDescribing
    public void describeTo(Description description) {
        description.appendText("a string ").appendText(this.relationship).appendText(" ").appendValue(this.substring);
        if (this.ignoringCase) {
            description.appendText(" ignoring case");
        }
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public String converted(String arg) {
        return this.ignoringCase ? arg.toLowerCase() : arg;
    }
}
