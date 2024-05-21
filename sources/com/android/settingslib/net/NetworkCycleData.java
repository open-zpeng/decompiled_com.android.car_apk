package com.android.settingslib.net;
/* loaded from: classes3.dex */
public class NetworkCycleData {
    private long mEndTime;
    private long mStartTime;
    private long mTotalUsage;

    public long getStartTime() {
        return this.mStartTime;
    }

    public long getEndTime() {
        return this.mEndTime;
    }

    public long getTotalUsage() {
        return this.mTotalUsage;
    }

    /* loaded from: classes3.dex */
    public static class Builder {
        private NetworkCycleData mObject = new NetworkCycleData();

        public Builder setStartTime(long start) {
            getObject().mStartTime = start;
            return this;
        }

        public Builder setEndTime(long end) {
            getObject().mEndTime = end;
            return this;
        }

        public Builder setTotalUsage(long total) {
            getObject().mTotalUsage = total;
            return this;
        }

        protected NetworkCycleData getObject() {
            return this.mObject;
        }

        public NetworkCycleData build() {
            return getObject();
        }
    }
}
