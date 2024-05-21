package com.android.car.user;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.car.CarNotConnectedException;
import android.car.hardware.power.CarPowerManager;
import android.car.user.IUserNotice;
import android.car.user.IUserNoticeUI;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Slog;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;
import com.android.car.CarLocalServices;
import com.android.car.CarLog;
import com.android.car.CarServiceBase;
import com.android.car.R;
import com.android.car.user.CarUserNoticeService;
import com.android.car.user.CarUserService;
import com.android.internal.annotations.GuardedBy;
import java.io.PrintWriter;
/* loaded from: classes3.dex */
public final class CarUserNoticeService implements CarServiceBase {
    private static final long KEYGUARD_POLLING_INTERVAL_MS = 100;
    @GuardedBy({"mLock"})
    private CarPowerManager mCarPowerManager;
    private final Context mContext;
    @GuardedBy({"mLock"})
    private int mKeyguardPollingCounter;
    private final Intent mServiceIntent;
    @GuardedBy({"mLock"})
    private IUserNoticeUI mUiService;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final Object mLock = new Object();
    @GuardedBy({"mLock"})
    private boolean mServiceBound = false;
    @GuardedBy({"mLock"})
    private boolean mUiShown = false;
    @GuardedBy({"mLock"})
    private int mUserId = -10000;
    @GuardedBy({"mLock"})
    private int mIgnoreUserId = -10000;
    private final CarUserService.UserCallback mUserCallback = new AnonymousClass1();
    private final CarPowerManager.CarPowerStateListener mPowerStateListener = new AnonymousClass2();
    private final BroadcastReceiver mDisplayBroadcastReceiver = new BroadcastReceiver() { // from class: com.android.car.user.CarUserNoticeService.3
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            if ("android.intent.action.SCREEN_OFF".equals(intent.getAction())) {
                if (CarUserNoticeService.this.isDisplayOn()) {
                    Slog.i(CarLog.TAG_USER, "SCREEN_OFF while display is already on");
                    return;
                }
                Slog.i(CarLog.TAG_USER, "Display off, stopping UI");
                CarUserNoticeService.this.stopUi(true);
            } else if ("android.intent.action.SCREEN_ON".equals(intent.getAction())) {
                if (!CarUserNoticeService.this.isDisplayOn()) {
                    Slog.i(CarLog.TAG_USER, "SCREEN_ON while display is already off");
                    return;
                }
                Slog.i(CarLog.TAG_USER, "Display on, starting UI");
                CarUserNoticeService.this.startNoticeUiIfNecessary();
            }
        }
    };
    private final IUserNotice.Stub mIUserNotice = new AnonymousClass4();
    private final ServiceConnection mUiServiceConnection = new ServiceConnection() { // from class: com.android.car.user.CarUserNoticeService.5
        @Override // android.content.ServiceConnection
        public void onServiceConnected(ComponentName name, IBinder service) {
            IUserNoticeUI binder;
            synchronized (CarUserNoticeService.this.mLock) {
                if (CarUserNoticeService.this.mServiceBound) {
                    IUserNoticeUI binder2 = IUserNoticeUI.Stub.asInterface(service);
                    try {
                        binder2.setCallbackBinder(CarUserNoticeService.this.mIUserNotice);
                        binder = binder2;
                    } catch (RemoteException e) {
                        Slog.w(CarLog.TAG_USER, "UserNoticeUI Service died", e);
                        binder = null;
                    }
                    synchronized (CarUserNoticeService.this.mLock) {
                        CarUserNoticeService.this.mUiService = binder;
                    }
                }
            }
        }

        @Override // android.content.ServiceConnection
        public void onServiceDisconnected(ComponentName name) {
            CarUserNoticeService.this.stopUi(true);
        }
    };
    private final Runnable mKeyguardPollingRunnable = new Runnable() { // from class: com.android.car.user.-$$Lambda$CarUserNoticeService$OBhlqwteKkRlzDnONYIaDTB34cc
        @Override // java.lang.Runnable
        public final void run() {
            CarUserNoticeService.this.lambda$new$0$CarUserNoticeService();
        }
    };

    /* JADX INFO: Access modifiers changed from: package-private */
    /* renamed from: com.android.car.user.CarUserNoticeService$1  reason: invalid class name */
    /* loaded from: classes3.dex */
    public class AnonymousClass1 implements CarUserService.UserCallback {
        AnonymousClass1() {
        }

        @Override // com.android.car.user.CarUserService.UserCallback
        public void onUserLockChanged(int userId, boolean unlocked) {
        }

        @Override // com.android.car.user.CarUserService.UserCallback
        public void onSwitchUser(final int userId) {
            CarUserNoticeService.this.mMainHandler.post(new Runnable() { // from class: com.android.car.user.-$$Lambda$CarUserNoticeService$1$MQz0FgPm-NIxIdRGinJEWg0Yfl8
                @Override // java.lang.Runnable
                public final void run() {
                    CarUserNoticeService.AnonymousClass1.this.lambda$onSwitchUser$0$CarUserNoticeService$1(userId);
                }
            });
        }

        public /* synthetic */ void lambda$onSwitchUser$0$CarUserNoticeService$1(int userId) {
            CarUserNoticeService.this.stopUi(true);
            synchronized (CarUserNoticeService.this.mLock) {
                CarUserNoticeService.this.mUserId = userId;
            }
            CarUserNoticeService.this.startNoticeUiIfNecessary();
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* renamed from: com.android.car.user.CarUserNoticeService$2  reason: invalid class name */
    /* loaded from: classes3.dex */
    public class AnonymousClass2 implements CarPowerManager.CarPowerStateListener {
        AnonymousClass2() {
        }

        public void onStateChanged(int state) {
            if (state == 7) {
                CarUserNoticeService.this.mMainHandler.post(new Runnable() { // from class: com.android.car.user.-$$Lambda$CarUserNoticeService$2$ByUmffA19U_oJRytIXCy_IBm8lc
                    @Override // java.lang.Runnable
                    public final void run() {
                        CarUserNoticeService.AnonymousClass2.this.lambda$onStateChanged$0$CarUserNoticeService$2();
                    }
                });
            } else if (state == 6) {
                CarUserNoticeService.this.mMainHandler.post(new Runnable() { // from class: com.android.car.user.-$$Lambda$CarUserNoticeService$2$qylxILhbtO1AUfX34eTyZpu3kvM
                    @Override // java.lang.Runnable
                    public final void run() {
                        CarUserNoticeService.AnonymousClass2.this.lambda$onStateChanged$1$CarUserNoticeService$2();
                    }
                });
            }
        }

        public /* synthetic */ void lambda$onStateChanged$0$CarUserNoticeService$2() {
            CarUserNoticeService.this.stopUi(true);
        }

        public /* synthetic */ void lambda$onStateChanged$1$CarUserNoticeService$2() {
            CarUserNoticeService.this.startNoticeUiIfNecessary();
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* renamed from: com.android.car.user.CarUserNoticeService$4  reason: invalid class name */
    /* loaded from: classes3.dex */
    public class AnonymousClass4 extends IUserNotice.Stub {
        AnonymousClass4() {
        }

        public /* synthetic */ void lambda$onDialogDismissed$0$CarUserNoticeService$4() {
            CarUserNoticeService.this.stopUi(false);
        }

        public void onDialogDismissed() {
            CarUserNoticeService.this.mMainHandler.post(new Runnable() { // from class: com.android.car.user.-$$Lambda$CarUserNoticeService$4$dQhG_b8712TpgvOi04Ba4w_9lEk
                @Override // java.lang.Runnable
                public final void run() {
                    CarUserNoticeService.AnonymousClass4.this.lambda$onDialogDismissed$0$CarUserNoticeService$4();
                }
            });
        }
    }

    public /* synthetic */ void lambda$new$0$CarUserNoticeService() {
        synchronized (this.mLock) {
            this.mKeyguardPollingCounter++;
        }
        startNoticeUiIfNecessary();
    }

    public CarUserNoticeService(Context context) {
        Resources res = context.getResources();
        String componentName = res.getString(R.string.config_userNoticeUiService);
        if (componentName.isEmpty()) {
            this.mContext = null;
            this.mServiceIntent = null;
            return;
        }
        this.mContext = context;
        this.mServiceIntent = new Intent();
        this.mServiceIntent.setComponent(ComponentName.unflattenFromString(componentName));
    }

    public void ignoreUserNotice(int userId) {
        synchronized (this.mLock) {
            this.mIgnoreUserId = userId;
        }
    }

    private boolean checkKeyguardLockedWithPolling() {
        this.mMainHandler.removeCallbacks(this.mKeyguardPollingRunnable);
        IWindowManager wm = WindowManagerGlobal.getWindowManagerService();
        boolean locked = true;
        if (wm != null) {
            try {
                locked = wm.isKeyguardLocked();
            } catch (RemoteException e) {
                Slog.w(CarLog.TAG_USER, "system server crashed", e);
            }
        }
        if (locked) {
            this.mMainHandler.postDelayed(this.mKeyguardPollingRunnable, KEYGUARD_POLLING_INTERVAL_MS);
        }
        return locked;
    }

    private boolean isNoticeScreenEnabledInSetting(int userId) {
        return Settings.Secure.getIntForUser(this.mContext.getContentResolver(), "android.car.ENABLE_INITIAL_NOTICE_SCREEN_TO_USER", 1, userId) == 1;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isDisplayOn() {
        PowerManager pm = (PowerManager) this.mContext.getSystemService(PowerManager.class);
        if (pm == null) {
            return false;
        }
        return pm.isInteractive();
    }

    private boolean grantSystemAlertWindowPermission(int userId) {
        AppOpsManager appOpsManager = (AppOpsManager) this.mContext.getSystemService(AppOpsManager.class);
        if (appOpsManager == null) {
            Slog.w(CarLog.TAG_USER, "AppOpsManager not ready yet");
            return false;
        }
        String packageName = this.mServiceIntent.getComponent().getPackageName();
        try {
            int packageUid = this.mContext.getPackageManager().getPackageUidAsUser(packageName, userId);
            appOpsManager.setMode(24, packageUid, packageName, 0);
            Slog.i(CarLog.TAG_USER, "Granted SYSTEM_ALERT_WINDOW permission to package:" + packageName + " package uid:" + packageUid);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            Slog.e(CarLog.TAG_USER, "Target package for config_userNoticeUiService not found:" + packageName + " userId:" + userId);
            return false;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void startNoticeUiIfNecessary() {
        synchronized (this.mLock) {
            if (!this.mUiShown && !this.mServiceBound) {
                int userId = this.mUserId;
                if (this.mIgnoreUserId == userId) {
                    return;
                }
                this.mIgnoreUserId = -10000;
                if (userId == -10000 || userId == 0 || !isNoticeScreenEnabledInSetting(userId) || userId != ActivityManager.getCurrentUser() || !isDisplayOn() || checkKeyguardLockedWithPolling() || !grantSystemAlertWindowPermission(userId)) {
                    return;
                }
                boolean bound = this.mContext.bindServiceAsUser(this.mServiceIntent, this.mUiServiceConnection, 1, UserHandle.of(userId));
                if (bound) {
                    Slog.i(CarLog.TAG_USER, "Bound UserNoticeUI Service Service:" + this.mServiceIntent);
                    synchronized (this.mLock) {
                        this.mServiceBound = true;
                        this.mUiShown = true;
                    }
                    return;
                }
                Slog.w(CarLog.TAG_USER, "Cannot bind to UserNoticeUI Service Service" + this.mServiceIntent);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void stopUi(boolean clearUiShown) {
        boolean serviceBound;
        this.mMainHandler.removeCallbacks(this.mKeyguardPollingRunnable);
        synchronized (this.mLock) {
            this.mUiService = null;
            serviceBound = this.mServiceBound;
            this.mServiceBound = false;
            if (clearUiShown) {
                this.mUiShown = false;
            }
        }
        if (serviceBound) {
            Slog.i(CarLog.TAG_USER, "Unbound UserNoticeUI Service");
            this.mContext.unbindService(this.mUiServiceConnection);
        }
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
        CarPowerManager carPowerManager;
        if (this.mServiceIntent == null) {
            return;
        }
        synchronized (this.mLock) {
            this.mCarPowerManager = CarLocalServices.createCarPowerManager(this.mContext);
            carPowerManager = this.mCarPowerManager;
        }
        try {
            carPowerManager.setListener(this.mPowerStateListener);
            CarUserService userService = (CarUserService) CarLocalServices.getService(CarUserService.class);
            userService.addUserCallback(this.mUserCallback);
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction("android.intent.action.SCREEN_OFF");
            intentFilter.addAction("android.intent.action.SCREEN_ON");
            this.mContext.registerReceiver(this.mDisplayBroadcastReceiver, intentFilter);
        } catch (CarNotConnectedException e) {
            throw new RuntimeException("CarNotConnectedException from CarPowerManager", e);
        }
    }

    @Override // com.android.car.CarServiceBase
    public void release() {
        CarPowerManager carPowerManager;
        if (this.mServiceIntent == null) {
            return;
        }
        this.mContext.unregisterReceiver(this.mDisplayBroadcastReceiver);
        CarUserService userService = (CarUserService) CarLocalServices.getService(CarUserService.class);
        userService.removeUserCallback(this.mUserCallback);
        synchronized (this.mLock) {
            carPowerManager = this.mCarPowerManager;
            this.mUserId = -10000;
        }
        carPowerManager.clearListener();
        stopUi(true);
    }

    @Override // com.android.car.CarServiceBase
    public void dump(PrintWriter writer) {
        synchronized (this.mLock) {
            if (this.mServiceIntent == null) {
                writer.println("*CarUserNoticeService* disabled");
            } else if (this.mUserId == -10000) {
                writer.println("*CarUserNoticeService* User not started yet.");
            } else {
                writer.println("*CarUserNoticeService* mServiceIntent:" + this.mServiceIntent + ", mUserId:" + this.mUserId + ", mUiShown:" + this.mUiShown + ", mServiceBound:" + this.mServiceBound + ", mKeyguardPollingCounter:" + this.mKeyguardPollingCounter + ", Setting enabled:" + isNoticeScreenEnabledInSetting(this.mUserId) + ", Ignore User: " + this.mIgnoreUserId);
            }
        }
    }
}
