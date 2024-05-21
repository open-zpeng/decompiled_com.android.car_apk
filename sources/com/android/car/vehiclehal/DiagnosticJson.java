package com.android.car.vehiclehal;

import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.util.JsonReader;
import android.util.Log;
import android.util.SparseArray;
import com.android.car.vehiclehal.Utils;
import com.android.settingslib.datetime.ZoneGetter;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
/* loaded from: classes3.dex */
public class DiagnosticJson {
    public final String dtc;
    public final SparseArray<Float> floatValues;
    public final SparseArray<Integer> intValues;
    public final long timestamp;
    public final String type;

    DiagnosticJson(String type, long timestamp, SparseArray<Integer> intValues, SparseArray<Float> floatValues, String dtc) {
        this.type = type;
        this.timestamp = timestamp;
        this.intValues = (SparseArray) Objects.requireNonNull(intValues);
        this.floatValues = (SparseArray) Objects.requireNonNull(floatValues);
        this.dtc = dtc;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public VehiclePropValue build(final DiagnosticEventBuilder builder) {
        new Utils.SparseArrayIterator(this.intValues).forEach(new Consumer() { // from class: com.android.car.vehiclehal.-$$Lambda$DiagnosticJson$jQRvOI9zncWW64SQ9Wxf63kQZps
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                DiagnosticEventBuilder.this.addIntSensor(r2.key, ((Integer) ((Utils.SparseArrayIterator.SparseArrayEntry) obj).value).intValue());
            }
        });
        new Utils.SparseArrayIterator(this.floatValues).forEach(new Consumer() { // from class: com.android.car.vehiclehal.-$$Lambda$DiagnosticJson$ECN_o0n04_AOhxC6lZmBE8nrb_o
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                DiagnosticEventBuilder.this.addFloatSensor(r2.key, ((Float) ((Utils.SparseArrayIterator.SparseArrayEntry) obj).value).floatValue());
            }
        });
        builder.setDTC(this.dtc);
        VehiclePropValue vehiclePropValue = builder.build(this.timestamp);
        builder.clear();
        return vehiclePropValue;
    }

    /* loaded from: classes3.dex */
    static class Builder {
        public static final String TAG = Builder.class.getSimpleName();
        final WriteOnce<String> mType = new WriteOnce<>();
        final WriteOnce<Long> mTimestamp = new WriteOnce<>();
        final SparseArray<Integer> mIntValues = new SparseArray<>();
        final SparseArray<Float> mFloatValues = new SparseArray<>();
        final WriteOnce<String> mDtc = new WriteOnce<>();

        /* JADX INFO: Access modifiers changed from: package-private */
        /* loaded from: classes3.dex */
        public static class WriteOnce<T> {
            private Optional<T> mValue = Optional.empty();

            WriteOnce() {
            }

            void write(T value) {
                if (this.mValue.isPresent()) {
                    throw new IllegalStateException("WriteOnce already stored");
                }
                this.mValue = Optional.of(value);
            }

            T get() {
                if (!this.mValue.isPresent()) {
                    throw new IllegalStateException("WriteOnce never stored");
                }
                return this.mValue.get();
            }

            T get(T defaultValue) {
                return this.mValue.isPresent() ? this.mValue.get() : defaultValue;
            }
        }

        private void readIntValues(JsonReader jsonReader) throws IOException {
            while (jsonReader.hasNext()) {
                int id = 0;
                int value = 0;
                jsonReader.beginObject();
                while (jsonReader.hasNext()) {
                    String name = jsonReader.nextName();
                    if (name.equals(ZoneGetter.KEY_ID)) {
                        id = jsonReader.nextInt();
                    } else if (name.equals("value")) {
                        value = jsonReader.nextInt();
                    }
                }
                jsonReader.endObject();
                this.mIntValues.put(id, Integer.valueOf(value));
            }
        }

        private void readFloatValues(JsonReader jsonReader) throws IOException {
            while (jsonReader.hasNext()) {
                int id = 0;
                float value = 0.0f;
                jsonReader.beginObject();
                while (jsonReader.hasNext()) {
                    String name = jsonReader.nextName();
                    if (name.equals(ZoneGetter.KEY_ID)) {
                        id = jsonReader.nextInt();
                    } else if (name.equals("value")) {
                        value = (float) jsonReader.nextDouble();
                    }
                }
                jsonReader.endObject();
                this.mFloatValues.put(id, Float.valueOf(value));
            }
        }

        Builder(JsonReader jsonReader) throws IOException {
            jsonReader.beginObject();
            while (jsonReader.hasNext()) {
                String name = jsonReader.nextName();
                char c = 65535;
                switch (name.hashCode()) {
                    case -1732872546:
                        if (name.equals("floatValues")) {
                            c = 3;
                            break;
                        }
                        break;
                    case -1519213600:
                        if (name.equals("stringValue")) {
                            c = 4;
                            break;
                        }
                        break;
                    case 3575610:
                        if (name.equals("type")) {
                            c = 0;
                            break;
                        }
                        break;
                    case 55126294:
                        if (name.equals("timestamp")) {
                            c = 1;
                            break;
                        }
                        break;
                    case 57684465:
                        if (name.equals("intValues")) {
                            c = 2;
                            break;
                        }
                        break;
                }
                if (c == 0) {
                    this.mType.write(jsonReader.nextString());
                } else if (c == 1) {
                    this.mTimestamp.write(Long.valueOf(jsonReader.nextLong()));
                } else if (c == 2) {
                    jsonReader.beginArray();
                    readIntValues(jsonReader);
                    jsonReader.endArray();
                } else if (c == 3) {
                    jsonReader.beginArray();
                    readFloatValues(jsonReader);
                    jsonReader.endArray();
                } else if (c == 4) {
                    this.mDtc.write(jsonReader.nextString());
                } else {
                    Log.w(TAG, "Unknown name in diagnostic JSON: " + name);
                }
            }
            jsonReader.endObject();
        }

        DiagnosticJson build() {
            return new DiagnosticJson(this.mType.get(), this.mTimestamp.get().longValue(), this.mIntValues, this.mFloatValues, this.mDtc.get(null));
        }
    }

    public static DiagnosticJson build(JsonReader jsonReader) throws IOException {
        return new Builder(jsonReader).build();
    }
}
