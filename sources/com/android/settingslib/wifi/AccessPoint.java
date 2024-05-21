package com.android.settingslib.wifi;

import android.app.AppGlobals;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkKey;
import android.net.NetworkScoreManager;
import android.net.NetworkScorerAppData;
import android.net.ScoredNetwork;
import android.net.wifi.IWifiManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkScoreCache;
import android.net.wifi.hotspot2.OsuProvider;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.net.wifi.hotspot2.ProvisioningCallback;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.mediarouter.media.SystemMediaRouteProvider;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.CollectionUtils;
import com.android.settingslib.R;
import com.android.settingslib.utils.ThreadUtils;
import com.android.settingslib.wifi.AccessPoint;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
/* loaded from: classes3.dex */
public class AccessPoint implements Comparable<AccessPoint> {
    private static final int EAP_UNKNOWN = 0;
    private static final int EAP_WPA = 1;
    private static final int EAP_WPA2_WPA3 = 2;
    public static final int HIGHER_FREQ_24GHZ = 2500;
    public static final int HIGHER_FREQ_5GHZ = 5900;
    static final String KEY_CARRIER_AP_EAP_TYPE = "key_carrier_ap_eap_type";
    static final String KEY_CARRIER_NAME = "key_carrier_name";
    static final String KEY_CONFIG = "key_config";
    static final String KEY_EAPTYPE = "eap_psktype";
    static final String KEY_FQDN = "key_fqdn";
    static final String KEY_IS_CARRIER_AP = "key_is_carrier_ap";
    static final String KEY_IS_OWE_TRANSITION_MODE = "key_is_owe_transition_mode";
    static final String KEY_IS_PSK_SAE_TRANSITION_MODE = "key_is_psk_sae_transition_mode";
    static final String KEY_NETWORKINFO = "key_networkinfo";
    public static final String KEY_PREFIX_AP = "AP:";
    public static final String KEY_PREFIX_FQDN = "FQDN:";
    public static final String KEY_PREFIX_OSU = "OSU:";
    static final String KEY_PROVIDER_FRIENDLY_NAME = "key_provider_friendly_name";
    static final String KEY_PSKTYPE = "key_psktype";
    static final String KEY_SCANRESULTS = "key_scanresults";
    static final String KEY_SCOREDNETWORKCACHE = "key_scorednetworkcache";
    static final String KEY_SECURITY = "key_security";
    static final String KEY_SPEED = "key_speed";
    static final String KEY_SSID = "key_ssid";
    static final String KEY_WIFIINFO = "key_wifiinfo";
    public static final int LOWER_FREQ_24GHZ = 2400;
    public static final int LOWER_FREQ_5GHZ = 4900;
    private static final int PSK_UNKNOWN = 0;
    private static final int PSK_WPA = 1;
    private static final int PSK_WPA2 = 2;
    private static final int PSK_WPA_WPA2 = 3;
    public static final int SECURITY_EAP = 3;
    public static final int SECURITY_EAP_SUITE_B = 6;
    public static final int SECURITY_MAX_VAL = 7;
    public static final int SECURITY_NONE = 0;
    public static final int SECURITY_OWE = 4;
    public static final int SECURITY_PSK = 2;
    public static final int SECURITY_SAE = 5;
    public static final int SECURITY_WEP = 1;
    public static final int SIGNAL_LEVELS = 5;
    static final String TAG = "SettingsLib.AccessPoint";
    public static final int UNREACHABLE_RSSI = Integer.MIN_VALUE;
    static final AtomicInteger sLastId = new AtomicInteger(0);
    private String bssid;
    AccessPointListener mAccessPointListener;
    private int mCarrierApEapType;
    private String mCarrierName;
    private WifiConfiguration mConfig;
    private WifiManager.ActionListener mConnectListener;
    private final Context mContext;
    private int mEapType;
    @GuardedBy("mLock")
    private final ArraySet<ScanResult> mExtraScanResults;
    private String mFqdn;
    private WifiInfo mInfo;
    private boolean mIsCarrierAp;
    private boolean mIsOweTransitionMode;
    private boolean mIsPskSaeTransitionMode;
    private boolean mIsRoaming;
    private boolean mIsScoredNetworkMetered;
    private String mKey;
    private final Object mLock;
    private NetworkInfo mNetworkInfo;
    private String mOsuFailure;
    private OsuProvider mOsuProvider;
    private boolean mOsuProvisioningComplete;
    private String mOsuStatus;
    private String mProviderFriendlyName;
    private int mRssi;
    @GuardedBy("mLock")
    private final ArraySet<ScanResult> mScanResults;
    private final Map<String, TimestampedScoredNetwork> mScoredNetworkCache;
    private int mSpeed;
    private Object mTag;
    private WifiManager mWifiManager;
    private int networkId;
    private int pskType;
    private int security;
    private String ssid;

    /* loaded from: classes3.dex */
    public interface AccessPointListener {
        void onAccessPointChanged(AccessPoint accessPoint);

        void onLevelChanged(AccessPoint accessPoint);
    }

    @Retention(RetentionPolicy.SOURCE)
    /* loaded from: classes3.dex */
    public @interface Speed {
        public static final int FAST = 20;
        public static final int MODERATE = 10;
        public static final int NONE = 0;
        public static final int SLOW = 5;
        public static final int VERY_FAST = 30;
    }

    public AccessPoint(Context context, Bundle savedState) {
        this.mLock = new Object();
        this.mScanResults = new ArraySet<>();
        this.mExtraScanResults = new ArraySet<>();
        this.mScoredNetworkCache = new HashMap();
        this.networkId = -1;
        this.pskType = 0;
        this.mEapType = 0;
        this.mRssi = Integer.MIN_VALUE;
        this.mSpeed = 0;
        this.mIsScoredNetworkMetered = false;
        this.mIsRoaming = false;
        this.mIsCarrierAp = false;
        this.mOsuProvisioningComplete = false;
        this.mIsPskSaeTransitionMode = false;
        this.mIsOweTransitionMode = false;
        this.mCarrierApEapType = -1;
        this.mCarrierName = null;
        this.mContext = context;
        if (savedState.containsKey(KEY_CONFIG)) {
            this.mConfig = (WifiConfiguration) savedState.getParcelable(KEY_CONFIG);
        }
        WifiConfiguration wifiConfiguration = this.mConfig;
        if (wifiConfiguration != null) {
            loadConfig(wifiConfiguration);
        }
        if (savedState.containsKey(KEY_SSID)) {
            this.ssid = savedState.getString(KEY_SSID);
        }
        if (savedState.containsKey(KEY_SECURITY)) {
            this.security = savedState.getInt(KEY_SECURITY);
        }
        if (savedState.containsKey(KEY_SPEED)) {
            this.mSpeed = savedState.getInt(KEY_SPEED);
        }
        if (savedState.containsKey(KEY_PSKTYPE)) {
            this.pskType = savedState.getInt(KEY_PSKTYPE);
        }
        if (savedState.containsKey(KEY_EAPTYPE)) {
            this.mEapType = savedState.getInt(KEY_EAPTYPE);
        }
        this.mInfo = (WifiInfo) savedState.getParcelable(KEY_WIFIINFO);
        if (savedState.containsKey(KEY_NETWORKINFO)) {
            this.mNetworkInfo = (NetworkInfo) savedState.getParcelable(KEY_NETWORKINFO);
        }
        if (savedState.containsKey(KEY_SCANRESULTS)) {
            Parcelable[] scanResults = savedState.getParcelableArray(KEY_SCANRESULTS);
            this.mScanResults.clear();
            for (Parcelable result : scanResults) {
                this.mScanResults.add((ScanResult) result);
            }
        }
        if (savedState.containsKey(KEY_SCOREDNETWORKCACHE)) {
            ArrayList<TimestampedScoredNetwork> scoredNetworkArrayList = savedState.getParcelableArrayList(KEY_SCOREDNETWORKCACHE);
            Iterator<TimestampedScoredNetwork> it = scoredNetworkArrayList.iterator();
            while (it.hasNext()) {
                TimestampedScoredNetwork timedScore = it.next();
                this.mScoredNetworkCache.put(timedScore.getScore().networkKey.wifiKey.bssid, timedScore);
            }
        }
        if (savedState.containsKey(KEY_FQDN)) {
            this.mFqdn = savedState.getString(KEY_FQDN);
        }
        if (savedState.containsKey(KEY_PROVIDER_FRIENDLY_NAME)) {
            this.mProviderFriendlyName = savedState.getString(KEY_PROVIDER_FRIENDLY_NAME);
        }
        if (savedState.containsKey(KEY_IS_CARRIER_AP)) {
            this.mIsCarrierAp = savedState.getBoolean(KEY_IS_CARRIER_AP);
        }
        if (savedState.containsKey(KEY_CARRIER_AP_EAP_TYPE)) {
            this.mCarrierApEapType = savedState.getInt(KEY_CARRIER_AP_EAP_TYPE);
        }
        if (savedState.containsKey(KEY_CARRIER_NAME)) {
            this.mCarrierName = savedState.getString(KEY_CARRIER_NAME);
        }
        if (savedState.containsKey(KEY_IS_PSK_SAE_TRANSITION_MODE)) {
            this.mIsPskSaeTransitionMode = savedState.getBoolean(KEY_IS_PSK_SAE_TRANSITION_MODE);
        }
        if (savedState.containsKey(KEY_IS_OWE_TRANSITION_MODE)) {
            this.mIsOweTransitionMode = savedState.getBoolean(KEY_IS_OWE_TRANSITION_MODE);
        }
        update(this.mConfig, this.mInfo, this.mNetworkInfo);
        updateKey();
        updateBestRssiInfo();
    }

    public AccessPoint(Context context, WifiConfiguration config) {
        this.mLock = new Object();
        this.mScanResults = new ArraySet<>();
        this.mExtraScanResults = new ArraySet<>();
        this.mScoredNetworkCache = new HashMap();
        this.networkId = -1;
        this.pskType = 0;
        this.mEapType = 0;
        this.mRssi = Integer.MIN_VALUE;
        this.mSpeed = 0;
        this.mIsScoredNetworkMetered = false;
        this.mIsRoaming = false;
        this.mIsCarrierAp = false;
        this.mOsuProvisioningComplete = false;
        this.mIsPskSaeTransitionMode = false;
        this.mIsOweTransitionMode = false;
        this.mCarrierApEapType = -1;
        this.mCarrierName = null;
        this.mContext = context;
        loadConfig(config);
        updateKey();
    }

    public AccessPoint(Context context, PasspointConfiguration config) {
        this.mLock = new Object();
        this.mScanResults = new ArraySet<>();
        this.mExtraScanResults = new ArraySet<>();
        this.mScoredNetworkCache = new HashMap();
        this.networkId = -1;
        this.pskType = 0;
        this.mEapType = 0;
        this.mRssi = Integer.MIN_VALUE;
        this.mSpeed = 0;
        this.mIsScoredNetworkMetered = false;
        this.mIsRoaming = false;
        this.mIsCarrierAp = false;
        this.mOsuProvisioningComplete = false;
        this.mIsPskSaeTransitionMode = false;
        this.mIsOweTransitionMode = false;
        this.mCarrierApEapType = -1;
        this.mCarrierName = null;
        this.mContext = context;
        this.mFqdn = config.getHomeSp().getFqdn();
        this.mProviderFriendlyName = config.getHomeSp().getFriendlyName();
        updateKey();
    }

    public AccessPoint(@NonNull Context context, @NonNull WifiConfiguration config, Collection<ScanResult> homeScans, Collection<ScanResult> roamingScans) {
        this.mLock = new Object();
        this.mScanResults = new ArraySet<>();
        this.mExtraScanResults = new ArraySet<>();
        this.mScoredNetworkCache = new HashMap();
        this.networkId = -1;
        this.pskType = 0;
        this.mEapType = 0;
        this.mRssi = Integer.MIN_VALUE;
        this.mSpeed = 0;
        this.mIsScoredNetworkMetered = false;
        this.mIsRoaming = false;
        this.mIsCarrierAp = false;
        this.mOsuProvisioningComplete = false;
        this.mIsPskSaeTransitionMode = false;
        this.mIsOweTransitionMode = false;
        this.mCarrierApEapType = -1;
        this.mCarrierName = null;
        this.mContext = context;
        this.networkId = config.networkId;
        this.mConfig = config;
        this.mFqdn = config.FQDN;
        setScanResultsPasspoint(homeScans, roamingScans);
        updateKey();
    }

    public AccessPoint(@NonNull Context context, @NonNull OsuProvider provider, @NonNull Collection<ScanResult> results) {
        this.mLock = new Object();
        this.mScanResults = new ArraySet<>();
        this.mExtraScanResults = new ArraySet<>();
        this.mScoredNetworkCache = new HashMap();
        this.networkId = -1;
        this.pskType = 0;
        this.mEapType = 0;
        this.mRssi = Integer.MIN_VALUE;
        this.mSpeed = 0;
        this.mIsScoredNetworkMetered = false;
        this.mIsRoaming = false;
        this.mIsCarrierAp = false;
        this.mOsuProvisioningComplete = false;
        this.mIsPskSaeTransitionMode = false;
        this.mIsOweTransitionMode = false;
        this.mCarrierApEapType = -1;
        this.mCarrierName = null;
        this.mContext = context;
        this.mOsuProvider = provider;
        setScanResults(results);
        updateKey();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public AccessPoint(Context context, Collection<ScanResult> results) {
        this.mLock = new Object();
        this.mScanResults = new ArraySet<>();
        this.mExtraScanResults = new ArraySet<>();
        this.mScoredNetworkCache = new HashMap();
        this.networkId = -1;
        this.pskType = 0;
        this.mEapType = 0;
        this.mRssi = Integer.MIN_VALUE;
        this.mSpeed = 0;
        this.mIsScoredNetworkMetered = false;
        this.mIsRoaming = false;
        this.mIsCarrierAp = false;
        this.mOsuProvisioningComplete = false;
        this.mIsPskSaeTransitionMode = false;
        this.mIsOweTransitionMode = false;
        this.mCarrierApEapType = -1;
        this.mCarrierName = null;
        this.mContext = context;
        setScanResults(results);
        updateKey();
    }

    @VisibleForTesting
    void loadConfig(WifiConfiguration config) {
        this.ssid = config.SSID == null ? "" : removeDoubleQuotes(config.SSID);
        this.bssid = config.BSSID;
        this.security = getSecurity(config);
        this.networkId = config.networkId;
        this.mConfig = config;
    }

    private void updateKey() {
        if (isPasspoint()) {
            this.mKey = getKey(this.mConfig);
        } else if (isPasspointConfig()) {
            this.mKey = getKey(this.mFqdn);
        } else if (isOsuProvider()) {
            this.mKey = getKey(this.mOsuProvider);
        } else {
            this.mKey = getKey(getSsidStr(), getBssid(), getSecurity());
        }
    }

    @Override // java.lang.Comparable
    public int compareTo(@NonNull AccessPoint other) {
        if (!isActive() || other.isActive()) {
            if (isActive() || !other.isActive()) {
                if (!isReachable() || other.isReachable()) {
                    if (isReachable() || !other.isReachable()) {
                        if (!isSaved() || other.isSaved()) {
                            if (isSaved() || !other.isSaved()) {
                                if (getSpeed() != other.getSpeed()) {
                                    return other.getSpeed() - getSpeed();
                                }
                                int difference = WifiManager.calculateSignalLevel(other.mRssi, 5) - WifiManager.calculateSignalLevel(this.mRssi, 5);
                                if (difference != 0) {
                                    return difference;
                                }
                                int difference2 = getTitle().compareToIgnoreCase(other.getTitle());
                                if (difference2 != 0) {
                                    return difference2;
                                }
                                return getSsidStr().compareTo(other.getSsidStr());
                            }
                            return 1;
                        }
                        return -1;
                    }
                    return 1;
                }
                return -1;
            }
            return 1;
        }
        return -1;
    }

    public boolean equals(Object other) {
        return (other instanceof AccessPoint) && compareTo((AccessPoint) other) == 0;
    }

    public int hashCode() {
        WifiInfo wifiInfo = this.mInfo;
        int result = wifiInfo != null ? 0 + (wifiInfo.hashCode() * 13) : 0;
        return result + (this.mRssi * 19) + (this.networkId * 23) + (this.ssid.hashCode() * 29);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("AccessPoint(");
        StringBuilder builder = sb.append(this.ssid);
        if (this.bssid != null) {
            builder.append(":");
            builder.append(this.bssid);
        }
        if (isSaved()) {
            builder.append(',');
            builder.append("saved");
        }
        if (isActive()) {
            builder.append(',');
            builder.append("active");
        }
        if (isEphemeral()) {
            builder.append(',');
            builder.append("ephemeral");
        }
        if (isConnectable()) {
            builder.append(',');
            builder.append("connectable");
        }
        int i = this.security;
        if (i != 0 && i != 4) {
            builder.append(',');
            builder.append(securityToString(this.security, this.pskType));
        }
        builder.append(",level=");
        builder.append(getLevel());
        if (this.mSpeed != 0) {
            builder.append(",speed=");
            builder.append(this.mSpeed);
        }
        builder.append(",metered=");
        builder.append(isMetered());
        if (isVerboseLoggingEnabled()) {
            builder.append(",rssi=");
            builder.append(this.mRssi);
            synchronized (this.mLock) {
                builder.append(",scan cache size=");
                builder.append(this.mScanResults.size() + this.mExtraScanResults.size());
            }
        }
        builder.append(')');
        return builder.toString();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean update(WifiNetworkScoreCache scoreCache, boolean scoringUiEnabled, long maxScoreCacheAgeMillis) {
        boolean scoreChanged = false;
        if (scoringUiEnabled) {
            scoreChanged = updateScores(scoreCache, maxScoreCacheAgeMillis);
        }
        return updateMetered(scoreCache) || scoreChanged;
    }

    private boolean updateScores(WifiNetworkScoreCache scoreCache, long maxScoreCacheAgeMillis) {
        long nowMillis = SystemClock.elapsedRealtime();
        synchronized (this.mLock) {
            Iterator<ScanResult> it = this.mScanResults.iterator();
            while (it.hasNext()) {
                ScanResult result = it.next();
                ScoredNetwork score = scoreCache.getScoredNetwork(result);
                if (score != null) {
                    TimestampedScoredNetwork timedScore = this.mScoredNetworkCache.get(result.BSSID);
                    if (timedScore == null) {
                        this.mScoredNetworkCache.put(result.BSSID, new TimestampedScoredNetwork(score, nowMillis));
                    } else {
                        timedScore.update(score, nowMillis);
                    }
                }
            }
        }
        final long evictionCutoff = nowMillis - maxScoreCacheAgeMillis;
        final Iterator<TimestampedScoredNetwork> iterator = this.mScoredNetworkCache.values().iterator();
        iterator.forEachRemaining(new Consumer() { // from class: com.android.settingslib.wifi.-$$Lambda$AccessPoint$OIXfUc7y1PqI_zmQ3STe_086YzY
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                AccessPoint.lambda$updateScores$0(evictionCutoff, iterator, (TimestampedScoredNetwork) obj);
            }
        });
        return updateSpeed();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ void lambda$updateScores$0(long evictionCutoff, Iterator iterator, TimestampedScoredNetwork timestampedScoredNetwork) {
        if (timestampedScoredNetwork.getUpdatedTimestampMillis() < evictionCutoff) {
            iterator.remove();
        }
    }

    private boolean updateSpeed() {
        int oldSpeed = this.mSpeed;
        this.mSpeed = generateAverageSpeedForSsid();
        boolean changed = oldSpeed != this.mSpeed;
        if (isVerboseLoggingEnabled() && changed) {
            Log.i(TAG, String.format("%s: Set speed to %d", this.ssid, Integer.valueOf(this.mSpeed)));
        }
        return changed;
    }

    private int generateAverageSpeedForSsid() {
        if (this.mScoredNetworkCache.isEmpty()) {
            return 0;
        }
        if (Log.isLoggable(TAG, 3)) {
            Log.d(TAG, String.format("Generating fallbackspeed for %s using cache: %s", getSsidStr(), this.mScoredNetworkCache));
        }
        int count = 0;
        int totalSpeed = 0;
        for (TimestampedScoredNetwork timedScore : this.mScoredNetworkCache.values()) {
            int speed = timedScore.getScore().calculateBadge(this.mRssi);
            if (speed != 0) {
                count++;
                totalSpeed += speed;
            }
        }
        int speed2 = count == 0 ? 0 : totalSpeed / count;
        if (isVerboseLoggingEnabled()) {
            Log.i(TAG, String.format("%s generated fallback speed is: %d", getSsidStr(), Integer.valueOf(speed2)));
        }
        return roundToClosestSpeedEnum(speed2);
    }

    private boolean updateMetered(WifiNetworkScoreCache scoreCache) {
        WifiInfo wifiInfo;
        boolean oldMetering = this.mIsScoredNetworkMetered;
        this.mIsScoredNetworkMetered = false;
        if (isActive() && (wifiInfo = this.mInfo) != null) {
            NetworkKey key = NetworkKey.createFromWifiInfo(wifiInfo);
            ScoredNetwork score = scoreCache.getScoredNetwork(key);
            if (score != null) {
                this.mIsScoredNetworkMetered |= score.meteredHint;
            }
        } else {
            synchronized (this.mLock) {
                Iterator<ScanResult> it = this.mScanResults.iterator();
                while (it.hasNext()) {
                    ScanResult result = it.next();
                    ScoredNetwork score2 = scoreCache.getScoredNetwork(result);
                    if (score2 != null) {
                        this.mIsScoredNetworkMetered |= score2.meteredHint;
                    }
                }
            }
        }
        return oldMetering == this.mIsScoredNetworkMetered;
    }

    public static String getKey(Context context, ScanResult result) {
        return getKey(result.SSID, result.BSSID, getSecurity(context, result));
    }

    public static String getKey(WifiConfiguration config) {
        if (config.isPasspoint()) {
            return getKey(config.FQDN);
        }
        return getKey(removeDoubleQuotes(config.SSID), config.BSSID, getSecurity(config));
    }

    public static String getKey(String fqdn) {
        return KEY_PREFIX_FQDN + fqdn;
    }

    public static String getKey(OsuProvider provider) {
        return KEY_PREFIX_OSU + provider.getFriendlyName() + ',' + provider.getServerUri();
    }

    private static String getKey(String ssid, String bssid, int security) {
        StringBuilder builder = new StringBuilder();
        builder.append(KEY_PREFIX_AP);
        if (TextUtils.isEmpty(ssid)) {
            builder.append(bssid);
        } else {
            builder.append(ssid);
        }
        builder.append(',');
        builder.append(security);
        return builder.toString();
    }

    public String getKey() {
        return this.mKey;
    }

    public boolean matches(AccessPoint other) {
        if (isPasspoint() || isPasspointConfig() || isOsuProvider()) {
            return getKey().equals(other.getKey());
        }
        if (isSameSsidOrBssid(other)) {
            int otherApSecurity = other.getSecurity();
            if (this.mIsPskSaeTransitionMode) {
                if ((otherApSecurity == 5 && getWifiManager().isWpa3SaeSupported()) || otherApSecurity == 2) {
                    return true;
                }
            } else {
                int i = this.security;
                if ((i == 5 || i == 2) && other.isPskSaeTransitionMode()) {
                    return true;
                }
            }
            if (this.mIsOweTransitionMode) {
                if ((otherApSecurity == 4 && getWifiManager().isEnhancedOpenSupported()) || otherApSecurity == 0) {
                    return true;
                }
            } else {
                int i2 = this.security;
                if ((i2 == 4 || i2 == 0) && other.isOweTransitionMode()) {
                    return true;
                }
            }
            return this.security == other.getSecurity();
        }
        return false;
    }

    public boolean matches(WifiConfiguration config) {
        WifiConfiguration wifiConfiguration;
        if (config.isPasspoint()) {
            return isPasspoint() && config.FQDN.equals(this.mConfig.FQDN);
        } else if (this.ssid.equals(removeDoubleQuotes(config.SSID)) && ((wifiConfiguration = this.mConfig) == null || wifiConfiguration.shared == config.shared)) {
            int configSecurity = getSecurity(config);
            if (this.mIsPskSaeTransitionMode && ((configSecurity == 5 && getWifiManager().isWpa3SaeSupported()) || configSecurity == 2)) {
                return true;
            }
            return (this.mIsOweTransitionMode && ((configSecurity == 4 && getWifiManager().isEnhancedOpenSupported()) || configSecurity == 0)) || this.security == getSecurity(config);
        } else {
            return false;
        }
    }

    private boolean matches(WifiConfiguration config, WifiInfo wifiInfo) {
        if (config == null || wifiInfo == null) {
            return false;
        }
        if (!config.isPasspoint() && !isSameSsidOrBssid(wifiInfo)) {
            return false;
        }
        return matches(config);
    }

    @VisibleForTesting
    boolean matches(ScanResult scanResult) {
        if (scanResult == null) {
            return false;
        }
        if (isPasspoint() || isOsuProvider()) {
            throw new IllegalStateException("Should not matches a Passpoint by ScanResult");
        }
        if (!isSameSsidOrBssid(scanResult)) {
            return false;
        }
        if (this.mIsPskSaeTransitionMode) {
            if ((scanResult.capabilities.contains("SAE") && getWifiManager().isWpa3SaeSupported()) || scanResult.capabilities.contains("PSK")) {
                return true;
            }
        } else {
            int i = this.security;
            if ((i == 5 || i == 2) && isPskSaeTransitionMode(scanResult)) {
                return true;
            }
        }
        if (this.mIsOweTransitionMode) {
            int scanResultSccurity = getSecurity(this.mContext, scanResult);
            if ((scanResultSccurity == 4 && getWifiManager().isEnhancedOpenSupported()) || scanResultSccurity == 0) {
                return true;
            }
        } else {
            int i2 = this.security;
            if ((i2 == 4 || i2 == 0) && isOweTransitionMode(scanResult)) {
                return true;
            }
        }
        return this.security == getSecurity(this.mContext, scanResult);
    }

    public WifiConfiguration getConfig() {
        return this.mConfig;
    }

    public String getPasspointFqdn() {
        return this.mFqdn;
    }

    public void clearConfig() {
        this.mConfig = null;
        this.networkId = -1;
    }

    public WifiInfo getInfo() {
        return this.mInfo;
    }

    public int getLevel() {
        return WifiManager.calculateSignalLevel(this.mRssi, 5);
    }

    public int getRssi() {
        return this.mRssi;
    }

    public Set<ScanResult> getScanResults() {
        Set<ScanResult> allScans = new ArraySet<>();
        synchronized (this.mLock) {
            allScans.addAll(this.mScanResults);
            allScans.addAll(this.mExtraScanResults);
        }
        return allScans;
    }

    public Map<String, TimestampedScoredNetwork> getScoredNetworkCache() {
        return this.mScoredNetworkCache;
    }

    private void updateBestRssiInfo() {
        int i;
        if (isActive()) {
            return;
        }
        ScanResult bestResult = null;
        int bestRssi = Integer.MIN_VALUE;
        synchronized (this.mLock) {
            Iterator<ScanResult> it = this.mScanResults.iterator();
            while (it.hasNext()) {
                ScanResult result = it.next();
                if (result.level > bestRssi) {
                    bestRssi = result.level;
                    bestResult = result;
                }
            }
        }
        if (bestRssi != Integer.MIN_VALUE && (i = this.mRssi) != Integer.MIN_VALUE) {
            this.mRssi = (i + bestRssi) / 2;
        } else {
            this.mRssi = bestRssi;
        }
        if (bestResult != null) {
            this.ssid = bestResult.SSID;
            this.bssid = bestResult.BSSID;
            this.security = getSecurity(this.mContext, bestResult);
            int i2 = this.security;
            if (i2 == 2 || i2 == 5) {
                this.pskType = getPskType(bestResult);
            }
            if (this.security == 3) {
                this.mEapType = getEapType(bestResult);
            }
            this.mIsPskSaeTransitionMode = isPskSaeTransitionMode(bestResult);
            this.mIsOweTransitionMode = isOweTransitionMode(bestResult);
            this.mIsCarrierAp = bestResult.isCarrierAp;
            this.mCarrierApEapType = bestResult.carrierApEapType;
            this.mCarrierName = bestResult.carrierName;
        }
        if (isPasspoint()) {
            this.mConfig.SSID = convertToQuotedString(this.ssid);
        }
    }

    public boolean isMetered() {
        return this.mIsScoredNetworkMetered || WifiConfiguration.isMetered(this.mConfig, this.mInfo);
    }

    public NetworkInfo getNetworkInfo() {
        return this.mNetworkInfo;
    }

    public int getSecurity() {
        return this.security;
    }

    public String getSecurityString(boolean concise) {
        Context context = this.mContext;
        if (isPasspoint() || isPasspointConfig()) {
            return concise ? context.getString(R.string.wifi_security_short_eap) : context.getString(R.string.wifi_security_eap);
        } else if (this.mIsPskSaeTransitionMode) {
            return concise ? context.getString(R.string.wifi_security_short_psk_sae) : context.getString(R.string.wifi_security_psk_sae);
        } else {
            switch (this.security) {
                case 1:
                    return concise ? context.getString(R.string.wifi_security_short_wep) : context.getString(R.string.wifi_security_wep);
                case 2:
                    int i = this.pskType;
                    return i != 1 ? i != 2 ? i != 3 ? concise ? context.getString(R.string.wifi_security_short_psk_generic) : context.getString(R.string.wifi_security_psk_generic) : concise ? context.getString(R.string.wifi_security_short_wpa_wpa2) : context.getString(R.string.wifi_security_wpa_wpa2) : concise ? context.getString(R.string.wifi_security_short_wpa2) : context.getString(R.string.wifi_security_wpa2) : concise ? context.getString(R.string.wifi_security_short_wpa) : context.getString(R.string.wifi_security_wpa);
                case 3:
                    int i2 = this.mEapType;
                    if (i2 == 1) {
                        return concise ? context.getString(R.string.wifi_security_short_eap_wpa) : context.getString(R.string.wifi_security_eap_wpa);
                    } else if (i2 == 2) {
                        if (concise) {
                            return context.getString(R.string.wifi_security_short_eap_wpa2_wpa3);
                        }
                        return context.getString(R.string.wifi_security_eap_wpa2_wpa3);
                    } else if (concise) {
                        return context.getString(R.string.wifi_security_short_eap);
                    } else {
                        return context.getString(R.string.wifi_security_eap);
                    }
                case 4:
                    return concise ? context.getString(R.string.wifi_security_short_owe) : context.getString(R.string.wifi_security_owe);
                case 5:
                    return concise ? context.getString(R.string.wifi_security_short_sae) : context.getString(R.string.wifi_security_sae);
                case 6:
                    return concise ? context.getString(R.string.wifi_security_short_eap_suiteb) : context.getString(R.string.wifi_security_eap_suiteb);
                default:
                    return concise ? "" : context.getString(R.string.wifi_security_none);
            }
        }
    }

    public String getSsidStr() {
        return this.ssid;
    }

    public String getBssid() {
        return this.bssid;
    }

    public CharSequence getSsid() {
        return this.ssid;
    }

    @Deprecated
    public String getConfigName() {
        WifiConfiguration wifiConfiguration = this.mConfig;
        if (wifiConfiguration != null && wifiConfiguration.isPasspoint()) {
            return this.mConfig.providerFriendlyName;
        }
        if (this.mFqdn != null) {
            return this.mProviderFriendlyName;
        }
        return this.ssid;
    }

    public NetworkInfo.DetailedState getDetailedState() {
        NetworkInfo networkInfo = this.mNetworkInfo;
        if (networkInfo != null) {
            return networkInfo.getDetailedState();
        }
        Log.w(TAG, "NetworkInfo is null, cannot return detailed state");
        return null;
    }

    public boolean isCarrierAp() {
        return this.mIsCarrierAp;
    }

    public int getCarrierApEapType() {
        return this.mCarrierApEapType;
    }

    public String getCarrierName() {
        return this.mCarrierName;
    }

    public String getSavedNetworkSummary() {
        WifiConfiguration config = this.mConfig;
        if (config != null) {
            PackageManager pm = this.mContext.getPackageManager();
            String systemName = pm.getNameForUid(1000);
            int userId = UserHandle.getUserId(config.creatorUid);
            ApplicationInfo appInfo = null;
            if (config.creatorName != null && config.creatorName.equals(systemName)) {
                appInfo = this.mContext.getApplicationInfo();
            } else {
                try {
                    IPackageManager ipm = AppGlobals.getPackageManager();
                    appInfo = ipm.getApplicationInfo(config.creatorName, 0, userId);
                } catch (RemoteException e) {
                }
            }
            return (appInfo == null || appInfo.packageName.equals(this.mContext.getString(R.string.settings_package)) || appInfo.packageName.equals(this.mContext.getString(R.string.certinstaller_package))) ? "" : this.mContext.getString(R.string.saved_network, appInfo.loadLabel(pm));
        }
        return "";
    }

    public String getTitle() {
        if (isPasspoint()) {
            return this.mConfig.providerFriendlyName;
        }
        if (isPasspointConfig()) {
            return this.mProviderFriendlyName;
        }
        if (isOsuProvider()) {
            return this.mOsuProvider.getFriendlyName();
        }
        return getSsidStr();
    }

    public String getSummary() {
        return getSettingsSummary();
    }

    public String getSettingsSummary() {
        return getSettingsSummary(false);
    }

    public String getSettingsSummary(boolean convertSavedAsDisconnected) {
        int messageID;
        StringBuilder summary = new StringBuilder();
        if (isOsuProvider()) {
            if (this.mOsuProvisioningComplete) {
                summary.append(this.mContext.getString(R.string.osu_sign_up_complete));
            } else {
                String str = this.mOsuFailure;
                if (str != null) {
                    summary.append(str);
                } else {
                    String str2 = this.mOsuStatus;
                    if (str2 != null) {
                        summary.append(str2);
                    } else {
                        summary.append(this.mContext.getString(R.string.tap_to_sign_up));
                    }
                }
            }
        } else if (isActive()) {
            if (getDetailedState() == NetworkInfo.DetailedState.CONNECTED && this.mIsCarrierAp) {
                summary.append(String.format(this.mContext.getString(R.string.connected_via_carrier), this.mCarrierName));
            } else {
                Context context = this.mContext;
                NetworkInfo.DetailedState detailedState = getDetailedState();
                WifiInfo wifiInfo = this.mInfo;
                boolean z = wifiInfo != null && wifiInfo.isEphemeral();
                WifiInfo wifiInfo2 = this.mInfo;
                summary.append(getSummary(context, null, detailedState, z, wifiInfo2 != null ? wifiInfo2.getNetworkSuggestionOrSpecifierPackageName() : null));
            }
        } else {
            WifiConfiguration wifiConfiguration = this.mConfig;
            if (wifiConfiguration == null || !wifiConfiguration.hasNoInternetAccess()) {
                WifiConfiguration wifiConfiguration2 = this.mConfig;
                if (wifiConfiguration2 == null || wifiConfiguration2.getNetworkSelectionStatus().isNetworkEnabled()) {
                    WifiConfiguration wifiConfiguration3 = this.mConfig;
                    if (wifiConfiguration3 != null && wifiConfiguration3.getNetworkSelectionStatus().isNotRecommended()) {
                        summary.append(this.mContext.getString(R.string.wifi_disabled_by_recommendation_provider));
                    } else if (this.mIsCarrierAp) {
                        summary.append(String.format(this.mContext.getString(R.string.available_via_carrier), this.mCarrierName));
                    } else if (!isReachable()) {
                        summary.append(this.mContext.getString(R.string.wifi_not_in_range));
                    } else {
                        WifiConfiguration wifiConfiguration4 = this.mConfig;
                        if (wifiConfiguration4 != null) {
                            if (wifiConfiguration4.recentFailure.getAssociationStatus() == 17) {
                                summary.append(this.mContext.getString(R.string.wifi_ap_unable_to_handle_new_sta));
                            } else if (convertSavedAsDisconnected) {
                                summary.append(this.mContext.getString(R.string.wifi_disconnected));
                            } else {
                                summary.append(this.mContext.getString(R.string.wifi_remembered));
                            }
                        }
                    }
                } else {
                    WifiConfiguration.NetworkSelectionStatus networkStatus = this.mConfig.getNetworkSelectionStatus();
                    int networkSelectionDisableReason = networkStatus.getNetworkSelectionDisableReason();
                    if (networkSelectionDisableReason == 2) {
                        summary.append(this.mContext.getString(R.string.wifi_disabled_generic));
                    } else if (networkSelectionDisableReason == 3) {
                        summary.append(this.mContext.getString(R.string.wifi_disabled_password_failure));
                    } else if (networkSelectionDisableReason == 4 || networkSelectionDisableReason == 5) {
                        summary.append(this.mContext.getString(R.string.wifi_disabled_network_failure));
                    } else if (networkSelectionDisableReason == 13) {
                        summary.append(this.mContext.getString(R.string.wifi_check_password_try_again));
                    }
                }
            } else {
                if (this.mConfig.getNetworkSelectionStatus().isNetworkPermanentlyDisabled()) {
                    messageID = R.string.wifi_no_internet_no_reconnect;
                } else {
                    messageID = R.string.wifi_no_internet;
                }
                summary.append(this.mContext.getString(messageID));
            }
        }
        if (isVerboseLoggingEnabled()) {
            summary.append(WifiUtils.buildLoggingSummary(this, this.mConfig));
        }
        WifiConfiguration wifiConfiguration5 = this.mConfig;
        if (wifiConfiguration5 != null && (WifiUtils.isMeteredOverridden(wifiConfiguration5) || this.mConfig.meteredHint)) {
            return this.mContext.getResources().getString(R.string.preference_summary_default_combination, WifiUtils.getMeteredLabel(this.mContext, this.mConfig), summary.toString());
        }
        if (getSpeedLabel() != null && summary.length() != 0) {
            return this.mContext.getResources().getString(R.string.preference_summary_default_combination, getSpeedLabel(), summary.toString());
        }
        if (getSpeedLabel() != null) {
            return getSpeedLabel();
        }
        return summary.toString();
    }

    public boolean isActive() {
        NetworkInfo networkInfo = this.mNetworkInfo;
        return (networkInfo == null || (this.networkId == -1 && networkInfo.getState() == NetworkInfo.State.DISCONNECTED)) ? false : true;
    }

    public boolean isConnectable() {
        return getLevel() != -1 && getDetailedState() == null;
    }

    public boolean isEphemeral() {
        NetworkInfo networkInfo;
        WifiInfo wifiInfo = this.mInfo;
        return (wifiInfo == null || !wifiInfo.isEphemeral() || (networkInfo = this.mNetworkInfo) == null || networkInfo.getState() == NetworkInfo.State.DISCONNECTED) ? false : true;
    }

    public boolean isPasspoint() {
        WifiConfiguration wifiConfiguration = this.mConfig;
        return wifiConfiguration != null && wifiConfiguration.isPasspoint();
    }

    public boolean isPasspointConfig() {
        return this.mFqdn != null && this.mConfig == null;
    }

    public boolean isOsuProvider() {
        return this.mOsuProvider != null;
    }

    public void startOsuProvisioning(WifiManager.ActionListener connectListener) {
        this.mConnectListener = connectListener;
        getWifiManager().startSubscriptionProvisioning(this.mOsuProvider, this.mContext.getMainExecutor(), new AccessPointProvisioningCallback());
    }

    private boolean isInfoForThisAccessPoint(WifiConfiguration config, WifiInfo info) {
        if (info.isOsuAp() || this.mOsuStatus != null) {
            return info.isOsuAp() && this.mOsuStatus != null;
        } else if (info.isPasspointAp() || isPasspoint()) {
            return info.isPasspointAp() && isPasspoint() && TextUtils.equals(info.getPasspointFqdn(), this.mConfig.FQDN);
        } else {
            int i = this.networkId;
            if (i != -1) {
                return i == info.getNetworkId();
            } else if (config != null) {
                return matches(config, info);
            } else {
                return TextUtils.equals(removeDoubleQuotes(info.getSSID()), this.ssid);
            }
        }
    }

    public boolean isSaved() {
        return this.mConfig != null;
    }

    public Object getTag() {
        return this.mTag;
    }

    public void setTag(Object tag) {
        this.mTag = tag;
    }

    public void generateOpenNetworkConfig() {
        int i = this.security;
        if (i != 0 && i != 4) {
            throw new IllegalStateException();
        }
        if (this.mConfig != null) {
            return;
        }
        this.mConfig = new WifiConfiguration();
        this.mConfig.SSID = convertToQuotedString(this.ssid);
        if (this.security == 0 || !getWifiManager().isEasyConnectSupported()) {
            this.mConfig.allowedKeyManagement.set(0);
            return;
        }
        this.mConfig.allowedKeyManagement.set(9);
        this.mConfig.requirePMF = true;
    }

    public void saveWifiState(Bundle savedState) {
        if (this.ssid != null) {
            savedState.putString(KEY_SSID, getSsidStr());
        }
        savedState.putInt(KEY_SECURITY, this.security);
        savedState.putInt(KEY_SPEED, this.mSpeed);
        savedState.putInt(KEY_PSKTYPE, this.pskType);
        savedState.putInt(KEY_EAPTYPE, this.mEapType);
        WifiConfiguration wifiConfiguration = this.mConfig;
        if (wifiConfiguration != null) {
            savedState.putParcelable(KEY_CONFIG, wifiConfiguration);
        }
        savedState.putParcelable(KEY_WIFIINFO, this.mInfo);
        synchronized (this.mLock) {
            savedState.putParcelableArray(KEY_SCANRESULTS, (Parcelable[]) this.mScanResults.toArray(new Parcelable[this.mScanResults.size() + this.mExtraScanResults.size()]));
        }
        savedState.putParcelableArrayList(KEY_SCOREDNETWORKCACHE, new ArrayList<>(this.mScoredNetworkCache.values()));
        NetworkInfo networkInfo = this.mNetworkInfo;
        if (networkInfo != null) {
            savedState.putParcelable(KEY_NETWORKINFO, networkInfo);
        }
        String str = this.mFqdn;
        if (str != null) {
            savedState.putString(KEY_FQDN, str);
        }
        String str2 = this.mProviderFriendlyName;
        if (str2 != null) {
            savedState.putString(KEY_PROVIDER_FRIENDLY_NAME, str2);
        }
        savedState.putBoolean(KEY_IS_CARRIER_AP, this.mIsCarrierAp);
        savedState.putInt(KEY_CARRIER_AP_EAP_TYPE, this.mCarrierApEapType);
        savedState.putString(KEY_CARRIER_NAME, this.mCarrierName);
        savedState.putBoolean(KEY_IS_PSK_SAE_TRANSITION_MODE, this.mIsPskSaeTransitionMode);
        savedState.putBoolean(KEY_IS_OWE_TRANSITION_MODE, this.mIsOweTransitionMode);
    }

    public void setListener(AccessPointListener listener) {
        this.mAccessPointListener = listener;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void setScanResults(Collection<ScanResult> scanResults) {
        if (CollectionUtils.isEmpty(scanResults)) {
            Log.d(TAG, "Cannot set scan results to empty list");
            return;
        }
        if (this.mKey != null && !isPasspoint() && !isOsuProvider()) {
            for (ScanResult result : scanResults) {
                if (!matches(result)) {
                    Log.d(TAG, String.format("ScanResult %s\nkey of %s did not match current AP key %s", result, getKey(this.mContext, result), this.mKey));
                    return;
                }
            }
        }
        int oldLevel = getLevel();
        synchronized (this.mLock) {
            this.mScanResults.clear();
            this.mScanResults.addAll(scanResults);
        }
        updateBestRssiInfo();
        int newLevel = getLevel();
        if (newLevel > 0 && newLevel != oldLevel) {
            updateSpeed();
            ThreadUtils.postOnMainThread(new Runnable() { // from class: com.android.settingslib.wifi.-$$Lambda$AccessPoint$MkkIS1nUbezHicDMmYnviyiBJyo
                @Override // java.lang.Runnable
                public final void run() {
                    AccessPoint.this.lambda$setScanResults$1$AccessPoint();
                }
            });
        }
        ThreadUtils.postOnMainThread(new Runnable() { // from class: com.android.settingslib.wifi.-$$Lambda$AccessPoint$0Yq14aFJZLjPMzFGAvglLaxsblI
            @Override // java.lang.Runnable
            public final void run() {
                AccessPoint.this.lambda$setScanResults$2$AccessPoint();
            }
        });
    }

    public /* synthetic */ void lambda$setScanResults$1$AccessPoint() {
        AccessPointListener accessPointListener = this.mAccessPointListener;
        if (accessPointListener != null) {
            accessPointListener.onLevelChanged(this);
        }
    }

    public /* synthetic */ void lambda$setScanResults$2$AccessPoint() {
        AccessPointListener accessPointListener = this.mAccessPointListener;
        if (accessPointListener != null) {
            accessPointListener.onAccessPointChanged(this);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void setScanResultsPasspoint(Collection<ScanResult> homeScans, Collection<ScanResult> roamingScans) {
        synchronized (this.mLock) {
            this.mExtraScanResults.clear();
            if (!CollectionUtils.isEmpty(homeScans)) {
                this.mIsRoaming = false;
                if (!CollectionUtils.isEmpty(roamingScans)) {
                    this.mExtraScanResults.addAll(roamingScans);
                }
                setScanResults(homeScans);
            } else if (!CollectionUtils.isEmpty(roamingScans)) {
                this.mIsRoaming = true;
                setScanResults(roamingScans);
            }
        }
    }

    public boolean update(WifiConfiguration config, WifiInfo info, NetworkInfo networkInfo) {
        boolean updated = false;
        int oldLevel = getLevel();
        if (info != null && isInfoForThisAccessPoint(config, info)) {
            updated = this.mInfo == null;
            if (!isPasspoint() && this.mConfig != config) {
                update(config);
            }
            if (this.mRssi != info.getRssi() && info.getRssi() != -127) {
                this.mRssi = info.getRssi();
                updated = true;
            } else {
                NetworkInfo networkInfo2 = this.mNetworkInfo;
                if (networkInfo2 != null && networkInfo != null && networkInfo2.getDetailedState() != networkInfo.getDetailedState()) {
                    updated = true;
                }
            }
            this.mInfo = info;
            this.mNetworkInfo = networkInfo;
        } else if (this.mInfo != null) {
            updated = true;
            this.mInfo = null;
            this.mNetworkInfo = null;
        }
        if (updated && this.mAccessPointListener != null) {
            ThreadUtils.postOnMainThread(new Runnable() { // from class: com.android.settingslib.wifi.-$$Lambda$AccessPoint$S7H59e_8IxpVPy0V68Oc2-zX-rg
                @Override // java.lang.Runnable
                public final void run() {
                    AccessPoint.this.lambda$update$3$AccessPoint();
                }
            });
            if (oldLevel != getLevel()) {
                ThreadUtils.postOnMainThread(new Runnable() { // from class: com.android.settingslib.wifi.-$$Lambda$AccessPoint$QW-1Uw0oxoaKqUtEtPO0oPvH5ng
                    @Override // java.lang.Runnable
                    public final void run() {
                        AccessPoint.this.lambda$update$4$AccessPoint();
                    }
                });
            }
        }
        return updated;
    }

    public /* synthetic */ void lambda$update$3$AccessPoint() {
        AccessPointListener accessPointListener = this.mAccessPointListener;
        if (accessPointListener != null) {
            accessPointListener.onAccessPointChanged(this);
        }
    }

    public /* synthetic */ void lambda$update$4$AccessPoint() {
        AccessPointListener accessPointListener = this.mAccessPointListener;
        if (accessPointListener != null) {
            accessPointListener.onLevelChanged(this);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void update(WifiConfiguration config) {
        this.mConfig = config;
        if (this.mConfig != null && !isPasspoint()) {
            this.ssid = removeDoubleQuotes(this.mConfig.SSID);
        }
        this.networkId = config != null ? config.networkId : -1;
        ThreadUtils.postOnMainThread(new Runnable() { // from class: com.android.settingslib.wifi.-$$Lambda$AccessPoint$QyP0aXhFuWtm7lmBu1IY3qbfmBA
            @Override // java.lang.Runnable
            public final void run() {
                AccessPoint.this.lambda$update$5$AccessPoint();
            }
        });
    }

    public /* synthetic */ void lambda$update$5$AccessPoint() {
        AccessPointListener accessPointListener = this.mAccessPointListener;
        if (accessPointListener != null) {
            accessPointListener.onAccessPointChanged(this);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @VisibleForTesting
    public void setRssi(int rssi) {
        this.mRssi = rssi;
    }

    void setUnreachable() {
        setRssi(Integer.MIN_VALUE);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public int getSpeed() {
        return this.mSpeed;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public String getSpeedLabel() {
        return getSpeedLabel(this.mSpeed);
    }

    private static int roundToClosestSpeedEnum(int speed) {
        if (speed < 5) {
            return 0;
        }
        if (speed < 7) {
            return 5;
        }
        if (speed < 15) {
            return 10;
        }
        if (speed < 25) {
            return 20;
        }
        return 30;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public String getSpeedLabel(int speed) {
        return getSpeedLabel(this.mContext, speed);
    }

    private static String getSpeedLabel(Context context, int speed) {
        if (speed != 5) {
            if (speed != 10) {
                if (speed != 20) {
                    if (speed == 30) {
                        return context.getString(R.string.speed_label_very_fast);
                    }
                    return null;
                }
                return context.getString(R.string.speed_label_fast);
            }
            return context.getString(R.string.speed_label_okay);
        }
        return context.getString(R.string.speed_label_slow);
    }

    public static String getSpeedLabel(Context context, ScoredNetwork scoredNetwork, int rssi) {
        return getSpeedLabel(context, roundToClosestSpeedEnum(scoredNetwork.calculateBadge(rssi)));
    }

    public boolean isReachable() {
        return this.mRssi != Integer.MIN_VALUE;
    }

    private static CharSequence getAppLabel(String packageName, PackageManager packageManager) {
        try {
            int userId = UserHandle.getUserId(-2);
            ApplicationInfo appInfo = packageManager.getApplicationInfoAsUser(packageName, 0, userId);
            if (appInfo == null) {
                return "";
            }
            CharSequence appLabel = appInfo.loadLabel(packageManager);
            return appLabel;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to get app info", e);
            return "";
        }
    }

    public static String getSummary(Context context, String ssid, NetworkInfo.DetailedState state, boolean isEphemeral, String suggestionOrSpecifierPackageName) {
        if (state == NetworkInfo.DetailedState.CONNECTED) {
            if (isEphemeral && !TextUtils.isEmpty(suggestionOrSpecifierPackageName)) {
                CharSequence appLabel = getAppLabel(suggestionOrSpecifierPackageName, context.getPackageManager());
                return context.getString(R.string.connected_via_app, appLabel);
            } else if (isEphemeral) {
                NetworkScoreManager networkScoreManager = (NetworkScoreManager) context.getSystemService(NetworkScoreManager.class);
                NetworkScorerAppData scorer = networkScoreManager.getActiveScorer();
                if (scorer != null && scorer.getRecommendationServiceLabel() != null) {
                    String format = context.getString(R.string.connected_via_network_scorer);
                    return String.format(format, scorer.getRecommendationServiceLabel());
                }
                return context.getString(R.string.connected_via_network_scorer_default);
            }
        }
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService("connectivity");
        if (state == NetworkInfo.DetailedState.CONNECTED) {
            IWifiManager wifiManager = IWifiManager.Stub.asInterface(ServiceManager.getService("wifi"));
            NetworkCapabilities nc = null;
            try {
                nc = cm.getNetworkCapabilities(wifiManager.getCurrentNetwork());
            } catch (RemoteException e) {
            }
            if (nc != null) {
                if (nc.hasCapability(17)) {
                    int id = context.getResources().getIdentifier("network_available_sign_in", "string", SystemMediaRouteProvider.PACKAGE_NAME);
                    return context.getString(id);
                } else if (nc.hasCapability(24)) {
                    return context.getString(R.string.wifi_limited_connection);
                } else {
                    if (!nc.hasCapability(16)) {
                        return context.getString(R.string.wifi_connected_no_internet);
                    }
                }
            }
        }
        if (state == null) {
            Log.w(TAG, "state is null, returning empty summary");
            return "";
        }
        String[] formats = context.getResources().getStringArray(ssid == null ? R.array.wifi_status : R.array.wifi_status_with_ssid);
        int index = state.ordinal();
        return (index >= formats.length || formats[index].length() == 0) ? "" : String.format(formats[index], ssid);
    }

    public static String convertToQuotedString(String string) {
        return "\"" + string + "\"";
    }

    private static int getPskType(ScanResult result) {
        boolean wpa = result.capabilities.contains("WPA-PSK");
        boolean wpa2 = result.capabilities.contains("RSN-PSK");
        boolean wpa3 = result.capabilities.contains("RSN-SAE");
        if (wpa2 && wpa) {
            return 3;
        }
        if (wpa2) {
            return 2;
        }
        if (wpa) {
            return 1;
        }
        if (!wpa3) {
            Log.w(TAG, "Received abnormal flag string: " + result.capabilities);
            return 0;
        }
        return 0;
    }

    private static int getEapType(ScanResult result) {
        if (result.capabilities.contains("RSN-EAP")) {
            return 2;
        }
        if (result.capabilities.contains("WPA-EAP")) {
            return 1;
        }
        return 0;
    }

    private static int getSecurity(Context context, ScanResult result) {
        boolean isWep = result.capabilities.contains("WEP");
        boolean isSae = result.capabilities.contains("SAE");
        boolean isPsk = result.capabilities.contains("PSK");
        boolean isEapSuiteB192 = result.capabilities.contains("EAP_SUITE_B_192");
        boolean isEap = result.capabilities.contains("EAP");
        boolean isOwe = result.capabilities.contains("OWE");
        boolean isOweTransition = result.capabilities.contains("OWE_TRANSITION");
        if (isSae && isPsk) {
            WifiManager wifiManager = (WifiManager) context.getSystemService("wifi");
            return wifiManager.isWpa3SaeSupported() ? 5 : 2;
        } else if (isOweTransition) {
            WifiManager wifiManager2 = (WifiManager) context.getSystemService("wifi");
            return wifiManager2.isEnhancedOpenSupported() ? 4 : 0;
        } else if (isWep) {
            return 1;
        } else {
            if (isSae) {
                return 5;
            }
            if (isPsk) {
                return 2;
            }
            if (isEapSuiteB192) {
                return 6;
            }
            if (isEap) {
                return 3;
            }
            return isOwe ? 4 : 0;
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static int getSecurity(WifiConfiguration config) {
        if (config.allowedKeyManagement.get(8)) {
            return 5;
        }
        if (config.allowedKeyManagement.get(1)) {
            return 2;
        }
        if (config.allowedKeyManagement.get(10)) {
            return 6;
        }
        if (config.allowedKeyManagement.get(2) || config.allowedKeyManagement.get(3)) {
            return 3;
        }
        if (config.allowedKeyManagement.get(9)) {
            return 4;
        }
        return config.wepKeys[0] != null ? 1 : 0;
    }

    public static String securityToString(int security, int pskType) {
        if (security == 1) {
            return "WEP";
        }
        if (security == 2) {
            if (pskType == 1) {
                return "WPA";
            }
            if (pskType == 2) {
                return "WPA2";
            }
            if (pskType == 3) {
                return "WPA_WPA2";
            }
            return "PSK";
        } else if (security == 3) {
            return "EAP";
        } else {
            if (security == 5) {
                return "SAE";
            }
            if (security == 6) {
                return "SUITE_B";
            }
            if (security == 4) {
                return "OWE";
            }
            return "NONE";
        }
    }

    static String removeDoubleQuotes(String string) {
        if (TextUtils.isEmpty(string)) {
            return "";
        }
        int length = string.length();
        if (length > 1 && string.charAt(0) == '\"' && string.charAt(length - 1) == '\"') {
            return string.substring(1, length - 1);
        }
        return string;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public WifiManager getWifiManager() {
        if (this.mWifiManager == null) {
            this.mWifiManager = (WifiManager) this.mContext.getSystemService("wifi");
        }
        return this.mWifiManager;
    }

    private static boolean isVerboseLoggingEnabled() {
        return WifiTracker.sVerboseLogging || Log.isLoggable(TAG, 2);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @VisibleForTesting
    /* loaded from: classes3.dex */
    public class AccessPointProvisioningCallback extends ProvisioningCallback {
        AccessPointProvisioningCallback() {
        }

        public void onProvisioningFailure(int status) {
            if (TextUtils.equals(AccessPoint.this.mOsuStatus, AccessPoint.this.mContext.getString(R.string.osu_completing_sign_up))) {
                AccessPoint accessPoint = AccessPoint.this;
                accessPoint.mOsuFailure = accessPoint.mContext.getString(R.string.osu_sign_up_failed);
            } else {
                AccessPoint accessPoint2 = AccessPoint.this;
                accessPoint2.mOsuFailure = accessPoint2.mContext.getString(R.string.osu_connect_failed);
            }
            AccessPoint.this.mOsuStatus = null;
            AccessPoint.this.mOsuProvisioningComplete = false;
            ThreadUtils.postOnMainThread(new Runnable() { // from class: com.android.settingslib.wifi.-$$Lambda$AccessPoint$AccessPointProvisioningCallback$74qKnAJvzvRGvsJDwRIri14jOnQ
                @Override // java.lang.Runnable
                public final void run() {
                    AccessPoint.AccessPointProvisioningCallback.this.lambda$onProvisioningFailure$0$AccessPoint$AccessPointProvisioningCallback();
                }
            });
        }

        public /* synthetic */ void lambda$onProvisioningFailure$0$AccessPoint$AccessPointProvisioningCallback() {
            if (AccessPoint.this.mAccessPointListener != null) {
                AccessPoint.this.mAccessPointListener.onAccessPointChanged(AccessPoint.this);
            }
        }

        public void onProvisioningStatus(int status) {
            String newStatus = null;
            switch (status) {
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                    newStatus = String.format(AccessPoint.this.mContext.getString(R.string.osu_opening_provider), AccessPoint.this.mOsuProvider.getFriendlyName());
                    break;
                case 8:
                case 9:
                case 10:
                case 11:
                    newStatus = AccessPoint.this.mContext.getString(R.string.osu_completing_sign_up);
                    break;
            }
            boolean updated = true ^ TextUtils.equals(AccessPoint.this.mOsuStatus, newStatus);
            AccessPoint.this.mOsuStatus = newStatus;
            AccessPoint.this.mOsuFailure = null;
            AccessPoint.this.mOsuProvisioningComplete = false;
            if (updated) {
                ThreadUtils.postOnMainThread(new Runnable() { // from class: com.android.settingslib.wifi.-$$Lambda$AccessPoint$AccessPointProvisioningCallback$ko59tOsAuz6AC9y5Nq-UikXZo9s
                    @Override // java.lang.Runnable
                    public final void run() {
                        AccessPoint.AccessPointProvisioningCallback.this.lambda$onProvisioningStatus$1$AccessPoint$AccessPointProvisioningCallback();
                    }
                });
            }
        }

        public /* synthetic */ void lambda$onProvisioningStatus$1$AccessPoint$AccessPointProvisioningCallback() {
            if (AccessPoint.this.mAccessPointListener != null) {
                AccessPoint.this.mAccessPointListener.onAccessPointChanged(AccessPoint.this);
            }
        }

        public void onProvisioningComplete() {
            AccessPoint.this.mOsuProvisioningComplete = true;
            AccessPoint.this.mOsuFailure = null;
            AccessPoint.this.mOsuStatus = null;
            ThreadUtils.postOnMainThread(new Runnable() { // from class: com.android.settingslib.wifi.-$$Lambda$AccessPoint$AccessPointProvisioningCallback$8NkGPNV0jfGEnIZHmtcNMYE5Q7Q
                @Override // java.lang.Runnable
                public final void run() {
                    AccessPoint.AccessPointProvisioningCallback.this.lambda$onProvisioningComplete$2$AccessPoint$AccessPointProvisioningCallback();
                }
            });
            WifiManager wifiManager = AccessPoint.this.getWifiManager();
            PasspointConfiguration passpointConfig = (PasspointConfiguration) wifiManager.getMatchingPasspointConfigsForOsuProviders(Collections.singleton(AccessPoint.this.mOsuProvider)).get(AccessPoint.this.mOsuProvider);
            if (passpointConfig == null) {
                Log.e(AccessPoint.TAG, "Missing PasspointConfiguration for newly provisioned network!");
                if (AccessPoint.this.mConnectListener != null) {
                    AccessPoint.this.mConnectListener.onFailure(0);
                    return;
                }
                return;
            }
            String fqdn = passpointConfig.getHomeSp().getFqdn();
            for (Pair<WifiConfiguration, Map<Integer, List<ScanResult>>> pairing : wifiManager.getAllMatchingWifiConfigs(wifiManager.getScanResults())) {
                WifiConfiguration config = (WifiConfiguration) pairing.first;
                if (TextUtils.equals(config.FQDN, fqdn)) {
                    List<ScanResult> homeScans = (List) ((Map) pairing.second).get(0);
                    List<ScanResult> roamingScans = (List) ((Map) pairing.second).get(1);
                    AccessPoint connectionAp = new AccessPoint(AccessPoint.this.mContext, config, homeScans, roamingScans);
                    wifiManager.connect(connectionAp.getConfig(), AccessPoint.this.mConnectListener);
                    return;
                }
            }
            if (AccessPoint.this.mConnectListener != null) {
                AccessPoint.this.mConnectListener.onFailure(0);
            }
        }

        public /* synthetic */ void lambda$onProvisioningComplete$2$AccessPoint$AccessPointProvisioningCallback() {
            if (AccessPoint.this.mAccessPointListener != null) {
                AccessPoint.this.mAccessPointListener.onAccessPointChanged(AccessPoint.this);
            }
        }
    }

    public boolean isPskSaeTransitionMode() {
        return this.mIsPskSaeTransitionMode;
    }

    public boolean isOweTransitionMode() {
        return this.mIsOweTransitionMode;
    }

    private static boolean isPskSaeTransitionMode(ScanResult scanResult) {
        return scanResult.capabilities.contains("PSK") && scanResult.capabilities.contains("SAE");
    }

    private static boolean isOweTransitionMode(ScanResult scanResult) {
        return scanResult.capabilities.contains("OWE_TRANSITION");
    }

    private boolean isSameSsidOrBssid(ScanResult scanResult) {
        if (scanResult == null) {
            return false;
        }
        if (TextUtils.equals(this.ssid, scanResult.SSID)) {
            return true;
        }
        return scanResult.BSSID != null && TextUtils.equals(this.bssid, scanResult.BSSID);
    }

    private boolean isSameSsidOrBssid(WifiInfo wifiInfo) {
        if (wifiInfo == null) {
            return false;
        }
        if (TextUtils.equals(this.ssid, removeDoubleQuotes(wifiInfo.getSSID()))) {
            return true;
        }
        return wifiInfo.getBSSID() != null && TextUtils.equals(this.bssid, wifiInfo.getBSSID());
    }

    private boolean isSameSsidOrBssid(AccessPoint accessPoint) {
        if (accessPoint == null) {
            return false;
        }
        if (TextUtils.equals(this.ssid, accessPoint.getSsid())) {
            return true;
        }
        return accessPoint.getBssid() != null && TextUtils.equals(this.bssid, accessPoint.getBssid());
    }
}
