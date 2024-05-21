package com.android.settingslib.net;

import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.INetworkStatsService;
import android.net.INetworkStatsSession;
import android.net.NetworkPolicy;
import android.net.NetworkPolicyManager;
import android.net.NetworkTemplate;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.support.v4.media.session.PlaybackStateCompat;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.Range;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.ArrayUtils;
import java.time.ZonedDateTime;
import java.util.Formatter;
import java.util.Iterator;
import java.util.Locale;
/* loaded from: classes3.dex */
public class DataUsageController {
    private static final int FIELDS = 10;
    private Callback mCallback;
    private final ConnectivityManager mConnectivityManager;
    private final Context mContext;
    private NetworkNameProvider mNetworkController;
    private final NetworkStatsManager mNetworkStatsManager;
    private final NetworkPolicyManager mPolicyManager;
    private INetworkStatsSession mSession;
    private final INetworkStatsService mStatsService = INetworkStatsService.Stub.asInterface(ServiceManager.getService("netstats"));
    private int mSubscriptionId = -1;
    private static final String TAG = "DataUsageController";
    private static final boolean DEBUG = Log.isLoggable(TAG, 3);
    private static final StringBuilder PERIOD_BUILDER = new StringBuilder(50);
    private static final Formatter PERIOD_FORMATTER = new Formatter(PERIOD_BUILDER, Locale.getDefault());

    /* loaded from: classes3.dex */
    public interface Callback {
        void onMobileDataEnabled(boolean z);
    }

    /* loaded from: classes3.dex */
    public static class DataUsageInfo {
        public String carrier;
        public long cycleEnd;
        public long cycleStart;
        public long limitLevel;
        public String period;
        public long startDate;
        public long usageLevel;
        public long warningLevel;
    }

    /* loaded from: classes3.dex */
    public interface NetworkNameProvider {
        String getMobileDataNetworkName();
    }

    public DataUsageController(Context context) {
        this.mContext = context;
        this.mConnectivityManager = ConnectivityManager.from(context);
        this.mPolicyManager = NetworkPolicyManager.from(this.mContext);
        this.mNetworkStatsManager = (NetworkStatsManager) context.getSystemService(NetworkStatsManager.class);
    }

    public void setNetworkController(NetworkNameProvider networkController) {
        this.mNetworkController = networkController;
    }

    public void setSubscriptionId(int subscriptionId) {
        this.mSubscriptionId = subscriptionId;
    }

    public long getDefaultWarningLevel() {
        return this.mContext.getResources().getInteger(17694969) * PlaybackStateCompat.ACTION_SET_CAPTIONING_ENABLED;
    }

    public void setCallback(Callback callback) {
        this.mCallback = callback;
    }

    private DataUsageInfo warn(String msg) {
        Log.w(TAG, "Failed to get data usage, " + msg);
        return null;
    }

    public DataUsageInfo getDataUsageInfo() {
        NetworkTemplate template = DataUsageUtils.getMobileTemplate(this.mContext, this.mSubscriptionId);
        return getDataUsageInfo(template);
    }

    public DataUsageInfo getWifiDataUsageInfo() {
        NetworkTemplate template = NetworkTemplate.buildTemplateWifiWildcard();
        return getDataUsageInfo(template);
    }

    public DataUsageInfo getDataUsageInfo(NetworkTemplate template) {
        long end;
        long start;
        NetworkPolicy policy = findNetworkPolicy(template);
        long now = System.currentTimeMillis();
        Iterator<Range<ZonedDateTime>> it = policy != null ? policy.cycleIterator() : null;
        if (it != null && it.hasNext()) {
            Range<ZonedDateTime> cycle = it.next();
            long start2 = cycle.getLower().toInstant().toEpochMilli();
            long end2 = cycle.getUpper().toInstant().toEpochMilli();
            start = start2;
            end = end2;
        } else {
            end = now;
            start = now - 2419200000L;
        }
        long totalBytes = getUsageLevel(template, start, end);
        if (totalBytes < 0) {
            return warn("no entry data");
        }
        DataUsageInfo usage = new DataUsageInfo();
        usage.startDate = start;
        usage.usageLevel = totalBytes;
        usage.period = formatDateRange(start, end);
        usage.cycleStart = start;
        usage.cycleEnd = end;
        if (policy != null) {
            usage.limitLevel = policy.limitBytes > 0 ? policy.limitBytes : 0L;
            usage.warningLevel = policy.warningBytes > 0 ? policy.warningBytes : 0L;
        } else {
            usage.warningLevel = getDefaultWarningLevel();
        }
        NetworkNameProvider networkNameProvider = this.mNetworkController;
        if (networkNameProvider != null) {
            usage.carrier = networkNameProvider.getMobileDataNetworkName();
        }
        return usage;
    }

    public long getHistoricalUsageLevel(NetworkTemplate template) {
        return getUsageLevel(template, 0L, System.currentTimeMillis());
    }

    private long getUsageLevel(NetworkTemplate template, long start, long end) {
        try {
            NetworkStats.Bucket bucket = this.mNetworkStatsManager.querySummaryForDevice(template, start, end);
            if (bucket == null) {
                Log.w(TAG, "Failed to get data usage, no entry data");
                return -1L;
            }
            return bucket.getRxBytes() + bucket.getTxBytes();
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to get data usage, remote call failed");
            return -1L;
        }
    }

    private NetworkPolicy findNetworkPolicy(NetworkTemplate template) {
        NetworkPolicy[] policies;
        NetworkPolicyManager networkPolicyManager = this.mPolicyManager;
        if (networkPolicyManager == null || template == null || (policies = networkPolicyManager.getNetworkPolicies()) == null) {
            return null;
        }
        for (NetworkPolicy policy : policies) {
            if (policy != null && template.equals(policy.template)) {
                return policy;
            }
        }
        return null;
    }

    private static String statsBucketToString(NetworkStats.Bucket bucket) {
        if (bucket == null) {
            return null;
        }
        return "Entry[bucketDuration=" + (bucket.getEndTimeStamp() - bucket.getStartTimeStamp()) + ",bucketStart=" + bucket.getStartTimeStamp() + ",rxBytes=" + bucket.getRxBytes() + ",rxPackets=" + bucket.getRxPackets() + ",txBytes=" + bucket.getTxBytes() + ",txPackets=" + bucket.getTxPackets() + ']';
    }

    @VisibleForTesting
    public TelephonyManager getTelephonyManager() {
        int subscriptionId = this.mSubscriptionId;
        if (!SubscriptionManager.isValidSubscriptionId(subscriptionId)) {
            subscriptionId = SubscriptionManager.getDefaultDataSubscriptionId();
        }
        if (!SubscriptionManager.isValidSubscriptionId(subscriptionId)) {
            int[] activeSubIds = SubscriptionManager.from(this.mContext).getActiveSubscriptionIdList();
            if (!ArrayUtils.isEmpty(activeSubIds)) {
                subscriptionId = activeSubIds[0];
            }
        }
        return TelephonyManager.from(this.mContext).createForSubscriptionId(subscriptionId);
    }

    public void setMobileDataEnabled(boolean enabled) {
        Log.d(TAG, "setMobileDataEnabled: enabled=" + enabled);
        getTelephonyManager().setDataEnabled(enabled);
        Callback callback = this.mCallback;
        if (callback != null) {
            callback.onMobileDataEnabled(enabled);
        }
    }

    public boolean isMobileDataSupported() {
        return this.mConnectivityManager.isNetworkSupported(0) && getTelephonyManager().getSimState() == 5;
    }

    public boolean isMobileDataEnabled() {
        return getTelephonyManager().isDataEnabled();
    }

    static int getNetworkType(NetworkTemplate networkTemplate) {
        if (networkTemplate == null) {
            return -1;
        }
        int matchRule = networkTemplate.getMatchRule();
        if (matchRule != 1) {
            if (matchRule != 4) {
                if (matchRule == 5) {
                    return 9;
                }
                if (matchRule == 6 || matchRule != 7) {
                    return 0;
                }
            }
            return 1;
        }
        return 0;
    }

    private String getActiveSubscriberId() {
        String actualSubscriberId = getTelephonyManager().getSubscriberId();
        return actualSubscriberId;
    }

    private String formatDateRange(long start, long end) {
        synchronized (PERIOD_BUILDER) {
            try {
                try {
                    PERIOD_BUILDER.setLength(0);
                    return DateUtils.formatDateRange(this.mContext, PERIOD_FORMATTER, start, end, 65552, null).toString();
                } catch (Throwable th) {
                    th = th;
                    throw th;
                }
            } catch (Throwable th2) {
                th = th2;
            }
        }
    }
}
