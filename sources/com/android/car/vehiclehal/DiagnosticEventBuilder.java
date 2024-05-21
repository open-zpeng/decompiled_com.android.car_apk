package com.android.car.vehiclehal;

import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.util.SparseArray;
import java.util.BitSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;
/* loaded from: classes3.dex */
public class DiagnosticEventBuilder {
    private final BitSet mBitmask;
    private String mDtc;
    private final DefaultedArray<Float> mFloatValues;
    private final DefaultedArray<Integer> mIntValues;
    private final int mNumIntSensors;
    private final int mPropertyId;

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes3.dex */
    public class DefaultedArray<T> implements Iterable<T> {
        private final T mDefaultValue;
        private final SparseArray<T> mElements = new SparseArray<>();
        private final int mSize;

        DefaultedArray(int size, T defaultValue) {
            this.mSize = size;
            this.mDefaultValue = defaultValue;
        }

        private int checkIndex(int index) {
            if (index < 0 || index >= this.mSize) {
                throw new IndexOutOfBoundsException(String.format("Index: %d, Size: %d", Integer.valueOf(index), Integer.valueOf(this.mSize)));
            }
            return index;
        }

        DefaultedArray<T> set(int index, T element) {
            checkIndex(index);
            this.mElements.put(index, element);
            return this;
        }

        T get(int index) {
            checkIndex(index);
            return this.mElements.get(index, this.mDefaultValue);
        }

        int size() {
            return this.mSize;
        }

        void clear() {
            this.mElements.clear();
        }

        @Override // java.lang.Iterable
        public Iterator<T> iterator() {
            return new Iterator<T>() { // from class: com.android.car.vehiclehal.DiagnosticEventBuilder.DefaultedArray.1
                private int mIndex = 0;

                @Override // java.util.Iterator
                public boolean hasNext() {
                    int i = this.mIndex;
                    return i >= 0 && i < DefaultedArray.this.mSize;
                }

                @Override // java.util.Iterator
                public T next() {
                    int index = this.mIndex;
                    this.mIndex = index + 1;
                    return (T) DefaultedArray.this.get(index);
                }
            };
        }
    }

    public DiagnosticEventBuilder(VehiclePropConfig propConfig) {
        this(propConfig.prop, propConfig.configArray.get(0).intValue(), propConfig.configArray.get(1).intValue());
    }

    public DiagnosticEventBuilder(int propertyId) {
        this(propertyId, 0, 0);
    }

    public DiagnosticEventBuilder(int propertyId, int numVendorIntSensors, int numVendorFloatSensors) {
        this.mDtc = null;
        this.mPropertyId = propertyId;
        this.mNumIntSensors = numVendorIntSensors + 32;
        int numFloatSensors = numVendorFloatSensors + 71;
        this.mBitmask = new BitSet(this.mNumIntSensors + numFloatSensors);
        this.mIntValues = new DefaultedArray<>(this.mNumIntSensors, 0);
        this.mFloatValues = new DefaultedArray<>(numFloatSensors, Float.valueOf(0.0f));
    }

    public DiagnosticEventBuilder clear() {
        this.mIntValues.clear();
        this.mFloatValues.clear();
        this.mBitmask.clear();
        this.mDtc = null;
        return this;
    }

    public DiagnosticEventBuilder addIntSensor(int index, int value) {
        this.mIntValues.set(index, Integer.valueOf(value));
        this.mBitmask.set(index);
        return this;
    }

    public DiagnosticEventBuilder addFloatSensor(int index, float value) {
        this.mFloatValues.set(index, Float.valueOf(value));
        this.mBitmask.set(this.mNumIntSensors + index);
        return this;
    }

    public DiagnosticEventBuilder setDTC(String dtc) {
        this.mDtc = dtc;
        return this;
    }

    public VehiclePropValue build() {
        return build(0L);
    }

    public VehiclePropValue build(long timestamp) {
        final VehiclePropValueBuilder propValueBuilder = VehiclePropValueBuilder.newBuilder(this.mPropertyId);
        if (0 == timestamp) {
            propValueBuilder.setTimestamp();
        } else {
            propValueBuilder.setTimestamp(timestamp);
        }
        DefaultedArray<Integer> defaultedArray = this.mIntValues;
        Objects.requireNonNull(propValueBuilder);
        defaultedArray.forEach(new Consumer() { // from class: com.android.car.vehiclehal.-$$Lambda$DiagnosticEventBuilder$JA7P1fUfWmYWG6Jvv8xBl5aX0tg
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                VehiclePropValueBuilder.this.addIntValue(((Integer) obj).intValue());
            }
        });
        DefaultedArray<Float> defaultedArray2 = this.mFloatValues;
        Objects.requireNonNull(propValueBuilder);
        defaultedArray2.forEach(new Consumer() { // from class: com.android.car.vehiclehal.-$$Lambda$DiagnosticEventBuilder$zsapxUg-4M90ROVU0aEDJGrZPiI
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                VehiclePropValueBuilder.this.addFloatValue(((Float) obj).floatValue());
            }
        });
        return propValueBuilder.addByteValue(this.mBitmask.toByteArray()).setStringValue(this.mDtc).build();
    }
}
