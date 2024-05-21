package org.junit.internal.runners.statements;

import java.util.ArrayList;
import java.util.List;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.MultipleFailureException;
import org.junit.runners.model.Statement;
/* loaded from: classes3.dex */
public class RunAfters extends Statement {
    private final List<FrameworkMethod> afters;
    private final Statement next;
    private final Object target;

    public RunAfters(Statement next, List<FrameworkMethod> afters, Object target) {
        this.next = next;
        this.afters = afters;
        this.target = target;
    }

    @Override // org.junit.runners.model.Statement
    public void evaluate() throws Throwable {
        List<Throwable> errors = new ArrayList<>();
        try {
            this.next.evaluate();
            for (FrameworkMethod each : this.afters) {
                try {
                    each.invokeExplosively(this.target, new Object[0]);
                } catch (Throwable e) {
                    errors.add(e);
                }
            }
        } catch (Throwable e2) {
            try {
                errors.add(e2);
                for (FrameworkMethod each2 : this.afters) {
                    try {
                        each2.invokeExplosively(this.target, new Object[0]);
                    } catch (Throwable e3) {
                        errors.add(e3);
                    }
                }
            } catch (Throwable th) {
                for (FrameworkMethod each3 : this.afters) {
                    try {
                        each3.invokeExplosively(this.target, new Object[0]);
                    } catch (Throwable e4) {
                        errors.add(e4);
                    }
                }
                throw th;
            }
        }
        MultipleFailureException.assertEmpty(errors);
    }
}
