package com.android.car;

import android.car.hardware.CarSensorEvent;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import java.util.List;
/* loaded from: classes3.dex */
public class CarSensorEventFactory {
    public static CarSensorEvent createBooleanEvent(int sensorType, long timestamp, boolean value) {
        CarSensorEvent event = new CarSensorEvent(sensorType, timestamp, 0, 1, 0);
        event.intValues[0] = value ? 1 : 0;
        return event;
    }

    public static CarSensorEvent createIntEvent(int sensorType, long timestamp, int value) {
        CarSensorEvent event = new CarSensorEvent(sensorType, timestamp, 0, 1, 0);
        event.intValues[0] = value;
        return event;
    }

    public static CarSensorEvent createInt64VecEvent(int sensorType, long timestamp, List<Long> value) {
        CarSensorEvent event = new CarSensorEvent(sensorType, timestamp, 0, 0, value.size());
        for (int i = 0; i < value.size(); i++) {
            event.longValues[i] = value.get(i).longValue();
        }
        return event;
    }

    public static CarSensorEvent createFloatEvent(int sensorType, long timestamp, float value) {
        CarSensorEvent event = new CarSensorEvent(sensorType, timestamp, 1, 0, 0);
        event.floatValues[0] = value;
        return event;
    }

    public static CarSensorEvent createMixedEvent(int sensorType, long timestamp, VehiclePropValue v) {
        int numFloats = v.value.floatValues.size();
        int numInts = v.value.int32Values.size();
        int numLongs = v.value.int64Values.size();
        CarSensorEvent event = new CarSensorEvent(sensorType, timestamp, numFloats, numInts, numLongs);
        for (int i = 0; i < numFloats; i++) {
            event.floatValues[i] = v.value.floatValues.get(i).floatValue();
        }
        for (int i2 = 0; i2 < numInts; i2++) {
            event.intValues[i2] = v.value.int32Values.get(i2).intValue();
        }
        for (int i3 = 0; i3 < numLongs; i3++) {
            event.longValues[i3] = v.value.int64Values.get(i3).longValue();
        }
        return event;
    }

    public static void returnToPool(CarSensorEvent event) {
    }
}
