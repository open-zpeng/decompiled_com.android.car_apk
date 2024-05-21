package com.android.settingslib.net;

import com.android.settingslib.net.NetworkCycleData;
import java.util.List;
import java.util.concurrent.TimeUnit;
/* loaded from: classes3.dex */
public class NetworkCycleChartData extends NetworkCycleData {
    public static final long BUCKET_DURATION_MS = TimeUnit.DAYS.toMillis(1);
    private List<NetworkCycleData> mUsageBuckets;

    private NetworkCycleChartData() {
    }

    public List<NetworkCycleData> getUsageBuckets() {
        return this.mUsageBuckets;
    }

    /* loaded from: classes3.dex */
    public static class Builder extends NetworkCycleData.Builder {
        private NetworkCycleChartData mObject = new NetworkCycleChartData();

        public Builder setUsageBuckets(List<NetworkCycleData> buckets) {
            getObject().mUsageBuckets = buckets;
            return this;
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // com.android.settingslib.net.NetworkCycleData.Builder
        public NetworkCycleChartData getObject() {
            return this.mObject;
        }

        @Override // com.android.settingslib.net.NetworkCycleData.Builder
        public NetworkCycleChartData build() {
            return getObject();
        }
    }
}
