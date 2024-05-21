package com.android.car.condition;

import android.car.hardware.CarPropertyValue;
import android.car.hardware.condition.CarConditionInfo;
import android.util.SparseArray;
import java.util.List;
/* loaded from: classes3.dex */
public class CarConditionUtils {
    public static boolean isConditionLimitSatisfied(int propId, CarConditionStatus status, CarPropertyValue value) {
        CarConditionInfo info = status.getCarConditionInfo();
        SparseArray<List<? extends Number>> array = info.getLimitsArray();
        List limits = array.get(propId);
        if (limits != null) {
            Object target = value.getValue();
            int preLimitIndex = status.getLimitIndex(propId);
            int size = limits.size();
            int limitIndex = -1;
            if (target instanceof Float) {
                boolean isTargetEqualZero = ((Float) target).floatValue() == 0.0f;
                boolean isTargetOverZero = ((Float) target).floatValue() > 0.0f;
                limitIndex = isConditionLimitSatisfied(limits, preLimitIndex, size, isTargetEqualZero, isTargetOverZero, Float.valueOf(((Float) target).floatValue()));
            } else if (target instanceof Integer) {
                boolean isTargetEqualZero2 = ((Integer) target).intValue() == 0;
                boolean isTargetOverZero2 = ((Integer) target).intValue() > 0;
                limitIndex = isConditionLimitSatisfied(limits, preLimitIndex, size, isTargetEqualZero2, isTargetOverZero2, Integer.valueOf(((Integer) target).intValue()));
            } else if (target instanceof Long) {
                boolean isTargetEqualZero3 = ((Long) target).longValue() == 0;
                boolean isTargetOverZero3 = ((Long) target).longValue() > 0;
                limitIndex = isConditionLimitSatisfied(limits, preLimitIndex, size, isTargetEqualZero3, isTargetOverZero3, Long.valueOf(((Long) target).longValue()));
            } else if (target instanceof Double) {
                boolean isTargetEqualZero4 = ((Double) target).doubleValue() == 0.0d;
                boolean isTargetOverZero4 = ((Double) target).doubleValue() > 0.0d;
                limitIndex = isConditionLimitSatisfied(limits, preLimitIndex, size, isTargetEqualZero4, isTargetOverZero4, Double.valueOf(((Double) target).doubleValue()));
            }
            if (limitIndex != preLimitIndex && limitIndex != -1) {
                status.setLimitIndex(propId, limitIndex);
                return true;
            }
        }
        return false;
    }

    private static <T extends Number & Comparable<T>> int isConditionLimitSatisfied(List<T> limits, int preLimitIndex, int size, boolean isEqualZero, boolean isOverZero, T target) {
        int compareResult;
        int i = 0;
        if (preLimitIndex == size && isOverZero && ((Comparable) target).compareTo(limits.get(preLimitIndex - 1)) >= 0) {
            return preLimitIndex;
        }
        if (preLimitIndex == 0 && ((compareResult = ((Comparable) target).compareTo(limits.get(0))) < 0 || (compareResult == 0 && isEqualZero))) {
            return preLimitIndex;
        }
        if (preLimitIndex > 0 && preLimitIndex < size) {
            int lowCompare = ((Comparable) target).compareTo(limits.get(preLimitIndex - 1));
            int highCompare = ((Comparable) target).compareTo(limits.get(preLimitIndex));
            if (isOverZero && lowCompare >= 0 && highCompare < 0) {
                return preLimitIndex;
            }
            if (highCompare >= 0) {
                i = preLimitIndex - 1;
            }
            if (lowCompare < 0) {
                size = preLimitIndex;
            }
        }
        int middleIndex = ((i + size) - 1) / 2;
        if (((Comparable) target).compareTo(limits.get(middleIndex)) >= 0) {
            i = middleIndex;
        }
        int limitIndex = -1;
        while (i < size) {
            int lowCompare2 = ((Comparable) target).compareTo(limits.get(i));
            int highCompare2 = -1;
            if (i + 1 < size) {
                highCompare2 = ((Comparable) target).compareTo(limits.get(i + 1));
            }
            if (lowCompare2 < 0 || (lowCompare2 == 0 && isEqualZero)) {
                int limitIndex2 = i;
                return limitIndex2;
            } else if (highCompare2 >= 0) {
                limitIndex = i + 2;
                i += 2;
            } else {
                int limitIndex3 = i + 1;
                return limitIndex3;
            }
        }
        return limitIndex;
    }
}
