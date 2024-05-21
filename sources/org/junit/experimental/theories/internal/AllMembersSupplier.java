package org.junit.experimental.theories.internal;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Assume;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.ParameterSignature;
import org.junit.experimental.theories.ParameterSupplier;
import org.junit.experimental.theories.PotentialAssignment;
import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.TestClass;
/* loaded from: classes3.dex */
public class AllMembersSupplier extends ParameterSupplier {
    private final TestClass clazz;

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes3.dex */
    public static class MethodParameterValue extends PotentialAssignment {
        private final FrameworkMethod method;

        private MethodParameterValue(FrameworkMethod dataPointMethod) {
            this.method = dataPointMethod;
        }

        @Override // org.junit.experimental.theories.PotentialAssignment
        public Object getValue() throws PotentialAssignment.CouldNotGenerateValueException {
            boolean z = false;
            try {
                return this.method.invokeExplosively(null, new Object[0]);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("unexpected: getMethods returned an inaccessible method");
            } catch (IllegalArgumentException e2) {
                throw new RuntimeException("unexpected: argument length is checked");
            } catch (Throwable throwable) {
                DataPoint annotation = (DataPoint) this.method.getAnnotation(DataPoint.class);
                Assume.assumeTrue((annotation == null || !AllMembersSupplier.isAssignableToAnyOf(annotation.ignoredExceptions(), throwable)) ? true : true);
                throw new PotentialAssignment.CouldNotGenerateValueException(throwable);
            }
        }

        @Override // org.junit.experimental.theories.PotentialAssignment
        public String getDescription() throws PotentialAssignment.CouldNotGenerateValueException {
            return this.method.getName();
        }
    }

    public AllMembersSupplier(TestClass type) {
        this.clazz = type;
    }

    @Override // org.junit.experimental.theories.ParameterSupplier
    public List<PotentialAssignment> getValueSources(ParameterSignature sig) throws Throwable {
        List<PotentialAssignment> list = new ArrayList<>();
        addSinglePointFields(sig, list);
        addMultiPointFields(sig, list);
        addSinglePointMethods(sig, list);
        addMultiPointMethods(sig, list);
        return list;
    }

    private void addMultiPointMethods(ParameterSignature sig, List<PotentialAssignment> list) throws Throwable {
        for (FrameworkMethod dataPointsMethod : getDataPointsMethods(sig)) {
            Class<?> returnType = dataPointsMethod.getReturnType();
            if ((returnType.isArray() && sig.canPotentiallyAcceptType(returnType.getComponentType())) || Iterable.class.isAssignableFrom(returnType)) {
                try {
                    addDataPointsValues(returnType, sig, dataPointsMethod.getName(), list, dataPointsMethod.invokeExplosively(null, new Object[0]));
                } catch (Throwable throwable) {
                    DataPoints annotation = (DataPoints) dataPointsMethod.getAnnotation(DataPoints.class);
                    if (annotation != null && isAssignableToAnyOf(annotation.ignoredExceptions(), throwable)) {
                        return;
                    }
                    throw throwable;
                }
            }
        }
    }

    private void addSinglePointMethods(ParameterSignature sig, List<PotentialAssignment> list) {
        for (FrameworkMethod dataPointMethod : getSingleDataPointMethods(sig)) {
            if (sig.canAcceptType(dataPointMethod.getType())) {
                list.add(new MethodParameterValue(dataPointMethod));
            }
        }
    }

    private void addMultiPointFields(ParameterSignature sig, List<PotentialAssignment> list) {
        for (Field field : getDataPointsFields(sig)) {
            Class<?> type = field.getType();
            addDataPointsValues(type, sig, field.getName(), list, getStaticFieldValue(field));
        }
    }

    private void addSinglePointFields(ParameterSignature sig, List<PotentialAssignment> list) {
        for (Field field : getSingleDataPointFields(sig)) {
            Object value = getStaticFieldValue(field);
            if (sig.canAcceptValue(value)) {
                list.add(PotentialAssignment.forValue(field.getName(), value));
            }
        }
    }

    private void addDataPointsValues(Class<?> type, ParameterSignature sig, String name, List<PotentialAssignment> list, Object value) {
        if (type.isArray()) {
            addArrayValues(sig, name, list, value);
        } else if (Iterable.class.isAssignableFrom(type)) {
            addIterableValues(sig, name, list, (Iterable) value);
        }
    }

    private void addArrayValues(ParameterSignature sig, String name, List<PotentialAssignment> list, Object array) {
        for (int i = 0; i < Array.getLength(array); i++) {
            Object value = Array.get(array, i);
            if (sig.canAcceptValue(value)) {
                list.add(PotentialAssignment.forValue(name + "[" + i + "]", value));
            }
        }
    }

    private void addIterableValues(ParameterSignature sig, String name, List<PotentialAssignment> list, Iterable<?> iterable) {
        int i = 0;
        for (Object value : iterable) {
            if (sig.canAcceptValue(value)) {
                list.add(PotentialAssignment.forValue(name + "[" + i + "]", value));
            }
            i++;
        }
    }

    private Object getStaticFieldValue(Field field) {
        try {
            return field.get(null);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("unexpected: getFields returned an inaccessible field");
        } catch (IllegalArgumentException e2) {
            throw new RuntimeException("unexpected: field from getClass doesn't exist on object");
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean isAssignableToAnyOf(Class<?>[] typeArray, Object target) {
        for (Class<?> type : typeArray) {
            if (type.isAssignableFrom(target.getClass())) {
                return true;
            }
        }
        return false;
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public Collection<FrameworkMethod> getDataPointsMethods(ParameterSignature sig) {
        return this.clazz.getAnnotatedMethods(DataPoints.class);
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public Collection<Field> getSingleDataPointFields(ParameterSignature sig) {
        List<FrameworkField> fields = this.clazz.getAnnotatedFields(DataPoint.class);
        Collection<Field> validFields = new ArrayList<>();
        for (FrameworkField frameworkField : fields) {
            validFields.add(frameworkField.getField());
        }
        return validFields;
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public Collection<Field> getDataPointsFields(ParameterSignature sig) {
        List<FrameworkField> fields = this.clazz.getAnnotatedFields(DataPoints.class);
        Collection<Field> validFields = new ArrayList<>();
        for (FrameworkField frameworkField : fields) {
            validFields.add(frameworkField.getField());
        }
        return validFields;
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public Collection<FrameworkMethod> getSingleDataPointMethods(ParameterSignature sig) {
        return this.clazz.getAnnotatedMethods(DataPoint.class);
    }
}
