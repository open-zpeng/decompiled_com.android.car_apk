package com.android.settingslib.wifi;

import android.net.ScoredNetwork;
import android.os.Parcel;
import android.os.Parcelable;
/* JADX INFO: Access modifiers changed from: package-private */
/* loaded from: classes3.dex */
public class TimestampedScoredNetwork implements Parcelable {
    public static final Parcelable.Creator<TimestampedScoredNetwork> CREATOR = new Parcelable.Creator<TimestampedScoredNetwork>() { // from class: com.android.settingslib.wifi.TimestampedScoredNetwork.1
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public TimestampedScoredNetwork createFromParcel(Parcel in) {
            return new TimestampedScoredNetwork(in);
        }

        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public TimestampedScoredNetwork[] newArray(int size) {
            return new TimestampedScoredNetwork[size];
        }
    };
    private ScoredNetwork mScore;
    private long mUpdatedTimestampMillis;

    /* JADX INFO: Access modifiers changed from: package-private */
    public TimestampedScoredNetwork(ScoredNetwork score, long updatedTimestampMillis) {
        this.mScore = score;
        this.mUpdatedTimestampMillis = updatedTimestampMillis;
    }

    protected TimestampedScoredNetwork(Parcel in) {
        this.mScore = in.readParcelable(ScoredNetwork.class.getClassLoader());
        this.mUpdatedTimestampMillis = in.readLong();
    }

    public void update(ScoredNetwork score, long updatedTimestampMillis) {
        this.mScore = score;
        this.mUpdatedTimestampMillis = updatedTimestampMillis;
    }

    public ScoredNetwork getScore() {
        return this.mScore;
    }

    public long getUpdatedTimestampMillis() {
        return this.mUpdatedTimestampMillis;
    }

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(this.mScore, flags);
        dest.writeLong(this.mUpdatedTimestampMillis);
    }
}
