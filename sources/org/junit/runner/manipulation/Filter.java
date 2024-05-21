package org.junit.runner.manipulation;

import java.util.Iterator;
import org.junit.runner.Description;
/* loaded from: classes3.dex */
public abstract class Filter {
    public static final Filter ALL = new Filter() { // from class: org.junit.runner.manipulation.Filter.1
        @Override // org.junit.runner.manipulation.Filter
        public boolean shouldRun(Description description) {
            return true;
        }

        @Override // org.junit.runner.manipulation.Filter
        public String describe() {
            return "all tests";
        }

        @Override // org.junit.runner.manipulation.Filter
        public void apply(Object child) throws NoTestsRemainException {
        }

        @Override // org.junit.runner.manipulation.Filter
        public Filter intersect(Filter second) {
            return second;
        }
    };

    public abstract String describe();

    public abstract boolean shouldRun(Description description);

    public static Filter matchMethodDescription(final Description desiredDescription) {
        return new Filter() { // from class: org.junit.runner.manipulation.Filter.2
            @Override // org.junit.runner.manipulation.Filter
            public boolean shouldRun(Description description) {
                if (description.isTest()) {
                    return Description.this.equals(description);
                }
                Iterator<Description> it = description.getChildren().iterator();
                while (it.hasNext()) {
                    Description each = it.next();
                    if (shouldRun(each)) {
                        return true;
                    }
                }
                return false;
            }

            @Override // org.junit.runner.manipulation.Filter
            public String describe() {
                return String.format("Method %s", Description.this.getDisplayName());
            }
        };
    }

    public void apply(Object child) throws NoTestsRemainException {
        if (!(child instanceof Filterable)) {
            return;
        }
        Filterable filterable = (Filterable) child;
        filterable.filter(this);
    }

    public Filter intersect(final Filter second) {
        if (second == this || second == ALL) {
            return this;
        }
        return new Filter() { // from class: org.junit.runner.manipulation.Filter.3
            @Override // org.junit.runner.manipulation.Filter
            public boolean shouldRun(Description description) {
                return first.shouldRun(description) && second.shouldRun(description);
            }

            @Override // org.junit.runner.manipulation.Filter
            public String describe() {
                return first.describe() + " and " + second.describe();
            }
        };
    }
}
