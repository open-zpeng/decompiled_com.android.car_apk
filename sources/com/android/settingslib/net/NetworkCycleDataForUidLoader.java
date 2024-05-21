package com.android.settingslib.net;

import android.app.usage.NetworkStats;
import android.content.Context;
import android.util.Log;
import androidx.annotation.VisibleForTesting;
import com.android.settingslib.net.NetworkCycleDataForUid;
import com.android.settingslib.net.NetworkCycleDataLoader;
import java.util.ArrayList;
import java.util.List;
/* loaded from: classes3.dex */
public class NetworkCycleDataForUidLoader extends NetworkCycleDataLoader<List<NetworkCycleDataForUid>> {
    private static final String TAG = "NetworkDataForUidLoader";
    private final List<NetworkCycleDataForUid> mData;
    private final boolean mRetrieveDetail;
    private final List<Integer> mUids;

    private NetworkCycleDataForUidLoader(Builder builder) {
        super(builder);
        this.mUids = builder.mUids;
        this.mRetrieveDetail = builder.mRetrieveDetail;
        this.mData = new ArrayList();
    }

    @Override // com.android.settingslib.net.NetworkCycleDataLoader
    void recordUsage(long start, long end) {
        long totalUsage = 0;
        try {
            long totalForeground = 0;
            for (Integer num : this.mUids) {
                int uid = num.intValue();
                NetworkStats stats = this.mNetworkStatsManager.queryDetailsForUid(this.mNetworkTemplate, start, end, uid);
                long usage = getTotalUsage(stats);
                if (usage > 0) {
                    long totalUsage2 = totalUsage + usage;
                    if (!this.mRetrieveDetail) {
                        totalUsage = totalUsage2;
                    } else {
                        totalForeground += getForegroundUsage(start, end, uid);
                        totalUsage = totalUsage2;
                    }
                }
            }
            if (totalUsage > 0) {
                NetworkCycleDataForUid.Builder builder = new NetworkCycleDataForUid.Builder();
                try {
                } catch (Exception e) {
                    e = e;
                    Log.e(TAG, "Exception querying network detail.", e);
                }
                try {
                    builder.setStartTime(start).setEndTime(end).setTotalUsage(totalUsage);
                    if (this.mRetrieveDetail) {
                        builder.setBackgroundUsage(totalUsage - totalForeground).setForegroundUsage(totalForeground);
                    }
                    this.mData.add(builder.build());
                } catch (Exception e2) {
                    e = e2;
                    Log.e(TAG, "Exception querying network detail.", e);
                }
            }
        } catch (Exception e3) {
            e = e3;
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @Override // com.android.settingslib.net.NetworkCycleDataLoader
    public List<NetworkCycleDataForUid> getCycleUsage() {
        return this.mData;
    }

    public static Builder<?> builder(Context context) {
        return new Builder<NetworkCycleDataForUidLoader>(context) { // from class: com.android.settingslib.net.NetworkCycleDataForUidLoader.1
            @Override // com.android.settingslib.net.NetworkCycleDataLoader.Builder
            public NetworkCycleDataForUidLoader build() {
                return new NetworkCycleDataForUidLoader(this);
            }
        };
    }

    @VisibleForTesting(otherwise = 5)
    public List<Integer> getUids() {
        return this.mUids;
    }

    private long getForegroundUsage(long start, long end, int uid) {
        NetworkStats stats = this.mNetworkStatsManager.queryDetailsForUidTagState(this.mNetworkTemplate, start, end, uid, 0, 2);
        return getTotalUsage(stats);
    }

    /* loaded from: classes3.dex */
    public static abstract class Builder<T extends NetworkCycleDataForUidLoader> extends NetworkCycleDataLoader.Builder<T> {
        private boolean mRetrieveDetail;
        private final List<Integer> mUids;

        public Builder(Context context) {
            super(context);
            this.mUids = new ArrayList();
            this.mRetrieveDetail = true;
        }

        public Builder<T> addUid(int uid) {
            this.mUids.add(Integer.valueOf(uid));
            return this;
        }

        public Builder<T> setRetrieveDetail(boolean retrieveDetail) {
            this.mRetrieveDetail = retrieveDetail;
            return this;
        }
    }
}
