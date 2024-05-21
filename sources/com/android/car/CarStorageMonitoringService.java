package com.android.car;

import android.car.storagemonitoring.ICarStorageMonitoring;
import android.car.storagemonitoring.IIoStatsListener;
import android.car.storagemonitoring.IoStats;
import android.car.storagemonitoring.IoStatsEntry;
import android.car.storagemonitoring.LifetimeWriteInfo;
import android.car.storagemonitoring.UidIoRecord;
import android.car.storagemonitoring.WearEstimate;
import android.car.storagemonitoring.WearEstimateChange;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.JsonWriter;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;
import com.android.car.Manifest;
import com.android.car.internal.CarPermission;
import com.android.car.storagemonitoring.IoStatsTracker;
import com.android.car.storagemonitoring.UidIoStatsProvider;
import com.android.car.storagemonitoring.WearEstimateRecord;
import com.android.car.storagemonitoring.WearHistory;
import com.android.car.storagemonitoring.WearInformation;
import com.android.car.storagemonitoring.WearInformationProvider;
import com.android.car.systeminterface.SystemInterface;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
/* loaded from: classes3.dex */
public class CarStorageMonitoringService extends ICarStorageMonitoring.Stub implements CarServiceBase {
    private static final boolean DBG = false;
    private static final boolean ENABLE_DUMP = false;
    public static final String INTENT_EXCESSIVE_IO = "android.car.storagemonitoring.EXCESSIVE_IO";
    static final String LIFETIME_WRITES_FILENAME = "lifetime_write";
    private static final int MIN_WEAR_ESTIMATE_OF_CONCERN = 80;
    public static final long SHUTDOWN_COST_INFO_MISSING = -1;
    private static final String TAG = "CAR.STORAGE";
    static final String UPTIME_TRACKER_FILENAME = "service_uptime";
    static final String WEAR_INFO_FILENAME = "wear_info";
    private final Configuration mConfiguration;
    private final Context mContext;
    private final SlidingWindow<IoStats> mIoStatsSamples;
    private final File mLifetimeWriteFile;
    private final RemoteCallbackList<IIoStatsListener> mListeners;
    private final OnShutdownReboot mOnShutdownReboot;
    private String mShutdownCostMissingReason;
    private final CarPermission mStorageMonitoringPermission;
    private final SystemInterface mSystemInterface;
    private final UidIoStatsProvider mUidIoStatsProvider;
    private final File mUptimeTrackerFile;
    private List<WearEstimateChange> mWearEstimateChanges;
    private final File mWearInfoFile;
    private final WearInformationProvider[] mWearInformationProviders;
    private final Object mIoStatsSamplesLock = new Object();
    private UptimeTracker mUptimeTracker = null;
    private Optional<WearInformation> mWearInformation = Optional.empty();
    private List<IoStatsEntry> mBootIoStats = Collections.emptyList();
    private IoStatsTracker mIoStatsTracker = null;
    private boolean mInitialized = false;
    private long mShutdownCostInfo = -1;

    public CarStorageMonitoringService(Context context, SystemInterface systemInterface) {
        this.mWearEstimateChanges = Collections.emptyList();
        this.mContext = context;
        Resources resources = this.mContext.getResources();
        this.mConfiguration = new Configuration(resources);
        if (Log.isLoggable("CAR.STORAGE", 3)) {
            Slog.d("CAR.STORAGE", "service configuration: " + this.mConfiguration);
        }
        this.mUidIoStatsProvider = systemInterface.getUidIoStatsProvider();
        this.mUptimeTrackerFile = new File(systemInterface.getSystemCarDir(), UPTIME_TRACKER_FILENAME);
        this.mWearInfoFile = new File(systemInterface.getSystemCarDir(), WEAR_INFO_FILENAME);
        this.mLifetimeWriteFile = new File(systemInterface.getSystemCarDir(), LIFETIME_WRITES_FILENAME);
        this.mOnShutdownReboot = new OnShutdownReboot(this.mContext);
        this.mSystemInterface = systemInterface;
        this.mWearInformationProviders = systemInterface.getFlashWearInformationProviders();
        this.mStorageMonitoringPermission = new CarPermission(this.mContext, Manifest.permission.STORAGE_MONITORING);
        this.mWearEstimateChanges = Collections.emptyList();
        this.mIoStatsSamples = new SlidingWindow<>(this.mConfiguration.ioStatsNumSamplesToStore);
        this.mListeners = new RemoteCallbackList<>();
        systemInterface.scheduleActionForBootCompleted(new Runnable() { // from class: com.android.car.-$$Lambda$CarStorageMonitoringService$4lfr8eOeJ5l1CIfzvzF8g3QqC4U
            @Override // java.lang.Runnable
            public final void run() {
                CarStorageMonitoringService.this.doInitServiceIfNeeded();
            }
        }, Duration.ofSeconds(10L));
    }

    private Optional<WearInformation> loadWearInformation() {
        WearInformationProvider[] wearInformationProviderArr;
        for (WearInformationProvider provider : this.mWearInformationProviders) {
            WearInformation wearInfo = provider.load();
            if (wearInfo != null) {
                Slog.d("CAR.STORAGE", "retrieved wear info " + wearInfo + " via provider " + provider);
                return Optional.of(wearInfo);
            }
        }
        Slog.d("CAR.STORAGE", "no wear info available");
        return Optional.empty();
    }

    private WearHistory loadWearHistory() {
        if (this.mWearInfoFile.exists()) {
            try {
                WearHistory wearHistory = WearHistory.fromJson(this.mWearInfoFile);
                Slog.d("CAR.STORAGE", "retrieved wear history " + wearHistory);
                return wearHistory;
            } catch (IOException | JSONException e) {
                Slog.e("CAR.STORAGE", "unable to read wear info file " + this.mWearInfoFile, e);
            }
        }
        Slog.d("CAR.STORAGE", "no wear history available");
        return new WearHistory();
    }

    private boolean addEventIfNeeded(WearHistory wearHistory) {
        WearEstimate lastWearEstimate;
        if (this.mWearInformation.isPresent()) {
            WearInformation wearInformation = this.mWearInformation.get();
            WearEstimate currentWearEstimate = wearInformation.toWearEstimate();
            if (wearHistory.size() == 0) {
                lastWearEstimate = WearEstimate.UNKNOWN_ESTIMATE;
            } else {
                lastWearEstimate = wearHistory.getLast().getNewWearEstimate();
            }
            if (currentWearEstimate.equals(lastWearEstimate)) {
                return false;
            }
            WearEstimateRecord newRecord = new WearEstimateRecord(lastWearEstimate, currentWearEstimate, this.mUptimeTracker.getTotalUptime(), Instant.now());
            Slog.d("CAR.STORAGE", "new wear record generated " + newRecord);
            wearHistory.add(newRecord);
            return true;
        }
        return false;
    }

    private void storeWearHistory(WearHistory wearHistory) {
        try {
            JsonWriter jsonWriter = new JsonWriter(new FileWriter(this.mWearInfoFile));
            wearHistory.writeToJson(jsonWriter);
            jsonWriter.close();
        } catch (IOException e) {
            Slog.e("CAR.STORAGE", "unable to write wear info file" + this.mWearInfoFile, e);
        }
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
        Slog.d("CAR.STORAGE", "CarStorageMonitoringService init()");
        this.mUptimeTracker = new UptimeTracker(this.mUptimeTrackerFile, this.mConfiguration.uptimeIntervalBetweenUptimeDataWriteMs, this.mSystemInterface);
    }

    private void launchWearChangeActivity() {
        String activityPath = this.mConfiguration.activityHandlerForFlashWearChanges;
        if (activityPath.isEmpty()) {
            return;
        }
        try {
            ComponentName activityComponent = (ComponentName) Objects.requireNonNull(ComponentName.unflattenFromString(activityPath));
            Intent intent = new Intent();
            intent.setComponent(activityComponent);
            intent.addFlags(268435456);
            this.mContext.startActivity(intent);
        } catch (ActivityNotFoundException | NullPointerException e) {
            Slog.e("CAR.STORAGE", "value of activityHandlerForFlashWearChanges invalid non-empty string " + activityPath, e);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void logOnAdverseWearLevel(WearInformation wearInformation) {
        if (wearInformation.preEolInfo > 1 || Math.max(wearInformation.lifetimeEstimateA, wearInformation.lifetimeEstimateB) >= 80) {
            Slog.w("CAR.STORAGE", "flash storage reached wear a level that requires attention: " + wearInformation);
        }
    }

    private SparseArray<UidIoRecord> loadNewIoStats() {
        SparseArray<UidIoRecord> ioRecords = this.mUidIoStatsProvider.load();
        return ioRecords == null ? new SparseArray<>() : ioRecords;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void collectNewIoMetrics() {
        IoStats ioStats;
        this.mIoStatsTracker.update(loadNewIoStats());
        synchronized (this.mIoStatsSamplesLock) {
            ioStats = new IoStats((List) SparseArrayStream.valueStream(this.mIoStatsTracker.getCurrentSample()).collect(Collectors.toList()), this.mSystemInterface.getUptime());
            this.mIoStatsSamples.add(ioStats);
        }
        dispatchNewIoEvent(ioStats);
        if (needsExcessiveIoBroadcast()) {
            Slog.d("CAR.STORAGE", "about to send android.car.storagemonitoring.EXCESSIVE_IO");
            sendExcessiveIoBroadcast();
        }
    }

    private void sendExcessiveIoBroadcast() {
        Slog.w("CAR.STORAGE", "sending android.car.storagemonitoring.EXCESSIVE_IO");
        String receiverPath = this.mConfiguration.intentReceiverForUnacceptableIoMetrics;
        if (receiverPath.isEmpty()) {
            return;
        }
        try {
            ComponentName receiverComponent = (ComponentName) Objects.requireNonNull(ComponentName.unflattenFromString(receiverPath));
            Intent intent = new Intent(INTENT_EXCESSIVE_IO);
            intent.setComponent(receiverComponent);
            intent.addFlags(268435456);
            this.mContext.sendBroadcast(intent, this.mStorageMonitoringPermission.toString());
        } catch (NullPointerException e) {
            Slog.e("CAR.STORAGE", "value of intentReceiverForUnacceptableIoMetrics non-null but invalid:" + receiverPath, e);
        }
    }

    private boolean needsExcessiveIoBroadcast() {
        boolean z;
        synchronized (this.mIoStatsSamplesLock) {
            z = this.mIoStatsSamples.count(new Predicate() { // from class: com.android.car.-$$Lambda$CarStorageMonitoringService$pCAdAR4fOJdGCeOYd7DWFDmMrYw
                @Override // java.util.function.Predicate
                public final boolean test(Object obj) {
                    return CarStorageMonitoringService.this.lambda$needsExcessiveIoBroadcast$1$CarStorageMonitoringService((IoStats) obj);
                }
            }) > this.mConfiguration.maxExcessiveIoSamplesInWindow;
        }
        return z;
    }

    public /* synthetic */ boolean lambda$needsExcessiveIoBroadcast$1$CarStorageMonitoringService(IoStats delta) {
        IoStatsEntry.Metrics total = delta.getTotals();
        boolean tooManyBytesWritten = total.bytesWrittenToStorage > this.mConfiguration.acceptableBytesWrittenPerSample;
        boolean tooManyFsyncCalls = total.fsyncCalls > ((long) this.mConfiguration.acceptableFsyncCallsPerSample);
        return tooManyBytesWritten || tooManyFsyncCalls;
    }

    private void dispatchNewIoEvent(final IoStats delta) {
        int listenersCount = this.mListeners.beginBroadcast();
        IntStream.range(0, listenersCount).forEach(new IntConsumer() { // from class: com.android.car.-$$Lambda$CarStorageMonitoringService$BB7XOuHptu3JcCtYJXf2rOLPkCU
            @Override // java.util.function.IntConsumer
            public final void accept(int i) {
                CarStorageMonitoringService.this.lambda$dispatchNewIoEvent$2$CarStorageMonitoringService(delta, i);
            }
        });
        this.mListeners.finishBroadcast();
    }

    public /* synthetic */ void lambda$dispatchNewIoEvent$2$CarStorageMonitoringService(IoStats delta, int i) {
        try {
            this.mListeners.getBroadcastItem(i).onSnapshot(delta);
        } catch (RemoteException e) {
            Slog.w("CAR.STORAGE", "failed to dispatch snapshot", e);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public synchronized void doInitServiceIfNeeded() {
        if (this.mInitialized) {
            return;
        }
        Slog.d("CAR.STORAGE", "initializing CarStorageMonitoringService");
        this.mWearInformation = loadWearInformation();
        WearHistory wearHistory = loadWearHistory();
        boolean didWearChangeHappen = addEventIfNeeded(wearHistory);
        if (didWearChangeHappen) {
            storeWearHistory(wearHistory);
        }
        Slog.d("CAR.STORAGE", "wear history being tracked is " + wearHistory);
        this.mWearEstimateChanges = wearHistory.toWearEstimateChanges((long) this.mConfiguration.acceptableHoursPerOnePercentFlashWear);
        this.mOnShutdownReboot.addAction(new BiConsumer() { // from class: com.android.car.-$$Lambda$CarStorageMonitoringService$_WmeglMKVK32-medXtW62Rd8ChI
            @Override // java.util.function.BiConsumer
            public final void accept(Object obj, Object obj2) {
                CarStorageMonitoringService.this.lambda$doInitServiceIfNeeded$3$CarStorageMonitoringService((Context) obj, (Intent) obj2);
            }
        }).addAction(new BiConsumer() { // from class: com.android.car.-$$Lambda$CarStorageMonitoringService$H5rrupwJsCJtCKqZLPCoVFCmMFc
            @Override // java.util.function.BiConsumer
            public final void accept(Object obj, Object obj2) {
                CarStorageMonitoringService.this.lambda$doInitServiceIfNeeded$4$CarStorageMonitoringService((Context) obj, (Intent) obj2);
            }
        });
        this.mWearInformation.ifPresent(new Consumer() { // from class: com.android.car.-$$Lambda$CarStorageMonitoringService$qW99OHY0SHOxhCevR7AsJS-Qwkc
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                CarStorageMonitoringService.logOnAdverseWearLevel((WearInformation) obj);
            }
        });
        final long bootUptime = this.mSystemInterface.getUptime();
        this.mBootIoStats = (List) SparseArrayStream.valueStream(loadNewIoStats()).map(new Function() { // from class: com.android.car.-$$Lambda$CarStorageMonitoringService$ZJRgUrXgNv6rcjMAtNH60oxt-XM
            @Override // java.util.function.Function
            public final Object apply(Object obj) {
                return CarStorageMonitoringService.lambda$doInitServiceIfNeeded$5(bootUptime, (UidIoRecord) obj);
            }
        }).collect(Collectors.toList());
        this.mIoStatsTracker = new IoStatsTracker(this.mBootIoStats, this.mConfiguration.ioStatsRefreshRateMs, this.mSystemInterface.getSystemStateInterface());
        if (this.mConfiguration.ioStatsNumSamplesToStore > 0) {
            this.mSystemInterface.scheduleAction(new Runnable() { // from class: com.android.car.-$$Lambda$CarStorageMonitoringService$xKTnl5Ai9zjrmyRyKQQQttzcWnE
                @Override // java.lang.Runnable
                public final void run() {
                    CarStorageMonitoringService.this.collectNewIoMetrics();
                }
            }, this.mConfiguration.ioStatsRefreshRateMs);
        } else {
            Slog.i("CAR.STORAGE", "service configuration disabled I/O sample window. not collecting samples");
        }
        this.mShutdownCostInfo = computeShutdownCost();
        Slog.d("CAR.STORAGE", "calculated data written in last shutdown was " + this.mShutdownCostInfo + " bytes");
        this.mLifetimeWriteFile.delete();
        Slog.i("CAR.STORAGE", "CarStorageMonitoringService is up");
        this.mInitialized = true;
    }

    public /* synthetic */ void lambda$doInitServiceIfNeeded$3$CarStorageMonitoringService(Context c, Intent i) {
        logLifetimeWrites();
    }

    public /* synthetic */ void lambda$doInitServiceIfNeeded$4$CarStorageMonitoringService(Context c, Intent i) {
        release();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ IoStatsEntry lambda$doInitServiceIfNeeded$5(long bootUptime, UidIoRecord record) {
        IoStatsEntry stats = new IoStatsEntry(record, bootUptime);
        return stats;
    }

    private long computeShutdownCost() {
        List<LifetimeWriteInfo> shutdownWrites = loadLifetimeWrites();
        if (shutdownWrites.isEmpty()) {
            Slog.d("CAR.STORAGE", "lifetime write data from last shutdown missing");
            this.mShutdownCostMissingReason = "no historical writes stored at last shutdown";
            return -1L;
        }
        List<LifetimeWriteInfo> currentWrites = Arrays.asList(this.mSystemInterface.getLifetimeWriteInfoProvider().load());
        if (currentWrites.isEmpty()) {
            Slog.d("CAR.STORAGE", "current lifetime write data missing");
            this.mShutdownCostMissingReason = "current write data cannot be obtained";
            return -1L;
        }
        long shutdownCost = 0;
        final Map<String, Long> shutdownLifetimeWrites = new HashMap<>();
        shutdownWrites.forEach(new Consumer() { // from class: com.android.car.-$$Lambda$CarStorageMonitoringService$M9OR9NrtUFo4vxYupRO3Lv4zVAI
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                CarStorageMonitoringService.lambda$computeShutdownCost$6(shutdownLifetimeWrites, (LifetimeWriteInfo) obj);
            }
        });
        for (int i = 0; i < currentWrites.size(); i++) {
            LifetimeWriteInfo li = currentWrites.get(i);
            long writtenAtShutdown = shutdownLifetimeWrites.getOrDefault(li.partition, Long.valueOf(li.writtenBytes)).longValue();
            long costDelta = li.writtenBytes - writtenAtShutdown;
            if (costDelta < 0) {
                this.mShutdownCostMissingReason = li.partition + " has a negative write amount (" + costDelta + " bytes)";
                Slog.e("CAR.STORAGE", "partition " + li.partition + " reported " + costDelta + " bytes written to it during shutdown. assuming we can't determine proper shutdown information.");
                return -1L;
            }
            Slog.d("CAR.STORAGE", "partition " + li.partition + " had " + costDelta + " bytes written to it during shutdown");
            shutdownCost += costDelta;
        }
        return shutdownCost;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ void lambda$computeShutdownCost$6(Map shutdownLifetimeWrites, LifetimeWriteInfo li) {
        Long l = (Long) shutdownLifetimeWrites.put(li.partition, Long.valueOf(li.writtenBytes));
    }

    private List<LifetimeWriteInfo> loadLifetimeWrites() {
        if (!this.mLifetimeWriteFile.exists() || !this.mLifetimeWriteFile.isFile()) {
            Slog.d("CAR.STORAGE", "lifetime write file missing or inaccessible " + this.mLifetimeWriteFile);
            return Collections.emptyList();
        }
        try {
            JSONObject jsonObject = new JSONObject(new String(Files.readAllBytes(this.mLifetimeWriteFile.toPath())));
            JSONArray jsonArray = jsonObject.getJSONArray("lifetimeWriteInfo");
            List<LifetimeWriteInfo> result = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                result.add(new LifetimeWriteInfo(jsonArray.getJSONObject(i)));
            }
            return result;
        } catch (IOException | JSONException e) {
            Slog.e("CAR.STORAGE", "lifetime write file does not contain valid JSON", e);
            return Collections.emptyList();
        }
    }

    private void logLifetimeWrites() {
        try {
            LifetimeWriteInfo[] lifetimeWriteInfos = this.mSystemInterface.getLifetimeWriteInfoProvider().load();
            JsonWriter jsonWriter = new JsonWriter(new FileWriter(this.mLifetimeWriteFile));
            jsonWriter.beginObject();
            jsonWriter.name("lifetimeWriteInfo").beginArray();
            for (LifetimeWriteInfo writeInfo : lifetimeWriteInfos) {
                Slog.d("CAR.STORAGE", "storing lifetime write info " + writeInfo);
                writeInfo.writeToJson(jsonWriter);
            }
            jsonWriter.endArray().endObject();
            jsonWriter.close();
        } catch (IOException e) {
            Slog.e("CAR.STORAGE", "unable to save lifetime write info on shutdown", e);
        }
    }

    @Override // com.android.car.CarServiceBase
    public void release() {
        Slog.i("CAR.STORAGE", "tearing down CarStorageMonitoringService");
        UptimeTracker uptimeTracker = this.mUptimeTracker;
        if (uptimeTracker != null) {
            uptimeTracker.onDestroy();
        }
        this.mOnShutdownReboot.clearActions();
        this.mListeners.kill();
    }

    private static /* synthetic */ String lambda$dump$7(IoStats sample) {
        return (String) sample.getStats().stream().map(new Function() { // from class: com.android.car.-$$Lambda$N7T3rOI2b6LP6BgZNum38gpOWD0
            @Override // java.util.function.Function
            public final Object apply(Object obj) {
                return ((IoStatsEntry) obj).toString();
            }
        }).collect(Collectors.joining("\n"));
    }

    @Override // com.android.car.CarServiceBase
    public void dump(PrintWriter writer) {
    }

    public int getPreEolIndicatorStatus() {
        this.mStorageMonitoringPermission.assertGranted();
        doInitServiceIfNeeded();
        return ((Integer) this.mWearInformation.map(new Function() { // from class: com.android.car.-$$Lambda$CarStorageMonitoringService$3JpWX-E7qURpq44XDloaxqv9kn4
            @Override // java.util.function.Function
            public final Object apply(Object obj) {
                Integer valueOf;
                valueOf = Integer.valueOf(((WearInformation) obj).preEolInfo);
                return valueOf;
            }
        }).orElse(0)).intValue();
    }

    public WearEstimate getWearEstimate() {
        this.mStorageMonitoringPermission.assertGranted();
        doInitServiceIfNeeded();
        return (WearEstimate) this.mWearInformation.map(new Function() { // from class: com.android.car.-$$Lambda$CarStorageMonitoringService$UxRDIRw7F7H6x6CvEJacqObxq-4
            @Override // java.util.function.Function
            public final Object apply(Object obj) {
                return CarStorageMonitoringService.lambda$getWearEstimate$9((WearInformation) obj);
            }
        }).orElse(WearEstimate.UNKNOWN_ESTIMATE);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ WearEstimate lambda$getWearEstimate$9(WearInformation wi) {
        return new WearEstimate(wi.lifetimeEstimateA, wi.lifetimeEstimateB);
    }

    public List<WearEstimateChange> getWearEstimateHistory() {
        this.mStorageMonitoringPermission.assertGranted();
        doInitServiceIfNeeded();
        return this.mWearEstimateChanges;
    }

    public List<IoStatsEntry> getBootIoStats() {
        this.mStorageMonitoringPermission.assertGranted();
        doInitServiceIfNeeded();
        return this.mBootIoStats;
    }

    public List<IoStatsEntry> getAggregateIoStats() {
        this.mStorageMonitoringPermission.assertGranted();
        doInitServiceIfNeeded();
        return (List) SparseArrayStream.valueStream(this.mIoStatsTracker.getTotal()).collect(Collectors.toList());
    }

    public long getShutdownDiskWriteAmount() {
        this.mStorageMonitoringPermission.assertGranted();
        doInitServiceIfNeeded();
        return this.mShutdownCostInfo;
    }

    public List<IoStats> getIoStatsDeltas() {
        List<IoStats> list;
        this.mStorageMonitoringPermission.assertGranted();
        doInitServiceIfNeeded();
        synchronized (this.mIoStatsSamplesLock) {
            list = (List) this.mIoStatsSamples.stream().collect(Collectors.toList());
        }
        return list;
    }

    public void registerListener(IIoStatsListener listener) {
        this.mStorageMonitoringPermission.assertGranted();
        doInitServiceIfNeeded();
        this.mListeners.register(listener);
    }

    public void unregisterListener(IIoStatsListener listener) {
        this.mStorageMonitoringPermission.assertGranted();
        this.mListeners.unregister(listener);
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static final class Configuration {
        final long acceptableBytesWrittenPerSample;
        final int acceptableFsyncCallsPerSample;
        final int acceptableHoursPerOnePercentFlashWear;
        final String activityHandlerForFlashWearChanges;
        final String intentReceiverForUnacceptableIoMetrics;
        final int ioStatsNumSamplesToStore;
        final int ioStatsRefreshRateMs;
        final int maxExcessiveIoSamplesInWindow;
        final long uptimeIntervalBetweenUptimeDataWriteMs;

        Configuration(Resources resources) throws Resources.NotFoundException {
            this.ioStatsNumSamplesToStore = resources.getInteger(R.integer.ioStatsNumSamplesToStore);
            this.acceptableBytesWrittenPerSample = resources.getInteger(R.integer.acceptableWrittenKBytesPerSample) * 1024;
            this.acceptableFsyncCallsPerSample = resources.getInteger(R.integer.acceptableFsyncCallsPerSample);
            this.maxExcessiveIoSamplesInWindow = resources.getInteger(R.integer.maxExcessiveIoSamplesInWindow);
            this.uptimeIntervalBetweenUptimeDataWriteMs = resources.getInteger(R.integer.uptimeHoursIntervalBetweenUptimeDataWrite) * 3600000;
            this.acceptableHoursPerOnePercentFlashWear = resources.getInteger(R.integer.acceptableHoursPerOnePercentFlashWear);
            this.ioStatsRefreshRateMs = resources.getInteger(R.integer.ioStatsRefreshRateSeconds) * 1000;
            this.activityHandlerForFlashWearChanges = resources.getString(R.string.activityHandlerForFlashWearChanges);
            this.intentReceiverForUnacceptableIoMetrics = resources.getString(R.string.intentReceiverForUnacceptableIoMetrics);
        }

        public String toString() {
            return String.format("acceptableBytesWrittenPerSample = %d, acceptableFsyncCallsPerSample = %d, acceptableHoursPerOnePercentFlashWear = %d, activityHandlerForFlashWearChanges = %s, intentReceiverForUnacceptableIoMetrics = %s, ioStatsNumSamplesToStore = %d, ioStatsRefreshRateMs = %d, maxExcessiveIoSamplesInWindow = %d, uptimeIntervalBetweenUptimeDataWriteMs = %d", Long.valueOf(this.acceptableBytesWrittenPerSample), Integer.valueOf(this.acceptableFsyncCallsPerSample), Integer.valueOf(this.acceptableHoursPerOnePercentFlashWear), this.activityHandlerForFlashWearChanges, this.intentReceiverForUnacceptableIoMetrics, Integer.valueOf(this.ioStatsNumSamplesToStore), Integer.valueOf(this.ioStatsRefreshRateMs), Integer.valueOf(this.maxExcessiveIoSamplesInWindow), Long.valueOf(this.uptimeIntervalBetweenUptimeDataWriteMs));
        }
    }
}
