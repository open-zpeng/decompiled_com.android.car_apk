package org.junit.runners;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.runner.Runner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.TestClass;
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParametersFactory;
import org.junit.runners.parameterized.ParametersRunnerFactory;
import org.junit.runners.parameterized.TestWithParameters;
/* loaded from: classes3.dex */
public class Parameterized extends Suite {
    private static final ParametersRunnerFactory DEFAULT_FACTORY = new BlockJUnit4ClassRunnerWithParametersFactory();
    private static final List<Runner> NO_RUNNERS = Collections.emptyList();
    private final List<Runner> runners;

    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    /* loaded from: classes3.dex */
    public @interface Parameter {
        int value() default 0;
    }

    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    /* loaded from: classes3.dex */
    public @interface Parameters {
        String name() default "{index}";
    }

    @Target({ElementType.TYPE})
    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    /* loaded from: classes3.dex */
    public @interface UseParametersRunnerFactory {
        Class<? extends ParametersRunnerFactory> value() default BlockJUnit4ClassRunnerWithParametersFactory.class;
    }

    public Parameterized(Class<?> klass) throws Throwable {
        super(klass, NO_RUNNERS);
        ParametersRunnerFactory runnerFactory = getParametersRunnerFactory(klass);
        Parameters parameters = (Parameters) getParametersMethod().getAnnotation(Parameters.class);
        this.runners = Collections.unmodifiableList(createRunnersForParameters(allParameters(), parameters.name(), runnerFactory));
    }

    private ParametersRunnerFactory getParametersRunnerFactory(Class<?> klass) throws InstantiationException, IllegalAccessException {
        UseParametersRunnerFactory annotation = (UseParametersRunnerFactory) klass.getAnnotation(UseParametersRunnerFactory.class);
        if (annotation == null) {
            return DEFAULT_FACTORY;
        }
        Class<? extends ParametersRunnerFactory> factoryClass = annotation.value();
        return factoryClass.newInstance();
    }

    @Override // org.junit.runners.Suite, org.junit.runners.ParentRunner
    protected List<Runner> getChildren() {
        return this.runners;
    }

    private TestWithParameters createTestWithNotNormalizedParameters(String pattern, int index, Object parametersOrSingleParameter) {
        Object[] parameters = parametersOrSingleParameter instanceof Object[] ? (Object[]) parametersOrSingleParameter : new Object[]{parametersOrSingleParameter};
        return createTestWithParameters(getTestClass(), pattern, index, parameters);
    }

    private Iterable<Object> allParameters() throws Throwable {
        Object parameters = getParametersMethod().invokeExplosively(null, new Object[0]);
        if (parameters instanceof Iterable) {
            return (Iterable) parameters;
        }
        if (parameters instanceof Object[]) {
            return Arrays.asList((Object[]) parameters);
        }
        throw parametersMethodReturnedWrongType();
    }

    private FrameworkMethod getParametersMethod() throws Exception {
        List<FrameworkMethod> methods = getTestClass().getAnnotatedMethods(Parameters.class);
        for (FrameworkMethod each : methods) {
            if (each.isStatic() && each.isPublic()) {
                return each;
            }
        }
        throw new Exception("No public static parameters method on class " + getTestClass().getName());
    }

    private List<Runner> createRunnersForParameters(Iterable<Object> allParameters, String namePattern, ParametersRunnerFactory runnerFactory) throws InitializationError, Exception {
        try {
            List<TestWithParameters> tests = createTestsForParameters(allParameters, namePattern);
            List<Runner> runners = new ArrayList<>();
            for (TestWithParameters test : tests) {
                runners.add(runnerFactory.createRunnerForTestWithParameters(test));
            }
            return runners;
        } catch (ClassCastException e) {
            throw parametersMethodReturnedWrongType();
        }
    }

    private List<TestWithParameters> createTestsForParameters(Iterable<Object> allParameters, String namePattern) throws Exception {
        int i = 0;
        List<TestWithParameters> children = new ArrayList<>();
        for (Object parametersOfSingleTest : allParameters) {
            children.add(createTestWithNotNormalizedParameters(namePattern, i, parametersOfSingleTest));
            i++;
        }
        return children;
    }

    private Exception parametersMethodReturnedWrongType() throws Exception {
        String className = getTestClass().getName();
        String methodName = getParametersMethod().getName();
        String message = MessageFormat.format("{0}.{1}() must return an Iterable of arrays.", className, methodName);
        return new Exception(message);
    }

    private static TestWithParameters createTestWithParameters(TestClass testClass, String pattern, int index, Object[] parameters) {
        String finalPattern = pattern.replaceAll("\\{index\\}", Integer.toString(index));
        String name = MessageFormat.format(finalPattern, parameters);
        return new TestWithParameters("[" + name + "]", testClass, Arrays.asList(parameters));
    }
}
