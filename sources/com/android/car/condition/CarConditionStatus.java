package com.android.car.condition;

import android.car.XpDebugLog;
import android.car.hardware.condition.CarConditionInfo;
import android.util.SparseIntArray;
/* loaded from: classes3.dex */
public class CarConditionStatus {
    private final CarConditionInfo carConditionInfo;
    private SparseIntArray limitIndex = new SparseIntArray();
    private final String processName;

    private CarConditionStatus(CarConditionInfo info, String name) {
        this.carConditionInfo = info;
        this.processName = name;
    }

    public static CarConditionStatus createFromCarConditionInfo(CarConditionInfo conditionInfo, String processName) {
        return new CarConditionStatus(conditionInfo, processName);
    }

    public CarConditionInfo getCarConditionInfo() {
        return this.carConditionInfo;
    }

    public int getLimitIndex(int propId) {
        return this.limitIndex.get(propId, -1);
    }

    public void setLimitIndex(int propId, int indexLimit) {
        this.limitIndex.put(propId, indexLimit);
    }

    public String getProcessName() {
        return this.processName;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("CarConditionStatus{");
        builder.append(this.carConditionInfo);
        int size = this.limitIndex.size();
        for (int i = 0; i < size; i++) {
            int key = this.limitIndex.keyAt(i);
            builder.append("\t\t");
            builder.append(XpDebugLog.getPropertyName(key));
            builder.append(" limitIndex: ");
            builder.append(this.limitIndex.get(key));
            builder.append("\n");
        }
        builder.append("      \t}");
        return builder.toString();
    }
}
