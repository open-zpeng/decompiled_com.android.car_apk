package com.android.car;

import android.bluetooth.BluetoothA2dpSink;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadsetClient;
import android.bluetooth.BluetoothMapClient;
import android.bluetooth.BluetoothPan;
import android.bluetooth.BluetoothPbapClient;
import android.bluetooth.BluetoothProfile;
import android.car.ICarBluetoothUserService;
import android.util.Log;
import android.util.Slog;
import android.util.SparseBooleanArray;
import com.android.internal.util.Preconditions;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
/* loaded from: classes3.dex */
public class CarBluetoothUserService extends ICarBluetoothUserService.Stub {
    private static final int PROXY_OPERATION_TIMEOUT_MS = 8000;
    private final BluetoothAdapter mBluetoothAdapter;
    private final ReentrantLock mBluetoothProxyLock;
    private final Condition mConditionAllProxiesConnected;
    private final PerUserCarService mService;
    private static final String TAG = "CarBluetoothUserService";
    private static final boolean DBG = Log.isLoggable(TAG, 3);
    private static final List<Integer> sProfilesToConnect = Arrays.asList(16, 17, 11, 18, 5);
    private BluetoothA2dpSink mBluetoothA2dpSink = null;
    private BluetoothHeadsetClient mBluetoothHeadsetClient = null;
    private BluetoothPbapClient mBluetoothPbapClient = null;
    private BluetoothMapClient mBluetoothMapClient = null;
    private BluetoothPan mBluetoothPan = null;
    private BluetoothProfile.ServiceListener mProfileListener = new BluetoothProfile.ServiceListener() { // from class: com.android.car.CarBluetoothUserService.1
        @Override // android.bluetooth.BluetoothProfile.ServiceListener
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            CarBluetoothUserService carBluetoothUserService = CarBluetoothUserService.this;
            carBluetoothUserService.logd("onServiceConnected profile: " + Utils.getProfileName(profile));
            CarBluetoothUserService.this.mBluetoothProxyLock.lock();
            try {
                if (profile == 5) {
                    CarBluetoothUserService.this.mBluetoothPan = (BluetoothPan) proxy;
                } else if (profile != 11) {
                    switch (profile) {
                        case 16:
                            CarBluetoothUserService.this.mBluetoothHeadsetClient = (BluetoothHeadsetClient) proxy;
                            break;
                        case 17:
                            CarBluetoothUserService.this.mBluetoothPbapClient = (BluetoothPbapClient) proxy;
                            break;
                        case 18:
                            CarBluetoothUserService.this.mBluetoothMapClient = (BluetoothMapClient) proxy;
                            break;
                        default:
                            CarBluetoothUserService carBluetoothUserService2 = CarBluetoothUserService.this;
                            carBluetoothUserService2.logd("Unhandled profile connected: " + Utils.getProfileName(profile));
                            break;
                    }
                } else {
                    CarBluetoothUserService.this.mBluetoothA2dpSink = (BluetoothA2dpSink) proxy;
                }
                if (!CarBluetoothUserService.this.mBluetoothProfileStatus.get(profile, false)) {
                    CarBluetoothUserService.this.mBluetoothProfileStatus.put(profile, true);
                    CarBluetoothUserService.access$808(CarBluetoothUserService.this);
                    if (CarBluetoothUserService.this.mConnectedProfiles == CarBluetoothUserService.sProfilesToConnect.size()) {
                        CarBluetoothUserService.this.logd("All profiles have connected");
                        CarBluetoothUserService.this.mConditionAllProxiesConnected.signal();
                    }
                } else {
                    Slog.w(CarBluetoothUserService.TAG, "Received duplicate service connection event for: " + Utils.getProfileName(profile));
                }
                CarBluetoothUserService.this.mBluetoothProxyLock.unlock();
            } catch (Throwable th) {
                CarBluetoothUserService.this.mBluetoothProxyLock.unlock();
                throw th;
            }
        }

        @Override // android.bluetooth.BluetoothProfile.ServiceListener
        public void onServiceDisconnected(int profile) {
            CarBluetoothUserService carBluetoothUserService = CarBluetoothUserService.this;
            carBluetoothUserService.logd("onServiceDisconnected profile: " + Utils.getProfileName(profile));
            CarBluetoothUserService.this.mBluetoothProxyLock.lock();
            try {
                if (CarBluetoothUserService.this.mBluetoothProfileStatus.get(profile, false)) {
                    CarBluetoothUserService.this.mBluetoothProfileStatus.put(profile, false);
                    CarBluetoothUserService.access$810(CarBluetoothUserService.this);
                } else {
                    Slog.w(CarBluetoothUserService.TAG, "Received duplicate service disconnection event for: " + Utils.getProfileName(profile));
                }
            } finally {
                CarBluetoothUserService.this.mBluetoothProxyLock.unlock();
            }
        }
    };
    private int mConnectedProfiles = 0;
    private SparseBooleanArray mBluetoothProfileStatus = new SparseBooleanArray();

    static /* synthetic */ int access$808(CarBluetoothUserService x0) {
        int i = x0.mConnectedProfiles;
        x0.mConnectedProfiles = i + 1;
        return i;
    }

    static /* synthetic */ int access$810(CarBluetoothUserService x0) {
        int i = x0.mConnectedProfiles;
        x0.mConnectedProfiles = i - 1;
        return i;
    }

    public CarBluetoothUserService(PerUserCarService service) {
        this.mService = service;
        for (Integer num : sProfilesToConnect) {
            int profile = num.intValue();
            this.mBluetoothProfileStatus.put(profile, false);
        }
        this.mBluetoothProxyLock = new ReentrantLock();
        this.mConditionAllProxiesConnected = this.mBluetoothProxyLock.newCondition();
        this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Preconditions.checkNotNull(this.mBluetoothAdapter, "Bluetooth adapter cannot be null");
    }

    public void setupBluetoothConnectionProxies() {
        logd("Initiate connections to profile proxies");
        closeBluetoothConnectionProxies();
        for (Integer num : sProfilesToConnect) {
            int profile = num.intValue();
            logd("Creating proxy for " + Utils.getProfileName(profile));
            this.mBluetoothAdapter.getProfileProxy(this.mService.getApplicationContext(), this.mProfileListener, profile);
        }
    }

    public void closeBluetoothConnectionProxies() {
        logd("Clean up profile proxy objects");
        this.mBluetoothProxyLock.lock();
        try {
            this.mBluetoothAdapter.closeProfileProxy(11, this.mBluetoothA2dpSink);
            this.mBluetoothA2dpSink = null;
            this.mBluetoothProfileStatus.put(11, false);
            this.mBluetoothAdapter.closeProfileProxy(16, this.mBluetoothHeadsetClient);
            this.mBluetoothHeadsetClient = null;
            this.mBluetoothProfileStatus.put(16, false);
            this.mBluetoothAdapter.closeProfileProxy(17, this.mBluetoothPbapClient);
            this.mBluetoothPbapClient = null;
            this.mBluetoothProfileStatus.put(17, false);
            this.mBluetoothAdapter.closeProfileProxy(18, this.mBluetoothMapClient);
            this.mBluetoothMapClient = null;
            this.mBluetoothProfileStatus.put(18, false);
            this.mBluetoothAdapter.closeProfileProxy(5, this.mBluetoothPan);
            this.mBluetoothPan = null;
            this.mBluetoothProfileStatus.put(5, false);
            this.mConnectedProfiles = 0;
        } finally {
            this.mBluetoothProxyLock.unlock();
        }
    }

    public boolean isBluetoothConnectionProxyAvailable(int profile) {
        if (this.mBluetoothAdapter.isEnabled()) {
            this.mBluetoothProxyLock.lock();
            try {
                boolean proxyConnected = this.mBluetoothProfileStatus.get(profile, false);
                return proxyConnected;
            } finally {
                this.mBluetoothProxyLock.unlock();
            }
        }
        return false;
    }

    private boolean waitForProxies(int timeout) {
        logd("waitForProxies()");
        if (this.mBluetoothAdapter.isEnabled()) {
            do {
                try {
                    if (this.mConnectedProfiles == sProfilesToConnect.size()) {
                        return true;
                    }
                } catch (InterruptedException e) {
                    Slog.w(TAG, "waitForProxies: interrupted", e);
                    return false;
                }
            } while (this.mConditionAllProxiesConnected.await(timeout, TimeUnit.MILLISECONDS));
            Slog.e(TAG, "Timeout while waiting for proxies, Connected: " + this.mConnectedProfiles + "/" + sProfilesToConnect.size());
            return false;
        }
        return false;
    }

    public boolean bluetoothConnectToProfile(int profile, BluetoothDevice device) {
        if (device == null) {
            Slog.e(TAG, "Cannot connect to profile on null device");
            return false;
        }
        logd("Trying to connect to " + device.getName() + " (" + device.getAddress() + ") Profile: " + Utils.getProfileName(profile));
        this.mBluetoothProxyLock.lock();
        try {
            if (!isBluetoothConnectionProxyAvailable(profile) && !waitForProxies(PROXY_OPERATION_TIMEOUT_MS)) {
                Slog.e(TAG, "Cannot connect to Profile. Proxy Unavailable");
                return false;
            } else if (profile != 5) {
                if (profile != 11) {
                    switch (profile) {
                        case 16:
                            return this.mBluetoothHeadsetClient.connect(device);
                        case 17:
                            return this.mBluetoothPbapClient.connect(device);
                        case 18:
                            return this.mBluetoothMapClient.connect(device);
                        default:
                            Slog.w(TAG, "Unknown Profile: " + Utils.getProfileName(profile));
                            return false;
                    }
                }
                return this.mBluetoothA2dpSink.connect(device);
            } else {
                return this.mBluetoothPan.connect(device);
            }
        } finally {
            this.mBluetoothProxyLock.unlock();
        }
    }

    public boolean bluetoothDisconnectFromProfile(int profile, BluetoothDevice device) {
        if (device == null) {
            Slog.e(TAG, "Cannot disconnect from profile on null device");
            return false;
        }
        logd("Trying to disconnect from " + device.getName() + " (" + device.getAddress() + ") Profile: " + Utils.getProfileName(profile));
        this.mBluetoothProxyLock.lock();
        try {
            if (!isBluetoothConnectionProxyAvailable(profile) && !waitForProxies(PROXY_OPERATION_TIMEOUT_MS)) {
                Slog.e(TAG, "Cannot disconnect from profile. Proxy Unavailable");
                return false;
            } else if (profile != 5) {
                if (profile != 11) {
                    switch (profile) {
                        case 16:
                            return this.mBluetoothHeadsetClient.disconnect(device);
                        case 17:
                            return this.mBluetoothPbapClient.disconnect(device);
                        case 18:
                            return this.mBluetoothMapClient.disconnect(device);
                        default:
                            Slog.w(TAG, "Unknown Profile: " + Utils.getProfileName(profile));
                            return false;
                    }
                }
                return this.mBluetoothA2dpSink.disconnect(device);
            } else {
                return this.mBluetoothPan.disconnect(device);
            }
        } finally {
            this.mBluetoothProxyLock.unlock();
        }
    }

    public int getProfilePriority(int profile, BluetoothDevice device) {
        int priority;
        if (device == null) {
            Slog.e(TAG, "Cannot get " + Utils.getProfileName(profile) + " profile priority on null device");
            return -1;
        }
        this.mBluetoothProxyLock.lock();
        try {
            if (!isBluetoothConnectionProxyAvailable(profile) && !waitForProxies(PROXY_OPERATION_TIMEOUT_MS)) {
                Slog.e(TAG, "Cannot get " + Utils.getProfileName(profile) + " profile priority. Proxy Unavailable");
                return -1;
            }
            if (profile == 11) {
                priority = this.mBluetoothA2dpSink.getPriority(device);
            } else {
                switch (profile) {
                    case 16:
                        priority = this.mBluetoothHeadsetClient.getPriority(device);
                        break;
                    case 17:
                        priority = this.mBluetoothPbapClient.getPriority(device);
                        break;
                    case 18:
                        priority = this.mBluetoothMapClient.getPriority(device);
                        break;
                    default:
                        Slog.w(TAG, "Unknown Profile: " + Utils.getProfileName(profile));
                        priority = -1;
                        break;
                }
            }
            this.mBluetoothProxyLock.unlock();
            logd(Utils.getProfileName(profile) + " priority for " + device.getName() + " (" + device.getAddress() + ") = " + priority);
            return priority;
        } finally {
            this.mBluetoothProxyLock.unlock();
        }
    }

    public void setProfilePriority(int profile, BluetoothDevice device, int priority) {
        if (device == null) {
            Slog.e(TAG, "Cannot set " + Utils.getProfileName(profile) + " profile priority on null device");
            return;
        }
        logd("Setting " + Utils.getProfileName(profile) + " priority for " + device.getName() + " (" + device.getAddress() + ") to " + priority);
        this.mBluetoothProxyLock.lock();
        try {
            if (!isBluetoothConnectionProxyAvailable(profile) && !waitForProxies(PROXY_OPERATION_TIMEOUT_MS)) {
                Slog.e(TAG, "Cannot set " + Utils.getProfileName(profile) + " profile priority. Proxy Unavailable");
                return;
            }
            if (profile == 11) {
                this.mBluetoothA2dpSink.setPriority(device, priority);
            } else {
                switch (profile) {
                    case 16:
                        this.mBluetoothHeadsetClient.setPriority(device, priority);
                        break;
                    case 17:
                        this.mBluetoothPbapClient.setPriority(device, priority);
                        break;
                    case 18:
                        this.mBluetoothMapClient.setPriority(device, priority);
                        break;
                    default:
                        Slog.w(TAG, "Unknown Profile: " + Utils.getProfileName(profile));
                        break;
                }
            }
        } finally {
            this.mBluetoothProxyLock.unlock();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void logd(String msg) {
        if (DBG) {
            Slog.d(TAG, msg);
        }
    }
}
