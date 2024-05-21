package com.android.settingslib.media;

import android.bluetooth.BluetoothClass;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.Pair;
import com.android.settingslib.R;
import com.android.settingslib.bluetooth.BluetoothUtils;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
/* loaded from: classes3.dex */
public class BluetoothMediaDevice extends MediaDevice {
    private static final String TAG = "BluetoothMediaDevice";
    private CachedBluetoothDevice mCachedDevice;

    /* JADX INFO: Access modifiers changed from: package-private */
    public BluetoothMediaDevice(Context context, CachedBluetoothDevice device) {
        super(context, 3);
        this.mCachedDevice = device;
        initDeviceRecord();
    }

    @Override // com.android.settingslib.media.MediaDevice
    public String getName() {
        return this.mCachedDevice.getName();
    }

    @Override // com.android.settingslib.media.MediaDevice
    public String getSummary() {
        if (isConnected() || this.mCachedDevice.isBusy()) {
            return this.mCachedDevice.getConnectionSummary();
        }
        return this.mContext.getString(R.string.bluetooth_disconnected);
    }

    @Override // com.android.settingslib.media.MediaDevice
    public Drawable getIcon() {
        Pair<Drawable, String> pair = BluetoothUtils.getBtRainbowDrawableWithDescription(this.mContext, this.mCachedDevice);
        return (Drawable) pair.first;
    }

    @Override // com.android.settingslib.media.MediaDevice
    public String getId() {
        return MediaDeviceUtils.getId(this.mCachedDevice);
    }

    @Override // com.android.settingslib.media.MediaDevice
    public boolean connect() {
        boolean isConnected = this.mCachedDevice.setActive();
        setConnectedRecord();
        Log.d(TAG, "connect() device : " + getName() + ", is selected : " + isConnected);
        return isConnected;
    }

    @Override // com.android.settingslib.media.MediaDevice
    public void disconnect() {
    }

    public CachedBluetoothDevice getCachedDevice() {
        return this.mCachedDevice;
    }

    @Override // com.android.settingslib.media.MediaDevice
    protected boolean isCarKitDevice() {
        BluetoothClass bluetoothClass = this.mCachedDevice.getDevice().getBluetoothClass();
        if (bluetoothClass != null) {
            int deviceClass = bluetoothClass.getDeviceClass();
            if (deviceClass == 1032 || deviceClass == 1056) {
                return true;
            }
            return false;
        }
        return false;
    }

    @Override // com.android.settingslib.media.MediaDevice
    public boolean isConnected() {
        return this.mCachedDevice.getBondState() == 12 && this.mCachedDevice.isConnected();
    }
}
