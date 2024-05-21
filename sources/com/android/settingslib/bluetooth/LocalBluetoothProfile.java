package com.android.settingslib.bluetooth;

import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
/* loaded from: classes3.dex */
public interface LocalBluetoothProfile {
    boolean accessProfileEnabled();

    boolean connect(BluetoothDevice bluetoothDevice);

    boolean disconnect(BluetoothDevice bluetoothDevice);

    int getConnectionStatus(BluetoothDevice bluetoothDevice);

    int getDrawableResource(BluetoothClass bluetoothClass);

    int getNameResource(BluetoothDevice bluetoothDevice);

    int getOrdinal();

    int getPreferred(BluetoothDevice bluetoothDevice);

    int getProfileId();

    int getSummaryResourceForDevice(BluetoothDevice bluetoothDevice);

    boolean isAutoConnectable();

    boolean isPreferred(BluetoothDevice bluetoothDevice);

    boolean isProfileReady();

    void setPreferred(BluetoothDevice bluetoothDevice, boolean z);
}
