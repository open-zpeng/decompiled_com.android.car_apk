package com.android.settingslib.bluetooth;
/* loaded from: classes3.dex */
public interface BluetoothCallback {
    default void onBluetoothStateChanged(int bluetoothState) {
    }

    default void onScanningStateChanged(boolean started) {
    }

    default void onDeviceAdded(CachedBluetoothDevice cachedDevice) {
    }

    default void onDeviceDeleted(CachedBluetoothDevice cachedDevice) {
    }

    default void onDeviceBondStateChanged(CachedBluetoothDevice cachedDevice, int bondState) {
    }

    default void onConnectionStateChanged(CachedBluetoothDevice cachedDevice, int state) {
    }

    default void onActiveDeviceChanged(CachedBluetoothDevice activeDevice, int bluetoothProfile) {
    }

    default void onAudioModeChanged() {
    }

    default void onProfileConnectionStateChanged(CachedBluetoothDevice cachedDevice, int state, int bluetoothProfile) {
    }

    default void onAclConnectionStateChanged(CachedBluetoothDevice cachedDevice, int state) {
    }
}
