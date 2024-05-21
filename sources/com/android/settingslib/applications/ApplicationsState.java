package com.android.settingslib.applications;

import android.app.ActivityManager;
import android.app.AppGlobals;
import android.app.Application;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.ModuleInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.content.pm.ParceledListSlice;
import android.content.pm.ResolveInfo;
import android.content.pm.UserInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.format.Formatter;
import android.util.IconDrawableFactory;
import android.util.Log;
import android.util.SparseArray;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.IntentCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import com.android.internal.util.ArrayUtils;
import com.android.settingslib.applications.ApplicationsState;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
/* loaded from: classes3.dex */
public class ApplicationsState {
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_LOCKING = false;
    public static final int DEFAULT_SESSION_FLAGS = 15;
    public static final int FLAG_SESSION_REQUEST_HOME_APP = 1;
    public static final int FLAG_SESSION_REQUEST_ICONS = 2;
    public static final int FLAG_SESSION_REQUEST_LAUNCHER = 8;
    public static final int FLAG_SESSION_REQUEST_LEANBACK_LAUNCHER = 16;
    public static final int FLAG_SESSION_REQUEST_SIZES = 4;
    public static final int SIZE_INVALID = -2;
    public static final int SIZE_UNKNOWN = -1;
    private static final String TAG = "ApplicationsState";
    @VisibleForTesting
    static ApplicationsState sInstance;
    final int mAdminRetrieveFlags;
    final BackgroundHandler mBackgroundHandler;
    final Context mContext;
    String mCurComputingSizePkg;
    int mCurComputingSizeUserId;
    UUID mCurComputingSizeUuid;
    final IconDrawableFactory mDrawableFactory;
    boolean mHaveDisabledApps;
    boolean mHaveInstantApps;
    final IPackageManager mIpm;
    PackageIntentReceiver mPackageIntentReceiver;
    final PackageManager mPm;
    boolean mResumed;
    final int mRetrieveFlags;
    boolean mSessionsChanged;
    final StorageStatsManager mStats;
    final HandlerThread mThread;
    final UserManager mUm;
    private static final Object sLock = new Object();
    private static final Pattern REMOVE_DIACRITICALS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    public static final Comparator<AppEntry> ALPHA_COMPARATOR = new Comparator<AppEntry>() { // from class: com.android.settingslib.applications.ApplicationsState.1
        private final Collator sCollator = Collator.getInstance();

        @Override // java.util.Comparator
        public int compare(AppEntry object1, AppEntry object2) {
            int compareResult;
            int compareResult2 = this.sCollator.compare(object1.label, object2.label);
            if (compareResult2 != 0) {
                return compareResult2;
            }
            if (object1.info != null && object2.info != null && (compareResult = this.sCollator.compare(object1.info.packageName, object2.info.packageName)) != 0) {
                return compareResult;
            }
            return object1.info.uid - object2.info.uid;
        }
    };
    public static final Comparator<AppEntry> SIZE_COMPARATOR = new Comparator<AppEntry>() { // from class: com.android.settingslib.applications.ApplicationsState.2
        @Override // java.util.Comparator
        public int compare(AppEntry object1, AppEntry object2) {
            if (object1.size < object2.size) {
                return 1;
            }
            if (object1.size > object2.size) {
                return -1;
            }
            return ApplicationsState.ALPHA_COMPARATOR.compare(object1, object2);
        }
    };
    public static final Comparator<AppEntry> INTERNAL_SIZE_COMPARATOR = new Comparator<AppEntry>() { // from class: com.android.settingslib.applications.ApplicationsState.3
        @Override // java.util.Comparator
        public int compare(AppEntry object1, AppEntry object2) {
            if (object1.internalSize < object2.internalSize) {
                return 1;
            }
            if (object1.internalSize > object2.internalSize) {
                return -1;
            }
            return ApplicationsState.ALPHA_COMPARATOR.compare(object1, object2);
        }
    };
    public static final Comparator<AppEntry> EXTERNAL_SIZE_COMPARATOR = new Comparator<AppEntry>() { // from class: com.android.settingslib.applications.ApplicationsState.4
        @Override // java.util.Comparator
        public int compare(AppEntry object1, AppEntry object2) {
            if (object1.externalSize < object2.externalSize) {
                return 1;
            }
            if (object1.externalSize > object2.externalSize) {
                return -1;
            }
            return ApplicationsState.ALPHA_COMPARATOR.compare(object1, object2);
        }
    };
    public static final AppFilter FILTER_PERSONAL = new AppFilter() { // from class: com.android.settingslib.applications.ApplicationsState.5
        private int mCurrentUser;

        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public void init() {
            this.mCurrentUser = ActivityManager.getCurrentUser();
        }

        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public boolean filterApp(AppEntry entry) {
            return UserHandle.getUserId(entry.info.uid) == this.mCurrentUser;
        }
    };
    public static final AppFilter FILTER_WITHOUT_DISABLED_UNTIL_USED = new AppFilter() { // from class: com.android.settingslib.applications.ApplicationsState.6
        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public void init() {
        }

        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public boolean filterApp(AppEntry entry) {
            return entry.info.enabledSetting != 4;
        }
    };
    public static final AppFilter FILTER_WORK = new AppFilter() { // from class: com.android.settingslib.applications.ApplicationsState.7
        private int mCurrentUser;

        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public void init() {
            this.mCurrentUser = ActivityManager.getCurrentUser();
        }

        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public boolean filterApp(AppEntry entry) {
            return UserHandle.getUserId(entry.info.uid) != this.mCurrentUser;
        }
    };
    public static final AppFilter FILTER_DOWNLOADED_AND_LAUNCHER = new AppFilter() { // from class: com.android.settingslib.applications.ApplicationsState.8
        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public void init() {
        }

        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public boolean filterApp(AppEntry entry) {
            if (AppUtils.isInstant(entry.info)) {
                return false;
            }
            if (ApplicationsState.hasFlag(entry.info.flags, 128) || !ApplicationsState.hasFlag(entry.info.flags, 1) || entry.hasLauncherEntry) {
                return true;
            }
            return ApplicationsState.hasFlag(entry.info.flags, 1) && entry.isHomeApp;
        }
    };
    public static final AppFilter FILTER_DOWNLOADED_AND_LAUNCHER_AND_INSTANT = new AppFilter() { // from class: com.android.settingslib.applications.ApplicationsState.9
        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public void init() {
        }

        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public boolean filterApp(AppEntry entry) {
            return AppUtils.isInstant(entry.info) || ApplicationsState.FILTER_DOWNLOADED_AND_LAUNCHER.filterApp(entry);
        }
    };
    public static final AppFilter FILTER_THIRD_PARTY = new AppFilter() { // from class: com.android.settingslib.applications.ApplicationsState.10
        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public void init() {
        }

        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public boolean filterApp(AppEntry entry) {
            return ApplicationsState.hasFlag(entry.info.flags, 128) || !ApplicationsState.hasFlag(entry.info.flags, 1);
        }
    };
    public static final AppFilter FILTER_DISABLED = new AppFilter() { // from class: com.android.settingslib.applications.ApplicationsState.11
        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public void init() {
        }

        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public boolean filterApp(AppEntry entry) {
            return (entry.info.enabled || AppUtils.isInstant(entry.info)) ? false : true;
        }
    };
    public static final AppFilter FILTER_INSTANT = new AppFilter() { // from class: com.android.settingslib.applications.ApplicationsState.12
        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public void init() {
        }

        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public boolean filterApp(AppEntry entry) {
            return AppUtils.isInstant(entry.info);
        }
    };
    public static final AppFilter FILTER_ALL_ENABLED = new AppFilter() { // from class: com.android.settingslib.applications.ApplicationsState.13
        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public void init() {
        }

        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public boolean filterApp(AppEntry entry) {
            return entry.info.enabled && !AppUtils.isInstant(entry.info);
        }
    };
    public static final AppFilter FILTER_EVERYTHING = new AppFilter() { // from class: com.android.settingslib.applications.ApplicationsState.14
        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public void init() {
        }

        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public boolean filterApp(AppEntry entry) {
            return true;
        }
    };
    public static final AppFilter FILTER_WITH_DOMAIN_URLS = new AppFilter() { // from class: com.android.settingslib.applications.ApplicationsState.15
        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public void init() {
        }

        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public boolean filterApp(AppEntry entry) {
            return !AppUtils.isInstant(entry.info) && ApplicationsState.hasFlag(entry.info.privateFlags, 16);
        }
    };
    public static final AppFilter FILTER_NOT_HIDE = new AppFilter() { // from class: com.android.settingslib.applications.ApplicationsState.16
        private String[] mHidePackageNames;

        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public void init(Context context) {
            this.mHidePackageNames = context.getResources().getStringArray(17236038);
        }

        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public void init() {
        }

        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public boolean filterApp(AppEntry entry) {
            if (ArrayUtils.contains(this.mHidePackageNames, entry.info.packageName)) {
                return entry.info.enabled && entry.info.enabledSetting != 4;
            }
            return true;
        }
    };
    public static final AppFilter FILTER_GAMES = new AppFilter() { // from class: com.android.settingslib.applications.ApplicationsState.17
        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public void init() {
        }

        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public boolean filterApp(AppEntry info) {
            boolean isGame;
            synchronized (info.info) {
                isGame = ApplicationsState.hasFlag(info.info.flags, 33554432) || info.info.category == 0;
            }
            return isGame;
        }
    };
    public static final AppFilter FILTER_AUDIO = new AppFilter() { // from class: com.android.settingslib.applications.ApplicationsState.18
        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public void init() {
        }

        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public boolean filterApp(AppEntry entry) {
            boolean isMusicApp;
            synchronized (entry) {
                boolean z = true;
                if (entry.info.category != 1) {
                    z = false;
                }
                isMusicApp = z;
            }
            return isMusicApp;
        }
    };
    public static final AppFilter FILTER_MOVIES = new AppFilter() { // from class: com.android.settingslib.applications.ApplicationsState.19
        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public void init() {
        }

        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public boolean filterApp(AppEntry entry) {
            boolean isMovieApp;
            synchronized (entry) {
                isMovieApp = entry.info.category == 2;
            }
            return isMovieApp;
        }
    };
    public static final AppFilter FILTER_PHOTOS = new AppFilter() { // from class: com.android.settingslib.applications.ApplicationsState.20
        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public void init() {
        }

        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public boolean filterApp(AppEntry entry) {
            boolean isPhotosApp;
            synchronized (entry) {
                isPhotosApp = entry.info.category == 3;
            }
            return isPhotosApp;
        }
    };
    public static final AppFilter FILTER_OTHER_APPS = new AppFilter() { // from class: com.android.settingslib.applications.ApplicationsState.21
        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public void init() {
        }

        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public boolean filterApp(AppEntry entry) {
            boolean isCategorized;
            synchronized (entry) {
                if (!ApplicationsState.FILTER_AUDIO.filterApp(entry) && !ApplicationsState.FILTER_GAMES.filterApp(entry) && !ApplicationsState.FILTER_MOVIES.filterApp(entry) && !ApplicationsState.FILTER_PHOTOS.filterApp(entry)) {
                    isCategorized = false;
                }
                isCategorized = true;
            }
            return !isCategorized;
        }
    };
    final ArrayList<Session> mSessions = new ArrayList<>();
    final ArrayList<Session> mRebuildingSessions = new ArrayList<>();
    private InterestingConfigChanges mInterestingConfigChanges = new InterestingConfigChanges();
    final SparseArray<HashMap<String, AppEntry>> mEntriesMap = new SparseArray<>();
    final ArrayList<AppEntry> mAppEntries = new ArrayList<>();
    List<ApplicationInfo> mApplications = new ArrayList();
    long mCurId = 1;
    final HashMap<String, Boolean> mSystemModules = new HashMap<>();
    final ArrayList<WeakReference<Session>> mActiveSessions = new ArrayList<>();
    final MainHandler mMainHandler = new MainHandler(Looper.getMainLooper());

    /* loaded from: classes3.dex */
    public interface Callbacks {
        void onAllSizesComputed();

        void onLauncherInfoChanged();

        void onLoadEntriesCompleted();

        void onPackageIconChanged();

        void onPackageListChanged();

        void onPackageSizeChanged(String str);

        void onRebuildComplete(ArrayList<AppEntry> arrayList);

        void onRunningStateChanged(boolean z);
    }

    @Retention(RetentionPolicy.SOURCE)
    /* loaded from: classes3.dex */
    public @interface SessionFlags {
    }

    /* loaded from: classes3.dex */
    public static class SizeInfo {
        public long cacheSize;
        public long codeSize;
        public long dataSize;
        public long externalCacheSize;
        public long externalCodeSize;
        public long externalDataSize;
    }

    public static ApplicationsState getInstance(Application app) {
        return getInstance(app, AppGlobals.getPackageManager());
    }

    @VisibleForTesting
    static ApplicationsState getInstance(Application app, IPackageManager iPackageManager) {
        ApplicationsState applicationsState;
        synchronized (sLock) {
            if (sInstance == null) {
                sInstance = new ApplicationsState(app, iPackageManager);
            }
            applicationsState = sInstance;
        }
        return applicationsState;
    }

    @VisibleForTesting
    void setInterestingConfigChanges(InterestingConfigChanges interestingConfigChanges) {
        this.mInterestingConfigChanges = interestingConfigChanges;
    }

    private ApplicationsState(Application app, IPackageManager iPackageManager) {
        int[] profileIdsWithDisabled;
        this.mContext = app;
        this.mPm = this.mContext.getPackageManager();
        this.mDrawableFactory = IconDrawableFactory.newInstance(this.mContext);
        this.mIpm = iPackageManager;
        this.mUm = (UserManager) this.mContext.getSystemService(UserManager.class);
        this.mStats = (StorageStatsManager) this.mContext.getSystemService(StorageStatsManager.class);
        for (int userId : this.mUm.getProfileIdsWithDisabled(UserHandle.myUserId())) {
            this.mEntriesMap.put(userId, new HashMap<>());
        }
        this.mThread = new HandlerThread("ApplicationsState.Loader", 10);
        this.mThread.start();
        this.mBackgroundHandler = new BackgroundHandler(this.mThread.getLooper());
        this.mAdminRetrieveFlags = 4227584;
        this.mRetrieveFlags = 33280;
        List<ModuleInfo> moduleInfos = this.mPm.getInstalledModules(0);
        for (ModuleInfo info : moduleInfos) {
            this.mSystemModules.put(info.getPackageName(), Boolean.valueOf(info.isHidden()));
        }
        synchronized (this.mEntriesMap) {
            try {
                this.mEntriesMap.wait(1L);
            } catch (InterruptedException e) {
            }
        }
    }

    public Looper getBackgroundLooper() {
        return this.mThread.getLooper();
    }

    public Session newSession(Callbacks callbacks) {
        return newSession(callbacks, null);
    }

    public Session newSession(Callbacks callbacks, Lifecycle lifecycle) {
        Session s = new Session(callbacks, lifecycle);
        synchronized (this.mEntriesMap) {
            this.mSessions.add(s);
        }
        return s;
    }

    void doResumeIfNeededLocked() {
        if (this.mResumed) {
            return;
        }
        this.mResumed = true;
        if (this.mPackageIntentReceiver == null) {
            this.mPackageIntentReceiver = new PackageIntentReceiver();
            this.mPackageIntentReceiver.registerReceiver();
        }
        List<ApplicationInfo> prevApplications = this.mApplications;
        this.mApplications = new ArrayList();
        for (UserInfo user : this.mUm.getProfiles(UserHandle.myUserId())) {
            try {
                if (this.mEntriesMap.indexOfKey(user.id) < 0) {
                    this.mEntriesMap.put(user.id, new HashMap<>());
                }
                ParceledListSlice<ApplicationInfo> list = this.mIpm.getInstalledApplications(user.isAdmin() ? this.mAdminRetrieveFlags : this.mRetrieveFlags, user.id);
                this.mApplications.addAll(list.getList());
            } catch (Exception e) {
                Log.e(TAG, "Error during doResumeIfNeededLocked", e);
            }
        }
        if (this.mInterestingConfigChanges.applyNewConfig(this.mContext.getResources())) {
            clearEntries();
        } else {
            for (int i = 0; i < this.mAppEntries.size(); i++) {
                this.mAppEntries.get(i).sizeStale = true;
            }
        }
        this.mHaveDisabledApps = false;
        this.mHaveInstantApps = false;
        int i2 = 0;
        while (i2 < this.mApplications.size()) {
            ApplicationInfo info = this.mApplications.get(i2);
            if (!info.enabled) {
                if (info.enabledSetting != 3) {
                    this.mApplications.remove(i2);
                    i2--;
                    i2++;
                } else {
                    this.mHaveDisabledApps = true;
                }
            }
            if (isHiddenModule(info.packageName)) {
                this.mApplications.remove(i2);
                i2--;
            } else {
                if (!this.mHaveInstantApps && AppUtils.isInstant(info)) {
                    this.mHaveInstantApps = true;
                }
                int userId = UserHandle.getUserId(info.uid);
                AppEntry entry = this.mEntriesMap.get(userId).get(info.packageName);
                if (entry != null) {
                    entry.info = info;
                }
            }
            i2++;
        }
        if (anyAppIsRemoved(prevApplications, this.mApplications)) {
            clearEntries();
        }
        this.mCurComputingSizePkg = null;
        if (!this.mBackgroundHandler.hasMessages(2)) {
            this.mBackgroundHandler.sendEmptyMessage(2);
        }
    }

    private static boolean anyAppIsRemoved(List<ApplicationInfo> prevApplications, List<ApplicationInfo> applications) {
        HashSet<String> packagesSet;
        if (prevApplications.size() == 0) {
            return false;
        }
        if (applications.size() < prevApplications.size()) {
            return true;
        }
        HashMap<String, HashSet<String>> packageMap = new HashMap<>();
        for (ApplicationInfo application : applications) {
            String userId = String.valueOf(UserHandle.getUserId(application.uid));
            HashSet<String> appPackages = packageMap.get(userId);
            if (appPackages == null) {
                appPackages = new HashSet<>();
                packageMap.put(userId, appPackages);
            }
            if (hasFlag(application.flags, 8388608)) {
                appPackages.add(application.packageName);
            }
        }
        for (ApplicationInfo prevApplication : prevApplications) {
            if (hasFlag(prevApplication.flags, 8388608) && ((packagesSet = packageMap.get(String.valueOf(UserHandle.getUserId(prevApplication.uid)))) == null || !packagesSet.remove(prevApplication.packageName))) {
                return true;
            }
        }
        return false;
    }

    @VisibleForTesting
    void clearEntries() {
        for (int i = 0; i < this.mEntriesMap.size(); i++) {
            this.mEntriesMap.valueAt(i).clear();
        }
        this.mAppEntries.clear();
    }

    public boolean haveDisabledApps() {
        return this.mHaveDisabledApps;
    }

    public boolean haveInstantApps() {
        return this.mHaveInstantApps;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean isHiddenModule(String packageName) {
        Boolean isHidden = this.mSystemModules.get(packageName);
        if (isHidden == null) {
            return false;
        }
        return isHidden.booleanValue();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean isSystemModule(String packageName) {
        return this.mSystemModules.containsKey(packageName);
    }

    void doPauseIfNeededLocked() {
        if (!this.mResumed) {
            return;
        }
        for (int i = 0; i < this.mSessions.size(); i++) {
            if (this.mSessions.get(i).mResumed) {
                return;
            }
        }
        doPauseLocked();
    }

    void doPauseLocked() {
        this.mResumed = false;
        PackageIntentReceiver packageIntentReceiver = this.mPackageIntentReceiver;
        if (packageIntentReceiver != null) {
            packageIntentReceiver.unregisterReceiver();
            this.mPackageIntentReceiver = null;
        }
    }

    public AppEntry getEntry(String packageName, int userId) {
        AppEntry entry;
        synchronized (this.mEntriesMap) {
            entry = this.mEntriesMap.get(userId).get(packageName);
            if (entry == null) {
                ApplicationInfo info = getAppInfoLocked(packageName, userId);
                if (info == null) {
                    try {
                        info = this.mIpm.getApplicationInfo(packageName, 0, userId);
                    } catch (RemoteException e) {
                        Log.w(TAG, "getEntry couldn't reach PackageManager", e);
                        return null;
                    }
                }
                if (info != null) {
                    entry = getEntryLocked(info);
                }
            }
        }
        return entry;
    }

    private ApplicationInfo getAppInfoLocked(String pkg, int userId) {
        for (int i = 0; i < this.mApplications.size(); i++) {
            ApplicationInfo info = this.mApplications.get(i);
            if (pkg.equals(info.packageName) && userId == UserHandle.getUserId(info.uid)) {
                return info;
            }
        }
        return null;
    }

    public void ensureIcon(AppEntry entry) {
        if (entry.icon != null) {
            return;
        }
        synchronized (entry) {
            entry.ensureIconLocked(this.mContext, this.mDrawableFactory);
        }
    }

    public void requestSize(final String packageName, final int userId) {
        synchronized (this.mEntriesMap) {
            final AppEntry entry = this.mEntriesMap.get(userId).get(packageName);
            if (entry != null && hasFlag(entry.info.flags, 8388608)) {
                this.mBackgroundHandler.post(new Runnable() { // from class: com.android.settingslib.applications.-$$Lambda$ApplicationsState$LuXUFbWTiS5lu-nO9WUp0g2nHmU
                    @Override // java.lang.Runnable
                    public final void run() {
                        ApplicationsState.this.lambda$requestSize$0$ApplicationsState(entry, packageName, userId);
                    }
                });
            }
        }
    }

    public /* synthetic */ void lambda$requestSize$0$ApplicationsState(AppEntry entry, String packageName, int userId) {
        try {
            StorageStats stats = this.mStats.queryStatsForPackage(entry.info.storageUuid, packageName, UserHandle.of(userId));
            long cacheQuota = this.mStats.getCacheQuotaBytes(entry.info.storageUuid.toString(), entry.info.uid);
            PackageStats legacy = new PackageStats(packageName, userId);
            legacy.codeSize = stats.getCodeBytes();
            legacy.dataSize = stats.getDataBytes();
            legacy.cacheSize = Math.min(stats.getCacheBytes(), cacheQuota);
            try {
                this.mBackgroundHandler.mStatsObserver.onGetStatsCompleted(legacy, true);
            } catch (RemoteException e) {
            }
        } catch (PackageManager.NameNotFoundException | IOException e2) {
            Log.w(TAG, "Failed to query stats: " + e2);
            try {
                this.mBackgroundHandler.mStatsObserver.onGetStatsCompleted((PackageStats) null, false);
            } catch (RemoteException e3) {
            }
        }
    }

    long sumCacheSizes() {
        long sum = 0;
        synchronized (this.mEntriesMap) {
            for (int i = this.mAppEntries.size() - 1; i >= 0; i--) {
                sum += this.mAppEntries.get(i).cacheSize;
            }
        }
        return sum;
    }

    int indexOfApplicationInfoLocked(String pkgName, int userId) {
        for (int i = this.mApplications.size() - 1; i >= 0; i--) {
            ApplicationInfo appInfo = this.mApplications.get(i);
            if (appInfo.packageName.equals(pkgName) && UserHandle.getUserId(appInfo.uid) == userId) {
                return i;
            }
        }
        return -1;
    }

    void addPackage(String pkgName, int userId) {
        try {
            synchronized (this.mEntriesMap) {
                if (this.mResumed) {
                    if (indexOfApplicationInfoLocked(pkgName, userId) >= 0) {
                        return;
                    }
                    ApplicationInfo info = this.mIpm.getApplicationInfo(pkgName, this.mUm.isUserAdmin(userId) ? this.mAdminRetrieveFlags : this.mRetrieveFlags, userId);
                    if (info == null) {
                        return;
                    }
                    if (!info.enabled) {
                        if (info.enabledSetting != 3) {
                            return;
                        }
                        this.mHaveDisabledApps = true;
                    }
                    if (AppUtils.isInstant(info)) {
                        this.mHaveInstantApps = true;
                    }
                    this.mApplications.add(info);
                    if (!this.mBackgroundHandler.hasMessages(2)) {
                        this.mBackgroundHandler.sendEmptyMessage(2);
                    }
                    if (!this.mMainHandler.hasMessages(2)) {
                        this.mMainHandler.sendEmptyMessage(2);
                    }
                }
            }
        } catch (RemoteException e) {
        }
    }

    public void removePackage(String pkgName, int userId) {
        synchronized (this.mEntriesMap) {
            int idx = indexOfApplicationInfoLocked(pkgName, userId);
            if (idx >= 0) {
                AppEntry entry = this.mEntriesMap.get(userId).get(pkgName);
                if (entry != null) {
                    this.mEntriesMap.get(userId).remove(pkgName);
                    this.mAppEntries.remove(entry);
                }
                ApplicationInfo info = this.mApplications.get(idx);
                this.mApplications.remove(idx);
                if (!info.enabled) {
                    this.mHaveDisabledApps = false;
                    Iterator<ApplicationInfo> it = this.mApplications.iterator();
                    while (true) {
                        if (!it.hasNext()) {
                            break;
                        }
                        ApplicationInfo otherInfo = it.next();
                        if (!otherInfo.enabled) {
                            this.mHaveDisabledApps = true;
                            break;
                        }
                    }
                }
                if (AppUtils.isInstant(info)) {
                    this.mHaveInstantApps = false;
                    Iterator<ApplicationInfo> it2 = this.mApplications.iterator();
                    while (true) {
                        if (!it2.hasNext()) {
                            break;
                        }
                        ApplicationInfo otherInfo2 = it2.next();
                        if (AppUtils.isInstant(otherInfo2)) {
                            this.mHaveInstantApps = true;
                            break;
                        }
                    }
                }
                if (!this.mMainHandler.hasMessages(2)) {
                    this.mMainHandler.sendEmptyMessage(2);
                }
            }
        }
    }

    public void invalidatePackage(String pkgName, int userId) {
        removePackage(pkgName, userId);
        addPackage(pkgName, userId);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void addUser(int userId) {
        int[] profileIds = this.mUm.getProfileIdsWithDisabled(UserHandle.myUserId());
        if (ArrayUtils.contains(profileIds, userId)) {
            synchronized (this.mEntriesMap) {
                this.mEntriesMap.put(userId, new HashMap<>());
                if (this.mResumed) {
                    doPauseLocked();
                    doResumeIfNeededLocked();
                }
                if (!this.mMainHandler.hasMessages(2)) {
                    this.mMainHandler.sendEmptyMessage(2);
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void removeUser(int userId) {
        synchronized (this.mEntriesMap) {
            HashMap<String, AppEntry> userMap = this.mEntriesMap.get(userId);
            if (userMap != null) {
                for (AppEntry appEntry : userMap.values()) {
                    this.mAppEntries.remove(appEntry);
                    this.mApplications.remove(appEntry.info);
                }
                this.mEntriesMap.remove(userId);
                if (!this.mMainHandler.hasMessages(2)) {
                    this.mMainHandler.sendEmptyMessage(2);
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public AppEntry getEntryLocked(ApplicationInfo info) {
        int userId = UserHandle.getUserId(info.uid);
        AppEntry entry = this.mEntriesMap.get(userId).get(info.packageName);
        if (entry == null) {
            if (isHiddenModule(info.packageName)) {
                return null;
            }
            Context context = this.mContext;
            long j = this.mCurId;
            this.mCurId = 1 + j;
            AppEntry entry2 = new AppEntry(context, info, j);
            this.mEntriesMap.get(userId).put(info.packageName, entry2);
            this.mAppEntries.add(entry2);
            return entry2;
        } else if (entry.info != info) {
            entry.info = info;
            return entry;
        } else {
            return entry;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public long getTotalInternalSize(PackageStats ps) {
        if (ps != null) {
            return (ps.codeSize + ps.dataSize) - ps.cacheSize;
        }
        return -2L;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public long getTotalExternalSize(PackageStats ps) {
        if (ps != null) {
            return ps.externalCodeSize + ps.externalDataSize + ps.externalCacheSize + ps.externalMediaSize + ps.externalObbSize;
        }
        return -2L;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public String getSizeStr(long size) {
        if (size >= 0) {
            return Formatter.formatFileSize(this.mContext, size);
        }
        return null;
    }

    void rebuildActiveSessions() {
        synchronized (this.mEntriesMap) {
            if (this.mSessionsChanged) {
                this.mActiveSessions.clear();
                for (int i = 0; i < this.mSessions.size(); i++) {
                    Session s = this.mSessions.get(i);
                    if (s.mResumed) {
                        this.mActiveSessions.add(new WeakReference<>(s));
                    }
                }
            }
        }
    }

    public static String normalize(String str) {
        String tmp = Normalizer.normalize(str, Normalizer.Form.NFD);
        return REMOVE_DIACRITICALS_PATTERN.matcher(tmp).replaceAll("").toLowerCase();
    }

    /* loaded from: classes3.dex */
    public class Session implements LifecycleObserver {
        final Callbacks mCallbacks;
        private final boolean mHasLifecycle;
        ArrayList<AppEntry> mLastAppList;
        boolean mRebuildAsync;
        Comparator<AppEntry> mRebuildComparator;
        AppFilter mRebuildFilter;
        boolean mRebuildForeground;
        boolean mRebuildRequested;
        ArrayList<AppEntry> mRebuildResult;
        boolean mResumed;
        final Object mRebuildSync = new Object();
        private int mFlags = 15;

        Session(Callbacks callbacks, Lifecycle lifecycle) {
            this.mCallbacks = callbacks;
            if (lifecycle != null) {
                lifecycle.addObserver(this);
                this.mHasLifecycle = true;
                return;
            }
            this.mHasLifecycle = false;
        }

        public int getSessionFlags() {
            return this.mFlags;
        }

        public void setSessionFlags(int flags) {
            this.mFlags = flags;
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
        public void onResume() {
            synchronized (ApplicationsState.this.mEntriesMap) {
                if (!this.mResumed) {
                    this.mResumed = true;
                    ApplicationsState.this.mSessionsChanged = true;
                    ApplicationsState.this.doPauseLocked();
                    ApplicationsState.this.doResumeIfNeededLocked();
                }
            }
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        public void onPause() {
            synchronized (ApplicationsState.this.mEntriesMap) {
                if (this.mResumed) {
                    this.mResumed = false;
                    ApplicationsState.this.mSessionsChanged = true;
                    ApplicationsState.this.mBackgroundHandler.removeMessages(1, this);
                    ApplicationsState.this.doPauseIfNeededLocked();
                }
            }
        }

        public ArrayList<AppEntry> getAllApps() {
            ArrayList<AppEntry> arrayList;
            synchronized (ApplicationsState.this.mEntriesMap) {
                arrayList = new ArrayList<>(ApplicationsState.this.mAppEntries);
            }
            return arrayList;
        }

        public ArrayList<AppEntry> rebuild(AppFilter filter, Comparator<AppEntry> comparator) {
            return rebuild(filter, comparator, true);
        }

        public ArrayList<AppEntry> rebuild(AppFilter filter, Comparator<AppEntry> comparator, boolean foreground) {
            synchronized (this.mRebuildSync) {
                synchronized (ApplicationsState.this.mRebuildingSessions) {
                    ApplicationsState.this.mRebuildingSessions.add(this);
                    this.mRebuildRequested = true;
                    this.mRebuildAsync = true;
                    this.mRebuildFilter = filter;
                    this.mRebuildComparator = comparator;
                    this.mRebuildForeground = foreground;
                    this.mRebuildResult = null;
                    if (!ApplicationsState.this.mBackgroundHandler.hasMessages(1)) {
                        Message msg = ApplicationsState.this.mBackgroundHandler.obtainMessage(1);
                        ApplicationsState.this.mBackgroundHandler.sendMessage(msg);
                    }
                }
            }
            return null;
        }

        void handleRebuildList() {
            List<AppEntry> apps;
            synchronized (this.mRebuildSync) {
                if (this.mRebuildRequested) {
                    AppFilter filter = this.mRebuildFilter;
                    Comparator<AppEntry> comparator = this.mRebuildComparator;
                    this.mRebuildRequested = false;
                    this.mRebuildFilter = null;
                    this.mRebuildComparator = null;
                    if (this.mRebuildForeground) {
                        Process.setThreadPriority(-2);
                        this.mRebuildForeground = false;
                    }
                    if (filter != null) {
                        filter.init(ApplicationsState.this.mContext);
                    }
                    synchronized (ApplicationsState.this.mEntriesMap) {
                        apps = new ArrayList<>(ApplicationsState.this.mAppEntries);
                    }
                    ArrayList<AppEntry> filteredApps = new ArrayList<>();
                    for (AppEntry entry : apps) {
                        if (entry != null && (filter == null || filter.filterApp(entry))) {
                            synchronized (ApplicationsState.this.mEntriesMap) {
                                if (comparator != null) {
                                    entry.ensureLabel(ApplicationsState.this.mContext);
                                }
                                filteredApps.add(entry);
                            }
                        }
                    }
                    if (comparator != null) {
                        synchronized (ApplicationsState.this.mEntriesMap) {
                            Collections.sort(filteredApps, comparator);
                        }
                    }
                    synchronized (this.mRebuildSync) {
                        if (!this.mRebuildRequested) {
                            this.mLastAppList = filteredApps;
                            if (!this.mRebuildAsync) {
                                this.mRebuildResult = filteredApps;
                                this.mRebuildSync.notifyAll();
                            } else if (!ApplicationsState.this.mMainHandler.hasMessages(1, this)) {
                                Message msg = ApplicationsState.this.mMainHandler.obtainMessage(1, this);
                                ApplicationsState.this.mMainHandler.sendMessage(msg);
                            }
                        }
                    }
                    Process.setThreadPriority(10);
                }
            }
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        public void onDestroy() {
            if (!this.mHasLifecycle) {
                onPause();
            }
            synchronized (ApplicationsState.this.mEntriesMap) {
                ApplicationsState.this.mSessions.remove(this);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes3.dex */
    public class MainHandler extends Handler {
        static final int MSG_ALL_SIZES_COMPUTED = 5;
        static final int MSG_LAUNCHER_INFO_CHANGED = 7;
        static final int MSG_LOAD_ENTRIES_COMPLETE = 8;
        static final int MSG_PACKAGE_ICON_CHANGED = 3;
        static final int MSG_PACKAGE_LIST_CHANGED = 2;
        static final int MSG_PACKAGE_SIZE_CHANGED = 4;
        static final int MSG_REBUILD_COMPLETE = 1;
        static final int MSG_RUNNING_STATE_CHANGED = 6;

        public MainHandler(Looper looper) {
            super(looper);
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            ApplicationsState.this.rebuildActiveSessions();
            switch (msg.what) {
                case 1:
                    Session s = (Session) msg.obj;
                    Iterator<WeakReference<Session>> it = ApplicationsState.this.mActiveSessions.iterator();
                    while (it.hasNext()) {
                        WeakReference<Session> sessionRef = it.next();
                        Session session = sessionRef.get();
                        if (session != null && session == s) {
                            s.mCallbacks.onRebuildComplete(s.mLastAppList);
                        }
                    }
                    return;
                case 2:
                    Iterator<WeakReference<Session>> it2 = ApplicationsState.this.mActiveSessions.iterator();
                    while (it2.hasNext()) {
                        WeakReference<Session> sessionRef2 = it2.next();
                        Session session2 = sessionRef2.get();
                        if (session2 != null) {
                            session2.mCallbacks.onPackageListChanged();
                        }
                    }
                    return;
                case 3:
                    Iterator<WeakReference<Session>> it3 = ApplicationsState.this.mActiveSessions.iterator();
                    while (it3.hasNext()) {
                        WeakReference<Session> sessionRef3 = it3.next();
                        Session session3 = sessionRef3.get();
                        if (session3 != null) {
                            session3.mCallbacks.onPackageIconChanged();
                        }
                    }
                    return;
                case 4:
                    Iterator<WeakReference<Session>> it4 = ApplicationsState.this.mActiveSessions.iterator();
                    while (it4.hasNext()) {
                        WeakReference<Session> sessionRef4 = it4.next();
                        Session session4 = sessionRef4.get();
                        if (session4 != null) {
                            session4.mCallbacks.onPackageSizeChanged((String) msg.obj);
                        }
                    }
                    return;
                case 5:
                    Iterator<WeakReference<Session>> it5 = ApplicationsState.this.mActiveSessions.iterator();
                    while (it5.hasNext()) {
                        WeakReference<Session> sessionRef5 = it5.next();
                        Session session5 = sessionRef5.get();
                        if (session5 != null) {
                            session5.mCallbacks.onAllSizesComputed();
                        }
                    }
                    return;
                case 6:
                    Iterator<WeakReference<Session>> it6 = ApplicationsState.this.mActiveSessions.iterator();
                    while (it6.hasNext()) {
                        WeakReference<Session> sessionRef6 = it6.next();
                        Session session6 = sessionRef6.get();
                        if (session6 != null) {
                            session6.mCallbacks.onRunningStateChanged(msg.arg1 != 0);
                        }
                    }
                    return;
                case 7:
                    Iterator<WeakReference<Session>> it7 = ApplicationsState.this.mActiveSessions.iterator();
                    while (it7.hasNext()) {
                        WeakReference<Session> sessionRef7 = it7.next();
                        Session session7 = sessionRef7.get();
                        if (session7 != null) {
                            session7.mCallbacks.onLauncherInfoChanged();
                        }
                    }
                    return;
                case 8:
                    Iterator<WeakReference<Session>> it8 = ApplicationsState.this.mActiveSessions.iterator();
                    while (it8.hasNext()) {
                        WeakReference<Session> sessionRef8 = it8.next();
                        Session session8 = sessionRef8.get();
                        if (session8 != null) {
                            session8.mCallbacks.onLoadEntriesCompleted();
                        }
                    }
                    return;
                default:
                    return;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class BackgroundHandler extends Handler {
        static final int MSG_LOAD_ENTRIES = 2;
        static final int MSG_LOAD_HOME_APP = 3;
        static final int MSG_LOAD_ICONS = 6;
        static final int MSG_LOAD_LAUNCHER = 4;
        static final int MSG_LOAD_LEANBACK_LAUNCHER = 5;
        static final int MSG_LOAD_SIZES = 7;
        static final int MSG_REBUILD_LIST = 1;
        boolean mRunning;
        final IPackageStatsObserver.Stub mStatsObserver;

        BackgroundHandler(Looper looper) {
            super(looper);
            this.mStatsObserver = new IPackageStatsObserver.Stub() { // from class: com.android.settingslib.applications.ApplicationsState.BackgroundHandler.1
                /* JADX WARN: Removed duplicated region for block: B:41:0x00dd A[Catch: all -> 0x0137, TRY_ENTER, TRY_LEAVE, TryCatch #3 {all -> 0x0137, blocks: (B:41:0x00dd, B:52:0x0102, B:54:0x010a, B:56:0x0118, B:58:0x0122, B:59:0x012f, B:63:0x0135, B:48:0x00fc), top: B:73:0x000f }] */
                /*
                    Code decompiled incorrectly, please refer to instructions dump.
                    To view partially-correct add '--show-bad-code' argument
                */
                public void onGetStatsCompleted(android.content.pm.PackageStats r18, boolean r19) {
                    /*
                        Method dump skipped, instructions count: 313
                        To view this dump add '--comments-level debug' option
                    */
                    throw new UnsupportedOperationException("Method not decompiled: com.android.settingslib.applications.ApplicationsState.BackgroundHandler.AnonymousClass1.onGetStatsCompleted(android.content.pm.PackageStats, boolean):void");
                }
            };
        }

        /* JADX WARN: Multi-variable type inference failed */
        /* JADX WARN: Type inference failed for: r11v0 */
        /* JADX WARN: Type inference failed for: r11v13 */
        /* JADX WARN: Type inference failed for: r11v14 */
        /* JADX WARN: Unsupported multi-entry loop pattern (BACK_EDGE: B:213:? -> B:131:0x0288). Please submit an issue!!! */
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            int numDone;
            ArrayList<Session> rebuildingSessions;
            HashMap<String, AppEntry> userEntries;
            ArrayList<Session> rebuildingSessions2;
            int numDone2;
            SparseArray<HashMap<String, AppEntry>> sparseArray;
            ArrayList<Session> rebuildingSessions3 = null;
            synchronized (ApplicationsState.this.mRebuildingSessions) {
                try {
                    if (ApplicationsState.this.mRebuildingSessions.size() > 0) {
                        rebuildingSessions3 = new ArrayList<>(ApplicationsState.this.mRebuildingSessions);
                        ApplicationsState.this.mRebuildingSessions.clear();
                    }
                } catch (Throwable th) {
                    th = th;
                }
                try {
                    if (rebuildingSessions3 != null) {
                        for (int i = 0; i < rebuildingSessions3.size(); i++) {
                            rebuildingSessions3.get(i).handleRebuildList();
                        }
                    }
                    int flags = getCombinedSessionFlags(ApplicationsState.this.mSessions);
                    int i2 = 8388608;
                    ?? r11 = 4;
                    boolean z = true;
                    switch (msg.what) {
                        case 1:
                            return;
                        case 2:
                            synchronized (ApplicationsState.this.mEntriesMap) {
                                numDone = 0;
                                for (int i3 = 0; i3 < ApplicationsState.this.mApplications.size() && numDone < 6; i3++) {
                                    if (!this.mRunning) {
                                        this.mRunning = true;
                                        Message m = ApplicationsState.this.mMainHandler.obtainMessage(6, 1);
                                        ApplicationsState.this.mMainHandler.sendMessage(m);
                                    }
                                    ApplicationInfo info = ApplicationsState.this.mApplications.get(i3);
                                    int userId = UserHandle.getUserId(info.uid);
                                    if (ApplicationsState.this.mEntriesMap.get(userId).get(info.packageName) == null) {
                                        numDone++;
                                        ApplicationsState.this.getEntryLocked(info);
                                    }
                                    if (userId != 0 && ApplicationsState.this.mEntriesMap.indexOfKey(0) >= 0) {
                                        AppEntry entry = ApplicationsState.this.mEntriesMap.get(0).get(info.packageName);
                                        if (entry != null && !ApplicationsState.hasFlag(entry.info.flags, 8388608)) {
                                            ApplicationsState.this.mEntriesMap.get(0).remove(info.packageName);
                                            ApplicationsState.this.mAppEntries.remove(entry);
                                        }
                                    }
                                }
                            }
                            if (numDone >= 6) {
                                sendEmptyMessage(2);
                                return;
                            }
                            if (!ApplicationsState.this.mMainHandler.hasMessages(8)) {
                                ApplicationsState.this.mMainHandler.sendEmptyMessage(8);
                            }
                            sendEmptyMessage(3);
                            return;
                        case 3:
                            if (ApplicationsState.hasFlag(flags, 1)) {
                                List<ResolveInfo> homeActivities = new ArrayList<>();
                                ApplicationsState.this.mPm.getHomeActivities(homeActivities);
                                synchronized (ApplicationsState.this.mEntriesMap) {
                                    int entryCount = ApplicationsState.this.mEntriesMap.size();
                                    for (int i4 = 0; i4 < entryCount; i4++) {
                                        HashMap<String, AppEntry> userEntries2 = ApplicationsState.this.mEntriesMap.valueAt(i4);
                                        for (ResolveInfo activity : homeActivities) {
                                            AppEntry entry2 = userEntries2.get(activity.activityInfo.packageName);
                                            if (entry2 != null) {
                                                entry2.isHomeApp = true;
                                            }
                                        }
                                    }
                                }
                            }
                            sendEmptyMessage(4);
                            return;
                        case 4:
                        case 5:
                            if ((msg.what == 4 && ApplicationsState.hasFlag(flags, 8)) || (msg.what == 5 && ApplicationsState.hasFlag(flags, 16))) {
                                Intent launchIntent = new Intent("android.intent.action.MAIN", (Uri) null);
                                launchIntent.addCategory(msg.what == 4 ? "android.intent.category.LAUNCHER" : IntentCompat.CATEGORY_LEANBACK_LAUNCHER);
                                int i5 = 0;
                                while (i5 < ApplicationsState.this.mEntriesMap.size()) {
                                    int userId2 = ApplicationsState.this.mEntriesMap.keyAt(i5);
                                    List<ResolveInfo> intents = ApplicationsState.this.mPm.queryIntentActivitiesAsUser(launchIntent, 786944, userId2);
                                    synchronized (ApplicationsState.this.mEntriesMap) {
                                        try {
                                            HashMap<String, AppEntry> userEntries3 = ApplicationsState.this.mEntriesMap.valueAt(i5);
                                            int N = intents.size();
                                            int j = 0;
                                            while (j < N) {
                                                ResolveInfo resolveInfo = intents.get(j);
                                                String packageName = resolveInfo.activityInfo.packageName;
                                                AppEntry entry3 = userEntries3.get(packageName);
                                                if (entry3 != null) {
                                                    try {
                                                        entry3.hasLauncherEntry = z;
                                                        userEntries = userEntries3;
                                                        entry3.launcherEntryEnabled = resolveInfo.activityInfo.enabled | entry3.launcherEntryEnabled;
                                                        rebuildingSessions2 = rebuildingSessions3;
                                                    } catch (Throwable th2) {
                                                        th = th2;
                                                        throw th;
                                                    }
                                                } else {
                                                    userEntries = userEntries3;
                                                    StringBuilder sb = new StringBuilder();
                                                    rebuildingSessions2 = rebuildingSessions3;
                                                    try {
                                                        sb.append("Cannot find pkg: ");
                                                        sb.append(packageName);
                                                        sb.append(" on user ");
                                                        sb.append(userId2);
                                                        Log.w(ApplicationsState.TAG, sb.toString());
                                                    } catch (Throwable th3) {
                                                        th = th3;
                                                        throw th;
                                                    }
                                                }
                                                j++;
                                                userEntries3 = userEntries;
                                                rebuildingSessions3 = rebuildingSessions2;
                                                z = true;
                                            }
                                            rebuildingSessions = rebuildingSessions3;
                                        } catch (Throwable th4) {
                                            th = th4;
                                        }
                                    }
                                    i5++;
                                    rebuildingSessions3 = rebuildingSessions;
                                    z = true;
                                }
                                if (!ApplicationsState.this.mMainHandler.hasMessages(7)) {
                                    ApplicationsState.this.mMainHandler.sendEmptyMessage(7);
                                }
                            }
                            if (msg.what == 4) {
                                sendEmptyMessage(5);
                                return;
                            } else {
                                sendEmptyMessage(6);
                                return;
                            }
                        case 6:
                            if (ApplicationsState.hasFlag(flags, 2)) {
                                synchronized (ApplicationsState.this.mEntriesMap) {
                                    numDone2 = 0;
                                    for (int i6 = 0; i6 < ApplicationsState.this.mAppEntries.size() && numDone2 < 2; i6++) {
                                        AppEntry entry4 = ApplicationsState.this.mAppEntries.get(i6);
                                        if (entry4.icon == null || !entry4.mounted) {
                                            synchronized (entry4) {
                                                if (entry4.ensureIconLocked(ApplicationsState.this.mContext, ApplicationsState.this.mDrawableFactory)) {
                                                    if (!this.mRunning) {
                                                        this.mRunning = true;
                                                        Message m2 = ApplicationsState.this.mMainHandler.obtainMessage(6, 1);
                                                        ApplicationsState.this.mMainHandler.sendMessage(m2);
                                                    }
                                                    numDone2++;
                                                }
                                            }
                                        }
                                    }
                                }
                                if (numDone2 > 0 && !ApplicationsState.this.mMainHandler.hasMessages(3)) {
                                    ApplicationsState.this.mMainHandler.sendEmptyMessage(3);
                                }
                                if (numDone2 >= 2) {
                                    sendEmptyMessage(6);
                                    return;
                                }
                            }
                            sendEmptyMessage(7);
                            return;
                        case 7:
                            if (ApplicationsState.hasFlag(flags, 4)) {
                                SparseArray<HashMap<String, AppEntry>> sparseArray2 = ApplicationsState.this.mEntriesMap;
                                synchronized (sparseArray2) {
                                    try {
                                        try {
                                            if (ApplicationsState.this.mCurComputingSizePkg == null) {
                                                long now = SystemClock.uptimeMillis();
                                                int i7 = 0;
                                                while (i7 < ApplicationsState.this.mAppEntries.size()) {
                                                    AppEntry entry5 = ApplicationsState.this.mAppEntries.get(i7);
                                                    if (ApplicationsState.hasFlag(entry5.info.flags, i2)) {
                                                        sparseArray = sparseArray2;
                                                        if (entry5.size == -1 || entry5.sizeStale) {
                                                            if (entry5.sizeLoadStart == 0 || entry5.sizeLoadStart < now - 20000) {
                                                                if (!this.mRunning) {
                                                                    this.mRunning = true;
                                                                    Message m3 = ApplicationsState.this.mMainHandler.obtainMessage(6, 1);
                                                                    ApplicationsState.this.mMainHandler.sendMessage(m3);
                                                                }
                                                                entry5.sizeLoadStart = now;
                                                                ApplicationsState.this.mCurComputingSizeUuid = entry5.info.storageUuid;
                                                                ApplicationsState.this.mCurComputingSizePkg = entry5.info.packageName;
                                                                ApplicationsState.this.mCurComputingSizeUserId = UserHandle.getUserId(entry5.info.uid);
                                                                ApplicationsState.this.mBackgroundHandler.post(new Runnable() { // from class: com.android.settingslib.applications.-$$Lambda$ApplicationsState$BackgroundHandler$7jhXQzAcRoT6ACDzmPBTQMi7Ldc
                                                                    @Override // java.lang.Runnable
                                                                    public final void run() {
                                                                        ApplicationsState.BackgroundHandler.this.lambda$handleMessage$0$ApplicationsState$BackgroundHandler();
                                                                    }
                                                                });
                                                            }
                                                            return;
                                                        }
                                                    } else {
                                                        sparseArray = sparseArray2;
                                                    }
                                                    i7++;
                                                    sparseArray2 = sparseArray;
                                                    i2 = 8388608;
                                                }
                                                SparseArray<HashMap<String, AppEntry>> sparseArray3 = sparseArray2;
                                                if (!ApplicationsState.this.mMainHandler.hasMessages(5)) {
                                                    ApplicationsState.this.mMainHandler.sendEmptyMessage(5);
                                                    this.mRunning = false;
                                                    Message m4 = ApplicationsState.this.mMainHandler.obtainMessage(6, 0);
                                                    ApplicationsState.this.mMainHandler.sendMessage(m4);
                                                }
                                                return;
                                            }
                                        } catch (Throwable th5) {
                                            th = th5;
                                            r11 = sparseArray2;
                                            throw th;
                                        }
                                    } catch (Throwable th6) {
                                        th = th6;
                                    }
                                }
                                return;
                            }
                            return;
                        default:
                            return;
                    }
                } catch (Throwable th7) {
                    th = th7;
                    throw th;
                }
            }
        }

        public /* synthetic */ void lambda$handleMessage$0$ApplicationsState$BackgroundHandler() {
            try {
                StorageStats stats = ApplicationsState.this.mStats.queryStatsForPackage(ApplicationsState.this.mCurComputingSizeUuid, ApplicationsState.this.mCurComputingSizePkg, UserHandle.of(ApplicationsState.this.mCurComputingSizeUserId));
                PackageStats legacy = new PackageStats(ApplicationsState.this.mCurComputingSizePkg, ApplicationsState.this.mCurComputingSizeUserId);
                legacy.codeSize = stats.getCodeBytes();
                legacy.dataSize = stats.getDataBytes();
                legacy.cacheSize = stats.getCacheBytes();
                try {
                    this.mStatsObserver.onGetStatsCompleted(legacy, true);
                } catch (RemoteException e) {
                }
            } catch (PackageManager.NameNotFoundException | IOException e2) {
                Log.w(ApplicationsState.TAG, "Failed to query stats: " + e2);
                try {
                    this.mStatsObserver.onGetStatsCompleted((PackageStats) null, false);
                } catch (RemoteException e3) {
                }
            }
        }

        private int getCombinedSessionFlags(List<Session> sessions) {
            int flags;
            synchronized (ApplicationsState.this.mEntriesMap) {
                flags = 0;
                for (Session session : sessions) {
                    flags |= session.mFlags;
                }
            }
            return flags;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class PackageIntentReceiver extends BroadcastReceiver {
        private PackageIntentReceiver() {
        }

        void registerReceiver() {
            IntentFilter filter = new IntentFilter("android.intent.action.PACKAGE_ADDED");
            filter.addAction("android.intent.action.PACKAGE_REMOVED");
            filter.addAction("android.intent.action.PACKAGE_CHANGED");
            filter.addDataScheme("package");
            ApplicationsState.this.mContext.registerReceiver(this, filter);
            IntentFilter sdFilter = new IntentFilter();
            sdFilter.addAction("android.intent.action.EXTERNAL_APPLICATIONS_AVAILABLE");
            sdFilter.addAction("android.intent.action.EXTERNAL_APPLICATIONS_UNAVAILABLE");
            ApplicationsState.this.mContext.registerReceiver(this, sdFilter);
            IntentFilter userFilter = new IntentFilter();
            userFilter.addAction("android.intent.action.USER_ADDED");
            userFilter.addAction("android.intent.action.USER_REMOVED");
            ApplicationsState.this.mContext.registerReceiver(this, userFilter);
        }

        void unregisterReceiver() {
            ApplicationsState.this.mContext.unregisterReceiver(this);
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String actionStr = intent.getAction();
            if ("android.intent.action.PACKAGE_ADDED".equals(actionStr)) {
                Uri data = intent.getData();
                String pkgName = data.getEncodedSchemeSpecificPart();
                for (int i = 0; i < ApplicationsState.this.mEntriesMap.size(); i++) {
                    ApplicationsState applicationsState = ApplicationsState.this;
                    applicationsState.addPackage(pkgName, applicationsState.mEntriesMap.keyAt(i));
                }
            } else if ("android.intent.action.PACKAGE_REMOVED".equals(actionStr)) {
                Uri data2 = intent.getData();
                String pkgName2 = data2.getEncodedSchemeSpecificPart();
                for (int i2 = 0; i2 < ApplicationsState.this.mEntriesMap.size(); i2++) {
                    ApplicationsState applicationsState2 = ApplicationsState.this;
                    applicationsState2.removePackage(pkgName2, applicationsState2.mEntriesMap.keyAt(i2));
                }
            } else if ("android.intent.action.PACKAGE_CHANGED".equals(actionStr)) {
                Uri data3 = intent.getData();
                String pkgName3 = data3.getEncodedSchemeSpecificPart();
                for (int i3 = 0; i3 < ApplicationsState.this.mEntriesMap.size(); i3++) {
                    ApplicationsState applicationsState3 = ApplicationsState.this;
                    applicationsState3.invalidatePackage(pkgName3, applicationsState3.mEntriesMap.keyAt(i3));
                }
            } else if ("android.intent.action.EXTERNAL_APPLICATIONS_AVAILABLE".equals(actionStr) || "android.intent.action.EXTERNAL_APPLICATIONS_UNAVAILABLE".equals(actionStr)) {
                String[] pkgList = intent.getStringArrayExtra("android.intent.extra.changed_package_list");
                if (pkgList == null || pkgList.length == 0) {
                    return;
                }
                boolean avail = "android.intent.action.EXTERNAL_APPLICATIONS_AVAILABLE".equals(actionStr);
                if (avail) {
                    for (String pkgName4 : pkgList) {
                        for (int i4 = 0; i4 < ApplicationsState.this.mEntriesMap.size(); i4++) {
                            ApplicationsState applicationsState4 = ApplicationsState.this;
                            applicationsState4.invalidatePackage(pkgName4, applicationsState4.mEntriesMap.keyAt(i4));
                        }
                    }
                }
            } else if ("android.intent.action.USER_ADDED".equals(actionStr)) {
                ApplicationsState.this.addUser(intent.getIntExtra("android.intent.extra.user_handle", -10000));
            } else if ("android.intent.action.USER_REMOVED".equals(actionStr)) {
                ApplicationsState.this.removeUser(intent.getIntExtra("android.intent.extra.user_handle", -10000));
            }
        }
    }

    /* loaded from: classes3.dex */
    public static class AppEntry extends SizeInfo {
        public final File apkFile;
        public long externalSize;
        public String externalSizeStr;
        public Object extraInfo;
        public boolean hasLauncherEntry;
        public Drawable icon;
        public final long id;
        public ApplicationInfo info;
        public long internalSize;
        public String internalSizeStr;
        public boolean isHomeApp;
        public String label;
        public boolean launcherEntryEnabled;
        public boolean mounted;
        public String normalizedLabel;
        public long sizeLoadStart;
        public String sizeStr;
        public long size = -1;
        public boolean sizeStale = true;

        public String getNormalizedLabel() {
            String str = this.normalizedLabel;
            if (str != null) {
                return str;
            }
            this.normalizedLabel = ApplicationsState.normalize(this.label);
            return this.normalizedLabel;
        }

        @VisibleForTesting(otherwise = 2)
        public AppEntry(Context context, ApplicationInfo info, long id) {
            this.apkFile = new File(info.sourceDir);
            this.id = id;
            this.info = info;
            ensureLabel(context);
        }

        public void ensureLabel(Context context) {
            if (this.label == null || !this.mounted) {
                if (!this.apkFile.exists()) {
                    this.mounted = false;
                    this.label = this.info.packageName;
                    return;
                }
                this.mounted = true;
                CharSequence label = this.info.loadLabel(context.getPackageManager());
                this.label = label != null ? label.toString() : this.info.packageName;
            }
        }

        boolean ensureIconLocked(Context context, IconDrawableFactory drawableFactory) {
            if (this.icon == null) {
                if (this.apkFile.exists()) {
                    this.icon = drawableFactory.getBadgedIcon(this.info);
                    return true;
                }
                this.mounted = false;
                this.icon = context.getDrawable(17303634);
            } else if (!this.mounted && this.apkFile.exists()) {
                this.mounted = true;
                this.icon = drawableFactory.getBadgedIcon(this.info);
                return true;
            }
            return false;
        }

        public String getVersion(Context context) {
            try {
                return context.getPackageManager().getPackageInfo(this.info.packageName, 0).versionName;
            } catch (PackageManager.NameNotFoundException e) {
                return "";
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean hasFlag(int flags, int flag) {
        return (flags & flag) != 0;
    }

    /* loaded from: classes3.dex */
    public interface AppFilter {
        boolean filterApp(AppEntry appEntry);

        void init();

        default void init(Context context) {
            init();
        }
    }

    /* loaded from: classes3.dex */
    public static class VolumeFilter implements AppFilter {
        private final String mVolumeUuid;

        public VolumeFilter(String volumeUuid) {
            this.mVolumeUuid = volumeUuid;
        }

        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public void init() {
        }

        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public boolean filterApp(AppEntry info) {
            return Objects.equals(info.info.volumeUuid, this.mVolumeUuid);
        }
    }

    /* loaded from: classes3.dex */
    public static class CompoundFilter implements AppFilter {
        private final AppFilter mFirstFilter;
        private final AppFilter mSecondFilter;

        public CompoundFilter(AppFilter first, AppFilter second) {
            this.mFirstFilter = first;
            this.mSecondFilter = second;
        }

        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public void init(Context context) {
            this.mFirstFilter.init(context);
            this.mSecondFilter.init(context);
        }

        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public void init() {
            this.mFirstFilter.init();
            this.mSecondFilter.init();
        }

        @Override // com.android.settingslib.applications.ApplicationsState.AppFilter
        public boolean filterApp(AppEntry info) {
            return this.mFirstFilter.filterApp(info) && this.mSecondFilter.filterApp(info);
        }
    }
}
