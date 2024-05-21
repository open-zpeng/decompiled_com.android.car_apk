package com.android.settingslib.media;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import com.android.settingslib.R;
import com.android.settingslib.bluetooth.A2dpProfile;
import com.android.settingslib.bluetooth.BluetoothUtils;
import com.android.settingslib.bluetooth.HearingAidProfile;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.bluetooth.LocalBluetoothProfileManager;
/* loaded from: classes3.dex */
public class PhoneMediaDevice extends MediaDevice {
    public static final String ID = "phone_media_device_id_1";
    private static final String TAG = "PhoneMediaDevice";
    private LocalBluetoothManager mLocalBluetoothManager;
    private LocalBluetoothProfileManager mProfileManager;
    private String mSummary;

    /* JADX INFO: Access modifiers changed from: package-private */
    public PhoneMediaDevice(Context context, LocalBluetoothManager localBluetoothManager) {
        super(context, 1);
        this.mSummary = "";
        this.mLocalBluetoothManager = localBluetoothManager;
        this.mProfileManager = this.mLocalBluetoothManager.getProfileManager();
        initDeviceRecord();
    }

    @Override // com.android.settingslib.media.MediaDevice
    public String getName() {
        return this.mContext.getString(R.string.media_transfer_this_device_name);
    }

    @Override // com.android.settingslib.media.MediaDevice
    public String getSummary() {
        return this.mSummary;
    }

    @Override // com.android.settingslib.media.MediaDevice
    public Drawable getIcon() {
        return BluetoothUtils.buildBtRainbowDrawable(this.mContext, this.mContext.getDrawable(R.drawable.ic_smartphone), getId().hashCode());
    }

    @Override // com.android.settingslib.media.MediaDevice
    public String getId() {
        return ID;
    }

    @Override // com.android.settingslib.media.MediaDevice
    public boolean connect() {
        HearingAidProfile hapProfile = this.mProfileManager.getHearingAidProfile();
        A2dpProfile a2dpProfile = this.mProfileManager.getA2dpProfile();
        boolean isConnected = false;
        if (hapProfile != null && a2dpProfile != null) {
            isConnected = hapProfile.setActiveDevice(null) && a2dpProfile.setActiveDevice(null);
        } else if (a2dpProfile != null) {
            isConnected = a2dpProfile.setActiveDevice(null);
        } else if (hapProfile != null) {
            isConnected = hapProfile.setActiveDevice(null);
        }
        updateSummary(isConnected);
        setConnectedRecord();
        Log.d(TAG, "connect() device : " + getName() + ", is selected : " + isConnected);
        return isConnected;
    }

    @Override // com.android.settingslib.media.MediaDevice
    public void disconnect() {
        updateSummary(false);
    }

    @Override // com.android.settingslib.media.MediaDevice
    public boolean isConnected() {
        return true;
    }

    public void updateSummary(boolean isActive) {
        String str;
        if (isActive) {
            str = this.mContext.getString(R.string.bluetooth_active_no_battery_level);
        } else {
            str = "";
        }
        this.mSummary = str;
    }
}
