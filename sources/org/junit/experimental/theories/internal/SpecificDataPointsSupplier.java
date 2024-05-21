package org.junit.experimental.theories.internal;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.ParameterSignature;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.TestClass;
/* loaded from: classes3.dex */
public class SpecificDataPointsSupplier extends AllMembersSupplier {
    public SpecificDataPointsSupplier(TestClass testClass) {
        super(testClass);
    }

    /* JADX INFO: Access modifiers changed from: protected */
    @Override // org.junit.experimental.theories.internal.AllMembersSupplier
    public Collection<Field> getSingleDataPointFields(ParameterSignature sig) {
        Collection<Field> fields = super.getSingleDataPointFields(sig);
        String requestedName = ((FromDataPoints) sig.getAnnotation(FromDataPoints.class)).value();
        List<Field> fieldsWithMatchingNames = new ArrayList<>();
        for (Field field : fields) {
            String[] fieldNames = ((DataPoint) field.getAnnotation(DataPoint.class)).value();
            if (Arrays.asList(fieldNames).contains(requestedName)) {
                fieldsWithMatchingNames.add(field);
            }
        }
        return fieldsWithMatchingNames;
    }

    /* JADX INFO: Access modifiers changed from: protected */
    @Override // org.junit.experimental.theories.internal.AllMembersSupplier
    public Collection<Field> getDataPointsFields(ParameterSignature sig) {
        Collection<Field> fields = super.getDataPointsFields(sig);
        String requestedName = ((FromDataPoints) sig.getAnnotation(FromDataPoints.class)).value();
        List<Field> fieldsWithMatchingNames = new ArrayList<>();
        for (Field field : fields) {
            String[] fieldNames = ((DataPoints) field.getAnnotation(DataPoints.class)).value();
            if (Arrays.asList(fieldNames).contains(requestedName)) {
                fieldsWithMatchingNames.add(field);
            }
        }
        return fieldsWithMatchingNames;
    }

    /* JADX INFO: Access modifiers changed from: protected */
    @Override // org.junit.experimental.theories.internal.AllMembersSupplier
    public Collection<FrameworkMethod> getSingleDataPointMethods(ParameterSignature sig) {
        Collection<FrameworkMethod> methods = super.getSingleDataPointMethods(sig);
        String requestedName = ((FromDataPoints) sig.getAnnotation(FromDataPoints.class)).value();
        List<FrameworkMethod> methodsWithMatchingNames = new ArrayList<>();
        for (FrameworkMethod method : methods) {
            String[] methodNames = ((DataPoint) method.getAnnotation(DataPoint.class)).value();
            if (Arrays.asList(methodNames).contains(requestedName)) {
                methodsWithMatchingNames.add(method);
            }
        }
        return methodsWithMatchingNames;
    }

    /* JADX INFO: Access modifiers changed from: protected */
    @Override // org.junit.experimental.theories.internal.AllMembersSupplier
    public Collection<FrameworkMethod> getDataPointsMethods(ParameterSignature sig) {
        Collection<FrameworkMethod> methods = super.getDataPointsMethods(sig);
        String requestedName = ((FromDataPoints) sig.getAnnotation(FromDataPoints.class)).value();
        List<FrameworkMethod> methodsWithMatchingNames = new ArrayList<>();
        for (FrameworkMethod method : methods) {
            String[] methodNames = ((DataPoints) method.getAnnotation(DataPoints.class)).value();
            if (Arrays.asList(methodNames).contains(requestedName)) {
                methodsWithMatchingNames.add(method);
            }
        }
        return methodsWithMatchingNames;
    }
}
