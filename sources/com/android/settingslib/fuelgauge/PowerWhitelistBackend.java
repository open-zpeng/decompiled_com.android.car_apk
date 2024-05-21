package com.android.settingslib.fuelgauge;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.IDeviceIdleController;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telecom.DefaultDialerManager;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import androidx.annotation.VisibleForTesting;
import com.android.internal.telephony.SmsApplication;
import com.android.internal.util.ArrayUtils;
/* loaded from: classes3.dex */
public class PowerWhitelistBackend {
    private static final String DEVICE_IDLE_SERVICE = "deviceidle";
    private static final String TAG = "PowerWhitelistBackend";
    private static PowerWhitelistBackend sInstance;
    private final Context mAppContext;
    private final ArraySet<String> mDefaultActiveApps;
    private final IDeviceIdleController mDeviceIdleService;
    private final ArraySet<String> mSysWhitelistedApps;
    private final ArraySet<String> mSysWhitelistedAppsExceptIdle;
    private final ArraySet<String> mWhitelistedApps;

    public PowerWhitelistBackend(Context context) {
        this(context, IDeviceIdleController.Stub.asInterface(ServiceManager.getService(DEVICE_IDLE_SERVICE)));
    }

    @VisibleForTesting
    PowerWhitelistBackend(Context context, IDeviceIdleController deviceIdleService) {
        this.mWhitelistedApps = new ArraySet<>();
        this.mSysWhitelistedApps = new ArraySet<>();
        this.mSysWhitelistedAppsExceptIdle = new ArraySet<>();
        this.mDefaultActiveApps = new ArraySet<>();
        this.mAppContext = context.getApplicationContext();
        this.mDeviceIdleService = deviceIdleService;
        refreshList();
    }

    public int getWhitelistSize() {
        return this.mWhitelistedApps.size();
    }

    public boolean isSysWhitelisted(String pkg) {
        return this.mSysWhitelistedApps.contains(pkg);
    }

    public boolean isWhitelisted(String pkg) {
        return this.mWhitelistedApps.contains(pkg) || isDefaultActiveApp(pkg);
    }

    public boolean isDefaultActiveApp(String pkg) {
        if (this.mDefaultActiveApps.contains(pkg)) {
            return true;
        }
        DevicePolicyManager devicePolicyManager = (DevicePolicyManager) this.mAppContext.getSystemService(DevicePolicyManager.class);
        return devicePolicyManager.packageHasActiveAdmins(pkg);
    }

    public boolean isWhitelisted(String[] pkgs) {
        if (ArrayUtils.isEmpty(pkgs)) {
            return false;
        }
        for (String pkg : pkgs) {
            if (isWhitelisted(pkg)) {
                return true;
            }
        }
        return false;
    }

    public boolean isSysWhitelistedExceptIdle(String pkg) {
        return this.mSysWhitelistedAppsExceptIdle.contains(pkg);
    }

    public boolean isSysWhitelistedExceptIdle(String[] pkgs) {
        if (ArrayUtils.isEmpty(pkgs)) {
            return false;
        }
        for (String pkg : pkgs) {
            if (isSysWhitelistedExceptIdle(pkg)) {
                return true;
            }
        }
        return false;
    }

    public void addApp(String pkg) {
        try {
            this.mDeviceIdleService.addPowerSaveWhitelistApp(pkg);
            this.mWhitelistedApps.add(pkg);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to reach IDeviceIdleController", e);
        }
    }

    public void removeApp(String pkg) {
        try {
            this.mDeviceIdleService.removePowerSaveWhitelistApp(pkg);
            this.mWhitelistedApps.remove(pkg);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to reach IDeviceIdleController", e);
        }
    }

    @VisibleForTesting
    public void refreshList() {
        this.mSysWhitelistedApps.clear();
        this.mSysWhitelistedAppsExceptIdle.clear();
        this.mWhitelistedApps.clear();
        this.mDefaultActiveApps.clear();
        IDeviceIdleController iDeviceIdleController = this.mDeviceIdleService;
        if (iDeviceIdleController == null) {
            return;
        }
        try {
            String[] whitelistedApps = iDeviceIdleController.getFullPowerWhitelist();
            for (String app : whitelistedApps) {
                this.mWhitelistedApps.add(app);
            }
            String[] sysWhitelistedApps = this.mDeviceIdleService.getSystemPowerWhitelist();
            for (String app2 : sysWhitelistedApps) {
                this.mSysWhitelistedApps.add(app2);
            }
            String[] sysWhitelistedAppsExceptIdle = this.mDeviceIdleService.getSystemPowerWhitelistExceptIdle();
            for (String app3 : sysWhitelistedAppsExceptIdle) {
                this.mSysWhitelistedAppsExceptIdle.add(app3);
            }
            boolean hasTelephony = this.mAppContext.getPackageManager().hasSystemFeature("android.hardware.telephony");
            ComponentName defaultSms = SmsApplication.getDefaultSmsApplication(this.mAppContext, true);
            String defaultDialer = DefaultDialerManager.getDefaultDialerApplication(this.mAppContext);
            if (hasTelephony) {
                if (defaultSms != null) {
                    this.mDefaultActiveApps.add(defaultSms.getPackageName());
                }
                if (!TextUtils.isEmpty(defaultDialer)) {
                    this.mDefaultActiveApps.add(defaultDialer);
                }
            }
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to reach IDeviceIdleController", e);
        }
    }

    public static PowerWhitelistBackend getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new PowerWhitelistBackend(context);
        }
        return sInstance;
    }
}
