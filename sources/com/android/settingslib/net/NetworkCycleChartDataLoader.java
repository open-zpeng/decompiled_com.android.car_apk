package com.android.settingslib.net;

import android.app.usage.NetworkStats;
import android.content.Context;
import android.os.RemoteException;
import android.util.Log;
import com.android.settingslib.net.NetworkCycleChartData;
import com.android.settingslib.net.NetworkCycleData;
import com.android.settingslib.net.NetworkCycleDataLoader;
import java.util.ArrayList;
import java.util.List;
/* loaded from: classes3.dex */
public class NetworkCycleChartDataLoader extends NetworkCycleDataLoader<List<NetworkCycleChartData>> {
    private static final String TAG = "NetworkCycleChartLoader";
    private final List<NetworkCycleChartData> mData;

    private NetworkCycleChartDataLoader(Builder builder) {
        super(builder);
        this.mData = new ArrayList();
    }

    @Override // com.android.settingslib.net.NetworkCycleDataLoader
    void recordUsage(long start, long end) {
        try {
            NetworkStats.Bucket bucket = this.mNetworkStatsManager.querySummaryForDevice(this.mNetworkTemplate, start, end);
            long total = bucket == null ? 0L : bucket.getRxBytes() + bucket.getTxBytes();
            if (total > 0) {
                NetworkCycleChartData.Builder builder = new NetworkCycleChartData.Builder();
                builder.setUsageBuckets(getUsageBuckets(start, end)).setStartTime(start).setEndTime(end).setTotalUsage(total);
                this.mData.add(builder.build());
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Exception querying network detail.", e);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @Override // com.android.settingslib.net.NetworkCycleDataLoader
    public List<NetworkCycleChartData> getCycleUsage() {
        return this.mData;
    }

    public static Builder<?> builder(Context context) {
        return new Builder<NetworkCycleChartDataLoader>(context) { // from class: com.android.settingslib.net.NetworkCycleChartDataLoader.1
            @Override // com.android.settingslib.net.NetworkCycleDataLoader.Builder
            public NetworkCycleChartDataLoader build() {
                return new NetworkCycleChartDataLoader(this);
            }
        };
    }

    private List<NetworkCycleData> getUsageBuckets(long start, long end) {
        List<NetworkCycleData> data = new ArrayList<>();
        long bucketStart = start;
        for (long bucketEnd = start + NetworkCycleChartData.BUCKET_DURATION_MS; bucketEnd <= end; bucketEnd += NetworkCycleChartData.BUCKET_DURATION_MS) {
            long usage = 0;
            try {
                NetworkStats.Bucket bucket = this.mNetworkStatsManager.querySummaryForDevice(this.mNetworkTemplate, bucketStart, bucketEnd);
                if (bucket != null) {
                    usage = bucket.getRxBytes() + bucket.getTxBytes();
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Exception querying network detail.", e);
            }
            data.add(new NetworkCycleData.Builder().setStartTime(bucketStart).setEndTime(bucketEnd).setTotalUsage(usage).build());
            bucketStart = bucketEnd;
        }
        return data;
    }

    /* loaded from: classes3.dex */
    public static abstract class Builder<T extends NetworkCycleChartDataLoader> extends NetworkCycleDataLoader.Builder<T> {
        public Builder(Context context) {
            super(context);
        }
    }
}
