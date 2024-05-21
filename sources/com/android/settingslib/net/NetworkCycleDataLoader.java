package com.android.settingslib.net;

import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.net.INetworkStatsService;
import android.net.INetworkStatsSession;
import android.net.NetworkPolicy;
import android.net.NetworkPolicyManager;
import android.net.NetworkStatsHistory;
import android.net.NetworkTemplate;
import android.net.TrafficStats;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Pair;
import androidx.annotation.VisibleForTesting;
import androidx.loader.content.AsyncTaskLoader;
import com.android.settingslib.NetworkPolicyEditor;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Iterator;
/* loaded from: classes3.dex */
public abstract class NetworkCycleDataLoader<D> extends AsyncTaskLoader<D> {
    private static final String TAG = "NetworkCycleDataLoader";
    private final ArrayList<Long> mCycles;
    protected final NetworkStatsManager mNetworkStatsManager;
    @VisibleForTesting
    final INetworkStatsService mNetworkStatsService;
    protected final NetworkTemplate mNetworkTemplate;
    private final NetworkPolicy mPolicy;

    abstract D getCycleUsage();

    @VisibleForTesting
    abstract void recordUsage(long j, long j2);

    /* JADX INFO: Access modifiers changed from: protected */
    public NetworkCycleDataLoader(Builder<?> builder) {
        super(((Builder) builder).mContext);
        this.mNetworkTemplate = ((Builder) builder).mNetworkTemplate;
        this.mCycles = ((Builder) builder).mCycles;
        this.mNetworkStatsManager = (NetworkStatsManager) ((Builder) builder).mContext.getSystemService("netstats");
        this.mNetworkStatsService = INetworkStatsService.Stub.asInterface(ServiceManager.getService("netstats"));
        NetworkPolicyEditor policyEditor = new NetworkPolicyEditor(NetworkPolicyManager.from(((Builder) builder).mContext));
        policyEditor.read();
        this.mPolicy = policyEditor.getPolicy(this.mNetworkTemplate);
    }

    /* JADX INFO: Access modifiers changed from: protected */
    @Override // androidx.loader.content.Loader
    public void onStartLoading() {
        super.onStartLoading();
        forceLoad();
    }

    @Override // androidx.loader.content.AsyncTaskLoader
    public D loadInBackground() {
        ArrayList<Long> arrayList = this.mCycles;
        if (arrayList != null && arrayList.size() > 1) {
            loadDataForSpecificCycles();
        } else if (this.mPolicy == null) {
            loadFourWeeksData();
        } else {
            loadPolicyData();
        }
        return getCycleUsage();
    }

    @VisibleForTesting
    void loadPolicyData() {
        Iterator<Pair<ZonedDateTime, ZonedDateTime>> iterator = NetworkPolicyManager.cycleIterator(this.mPolicy);
        while (iterator.hasNext()) {
            Pair<ZonedDateTime, ZonedDateTime> cycle = iterator.next();
            long cycleStart = ((ZonedDateTime) cycle.first).toInstant().toEpochMilli();
            long cycleEnd = ((ZonedDateTime) cycle.second).toInstant().toEpochMilli();
            recordUsage(cycleStart, cycleEnd);
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

    @VisibleForTesting
    void loadFourWeeksData() {
        try {
            INetworkStatsSession networkSession = this.mNetworkStatsService.openSession();
            NetworkStatsHistory networkHistory = networkSession.getHistoryForNetwork(this.mNetworkTemplate, 10);
            long historyStart = networkHistory.getStart();
            long historyEnd = networkHistory.getEnd();
            long cycleEnd = historyEnd;
            while (cycleEnd > historyStart) {
                long cycleStart = cycleEnd - 2419200000L;
                recordUsage(cycleStart, cycleEnd);
                cycleEnd = cycleStart;
            }
            TrafficStats.closeQuietly(networkSession);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @VisibleForTesting
    void loadDataForSpecificCycles() {
        long cycleEnd = this.mCycles.get(0).longValue();
        int lastCycleIndex = this.mCycles.size() - 1;
        for (int i = 1; i <= lastCycleIndex; i++) {
            long cycleStart = this.mCycles.get(i).longValue();
            recordUsage(cycleStart, cycleEnd);
            cycleEnd = cycleStart;
        }
    }

    public static Builder<?> builder(Context context) {
        return new Builder<NetworkCycleDataLoader>(context) { // from class: com.android.settingslib.net.NetworkCycleDataLoader.1
            @Override // com.android.settingslib.net.NetworkCycleDataLoader.Builder
            public NetworkCycleDataLoader build() {
                return null;
            }
        };
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public long getTotalUsage(NetworkStats stats) {
        long bytes = 0;
        if (stats != null) {
            NetworkStats.Bucket bucket = new NetworkStats.Bucket();
            while (stats.hasNextBucket() && stats.getNextBucket(bucket)) {
                bytes += bucket.getRxBytes() + bucket.getTxBytes();
            }
            stats.close();
        }
        return bytes;
    }

    @VisibleForTesting(otherwise = 5)
    public ArrayList<Long> getCycles() {
        return this.mCycles;
    }

    /* loaded from: classes3.dex */
    public static abstract class Builder<T extends NetworkCycleDataLoader> {
        private final Context mContext;
        private ArrayList<Long> mCycles;
        private NetworkTemplate mNetworkTemplate;

        public abstract T build();

        public Builder(Context context) {
            this.mContext = context;
        }

        public Builder<T> setNetworkTemplate(NetworkTemplate template) {
            this.mNetworkTemplate = template;
            return this;
        }

        public Builder<T> setCycles(ArrayList<Long> cycles) {
            this.mCycles = cycles;
            return this;
        }
    }
}
