package com.android.car.storagemonitoring;

import android.car.storagemonitoring.WearEstimate;
import android.car.storagemonitoring.WearEstimateChange;
import android.util.JsonWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import org.json.JSONException;
import org.json.JSONObject;
/* loaded from: classes3.dex */
public class WearEstimateRecord {
    private final WearEstimate mNewWearEstimate;
    private final WearEstimate mOldWearEstimate;
    private final long mTotalCarServiceUptime;
    private final Instant mUnixTimestamp;

    public WearEstimateRecord(WearEstimate oldWearEstimate, WearEstimate newWearEstimate, long totalCarServiceUptime, Instant unixTimestamp) {
        this.mOldWearEstimate = (WearEstimate) Objects.requireNonNull(oldWearEstimate);
        this.mNewWearEstimate = (WearEstimate) Objects.requireNonNull(newWearEstimate);
        this.mTotalCarServiceUptime = totalCarServiceUptime;
        this.mUnixTimestamp = (Instant) Objects.requireNonNull(unixTimestamp);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public WearEstimateRecord(JSONObject json) throws JSONException {
        this.mOldWearEstimate = new WearEstimate(json.getJSONObject("oldWearEstimate"));
        this.mNewWearEstimate = new WearEstimate(json.getJSONObject("newWearEstimate"));
        this.mTotalCarServiceUptime = json.getLong("totalCarServiceUptime");
        this.mUnixTimestamp = Instant.ofEpochMilli(json.getLong("unixTimestamp"));
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void writeToJson(JsonWriter jsonWriter) throws IOException {
        jsonWriter.beginObject();
        jsonWriter.name("oldWearEstimate");
        this.mOldWearEstimate.writeToJson(jsonWriter);
        jsonWriter.name("newWearEstimate");
        this.mNewWearEstimate.writeToJson(jsonWriter);
        jsonWriter.name("totalCarServiceUptime").value(this.mTotalCarServiceUptime);
        jsonWriter.name("unixTimestamp").value(this.mUnixTimestamp.toEpochMilli());
        jsonWriter.endObject();
    }

    public WearEstimate getOldWearEstimate() {
        return this.mOldWearEstimate;
    }

    public WearEstimate getNewWearEstimate() {
        return this.mNewWearEstimate;
    }

    public long getTotalCarServiceUptime() {
        return this.mTotalCarServiceUptime;
    }

    public Instant getUnixTimestamp() {
        return this.mUnixTimestamp;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public WearEstimateChange toWearEstimateChange(boolean isAcceptableDegradation) {
        return new WearEstimateChange(this.mOldWearEstimate, this.mNewWearEstimate, this.mTotalCarServiceUptime, this.mUnixTimestamp, isAcceptableDegradation);
    }

    public boolean equals(Object other) {
        if (other instanceof WearEstimateRecord) {
            WearEstimateRecord wer = (WearEstimateRecord) other;
            return wer.mOldWearEstimate.equals(this.mOldWearEstimate) && wer.mNewWearEstimate.equals(this.mNewWearEstimate) && wer.mTotalCarServiceUptime == this.mTotalCarServiceUptime && wer.mUnixTimestamp.equals(this.mUnixTimestamp);
        }
        return false;
    }

    public boolean isSameAs(WearEstimateChange wearEstimateChange) {
        return this.mOldWearEstimate.equals(wearEstimateChange.oldEstimate) && this.mNewWearEstimate.equals(wearEstimateChange.newEstimate) && this.mTotalCarServiceUptime == wearEstimateChange.uptimeAtChange;
    }

    public int hashCode() {
        return Objects.hash(this.mOldWearEstimate, this.mNewWearEstimate, Long.valueOf(this.mTotalCarServiceUptime), this.mUnixTimestamp);
    }

    public String toString() {
        return String.format("WearEstimateRecord {mOldWearEstimate = %s, mNewWearEstimate = %s, mTotalCarServiceUptime = %d, mUnixTimestamp = %s}", this.mOldWearEstimate, this.mNewWearEstimate, Long.valueOf(this.mTotalCarServiceUptime), this.mUnixTimestamp);
    }

    /* loaded from: classes3.dex */
    public static final class Builder {
        private WearEstimate mOldWearEstimate = null;
        private WearEstimate mNewWearEstimate = null;
        private long mTotalCarServiceUptime = -1;
        private Instant mUnixTimestamp = null;

        private Builder() {
        }

        public static Builder newBuilder() {
            return new Builder();
        }

        public Builder fromWearEstimate(WearEstimate wearEstimate) {
            this.mOldWearEstimate = (WearEstimate) Objects.requireNonNull(wearEstimate);
            return this;
        }

        public Builder toWearEstimate(WearEstimate wearEstimate) {
            this.mNewWearEstimate = (WearEstimate) Objects.requireNonNull(wearEstimate);
            return this;
        }

        public Builder atUptime(long uptime) {
            if (uptime < 0) {
                throw new IllegalArgumentException("uptime must be >= 0");
            }
            this.mTotalCarServiceUptime = uptime;
            return this;
        }

        public Builder atTimestamp(Instant now) {
            this.mUnixTimestamp = (Instant) Objects.requireNonNull(now);
            return this;
        }

        public WearEstimateRecord build() {
            WearEstimate wearEstimate;
            Instant instant;
            WearEstimate wearEstimate2 = this.mOldWearEstimate;
            if (wearEstimate2 != null && (wearEstimate = this.mNewWearEstimate) != null) {
                long j = this.mTotalCarServiceUptime;
                if (j >= 0 && (instant = this.mUnixTimestamp) != null) {
                    return new WearEstimateRecord(wearEstimate2, wearEstimate, j, instant);
                }
            }
            throw new IllegalStateException("malformed builder state");
        }
    }
}
