package com.android.car.pm;

import android.app.ActivityManager;
import android.car.content.pm.AppBlockingPackageInfo;
import android.car.content.pm.CarAppBlockingPolicy;
import android.car.content.pm.ICarPackageManager;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.ICarUxRestrictionsChangeListener;
import android.car.userlib.CarUserManagerHelper;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.content.res.Resources;
import android.hardware.display.DisplayManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.UserHandle;
import android.text.format.DateFormat;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import android.util.Slog;
import android.util.SparseArray;
import android.view.Display;
import android.view.DisplayAddress;
import com.android.car.CarLog;
import com.android.car.CarServiceBase;
import com.android.car.CarServiceUtils;
import com.android.car.CarUxRestrictionsManagerService;
import com.android.car.Manifest;
import com.android.car.R;
import com.android.car.SystemActivityMonitoringService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.settingslib.accessibility.AccessibilityUtils;
import com.google.android.collect.Sets;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
/* loaded from: classes3.dex */
public class CarPackageManagerService extends ICarPackageManager.Stub implements CarServiceBase {
    public static final String BLOCKING_INTENT_EXTRA_BLOCKED_ACTIVITY_NAME = "blocked_activity";
    public static final String BLOCKING_INTENT_EXTRA_BLOCKED_TASK_ID = "blocked_task_id";
    public static final String BLOCKING_INTENT_EXTRA_DISPLAY_ID = "display_id";
    public static final String BLOCKING_INTENT_EXTRA_IS_ROOT_ACTIVITY_DO = "is_root_activity_do";
    public static final String BLOCKING_INTENT_EXTRA_ROOT_ACTIVITY_NAME = "root_activity_name";
    private static final boolean DBG_POLICY_CHECK = false;
    private static final boolean DBG_POLICY_ENFORCEMENT = false;
    private static final boolean DBG_POLICY_SET = false;
    private static final int LOG_SIZE = 20;
    private static final String PACKAGE_ACTIVITY_DELIMITER = "/";
    private static final String PACKAGE_DELIMITER = ",";
    private final ComponentName mActivityBlockingActivity;
    private final ActivityManager mActivityManager;
    private final List<String> mAllowedAppInstallSources;
    private final CarUxRestrictionsManagerService mCarUxRestrictionsService;
    private String mConfiguredBlacklist;
    private String mConfiguredSystemWhitelist;
    private String mConfiguredWhitelist;
    private final Context mContext;
    private final DisplayManager mDisplayManager;
    private boolean mEnableActivityBlocking;
    private final PackageHandler mHandler;
    private boolean mHasParsedPackages;
    private final PackageManager mPackageManager;
    @GuardedBy({"this"})
    private LinkedList<AppBlockingPolicyProxy> mProxies;
    private final SystemActivityMonitoringService mSystemActivityMonitoringService;
    private final VendorServiceController mVendorServiceController;
    private final LinkedList<String> mBlockedActivityLogs = new LinkedList<>();
    @GuardedBy({"this"})
    private final HashMap<String, ClientPolicy> mClientPolicies = new HashMap<>();
    @GuardedBy({"this"})
    private HashMap<String, AppBlockingPackageInfoWrapper> mActivityWhitelistMap = new HashMap<>();
    @GuardedBy({"this"})
    private final LinkedList<CarAppBlockingPolicy> mWaitingPolicies = new LinkedList<>();
    private final ActivityLaunchListener mActivityLaunchListener = new ActivityLaunchListener();
    private final SparseArray<UxRestrictionsListener> mUxRestrictionsListeners = new SparseArray<>();
    private final Set<String> mPackageManagerActions = Sets.newArraySet(new String[]{"android.intent.action.PACKAGE_ADDED", "android.intent.action.PACKAGE_CHANGED", "android.intent.action.PACKAGE_REMOVED", "android.intent.action.PACKAGE_REPLACED"});
    private final PackageParsingEventReceiver mPackageParsingEventReceiver = new PackageParsingEventReceiver();
    private final UserSwitchedEventReceiver mUserSwitchedEventReceiver = new UserSwitchedEventReceiver();
    private final HandlerThread mHandlerThread = new HandlerThread(CarLog.TAG_PACKAGE);

    public CarPackageManagerService(Context context, CarUxRestrictionsManagerService uxRestrictionsService, SystemActivityMonitoringService systemActivityMonitoringService, CarUserManagerHelper carUserManagerHelper) {
        this.mContext = context;
        this.mCarUxRestrictionsService = uxRestrictionsService;
        this.mSystemActivityMonitoringService = systemActivityMonitoringService;
        this.mPackageManager = this.mContext.getPackageManager();
        this.mActivityManager = (ActivityManager) this.mContext.getSystemService(ActivityManager.class);
        this.mDisplayManager = (DisplayManager) this.mContext.getSystemService(DisplayManager.class);
        this.mHandlerThread.start();
        this.mHandler = new PackageHandler(this.mHandlerThread.getLooper());
        Resources res = context.getResources();
        this.mEnableActivityBlocking = res.getBoolean(R.bool.enableActivityBlockingForSafety);
        String blockingActivity = res.getString(R.string.activityBlockingActivity);
        this.mActivityBlockingActivity = ComponentName.unflattenFromString(blockingActivity);
        this.mAllowedAppInstallSources = Arrays.asList(res.getStringArray(R.array.allowedAppInstallSources));
        this.mVendorServiceController = new VendorServiceController(this.mContext, this.mHandler.getLooper(), carUserManagerHelper);
    }

    public void setAppBlockingPolicy(String packageName, CarAppBlockingPolicy policy, int flags) {
        doSetAppBlockingPolicy(packageName, policy, flags);
    }

    public void restartTask(int taskId) {
        this.mSystemActivityMonitoringService.restartTask(taskId);
    }

    private void doSetAppBlockingPolicy(String packageName, CarAppBlockingPolicy policy, int flags) {
        if (this.mContext.checkCallingOrSelfPermission(Manifest.permission.CONTROL_APP_BLOCKING) != 0) {
            throw new SecurityException("requires permission android.car.permission.CONTROL_APP_BLOCKING");
        }
        CarServiceUtils.assertPackageName(this.mContext, packageName);
        if (policy == null) {
            throw new IllegalArgumentException("policy cannot be null");
        }
        if ((flags & 2) != 0 && (flags & 4) != 0) {
            throw new IllegalArgumentException("Cannot set both FLAG_SET_POLICY_ADD and FLAG_SET_POLICY_REMOVE flag");
        }
        synchronized (this) {
            if ((flags & 1) != 0) {
                this.mWaitingPolicies.add(policy);
            }
        }
        this.mHandler.requestUpdatingPolicy(packageName, policy, flags);
        if ((flags & 1) != 0) {
            synchronized (this) {
                while (this.mWaitingPolicies.contains(policy)) {
                    try {
                        try {
                            wait();
                        } catch (InterruptedException e) {
                            throw new IllegalStateException("Interrupted while waiting for policy completion", e);
                        }
                    } finally {
                    }
                }
            }
        }
    }

    public boolean isActivityDistractionOptimized(String packageName, String className) {
        assertPackageAndClassName(packageName, className);
        synchronized (this) {
            AppBlockingPackageInfo info = searchFromBlacklistsLocked(packageName);
            if (info != null) {
                return false;
            }
            return isActivityInWhitelistsLocked(packageName, className);
        }
    }

    public boolean isServiceDistractionOptimized(String packageName, String className) {
        if (packageName == null) {
            throw new IllegalArgumentException("Package name null");
        }
        synchronized (this) {
            AppBlockingPackageInfo info = searchFromBlacklistsLocked(packageName);
            if (info != null) {
                return false;
            }
            AppBlockingPackageInfo info2 = searchFromWhitelistsLocked(packageName);
            if (info2 == null) {
                return false;
            }
            return true;
        }
    }

    public boolean isActivityBackedBySafeActivity(ComponentName activityName) {
        ActivityManager.StackInfo info = this.mSystemActivityMonitoringService.getFocusedStackForTopActivity(activityName);
        if (info != null && isUxRestrictedOnDisplay(info.displayId)) {
            if (info.taskNames.length <= 1) {
                return false;
            }
            ComponentName activityBehind = ComponentName.unflattenFromString(info.taskNames[info.taskNames.length - 2]);
            return isActivityDistractionOptimized(activityBehind.getPackageName(), activityBehind.getClassName());
        }
        return true;
    }

    public Looper getLooper() {
        return this.mHandlerThread.getLooper();
    }

    private void assertPackageAndClassName(String packageName, String className) {
        if (packageName == null) {
            throw new IllegalArgumentException("Package name null");
        }
        if (className == null) {
            throw new IllegalArgumentException("Class name null");
        }
    }

    @GuardedBy({"this"})
    private AppBlockingPackageInfo searchFromBlacklistsLocked(String packageName) {
        for (ClientPolicy policy : this.mClientPolicies.values()) {
            AppBlockingPackageInfoWrapper wrapper = (AppBlockingPackageInfoWrapper) policy.blacklistsMap.get(packageName);
            if (wrapper != null && wrapper.isMatching) {
                return wrapper.info;
            }
        }
        return null;
    }

    @GuardedBy({"this"})
    private AppBlockingPackageInfo searchFromWhitelistsLocked(String packageName) {
        for (ClientPolicy policy : this.mClientPolicies.values()) {
            AppBlockingPackageInfoWrapper wrapper = (AppBlockingPackageInfoWrapper) policy.whitelistsMap.get(packageName);
            if (wrapper != null && wrapper.isMatching) {
                return wrapper.info;
            }
        }
        AppBlockingPackageInfoWrapper wrapper2 = this.mActivityWhitelistMap.get(packageName);
        if (wrapper2 != null) {
            return wrapper2.info;
        }
        return null;
    }

    @GuardedBy({"this"})
    private boolean isActivityInWhitelistsLocked(String packageName, String className) {
        for (ClientPolicy policy : this.mClientPolicies.values()) {
            if (isActivityInMapAndMatching(policy.whitelistsMap, packageName, className)) {
                return true;
            }
        }
        return isActivityInMapAndMatching(this.mActivityWhitelistMap, packageName, className);
    }

    private boolean isActivityInMapAndMatching(HashMap<String, AppBlockingPackageInfoWrapper> map, String packageName, String className) {
        AppBlockingPackageInfoWrapper wrapper = map.get(packageName);
        if (wrapper == null || !wrapper.isMatching) {
            return false;
        }
        return wrapper.info.isActivityCovered(className);
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
        synchronized (this) {
            this.mHandler.requestInit();
        }
    }

    @Override // com.android.car.CarServiceBase
    public void release() {
        synchronized (this) {
            this.mHandler.requestRelease();
            try {
                wait();
            } catch (InterruptedException e) {
            }
            this.mHasParsedPackages = false;
            this.mActivityWhitelistMap.clear();
            this.mClientPolicies.clear();
            if (this.mProxies != null) {
                Iterator<AppBlockingPolicyProxy> it = this.mProxies.iterator();
                while (it.hasNext()) {
                    AppBlockingPolicyProxy proxy = it.next();
                    proxy.disconnect();
                }
                this.mProxies.clear();
            }
            this.mWaitingPolicies.clear();
            notifyAll();
        }
        this.mContext.unregisterReceiver(this.mPackageParsingEventReceiver);
        this.mContext.unregisterReceiver(this.mUserSwitchedEventReceiver);
        this.mSystemActivityMonitoringService.registerActivityLaunchListener(null);
        for (int i = 0; i < this.mUxRestrictionsListeners.size(); i++) {
            this.mCarUxRestrictionsService.unregisterUxRestrictionsChangeListener((UxRestrictionsListener) this.mUxRestrictionsListeners.valueAt(i));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void doHandleInit() {
        startAppBlockingPolicies();
        IntentFilter intent = new IntentFilter();
        intent.addAction("android.intent.action.USER_SWITCHED");
        this.mContext.registerReceiver(this.mUserSwitchedEventReceiver, intent);
        IntentFilter pkgParseIntent = new IntentFilter();
        for (String action : this.mPackageManagerActions) {
            pkgParseIntent.addAction(action);
        }
        pkgParseIntent.addDataScheme("package");
        this.mContext.registerReceiverAsUser(this.mPackageParsingEventReceiver, UserHandle.ALL, pkgParseIntent, null, null);
        List<Display> physicalDisplays = getPhysicalDisplays();
        Display defaultDisplay = this.mDisplayManager.getDisplay(0);
        if (!physicalDisplays.contains(defaultDisplay)) {
            if (Log.isLoggable(CarLog.TAG_PACKAGE, 4)) {
                Slog.i(CarLog.TAG_PACKAGE, "Adding default display to physical displays.");
            }
            physicalDisplays.add(defaultDisplay);
        }
        for (Display physicalDisplay : physicalDisplays) {
            int displayId = physicalDisplay.getDisplayId();
            ICarUxRestrictionsChangeListener uxRestrictionsListener = new UxRestrictionsListener(this.mCarUxRestrictionsService);
            this.mUxRestrictionsListeners.put(displayId, uxRestrictionsListener);
            this.mCarUxRestrictionsService.registerUxRestrictionsChangeListener(uxRestrictionsListener, displayId);
        }
        this.mVendorServiceController.init();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void doParseInstalledPackages() {
        ActivityManager activityManager = this.mActivityManager;
        int userId = ActivityManager.getCurrentUser();
        generateActivityWhitelistMap(userId);
        synchronized (this) {
            this.mHasParsedPackages = true;
        }
        this.mSystemActivityMonitoringService.registerActivityLaunchListener(this.mActivityLaunchListener);
        blockTopActivitiesIfNecessary();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public synchronized void doHandleRelease() {
        this.mVendorServiceController.release();
        notifyAll();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void doUpdatePolicy(String packageName, CarAppBlockingPolicy policy, int flags) {
        AppBlockingPackageInfoWrapper[] blacklistWrapper = verifyList(policy.blacklists);
        AppBlockingPackageInfoWrapper[] whitelistWrapper = verifyList(policy.whitelists);
        synchronized (this) {
            ClientPolicy clientPolicy = this.mClientPolicies.get(packageName);
            if (clientPolicy == null) {
                clientPolicy = new ClientPolicy();
                this.mClientPolicies.put(packageName, clientPolicy);
            }
            if ((flags & 2) == 0) {
                if ((flags & 4) != 0) {
                    clientPolicy.removeBlacklists(blacklistWrapper);
                    clientPolicy.removeWhitelists(whitelistWrapper);
                } else {
                    clientPolicy.replaceBlacklists(blacklistWrapper);
                    clientPolicy.replaceWhitelists(whitelistWrapper);
                }
            } else {
                clientPolicy.addToBlacklists(blacklistWrapper);
                clientPolicy.addToWhitelists(whitelistWrapper);
            }
            if ((flags & 1) != 0) {
                this.mWaitingPolicies.remove(policy);
                notifyAll();
            }
        }
        blockTopActivitiesIfNecessary();
    }

    private AppBlockingPackageInfoWrapper[] verifyList(AppBlockingPackageInfo[] list) {
        if (list == null) {
            return null;
        }
        LinkedList<AppBlockingPackageInfoWrapper> wrappers = new LinkedList<>();
        for (AppBlockingPackageInfo info : list) {
            if (info != null) {
                boolean isMatching = isInstalledPackageMatching(info);
                wrappers.add(new AppBlockingPackageInfoWrapper(info, isMatching));
            }
        }
        return (AppBlockingPackageInfoWrapper[]) wrappers.toArray(new AppBlockingPackageInfoWrapper[wrappers.size()]);
    }

    boolean isInstalledPackageMatching(AppBlockingPackageInfo info) {
        try {
            PackageInfo packageInfo = this.mPackageManager.getPackageInfo(info.packageName, 64);
            if (packageInfo == null) {
                return false;
            }
            if ((info.flags & 1) == 0 || (!packageInfo.applicationInfo.isSystemApp() && !packageInfo.applicationInfo.isUpdatedSystemApp())) {
                Signature[] signatures = packageInfo.signatures;
                if (!isAnySignatureMatching(signatures, info.signatures)) {
                    return false;
                }
            }
            int version = packageInfo.versionCode;
            return info.minRevisionCode == 0 ? info.maxRevisionCode == 0 || info.maxRevisionCode > version : info.maxRevisionCode == 0 ? info.minRevisionCode < version : info.minRevisionCode < version && info.maxRevisionCode > version;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    boolean isAnySignatureMatching(Signature[] fromPackage, Signature[] fromPolicy) {
        if (fromPackage == null || fromPolicy == null) {
            return false;
        }
        ArraySet<Signature> setFromPackage = new ArraySet<>();
        for (Signature sig : fromPackage) {
            setFromPackage.add(sig);
        }
        for (Signature sig2 : fromPolicy) {
            if (setFromPackage.contains(sig2)) {
                return true;
            }
        }
        return false;
    }

    private void generateActivityWhitelistMap(int userId) {
        Map<String, Set<String>> configWhitelist = generateConfigWhitelist();
        Map<String, Set<String>> configBlacklist = generateConfigBlacklist();
        Map<String, AppBlockingPackageInfoWrapper> activityWhitelist = generateActivityWhitelistAsUser(0, configWhitelist, configBlacklist);
        if (userId != 0) {
            Map<String, AppBlockingPackageInfoWrapper> userWhitelistedPackages = generateActivityWhitelistAsUser(userId, configWhitelist, configBlacklist);
            for (String packageName : userWhitelistedPackages.keySet()) {
                if (!activityWhitelist.containsKey(packageName)) {
                    activityWhitelist.put(packageName, userWhitelistedPackages.get(packageName));
                }
            }
        }
        synchronized (this) {
            this.mActivityWhitelistMap.clear();
            this.mActivityWhitelistMap.putAll(activityWhitelist);
        }
    }

    private Map<String, Set<String>> generateConfigWhitelist() {
        Map<String, Set<String>> configWhitelist = new HashMap<>();
        this.mConfiguredWhitelist = this.mContext.getString(R.string.activityWhitelist);
        parseConfigList(this.mConfiguredWhitelist, configWhitelist);
        this.mConfiguredSystemWhitelist = this.mContext.getString(R.string.systemActivityWhitelist);
        parseConfigList(this.mConfiguredSystemWhitelist, configWhitelist);
        Set<String> defaultActivity = new ArraySet<>();
        ComponentName componentName = this.mActivityBlockingActivity;
        if (componentName != null) {
            defaultActivity.add(componentName.getClassName());
            configWhitelist.put(this.mActivityBlockingActivity.getPackageName(), defaultActivity);
        }
        return configWhitelist;
    }

    private Map<String, Set<String>> generateConfigBlacklist() {
        Map<String, Set<String>> configBlacklist = new HashMap<>();
        this.mConfiguredBlacklist = this.mContext.getString(R.string.activityBlacklist);
        parseConfigList(this.mConfiguredBlacklist, configBlacklist);
        return configBlacklist;
    }

    /* JADX WARN: Removed duplicated region for block: B:37:0x00a4  */
    /* JADX WARN: Removed duplicated region for block: B:38:0x00a7  */
    /* JADX WARN: Removed duplicated region for block: B:46:0x00d8 A[Catch: NameNotFoundException -> 0x0130, TRY_LEAVE, TryCatch #1 {NameNotFoundException -> 0x0130, blocks: (B:44:0x00ce, B:46:0x00d8), top: B:63:0x00ce }] */
    /* JADX WARN: Removed duplicated region for block: B:70:0x00e8 A[SYNTHETIC] */
    /* JADX WARN: Removed duplicated region for block: B:78:0x0019 A[SYNTHETIC] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    private java.util.Map<java.lang.String, com.android.car.pm.CarPackageManagerService.AppBlockingPackageInfoWrapper> generateActivityWhitelistAsUser(int r21, java.util.Map<java.lang.String, java.util.Set<java.lang.String>> r22, java.util.Map<java.lang.String, java.util.Set<java.lang.String>> r23) {
        /*
            Method dump skipped, instructions count: 332
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.car.pm.CarPackageManagerService.generateActivityWhitelistAsUser(int, java.util.Map, java.util.Map):java.util.Map");
    }

    private boolean isDebugBuild() {
        return Build.IS_USERDEBUG || Build.IS_ENG;
    }

    @VisibleForTesting
    void parseConfigList(String configList, Map<String, Set<String>> packageToActivityMap) {
        if (configList == null) {
            return;
        }
        String[] entries = configList.split(PACKAGE_DELIMITER);
        for (String entry : entries) {
            String[] packageActivityPair = entry.split(PACKAGE_ACTIVITY_DELIMITER);
            Set<String> activities = packageToActivityMap.get(packageActivityPair[0]);
            boolean newPackage = false;
            if (activities == null) {
                activities = new ArraySet();
                newPackage = true;
                packageToActivityMap.put(packageActivityPair[0], activities);
            }
            if (packageActivityPair.length == 1) {
                activities.clear();
            } else if (packageActivityPair.length == 2 && (newPackage || activities.size() > 0)) {
                activities.add(packageActivityPair[1]);
            }
        }
    }

    private List<String> getActivitiesInPackage(PackageInfo info) {
        ActivityInfo[] activityInfoArr;
        if (info == null || info.activities == null) {
            return null;
        }
        List<String> activityList = new ArrayList<>();
        for (ActivityInfo aInfo : info.activities) {
            activityList.add(aInfo.name);
        }
        return activityList;
    }

    @VisibleForTesting
    public void startAppBlockingPolicies() {
        Intent policyIntent = new Intent();
        policyIntent.setAction("android.car.content.pm.CarAppBlockingPolicyService");
        List<ResolveInfo> policyInfos = this.mPackageManager.queryIntentServices(policyIntent, 0);
        if (policyInfos == null) {
            return;
        }
        LinkedList<AppBlockingPolicyProxy> proxies = new LinkedList<>();
        for (ResolveInfo resolveInfo : policyInfos) {
            ServiceInfo serviceInfo = resolveInfo.serviceInfo;
            if (serviceInfo != null && serviceInfo.isEnabled() && this.mPackageManager.checkPermission(Manifest.permission.CONTROL_APP_BLOCKING, serviceInfo.packageName) == 0) {
                Slog.i(CarLog.TAG_PACKAGE, "found policy holding service:" + serviceInfo);
                AppBlockingPolicyProxy proxy = new AppBlockingPolicyProxy(this, this.mContext, serviceInfo);
                proxy.connect();
                proxies.add(proxy);
            }
        }
        synchronized (this) {
            this.mProxies = proxies;
        }
    }

    public void onPolicyConnectionAndSet(AppBlockingPolicyProxy proxy, CarAppBlockingPolicy policy) {
        doHandlePolicyConnection(proxy, policy);
    }

    public void onPolicyConnectionFailure(AppBlockingPolicyProxy proxy) {
        doHandlePolicyConnection(proxy, null);
    }

    private void doHandlePolicyConnection(AppBlockingPolicyProxy proxy, CarAppBlockingPolicy policy) {
        synchronized (this) {
            if (this.mProxies == null) {
                return;
            }
            this.mProxies.remove(proxy);
            if (this.mProxies.size() == 0) {
                this.mProxies = null;
            }
            if (policy != null) {
                try {
                    doSetAppBlockingPolicy(proxy.getPackageName(), policy, 0);
                } finally {
                    proxy.disconnect();
                }
            }
        }
    }

    @Override // com.android.car.CarServiceBase
    public void dump(PrintWriter writer) {
        synchronized (this) {
            writer.println("*CarPackageManagerService*");
            writer.println("mEnableActivityBlocking:" + this.mEnableActivityBlocking);
            writer.println("mHasParsedPackages:" + this.mHasParsedPackages);
            List<String> restrictions = new ArrayList<>(this.mUxRestrictionsListeners.size());
            for (int i = 0; i < this.mUxRestrictionsListeners.size(); i++) {
                int displayId = this.mUxRestrictionsListeners.keyAt(i);
                UxRestrictionsListener listener = this.mUxRestrictionsListeners.valueAt(i);
                Object[] objArr = new Object[2];
                objArr[0] = Integer.valueOf(displayId);
                objArr[1] = listener.isRestricted() ? "restricted" : "unrestricted";
                restrictions.add(String.format("Display %d is %s", objArr));
            }
            writer.println("Display Restrictions:\n" + String.join("\n", restrictions));
            writer.println(" Blocked activity log:");
            writer.println(String.join("\n", this.mBlockedActivityLogs));
            writer.print(dumpPoliciesLocked(true));
        }
    }

    @GuardedBy({"this"})
    private String dumpPoliciesLocked(boolean dumpAll) {
        StringBuilder sb = new StringBuilder();
        if (dumpAll) {
            sb.append("**System whitelist**\n");
            for (AppBlockingPackageInfoWrapper wrapper : this.mActivityWhitelistMap.values()) {
                sb.append(wrapper.toString() + "\n");
            }
        }
        sb.append("**Client Policies**\n");
        for (Map.Entry<String, ClientPolicy> entry : this.mClientPolicies.entrySet()) {
            sb.append("Client:" + entry.getKey() + "\n");
            sb.append("  whitelists:\n");
            for (AppBlockingPackageInfoWrapper wrapper2 : entry.getValue().whitelistsMap.values()) {
                sb.append(wrapper2.toString() + "\n");
            }
            sb.append("  blacklists:\n");
            for (AppBlockingPackageInfoWrapper wrapper3 : entry.getValue().blacklistsMap.values()) {
                sb.append(wrapper3.toString() + "\n");
            }
        }
        sb.append("**Unprocessed policy services**\n");
        LinkedList<AppBlockingPolicyProxy> linkedList = this.mProxies;
        if (linkedList != null) {
            Iterator<AppBlockingPolicyProxy> it = linkedList.iterator();
            while (it.hasNext()) {
                AppBlockingPolicyProxy proxy = it.next();
                sb.append(proxy.toString() + "\n");
            }
        }
        sb.append("**Whitelist string in resource**\n");
        sb.append(this.mConfiguredWhitelist + "\n");
        sb.append("**System whitelist string in resource**\n");
        sb.append(this.mConfiguredSystemWhitelist + "\n");
        sb.append("**Blacklist string in resource**\n");
        sb.append(this.mConfiguredBlacklist + "\n");
        return sb.toString();
    }

    private List<Display> getPhysicalDisplays() {
        Display[] displays;
        List<Display> displays2 = new ArrayList<>();
        for (Display display : this.mDisplayManager.getDisplays()) {
            if (display.getAddress() instanceof DisplayAddress.Physical) {
                displays2.add(display);
            }
        }
        return displays2;
    }

    private boolean isUxRestrictedOnDisplay(int displayId) {
        UxRestrictionsListener listenerForTopTaskDisplay;
        if (this.mUxRestrictionsListeners.indexOfKey(displayId) < 0) {
            listenerForTopTaskDisplay = this.mUxRestrictionsListeners.get(0);
            if (listenerForTopTaskDisplay == null) {
                Slog.e(CarLog.TAG_PACKAGE, "Missing listener for default display.");
                return true;
            }
        } else {
            listenerForTopTaskDisplay = this.mUxRestrictionsListeners.get(displayId);
        }
        return listenerForTopTaskDisplay.isRestricted();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void blockTopActivitiesIfNecessary() {
        List<SystemActivityMonitoringService.TopTaskInfoContainer> topTasks = this.mSystemActivityMonitoringService.getTopTasks();
        for (SystemActivityMonitoringService.TopTaskInfoContainer topTask : topTasks) {
            if (topTask == null) {
                Slog.e(CarLog.TAG_PACKAGE, "Top tasks contains null.");
            } else {
                blockTopActivityIfNecessary(topTask);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void blockTopActivityIfNecessary(SystemActivityMonitoringService.TopTaskInfoContainer topTask) {
        if (isUxRestrictedOnDisplay(topTask.displayId)) {
            doBlockTopActivityIfNotAllowed(topTask);
        }
    }

    private void doBlockTopActivityIfNotAllowed(SystemActivityMonitoringService.TopTaskInfoContainer topTask) {
        if (topTask.topActivity == null) {
            return;
        }
        if (!this.mHasParsedPackages) {
            if (Log.isLoggable(CarLog.TAG_PACKAGE, 4)) {
                Slog.i(CarLog.TAG_PACKAGE, "Packages not parsed, so ignoring block for " + topTask);
                return;
            }
            return;
        }
        boolean allowed = isActivityDistractionOptimized(topTask.topActivity.getPackageName(), topTask.topActivity.getClassName());
        if (allowed) {
            return;
        }
        synchronized (this) {
            if (!this.mEnableActivityBlocking) {
                Slog.d(CarLog.TAG_PACKAGE, "Current activity " + topTask.topActivity + " not allowed, blocking disabled. Number of tasks in stack:" + topTask.stackInfo.taskIds.length);
                return;
            }
            String taskRootActivity = null;
            int i = 0;
            while (true) {
                if (i >= topTask.stackInfo.taskIds.length) {
                    break;
                } else if (topTask.stackInfo.taskIds[i] != topTask.taskId) {
                    i++;
                } else {
                    taskRootActivity = topTask.stackInfo.taskNames[i];
                    break;
                }
            }
            boolean isRootDO = false;
            if (taskRootActivity != null) {
                ComponentName componentName = ComponentName.unflattenFromString(taskRootActivity);
                isRootDO = isActivityDistractionOptimized(componentName.getPackageName(), componentName.getClassName());
            }
            Intent newActivityIntent = createBlockingActivityIntent(this.mActivityBlockingActivity, topTask.displayId, topTask.topActivity.flattenToShortString(), topTask.taskId, taskRootActivity, isRootDO);
            String log = "Starting blocking activity with intent: " + newActivityIntent.toUri(0);
            if (Log.isLoggable(CarLog.TAG_PACKAGE, 4)) {
                Slog.i(CarLog.TAG_PACKAGE, log);
            }
            addLog(log);
            this.mSystemActivityMonitoringService.blockActivity(topTask, newActivityIntent);
        }
    }

    private static Intent createBlockingActivityIntent(ComponentName blockingActivity, int displayId, String blockedActivity, int blockedTaskId, String taskRootActivity, boolean isRootDo) {
        Intent newActivityIntent = new Intent();
        newActivityIntent.setFlags(134217728);
        newActivityIntent.setComponent(blockingActivity);
        newActivityIntent.putExtra(BLOCKING_INTENT_EXTRA_DISPLAY_ID, displayId);
        newActivityIntent.putExtra(BLOCKING_INTENT_EXTRA_BLOCKED_ACTIVITY_NAME, blockedActivity);
        newActivityIntent.putExtra(BLOCKING_INTENT_EXTRA_BLOCKED_TASK_ID, blockedTaskId);
        newActivityIntent.putExtra(BLOCKING_INTENT_EXTRA_ROOT_ACTIVITY_NAME, taskRootActivity);
        newActivityIntent.putExtra(BLOCKING_INTENT_EXTRA_IS_ROOT_ACTIVITY_DO, isRootDo);
        return newActivityIntent;
    }

    public synchronized void setEnableActivityBlocking(boolean enable) {
        if (!isDebugBuild()) {
            Slog.e(CarLog.TAG_PACKAGE, "Cannot enable/disable activity blocking");
        } else if (this.mPackageManager.checkSignatures(Process.myUid(), Binder.getCallingUid()) != 0) {
            throw new SecurityException("Caller " + this.mPackageManager.getNameForUid(Binder.getCallingUid()) + " does not have the right signature");
        } else {
            this.mCarUxRestrictionsService.setUxRChangeBroadcastEnabled(enable);
        }
    }

    public String[] getDistractionOptimizedActivities(String pkgName) {
        try {
            Context context = this.mContext;
            ActivityManager activityManager = this.mActivityManager;
            return CarAppMetadataReader.findDistractionOptimizedActivitiesAsUser(context, pkgName, ActivityManager.getCurrentUser());
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private void addLog(String log) {
        while (this.mBlockedActivityLogs.size() >= 20) {
            this.mBlockedActivityLogs.remove();
        }
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append(CarLog.TAG_PACKAGE);
        stringBuffer.append(AccessibilityUtils.ENABLED_ACCESSIBILITY_SERVICES_SEPARATOR);
        stringBuffer.append(DateFormat.format("MM-dd HH:mm:ss", System.currentTimeMillis()));
        stringBuffer.append(": ");
        StringBuffer sb = stringBuffer.append(log);
        this.mBlockedActivityLogs.add(sb.toString());
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class PackageHandler extends Handler {
        private static final int MSG_INIT = 0;
        private static final int MSG_PARSE_PKG = 1;
        private static final int MSG_RELEASE = 3;
        private static final int MSG_UPDATE_POLICY = 2;

        private PackageHandler(Looper looper) {
            super(looper);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void requestInit() {
            Message msg = obtainMessage(0);
            sendMessage(msg);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void requestRelease() {
            removeMessages(0);
            removeMessages(2);
            Message msg = obtainMessage(3);
            sendMessage(msg);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void requestUpdatingPolicy(String packageName, CarAppBlockingPolicy policy, int flags) {
            Pair<String, CarAppBlockingPolicy> pair = new Pair<>(packageName, policy);
            Message msg = obtainMessage(2, flags, 0, pair);
            sendMessage(msg);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void requestParsingInstalledPkgs(long delayMs) {
            removeMessages(1);
            Message msg = obtainMessage(1);
            if (delayMs == 0) {
                sendMessage(msg);
            } else {
                sendMessageDelayed(msg, delayMs);
            }
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            int i = msg.what;
            if (i == 0) {
                CarPackageManagerService.this.doHandleInit();
            } else if (i == 1) {
                CarPackageManagerService.this.doParseInstalledPackages();
            } else if (i == 2) {
                Pair<String, CarAppBlockingPolicy> pair = (Pair) msg.obj;
                CarPackageManagerService.this.doUpdatePolicy((String) pair.first, (CarAppBlockingPolicy) pair.second, msg.arg1);
            } else if (i == 3) {
                CarPackageManagerService.this.doHandleRelease();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static class AppBlockingPackageInfoWrapper {
        private final AppBlockingPackageInfo info;
        private boolean isMatching;

        private AppBlockingPackageInfoWrapper(AppBlockingPackageInfo info, boolean isMatching) {
            this.info = info;
            this.isMatching = isMatching;
        }

        public String toString() {
            return "AppBlockingPackageInfoWrapper [info=" + this.info + ", isMatching=" + this.isMatching + "]";
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static class ClientPolicy {
        private final HashMap<String, AppBlockingPackageInfoWrapper> blacklistsMap;
        private final HashMap<String, AppBlockingPackageInfoWrapper> whitelistsMap;

        private ClientPolicy() {
            this.whitelistsMap = new HashMap<>();
            this.blacklistsMap = new HashMap<>();
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void replaceWhitelists(AppBlockingPackageInfoWrapper[] whitelists) {
            this.whitelistsMap.clear();
            addToWhitelists(whitelists);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void addToWhitelists(AppBlockingPackageInfoWrapper[] whitelists) {
            if (whitelists == null) {
                return;
            }
            for (AppBlockingPackageInfoWrapper wrapper : whitelists) {
                if (wrapper != null) {
                    this.whitelistsMap.put(wrapper.info.packageName, wrapper);
                }
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void removeWhitelists(AppBlockingPackageInfoWrapper[] whitelists) {
            if (whitelists == null) {
                return;
            }
            for (AppBlockingPackageInfoWrapper wrapper : whitelists) {
                if (wrapper != null) {
                    this.whitelistsMap.remove(wrapper.info.packageName);
                }
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void replaceBlacklists(AppBlockingPackageInfoWrapper[] blacklists) {
            this.blacklistsMap.clear();
            addToBlacklists(blacklists);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void addToBlacklists(AppBlockingPackageInfoWrapper[] blacklists) {
            if (blacklists == null) {
                return;
            }
            for (AppBlockingPackageInfoWrapper wrapper : blacklists) {
                if (wrapper != null) {
                    this.blacklistsMap.put(wrapper.info.packageName, wrapper);
                }
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void removeBlacklists(AppBlockingPackageInfoWrapper[] blacklists) {
            if (blacklists == null) {
                return;
            }
            for (AppBlockingPackageInfoWrapper wrapper : blacklists) {
                if (wrapper != null) {
                    this.blacklistsMap.remove(wrapper.info.packageName);
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class ActivityLaunchListener implements SystemActivityMonitoringService.ActivityLaunchListener {
        private ActivityLaunchListener() {
        }

        @Override // com.android.car.SystemActivityMonitoringService.ActivityLaunchListener
        public void onActivityLaunch(SystemActivityMonitoringService.TopTaskInfoContainer topTask) {
            if (topTask != null) {
                CarPackageManagerService.this.blockTopActivityIfNecessary(topTask);
            } else {
                Slog.e(CarLog.TAG_PACKAGE, "Received callback with null top task.");
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class UxRestrictionsListener extends ICarUxRestrictionsChangeListener.Stub {
        @GuardedBy({"this"})
        private CarUxRestrictions mCurrentUxRestrictions;
        private final CarUxRestrictionsManagerService uxRestrictionsService;

        public UxRestrictionsListener(CarUxRestrictionsManagerService service) {
            this.uxRestrictionsService = service;
        }

        public void onUxRestrictionsChanged(CarUxRestrictions restrictions) {
            if (!CarPackageManagerService.this.mHasParsedPackages) {
                return;
            }
            synchronized (this) {
                this.mCurrentUxRestrictions = new CarUxRestrictions(restrictions);
            }
            checkIfTopActivityNeedsBlocking();
        }

        private void checkIfTopActivityNeedsBlocking() {
            boolean shouldCheck = false;
            synchronized (this) {
                if (this.mCurrentUxRestrictions != null && this.mCurrentUxRestrictions.isRequiresDistractionOptimization()) {
                    shouldCheck = true;
                }
            }
            if (shouldCheck) {
                CarPackageManagerService.this.blockTopActivitiesIfNecessary();
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public synchronized boolean isRestricted() {
            if (this.mCurrentUxRestrictions == null) {
                this.mCurrentUxRestrictions = this.uxRestrictionsService.getCurrentUxRestrictions();
            }
            if (this.mCurrentUxRestrictions != null) {
                return this.mCurrentUxRestrictions.isRequiresDistractionOptimization();
            }
            return false;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class UserSwitchedEventReceiver extends BroadcastReceiver {
        private UserSwitchedEventReceiver() {
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.getAction() != null && "android.intent.action.USER_SWITCHED".equals(intent.getAction())) {
                CarPackageManagerService.this.mHandler.requestParsingInstalledPkgs(0L);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class PackageParsingEventReceiver extends BroadcastReceiver {
        private static final long PACKAGE_PARSING_DELAY_MS = 500;

        private PackageParsingEventReceiver() {
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }
            String action = intent.getAction();
            if (isPackageManagerAction(action)) {
                logEventChange(intent);
                CarPackageManagerService.this.mHandler.requestParsingInstalledPkgs(PACKAGE_PARSING_DELAY_MS);
            }
        }

        private boolean isPackageManagerAction(String action) {
            return CarPackageManagerService.this.mPackageManagerActions.contains(action);
        }

        private void logEventChange(Intent intent) {
        }
    }
}
