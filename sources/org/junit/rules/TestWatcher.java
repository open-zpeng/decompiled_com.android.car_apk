package org.junit.rules;

import java.util.ArrayList;
import java.util.List;
import org.junit.internal.AssumptionViolatedException;
import org.junit.runner.Description;
import org.junit.runners.model.MultipleFailureException;
import org.junit.runners.model.Statement;
/* loaded from: classes3.dex */
public abstract class TestWatcher implements TestRule {
    @Override // org.junit.rules.TestRule
    public Statement apply(final Statement base, final Description description) {
        return new Statement() { // from class: org.junit.rules.TestWatcher.1
            @Override // org.junit.runners.model.Statement
            public void evaluate() throws Throwable {
                List<Throwable> errors = new ArrayList<>();
                TestWatcher.this.startingQuietly(description, errors);
                try {
                    try {
                        base.evaluate();
                        TestWatcher.this.succeededQuietly(description, errors);
                    } finally {
                        TestWatcher.this.finishedQuietly(description, errors);
                    }
                } catch (AssumptionViolatedException e) {
                    errors.add(e);
                    TestWatcher.this.skippedQuietly(e, description, errors);
                    MultipleFailureException.assertEmpty(errors);
                } catch (Throwable e2) {
                    errors.add(e2);
                    TestWatcher.this.failedQuietly(e2, description, errors);
                    MultipleFailureException.assertEmpty(errors);
                }
                MultipleFailureException.assertEmpty(errors);
            }
        };
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void succeededQuietly(Description description, List<Throwable> errors) {
        try {
            succeeded(description);
        } catch (Throwable e) {
            errors.add(e);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void failedQuietly(Throwable e, Description description, List<Throwable> errors) {
        try {
            failed(e, description);
        } catch (Throwable e1) {
            errors.add(e1);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void skippedQuietly(AssumptionViolatedException e, Description description, List<Throwable> errors) {
        try {
            if (e instanceof org.junit.AssumptionViolatedException) {
                skipped((org.junit.AssumptionViolatedException) e, description);
            } else {
                skipped(e, description);
            }
        } catch (Throwable e1) {
            errors.add(e1);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void startingQuietly(Description description, List<Throwable> errors) {
        try {
            starting(description);
        } catch (Throwable e) {
            errors.add(e);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void finishedQuietly(Description description, List<Throwable> errors) {
        try {
            finished(description);
        } catch (Throwable e) {
            errors.add(e);
        }
    }

    protected void succeeded(Description description) {
    }

    protected void failed(Throwable e, Description description) {
    }

    protected void skipped(org.junit.AssumptionViolatedException e, Description description) {
        skipped((AssumptionViolatedException) e, description);
    }

    @Deprecated
    protected void skipped(AssumptionViolatedException e, Description description) {
    }

    protected void starting(Description description) {
    }

    protected void finished(Description description) {
    }
}
