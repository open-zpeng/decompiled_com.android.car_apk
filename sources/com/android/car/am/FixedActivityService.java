package com.android.car.am;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.IActivityManager;
import android.app.IProcessObserver;
import android.app.Presentation;
import android.app.TaskStackListener;
import android.car.hardware.power.CarPowerManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Slog;
import android.util.SparseArray;
import android.view.Display;
import com.android.car.CarLocalServices;
import com.android.car.CarLog;
import com.android.car.CarServiceBase;
import com.android.car.CarServiceUtils;
import com.android.car.R;
import com.android.car.user.CarUserService;
import com.android.internal.annotations.GuardedBy;
import java.io.PrintWriter;
import java.util.List;
/* loaded from: classes3.dex */
public final class FixedActivityService implements CarServiceBase {
    private static final long CRASH_FORGET_INTERVAL_MS = 120000;
    private static final boolean DBG = false;
    private static final int MAX_NUMBER_OF_CONSECUTIVE_CRASH_RETRY = 5;
    private static final long RECHECK_INTERVAL_MS = 500;
    @GuardedBy({"mLock"})
    private CarPowerManager mCarPowerManager;
    private final Context mContext;
    private final DisplayManager mDm;
    @GuardedBy({"mLock"})
    private boolean mEventMonitoringActive;
    private final UserManager mUm;
    private final CarUserService.UserCallback mUserCallback = new CarUserService.UserCallback() { // from class: com.android.car.am.FixedActivityService.1
        @Override // com.android.car.user.CarUserService.UserCallback
        public void onUserLockChanged(int userId, boolean unlocked) {
        }

        @Override // com.android.car.user.CarUserService.UserCallback
        public void onSwitchUser(int userId) {
            synchronized (FixedActivityService.this.mLock) {
                FixedActivityService.this.mRunningActivities.clear();
            }
        }
    };
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() { // from class: com.android.car.am.FixedActivityService.2
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("android.intent.action.PACKAGE_CHANGED".equals(action) || "android.intent.action.PACKAGE_REPLACED".equals(action)) {
                Uri packageData = intent.getData();
                if (packageData == null) {
                    Slog.w(CarLog.TAG_AM, "null packageData");
                    return;
                }
                String packageName = packageData.getSchemeSpecificPart();
                if (packageName == null) {
                    Slog.w(CarLog.TAG_AM, "null packageName");
                    return;
                }
                int uid = intent.getIntExtra("android.intent.extra.UID", -1);
                int userId = UserHandle.getUserId(uid);
                boolean tryLaunch = false;
                synchronized (FixedActivityService.this.mLock) {
                    for (int i = 0; i < FixedActivityService.this.mRunningActivities.size(); i++) {
                        RunningActivityInfo info = (RunningActivityInfo) FixedActivityService.this.mRunningActivities.valueAt(i);
                        ComponentName component = info.intent.getComponent();
                        if (packageName.equals(component.getPackageName()) && info.userId == userId) {
                            Slog.i(CarLog.TAG_AM, "Package updated:" + packageName + ",user:" + userId);
                            info.resetCrashCounterLocked();
                            tryLaunch = true;
                        }
                    }
                }
                if (tryLaunch) {
                    FixedActivityService.this.lambda$new$0$FixedActivityService();
                }
            }
        }
    };
    private final TaskStackListener mTaskStackListener = new TaskStackListener() { // from class: com.android.car.am.FixedActivityService.3
        public void onTaskStackChanged() {
            FixedActivityService.this.lambda$new$0$FixedActivityService();
        }

        public void onTaskCreated(int taskId, ComponentName componentName) {
            FixedActivityService.this.lambda$new$0$FixedActivityService();
        }

        public void onTaskRemoved(int taskId) {
            FixedActivityService.this.lambda$new$0$FixedActivityService();
        }

        public void onTaskMovedToFront(int taskId) {
            FixedActivityService.this.lambda$new$0$FixedActivityService();
        }

        public void onTaskRemovalStarted(int taskId) {
            FixedActivityService.this.lambda$new$0$FixedActivityService();
        }
    };
    private final IProcessObserver mProcessObserver = new IProcessObserver.Stub() { // from class: com.android.car.am.FixedActivityService.4
        public void onForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities) {
            FixedActivityService.this.lambda$new$0$FixedActivityService();
        }

        public void onForegroundServicesChanged(int pid, int uid, int fgServiceTypes) {
        }

        public void onProcessDied(int pid, int uid) {
            FixedActivityService.this.lambda$new$0$FixedActivityService();
        }
    };
    private final HandlerThread mHandlerThread = new HandlerThread(FixedActivityService.class.getSimpleName());
    private final Runnable mActivityCheckRunnable = new Runnable() { // from class: com.android.car.am.-$$Lambda$FixedActivityService$YIIxfUh44ByxKrPzWysY6xeGvQA
        @Override // java.lang.Runnable
        public final void run() {
            FixedActivityService.this.lambda$new$0$FixedActivityService();
        }
    };
    private final Object mLock = new Object();
    @GuardedBy({"mLock"})
    private final SparseArray<RunningActivityInfo> mRunningActivities = new SparseArray<>(1);
    @GuardedBy({"mLock"})
    private final SparseArray<Presentation> mBlockingPresentations = new SparseArray<>(1);
    private final CarPowerManager.CarPowerStateListener mCarPowerStateListener = new CarPowerManager.CarPowerStateListener() { // from class: com.android.car.am.-$$Lambda$FixedActivityService$PGGFZ9RAnz2qKhaG9LXvPwOxlBA
        public final void onStateChanged(int i) {
            FixedActivityService.this.lambda$new$1$FixedActivityService(i);
        }
    };
    private final IActivityManager mAm = ActivityManager.getService();

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static class RunningActivityInfo {
        public final ActivityOptions activityOptions;
        @GuardedBy({"mLock"})
        public boolean failureLogged;
        @GuardedBy({"mLock"})
        public boolean inBackground;
        public final Intent intent;
        @GuardedBy({"mLock"})
        public boolean isVisible;
        public final int userId;
        @GuardedBy({"mLock"})
        public long lastLaunchTimeMs = 0;
        @GuardedBy({"mLock"})
        public int consecutiveRetries = 0;
        @GuardedBy({"mLock"})
        public int taskId = -1;
        @GuardedBy({"mLock"})
        public int previousTaskId = -1;

        RunningActivityInfo(Intent intent, ActivityOptions activityOptions, int userId) {
            this.intent = intent;
            this.activityOptions = activityOptions;
            this.userId = userId;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void resetCrashCounterLocked() {
            this.consecutiveRetries = 0;
            this.failureLogged = false;
        }

        public String toString() {
            return "RunningActivityInfo{intent:" + this.intent + ",activityOptions:" + this.activityOptions + ",userId:" + this.userId + ",isVisible:" + this.isVisible + ",lastLaunchTimeMs:" + this.lastLaunchTimeMs + ",consecutiveRetries:" + this.consecutiveRetries + ",taskId:" + this.taskId + "}";
        }
    }

    public /* synthetic */ void lambda$new$1$FixedActivityService(int state) {
        if (state != 6) {
            return;
        }
        synchronized (this.mLock) {
            for (int i = 0; i < this.mRunningActivities.size(); i++) {
                RunningActivityInfo info = this.mRunningActivities.valueAt(i);
                info.resetCrashCounterLocked();
            }
        }
        lambda$new$0$FixedActivityService();
    }

    public FixedActivityService(Context context) {
        this.mContext = context;
        this.mUm = (UserManager) context.getSystemService(UserManager.class);
        this.mDm = (DisplayManager) context.getSystemService(DisplayManager.class);
        this.mHandlerThread.start();
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
    }

    @Override // com.android.car.CarServiceBase
    public void release() {
        stopMonitoringEvents();
    }

    @Override // com.android.car.CarServiceBase
    public void dump(PrintWriter writer) {
        writer.println("*FixedActivityService*");
        synchronized (this.mLock) {
            writer.println("mRunningActivities:" + this.mRunningActivities + " ,mEventMonitoringActive:" + this.mEventMonitoringActive);
        }
    }

    private void postRecheck(long delayMs) {
        this.mHandlerThread.getThreadHandler().postDelayed(this.mActivityCheckRunnable, delayMs);
    }

    private void startMonitoringEvents() {
        synchronized (this.mLock) {
            if (this.mEventMonitoringActive) {
                return;
            }
            this.mEventMonitoringActive = true;
            CarPowerManager carPowerManager = CarLocalServices.createCarPowerManager(this.mContext);
            this.mCarPowerManager = carPowerManager;
            CarUserService userService = (CarUserService) CarLocalServices.getService(CarUserService.class);
            userService.addUserCallback(this.mUserCallback);
            IntentFilter filter = new IntentFilter();
            filter.addAction("android.intent.action.PACKAGE_CHANGED");
            filter.addAction("android.intent.action.PACKAGE_REPLACED");
            filter.addDataScheme("package");
            this.mContext.registerReceiverAsUser(this.mBroadcastReceiver, UserHandle.ALL, filter, null, null);
            try {
                this.mAm.registerTaskStackListener(this.mTaskStackListener);
                this.mAm.registerProcessObserver(this.mProcessObserver);
            } catch (RemoteException e) {
                Slog.e(CarLog.TAG_AM, "remote exception from AM", e);
            }
            try {
                carPowerManager.setListener(this.mCarPowerStateListener);
            } catch (Exception e2) {
                Slog.e(CarLog.TAG_AM, "Got exception from CarPowerManager", e2);
            }
        }
    }

    private void stopMonitoringEvents() {
        synchronized (this.mLock) {
            if (this.mEventMonitoringActive) {
                this.mEventMonitoringActive = false;
                CarPowerManager carPowerManager = this.mCarPowerManager;
                this.mCarPowerManager = null;
                if (carPowerManager != null) {
                    carPowerManager.clearListener();
                }
                this.mHandlerThread.getThreadHandler().removeCallbacks(this.mActivityCheckRunnable);
                CarUserService userService = (CarUserService) CarLocalServices.getService(CarUserService.class);
                userService.removeUserCallback(this.mUserCallback);
                try {
                    this.mAm.unregisterTaskStackListener(this.mTaskStackListener);
                    this.mAm.unregisterProcessObserver(this.mProcessObserver);
                } catch (RemoteException e) {
                    Slog.e(CarLog.TAG_AM, "remote exception from AM", e);
                }
                this.mContext.unregisterReceiver(this.mBroadcastReceiver);
            }
        }
    }

    private List<ActivityManager.StackInfo> getStackInfos() {
        try {
            return this.mAm.getAllStackInfos();
        } catch (RemoteException e) {
            Slog.e(CarLog.TAG_AM, "remote exception from AM", e);
            return null;
        }
    }

    private boolean launchIfNecessary(int displayId) {
        List<ActivityManager.StackInfo> infos = getStackInfos();
        int i = 0;
        if (infos == null) {
            Slog.e(CarLog.TAG_AM, "cannot get StackInfo from AM");
            return false;
        }
        long now = SystemClock.elapsedRealtime();
        synchronized (this.mLock) {
            try {
                try {
                    if (this.mRunningActivities.size() == 0) {
                        return false;
                    }
                    for (int i2 = this.mRunningActivities.size() - 1; i2 >= 0; i2--) {
                        RunningActivityInfo activityInfo = this.mRunningActivities.valueAt(i2);
                        activityInfo.isVisible = false;
                        if (!isUserAllowedToLaunchActivity(activityInfo.userId)) {
                            final int displayIdForActivity = this.mRunningActivities.keyAt(i2);
                            if (activityInfo.taskId != -1) {
                                Slog.i(CarLog.TAG_AM, "Finishing fixed activity on user switching:" + activityInfo);
                                try {
                                    this.mAm.removeTask(activityInfo.taskId);
                                } catch (RemoteException e) {
                                    Slog.e(CarLog.TAG_AM, "remote exception from AM", e);
                                }
                                CarServiceUtils.runOnMain(new Runnable() { // from class: com.android.car.am.-$$Lambda$FixedActivityService$aL2-yW2hu9pwfp_Jcn_2lNoc6XE
                                    @Override // java.lang.Runnable
                                    public final void run() {
                                        FixedActivityService.this.lambda$launchIfNecessary$2$FixedActivityService(displayIdForActivity);
                                    }
                                });
                            }
                            this.mRunningActivities.removeAt(i2);
                        }
                    }
                    for (ActivityManager.StackInfo stackInfo : infos) {
                        RunningActivityInfo activityInfo2 = this.mRunningActivities.get(stackInfo.displayId);
                        if (activityInfo2 != null) {
                            int topUserId = stackInfo.taskUserIds[stackInfo.taskUserIds.length - 1];
                            if (activityInfo2.intent.getComponent().equals(stackInfo.topActivity) && activityInfo2.userId == topUserId && stackInfo.visible) {
                                activityInfo2.isVisible = true;
                                activityInfo2.taskId = stackInfo.taskIds[stackInfo.taskIds.length - 1];
                            } else {
                                activityInfo2.previousTaskId = stackInfo.taskIds[stackInfo.taskIds.length - 1];
                                Slog.i(CarLog.TAG_AM, "Unmatched top activity will be removed:" + stackInfo.topActivity + " top task id:" + activityInfo2.previousTaskId + " user:" + topUserId + " display:" + stackInfo.displayId);
                                activityInfo2.inBackground = false;
                                for (int i3 = 0; i3 < stackInfo.taskIds.length - 1; i3++) {
                                    if (activityInfo2.taskId == stackInfo.taskIds[i3]) {
                                        activityInfo2.inBackground = true;
                                    }
                                }
                                if (!activityInfo2.inBackground) {
                                    activityInfo2.taskId = -1;
                                }
                            }
                        }
                    }
                    int i4 = 0;
                    while (i4 < this.mRunningActivities.size()) {
                        RunningActivityInfo activityInfo3 = this.mRunningActivities.valueAt(i4);
                        long timeSinceLastLaunchMs = now - activityInfo3.lastLaunchTimeMs;
                        if (!activityInfo3.isVisible) {
                            if (isComponentAvailable(activityInfo3.intent.getComponent(), activityInfo3.userId) && (activityInfo3.consecutiveRetries <= 0 || timeSinceLastLaunchMs >= RECHECK_INTERVAL_MS)) {
                                if (activityInfo3.consecutiveRetries >= 5) {
                                    if (!activityInfo3.failureLogged) {
                                        activityInfo3.failureLogged = true;
                                        Slog.w(CarLog.TAG_AM, "Too many relaunch failure of fixed activity:" + activityInfo3);
                                    }
                                } else {
                                    Slog.i(CarLog.TAG_AM, "Launching Activity for fixed mode. Intent:" + activityInfo3.intent + ",userId:" + UserHandle.of(activityInfo3.userId) + ",displayId:" + this.mRunningActivities.keyAt(i4));
                                    if (!activityInfo3.inBackground) {
                                        activityInfo3.consecutiveRetries++;
                                    }
                                    try {
                                        postRecheck(RECHECK_INTERVAL_MS);
                                        postRecheck(CRASH_FORGET_INTERVAL_MS);
                                        this.mContext.startActivityAsUser(activityInfo3.intent, activityInfo3.activityOptions.toBundle(), UserHandle.of(activityInfo3.userId));
                                        activityInfo3.isVisible = true;
                                        activityInfo3.lastLaunchTimeMs = SystemClock.elapsedRealtime();
                                    } catch (Exception e2) {
                                        Slog.w(CarLog.TAG_AM, "Cannot start activity:" + activityInfo3.intent, e2);
                                    }
                                }
                            }
                        } else if (timeSinceLastLaunchMs >= CRASH_FORGET_INTERVAL_MS) {
                            activityInfo3.consecutiveRetries = i;
                        }
                        i4++;
                        i = 0;
                    }
                    RunningActivityInfo activityInfo4 = this.mRunningActivities.get(displayId);
                    if (activityInfo4 == null) {
                        return false;
                    }
                    return activityInfo4.isVisible;
                } catch (Throwable th) {
                    th = th;
                    throw th;
                }
            } catch (Throwable th2) {
                th = th2;
                throw th;
            }
        }
    }

    public /* synthetic */ void lambda$launchIfNecessary$2$FixedActivityService(int displayIdForActivity) {
        Display display = this.mDm.getDisplay(displayIdForActivity);
        if (display == null) {
            Slog.e(CarLog.TAG_AM, "Display not available, cannot launnch window:" + displayIdForActivity);
            return;
        }
        Presentation p = new Presentation(this.mContext, display, 16973834);
        p.setContentView(R.layout.activity_continuous_blank);
        p.show();
        synchronized (this.mLock) {
            this.mBlockingPresentations.append(displayIdForActivity, p);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* renamed from: launchIfNecessary */
    public void lambda$new$0$FixedActivityService() {
        launchIfNecessary(-1);
    }

    private void logComponentNotFound(ComponentName component, int userId, Exception e) {
        Slog.e(CarLog.TAG_AM, "Specified Component not found:" + component + " for userid:" + userId, e);
    }

    private boolean isComponentAvailable(ComponentName component, int userId) {
        ActivityInfo[] activityInfoArr;
        try {
            PackageInfo packageInfo = this.mContext.getPackageManager().getPackageInfoAsUser(component.getPackageName(), 1, userId);
            if (packageInfo == null || packageInfo.activities == null) {
                logComponentNotFound(component, userId, new RuntimeException());
                return false;
            }
            String fullName = component.getClassName();
            String shortName = component.getShortClassName();
            for (ActivityInfo info : packageInfo.activities) {
                if (info.name.equals(fullName) || info.name.equals(shortName)) {
                    return true;
                }
            }
            logComponentNotFound(component, userId, new RuntimeException());
            return false;
        } catch (PackageManager.NameNotFoundException e) {
            logComponentNotFound(component, userId, e);
            return false;
        }
    }

    private boolean isUserAllowedToLaunchActivity(int userId) {
        int currentUser = ActivityManager.getCurrentUser();
        if (userId == currentUser) {
            return true;
        }
        int[] profileIds = this.mUm.getEnabledProfileIds(currentUser);
        for (int id : profileIds) {
            if (id == userId) {
                return true;
            }
        }
        return false;
    }

    private boolean isDisplayAllowedForFixedMode(int displayId) {
        if (displayId == 0 || displayId == -1) {
            Slog.w(CarLog.TAG_AM, "Target display cannot be used for fixed mode, displayId:" + displayId, new RuntimeException());
            return false;
        }
        return true;
    }

    public boolean startFixedActivityModeForDisplayAndUser(Intent intent, ActivityOptions options, int displayId, int userId) {
        if (isDisplayAllowedForFixedMode(displayId)) {
            if (options == null) {
                Slog.e(CarLog.TAG_AM, "startFixedActivityModeForDisplayAndUser, null options");
                return false;
            } else if (!isUserAllowedToLaunchActivity(userId)) {
                Slog.e(CarLog.TAG_AM, "startFixedActivityModeForDisplayAndUser, requested user:" + userId + " cannot launch activity, Intent:" + intent);
                return false;
            } else {
                ComponentName component = intent.getComponent();
                if (component == null) {
                    Slog.e(CarLog.TAG_AM, "startFixedActivityModeForDisplayAndUser: No component specified for requested Intent" + intent);
                    return false;
                } else if (isComponentAvailable(component, userId)) {
                    boolean startMonitoringEvents = false;
                    synchronized (this.mLock) {
                        Presentation p = (Presentation) this.mBlockingPresentations.removeReturnOld(displayId);
                        if (p != null) {
                            p.dismiss();
                        }
                        if (this.mRunningActivities.size() == 0) {
                            startMonitoringEvents = true;
                        }
                        RunningActivityInfo activityInfo = this.mRunningActivities.get(displayId);
                        boolean replaceEntry = true;
                        if (activityInfo != null && activityInfo.intent.equals(intent) && options.equals(activityInfo.activityOptions) && userId == activityInfo.userId) {
                            replaceEntry = false;
                            if (activityInfo.isVisible) {
                                return true;
                            }
                        }
                        if (replaceEntry) {
                            this.mRunningActivities.put(displayId, new RunningActivityInfo(intent, options, userId));
                        }
                        boolean launched = launchIfNecessary(displayId);
                        if (!launched) {
                            synchronized (this.mLock) {
                                this.mRunningActivities.remove(displayId);
                            }
                        }
                        if (startMonitoringEvents && launched) {
                            startMonitoringEvents();
                        }
                        return launched;
                    }
                } else {
                    return false;
                }
            }
        }
        return false;
    }

    public void stopFixedActivityMode(int displayId) {
        if (!isDisplayAllowedForFixedMode(displayId)) {
            return;
        }
        boolean stopMonitoringEvents = false;
        synchronized (this.mLock) {
            this.mRunningActivities.remove(displayId);
            if (this.mRunningActivities.size() == 0) {
                stopMonitoringEvents = true;
            }
        }
        if (stopMonitoringEvents) {
            stopMonitoringEvents();
        }
    }
}
