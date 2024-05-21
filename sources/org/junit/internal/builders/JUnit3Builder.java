package org.junit.internal.builders;

import junit.framework.TestCase;
import org.junit.internal.runners.JUnit38ClassRunner;
import org.junit.runner.Runner;
import org.junit.runners.model.RunnerBuilder;
/* loaded from: classes3.dex */
public class JUnit3Builder extends RunnerBuilder {
    @Override // org.junit.runners.model.RunnerBuilder
    public Runner runnerForClass(Class<?> testClass) throws Throwable {
        if (isPre4Test(testClass)) {
            return new JUnit38ClassRunner(testClass);
        }
        return null;
    }

    boolean isPre4Test(Class<?> testClass) {
        return TestCase.class.isAssignableFrom(testClass);
    }
}
