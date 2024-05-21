package com.android.car.storagemonitoring;

import android.car.storagemonitoring.WearEstimate;
import java.util.Objects;
/* loaded from: classes3.dex */
public final class WearInformation {
    public static final int PRE_EOL_INFO_NORMAL = 1;
    public static final int PRE_EOL_INFO_URGENT = 3;
    public static final int PRE_EOL_INFO_WARNING = 2;
    private static final String[] PRE_EOL_STRINGS = {"unknown", "normal", "warning", "urgent"};
    private static final String UNKNOWN = "unknown";
    public static final int UNKNOWN_LIFETIME_ESTIMATE = -1;
    public static final int UNKNOWN_PRE_EOL_INFO = 0;
    public final int lifetimeEstimateA;
    public final int lifetimeEstimateB;
    public final int preEolInfo;

    public WearInformation(int lifetimeA, int lifetimeB, int preEol) {
        this.lifetimeEstimateA = lifetimeA;
        this.lifetimeEstimateB = lifetimeB;
        this.preEolInfo = preEol;
    }

    public int hashCode() {
        return Objects.hash(Integer.valueOf(this.lifetimeEstimateA), Integer.valueOf(this.lifetimeEstimateB), Integer.valueOf(this.preEolInfo));
    }

    public boolean equals(Object other) {
        if (other instanceof WearInformation) {
            WearInformation wi = (WearInformation) other;
            return wi.lifetimeEstimateA == this.lifetimeEstimateA && wi.lifetimeEstimateB == this.lifetimeEstimateB && wi.preEolInfo == this.preEolInfo;
        }
        return false;
    }

    private String lifetimeToString(int lifetime) {
        if (lifetime == -1) {
            return "unknown";
        }
        return lifetime + "%";
    }

    public String toString() {
        return String.format("lifetime estimate: A = %s, B = %s; pre EOL info: %s", lifetimeToString(this.lifetimeEstimateA), lifetimeToString(this.lifetimeEstimateB), PRE_EOL_STRINGS[this.preEolInfo]);
    }

    public WearEstimate toWearEstimate() {
        return new WearEstimate(this.lifetimeEstimateA, this.lifetimeEstimateB);
    }
}
