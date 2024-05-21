package com.android.car;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.car.hardware.power.CarPowerManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.util.Slog;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
/* loaded from: classes3.dex */
public class BluetoothDeviceConnectionPolicy {
    private final CarBluetoothService mCarBluetoothService;
    private CarPowerManager mCarPowerManager;
    private final Context mContext;
    private boolean mEnableBluetoothPowerManager;
    private final int mUserId;
    private static final String TAG = "BluetoothDeviceConnectionPolicy";
    private static final boolean DBG = Log.isLoggable(TAG, 3);
    private final CarPowerManager.CarPowerStateListenerWithCompletion mCarPowerStateListener = new CarPowerManager.CarPowerStateListenerWithCompletion() { // from class: com.android.car.BluetoothDeviceConnectionPolicy.1
        public void onStateChanged(int state, CompletableFuture<Void> future) {
            BluetoothDeviceConnectionPolicy.logd("Car power state has changed to " + state);
            if (state == 6) {
                BluetoothDeviceConnectionPolicy.logd("Car is powering on. Enable Bluetooth and auto-connect to devices");
                if (BluetoothDeviceConnectionPolicy.this.isBluetoothPersistedOn()) {
                    BluetoothDeviceConnectionPolicy.this.enableBluetooth();
                }
                if (BluetoothDeviceConnectionPolicy.this.mBluetoothAdapter.getState() == 12) {
                    BluetoothDeviceConnectionPolicy.this.connectDevices();
                }
            } else if (state == 7) {
                BluetoothDeviceConnectionPolicy.logd("Car is preparing for shutdown. Disable bluetooth adapter");
                BluetoothDeviceConnectionPolicy.this.disableBluetooth();
                if (future != null) {
                    future.complete(null);
                }
            }
        }
    };
    private final BluetoothBroadcastReceiver mBluetoothBroadcastReceiver = new BluetoothBroadcastReceiver();
    private final BluetoothAdapter mBluetoothAdapter = (BluetoothAdapter) Objects.requireNonNull(BluetoothAdapter.getDefaultAdapter());

    public CarPowerManager.CarPowerStateListenerWithCompletion getCarPowerStateListener() {
        return this.mCarPowerStateListener;
    }

    /* loaded from: classes3.dex */
    private class BluetoothBroadcastReceiver extends BroadcastReceiver {
        private BluetoothBroadcastReceiver() {
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice bluetoothDevice = (BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
            if ("android.bluetooth.adapter.action.STATE_CHANGED".equals(action)) {
                int state = intent.getIntExtra("android.bluetooth.adapter.extra.STATE", -1);
                BluetoothDeviceConnectionPolicy.logd("Bluetooth Adapter state changed: " + Utils.getAdapterStateName(state));
                if (state == 12) {
                    BluetoothDeviceConnectionPolicy.this.connectDevices();
                }
            }
        }
    }

    public static BluetoothDeviceConnectionPolicy create(Context context, int userId, CarBluetoothService bluetoothService) {
        try {
            return new BluetoothDeviceConnectionPolicy(context, userId, bluetoothService);
        } catch (NullPointerException e) {
            return null;
        }
    }

    private BluetoothDeviceConnectionPolicy(Context context, int userId, CarBluetoothService bluetoothService) {
        this.mUserId = userId;
        this.mContext = (Context) Objects.requireNonNull(context);
        this.mCarBluetoothService = bluetoothService;
    }

    public void init() {
        logd("init()");
        IntentFilter profileFilter = new IntentFilter();
        profileFilter.addAction("android.bluetooth.adapter.action.STATE_CHANGED");
        this.mContext.registerReceiverAsUser(this.mBluetoothBroadcastReceiver, UserHandle.CURRENT, profileFilter, null, null);
        this.mEnableBluetoothPowerManager = isBluetoothPowerManagerEnabled(this.mContext);
        logd("Enable Bluetooth power manager: " + this.mEnableBluetoothPowerManager);
        if (this.mEnableBluetoothPowerManager) {
            this.mCarPowerManager = CarLocalServices.createCarPowerManager(this.mContext);
            CarPowerManager carPowerManager = this.mCarPowerManager;
            if (carPowerManager != null) {
                carPowerManager.setListenerWithCompletion(this.mCarPowerStateListener);
            } else {
                logd("Failed to get car power manager");
            }
        }
        if (this.mBluetoothAdapter.getState() == 12) {
            connectDevices();
        }
    }

    public void release() {
        logd("release()");
        CarPowerManager carPowerManager = this.mCarPowerManager;
        if (carPowerManager != null) {
            carPowerManager.clearListener();
            this.mCarPowerManager = null;
        }
        BluetoothBroadcastReceiver bluetoothBroadcastReceiver = this.mBluetoothBroadcastReceiver;
        if (bluetoothBroadcastReceiver != null) {
            this.mContext.unregisterReceiver(bluetoothBroadcastReceiver);
        }
    }

    public void connectDevices() {
        logd("Connect devices for each profile");
        this.mCarBluetoothService.connectDevices();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isBluetoothPersistedOn() {
        return Settings.Global.getInt(this.mContext.getContentResolver(), "bluetooth_on", -1) != 0;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void enableBluetooth() {
        logd("Enable bluetooth adapter");
        BluetoothAdapter bluetoothAdapter = this.mBluetoothAdapter;
        if (bluetoothAdapter == null) {
            Slog.e(TAG, "Cannot enable Bluetooth adapter. The object is null.");
        } else {
            bluetoothAdapter.enable();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void disableBluetooth() {
        logd("Disable bluetooth, do not persist state across reboot");
        BluetoothAdapter bluetoothAdapter = this.mBluetoothAdapter;
        if (bluetoothAdapter == null) {
            Slog.e(TAG, "Cannot disable Bluetooth adapter. The object is null.");
        } else {
            bluetoothAdapter.disable(false);
        }
    }

    private boolean isBluetoothPowerManagerEnabled(Context context) {
        boolean enableAirplaneModeService = false;
        if (context != null) {
            enableAirplaneModeService = context.getResources().getBoolean(R.bool.enableAirplaneModeService);
        }
        return !enableAirplaneModeService;
    }

    public void dump(PrintWriter writer, String indent) {
        writer.println(indent + TAG + ":");
        writer.println(indent + "\tUserId: " + this.mUserId);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void logd(String msg) {
        if (DBG) {
            Slog.d(TAG, msg);
        }
    }
}
