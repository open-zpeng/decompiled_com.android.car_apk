package com.android.car.vehiclehal;

import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.os.SystemClock;
/* loaded from: classes3.dex */
public class VehiclePropValueBuilder {
    private final VehiclePropValue mPropValue;

    public static VehiclePropValueBuilder newBuilder(int propId) {
        return new VehiclePropValueBuilder(propId);
    }

    public static VehiclePropValueBuilder newBuilder(VehiclePropValue propValue) {
        return new VehiclePropValueBuilder(propValue);
    }

    private VehiclePropValueBuilder(int propId) {
        this.mPropValue = new VehiclePropValue();
        this.mPropValue.prop = propId;
    }

    private VehiclePropValueBuilder(VehiclePropValue propValue) {
        this.mPropValue = clone(propValue);
    }

    private VehiclePropValue clone(VehiclePropValue propValue) {
        VehiclePropValue newValue = new VehiclePropValue();
        newValue.prop = propValue.prop;
        newValue.areaId = propValue.areaId;
        newValue.timestamp = propValue.timestamp;
        newValue.value.stringValue = propValue.value.stringValue;
        newValue.value.int32Values.addAll(propValue.value.int32Values);
        newValue.value.floatValues.addAll(propValue.value.floatValues);
        newValue.value.int64Values.addAll(propValue.value.int64Values);
        newValue.value.bytes.addAll(propValue.value.bytes);
        return newValue;
    }

    public VehiclePropValueBuilder setAreaId(int areaId) {
        this.mPropValue.areaId = areaId;
        return this;
    }

    public VehiclePropValueBuilder setTimestamp(long timestamp) {
        this.mPropValue.timestamp = timestamp;
        return this;
    }

    public VehiclePropValueBuilder setTimestamp() {
        this.mPropValue.timestamp = SystemClock.elapsedRealtimeNanos();
        return this;
    }

    public VehiclePropValueBuilder addIntValue(int... values) {
        for (int val : values) {
            this.mPropValue.value.int32Values.add(Integer.valueOf(val));
        }
        return this;
    }

    public VehiclePropValueBuilder addFloatValue(float... values) {
        for (float val : values) {
            this.mPropValue.value.floatValues.add(Float.valueOf(val));
        }
        return this;
    }

    public VehiclePropValueBuilder addByteValue(byte... values) {
        for (byte val : values) {
            this.mPropValue.value.bytes.add(Byte.valueOf(val));
        }
        return this;
    }

    public VehiclePropValueBuilder setInt64Value(long... values) {
        for (long val : values) {
            this.mPropValue.value.int64Values.add(Long.valueOf(val));
        }
        return this;
    }

    public VehiclePropValueBuilder setBooleanValue(boolean value) {
        this.mPropValue.value.int32Values.clear();
        this.mPropValue.value.int32Values.add(Integer.valueOf(value ? 1 : 0));
        return this;
    }

    public VehiclePropValueBuilder setStringValue(String val) {
        this.mPropValue.value.stringValue = val;
        return this;
    }

    public VehiclePropValue build() {
        return clone(this.mPropValue);
    }
}
