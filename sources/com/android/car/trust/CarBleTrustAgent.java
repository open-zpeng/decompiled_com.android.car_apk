package com.android.car.trust;

import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.car.trust.TrustedDeviceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.UserHandle;
import android.os.UserManager;
import android.service.trust.TrustAgentService;
import android.util.Log;
import android.util.Slog;
import com.android.car.CarLocalServices;
import com.android.car.Utils;
import com.android.car.trust.CarTrustAgentEnrollmentService;
import com.android.car.trust.CarTrustAgentUnlockService;
import java.util.List;
/* loaded from: classes3.dex */
public class CarBleTrustAgent extends TrustAgentService {
    private static final String TAG = CarBleTrustAgent.class.getSimpleName();
    private CarTrustAgentEnrollmentService mCarTrustAgentEnrollmentService;
    private CarTrustAgentUnlockService mCarTrustAgentUnlockService;
    private CarTrustedDeviceService mCarTrustedDeviceService;
    private boolean mIsDeviceLocked;
    private final BroadcastReceiver mBluetoothBroadcastReceiver = new BroadcastReceiver() { // from class: com.android.car.trust.CarBleTrustAgent.1
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null && "android.bluetooth.adapter.action.BLE_STATE_CHANGED".equals(intent.getAction())) {
                CarBleTrustAgent.this.onBluetoothStateChanged(intent.getIntExtra("android.bluetooth.adapter.extra.STATE", -1));
            }
        }
    };
    private final CarTrustAgentEnrollmentService.CarTrustAgentEnrollmentRequestDelegate mEnrollDelegate = new CarTrustAgentEnrollmentService.CarTrustAgentEnrollmentRequestDelegate() { // from class: com.android.car.trust.CarBleTrustAgent.2
        @Override // com.android.car.trust.CarTrustAgentEnrollmentService.CarTrustAgentEnrollmentRequestDelegate
        public void addEscrowToken(byte[] token, int uid) {
            if (Log.isLoggable(CarBleTrustAgent.TAG, 3)) {
                String str = CarBleTrustAgent.TAG;
                Slog.d(str, "addEscrowToken. uid: " + uid + " token: " + Utils.byteArrayToHexString(token));
            }
            CarBleTrustAgent.this.addEscrowToken(token, UserHandle.of(uid));
        }

        @Override // com.android.car.trust.CarTrustAgentEnrollmentService.CarTrustAgentEnrollmentRequestDelegate
        public void removeEscrowToken(long handle, int uid) {
            if (Log.isLoggable(CarBleTrustAgent.TAG, 3)) {
                String str = CarBleTrustAgent.TAG;
                Slog.d(str, "removeEscrowToken. uid: " + ActivityManager.getCurrentUser() + " handle: " + handle);
            }
            CarBleTrustAgent.this.removeEscrowToken(handle, UserHandle.of(uid));
        }

        @Override // com.android.car.trust.CarTrustAgentEnrollmentService.CarTrustAgentEnrollmentRequestDelegate
        public void isEscrowTokenActive(long handle, int uid) {
            CarBleTrustAgent.this.isEscrowTokenActive(handle, UserHandle.of(uid));
        }
    };
    private final CarTrustAgentUnlockService.CarTrustAgentUnlockDelegate mUnlockDelegate = new CarTrustAgentUnlockService.CarTrustAgentUnlockDelegate() { // from class: com.android.car.trust.CarBleTrustAgent.3
        @Override // com.android.car.trust.CarTrustAgentUnlockService.CarTrustAgentUnlockDelegate
        public void onUnlockDataReceived(int user, byte[] token, long handle) {
            if (Log.isLoggable(CarBleTrustAgent.TAG, 3)) {
                String str = CarBleTrustAgent.TAG;
                Slog.d(str, "onUnlockDataReceived:" + user + " token: " + Long.toHexString(Utils.bytesToLong(token)) + " handle: " + Long.toHexString(handle));
            }
            if (ActivityManager.getCurrentUser() != user) {
                String str2 = CarBleTrustAgent.TAG;
                Slog.e(str2, "Expected User: " + ActivityManager.getCurrentUser() + " Presented User: " + user);
                return;
            }
            CarBleTrustAgent.this.unlockUserInternally(user, token, handle);
            EventLog.logUnlockEvent("USER_UNLOCKED");
        }
    };

    public void onCreate() {
        if (Log.isLoggable(TAG, 3)) {
            Slog.d(TAG, "onCreate()");
        }
        super.onCreate();
        IntentFilter intentFilter = new IntentFilter("android.bluetooth.adapter.action.BLE_STATE_CHANGED");
        registerReceiver(this.mBluetoothBroadcastReceiver, intentFilter);
        this.mCarTrustedDeviceService = (CarTrustedDeviceService) CarLocalServices.getService(CarTrustedDeviceService.class);
        CarTrustedDeviceService carTrustedDeviceService = this.mCarTrustedDeviceService;
        if (carTrustedDeviceService == null) {
            Slog.e(TAG, "Cannot retrieve the Trusted device Service");
            return;
        }
        this.mCarTrustAgentEnrollmentService = carTrustedDeviceService.getCarTrustAgentEnrollmentService();
        setEnrollmentRequestDelegate();
        this.mCarTrustAgentUnlockService = this.mCarTrustedDeviceService.getCarTrustAgentUnlockService();
        setUnlockRequestDelegate();
        setManagingTrust(true);
    }

    public void onDestroy() {
        if (Log.isLoggable(TAG, 3)) {
            Slog.d(TAG, "Car Trust agent shutting down");
        }
        super.onDestroy();
        this.mCarTrustAgentEnrollmentService = null;
        BroadcastReceiver broadcastReceiver = this.mBluetoothBroadcastReceiver;
        if (broadcastReceiver != null) {
            unregisterReceiver(broadcastReceiver);
        }
    }

    public void onDeviceLocked() {
        CarTrustAgentUnlockService carTrustAgentUnlockService;
        int uid = ActivityManager.getCurrentUser();
        if (Log.isLoggable(TAG, 3)) {
            String str = TAG;
            Slog.d(str, "onDeviceLocked Current user: " + uid);
        }
        super.onDeviceLocked();
        this.mIsDeviceLocked = true;
        if (!hasTrustedDevice(uid)) {
            if (Log.isLoggable(TAG, 3)) {
                String str2 = TAG;
                Slog.d(str2, "Not starting Unlock Advertising yet, since current user: " + uid + "has no trusted device");
            }
        } else if (isBluetoothAvailable() && (carTrustAgentUnlockService = this.mCarTrustAgentUnlockService) != null) {
            carTrustAgentUnlockService.startUnlockAdvertising();
        }
    }

    public void onDeviceUnlocked() {
        CarTrustAgentUnlockService carTrustAgentUnlockService;
        if (Log.isLoggable(TAG, 3)) {
            String str = TAG;
            Slog.d(str, "onDeviceUnlocked Current user: " + ActivityManager.getCurrentUser());
        }
        super.onDeviceUnlocked();
        this.mIsDeviceLocked = false;
        if (isBluetoothAvailable() && (carTrustAgentUnlockService = this.mCarTrustAgentUnlockService) != null) {
            carTrustAgentUnlockService.stopUnlockAdvertising();
        }
    }

    private boolean isBluetoothAvailable() {
        BluetoothAdapter defaultAdapter = BluetoothAdapter.getDefaultAdapter();
        if (defaultAdapter == null) {
            if (Log.isLoggable(TAG, 3)) {
                Slog.d(TAG, "Bluetooth Adapter null.");
            }
            return false;
        } else if (defaultAdapter.getState() == 10) {
            if (Log.isLoggable(TAG, 3)) {
                Slog.d(TAG, "Bluetooth Adapter is off");
            }
            return false;
        } else {
            return true;
        }
    }

    public void onEscrowTokenRemoved(long handle, boolean successful) {
        if (Log.isLoggable(TAG, 3)) {
            String str = TAG;
            Slog.d(str, "onEscrowTokenRemoved handle: " + Long.toHexString(handle));
        }
        CarTrustAgentEnrollmentService carTrustAgentEnrollmentService = this.mCarTrustAgentEnrollmentService;
        if (carTrustAgentEnrollmentService != null && successful) {
            carTrustAgentEnrollmentService.onEscrowTokenRemoved(handle, ActivityManager.getCurrentUser());
        }
    }

    public void onEscrowTokenStateReceived(long handle, int tokenState) {
        if (Log.isLoggable(TAG, 3)) {
            String str = TAG;
            Slog.d(str, "onEscrowTokenStateReceived: " + Long.toHexString(handle) + " state: " + tokenState);
        }
        CarTrustAgentEnrollmentService carTrustAgentEnrollmentService = this.mCarTrustAgentEnrollmentService;
        if (carTrustAgentEnrollmentService == null) {
            return;
        }
        carTrustAgentEnrollmentService.onEscrowTokenActiveStateChanged(handle, tokenState == 1, ActivityManager.getCurrentUser());
    }

    public void onEscrowTokenAdded(byte[] token, long handle, UserHandle user) {
        if (Log.isLoggable(TAG, 3)) {
            String str = TAG;
            Slog.d(str, "onEscrowTokenAdded handle: " + Long.toHexString(handle) + " token: " + Utils.byteArrayToHexString(token));
        }
        CarTrustAgentEnrollmentService carTrustAgentEnrollmentService = this.mCarTrustAgentEnrollmentService;
        if (carTrustAgentEnrollmentService == null) {
            return;
        }
        carTrustAgentEnrollmentService.onEscrowTokenAdded(token, handle, user.getIdentifier());
    }

    private void setEnrollmentRequestDelegate() {
        CarTrustAgentEnrollmentService carTrustAgentEnrollmentService = this.mCarTrustAgentEnrollmentService;
        if (carTrustAgentEnrollmentService == null) {
            return;
        }
        carTrustAgentEnrollmentService.setEnrollmentRequestDelegate(this.mEnrollDelegate);
    }

    private void setUnlockRequestDelegate() {
        CarTrustAgentUnlockService carTrustAgentUnlockService = this.mCarTrustAgentUnlockService;
        if (carTrustAgentUnlockService == null) {
            return;
        }
        carTrustAgentUnlockService.setUnlockRequestDelegate(this.mUnlockDelegate);
    }

    private boolean hasTrustedDevice(int uid) {
        List<TrustedDeviceInfo> trustedDeviceInfos;
        CarTrustAgentEnrollmentService carTrustAgentEnrollmentService = this.mCarTrustAgentEnrollmentService;
        return (carTrustAgentEnrollmentService == null || (trustedDeviceInfos = carTrustAgentEnrollmentService.getEnrolledDeviceInfosForUser(uid)) == null || trustedDeviceInfos.size() <= 0) ? false : true;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void unlockUserInternally(int uid, byte[] token, long handle) {
        if (Log.isLoggable(TAG, 3)) {
            String str = TAG;
            Slog.d(str, "About to unlock user: " + uid);
            UserManager um = (UserManager) getSystemService("user");
            if (um.isUserUnlocked(UserHandle.of(uid))) {
                Slog.d(TAG, "User currently unlocked");
            } else {
                Slog.d(TAG, "User currently locked");
            }
        }
        unlockUserWithToken(handle, token, UserHandle.of(uid));
        grantTrust("Granting trust from escrow token", 0L, 2);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onBluetoothStateChanged(int state) {
        if (Log.isLoggable(TAG, 3)) {
            String str = TAG;
            Slog.d(str, "onBluetoothStateChanged: " + state);
        }
        if (!this.mIsDeviceLocked) {
            return;
        }
        EventLog.logUnlockEvent("BLUETOOTH_STATE_CHANGED", state);
        if (state == 10) {
            Slog.e(TAG, "Bluetooth Adapter Off in lock screen");
            CarTrustedDeviceService carTrustedDeviceService = this.mCarTrustedDeviceService;
            if (carTrustedDeviceService != null) {
                carTrustedDeviceService.cleanupBleService();
            }
        } else if (state == 15) {
            int uid = ActivityManager.getCurrentUser();
            if (this.mCarTrustAgentUnlockService != null && hasTrustedDevice(uid)) {
                this.mCarTrustAgentUnlockService.startUnlockAdvertising();
            }
        }
    }
}
