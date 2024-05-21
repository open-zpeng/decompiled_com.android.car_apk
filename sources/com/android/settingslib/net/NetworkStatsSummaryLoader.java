package com.android.settingslib.net;

import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.net.NetworkTemplate;
import android.os.RemoteException;
import android.util.Log;
import androidx.loader.content.AsyncTaskLoader;
/* loaded from: classes3.dex */
public class NetworkStatsSummaryLoader extends AsyncTaskLoader<NetworkStats> {
    private static final String TAG = "NetworkDetailLoader";
    private final long mEnd;
    private final NetworkStatsManager mNetworkStatsManager;
    private final NetworkTemplate mNetworkTemplate;
    private final long mStart;

    private NetworkStatsSummaryLoader(Builder builder) {
        super(builder.mContext);
        this.mStart = builder.mStart;
        this.mEnd = builder.mEnd;
        this.mNetworkTemplate = builder.mNetworkTemplate;
        this.mNetworkStatsManager = (NetworkStatsManager) builder.mContext.getSystemService("netstats");
    }

    /* JADX INFO: Access modifiers changed from: protected */
    @Override // androidx.loader.content.Loader
    public void onStartLoading() {
        super.onStartLoading();
        forceLoad();
    }

    /* JADX WARN: Can't rename method to resolve collision */
    @Override // androidx.loader.content.AsyncTaskLoader
    public NetworkStats loadInBackground() {
        try {
            return this.mNetworkStatsManager.querySummary(this.mNetworkTemplate, this.mStart, this.mEnd);
        } catch (RemoteException e) {
            Log.e(TAG, "Exception querying network detail.", e);
            return null;
        }
    }

    /* JADX INFO: Access modifiers changed from: protected */
    @Override // androidx.loader.content.Loader
    public void onStopLoading() {
        super.onStopLoading();
        cancelLoad();
    }

    /* JADX INFO: Access modifiers changed from: protected */
    @Override // androidx.loader.content.Loader
    public void onReset() {
        super.onReset();
        cancelLoad();
    }

    /* loaded from: classes3.dex */
    public static class Builder {
        private final Context mContext;
        private long mEnd;
        private NetworkTemplate mNetworkTemplate;
        private long mStart;

        public Builder(Context context) {
            this.mContext = context;
        }

        public Builder setStartTime(long start) {
            this.mStart = start;
            return this;
        }

        public Builder setEndTime(long end) {
            this.mEnd = end;
            return this;
        }

        public Builder setNetworkTemplate(NetworkTemplate template) {
            this.mNetworkTemplate = template;
            return this;
        }

        public NetworkStatsSummaryLoader build() {
            return new NetworkStatsSummaryLoader(this);
        }
    }
}
