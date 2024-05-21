package com.android.car.trust;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.util.Slog;
import androidx.annotation.Nullable;
import com.android.car.Utils;
import java.util.UUID;
/* loaded from: classes3.dex */
public abstract class BleManager {
    private static final int BLE_RETRY_INTERVAL_MS = 1000;
    private static final int BLE_RETRY_LIMIT = 5;
    private static final int GATT_SERVER_RETRY_DELAY_MS = 200;
    private static final int GATT_SERVER_RETRY_LIMIT = 20;
    private AdvertiseCallback mAdvertiseCallback;
    private BluetoothLeAdvertiser mAdvertiser;
    private int mAdvertiserStartCount;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattService mBluetoothGattService;
    private BluetoothManager mBluetoothManager;
    private final Context mContext;
    private AdvertiseData mData;
    private BluetoothGattServer mGattServer;
    private int mGattServerRetryStartCount;
    private static final String TAG = BleManager.class.getSimpleName();
    private static final UUID GENERIC_ACCESS_PROFILE_UUID = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb");
    private static final UUID DEVICE_NAME_UUID = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb");
    private final Handler mHandler = new Handler();
    private final BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() { // from class: com.android.car.trust.BleManager.1
        @Override // android.bluetooth.BluetoothGattServerCallback
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (Log.isLoggable(BleManager.TAG, 3)) {
                String str = BleManager.TAG;
                Slog.d(str, "BLE Connection State Change: " + newState);
            }
            if (newState == 0) {
                BleManager.this.onRemoteDeviceDisconnected(device);
            } else if (newState != 2) {
                String str2 = BleManager.TAG;
                Slog.w(str2, "Connection state not connecting or disconnecting; ignoring: " + newState);
            } else {
                BleManager.this.onRemoteDeviceConnected(device);
            }
        }

        @Override // android.bluetooth.BluetoothGattServerCallback
        public void onServiceAdded(int status, BluetoothGattService service) {
            if (Log.isLoggable(BleManager.TAG, 3)) {
                String str = BleManager.TAG;
                Slog.d(str, "Service added status: " + status + " uuid: " + service.getUuid());
            }
        }

        @Override // android.bluetooth.BluetoothGattServerCallback
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            if (Log.isLoggable(BleManager.TAG, 3)) {
                String str = BleManager.TAG;
                Slog.d(str, "Read request for characteristic: " + characteristic.getUuid());
            }
            BleManager.this.mGattServer.sendResponse(device, requestId, 0, offset, characteristic.getValue());
            BleManager.this.onCharacteristicRead(device, requestId, offset, characteristic);
        }

        @Override // android.bluetooth.BluetoothGattServerCallback
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            if (Log.isLoggable(BleManager.TAG, 3)) {
                String str = BleManager.TAG;
                Slog.d(str, "Write request for characteristic: " + characteristic.getUuid() + "value: " + Utils.byteArrayToHexString(value));
            }
            BleManager.this.mGattServer.sendResponse(device, requestId, 0, offset, value);
            BleManager.this.onCharacteristicWrite(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
        }

        @Override // android.bluetooth.BluetoothGattServerCallback
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            if (Log.isLoggable(BleManager.TAG, 3)) {
                String str = BleManager.TAG;
                Slog.d(str, "Write request for descriptor: " + descriptor.getUuid() + "; value: " + Utils.byteArrayToHexString(value));
            }
            BleManager.this.mGattServer.sendResponse(device, requestId, 0, offset, value);
        }

        @Override // android.bluetooth.BluetoothGattServerCallback
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            if (Log.isLoggable(BleManager.TAG, 3)) {
                String str = BleManager.TAG;
                Slog.d(str, "onMtuChanged: " + mtu + " for device " + device.getAddress());
            }
            BleManager.this.onMtuSizeChanged(mtu);
        }
    };
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() { // from class: com.android.car.trust.BleManager.2
        @Override // android.bluetooth.BluetoothGattCallback
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (Log.isLoggable(BleManager.TAG, 3)) {
                String str = BleManager.TAG;
                Slog.d(str, "Gatt Connection State Change: " + newState);
            }
            if (newState == 0) {
                if (Log.isLoggable(BleManager.TAG, 3)) {
                    Slog.d(BleManager.TAG, "Gatt Disconnected");
                }
            } else if (newState != 2) {
                if (Log.isLoggable(BleManager.TAG, 3)) {
                    String str2 = BleManager.TAG;
                    Slog.d(str2, "Connection state not connecting or disconnecting; ignoring: " + newState);
                }
            } else {
                if (Log.isLoggable(BleManager.TAG, 3)) {
                    Slog.d(BleManager.TAG, "Gatt connected");
                }
                BleManager.this.mBluetoothGatt.discoverServices();
            }
        }

        @Override // android.bluetooth.BluetoothGattCallback
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (Log.isLoggable(BleManager.TAG, 3)) {
                Slog.d(BleManager.TAG, "Gatt Services Discovered");
            }
            BluetoothGattService gapService = BleManager.this.mBluetoothGatt.getService(BleManager.GENERIC_ACCESS_PROFILE_UUID);
            if (gapService == null) {
                Slog.e(BleManager.TAG, "Generic Access Service is Null");
                return;
            }
            BluetoothGattCharacteristic deviceNameCharacteristic = gapService.getCharacteristic(BleManager.DEVICE_NAME_UUID);
            if (deviceNameCharacteristic == null) {
                Slog.e(BleManager.TAG, "Device Name Characteristic is Null");
            } else {
                BleManager.this.mBluetoothGatt.readCharacteristic(deviceNameCharacteristic);
            }
        }

        @Override // android.bluetooth.BluetoothGattCallback
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status != 0) {
                String str = BleManager.TAG;
                Slog.e(str, "Reading GAP Failed: " + status);
                return;
            }
            String deviceName = characteristic.getStringValue(0);
            if (Log.isLoggable(BleManager.TAG, 3)) {
                String str2 = BleManager.TAG;
                Slog.d(str2, "BLE Device Name: " + deviceName);
            }
            BleManager.this.onDeviceNameRetrieved(deviceName);
        }
    };

    protected abstract void onCharacteristicRead(BluetoothDevice bluetoothDevice, int i, int i2, BluetoothGattCharacteristic bluetoothGattCharacteristic);

    protected abstract void onCharacteristicWrite(BluetoothDevice bluetoothDevice, int i, BluetoothGattCharacteristic bluetoothGattCharacteristic, boolean z, boolean z2, int i2, byte[] bArr);

    /* JADX INFO: Access modifiers changed from: package-private */
    public BleManager(Context context) {
        this.mContext = context;
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public void startAdvertising(BluetoothGattService service, AdvertiseData data, AdvertiseCallback advertiseCallback) {
        if (Log.isLoggable(TAG, 3)) {
            String str = TAG;
            Slog.d(str, "startAdvertising: " + service.getUuid().toString());
        }
        if (!this.mContext.getPackageManager().hasSystemFeature("android.hardware.bluetooth_le")) {
            Slog.e(TAG, "System does not support BLE");
            return;
        }
        this.mBluetoothGattService = service;
        this.mAdvertiseCallback = advertiseCallback;
        this.mData = data;
        this.mGattServerRetryStartCount = 0;
        this.mBluetoothManager = (BluetoothManager) this.mContext.getSystemService("bluetooth");
        lambda$openGattServer$0$BleManager();
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* renamed from: openGattServer */
    public void lambda$openGattServer$0$BleManager() {
        if (this.mGattServer == null) {
            if (this.mGattServerRetryStartCount < 20) {
                this.mGattServer = this.mBluetoothManager.openGattServer(this.mContext, this.mGattServerCallback);
                this.mGattServerRetryStartCount++;
                this.mHandler.postDelayed(new Runnable() { // from class: com.android.car.trust.-$$Lambda$BleManager$-n3T9QeJVwkSM0RCsARFvrCJw3A
                    @Override // java.lang.Runnable
                    public final void run() {
                        BleManager.this.lambda$openGattServer$0$BleManager();
                    }
                }, 200L);
                return;
            }
            Slog.e(TAG, "Gatt server not created - exceeded retry limit.");
            return;
        }
        if (Log.isLoggable(TAG, 3)) {
            Slog.d(TAG, "Gatt Server created, retry count: " + this.mGattServerRetryStartCount);
        }
        this.mGattServer.clearServices();
        this.mGattServer.addService(this.mBluetoothGattService);
        AdvertiseSettings settings = new AdvertiseSettings.Builder().setAdvertiseMode(2).setTxPowerLevel(3).setConnectable(true).build();
        this.mAdvertiserStartCount = 0;
        lambda$startAdvertisingInternally$1$BleManager(settings, this.mData, this.mAdvertiseCallback);
        this.mGattServerRetryStartCount = 0;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* renamed from: startAdvertisingInternally */
    public void lambda$startAdvertisingInternally$1$BleManager(final AdvertiseSettings settings, final AdvertiseData data, final AdvertiseCallback advertiseCallback) {
        if (BluetoothAdapter.getDefaultAdapter() != null) {
            this.mAdvertiser = BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser();
        }
        if (this.mAdvertiser != null) {
            if (Log.isLoggable(TAG, 3)) {
                Slog.d(TAG, "Advertiser created, retry count: " + this.mAdvertiserStartCount);
            }
            this.mAdvertiser.startAdvertising(settings, data, advertiseCallback);
            this.mAdvertiserStartCount = 0;
        } else if (this.mAdvertiserStartCount < 5) {
            this.mHandler.postDelayed(new Runnable() { // from class: com.android.car.trust.-$$Lambda$BleManager$Dq-f1eKAFssjT5au7PXFoKDOgOI
                @Override // java.lang.Runnable
                public final void run() {
                    BleManager.this.lambda$startAdvertisingInternally$1$BleManager(settings, data, advertiseCallback);
                }
            }, 1000L);
            this.mAdvertiserStartCount++;
        } else {
            Slog.e(TAG, "Cannot start BLE Advertisement.  BT Adapter: " + BluetoothAdapter.getDefaultAdapter() + " Advertise Retry count: " + this.mAdvertiserStartCount);
        }
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public void stopAdvertising(AdvertiseCallback advertiseCallback) {
        if (this.mAdvertiser != null) {
            if (Log.isLoggable(TAG, 3)) {
                Slog.d(TAG, "stopAdvertising: ");
            }
            this.mAdvertiser.stopAdvertising(advertiseCallback);
        }
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public void notifyCharacteristicChanged(BluetoothDevice device, BluetoothGattCharacteristic characteristic, boolean confirm) {
        BluetoothGattServer bluetoothGattServer = this.mGattServer;
        if (bluetoothGattServer == null) {
            return;
        }
        boolean result = bluetoothGattServer.notifyCharacteristicChanged(device, characteristic, confirm);
        if (Log.isLoggable(TAG, 3)) {
            String str = TAG;
            Slog.d(str, "notifyCharacteristicChanged succeeded: " + result);
        }
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public final void retrieveDeviceName(BluetoothDevice device) {
        this.mBluetoothGatt = device.connectGatt(getContext(), false, this.mGattCallback);
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public Context getContext() {
        return this.mContext;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void cleanup() {
        BluetoothLeAdvertiser bluetoothLeAdvertiser = this.mAdvertiser;
        if (bluetoothLeAdvertiser != null) {
            bluetoothLeAdvertiser.cleanup();
        }
        BluetoothGattServer bluetoothGattServer = this.mGattServer;
        if (bluetoothGattServer != null) {
            bluetoothGattServer.clearServices();
            try {
                try {
                    for (BluetoothDevice d : this.mBluetoothManager.getConnectedDevices(8)) {
                        this.mGattServer.cancelConnection(d);
                    }
                } catch (UnsupportedOperationException e) {
                    Slog.e(TAG, "Error getting connected devices", e);
                }
            } finally {
                stopGattServer();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void stopGattServer() {
        if (this.mGattServer == null) {
            return;
        }
        if (Log.isLoggable(TAG, 3)) {
            Slog.d(TAG, "stopGattServer");
        }
        BluetoothGatt bluetoothGatt = this.mBluetoothGatt;
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
        }
        this.mGattServer.close();
        this.mGattServer = null;
    }

    protected void onDeviceNameRetrieved(@Nullable String deviceName) {
    }

    protected void onMtuSizeChanged(int size) {
    }

    protected void onRemoteDeviceConnected(BluetoothDevice device) {
    }

    protected void onRemoteDeviceDisconnected(BluetoothDevice device) {
    }
}
