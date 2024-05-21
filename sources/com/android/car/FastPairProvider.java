package com.android.car;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.res.Resources;
import android.os.ParcelUuid;
import android.util.Slog;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
/* JADX INFO: Access modifiers changed from: package-private */
/* loaded from: classes3.dex */
public class FastPairProvider {
    private static final boolean DBG = Utils.DBG.booleanValue();
    private static final ParcelUuid FastPairServiceUuid = ParcelUuid.fromString("0000FE2C-0000-1000-8000-00805f9b34fb");
    private static final String TAG = "FastPairProvider";
    private AdvertiseCallback mAdvertiseCallback;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    private AdvertiseData mData;
    private AdvertiseSettings mSettings;

    FastPairProvider(Context context) {
        BluetoothAdapter bluetoothAdapter;
        Resources res = context.getResources();
        int modelId = res.getInteger(R.integer.fastPairModelId);
        if (modelId == 0) {
            Slog.w(TAG, "Model ID undefined, disabling");
            return;
        }
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService("bluetooth");
        if (bluetoothManager != null && (bluetoothAdapter = bluetoothManager.getAdapter()) != null) {
            this.mBluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        }
        AdvertiseSettings.Builder settingsBuilder = new AdvertiseSettings.Builder();
        settingsBuilder.setAdvertiseMode(2);
        settingsBuilder.setTxPowerLevel(3);
        settingsBuilder.setConnectable(true);
        settingsBuilder.setTimeout(0);
        this.mSettings = settingsBuilder.build();
        AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();
        ByteBuffer modelIdBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(modelId);
        byte[] fastPairServiceData = Arrays.copyOfRange(modelIdBytes.array(), 0, 3);
        dataBuilder.addServiceData(FastPairServiceUuid, fastPairServiceData);
        dataBuilder.setIncludeTxPowerLevel(true).build();
        this.mData = dataBuilder.build();
        this.mAdvertiseCallback = new FastPairAdvertiseCallback();
    }

    boolean startAdvertising() {
        BluetoothLeAdvertiser bluetoothLeAdvertiser = this.mBluetoothLeAdvertiser;
        if (bluetoothLeAdvertiser != null) {
            bluetoothLeAdvertiser.startAdvertising(this.mSettings, this.mData, this.mAdvertiseCallback);
            return true;
        }
        return false;
    }

    boolean stopAdvertising() {
        BluetoothLeAdvertiser bluetoothLeAdvertiser = this.mBluetoothLeAdvertiser;
        if (bluetoothLeAdvertiser != null) {
            bluetoothLeAdvertiser.stopAdvertising(this.mAdvertiseCallback);
            return true;
        }
        return false;
    }

    /* loaded from: classes3.dex */
    private class FastPairAdvertiseCallback extends AdvertiseCallback {
        private FastPairAdvertiseCallback() {
        }

        @Override // android.bluetooth.le.AdvertiseCallback
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            if (FastPairProvider.DBG) {
                Slog.d(FastPairProvider.TAG, "Advertising failed");
            }
        }

        @Override // android.bluetooth.le.AdvertiseCallback
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            if (FastPairProvider.DBG) {
                Slog.d(FastPairProvider.TAG, "Advertising successfully started");
            }
        }
    }
}
