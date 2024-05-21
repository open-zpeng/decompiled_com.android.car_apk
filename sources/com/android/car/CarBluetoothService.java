package com.android.car;

import android.app.ActivityManager;
import android.bluetooth.BluetoothDevice;
import android.car.ICarBluetooth;
import android.car.ICarBluetoothUserService;
import android.car.ICarUserService;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;
import com.android.car.PerUserCarServiceHelper;
import com.android.internal.annotations.GuardedBy;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
/* loaded from: classes3.dex */
public class CarBluetoothService extends ICarBluetooth.Stub implements CarServiceBase {
    @GuardedBy({"mPerUserLock"})
    private ICarBluetoothUserService mCarBluetoothUserService;
    @GuardedBy({"mPerUserLock"})
    private ICarUserService mCarUserService;
    private final Context mContext;
    private final boolean mUseDefaultPolicy;
    private final PerUserCarServiceHelper mUserServiceHelper;
    private static final String TAG = "CarBluetoothService";
    private static final boolean DBG = Log.isLoggable(TAG, 3);
    private static final List<Integer> sManagedProfiles = Arrays.asList(16, 17, 11, 18, 5);
    private final Object mPerUserLock = new Object();
    private final SparseArray<BluetoothProfileDeviceManager> mProfileDeviceManagers = new SparseArray<>();
    @GuardedBy({"mPerUserLock"})
    private BluetoothProfileInhibitManager mInhibitManager = null;
    @GuardedBy({"mPerUserLock"})
    private BluetoothDeviceConnectionPolicy mBluetoothDeviceConnectionPolicy = null;
    private final PerUserCarServiceHelper.ServiceCallback mUserServiceCallback = new PerUserCarServiceHelper.ServiceCallback() { // from class: com.android.car.CarBluetoothService.1
        @Override // com.android.car.PerUserCarServiceHelper.ServiceCallback
        public void onServiceConnected(ICarUserService carUserService) {
            CarBluetoothService.logd("Connected to PerUserCarService");
            synchronized (CarBluetoothService.this.mPerUserLock) {
                CarBluetoothService.this.destroyUser();
                CarBluetoothService.this.mCarUserService = carUserService;
                CarBluetoothService.this.initializeUser();
            }
        }

        @Override // com.android.car.PerUserCarServiceHelper.ServiceCallback
        public void onPreUnbind() {
            CarBluetoothService.logd("Before Unbinding from PerCarUserService");
            CarBluetoothService.this.destroyUser();
        }

        @Override // com.android.car.PerUserCarServiceHelper.ServiceCallback
        public void onServiceDisconnected() {
            CarBluetoothService.logd("Disconnected from PerUserCarService");
            CarBluetoothService.this.destroyUser();
        }
    };
    @GuardedBy({"mPerUserLock"})
    private int mUserId = -1;

    public CarBluetoothService(Context context, PerUserCarServiceHelper userSwitchService) {
        this.mContext = context;
        this.mUserServiceHelper = userSwitchService;
        this.mUseDefaultPolicy = this.mContext.getResources().getBoolean(R.bool.useDefaultBluetoothConnectionPolicy);
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
        logd("init()");
        this.mUserServiceHelper.registerServiceCallback(this.mUserServiceCallback);
    }

    @Override // com.android.car.CarServiceBase
    public void release() {
        logd("release()");
        this.mUserServiceHelper.unregisterServiceCallback(this.mUserServiceCallback);
        destroyUser();
    }

    public static List<Integer> getManagedProfiles() {
        return sManagedProfiles;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void initializeUser() {
        logd("Initializing new user");
        synchronized (this.mPerUserLock) {
            this.mUserId = ActivityManager.getCurrentUser();
            createBluetoothUserService();
            createBluetoothProfileDeviceManagers();
            createBluetoothProfileInhibitManager();
            this.mBluetoothDeviceConnectionPolicy = null;
            if (this.mUseDefaultPolicy) {
                createBluetoothDeviceConnectionPolicy();
            }
            logd("Switched to user " + this.mUserId);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void destroyUser() {
        logd("Destroying user " + this.mUserId);
        synchronized (this.mPerUserLock) {
            destroyBluetoothDeviceConnectionPolicy();
            destroyBluetoothProfileInhibitManager();
            destroyBluetoothProfileDeviceManagers();
            destroyBluetoothUserService();
            this.mCarUserService = null;
            this.mUserId = -1;
        }
    }

    private void createBluetoothUserService() {
        synchronized (this.mPerUserLock) {
            if (this.mCarUserService != null) {
                try {
                    this.mCarBluetoothUserService = this.mCarUserService.getBluetoothUserService();
                    this.mCarBluetoothUserService.setupBluetoothConnectionProxies();
                } catch (RemoteException e) {
                    Slog.e(TAG, "Remote Service Exception on ServiceConnection Callback: " + e.getMessage());
                } catch (NullPointerException e2) {
                    Slog.e(TAG, "Initialization Failed: " + e2.getMessage());
                }
            }
        }
    }

    private void destroyBluetoothUserService() {
        synchronized (this.mPerUserLock) {
            if (this.mCarBluetoothUserService == null) {
                return;
            }
            try {
                this.mCarBluetoothUserService.closeBluetoothConnectionProxies();
            } catch (RemoteException e) {
                Slog.e(TAG, "Remote Service Exception on ServiceConnection Callback: " + e.getMessage());
            }
            this.mCarBluetoothUserService = null;
        }
    }

    private void createBluetoothProfileDeviceManagers() {
        synchronized (this.mPerUserLock) {
            if (this.mUserId == -1) {
                logd("No foreground user, cannot create profile device managers");
                return;
            }
            for (Integer num : sManagedProfiles) {
                int profileId = num.intValue();
                BluetoothProfileDeviceManager deviceManager = this.mProfileDeviceManagers.get(profileId);
                if (deviceManager != null) {
                    deviceManager.stop();
                    this.mProfileDeviceManagers.remove(profileId);
                    logd("Existing device manager removed for profile " + Utils.getProfileName(profileId));
                }
                BluetoothProfileDeviceManager deviceManager2 = BluetoothProfileDeviceManager.create(this.mContext, this.mUserId, this.mCarBluetoothUserService, profileId);
                if (deviceManager2 == null) {
                    logd("Failed to create profile device manager for " + Utils.getProfileName(profileId));
                } else {
                    this.mProfileDeviceManagers.put(profileId, deviceManager2);
                    logd("Created profile device manager for " + Utils.getProfileName(profileId));
                }
            }
            for (int i = 0; i < this.mProfileDeviceManagers.size(); i++) {
                int key = this.mProfileDeviceManagers.keyAt(i);
                this.mProfileDeviceManagers.get(key).start();
            }
        }
    }

    private void destroyBluetoothProfileDeviceManagers() {
        synchronized (this.mPerUserLock) {
            for (int i = 0; i < this.mProfileDeviceManagers.size(); i++) {
                int key = this.mProfileDeviceManagers.keyAt(i);
                BluetoothProfileDeviceManager deviceManager = this.mProfileDeviceManagers.get(key);
                deviceManager.stop();
            }
            this.mProfileDeviceManagers.clear();
        }
    }

    private void createBluetoothProfileInhibitManager() {
        logd("Creating inhibit manager");
        synchronized (this.mPerUserLock) {
            if (this.mUserId == -1) {
                logd("No foreground user, cannot create profile inhibit manager");
                return;
            }
            this.mInhibitManager = new BluetoothProfileInhibitManager(this.mContext, this.mUserId, this.mCarBluetoothUserService);
            this.mInhibitManager.start();
        }
    }

    private void destroyBluetoothProfileInhibitManager() {
        logd("Destroying inhibit manager");
        synchronized (this.mPerUserLock) {
            if (this.mInhibitManager == null) {
                return;
            }
            this.mInhibitManager.stop();
            this.mInhibitManager = null;
        }
    }

    private void createBluetoothDeviceConnectionPolicy() {
        logd("Creating device connection policy");
        synchronized (this.mPerUserLock) {
            if (this.mUserId == -1) {
                logd("No foreground user, cannot create device connection policy");
                return;
            }
            this.mBluetoothDeviceConnectionPolicy = BluetoothDeviceConnectionPolicy.create(this.mContext, this.mUserId, this);
            if (this.mBluetoothDeviceConnectionPolicy == null) {
                logd("Failed to create default Bluetooth device connection policy.");
            } else {
                this.mBluetoothDeviceConnectionPolicy.init();
            }
        }
    }

    private void destroyBluetoothDeviceConnectionPolicy() {
        logd("Destroying device connection policy");
        synchronized (this.mPerUserLock) {
            if (this.mBluetoothDeviceConnectionPolicy != null) {
                this.mBluetoothDeviceConnectionPolicy.release();
                this.mBluetoothDeviceConnectionPolicy = null;
            }
        }
    }

    public boolean isUsingDefaultConnectionPolicy() {
        boolean z;
        synchronized (this.mPerUserLock) {
            z = this.mBluetoothDeviceConnectionPolicy != null;
        }
        return z;
    }

    public void connectDevices() {
        enforceBluetoothAdminPermission();
        logd("Connect devices for each profile");
        synchronized (this.mPerUserLock) {
            for (int i = 0; i < this.mProfileDeviceManagers.size(); i++) {
                int key = this.mProfileDeviceManagers.keyAt(i);
                BluetoothProfileDeviceManager deviceManager = this.mProfileDeviceManagers.get(key);
                deviceManager.beginAutoConnecting();
            }
        }
    }

    public List<BluetoothDevice> getProfileDevicePriorityList(int profile) {
        enforceBluetoothAdminPermission();
        synchronized (this.mPerUserLock) {
            BluetoothProfileDeviceManager deviceManager = this.mProfileDeviceManagers.get(profile);
            if (deviceManager != null) {
                return deviceManager.getDeviceListSnapshot();
            }
            return new ArrayList();
        }
    }

    public int getDeviceConnectionPriority(int profile, BluetoothDevice device) {
        enforceBluetoothAdminPermission();
        synchronized (this.mPerUserLock) {
            BluetoothProfileDeviceManager deviceManager = this.mProfileDeviceManagers.get(profile);
            if (deviceManager != null) {
                return deviceManager.getDeviceConnectionPriority(device);
            }
            return -1;
        }
    }

    public void setDeviceConnectionPriority(int profile, BluetoothDevice device, int priority) {
        enforceBluetoothAdminPermission();
        synchronized (this.mPerUserLock) {
            BluetoothProfileDeviceManager deviceManager = this.mProfileDeviceManagers.get(profile);
            if (deviceManager != null) {
                deviceManager.setDeviceConnectionPriority(device, priority);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean requestProfileInhibit(BluetoothDevice device, int profile, IBinder token) {
        logd("Request profile inhibit: profile " + Utils.getProfileName(profile) + ", device " + device.getAddress());
        synchronized (this.mPerUserLock) {
            if (this.mInhibitManager == null) {
                return false;
            }
            return this.mInhibitManager.requestProfileInhibit(device, profile, token);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean releaseProfileInhibit(BluetoothDevice device, int profile, IBinder token) {
        logd("Release profile inhibit: profile " + Utils.getProfileName(profile) + ", device " + device.getAddress());
        synchronized (this.mPerUserLock) {
            if (this.mInhibitManager == null) {
                return false;
            }
            return this.mInhibitManager.releaseProfileInhibit(device, profile, token);
        }
    }

    private void enforceBluetoothAdminPermission() {
        Context context = this.mContext;
        if (context != null && context.checkCallingOrSelfPermission("android.permission.BLUETOOTH_ADMIN") == 0) {
            return;
        }
        if (this.mContext == null) {
            Slog.e(TAG, "CarBluetoothPrioritySettings does not have a Context");
        }
        throw new SecurityException("requires permission android.permission.BLUETOOTH_ADMIN");
    }

    @Override // com.android.car.CarServiceBase
    public void dump(PrintWriter writer) {
        writer.println("*CarBluetoothService*");
        synchronized (this.mPerUserLock) {
            writer.println("\tUser ID: " + this.mUserId);
            StringBuilder sb = new StringBuilder();
            sb.append("\tUser Proxies: ");
            sb.append(this.mCarBluetoothUserService != null ? "Yes" : "No");
            writer.println(sb.toString());
            for (int i = 0; i < this.mProfileDeviceManagers.size(); i++) {
                int key = this.mProfileDeviceManagers.keyAt(i);
                BluetoothProfileDeviceManager deviceManager = this.mProfileDeviceManagers.get(key);
                deviceManager.dump(writer, "\t");
            }
            if (this.mInhibitManager != null) {
                this.mInhibitManager.dump(writer, "\t");
            } else {
                writer.println("\tBluetoothProfileInhibitManager: null");
            }
            StringBuilder sb2 = new StringBuilder();
            sb2.append("\tUsing default policy? ");
            sb2.append(this.mUseDefaultPolicy ? "Yes" : "No");
            writer.println(sb2.toString());
            if (this.mBluetoothDeviceConnectionPolicy == null) {
                writer.println("\tBluetoothDeviceConnectionPolicy: null");
            } else {
                this.mBluetoothDeviceConnectionPolicy.dump(writer, "\t");
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void logd(String msg) {
        if (DBG) {
            Slog.d(TAG, msg);
        }
    }
}
