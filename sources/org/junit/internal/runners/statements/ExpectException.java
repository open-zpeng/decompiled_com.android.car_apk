package org.junit.internal.runners.statements;

import org.junit.internal.AssumptionViolatedException;
import org.junit.runners.model.Statement;
/* loaded from: classes3.dex */
public class ExpectException extends Statement {
    private final Class<? extends Throwable> expected;
    private final Statement next;

    public ExpectException(Statement next, Class<? extends Throwable> expected) {
        this.next = next;
        this.expected = expected;
    }

    @Override // org.junit.runners.model.Statement
    public void evaluate() throws Exception {
        boolean complete = false;
        try {
            this.next.evaluate();
            complete = true;
        } catch (AssumptionViolatedException e) {
            throw e;
        } catch (Throwable e2) {
            if (!this.expected.isAssignableFrom(e2.getClass())) {
                String message = "Unexpected exception, expected<" + this.expected.getName() + "> but was<" + e2.getClass().getName() + ">";
                throw new Exception(message, e2);
            }
        }
        if (complete) {
            throw new AssertionError("Expected exception: " + this.expected.getName());
        }
    }
}
