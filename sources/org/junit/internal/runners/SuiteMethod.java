package org.junit.internal.runners;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import junit.framework.Test;
import junit.runner.BaseTestRunner;
/* loaded from: classes3.dex */
public class SuiteMethod extends JUnit38ClassRunner {
    public SuiteMethod(Class<?> klass) throws Throwable {
        super(testFromSuiteMethod(klass));
    }

    public static Test testFromSuiteMethod(Class<?> klass) throws Throwable {
        try {
            Method suiteMethod = klass.getMethod(BaseTestRunner.SUITE_METHODNAME, new Class[0]);
            if (!Modifier.isStatic(suiteMethod.getModifiers())) {
                throw new Exception(klass.getName() + ".suite() must be static");
            }
            Test suite = (Test) suiteMethod.invoke(null, new Object[0]);
            return suite;
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
