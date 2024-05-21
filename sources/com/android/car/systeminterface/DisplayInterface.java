package com.android.car.systeminterface;

import android.app.ActivityManager;
import android.car.userlib.CarUserManagerHelper;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Slog;
import android.view.Display;
import android.view.DisplayAddress;
import android.view.IWindowManager;
import android.view.InputDevice;
import androidx.core.view.InputDeviceCompat;
import com.android.car.CarLog;
import com.android.car.CarPowerManagementService;
import com.android.settingslib.display.BrightnessUtils;
/* loaded from: classes3.dex */
public interface DisplayInterface {
    void reconfigureSecondaryDisplays();

    void refreshDisplayBrightness();

    void setDisplayBrightness(int i);

    void setDisplayState(String str, int i, boolean z);

    void setDisplayState(boolean z);

    void startDisplayStateMonitoring(CarPowerManagementService carPowerManagementService);

    void stopDisplayStateMonitoring();

    /* loaded from: classes3.dex */
    public static class DefaultImpl implements DisplayInterface, CarUserManagerHelper.OnUsersUpdateListener {
        static final String TAG = DisplayInterface.class.getSimpleName();
        private final ActivityManager mActivityManager;
        private CarUserManagerHelper mCarUserManagerHelper;
        private final ContentResolver mContentResolver;
        private final Context mContext;
        private final DisplayManager mDisplayManager;
        private boolean mDisplayStateSet;
        private final InputManager mInputManager;
        private final int mMaximumBacklight;
        private final int mMinimumBacklight;
        private final PowerManager mPowerManager;
        private CarPowerManagementService mService;
        private final WakeLockInterface mWakeLockInterface;
        private int mLastBrightnessLevel = -1;
        private ContentObserver mBrightnessObserver = new ContentObserver(new Handler(Looper.getMainLooper())) { // from class: com.android.car.systeminterface.DisplayInterface.DefaultImpl.1
            @Override // android.database.ContentObserver
            public void onChange(boolean selfChange) {
                DefaultImpl.this.refreshDisplayBrightness();
            }
        };
        private final DisplayManager.DisplayListener mDisplayListener = new DisplayManager.DisplayListener() { // from class: com.android.car.systeminterface.DisplayInterface.DefaultImpl.2
            @Override // android.hardware.display.DisplayManager.DisplayListener
            public void onDisplayAdded(int displayId) {
            }

            @Override // android.hardware.display.DisplayManager.DisplayListener
            public void onDisplayRemoved(int displayId) {
            }

            @Override // android.hardware.display.DisplayManager.DisplayListener
            public void onDisplayChanged(int displayId) {
                if (displayId == 0) {
                    DefaultImpl.this.handleMainDisplayChanged();
                }
            }
        };

        /* JADX INFO: Access modifiers changed from: package-private */
        public DefaultImpl(Context context, WakeLockInterface wakeLockInterface) {
            this.mActivityManager = (ActivityManager) context.getSystemService("activity");
            this.mContext = context;
            this.mContentResolver = this.mContext.getContentResolver();
            this.mDisplayManager = (DisplayManager) context.getSystemService("display");
            this.mInputManager = (InputManager) this.mContext.getSystemService("input");
            this.mPowerManager = (PowerManager) context.getSystemService("power");
            this.mMaximumBacklight = this.mPowerManager.getMaximumScreenBrightnessSetting();
            this.mMinimumBacklight = this.mPowerManager.getMinimumScreenBrightnessSetting();
            this.mWakeLockInterface = wakeLockInterface;
            this.mCarUserManagerHelper = new CarUserManagerHelper(context);
            this.mCarUserManagerHelper.registerOnUsersUpdateListener(this);
        }

        @Override // com.android.car.systeminterface.DisplayInterface
        public synchronized void refreshDisplayBrightness() {
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void handleMainDisplayChanged() {
            boolean isOn = isMainDisplayOn();
            synchronized (this) {
                if (this.mDisplayStateSet == isOn) {
                    return;
                }
                CarPowerManagementService service = this.mService;
                service.handleMainDisplayChanged(isOn);
            }
        }

        private boolean isMainDisplayOn() {
            Display disp = this.mDisplayManager.getDisplay(0);
            return disp.getState() == 2;
        }

        @Override // com.android.car.systeminterface.DisplayInterface
        public void setDisplayBrightness(int percentBright) {
            if (percentBright == this.mLastBrightnessLevel) {
                return;
            }
            this.mLastBrightnessLevel = percentBright;
            int gamma = ((percentBright * BrightnessUtils.GAMMA_SPACE_MAX) + 50) / 100;
            int linear = BrightnessUtils.convertGammaToLinear(gamma, this.mMinimumBacklight, this.mMaximumBacklight);
            ContentResolver contentResolver = this.mContentResolver;
            ActivityManager activityManager = this.mActivityManager;
            Settings.System.putIntForUser(contentResolver, "screen_brightness", linear, ActivityManager.getCurrentUser());
        }

        @Override // com.android.car.systeminterface.DisplayInterface
        public void startDisplayStateMonitoring(CarPowerManagementService service) {
            synchronized (this) {
                this.mService = service;
                this.mDisplayStateSet = isMainDisplayOn();
            }
            this.mDisplayManager.registerDisplayListener(this.mDisplayListener, service.getHandler());
            refreshDisplayBrightness();
        }

        @Override // com.android.car.systeminterface.DisplayInterface
        public void stopDisplayStateMonitoring() {
            this.mDisplayManager.unregisterDisplayListener(this.mDisplayListener);
        }

        @Override // com.android.car.systeminterface.DisplayInterface
        public void setDisplayState(String deviceName, int silenceState, boolean isExit) {
            this.mPowerManager.setDisplayState(deviceName, silenceState, isExit);
        }

        @Override // com.android.car.systeminterface.DisplayInterface
        public void setDisplayState(boolean on) {
            int[] inputDeviceIds;
            synchronized (this) {
                this.mDisplayStateSet = on;
            }
            if (on) {
                this.mWakeLockInterface.switchToFullWakeLock();
                Slog.i(CarLog.TAG_POWER, "on display");
                this.mPowerManager.wakeUp(SystemClock.uptimeMillis(), 10, "wakeUp:MCU");
            } else {
                this.mWakeLockInterface.switchToPartialWakeLock();
                Slog.i(CarLog.TAG_POWER, "off display");
                this.mPowerManager.goToSleep(SystemClock.uptimeMillis(), 9, 0);
            }
            for (int deviceId : this.mInputManager.getInputDeviceIds()) {
                InputDevice inputDevice = this.mInputManager.getInputDevice(deviceId);
                if (inputDevice != null && (inputDevice.getSources() & InputDeviceCompat.SOURCE_TOUCHSCREEN) == 4098) {
                    if (on) {
                        this.mInputManager.enableInputDevice(deviceId);
                    } else {
                        this.mInputManager.disableInputDevice(deviceId);
                    }
                }
            }
        }

        @Override // android.car.userlib.CarUserManagerHelper.OnUsersUpdateListener
        public void onUsersUpdate() {
            if (this.mService == null) {
                return;
            }
            this.mLastBrightnessLevel = -1;
            refreshDisplayBrightness();
        }

        @Override // com.android.car.systeminterface.DisplayInterface
        public void reconfigureSecondaryDisplays() {
            IWindowManager wm = IWindowManager.Stub.asInterface(ServiceManager.getService("window"));
            if (wm == null) {
                Slog.e(TAG, "reconfigureSecondaryDisplays IWindowManager not available");
                return;
            }
            Display[] displays = this.mDisplayManager.getDisplays();
            for (Display display : displays) {
                if (display.getDisplayId() != 0 && (display.getAddress() instanceof DisplayAddress.Physical)) {
                    int displayId = display.getDisplayId();
                    try {
                        int windowingMode = wm.getWindowingMode(displayId);
                        wm.setWindowingMode(displayId, windowingMode);
                    } catch (RemoteException e) {
                        Slog.e(CarLog.TAG_SERVICE, "cannot access IWindowManager", e);
                    }
                }
            }
        }
    }
}
