package com.android.settingslib.net;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.net.INetworkStatsSession;
import android.net.NetworkStatsHistory;
import android.net.NetworkTemplate;
import android.os.Bundle;
import android.os.RemoteException;
import com.android.car.UptimeTracker;
import com.android.settingslib.AppItem;
@Deprecated
/* loaded from: classes3.dex */
public class ChartDataLoader extends AsyncTaskLoader<ChartData> {
    private static final String KEY_APP = "app";
    private static final String KEY_FIELDS = "fields";
    private static final String KEY_TEMPLATE = "template";
    private final Bundle mArgs;
    private final INetworkStatsSession mSession;

    public static Bundle buildArgs(NetworkTemplate template, AppItem app) {
        return buildArgs(template, app, 10);
    }

    public static Bundle buildArgs(NetworkTemplate template, AppItem app, int fields) {
        Bundle args = new Bundle();
        args.putParcelable(KEY_TEMPLATE, template);
        args.putParcelable(KEY_APP, app);
        args.putInt(KEY_FIELDS, fields);
        return args;
    }

    public ChartDataLoader(Context context, INetworkStatsSession session, Bundle args) {
        super(context);
        this.mSession = session;
        this.mArgs = args;
    }

    @Override // android.content.Loader
    protected void onStartLoading() {
        super.onStartLoading();
        forceLoad();
    }

    /* JADX WARN: Can't rename method to resolve collision */
    @Override // android.content.AsyncTaskLoader
    public ChartData loadInBackground() {
        NetworkTemplate template = (NetworkTemplate) this.mArgs.getParcelable(KEY_TEMPLATE);
        AppItem app = (AppItem) this.mArgs.getParcelable(KEY_APP);
        int fields = this.mArgs.getInt(KEY_FIELDS);
        try {
            return loadInBackground(template, app, fields);
        } catch (RemoteException e) {
            throw new RuntimeException("problem reading network stats", e);
        }
    }

    private ChartData loadInBackground(NetworkTemplate template, AppItem app, int fields) throws RemoteException {
        ChartData data = new ChartData();
        data.network = this.mSession.getHistoryForNetwork(template, fields);
        if (app != null) {
            int size = app.uids.size();
            for (int i = 0; i < size; i++) {
                int uid = app.uids.keyAt(i);
                data.detailDefault = collectHistoryForUid(template, uid, 0, data.detailDefault);
                data.detailForeground = collectHistoryForUid(template, uid, 1, data.detailForeground);
            }
            if (size > 0) {
                data.detail = new NetworkStatsHistory(data.detailForeground.getBucketDuration());
                data.detail.recordEntireHistory(data.detailDefault);
                data.detail.recordEntireHistory(data.detailForeground);
            } else {
                data.detailDefault = new NetworkStatsHistory((long) UptimeTracker.MINIMUM_SNAPSHOT_INTERVAL_MS);
                data.detailForeground = new NetworkStatsHistory((long) UptimeTracker.MINIMUM_SNAPSHOT_INTERVAL_MS);
                data.detail = new NetworkStatsHistory((long) UptimeTracker.MINIMUM_SNAPSHOT_INTERVAL_MS);
            }
        }
        return data;
    }

    @Override // android.content.Loader
    protected void onStopLoading() {
        super.onStopLoading();
        cancelLoad();
    }

    @Override // android.content.Loader
    protected void onReset() {
        super.onReset();
        cancelLoad();
    }

    private NetworkStatsHistory collectHistoryForUid(NetworkTemplate template, int uid, int set, NetworkStatsHistory existing) throws RemoteException {
        NetworkStatsHistory history = this.mSession.getHistoryForUid(template, uid, set, 0, 10);
        if (existing != null) {
            existing.recordEntireHistory(history);
            return existing;
        }
        return history;
    }
}
