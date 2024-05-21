package com.android.car;

import android.app.ActivityManager;
import android.car.hardware.power.ICarPower;
import android.car.hardware.power.ICarPowerStateListener;
import android.car.userlib.CarUserManagerHelper;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.media.AudioSystem;
import android.os.Build;
import android.os.FileUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManagerInternal;
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Slog;
import com.android.car.CarPowerManagementService;
import com.android.car.Manifest;
import com.android.car.am.ContinuousBlankActivity;
import com.android.car.hal.PowerHalService;
import com.android.car.systeminterface.SystemInterface;
import com.android.car.user.CarUserNoticeService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.xiaopeng.util.FeatureOption;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
/* loaded from: classes3.dex */
public class CarPowerManagementService extends ICarPower.Stub implements CarServiceBase, PowerHalService.PowerEventListener {
    private static final boolean DEBUG = false;
    private static final int DEEP_SLEEP_RETRY_INTERVAL_MS = 1000;
    private static final int DEEP_SLEEP_RETRY_MAX_MS = 60000;
    private static final int FACTORY_VERSION = 6;
    private static final String NAPA_INIT_FINISH = "init_finish";
    private static final long POWER_STATE_HANDLE_INTERVAL = 500;
    private static final String PROP_ENABLE_CPMS_POLLING = "android.car.enable_cpms_polling";
    private static final String PROP_MAX_GARAGE_MODE_DURATION_OVERRIDE = "android.car.garagemodeduration";
    private static final int SHUTDOWN_EXTEND_MAX_MS = 5000;
    private static final int SHUTDOWN_POLLING_INTERVAL_MS = 2000;
    private static final int UI_TYPE_3D = 2;
    private static final int UI_TYPE_UNKNOWN = -1;
    private static final long VBUS_DELAY_TIME = 10000;
    private static final String VBUS_GPIO = "/sys/devices/platform/soc/a600000.ssusb/vbus_power";
    private static final String VBUS_POWER_OFF = "0";
    private static final String VBUS_POWER_ON = "1";
    private boolean mAutoSuspendEnable;
    private final long mAutoSuspendTime;
    private boolean mBootComplete;
    private BroadcastReceiver mBootCompleteReceiver;
    private int mBootReason;
    private final CarUserManagerHelper mCarUserManagerHelper;
    private final Context mContext;
    @GuardedBy({"mLock"})
    private CpmsState mCurrentState;
    @GuardedBy({"this"})
    private Timer mDeepSleepTimer;
    @GuardedBy({"this"})
    private boolean mDeepSleepTimerActive;
    private final boolean mDisableUserSwitchDuringResume;
    private boolean mEnableDeepSleepRetry;
    private final PowerHalService mHal;
    private long mHandlePowerTime;
    @GuardedBy({"mLock"})
    private PowerHandler mHandler;
    @GuardedBy({"mLock"})
    private HandlerThread mHandlerThread;
    @GuardedBy({"mSimulationWaitObject"})
    private boolean mInSimulatedDeepSleepMode;
    @GuardedBy({"mLock"})
    private boolean mIsBooting;
    @GuardedBy({"mLock"})
    private boolean mIsResuming;
    private boolean mIsSilentOn;
    @GuardedBy({"mLock"})
    private long mLastSleepEntryTime;
    @GuardedBy({"mLock"})
    private final Set<IBinder> mListenersWeAreWaitingFor;
    private final Object mLock;
    private final int mMyPid;
    private boolean mNapaFinished;
    private final String mNewGuestName;
    @GuardedBy({"mLock"})
    private int mNextWakeupSec;
    @GuardedBy({"mLock"})
    private final LinkedList<CpmsState> mPendingPowerStates;
    private final Map<IBinder, PowerListenerInfo> mPowerListenerCompletionMap;
    private final PowerManagerCallbackList mPowerManagerListeners;
    private final PowerManagerCallbackList mPowerManagerListenersWithCompletion;
    @GuardedBy({"mLock"})
    private long mProcessingStartTime;
    @GuardedBy({"mLock"})
    private boolean mRebootAfterGarageMode;
    @GuardedBy({"mLock"})
    private boolean mShutdownOnFinish;
    @GuardedBy({"mLock"})
    private boolean mShutdownOnNextSuspend;
    private final Object mSimulationWaitObject;
    private final SystemInterface mSystemInterface;
    @GuardedBy({"mLock"})
    private Timer mTimer;
    @GuardedBy({"mLock"})
    private boolean mTimerActive;
    private int mUiType;
    private final UserManager mUserManager;
    @GuardedBy({"mSimulationWaitObject"})
    private boolean mWakeFromSimulatedSleep;
    private static final int MIN_MAX_GARAGE_MODE_DURATION_MS = 120000;
    private static int sShutdownPrepareTimeMs = MIN_MAX_GARAGE_MODE_DURATION_MS;

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class PowerManagerCallbackList extends RemoteCallbackList<ICarPowerStateListener> {
        private PowerManagerCallbackList() {
        }

        @Override // android.os.RemoteCallbackList
        public void onCallbackDied(ICarPowerStateListener listener) {
            Slog.i(CarLog.TAG_POWER, "binderDied " + listener.asBinder());
            CarPowerManagementService.this.doUnregisterListener(listener);
        }
    }

    public CarPowerManagementService(Context context, PowerHalService powerHal, SystemInterface systemInterface, CarUserManagerHelper carUserManagerHelper) {
        this(context, context.getResources(), powerHal, systemInterface, carUserManagerHelper, UserManager.get(context), context.getString(R.string.default_guest_name));
    }

    @VisibleForTesting
    CarPowerManagementService(Context context, Resources resources, PowerHalService powerHal, SystemInterface systemInterface, CarUserManagerHelper carUserManagerHelper, UserManager userManager, String newGuestName) {
        this.mLock = new Object();
        this.mSimulationWaitObject = new Object();
        this.mPowerManagerListeners = new PowerManagerCallbackList();
        this.mPowerManagerListenersWithCompletion = new PowerManagerCallbackList();
        this.mPowerListenerCompletionMap = new ConcurrentHashMap(8);
        this.mMyPid = Process.myPid();
        this.mListenersWeAreWaitingFor = new HashSet();
        this.mPendingPowerStates = new LinkedList<>();
        this.mIsBooting = true;
        this.mEnableDeepSleepRetry = false;
        this.mBootComplete = false;
        this.mAutoSuspendEnable = false;
        this.mNapaFinished = false;
        this.mIsSilentOn = true;
        this.mUiType = -1;
        this.mBootCompleteReceiver = new BroadcastReceiver() { // from class: com.android.car.CarPowerManagementService.1
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context2, Intent intent) {
                String action = intent.getAction();
                if (!"android.intent.action.BOOT_COMPLETED".equals(action)) {
                    return;
                }
                CarPowerManagementService.this.mHandler.handleBootComplete();
                CarPowerManagementService.this.mHandler.cancelProcessingComplete();
                CarPowerManagementService.this.mHandler.handlePowerStateChange();
            }
        };
        if (6 == SystemProperties.getInt("ro.xiaopeng.special", 1)) {
            this.mAutoSuspendTime = SystemProperties.getLong("sys.autosuspend.time", 4000L);
        } else {
            this.mAutoSuspendTime = SystemProperties.getLong("sys.autosuspend.time", 120000L);
        }
        this.mContext = context;
        this.mHal = powerHal;
        this.mSystemInterface = systemInterface;
        this.mCarUserManagerHelper = carUserManagerHelper;
        this.mUserManager = userManager;
        this.mDisableUserSwitchDuringResume = resources.getBoolean(R.bool.config_disableUserSwitchDuringResume);
        sShutdownPrepareTimeMs = resources.getInteger(R.integer.maxGarageModeRunningDurationInSecs) * 1000;
        if (sShutdownPrepareTimeMs < MIN_MAX_GARAGE_MODE_DURATION_MS) {
            Slog.w(CarLog.TAG_POWER, "maxGarageModeRunningDurationInSecs smaller than minimum required, resource:" + sShutdownPrepareTimeMs + "(ms) while should exceed:" + MIN_MAX_GARAGE_MODE_DURATION_MS + "(ms), Ignore resource.");
            sShutdownPrepareTimeMs = MIN_MAX_GARAGE_MODE_DURATION_MS;
        }
        this.mEnableDeepSleepRetry = this.mContext.getResources().getBoolean(R.bool.enableDeepSleepRetry);
        this.mNewGuestName = newGuestName;
    }

    @VisibleForTesting
    protected static void setShutdownPrepareTimeout(int timeoutMs) {
        if (timeoutMs < SHUTDOWN_EXTEND_MAX_MS) {
            sShutdownPrepareTimeMs = SHUTDOWN_EXTEND_MAX_MS;
        } else {
            sShutdownPrepareTimeMs = timeoutMs;
        }
    }

    @VisibleForTesting
    protected HandlerThread getHandlerThread() {
        HandlerThread handlerThread;
        synchronized (this.mLock) {
            handlerThread = this.mHandlerThread;
        }
        return handlerThread;
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
        synchronized (this.mLock) {
            this.mHandlerThread = new HandlerThread(CarLog.TAG_POWER);
            this.mHandlerThread.start();
            this.mHandler = new PowerHandler(this.mHandlerThread.getLooper());
        }
        this.mContext.registerReceiver(this.mBootCompleteReceiver, new IntentFilter("android.intent.action.BOOT_COMPLETED"));
        int systemStartCount = SystemProperties.getInt("sys.system_server.start_count", 0);
        int carServiceCount = SystemProperties.getInt("sys.car_service.start_count", 0);
        if (VBUS_POWER_ON.equals(SystemProperties.get("sys.boot_completed")) && (carServiceCount > systemStartCount || systemStartCount == 1)) {
            this.mHandler.handleBootComplete();
        }
        this.mHal.setListener(this);
        if (this.mHal.isPowerStateSupported()) {
            if (isShouldSleep()) {
                onApPowerStateChange(new PowerHalService.PowerState(1, 2));
            } else {
                onApPowerStateChange(this.mHal.getCurrentPowerState());
            }
        } else {
            Slog.w(CarLog.TAG_POWER, "Vehicle hal does not support power state yet.");
            onApPowerStateChange(1, 6);
        }
        this.mSystemInterface.startDisplayStateMonitoring(this);
    }

    @Override // com.android.car.CarServiceBase
    public void release() {
        HandlerThread handlerThread;
        synchronized (this.mLock) {
            releaseTimerLocked();
            this.mCurrentState = null;
            this.mHandler.cancelAll();
            handlerThread = this.mHandlerThread;
            this.mListenersWeAreWaitingFor.clear();
        }
        handlerThread.quitSafely();
        try {
            handlerThread.join(1000L);
        } catch (InterruptedException e) {
            Slog.e(CarLog.TAG_POWER, "Timeout while joining for handler thread to join.");
        }
        this.mContext.unregisterReceiver(this.mBootCompleteReceiver);
        this.mSystemInterface.stopDisplayStateMonitoring();
        this.mPowerManagerListeners.kill();
        this.mSystemInterface.releaseAllWakeLocks();
    }

    @Override // com.android.car.CarServiceBase
    public void dump(final PrintWriter writer) {
        writer.println("*PowerManagementService*");
        writer.print("mCurrentState:" + this.mCurrentState);
        writer.print(",mProcessingStartTime:" + this.mProcessingStartTime);
        writer.print(",mLastSleepEntryTime:" + this.mLastSleepEntryTime);
        writer.print(",mNextWakeupSec:" + this.mNextWakeupSec);
        writer.print(",mShutdownOnNextSuspend:" + this.mShutdownOnNextSuspend);
        writer.print(",mShutdownOnFinish:" + this.mShutdownOnFinish);
        writer.print(",sShutdownPrepareTimeMs:" + sShutdownPrepareTimeMs);
        writer.print(",mDisableUserSwitchDuringResume:" + this.mDisableUserSwitchDuringResume);
        writer.println(",mRebootAfterGarageMode:" + this.mRebootAfterGarageMode);
        writer.print("mNewGuestName: ");
        writer.println(this.mNewGuestName);
        writer.println("mPowerListenersCompletion: " + this.mPowerListenerCompletionMap.size());
        this.mPowerListenerCompletionMap.entrySet().forEach(new Consumer() { // from class: com.android.car.-$$Lambda$CarPowerManagementService$bqYXvXJZ3giTJq2plTQjSbRqVH8
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                CarPowerManagementService.lambda$dump$0(writer, (Map.Entry) obj);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ void lambda$dump$0(PrintWriter writer, Map.Entry pc) {
        PowerListenerInfo info = (PowerListenerInfo) pc.getValue();
        writer.println("    process: " + info.processName + ", className: " + info.className);
    }

    @Override // com.android.car.hal.PowerHalService.PowerEventListener
    public void onApPowerStateChange(PowerHalService.PowerState state) {
        PowerHandler handler;
        synchronized (this.mLock) {
            this.mPendingPowerStates.addFirst(new CpmsState(state));
            if (state.mState != 1) {
                this.mLock.notifyAll();
            }
            handler = this.mHandler;
        }
        handler.handlePowerStateChange();
    }

    @VisibleForTesting
    void setStateForTesting(boolean isBooting, boolean isResuming) {
        synchronized (this.mLock) {
            Slog.d(CarLog.TAG_POWER, "setStateForTesting(): booting(" + this.mIsBooting + ">" + isBooting + ") resuming(" + this.mIsResuming + ">" + isResuming + ")");
            this.mIsBooting = isBooting;
            this.mIsResuming = isResuming;
        }
    }

    private void onApPowerStateChange(int apState, int carPowerStateListenerState) {
        PowerHandler handler;
        CpmsState newState = new CpmsState(apState, carPowerStateListenerState);
        synchronized (this.mLock) {
            this.mPendingPowerStates.addFirst(newState);
            handler = this.mHandler;
        }
        handler.handlePowerStateChange();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void doHandlePowerStateChange() {
        synchronized (this.mLock) {
            CpmsState state = this.mPendingPowerStates.peekFirst();
            this.mPendingPowerStates.clear();
            if (state == null) {
                return;
            }
            if (needPowerStateChangeLocked(state)) {
                Slog.i(CarLog.TAG_POWER, "doHandlePowerStateChange: newState=" + state.name());
                releaseTimerLocked();
                PowerHandler handler = this.mHandler;
                this.mHandlePowerTime = SystemClock.elapsedRealtime();
                handler.cancelProcessingComplete();
                Slog.i(CarLog.TAG_POWER, "setCurrentState " + state.toString());
                CarStatsLog.logPowerState(state.mState);
                this.mCurrentState = state;
                switch (state.mState) {
                    case 0:
                        handleWaitForVhal(state);
                        return;
                    case 1:
                        handleOn();
                        if (this.mIsSilentOn) {
                            writeBootSilentStatus("/sys/xpeng/cluster/cluster_status", "silence_off");
                            this.mSystemInterface.setDisplayState("xp_mt_ivi", 2, true);
                            if (PowerManagerInternal.IS_HAS_PASSENGER) {
                                this.mSystemInterface.setDisplayState("xp_mt_psg", 2, true);
                            }
                            this.mIsSilentOn = false;
                            return;
                        }
                        return;
                    case 2:
                        handleShutdownPrepare(state);
                        return;
                    case 3:
                        handleWaitForFinish(state);
                        return;
                    case 4:
                        handleFinish();
                        return;
                    case 5:
                        simulateShutdownPrepare();
                        return;
                    case 6:
                        handleOnScreenOff();
                        return;
                    default:
                        return;
                }
            }
        }
    }

    private void handleWaitForVhal(CpmsState state) {
        int carPowerStateListenerState = state.mCarPowerStateListenerState;
        sendPowerManagerEvent(carPowerStateListenerState);
        if (carPowerStateListenerState == 1) {
            this.mHal.sendWaitForVhal();
        } else if (carPowerStateListenerState == 3) {
            this.mHal.sendSleepExit();
        } else if (carPowerStateListenerState == 8) {
            this.mShutdownOnNextSuspend = false;
            this.mHal.sendShutdownCancel();
        }
    }

    private void updateCarUserNoticeServiceIfNecessary() {
        try {
            int currentUserId = ActivityManager.getCurrentUser();
            UserInfo currentUserInfo = this.mUserManager.getUserInfo(currentUserId);
            CarUserNoticeService carUserNoticeService = (CarUserNoticeService) CarLocalServices.getService(CarUserNoticeService.class);
            if (currentUserInfo != null && currentUserInfo.isGuest() && carUserNoticeService != null) {
                Slog.i(CarLog.TAG_POWER, "Car user notice service will ignore all messages before user switch.");
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(this.mContext.getPackageName(), ContinuousBlankActivity.class.getName()));
                intent.addFlags(268435456);
                this.mContext.startActivityAsUser(intent, UserHandle.CURRENT);
                carUserNoticeService.ignoreUserNotice(currentUserId);
            }
        } catch (Exception e) {
            Slog.w(CarLog.TAG_POWER, "Cannot ignore user notice for current user", e);
        }
    }

    private void handleOn() {
        boolean allowUserSwitch;
        synchronized (this) {
            exitSuspend();
        }
        releaseDeepSleepTimerLocked();
        updateCarUserNoticeServiceIfNecessary();
        synchronized (this.mLock) {
            if (this.mIsBooting) {
                allowUserSwitch = false;
                this.mIsBooting = false;
                this.mIsResuming = false;
                Slog.i(CarLog.TAG_POWER, "User switch disallowed while booting");
            } else {
                allowUserSwitch = !this.mDisableUserSwitchDuringResume;
                this.mIsBooting = false;
                this.mIsResuming = false;
                if (!allowUserSwitch) {
                    Slog.i(CarLog.TAG_POWER, "User switch disallowed while resuming");
                }
            }
        }
        onBootReasonReceived(4);
        SystemProperties.set("sys.xiaopeng.power_state", String.valueOf(0));
        this.mSystemInterface.setDisplayState(true);
        sendPowerManagerEvent(6);
        this.mHal.sendOn();
        try {
            switchUserOnResumeIfNecessary(allowUserSwitch);
        } catch (Exception e) {
            Slog.e(CarLog.TAG_POWER, "Could not switch user on resume: " + e);
        }
    }

    private void handleOnScreenOff() {
        synchronized (this) {
            exitSuspend();
        }
        releaseDeepSleepTimerLocked();
        onBootReasonReceived(7);
        SystemProperties.set("sys.xiaopeng.power_state", String.valueOf(1));
        this.mSystemInterface.setDisplayState(false);
        sendPowerManagerEvent(9);
    }

    private void switchUserOnResumeIfNecessary(boolean allowSwitching) {
        int targetUserId = this.mCarUserManagerHelper.getInitialUser();
        if (targetUserId == 0) {
            Slog.e(CarLog.TAG_POWER, "getInitialUser() returned system user");
            return;
        }
        int currentUserId = ActivityManager.getCurrentUser();
        UserInfo targetUserInfo = this.mUserManager.getUserInfo(targetUserId);
        boolean isTargetPersistent = !targetUserInfo.isEphemeral();
        boolean isTargetGuest = targetUserInfo.isGuest();
        Slog.d(CarLog.TAG_POWER, "getTargetUserId(): current=" + currentUserId + ", target=" + targetUserInfo.toFullString() + ", isTargetPersistent=" + isTargetPersistent + ", isTargetGuest=" + isTargetGuest + ", allowSwitching: " + allowSwitching);
        if (isTargetPersistent && !isTargetGuest) {
            if (!allowSwitching) {
                Slog.d(CarLog.TAG_POWER, "Not switching to " + targetUserId + " because it's not allowed");
            } else if (currentUserId == targetUserId) {
                Slog.v(CarLog.TAG_POWER, "no need to switch to (same user) " + currentUserId);
            } else {
                switchToUser(currentUserId, targetUserId, null);
            }
        } else if (!isTargetGuest) {
            Slog.w(CarLog.TAG_POWER, "target user is ephemeral but not a guest: " + targetUserInfo.toFullString());
            if (allowSwitching) {
                switchToUser(currentUserId, targetUserId, null);
            }
        } else {
            if (isTargetPersistent) {
                Slog.w(CarLog.TAG_POWER, "target user is a non-ephemeral guest: " + targetUserInfo.toFullString());
            }
            boolean marked = this.mUserManager.markGuestForDeletion(targetUserId);
            if (!marked) {
                Slog.w(CarLog.TAG_POWER, "Could not mark guest user " + targetUserId + " for deletion");
                return;
            }
            UserInfo newGuest = this.mUserManager.createGuest(this.mContext, this.mNewGuestName);
            if (newGuest == null) {
                Slog.e(CarLog.TAG_POWER, "Could not create new guest");
                return;
            }
            switchToUser(currentUserId, newGuest.id, "Created new guest");
            Slog.d(CarLog.TAG_POWER, "Removing previous guest " + targetUserId);
            this.mUserManager.removeUser(targetUserId);
        }
    }

    private void switchToUser(int fromUser, int toUser, String reason) {
        StringBuilder message = new StringBuilder();
        if (reason == null) {
            message.append("Desired user changed");
        } else {
            message.append(reason);
        }
        message.append(", switching from ");
        message.append(fromUser);
        message.append(" to ");
        message.append(toUser);
        Slog.i(CarLog.TAG_POWER, message.toString());
        this.mCarUserManagerHelper.switchToUserId(toUser);
    }

    private int getFirstSwitchableUser() {
        List<UserInfo> allUsers = this.mUserManager.getUsers();
        for (UserInfo user : allUsers) {
            if (user.id != 0) {
                return user.id;
            }
        }
        Slog.e(CarLog.TAG_POWER, "no switchable user: " + allUsers);
        return -10000;
    }

    private void handleShutdownPrepare(CpmsState newState) {
        boolean z;
        SystemProperties.set("sys.xiaopeng.power_state", String.valueOf(2));
        if (!newState.mCanPostpone && this.mHal.getBatteryState() == 4) {
            dumpInfo();
        }
        this.mSystemInterface.setDisplayState(false);
        synchronized (this.mLock) {
            if (!this.mShutdownOnNextSuspend && this.mHal.isDeepSleepAllowed() && this.mSystemInterface.isSystemSupportingDeepSleep() && newState.mCanSleep) {
                z = false;
                this.mShutdownOnFinish = z;
            }
            z = true;
            this.mShutdownOnFinish = z;
        }
        if (newState.mCanPostpone) {
            Slog.i(CarLog.TAG_POWER, "starting shutdown prepare");
            sendPowerManagerEvent(7);
            this.mHal.sendShutdownPrepare();
            doHandlePreprocessing();
            return;
        }
        Slog.i(CarLog.TAG_POWER, "starting shutdown immediately");
        synchronized (this.mLock) {
            releaseTimerLocked();
        }
        try {
            long startTime = SystemClock.elapsedRealtime();
            Thread.sleep(3000L);
            for (long rem = 8000 - (SystemClock.elapsedRealtime() - startTime); rem >= 0; rem = 8000 - (SystemClock.elapsedRealtime() - startTime)) {
                if (SystemProperties.getBoolean("sys.xp.dumpcoreinfo.end", false)) {
                    break;
                }
                Thread.sleep(200L);
            }
        } catch (Exception e) {
            Slog.w(CarLog.TAG_POWER, "dumpcoreinfo end: " + e.getMessage());
        }
        this.mHal.sendShutdownStart(0);
        this.mSystemInterface.shutdown();
    }

    private void dumpInfo() {
        try {
            FileUtils.stringToFile("/sys/touch_debug/is_trigger", "s");
            long startTime = SystemClock.elapsedRealtime();
            Thread.sleep(2000L);
            for (long rem = 6000 - (SystemClock.elapsedRealtime() - startTime); rem >= 0; rem = 6000 - (SystemClock.elapsedRealtime() - startTime)) {
                if (!SystemProperties.getBoolean("sys.xp.dumpcoreinfo.info", false)) {
                    Thread.sleep(100L);
                } else {
                    return;
                }
            }
        } catch (Exception e) {
            Slog.w(CarLog.TAG_POWER, "dumpcoreinfo: " + e.getMessage());
        }
    }

    private void simulateShutdownPrepare() {
        this.mSystemInterface.setDisplayState(false);
        Slog.i(CarLog.TAG_POWER, "starting shutdown prepare");
        sendPowerManagerEvent(7);
        this.mHal.sendShutdownPrepare();
        doHandlePreprocessing();
    }

    private void handleWaitForFinish(CpmsState state) {
        int wakeupSec;
        sendPowerManagerEvent(state.mCarPowerStateListenerState);
        synchronized (this.mLock) {
            wakeupSec = this.mNextWakeupSec;
        }
        int i = state.mCarPowerStateListenerState;
        if (i == 2) {
            this.mHal.sendSleepEntry(wakeupSec);
        } else if (i == 5) {
            this.mHal.sendShutdownStart(wakeupSec);
        }
    }

    private void handleFinish() {
        boolean simulatedMode;
        boolean mustShutDown;
        boolean forceReboot;
        synchronized (this.mSimulationWaitObject) {
            simulatedMode = this.mInSimulatedDeepSleepMode;
        }
        synchronized (this.mLock) {
            mustShutDown = this.mShutdownOnFinish && !simulatedMode;
            forceReboot = this.mRebootAfterGarageMode;
            this.mRebootAfterGarageMode = false;
        }
        if (forceReboot) {
            PowerManager powerManager = (PowerManager) this.mContext.getSystemService(PowerManager.class);
            if (powerManager == null) {
                Slog.e(CarLog.TAG_POWER, "No PowerManager. Cannot reboot.");
            } else {
                Slog.i(CarLog.TAG_POWER, "GarageMode has completed. Forcing reboot.");
                powerManager.reboot("GarageModeReboot");
                throw new AssertionError("Should not return from PowerManager.reboot()");
            }
        }
        if (mustShutDown) {
            this.mSystemInterface.shutdown();
        } else {
            doHandleDeepSleep(simulatedMode);
        }
        this.mShutdownOnNextSuspend = false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    @GuardedBy({"mLock"})
    public void releaseTimerLocked() {
        Timer timer = this.mTimer;
        if (timer != null) {
            timer.cancel();
        }
        this.mTimer = null;
        this.mTimerActive = false;
    }

    private void doHandlePreprocessing() {
        int shutdownPrepareTimeOverrideInSecs;
        boolean enablePolling = SystemProperties.getBoolean(PROP_ENABLE_CPMS_POLLING, true);
        int pollingCount = (sShutdownPrepareTimeMs / SHUTDOWN_POLLING_INTERVAL_MS) + 1;
        if ((Build.IS_USERDEBUG || Build.IS_ENG) && (shutdownPrepareTimeOverrideInSecs = SystemProperties.getInt(PROP_MAX_GARAGE_MODE_DURATION_OVERRIDE, -1)) >= 0) {
            pollingCount = ((shutdownPrepareTimeOverrideInSecs * 1000) / SHUTDOWN_POLLING_INTERVAL_MS) + 1;
            Slog.i(CarLog.TAG_POWER, "Garage mode duration overridden secs:" + shutdownPrepareTimeOverrideInSecs);
        }
        Slog.i(CarLog.TAG_POWER, "processing before shutdown expected for: " + sShutdownPrepareTimeMs + " ms, adding polling:" + pollingCount);
        synchronized (this.mLock) {
            this.mProcessingStartTime = SystemClock.elapsedRealtime();
            releaseTimerLocked();
            if (enablePolling) {
                this.mTimer = new Timer();
                this.mTimerActive = true;
                this.mTimer.scheduleAtFixedRate(new ShutdownProcessingTimerTask(pollingCount), 0L, 2000L);
            } else {
                Slog.w(CarLog.TAG_POWER, "Disable shutdown polling");
            }
        }
    }

    private void sendPowerManagerEvent(int newState) {
        notifyListeners(this.mPowerManagerListeners, newState);
        boolean allowCompletion = newState == 7;
        boolean haveSomeCompleters = false;
        PowerManagerCallbackList completingListeners = new PowerManagerCallbackList();
        synchronized (this.mLock) {
            this.mListenersWeAreWaitingFor.clear();
            int idx = this.mPowerManagerListenersWithCompletion.beginBroadcast();
            while (true) {
                int idx2 = idx - 1;
                if (idx <= 0) {
                    break;
                }
                ICarPowerStateListener listener = this.mPowerManagerListenersWithCompletion.getBroadcastItem(idx2);
                completingListeners.register(listener);
                if (allowCompletion) {
                    this.mListenersWeAreWaitingFor.add(listener.asBinder());
                    haveSomeCompleters = true;
                }
                idx = idx2;
            }
            this.mPowerManagerListenersWithCompletion.finishBroadcast();
        }
        notifyListeners(completingListeners, newState);
        completingListeners.kill();
        if (allowCompletion && !haveSomeCompleters) {
            signalComplete();
        }
    }

    private void notifyListeners(PowerManagerCallbackList listenerList, int newState) {
        int idx = listenerList.beginBroadcast();
        while (true) {
            int idx2 = idx - 1;
            if (idx > 0) {
                ICarPowerStateListener listener = listenerList.getBroadcastItem(idx2);
                try {
                    listener.onStateChanged(newState);
                } catch (RemoteException e) {
                    Slog.e(CarLog.TAG_POWER, "onStateChanged() call failed", e);
                }
                idx = idx2;
            } else {
                listenerList.finishBroadcast();
                return;
            }
        }
    }

    private void doHandleDeepSleep(boolean simulatedMode) {
        PowerHandler handler;
        this.mSystemInterface.switchToPartialWakeLock();
        synchronized (this.mLock) {
            handler = this.mHandler;
        }
        handler.cancelProcessingComplete();
        synchronized (this.mLock) {
            this.mLastSleepEntryTime = SystemClock.elapsedRealtime();
        }
        if (simulatedMode) {
            simulateSleepByWaiting();
            synchronized (this.mLock) {
                this.mIsResuming = true;
                this.mNextWakeupSec = 0;
            }
            Slog.i(CarLog.TAG_POWER, "Resuming after suspending");
            this.mSystemInterface.refreshDisplayBrightness();
            onApPowerStateChange(0, 8);
            return;
        }
        this.mAutoSuspendEnable = true;
        this.mSystemInterface.setAutoSuspendEnable(true);
        this.mHandler.handleDeepSleep(this.mAutoSuspendTime);
        writeVbusGpio(VBUS_POWER_OFF);
        if (isShouldSleep()) {
            this.mSystemInterface.releaseAllWakeLocks();
        }
    }

    private boolean needPowerStateChangeLocked(CpmsState newState) {
        if (newState == null) {
            return false;
        }
        CpmsState cpmsState = this.mCurrentState;
        if (cpmsState == null) {
            return true;
        }
        if (cpmsState.equals(newState)) {
            return false;
        }
        switch (this.mCurrentState.mState) {
            case 0:
                return newState.mState == 1 || newState.mState == 2;
            case 1:
                return newState.mState == 2 || newState.mState == 6 || newState.mState == 5;
            case 2:
                return (newState.mState == 2 && !newState.mCanPostpone) || newState.mState == 1 || newState.mState == 6 || newState.mState == 3 || newState.mState == 0;
            case 3:
                return newState.mState == 4;
            case 4:
                return newState.mState == 0;
            case 5:
                return true;
            case 6:
                return newState.mState == 2 || newState.mState == 1 || newState.mState == 5;
            default:
                Slog.e(CarLog.TAG_POWER, "Unhandled state transition:  currentState=" + this.mCurrentState.name() + ", newState=" + newState.name());
                return false;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void doHandleProcessingComplete() {
        synchronized (this.mLock) {
            releaseTimerLocked();
            if (!this.mShutdownOnFinish && this.mLastSleepEntryTime > this.mProcessingStartTime) {
                Slog.w(CarLog.TAG_POWER, "Duplicate sleep entry request, ignore");
                return;
            }
            int listenerState = this.mShutdownOnFinish ? 5 : 2;
            sendPowerManagerEvent(listenerState);
            waitTime(5000L);
            if (isShouldSleep()) {
                this.mHal.sendSleepEntry(0);
                AudioSystem.setParameters("PMSleep=true");
                waitTime(5000L);
            }
            handleFinish();
        }
    }

    public int getBootReason() {
        ICarImpl.assertPermission(this.mContext, Manifest.permission.CAR_POWER);
        Slog.i(CarLog.TAG_POWER, "bootup reason: " + this.mBootReason);
        return this.mBootReason;
    }

    private void onBootReasonReceived(int bootReason) {
        this.mBootReason = bootReason;
    }

    @Override // com.android.car.hal.PowerHalService.PowerEventListener
    public void onDisplayBrightnessChange(int brightness) {
        PowerHandler handler;
        synchronized (this.mLock) {
            handler = this.mHandler;
        }
        handler.handleDisplayBrightnessChange(brightness);
    }

    @Override // com.android.car.hal.PowerHalService.PowerEventListener
    public void onDisplayChange(String deviceName, int silenceState, boolean isOn) {
        PowerHandler handler;
        synchronized (this.mLock) {
            handler = this.mHandler;
        }
        handler.handleDisplayChange(deviceName, silenceState, isOn);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void doHandleDisplayBrightnessChange(int brightness) {
        this.mSystemInterface.setDisplayBrightness(brightness);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void doHandleMainDisplayStateChange(boolean on) {
        Slog.w(CarLog.TAG_POWER, "Unimplemented:  doHandleMainDisplayStateChange() - on = " + on);
    }

    public void handleMainDisplayChanged(boolean on) {
        PowerHandler handler;
        synchronized (this.mLock) {
            handler = this.mHandler;
        }
        handler.handleMainDisplayStateChange(on);
    }

    public void sendDisplayBrightness(int brightness) {
        this.mHal.sendDisplayBrightness(brightness);
    }

    public Handler getHandler() {
        PowerHandler powerHandler;
        synchronized (this.mLock) {
            powerHandler = this.mHandler;
        }
        return powerHandler;
    }

    public void registerListener(ICarPowerStateListener listener) {
        ICarImpl.assertPermission(this.mContext, Manifest.permission.CAR_POWER);
        this.mPowerManagerListeners.register(listener);
    }

    public void registerListenerWithCompletion(ICarPowerStateListener listener) {
        StackTraceElement[] stackTraceElements;
        ICarImpl.assertPermission(this.mContext, Manifest.permission.CAR_POWER);
        ICarImpl.assertCallingFromSystemProcessOrSelf();
        this.mPowerManagerListenersWithCompletion.register(listener);
        if (!Build.IS_USER || CarLog.isGetLogEnable()) {
            int pid = getCallingPid();
            int uid = getCallingUid();
            String processName = ProcessUtils.getProcessName(this.mContext, pid, uid);
            String className = "unknonw";
            if (pid == this.mMyPid && (stackTraceElements = Thread.currentThread().getStackTrace()) != null && stackTraceElements.length > 5) {
                className = stackTraceElements[5].getClassName();
            }
            this.mPowerListenerCompletionMap.put(listener.asBinder(), new PowerListenerInfo(processName, className));
        }
    }

    public void unregisterListener(ICarPowerStateListener listener) {
        ICarImpl.assertPermission(this.mContext, Manifest.permission.CAR_POWER);
        doUnregisterListener(listener);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void doUnregisterListener(ICarPowerStateListener listener) {
        this.mPowerManagerListeners.unregister(listener);
        boolean found = this.mPowerManagerListenersWithCompletion.unregister(listener);
        if (found) {
            if (!Build.IS_USER || CarLog.isGetLogEnable()) {
                this.mPowerListenerCompletionMap.remove(listener.asBinder());
            }
            finishedImpl(listener.asBinder());
        }
    }

    public void requestShutdownOnNextSuspend() {
        ICarImpl.assertPermission(this.mContext, Manifest.permission.CAR_POWER);
        synchronized (this.mLock) {
            this.mShutdownOnNextSuspend = true;
        }
    }

    public void finished(ICarPowerStateListener listener) {
        ICarImpl.assertPermission(this.mContext, Manifest.permission.CAR_POWER);
        ICarImpl.assertCallingFromSystemProcessOrSelf();
        finishedImpl(listener.asBinder());
    }

    public void scheduleNextWakeupTime(int seconds) {
        if (seconds < 0) {
            Slog.w(CarLog.TAG_POWER, "Next wake up time is negative. Ignoring!");
            return;
        }
        boolean timedWakeupAllowed = this.mHal.isTimedWakeupAllowed();
        synchronized (this.mLock) {
            if (!timedWakeupAllowed) {
                Slog.w(CarLog.TAG_POWER, "Setting timed wakeups are disabled in HAL. Skipping");
                this.mNextWakeupSec = 0;
                return;
            }
            if (this.mNextWakeupSec != 0 && this.mNextWakeupSec <= seconds) {
                Slog.d(CarLog.TAG_POWER, "Tried to schedule next wake up, but already had shorter scheduled time");
            }
            this.mNextWakeupSec = seconds;
        }
    }

    private void finishedImpl(IBinder binder) {
        boolean allAreComplete;
        synchronized (this.mLock) {
            boolean oneWasRemoved = this.mListenersWeAreWaitingFor.remove(binder);
            allAreComplete = oneWasRemoved && this.mListenersWeAreWaitingFor.isEmpty();
        }
        if (allAreComplete) {
            signalComplete();
        }
    }

    private void signalComplete() {
        if (this.mCurrentState.mState == 2 || this.mCurrentState.mState == 5) {
            synchronized (this.mLock) {
                if (!this.mShutdownOnFinish && this.mLastSleepEntryTime > this.mProcessingStartTime && this.mLastSleepEntryTime < SystemClock.elapsedRealtime()) {
                    Slog.i(CarLog.TAG_POWER, "signalComplete: Already slept!");
                    return;
                }
                PowerHandler powerHandler = this.mHandler;
                Slog.i(CarLog.TAG_POWER, "Apps are finished, call handleProcessingComplete()");
                powerHandler.handleProcessingComplete();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class PowerHandler extends Handler {
        private static final int CHECK_MAX_NAPA_COUNT = 60;
        private static final long CHECK_NAPA_INTERVAL = 1000;
        private final long MAIN_DISPLAY_EVENT_DELAY_MS;
        private final int MSG_BOOT_COMPLETE;
        private final int MSG_CHECK_NAPA_FINISH;
        private final int MSG_DEEP_SLEEP;
        private final int MSG_DISPLAY_BRIGHTNESS_CHANGE;
        private final int MSG_DISPLAY_CHANGE;
        private final int MSG_MAIN_DISPLAY_STATE_CHANGE;
        private final int MSG_POWER_STATE_CHANGE;
        private final int MSG_PROCESSING_COMPLETE;
        private int mCheckNapaCount;

        private PowerHandler(Looper looper) {
            super(looper);
            this.MSG_POWER_STATE_CHANGE = 0;
            this.MSG_DISPLAY_BRIGHTNESS_CHANGE = 1;
            this.MSG_MAIN_DISPLAY_STATE_CHANGE = 2;
            this.MSG_PROCESSING_COMPLETE = 3;
            this.MSG_BOOT_COMPLETE = 4;
            this.MSG_DEEP_SLEEP = 5;
            this.MSG_DISPLAY_CHANGE = 6;
            this.MSG_CHECK_NAPA_FINISH = 7;
            this.mCheckNapaCount = 0;
            this.MAIN_DISPLAY_EVENT_DELAY_MS = CarPowerManagementService.POWER_STATE_HANDLE_INTERVAL;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void handlePowerStateChange() {
            Message msg = obtainMessage(0);
            sendMessage(msg);
        }

        private void handlePowerStateChange(long time) {
            Message msg = obtainMessage(0);
            removeMessages(0);
            sendMessageDelayed(msg, time);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void handleDisplayBrightnessChange(int brightness) {
            Message msg = obtainMessage(1, brightness, 0);
            sendMessage(msg);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void handleDisplayChange(String deviceName, int silenceState, boolean isOn) {
            Message msg = obtainMessage(6, silenceState, isOn ? 1 : 0, deviceName);
            removeMessages(6);
            sendMessage(msg);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void handleMainDisplayStateChange(boolean on) {
            removeMessages(2);
            Message msg = obtainMessage(2, Boolean.valueOf(on));
            sendMessageDelayed(msg, CarPowerManagementService.POWER_STATE_HANDLE_INTERVAL);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void handleProcessingComplete() {
            removeMessages(3);
            Message msg = obtainMessage(3);
            sendMessage(msg);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void handleBootComplete() {
            sendEmptyMessage(4);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void cancelProcessingComplete() {
            removeMessages(3);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void cancelDeepSleep() {
            removeMessages(5);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void handleDeepSleep(long time) {
            cancelDeepSleep();
            Message msg = obtainMessage(5);
            sendMessageDelayed(msg, time);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void cancelAll() {
            removeMessages(0);
            removeMessages(1);
            removeMessages(2);
            removeMessages(3);
            removeMessages(4);
            removeMessages(5);
            removeMessages(6);
        }

        private void checkNapaFinished() {
            removeMessages(7);
            sendEmptyMessageDelayed(7, CHECK_NAPA_INTERVAL);
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    if (CarPowerManagementService.this.mBootComplete && CarPowerManagementService.this.needHandlePowerStateChange()) {
                        long intervalTime = SystemClock.elapsedRealtime() - CarPowerManagementService.this.mHandlePowerTime;
                        if (intervalTime >= CarPowerManagementService.POWER_STATE_HANDLE_INTERVAL) {
                            CarPowerManagementService.this.doHandlePowerStateChange();
                            return;
                        } else {
                            handlePowerStateChange(CarPowerManagementService.POWER_STATE_HANDLE_INTERVAL - intervalTime);
                            return;
                        }
                    }
                    Slog.i(CarLog.TAG_POWER, "system not boot complete or napa didn't not init finished");
                    CarPowerManagementService.this.handleBootSilent();
                    return;
                case 1:
                    CarPowerManagementService.this.doHandleDisplayBrightnessChange(msg.arg1);
                    return;
                case 2:
                    CarPowerManagementService.this.doHandleMainDisplayStateChange(((Boolean) msg.obj).booleanValue());
                    return;
                case 3:
                    CarPowerManagementService.this.doHandleProcessingComplete();
                    return;
                case 4:
                    Slog.i(CarLog.TAG_POWER, "system boot complete!!!");
                    if (CarPowerManagementService.this.is3DUi()) {
                        CarPowerManagementService.this.mNapaFinished = false;
                        this.mCheckNapaCount = 0;
                        checkNapaFinished();
                    } else {
                        CarPowerManagementService.this.mNapaFinished = true;
                        this.mCheckNapaCount = 60;
                    }
                    CarPowerManagementService.this.mBootComplete = true;
                    return;
                case 5:
                    CarPowerManagementService.this.mSystemInterface.switchToPartialWakeLock();
                    if (CarPowerManagementService.this.isShouldSleep()) {
                        CarPowerManagementService.this.writeVbusGpio(CarPowerManagementService.VBUS_POWER_OFF);
                        boolean sleepSucceeded = CarPowerManagementService.this.mSystemInterface.enterDeepSleep();
                        if (CarPowerManagementService.this.mEnableDeepSleepRetry) {
                            Slog.i(CarLog.TAG_POWER, "create deep sleep timer, sleepSucceeded: " + sleepSucceeded);
                            CarPowerManagementService.this.createDeepSleepTimer(sleepSucceeded);
                            return;
                        }
                        return;
                    }
                    return;
                case 6:
                    CarPowerManagementService.this.mSystemInterface.setDisplayState((String) msg.obj, msg.arg1, msg.arg2 == 1);
                    return;
                case 7:
                    if (CarPowerManagementService.NAPA_INIT_FINISH.equals(SystemProperties.get("sys.xiaopeng.napa_state", "")) || this.mCheckNapaCount >= 60 || !CarPowerManagementService.this.is3DUi()) {
                        Slog.i(CarLog.TAG_POWER, "napa init finished");
                        CarPowerManagementService.this.mNapaFinished = true;
                        cancelProcessingComplete();
                        handlePowerStateChange();
                    } else {
                        checkNapaFinished();
                    }
                    this.mCheckNapaCount++;
                    return;
                default:
                    return;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean is3DUi() {
        if (this.mUiType == -1) {
            this.mUiType = FeatureOption.FO_PROJECT_UI_TYPE;
            Slog.i(CarLog.TAG_POWER, "ui type is: " + this.mUiType);
        }
        if (this.mUiType == 2) {
            return true;
        }
        return false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean needHandlePowerStateChange() {
        return !is3DUi() || this.mNapaFinished;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleBootSilent() {
        synchronized (this.mLock) {
            CpmsState state = this.mPendingPowerStates.peekFirst();
            if (state == null) {
                return;
            }
            Slog.i(CarLog.TAG_POWER, "handleBootSilent: " + state.toString());
            if (this.mHal.isIcmShouldSilent()) {
                writeBootSilentStatus("/sys/xpeng/cluster/cluster_status", "silence_on");
            } else {
                writeBootSilentStatus("/sys/xpeng/cluster/cluster_status", "silence_off");
            }
            if (state.mState == 1) {
                this.mSystemInterface.setDisplayState("xp_mt_ivi", 2, true);
                if (PowerManagerInternal.IS_HAS_PASSENGER) {
                    this.mSystemInterface.setDisplayState("xp_mt_psg", 2, true);
                    return;
                }
                return;
            }
            this.mSystemInterface.setDisplayState("xp_mt_ivi", 2, false);
            if (PowerManagerInternal.IS_HAS_PASSENGER) {
                this.mSystemInterface.setDisplayState("xp_mt_psg", 2, false);
            }
        }
    }

    private void writeBootSilentStatus(String fileName, String status) {
        for (int i = 0; i < 2; i++) {
            try {
                Slog.i(CarLog.TAG_POWER, fileName.substring(fileName.lastIndexOf("/") + 1) + ": " + status);
                FileUtils.stringToFile(fileName, status);
                return;
            } catch (Exception e) {
                Slog.i(CarLog.TAG_POWER, fileName + ":" + e.getMessage());
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class ShutdownProcessingTimerTask extends TimerTask {
        private int mCurrentCount;
        private final int mExpirationCount;

        private ShutdownProcessingTimerTask(int expirationCount) {
            this.mExpirationCount = expirationCount;
            this.mCurrentCount = 0;
        }

        @Override // java.util.TimerTask, java.lang.Runnable
        public void run() {
            synchronized (CarPowerManagementService.this.mLock) {
                if (CarPowerManagementService.this.mTimerActive) {
                    this.mCurrentCount++;
                    if (this.mCurrentCount > this.mExpirationCount) {
                        CarPowerManagementService.this.releaseTimerLocked();
                        PowerHandler handler = CarPowerManagementService.this.mHandler;
                        if ((!Build.IS_USER || CarLog.isGetLogEnable()) && !CarPowerManagementService.this.mListenersWeAreWaitingFor.isEmpty()) {
                            CarPowerManagementService.this.mListenersWeAreWaitingFor.forEach(new Consumer() { // from class: com.android.car.-$$Lambda$CarPowerManagementService$ShutdownProcessingTimerTask$PGmpeGflnr9-ODIjlx7TH4adi9I
                                @Override // java.util.function.Consumer
                                public final void accept(Object obj) {
                                    CarPowerManagementService.ShutdownProcessingTimerTask.this.lambda$run$0$CarPowerManagementService$ShutdownProcessingTimerTask((IBinder) obj);
                                }
                            });
                        }
                        handler.handleProcessingComplete();
                    } else {
                        CarPowerManagementService.this.mHal.sendShutdownPostpone(CarPowerManagementService.SHUTDOWN_EXTEND_MAX_MS);
                    }
                }
            }
        }

        public /* synthetic */ void lambda$run$0$CarPowerManagementService$ShutdownProcessingTimerTask(IBinder listener) {
            PowerListenerInfo info = (PowerListenerInfo) CarPowerManagementService.this.mPowerListenerCompletionMap.get(listener);
            Slog.i(CarLog.TAG_POWER, "process not completed: " + info.processName + ", className: " + info.className);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static class CpmsState {
        public static final int ON = 1;
        public static final int ON_SCREEN_OFF = 6;
        public static final int SHUTDOWN_PREPARE = 2;
        public static final int SIMULATE_SLEEP = 5;
        public static final int SUSPEND = 4;
        public static final int WAIT_FOR_FINISH = 3;
        public static final int WAIT_FOR_VHAL = 0;
        public final boolean mCanPostpone;
        public final boolean mCanSleep;
        public final int mCarPowerStateListenerState;
        public final int mState;

        CpmsState(PowerHalService.PowerState halPowerState) {
            int i = halPowerState.mState;
            if (i == 0) {
                this.mCanPostpone = false;
                this.mCanSleep = false;
                this.mCarPowerStateListenerState = cpmsStateToPowerStateListenerState(1);
                this.mState = 1;
            } else if (i == 1) {
                this.mCanPostpone = halPowerState.canPostponeShutdown();
                this.mCanSleep = halPowerState.canEnterDeepSleep();
                this.mCarPowerStateListenerState = cpmsStateToPowerStateListenerState(2);
                this.mState = 2;
            } else if (i == 2) {
                this.mCanPostpone = false;
                this.mCanSleep = false;
                this.mCarPowerStateListenerState = 8;
                this.mState = 0;
            } else if (i == 3) {
                this.mCanPostpone = false;
                this.mCanSleep = false;
                this.mCarPowerStateListenerState = cpmsStateToPowerStateListenerState(4);
                this.mState = 4;
            } else if (i == 4) {
                this.mCanPostpone = false;
                this.mCanSleep = false;
                this.mCarPowerStateListenerState = cpmsStateToPowerStateListenerState(6);
                this.mState = 6;
            } else {
                this.mCanPostpone = false;
                this.mCanSleep = false;
                this.mCarPowerStateListenerState = 0;
                this.mState = 0;
            }
        }

        CpmsState(int state) {
            this(state, cpmsStateToPowerStateListenerState(state));
        }

        CpmsState(int state, int carPowerStateListenerState) {
            this.mCanPostpone = state == 5;
            this.mCanSleep = state == 5;
            this.mCarPowerStateListenerState = carPowerStateListenerState;
            this.mState = state;
        }

        public String name() {
            String baseName;
            switch (this.mState) {
                case 0:
                    baseName = "WAIT_FOR_VHAL";
                    break;
                case 1:
                    baseName = "ON";
                    break;
                case 2:
                    baseName = "SHUTDOWN_PREPARE";
                    break;
                case 3:
                    baseName = "WAIT_FOR_FINISH";
                    break;
                case 4:
                    baseName = "SUSPEND";
                    break;
                case 5:
                    baseName = "SIMULATE_SLEEP";
                    break;
                case 6:
                    baseName = "ON_SCREEN_OFF";
                    break;
                default:
                    baseName = "<unknown>";
                    break;
            }
            return baseName + "(" + this.mState + ")";
        }

        private static int cpmsStateToPowerStateListenerState(int state) {
            if (state != 1) {
                if (state != 2) {
                    if (state != 4) {
                        if (state != 6) {
                            return 0;
                        }
                        return 9;
                    }
                    return 2;
                }
                return 7;
            }
            return 6;
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o instanceof CpmsState) {
                CpmsState that = (CpmsState) o;
                return this.mState == that.mState && this.mCanSleep == that.mCanSleep && this.mCanPostpone == that.mCanPostpone && this.mCarPowerStateListenerState == that.mCarPowerStateListenerState;
            }
            return false;
        }

        public String toString() {
            return "CpmsState canSleep:" + this.mCanSleep + ", canPostpone=" + this.mCanPostpone + ", carPowerStateListenerState=" + this.mCarPowerStateListenerState + ", CpmsState=" + name();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static class PowerListenerInfo {
        public final String className;
        public final String processName;

        public PowerListenerInfo(String processName, String className) {
            this.processName = processName;
            this.className = className;
        }
    }

    public void forceSimulatedResume() {
        Slog.w(CarLog.TAG_POWER, "xp power don't support forceSimulatedResume");
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @VisibleForTesting
    public void forceSuspendAndMaybeReboot(boolean shouldReboot) {
        Slog.w(CarLog.TAG_POWER, "xp car power don't support forceSimulatedSuspend");
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void simulateIcmScreenEnable(boolean enable) {
        this.mSystemInterface.setXpIcmScreenEnable(enable);
    }

    private void simulateSleepByWaiting() {
        Slog.i(CarLog.TAG_POWER, "Starting to simulate Deep Sleep by waiting");
        synchronized (this.mSimulationWaitObject) {
            while (!this.mWakeFromSimulatedSleep) {
                try {
                    this.mSimulationWaitObject.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            this.mInSimulatedDeepSleepMode = false;
        }
        Slog.i(CarLog.TAG_POWER, "Exit Deep Sleep simulation");
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class DeepSleepTimerTask extends TimerTask {
        private int mCurrentCount;
        private final int mExpirationCount;
        private final boolean mSleepSucceed;

        private DeepSleepTimerTask(boolean sleepSucceed, int expirationCount) {
            this.mSleepSucceed = sleepSucceed;
            this.mExpirationCount = expirationCount;
            this.mCurrentCount = 0;
        }

        @Override // java.util.TimerTask, java.lang.Runnable
        public void run() {
            synchronized (CarPowerManagementService.this) {
                if (CarPowerManagementService.this.mDeepSleepTimerActive) {
                    if (CarPowerManagementService.this.isShouldSleep()) {
                        if (CarPowerManagementService.this.mSystemInterface.isInteractive()) {
                            Slog.i(CarLog.TAG_POWER, "system is interactive");
                            SystemProperties.set("sys.xiaopeng.power_state", String.valueOf(0));
                            CarPowerManagementService.this.releaseDeepSleepTimerLocked();
                            CarPowerManagementService.this.exitSuspend();
                            return;
                        }
                        this.mCurrentCount++;
                        if (this.mSleepSucceed || this.mCurrentCount <= this.mExpirationCount) {
                            CarPowerManagementService.this.writeVbusGpio(CarPowerManagementService.VBUS_POWER_OFF);
                            boolean sleepSucceed = CarPowerManagementService.this.mSystemInterface.enterDeepSleep();
                            Slog.i(CarLog.TAG_POWER, "DeepSleepTimerTask sleepSucceed: " + sleepSucceed);
                        } else {
                            CarPowerManagementService.this.releaseDeepSleepTimerLocked();
                            if (this.mSleepSucceed) {
                                CarPowerManagementService.this.exitSuspend();
                            } else {
                                Slog.e(CarLog.TAG_POWER, "Shut down system after deep sleep exceed max retry count");
                                CarPowerManagementService.this.mSystemInterface.shutdown();
                            }
                        }
                        return;
                    }
                    Slog.i(CarLog.TAG_POWER, "system is wakeup");
                    CarPowerManagementService.this.releaseDeepSleepTimerLocked();
                    CarPowerManagementService.this.exitSuspend();
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isShouldSleep() {
        return this.mHal.isShouldSleep();
    }

    /* JADX INFO: Access modifiers changed from: private */
    @GuardedBy({"this"})
    public void createDeepSleepTimer(boolean sleepSucceed) {
        synchronized (this) {
            Slog.i(CarLog.TAG_POWER, "createDeepSleepTimer sleepSucceed: " + sleepSucceed + ", pollingCount: 60");
            releaseDeepSleepTimerLocked();
            this.mDeepSleepTimer = new Timer();
            this.mDeepSleepTimerActive = true;
            this.mDeepSleepTimer.scheduleAtFixedRate(new DeepSleepTimerTask(sleepSucceed, 60), 1000L, 1000L);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    @GuardedBy({"this"})
    public void releaseDeepSleepTimerLocked() {
        synchronized (this) {
            if (this.mDeepSleepTimer != null) {
                Slog.i(CarLog.TAG_POWER, "cancel deep sleep timer");
                this.mDeepSleepTimer.cancel();
            }
            this.mDeepSleepTimer = null;
            this.mDeepSleepTimerActive = false;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void exitSuspend() {
        if (this.mAutoSuspendEnable) {
            Slog.i(CarLog.TAG_POWER, "exit suspend");
            synchronized (this.mLock) {
                this.mLock.notifyAll();
            }
            this.mNextWakeupSec = 0;
            this.mAutoSuspendEnable = false;
            this.mSystemInterface.setAutoSuspendEnable(false);
            this.mHandler.cancelDeepSleep();
            sendPowerManagerEvent(3);
            this.mHal.sendSleepExit();
            writeVbusGpio(VBUS_POWER_ON);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void writeVbusGpio(String value) {
        try {
            String status = FileUtils.readTextFile(new File(VBUS_GPIO), 2, "").trim();
            if (value.equals(status)) {
                return;
            }
            long time = 50;
            if (VBUS_POWER_OFF.equals(value) && isShouldSleep()) {
                Slog.i(CarLog.TAG_POWER, "vbus gpio status: " + status + ", value: 0");
                FileUtils.stringToFile(VBUS_GPIO, value);
                time = VBUS_DELAY_TIME;
            } else if (VBUS_POWER_ON.equals(value)) {
                Slog.i(CarLog.TAG_POWER, "vbus gpio status: " + status + ", value: 1");
                FileUtils.stringToFile(VBUS_GPIO, value);
            }
            waitTime(time);
            Slog.i(CarLog.TAG_POWER, "vbus delay " + time + "ms");
        } catch (IOException e) {
            Slog.w(CarLog.TAG_POWER, e.getCause());
        }
    }

    private void waitTime(long time) {
        synchronized (this.mLock) {
            try {
                this.mLock.wait(time);
            } catch (InterruptedException e) {
                Slog.w(CarLog.TAG_POWER, e.getCause());
            }
        }
    }

    public void onAutoWakeupResult(boolean success) {
        Slog.i(CarLog.TAG_POWER, "auto suspend result: " + success);
        this.mSystemInterface.switchToPartialWakeLock();
        if (!isShouldSleep()) {
            this.mHandler.post(new Runnable() { // from class: com.android.car.-$$Lambda$CarPowerManagementService$Z6Eg6aHkVC5i6-uywNIF5ACDcck
                @Override // java.lang.Runnable
                public final void run() {
                    CarPowerManagementService.this.lambda$onAutoWakeupResult$1$CarPowerManagementService();
                }
            });
        } else if (!success) {
            long time = this.mAutoSuspendTime - (SystemClock.elapsedRealtime() - this.mLastSleepEntryTime);
            if (time <= 0) {
                this.mHandler.handleDeepSleep(0L);
                return;
            }
            writeVbusGpio(VBUS_POWER_OFF);
            if (isShouldSleep()) {
                this.mSystemInterface.releaseAllWakeLocks();
            }
        }
    }

    public /* synthetic */ void lambda$onAutoWakeupResult$1$CarPowerManagementService() {
        synchronized (this) {
            exitSuspend();
        }
    }
}
