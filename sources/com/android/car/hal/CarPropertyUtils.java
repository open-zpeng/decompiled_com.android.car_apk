package com.android.car.hal;

import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.hardware.automotive.vehicle.V2_0.VehicleArea;
import android.hardware.automotive.vehicle.V2_0.VehicleAreaConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehiclePropertyType;
import com.android.car.CarServiceUtils;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
/* loaded from: classes3.dex */
final class CarPropertyUtils {
    private CarPropertyUtils() {
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static CarPropertyValue<?> toCarPropertyValue(VehiclePropValue halValue, int propertyId) {
        Class<?> clazz = getJavaClass(halValue.prop & VehiclePropertyType.MASK);
        int areaId = halValue.areaId;
        int status = halValue.status;
        long timestamp = halValue.timestamp;
        VehiclePropValue.RawValue v = halValue.value;
        if (Boolean.class == clazz) {
            return new CarPropertyValue<>(propertyId, areaId, status, timestamp, Boolean.valueOf(v.int32Values.get(0).intValue() == 1));
        } else if (Float.class == clazz) {
            return new CarPropertyValue<>(propertyId, areaId, status, timestamp, v.floatValues.get(0));
        } else {
            if (Integer.class == clazz) {
                return new CarPropertyValue<>(propertyId, areaId, status, timestamp, v.int32Values.get(0));
            }
            if (Long.class == clazz) {
                return new CarPropertyValue<>(propertyId, areaId, status, timestamp, v.int64Values.get(0));
            }
            if (Float[].class == clazz) {
                Float[] values = new Float[v.floatValues.size()];
                for (int i = 0; i < values.length; i++) {
                    values[i] = v.floatValues.get(i);
                }
                return new CarPropertyValue<>(propertyId, areaId, status, timestamp, values);
            } else if (Integer[].class == clazz) {
                Integer[] values2 = new Integer[v.int32Values.size()];
                for (int i2 = 0; i2 < values2.length; i2++) {
                    values2[i2] = v.int32Values.get(i2);
                }
                return new CarPropertyValue<>(propertyId, areaId, status, timestamp, values2);
            } else if (Long[].class == clazz) {
                Long[] values3 = new Long[v.int64Values.size()];
                for (int i3 = 0; i3 < values3.length; i3++) {
                    values3[i3] = v.int64Values.get(i3);
                }
                return new CarPropertyValue<>(propertyId, areaId, status, timestamp, values3);
            } else if (String.class == clazz) {
                return new CarPropertyValue<>(propertyId, areaId, status, timestamp, v.stringValue);
            } else {
                if (byte[].class == clazz) {
                    byte[] halData = CarServiceUtils.toByteArray(v.bytes);
                    return new CarPropertyValue<>(propertyId, areaId, status, timestamp, halData);
                } else if (Double.class == clazz) {
                    return new CarPropertyValue<>(propertyId, areaId, status, timestamp, v.doubleValues.get(0));
                } else {
                    if (Double[].class == clazz) {
                        Double[] values4 = new Double[v.doubleValues.size()];
                        for (int i4 = 0; i4 < values4.length; i4++) {
                            values4[i4] = v.doubleValues.get(i4);
                        }
                        return new CarPropertyValue<>(propertyId, areaId, status, timestamp, values4);
                    }
                    Object[] values5 = getRawValueList(clazz, v).toArray();
                    return new CarPropertyValue<>(propertyId, areaId, status, timestamp, values5.length == 1 ? values5[0] : values5);
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static VehiclePropValue toVehiclePropValue(CarPropertyValue carProp, int halPropId) {
        VehiclePropValue vehicleProp = new VehiclePropValue();
        vehicleProp.prop = halPropId;
        vehicleProp.areaId = carProp.getAreaId();
        vehicleProp.txPid = carProp.getTxPid();
        VehiclePropValue.RawValue v = vehicleProp.value;
        Object o = carProp.getValue();
        if (o instanceof Boolean) {
            v.int32Values.add(Integer.valueOf(((Boolean) o).booleanValue() ? 1 : 0));
        } else {
            int i = 0;
            if (o instanceof Boolean[]) {
                Boolean[] boolArr = (Boolean[]) o;
                int length = boolArr.length;
                while (i < length) {
                    Boolean b = boolArr[i];
                    v.int32Values.add(Integer.valueOf(b.booleanValue() ? 1 : 0));
                    i++;
                }
            } else if (o instanceof Integer) {
                v.int32Values.add((Integer) o);
            } else if (o instanceof Integer[]) {
                Collections.addAll(v.int32Values, (Integer[]) o);
            } else if (o instanceof Float) {
                v.floatValues.add((Float) o);
            } else if (o instanceof Float[]) {
                Collections.addAll(v.floatValues, (Float[]) o);
            } else if (o instanceof Long) {
                v.int64Values.add((Long) o);
            } else if (o instanceof Long[]) {
                Collections.addAll(v.int64Values, (Long[]) o);
            } else if (o instanceof String) {
                v.stringValue = (String) o;
            } else if (o instanceof byte[]) {
                byte[] bArr = (byte[]) o;
                int length2 = bArr.length;
                while (i < length2) {
                    byte b2 = bArr[i];
                    v.bytes.add(Byte.valueOf(b2));
                    i++;
                }
            } else if (o instanceof int[]) {
                int[] iArr = (int[]) o;
                int length3 = iArr.length;
                while (i < length3) {
                    int b3 = iArr[i];
                    v.int32Values.add(Integer.valueOf(b3));
                    i++;
                }
            } else if (o instanceof float[]) {
                float[] fArr = (float[]) o;
                int length4 = fArr.length;
                while (i < length4) {
                    float b4 = fArr[i];
                    v.floatValues.add(Float.valueOf(b4));
                    i++;
                }
            } else if (o instanceof long[]) {
                long[] jArr = (long[]) o;
                int length5 = jArr.length;
                while (i < length5) {
                    long b5 = jArr[i];
                    v.int64Values.add(Long.valueOf(b5));
                    i++;
                }
            } else if (o instanceof Double) {
                v.doubleValues.add((Double) o);
            } else if (o instanceof Double[]) {
                Collections.addAll(v.doubleValues, (Double[]) o);
            } else if (o instanceof double[]) {
                double[] dArr = (double[]) o;
                int length6 = dArr.length;
                while (i < length6) {
                    double b6 = dArr[i];
                    v.doubleValues.add(Double.valueOf(b6));
                    i++;
                }
            } else {
                throw new IllegalArgumentException("Unexpected type in: " + carProp);
            }
        }
        return vehicleProp;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static CarPropertyConfig<?> toCarPropertyConfig(VehiclePropConfig p, int propertyId) {
        int areaType = getVehicleAreaType(p.prop & VehicleArea.MASK);
        int[] areas = new int[p.areaConfigs.size()];
        for (int i = 0; i < p.areaConfigs.size(); i++) {
            areas[i] = p.areaConfigs.get(i).areaId;
        }
        int i2 = p.prop;
        Class<?> clazz = getJavaClass(i2 & VehiclePropertyType.MASK);
        if (p.areaConfigs.isEmpty()) {
            return CarPropertyConfig.newBuilder(clazz, propertyId, areaType, 1).addAreas(areas).setAccess(p.access).setChangeMode(p.changeMode).setConfigArray(p.configArray).setConfigString(p.configString).setMaxSampleRate(p.maxSampleRate).setMinSampleRate(p.minSampleRate).build();
        }
        CarPropertyConfig.Builder builder = CarPropertyConfig.newBuilder(clazz, propertyId, areaType, p.areaConfigs.size()).setAccess(p.access).setChangeMode(p.changeMode).setConfigArray(p.configArray).setConfigString(p.configString).setMaxSampleRate(p.maxSampleRate).setMinSampleRate(p.minSampleRate);
        Iterator<VehicleAreaConfig> it = p.areaConfigs.iterator();
        while (it.hasNext()) {
            VehicleAreaConfig area = it.next();
            if (classMatched(Integer.class, clazz)) {
                builder.addAreaConfig(area.areaId, Integer.valueOf(area.minInt32Value), Integer.valueOf(area.maxInt32Value));
            } else if (classMatched(Float.class, clazz)) {
                builder.addAreaConfig(area.areaId, Float.valueOf(area.minFloatValue), Float.valueOf(area.maxFloatValue));
            } else if (classMatched(Long.class, clazz)) {
                builder.addAreaConfig(area.areaId, Long.valueOf(area.minInt64Value), Long.valueOf(area.maxInt64Value));
            } else if (classMatched(Boolean.class, clazz) || classMatched(Float[].class, clazz) || classMatched(Integer[].class, clazz) || classMatched(Long[].class, clazz) || classMatched(String.class, clazz) || classMatched(byte[].class, clazz) || classMatched(Object.class, clazz)) {
                builder.addArea(area.areaId);
            } else {
                throw new IllegalArgumentException("Unexpected type: " + clazz);
            }
        }
        return builder.build();
    }

    private static int getVehicleAreaType(int halArea) {
        if (halArea != 16777216) {
            if (halArea != 50331648) {
                if (halArea != 67108864) {
                    if (halArea != 83886080) {
                        if (halArea != 100663296) {
                            if (halArea == 117440512) {
                                return 6;
                            }
                            throw new RuntimeException("Unsupported area type " + halArea);
                        }
                        return 4;
                    }
                    return 3;
                }
                return 5;
            }
            return 2;
        }
        return 0;
    }

    private static Class<?> getJavaClass(int halType) {
        switch (halType) {
            case 1048576:
                return String.class;
            case 2097152:
                return Boolean.class;
            case 4194304:
                return Integer.class;
            case VehiclePropertyType.INT32_VEC /* 4259840 */:
                return Integer[].class;
            case VehiclePropertyType.INT64 /* 5242880 */:
                return Long.class;
            case VehiclePropertyType.INT64_VEC /* 5308416 */:
                return Long[].class;
            case VehiclePropertyType.FLOAT /* 6291456 */:
                return Float.class;
            case VehiclePropertyType.FLOAT_VEC /* 6356992 */:
                return Float[].class;
            case VehiclePropertyType.BYTES /* 7340032 */:
                return byte[].class;
            case 8388608:
                return Double.class;
            case VehiclePropertyType.DOUBLE_VEC /* 8454144 */:
                return Double[].class;
            case VehiclePropertyType.MIXED /* 14680064 */:
                return Object.class;
            default:
                throw new IllegalArgumentException("Unexpected type: " + Integer.toHexString(halType));
        }
    }

    private static List getRawValueList(Class<?> clazz, VehiclePropValue.RawValue value) {
        if (classMatched(Float.class, clazz) || classMatched(Float[].class, clazz)) {
            return value.floatValues;
        }
        if (classMatched(Integer.class, clazz) || classMatched(Integer[].class, clazz)) {
            return value.int32Values;
        }
        if (classMatched(Long.class, clazz) || classMatched(Long[].class, clazz)) {
            return value.int64Values;
        }
        throw new IllegalArgumentException("Unexpected type: " + clazz);
    }

    private static boolean classMatched(Class<?> class1, Class<?> class2) {
        return class1 == class2 || class1.getComponentType() == class2;
    }
}
