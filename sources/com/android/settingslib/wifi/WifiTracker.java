package com.android.settingslib.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkKey;
import android.net.NetworkRequest;
import android.net.NetworkScoreManager;
import android.net.ScoredNetwork;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkScoreCache;
import android.net.wifi.hotspot2.OsuProvider;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.android.settingslib.R;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnDestroy;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;
import com.android.settingslib.utils.ThreadUtils;
import com.android.settingslib.wifi.WifiTracker;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;
/* loaded from: classes3.dex */
public class WifiTracker implements LifecycleObserver, OnStart, OnStop, OnDestroy {
    private static final long DEFAULT_MAX_CACHED_SCORE_AGE_MILLIS = 1200000;
    @VisibleForTesting
    static final long MAX_SCAN_RESULT_AGE_MILLIS = 15000;
    private static final String TAG = "WifiTracker";
    private static final int WIFI_RESCAN_INTERVAL_MS = 10000;
    private static final String WIFI_SECURITY_EAP = "EAP";
    private static final String WIFI_SECURITY_OWE = "OWE";
    private static final String WIFI_SECURITY_PSK = "PSK";
    private static final String WIFI_SECURITY_SAE = "SAE";
    private static final String WIFI_SECURITY_SUITE_B_192 = "SUITE_B_192";
    public static boolean sVerboseLogging;
    private final AtomicBoolean mConnected;
    private final ConnectivityManager mConnectivityManager;
    private final Context mContext;
    private final IntentFilter mFilter;
    @GuardedBy("mLock")
    private final List<AccessPoint> mInternalAccessPoints;
    private WifiInfo mLastInfo;
    private NetworkInfo mLastNetworkInfo;
    private boolean mLastScanSucceeded;
    private final WifiListenerExecutor mListener;
    private final Object mLock;
    private long mMaxSpeedLabelScoreCacheAge;
    private WifiTrackerNetworkCallback mNetworkCallback;
    private final NetworkRequest mNetworkRequest;
    private final NetworkScoreManager mNetworkScoreManager;
    private boolean mNetworkScoringUiEnabled;
    @VisibleForTesting
    final BroadcastReceiver mReceiver;
    private boolean mRegistered;
    @GuardedBy("mLock")
    private final Set<NetworkKey> mRequestedScores;
    private final HashMap<String, ScanResult> mScanResultCache;
    @GuardedBy("mLock")
    @VisibleForTesting
    Scanner mScanner;
    private WifiNetworkScoreCache mScoreCache;
    private boolean mStaleScanResults;
    private final WifiManager mWifiManager;
    @VisibleForTesting
    Handler mWorkHandler;
    private HandlerThread mWorkThread;

    /* loaded from: classes3.dex */
    public interface WifiListener {
        void onAccessPointsChanged();

        void onConnectedChanged();

        void onWifiStateChanged(int i);
    }

    static /* synthetic */ boolean access$1000() {
        return isVerboseLoggingEnabled();
    }

    private static final boolean DBG() {
        return Log.isLoggable(TAG, 3);
    }

    private static boolean isVerboseLoggingEnabled() {
        return sVerboseLogging || Log.isLoggable(TAG, 2);
    }

    private static IntentFilter newIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.net.wifi.WIFI_STATE_CHANGED");
        filter.addAction("android.net.wifi.SCAN_RESULTS");
        filter.addAction("android.net.wifi.NETWORK_IDS_CHANGED");
        filter.addAction("android.net.wifi.supplicant.STATE_CHANGE");
        filter.addAction("android.net.wifi.CONFIGURED_NETWORKS_CHANGE");
        filter.addAction("android.net.wifi.LINK_CONFIGURATION_CHANGED");
        filter.addAction("android.net.wifi.STATE_CHANGE");
        filter.addAction("android.net.wifi.RSSI_CHANGED");
        return filter;
    }

    @Deprecated
    public WifiTracker(Context context, WifiListener wifiListener, boolean includeSaved, boolean includeScans) {
        this(context, wifiListener, (WifiManager) context.getSystemService(WifiManager.class), (ConnectivityManager) context.getSystemService(ConnectivityManager.class), (NetworkScoreManager) context.getSystemService(NetworkScoreManager.class), newIntentFilter());
    }

    public WifiTracker(Context context, WifiListener wifiListener, @NonNull Lifecycle lifecycle, boolean includeSaved, boolean includeScans) {
        this(context, wifiListener, (WifiManager) context.getSystemService(WifiManager.class), (ConnectivityManager) context.getSystemService(ConnectivityManager.class), (NetworkScoreManager) context.getSystemService(NetworkScoreManager.class), newIntentFilter());
        lifecycle.addObserver(this);
    }

    @VisibleForTesting
    WifiTracker(Context context, WifiListener wifiListener, WifiManager wifiManager, ConnectivityManager connectivityManager, NetworkScoreManager networkScoreManager, IntentFilter filter) {
        boolean z = false;
        this.mConnected = new AtomicBoolean(false);
        this.mLock = new Object();
        this.mInternalAccessPoints = new ArrayList();
        this.mRequestedScores = new ArraySet();
        this.mStaleScanResults = true;
        this.mLastScanSucceeded = true;
        this.mScanResultCache = new HashMap<>();
        this.mReceiver = new BroadcastReceiver() { // from class: com.android.settingslib.wifi.WifiTracker.2
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context2, Intent intent) {
                String action = intent.getAction();
                if ("android.net.wifi.WIFI_STATE_CHANGED".equals(action)) {
                    WifiTracker.this.updateWifiState(intent.getIntExtra("wifi_state", 4));
                } else if ("android.net.wifi.SCAN_RESULTS".equals(action)) {
                    WifiTracker.this.mStaleScanResults = false;
                    WifiTracker.this.mLastScanSucceeded = intent.getBooleanExtra("resultsUpdated", true);
                    WifiTracker.this.fetchScansAndConfigsAndUpdateAccessPoints();
                } else if ("android.net.wifi.CONFIGURED_NETWORKS_CHANGE".equals(action) || "android.net.wifi.LINK_CONFIGURATION_CHANGED".equals(action)) {
                    WifiTracker.this.fetchScansAndConfigsAndUpdateAccessPoints();
                } else if ("android.net.wifi.STATE_CHANGE".equals(action)) {
                    NetworkInfo info = (NetworkInfo) intent.getParcelableExtra("networkInfo");
                    WifiTracker.this.updateNetworkInfo(info);
                    WifiTracker.this.fetchScansAndConfigsAndUpdateAccessPoints();
                } else if ("android.net.wifi.RSSI_CHANGED".equals(action)) {
                    NetworkInfo info2 = WifiTracker.this.mConnectivityManager.getNetworkInfo(WifiTracker.this.mWifiManager.getCurrentNetwork());
                    WifiTracker.this.updateNetworkInfo(info2);
                }
            }
        };
        this.mContext = context;
        this.mWifiManager = wifiManager;
        this.mListener = new WifiListenerExecutor(wifiListener);
        this.mConnectivityManager = connectivityManager;
        WifiManager wifiManager2 = this.mWifiManager;
        if (wifiManager2 != null && wifiManager2.getVerboseLoggingLevel() > 0) {
            z = true;
        }
        sVerboseLogging = z;
        this.mFilter = filter;
        this.mNetworkRequest = new NetworkRequest.Builder().clearCapabilities().addCapability(15).addTransportType(1).build();
        this.mNetworkScoreManager = networkScoreManager;
        HandlerThread workThread = new HandlerThread("WifiTracker{" + Integer.toHexString(System.identityHashCode(this)) + "}", 10);
        workThread.start();
        setWorkThread(workThread);
    }

    @VisibleForTesting
    void setWorkThread(HandlerThread workThread) {
        this.mWorkThread = workThread;
        this.mWorkHandler = new Handler(workThread.getLooper());
        this.mScoreCache = new WifiNetworkScoreCache(this.mContext, new WifiNetworkScoreCache.CacheListener(this.mWorkHandler) { // from class: com.android.settingslib.wifi.WifiTracker.1
            public void networkCacheUpdated(List<ScoredNetwork> networks) {
                if (WifiTracker.this.mRegistered) {
                    if (Log.isLoggable(WifiTracker.TAG, 2)) {
                        Log.v(WifiTracker.TAG, "Score cache was updated with networks: " + networks);
                    }
                    WifiTracker.this.updateNetworkScores();
                }
            }
        });
    }

    @Override // com.android.settingslib.core.lifecycle.events.OnDestroy
    public void onDestroy() {
        this.mWorkThread.quit();
    }

    private void pauseScanning() {
        synchronized (this.mLock) {
            if (this.mScanner != null) {
                this.mScanner.pause();
                this.mScanner = null;
            }
        }
        this.mStaleScanResults = true;
    }

    public void resumeScanning() {
        synchronized (this.mLock) {
            if (this.mScanner == null) {
                this.mScanner = new Scanner();
            }
            if (isWifiEnabled()) {
                this.mScanner.resume();
            }
        }
    }

    @Override // com.android.settingslib.core.lifecycle.events.OnStart
    public void onStart() {
        forceUpdate();
        registerScoreCache();
        this.mNetworkScoringUiEnabled = Settings.Global.getInt(this.mContext.getContentResolver(), "network_scoring_ui_enabled", 0) == 1;
        this.mMaxSpeedLabelScoreCacheAge = Settings.Global.getLong(this.mContext.getContentResolver(), "speed_label_cache_eviction_age_millis", DEFAULT_MAX_CACHED_SCORE_AGE_MILLIS);
        resumeScanning();
        if (!this.mRegistered) {
            this.mContext.registerReceiver(this.mReceiver, this.mFilter, null, this.mWorkHandler);
            this.mNetworkCallback = new WifiTrackerNetworkCallback();
            this.mConnectivityManager.registerNetworkCallback(this.mNetworkRequest, this.mNetworkCallback, this.mWorkHandler);
            this.mRegistered = true;
        }
    }

    @VisibleForTesting
    void forceUpdate() {
        this.mLastInfo = this.mWifiManager.getConnectionInfo();
        this.mLastNetworkInfo = this.mConnectivityManager.getNetworkInfo(this.mWifiManager.getCurrentNetwork());
        fetchScansAndConfigsAndUpdateAccessPoints();
    }

    private void registerScoreCache() {
        this.mNetworkScoreManager.registerNetworkScoreCache(1, this.mScoreCache, 2);
    }

    private void requestScoresForNetworkKeys(Collection<NetworkKey> keys) {
        if (keys.isEmpty()) {
            return;
        }
        if (DBG()) {
            Log.d(TAG, "Requesting scores for Network Keys: " + keys);
        }
        this.mNetworkScoreManager.requestScores((NetworkKey[]) keys.toArray(new NetworkKey[keys.size()]));
        synchronized (this.mLock) {
            this.mRequestedScores.addAll(keys);
        }
    }

    @Override // com.android.settingslib.core.lifecycle.events.OnStop
    public void onStop() {
        if (this.mRegistered) {
            this.mContext.unregisterReceiver(this.mReceiver);
            this.mConnectivityManager.unregisterNetworkCallback(this.mNetworkCallback);
            this.mRegistered = false;
        }
        unregisterScoreCache();
        pauseScanning();
        this.mWorkHandler.removeCallbacksAndMessages(null);
    }

    private void unregisterScoreCache() {
        this.mNetworkScoreManager.unregisterNetworkScoreCache(1, this.mScoreCache);
        synchronized (this.mLock) {
            this.mRequestedScores.clear();
        }
    }

    public List<AccessPoint> getAccessPoints() {
        ArrayList arrayList;
        synchronized (this.mLock) {
            arrayList = new ArrayList(this.mInternalAccessPoints);
        }
        return arrayList;
    }

    public WifiManager getManager() {
        return this.mWifiManager;
    }

    public boolean isWifiEnabled() {
        WifiManager wifiManager = this.mWifiManager;
        return wifiManager != null && wifiManager.isWifiEnabled();
    }

    public int getNumSavedNetworks() {
        return WifiSavedConfigUtils.getAllConfigs(this.mContext, this.mWifiManager).size();
    }

    public boolean isConnected() {
        return this.mConnected.get();
    }

    public void dump(PrintWriter pw) {
        pw.println("  - wifi tracker ------");
        for (AccessPoint accessPoint : getAccessPoints()) {
            pw.println("  " + accessPoint);
        }
    }

    private ArrayMap<String, List<ScanResult>> updateScanResultCache(List<ScanResult> newResults) {
        List<ScanResult> resultList;
        for (ScanResult newResult : newResults) {
            if (newResult.SSID != null && !newResult.SSID.isEmpty()) {
                this.mScanResultCache.put(newResult.BSSID, newResult);
            }
        }
        evictOldScans();
        ArrayMap<String, List<ScanResult>> scanResultsByApKey = new ArrayMap<>();
        for (ScanResult result : this.mScanResultCache.values()) {
            if (result.SSID != null && result.SSID.length() != 0 && !result.capabilities.contains("[IBSS]")) {
                String apKey = AccessPoint.getKey(this.mContext, result);
                if (scanResultsByApKey.containsKey(apKey)) {
                    resultList = scanResultsByApKey.get(apKey);
                } else {
                    resultList = new ArrayList<>();
                    scanResultsByApKey.put(apKey, resultList);
                }
                resultList.add(result);
            }
        }
        return scanResultsByApKey;
    }

    private void evictOldScans() {
        long evictionTimeoutMillis = this.mLastScanSucceeded ? MAX_SCAN_RESULT_AGE_MILLIS : 30000L;
        long nowMs = SystemClock.elapsedRealtime();
        Iterator<ScanResult> iter = this.mScanResultCache.values().iterator();
        while (iter.hasNext()) {
            ScanResult result = iter.next();
            if (nowMs - (result.timestamp / 1000) > evictionTimeoutMillis) {
                iter.remove();
            }
        }
    }

    private WifiConfiguration getWifiConfigurationForNetworkId(int networkId, List<WifiConfiguration> configs) {
        if (configs != null) {
            for (WifiConfiguration config : configs) {
                if (this.mLastInfo != null && networkId == config.networkId && (!config.selfAdded || config.numAssociation != 0)) {
                    return config;
                }
            }
            return null;
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void fetchScansAndConfigsAndUpdateAccessPoints() {
        List<ScanResult> newScanResults = this.mWifiManager.getScanResults();
        List<ScanResult> filteredScanResults = filterScanResultsByCapabilities(newScanResults);
        if (isVerboseLoggingEnabled()) {
            Log.i(TAG, "Fetched scan results: " + filteredScanResults);
        }
        List<WifiConfiguration> configs = this.mWifiManager.getConfiguredNetworks();
        updateAccessPoints(filteredScanResults, configs);
    }

    private void updateAccessPoints(List<ScanResult> newScanResults, List<WifiConfiguration> configs) {
        WifiConfiguration connectionConfig;
        WifiInfo wifiInfo = this.mLastInfo;
        if (wifiInfo != null) {
            WifiConfiguration connectionConfig2 = getWifiConfigurationForNetworkId(wifiInfo.getNetworkId(), configs);
            connectionConfig = connectionConfig2;
        } else {
            connectionConfig = null;
        }
        synchronized (this.mLock) {
            ArrayMap<String, List<ScanResult>> scanResultsByApKey = updateScanResultCache(newScanResults);
            List<AccessPoint> cachedAccessPoints = new ArrayList<>(this.mInternalAccessPoints);
            ArrayList<AccessPoint> accessPoints = new ArrayList<>();
            List<NetworkKey> scoresToRequest = new ArrayList<>();
            for (Map.Entry<String, List<ScanResult>> entry : scanResultsByApKey.entrySet()) {
                for (ScanResult result : entry.getValue()) {
                    NetworkKey key = NetworkKey.createFromScanResult(result);
                    if (key != null && !this.mRequestedScores.contains(key)) {
                        scoresToRequest.add(key);
                    }
                }
                final AccessPoint accessPoint = getCachedOrCreate(entry.getValue(), cachedAccessPoints);
                List<WifiConfiguration> matchedConfigs = (List) configs.stream().filter(new Predicate() { // from class: com.android.settingslib.wifi.-$$Lambda$WifiTracker$Up3TxfI1NaJ1CulBpL22WbeQznY
                    @Override // java.util.function.Predicate
                    public final boolean test(Object obj) {
                        boolean matches;
                        matches = AccessPoint.this.matches((WifiConfiguration) obj);
                        return matches;
                    }
                }).collect(Collectors.toList());
                int matchedConfigCount = matchedConfigs.size();
                if (matchedConfigCount == 0) {
                    accessPoint.update(null);
                } else if (matchedConfigCount == 1) {
                    accessPoint.update(matchedConfigs.get(0));
                } else {
                    Optional<WifiConfiguration> preferredConfig = matchedConfigs.stream().filter(new Predicate() { // from class: com.android.settingslib.wifi.-$$Lambda$WifiTracker$ZaDLRSIZwSj9aj6lj58U997Kj9s
                        @Override // java.util.function.Predicate
                        public final boolean test(Object obj) {
                            boolean isSaeOrOwe;
                            isSaeOrOwe = WifiTracker.isSaeOrOwe((WifiConfiguration) obj);
                            return isSaeOrOwe;
                        }
                    }).findFirst();
                    if (preferredConfig.isPresent()) {
                        accessPoint.update(preferredConfig.get());
                    } else {
                        accessPoint.update(matchedConfigs.get(0));
                    }
                }
                accessPoints.add(accessPoint);
            }
            List<ScanResult> cachedScanResults = new ArrayList<>(this.mScanResultCache.values());
            accessPoints.addAll(updatePasspointAccessPoints(this.mWifiManager.getAllMatchingWifiConfigs(cachedScanResults), cachedAccessPoints));
            accessPoints.addAll(updateOsuAccessPoints(this.mWifiManager.getMatchingOsuProviders(cachedScanResults), cachedAccessPoints));
            if (this.mLastInfo != null && this.mLastNetworkInfo != null) {
                Iterator<AccessPoint> it = accessPoints.iterator();
                while (it.hasNext()) {
                    AccessPoint ap = it.next();
                    ap.update(connectionConfig, this.mLastInfo, this.mLastNetworkInfo);
                }
            }
            if (accessPoints.isEmpty() && connectionConfig != null) {
                AccessPoint activeAp = new AccessPoint(this.mContext, connectionConfig);
                activeAp.update(connectionConfig, this.mLastInfo, this.mLastNetworkInfo);
                accessPoints.add(activeAp);
                scoresToRequest.add(NetworkKey.createFromWifiInfo(this.mLastInfo));
            }
            requestScoresForNetworkKeys(scoresToRequest);
            Iterator<AccessPoint> it2 = accessPoints.iterator();
            while (it2.hasNext()) {
                AccessPoint ap2 = it2.next();
                ap2.update(this.mScoreCache, this.mNetworkScoringUiEnabled, this.mMaxSpeedLabelScoreCacheAge);
            }
            Collections.sort(accessPoints);
            if (DBG()) {
                Log.d(TAG, "------ Dumping AccessPoints that were not seen on this scan ------");
                for (AccessPoint prevAccessPoint : this.mInternalAccessPoints) {
                    String prevTitle = prevAccessPoint.getTitle();
                    boolean found = false;
                    Iterator<AccessPoint> it3 = accessPoints.iterator();
                    while (true) {
                        if (!it3.hasNext()) {
                            break;
                        }
                        AccessPoint newAccessPoint = it3.next();
                        if (newAccessPoint.getTitle() != null && newAccessPoint.getTitle().equals(prevTitle)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        Log.d(TAG, "Did not find " + prevTitle + " in this scan");
                    }
                }
                Log.d(TAG, "---- Done dumping AccessPoints that were not seen on this scan ----");
            }
            this.mInternalAccessPoints.clear();
            this.mInternalAccessPoints.addAll(accessPoints);
        }
        conditionallyNotifyListeners();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean isSaeOrOwe(WifiConfiguration config) {
        int security = AccessPoint.getSecurity(config);
        return security == 5 || security == 4;
    }

    @VisibleForTesting
    List<AccessPoint> updatePasspointAccessPoints(List<Pair<WifiConfiguration, Map<Integer, List<ScanResult>>>> passpointConfigsAndScans, List<AccessPoint> accessPointCache) {
        List<AccessPoint> accessPoints = new ArrayList<>();
        Set<String> seenFQDNs = new ArraySet<>();
        for (Pair<WifiConfiguration, Map<Integer, List<ScanResult>>> pairing : passpointConfigsAndScans) {
            WifiConfiguration config = (WifiConfiguration) pairing.first;
            if (seenFQDNs.add(config.FQDN)) {
                List<ScanResult> homeScans = (List) ((Map) pairing.second).get(0);
                List<ScanResult> roamingScans = (List) ((Map) pairing.second).get(1);
                AccessPoint accessPoint = getCachedOrCreatePasspoint(config, homeScans, roamingScans, accessPointCache);
                accessPoints.add(accessPoint);
            }
        }
        return accessPoints;
    }

    @VisibleForTesting
    List<AccessPoint> updateOsuAccessPoints(Map<OsuProvider, List<ScanResult>> providersAndScans, List<AccessPoint> accessPointCache) {
        List<AccessPoint> accessPoints = new ArrayList<>();
        Set<OsuProvider> alreadyProvisioned = this.mWifiManager.getMatchingPasspointConfigsForOsuProviders(providersAndScans.keySet()).keySet();
        for (OsuProvider provider : providersAndScans.keySet()) {
            if (!alreadyProvisioned.contains(provider)) {
                AccessPoint accessPointOsu = getCachedOrCreateOsu(provider, providersAndScans.get(provider), accessPointCache);
                accessPoints.add(accessPointOsu);
            }
        }
        return accessPoints;
    }

    private AccessPoint getCachedOrCreate(List<ScanResult> scanResults, List<AccessPoint> cache) {
        AccessPoint accessPoint = getCachedByKey(cache, AccessPoint.getKey(this.mContext, scanResults.get(0)));
        if (accessPoint == null) {
            return new AccessPoint(this.mContext, scanResults);
        }
        accessPoint.setScanResults(scanResults);
        return accessPoint;
    }

    private AccessPoint getCachedOrCreatePasspoint(WifiConfiguration config, List<ScanResult> homeScans, List<ScanResult> roamingScans, List<AccessPoint> cache) {
        AccessPoint accessPoint = getCachedByKey(cache, AccessPoint.getKey(config));
        if (accessPoint == null) {
            return new AccessPoint(this.mContext, config, homeScans, roamingScans);
        }
        accessPoint.update(config);
        accessPoint.setScanResultsPasspoint(homeScans, roamingScans);
        return accessPoint;
    }

    private AccessPoint getCachedOrCreateOsu(OsuProvider provider, List<ScanResult> scanResults, List<AccessPoint> cache) {
        AccessPoint accessPoint = getCachedByKey(cache, AccessPoint.getKey(provider));
        if (accessPoint == null) {
            return new AccessPoint(this.mContext, provider, scanResults);
        }
        accessPoint.setScanResults(scanResults);
        return accessPoint;
    }

    private AccessPoint getCachedByKey(List<AccessPoint> cache, String key) {
        ListIterator<AccessPoint> lit = cache.listIterator();
        while (lit.hasNext()) {
            AccessPoint currentAccessPoint = lit.next();
            if (currentAccessPoint.getKey().equals(key)) {
                lit.remove();
                return currentAccessPoint;
            }
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateNetworkInfo(NetworkInfo networkInfo) {
        if (!isWifiEnabled()) {
            clearAccessPointsAndConditionallyUpdate();
            return;
        }
        if (networkInfo != null) {
            this.mLastNetworkInfo = networkInfo;
            if (DBG()) {
                Log.d(TAG, "mLastNetworkInfo set: " + this.mLastNetworkInfo);
            }
            if (networkInfo.isConnected() != this.mConnected.getAndSet(networkInfo.isConnected())) {
                this.mListener.onConnectedChanged();
            }
        }
        WifiConfiguration connectionConfig = null;
        this.mLastInfo = this.mWifiManager.getConnectionInfo();
        if (DBG()) {
            Log.d(TAG, "mLastInfo set as: " + this.mLastInfo);
        }
        WifiInfo wifiInfo = this.mLastInfo;
        if (wifiInfo != null) {
            connectionConfig = getWifiConfigurationForNetworkId(wifiInfo.getNetworkId(), this.mWifiManager.getConfiguredNetworks());
        }
        boolean updated = false;
        boolean reorder = false;
        synchronized (this.mLock) {
            for (int i = this.mInternalAccessPoints.size() - 1; i >= 0; i--) {
                AccessPoint ap = this.mInternalAccessPoints.get(i);
                boolean previouslyConnected = ap.isActive();
                if (ap.update(connectionConfig, this.mLastInfo, this.mLastNetworkInfo)) {
                    updated = true;
                    if (previouslyConnected != ap.isActive()) {
                        reorder = true;
                    }
                }
                if (ap.update(this.mScoreCache, this.mNetworkScoringUiEnabled, this.mMaxSpeedLabelScoreCacheAge)) {
                    reorder = true;
                    updated = true;
                }
            }
            if (reorder) {
                Collections.sort(this.mInternalAccessPoints);
            }
            if (updated) {
                conditionallyNotifyListeners();
            }
        }
    }

    private void clearAccessPointsAndConditionallyUpdate() {
        synchronized (this.mLock) {
            if (!this.mInternalAccessPoints.isEmpty()) {
                this.mInternalAccessPoints.clear();
                conditionallyNotifyListeners();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateNetworkScores() {
        synchronized (this.mLock) {
            boolean updated = false;
            for (int i = 0; i < this.mInternalAccessPoints.size(); i++) {
                if (this.mInternalAccessPoints.get(i).update(this.mScoreCache, this.mNetworkScoringUiEnabled, this.mMaxSpeedLabelScoreCacheAge)) {
                    updated = true;
                }
            }
            if (updated) {
                Collections.sort(this.mInternalAccessPoints);
                conditionallyNotifyListeners();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateWifiState(int state) {
        if (isVerboseLoggingEnabled()) {
            Log.d(TAG, "updateWifiState: " + state);
        }
        if (state == 3) {
            synchronized (this.mLock) {
                if (this.mScanner != null) {
                    this.mScanner.resume();
                }
            }
        } else {
            clearAccessPointsAndConditionallyUpdate();
            this.mLastInfo = null;
            this.mLastNetworkInfo = null;
            synchronized (this.mLock) {
                if (this.mScanner != null) {
                    this.mScanner.pause();
                }
            }
            this.mStaleScanResults = true;
        }
        this.mListener.onWifiStateChanged(state);
    }

    /* loaded from: classes3.dex */
    private final class WifiTrackerNetworkCallback extends ConnectivityManager.NetworkCallback {
        private WifiTrackerNetworkCallback() {
        }

        @Override // android.net.ConnectivityManager.NetworkCallback
        public void onCapabilitiesChanged(Network network, NetworkCapabilities nc) {
            if (network.equals(WifiTracker.this.mWifiManager.getCurrentNetwork())) {
                WifiTracker.this.updateNetworkInfo(null);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @VisibleForTesting
    /* loaded from: classes3.dex */
    public class Scanner extends Handler {
        static final int MSG_SCAN = 0;
        private int mRetry = 0;

        Scanner() {
        }

        void resume() {
            if (WifiTracker.access$1000()) {
                Log.d(WifiTracker.TAG, "Scanner resume");
            }
            if (!hasMessages(0)) {
                sendEmptyMessage(0);
            }
        }

        void pause() {
            if (WifiTracker.access$1000()) {
                Log.d(WifiTracker.TAG, "Scanner pause");
            }
            this.mRetry = 0;
            removeMessages(0);
        }

        @VisibleForTesting
        boolean isScanning() {
            return hasMessages(0);
        }

        @Override // android.os.Handler
        public void handleMessage(Message message) {
            if (message.what != 0) {
                return;
            }
            if (WifiTracker.this.mWifiManager.startScan()) {
                this.mRetry = 0;
            } else {
                int i = this.mRetry + 1;
                this.mRetry = i;
                if (i >= 3) {
                    this.mRetry = 0;
                    if (WifiTracker.this.mContext != null) {
                        Toast.makeText(WifiTracker.this.mContext, R.string.wifi_fail_to_scan, 1).show();
                        return;
                    }
                    return;
                }
            }
            sendEmptyMessageDelayed(0, 10000L);
        }
    }

    /* loaded from: classes3.dex */
    private static class Multimap<K, V> {
        private final HashMap<K, List<V>> store = new HashMap<>();

        private Multimap() {
        }

        List<V> getAll(K key) {
            List<V> values = this.store.get(key);
            return values != null ? values : Collections.emptyList();
        }

        void put(K key, V val) {
            List<V> curVals = this.store.get(key);
            if (curVals == null) {
                curVals = new ArrayList(3);
                this.store.put(key, curVals);
            }
            curVals.add(val);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @VisibleForTesting
    /* loaded from: classes3.dex */
    public class WifiListenerExecutor implements WifiListener {
        private final WifiListener mDelegatee;

        public WifiListenerExecutor(WifiListener listener) {
            this.mDelegatee = listener;
        }

        public /* synthetic */ void lambda$onWifiStateChanged$0$WifiTracker$WifiListenerExecutor(int state) {
            this.mDelegatee.onWifiStateChanged(state);
        }

        @Override // com.android.settingslib.wifi.WifiTracker.WifiListener
        public void onWifiStateChanged(final int state) {
            runAndLog(new Runnable() { // from class: com.android.settingslib.wifi.-$$Lambda$WifiTracker$WifiListenerExecutor$PZBvWEzpVHhaI95PbZNbzEgAH1I
                @Override // java.lang.Runnable
                public final void run() {
                    WifiTracker.WifiListenerExecutor.this.lambda$onWifiStateChanged$0$WifiTracker$WifiListenerExecutor(state);
                }
            }, String.format("Invoking onWifiStateChanged callback with state %d", Integer.valueOf(state)));
        }

        @Override // com.android.settingslib.wifi.WifiTracker.WifiListener
        public void onConnectedChanged() {
            final WifiListener wifiListener = this.mDelegatee;
            Objects.requireNonNull(wifiListener);
            runAndLog(new Runnable() { // from class: com.android.settingslib.wifi.-$$Lambda$6PbPNXCvqbAnKbPWPJrs-dDWQEQ
                @Override // java.lang.Runnable
                public final void run() {
                    WifiTracker.WifiListener.this.onConnectedChanged();
                }
            }, "Invoking onConnectedChanged callback");
        }

        @Override // com.android.settingslib.wifi.WifiTracker.WifiListener
        public void onAccessPointsChanged() {
            final WifiListener wifiListener = this.mDelegatee;
            Objects.requireNonNull(wifiListener);
            runAndLog(new Runnable() { // from class: com.android.settingslib.wifi.-$$Lambda$evcvquoPxZkPmBIit31UXvhXEJk
                @Override // java.lang.Runnable
                public final void run() {
                    WifiTracker.WifiListener.this.onAccessPointsChanged();
                }
            }, "Invoking onAccessPointsChanged callback");
        }

        private void runAndLog(final Runnable r, final String verboseLog) {
            ThreadUtils.postOnMainThread(new Runnable() { // from class: com.android.settingslib.wifi.-$$Lambda$WifiTracker$WifiListenerExecutor$BMWc3s6WnR_Ijg_9a3gQADAjI3Y
                @Override // java.lang.Runnable
                public final void run() {
                    WifiTracker.WifiListenerExecutor.this.lambda$runAndLog$1$WifiTracker$WifiListenerExecutor(verboseLog, r);
                }
            });
        }

        public /* synthetic */ void lambda$runAndLog$1$WifiTracker$WifiListenerExecutor(String verboseLog, Runnable r) {
            if (WifiTracker.this.mRegistered) {
                if (WifiTracker.access$1000()) {
                    Log.i(WifiTracker.TAG, verboseLog);
                }
                r.run();
            }
        }
    }

    private void conditionallyNotifyListeners() {
        if (this.mStaleScanResults) {
            return;
        }
        this.mListener.onAccessPointsChanged();
    }

    private List<ScanResult> filterScanResultsByCapabilities(List<ScanResult> scanResults) {
        if (scanResults == null) {
            return null;
        }
        boolean isOweSupported = this.mWifiManager.isEnhancedOpenSupported();
        boolean isSaeSupported = this.mWifiManager.isWpa3SaeSupported();
        boolean isSuiteBSupported = this.mWifiManager.isWpa3SuiteBSupported();
        List<ScanResult> filteredScanResultList = new ArrayList<>();
        for (ScanResult scanResult : scanResults) {
            if (scanResult.capabilities.contains(WIFI_SECURITY_PSK)) {
                filteredScanResultList.add(scanResult);
            } else if ((scanResult.capabilities.contains(WIFI_SECURITY_SUITE_B_192) && !isSuiteBSupported) || ((scanResult.capabilities.contains(WIFI_SECURITY_SAE) && !isSaeSupported) || (scanResult.capabilities.contains(WIFI_SECURITY_OWE) && !isOweSupported))) {
                if (isVerboseLoggingEnabled()) {
                    Log.v(TAG, "filterScanResultsByCapabilities: Filtering SSID " + scanResult.SSID + " with capabilities: " + scanResult.capabilities);
                }
            } else {
                filteredScanResultList.add(scanResult);
            }
        }
        return filteredScanResultList;
    }
}
