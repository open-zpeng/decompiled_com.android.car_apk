package org.junit.experimental.theories.internal;

import java.util.ArrayList;
import java.util.List;
import org.junit.experimental.theories.ParameterSignature;
import org.junit.experimental.theories.ParameterSupplier;
import org.junit.experimental.theories.PotentialAssignment;
/* loaded from: classes3.dex */
public class EnumSupplier extends ParameterSupplier {
    private Class<?> enumType;

    public EnumSupplier(Class<?> enumType) {
        this.enumType = enumType;
    }

    /* JADX WARN: Multi-variable type inference failed */
    @Override // org.junit.experimental.theories.ParameterSupplier
    public List<PotentialAssignment> getValueSources(ParameterSignature sig) {
        Object[] enumValues = this.enumType.getEnumConstants();
        List<PotentialAssignment> assignments = new ArrayList<>();
        for (Object value : enumValues) {
            assignments.add(PotentialAssignment.forValue(value.toString(), value));
        }
        return assignments;
    }
}
