package com.android.car.storagemonitoring;

import android.car.storagemonitoring.WearEstimateChange;
import android.util.JsonWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
/* loaded from: classes3.dex */
public class WearHistory {
    private final List<WearEstimateRecord> mWearHistory = new ArrayList();

    public WearHistory() {
    }

    WearHistory(JSONObject jsonObject) throws JSONException {
        JSONArray wearHistory = jsonObject.getJSONArray("wearHistory");
        for (int i = 0; i < wearHistory.length(); i++) {
            JSONObject wearRecordJson = wearHistory.getJSONObject(i);
            WearEstimateRecord wearRecord = new WearEstimateRecord(wearRecordJson);
            add(wearRecord);
        }
    }

    public static WearHistory fromRecords(WearEstimateRecord... records) {
        final WearHistory wearHistory = new WearHistory();
        Stream stream = Arrays.stream(records);
        Objects.requireNonNull(wearHistory);
        stream.forEach(new Consumer() { // from class: com.android.car.storagemonitoring.-$$Lambda$M-BSctG_y9vsZKZt8FdeHK1Ka2k
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                WearHistory.this.add((WearEstimateRecord) obj);
            }
        });
        return wearHistory;
    }

    public static WearHistory fromJson(File in) throws IOException, JSONException {
        JSONObject jsonObject = new JSONObject(new String(Files.readAllBytes(in.toPath())));
        return new WearHistory(jsonObject);
    }

    public void writeToJson(JsonWriter out) throws IOException {
        out.beginObject();
        out.name("wearHistory").beginArray();
        for (WearEstimateRecord wearRecord : this.mWearHistory) {
            wearRecord.writeToJson(out);
        }
        out.endArray();
        out.endObject();
    }

    public boolean add(WearEstimateRecord record) {
        if (record != null && this.mWearHistory.add(record)) {
            this.mWearHistory.sort(new Comparator() { // from class: com.android.car.storagemonitoring.-$$Lambda$WearHistory$gK9yZsKFOWBVEaJui2rxuZPhDyc
                @Override // java.util.Comparator
                public final int compare(Object obj, Object obj2) {
                    int compareTo;
                    compareTo = Long.valueOf(((WearEstimateRecord) obj).getTotalCarServiceUptime()).compareTo(Long.valueOf(((WearEstimateRecord) obj2).getTotalCarServiceUptime()));
                    return compareTo;
                }
            });
            return true;
        }
        return false;
    }

    public int size() {
        return this.mWearHistory.size();
    }

    public WearEstimateRecord get(int i) {
        return this.mWearHistory.get(i);
    }

    public WearEstimateRecord getLast() {
        return get(size() - 1);
    }

    public List<WearEstimateChange> toWearEstimateChanges(long acceptableHoursPerOnePercentFlashWear) {
        long acceptableWearRate = Duration.ofHours(acceptableHoursPerOnePercentFlashWear).toMillis() * 10;
        int numRecords = size();
        if (numRecords == 0) {
            return Collections.emptyList();
        }
        List<WearEstimateChange> result = new ArrayList<>();
        result.add(get(0).toWearEstimateChange(true));
        for (int i = 1; i < numRecords; i++) {
            WearEstimateRecord previousRecord = get(i - 1);
            WearEstimateRecord currentRecord = get(i);
            long timeForChange = currentRecord.getTotalCarServiceUptime() - previousRecord.getTotalCarServiceUptime();
            boolean isAcceptableDegradation = timeForChange >= acceptableWearRate;
            result.add(currentRecord.toWearEstimateChange(isAcceptableDegradation));
        }
        return Collections.unmodifiableList(result);
    }

    public boolean equals(Object other) {
        if (other instanceof WearHistory) {
            WearHistory wi = (WearHistory) other;
            return wi.mWearHistory.equals(this.mWearHistory);
        }
        return false;
    }

    public int hashCode() {
        return this.mWearHistory.hashCode();
    }

    public String toString() {
        Stream<R> map = this.mWearHistory.stream().map(new Function() { // from class: com.android.car.storagemonitoring.-$$Lambda$cJV0L6PvLef8TIU_I2T_P68nX28
            @Override // java.util.function.Function
            public final Object apply(Object obj) {
                return ((WearEstimateRecord) obj).toString();
            }
        });
        return (String) map.reduce("WearHistory[size = " + size() + "] -> ", new BinaryOperator() { // from class: com.android.car.storagemonitoring.-$$Lambda$WearHistory$weKZIAU1qLUek10yWT8t3cGB2Bs
            @Override // java.util.function.BiFunction
            public final Object apply(Object obj, Object obj2) {
                return WearHistory.lambda$toString$1((String) obj, (String) obj2);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ String lambda$toString$1(String s, String t) {
        return s + ", " + t;
    }
}
