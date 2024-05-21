package com.android.settingslib.media;

import android.app.Notification;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;
import com.android.settingslib.bluetooth.A2dpProfile;
import com.android.settingslib.bluetooth.BluetoothCallback;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.CachedBluetoothDeviceManager;
import com.android.settingslib.bluetooth.HearingAidProfile;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.bluetooth.LocalBluetoothProfileManager;
import java.util.ArrayList;
import java.util.List;
/* loaded from: classes3.dex */
public class BluetoothMediaManager extends MediaManager implements BluetoothCallback, LocalBluetoothProfileManager.ServiceListener {
    private static final String TAG = "BluetoothMediaManager";
    private CachedBluetoothDeviceManager mCachedBluetoothDeviceManager;
    private final DeviceAttributeChangeCallback mDeviceAttributeChangeCallback;
    private boolean mIsA2dpProfileReady;
    private boolean mIsHearingAidProfileReady;
    private MediaDevice mLastAddedDevice;
    private MediaDevice mLastRemovedDevice;
    private LocalBluetoothManager mLocalBluetoothManager;
    private LocalBluetoothProfileManager mProfileManager;

    /* JADX INFO: Access modifiers changed from: package-private */
    public BluetoothMediaManager(Context context, LocalBluetoothManager localBluetoothManager, Notification notification) {
        super(context, notification);
        this.mDeviceAttributeChangeCallback = new DeviceAttributeChangeCallback();
        this.mIsA2dpProfileReady = false;
        this.mIsHearingAidProfileReady = false;
        this.mLocalBluetoothManager = localBluetoothManager;
        this.mProfileManager = this.mLocalBluetoothManager.getProfileManager();
        this.mCachedBluetoothDeviceManager = this.mLocalBluetoothManager.getCachedDeviceManager();
    }

    @Override // com.android.settingslib.media.MediaManager
    public void startScan() {
        this.mLocalBluetoothManager.getEventManager().registerCallback(this);
        buildBluetoothDeviceList();
        dispatchDeviceListAdded();
        addServiceListenerIfNecessary();
    }

    private void addServiceListenerIfNecessary() {
        if (!this.mIsA2dpProfileReady || !this.mIsHearingAidProfileReady) {
            this.mProfileManager.addServiceListener(this);
        }
    }

    private void buildBluetoothDeviceList() {
        this.mMediaDevices.clear();
        addConnectableA2dpDevices();
        addConnectableHearingAidDevices();
    }

    private void addConnectableA2dpDevices() {
        A2dpProfile a2dpProfile = this.mProfileManager.getA2dpProfile();
        if (a2dpProfile == null) {
            Log.w(TAG, "addConnectableA2dpDevices() a2dp profile is null!");
            return;
        }
        List<BluetoothDevice> devices = a2dpProfile.getConnectableDevices();
        for (BluetoothDevice device : devices) {
            CachedBluetoothDevice cachedDevice = this.mCachedBluetoothDeviceManager.findDevice(device);
            if (cachedDevice == null) {
                Log.w(TAG, "Can't found CachedBluetoothDevice : " + device.getName());
            } else {
                Log.d(TAG, "addConnectableA2dpDevices() device : " + cachedDevice.getName() + ", is connected : " + cachedDevice.isConnected() + ", is preferred : " + a2dpProfile.isPreferred(device));
                if (a2dpProfile.isPreferred(device) && 12 == cachedDevice.getBondState()) {
                    addMediaDevice(cachedDevice);
                }
            }
        }
        this.mIsA2dpProfileReady = a2dpProfile.isProfileReady();
    }

    private void addConnectableHearingAidDevices() {
        HearingAidProfile hapProfile = this.mProfileManager.getHearingAidProfile();
        if (hapProfile == null) {
            Log.w(TAG, "addConnectableHearingAidDevices() hap profile is null!");
            return;
        }
        List<Long> devicesHiSyncIds = new ArrayList<>();
        List<BluetoothDevice> devices = hapProfile.getConnectableDevices();
        for (BluetoothDevice device : devices) {
            CachedBluetoothDevice cachedDevice = this.mCachedBluetoothDeviceManager.findDevice(device);
            if (cachedDevice == null) {
                Log.w(TAG, "Can't found CachedBluetoothDevice : " + device.getName());
            } else {
                Log.d(TAG, "addConnectableHearingAidDevices() device : " + cachedDevice.getName() + ", is connected : " + cachedDevice.isConnected() + ", is preferred : " + hapProfile.isPreferred(device));
                long hiSyncId = hapProfile.getHiSyncId(device);
                if (!devicesHiSyncIds.contains(Long.valueOf(hiSyncId)) && hapProfile.isPreferred(device) && 12 == cachedDevice.getBondState()) {
                    devicesHiSyncIds.add(Long.valueOf(hiSyncId));
                    addMediaDevice(cachedDevice);
                }
            }
        }
        this.mIsHearingAidProfileReady = hapProfile.isProfileReady();
    }

    private void addMediaDevice(CachedBluetoothDevice cachedDevice) {
        if (findMediaDevice(MediaDeviceUtils.getId(cachedDevice)) == null) {
            MediaDevice mediaDevice = new BluetoothMediaDevice(this.mContext, cachedDevice);
            cachedDevice.registerCallback(this.mDeviceAttributeChangeCallback);
            this.mLastAddedDevice = mediaDevice;
            this.mMediaDevices.add(mediaDevice);
        }
    }

    @Override // com.android.settingslib.media.MediaManager
    public void stopScan() {
        this.mLocalBluetoothManager.getEventManager().unregisterCallback(this);
        unregisterDeviceAttributeChangeCallback();
    }

    private void unregisterDeviceAttributeChangeCallback() {
        for (MediaDevice device : this.mMediaDevices) {
            ((BluetoothMediaDevice) device).getCachedDevice().unregisterCallback(this.mDeviceAttributeChangeCallback);
        }
    }

    @Override // com.android.settingslib.bluetooth.BluetoothCallback
    public void onBluetoothStateChanged(int bluetoothState) {
        if (12 == bluetoothState) {
            buildBluetoothDeviceList();
            dispatchDeviceListAdded();
            addServiceListenerIfNecessary();
        } else if (10 == bluetoothState) {
            List<MediaDevice> removeDevicesList = new ArrayList<>();
            for (MediaDevice device : this.mMediaDevices) {
                ((BluetoothMediaDevice) device).getCachedDevice().unregisterCallback(this.mDeviceAttributeChangeCallback);
                removeDevicesList.add(device);
            }
            this.mMediaDevices.removeAll(removeDevicesList);
            dispatchDeviceListRemoved(removeDevicesList);
        }
    }

    @Override // com.android.settingslib.bluetooth.BluetoothCallback
    public void onAudioModeChanged() {
        dispatchDataChanged();
    }

    @Override // com.android.settingslib.bluetooth.BluetoothCallback
    public void onDeviceAdded(CachedBluetoothDevice cachedDevice) {
        if (isCachedDeviceConnected(cachedDevice)) {
            addMediaDevice(cachedDevice);
            dispatchDeviceAdded(cachedDevice);
        }
    }

    private boolean isCachedDeviceConnected(CachedBluetoothDevice cachedDevice) {
        boolean isConnectedHearingAidDevice = cachedDevice.isConnectedHearingAidDevice();
        boolean isConnectedA2dpDevice = cachedDevice.isConnectedA2dpDevice();
        Log.d(TAG, "isCachedDeviceConnected() cachedDevice : " + cachedDevice + ", is hearing aid connected : " + isConnectedHearingAidDevice + ", is a2dp connected : " + isConnectedA2dpDevice);
        return isConnectedHearingAidDevice || isConnectedA2dpDevice;
    }

    private void dispatchDeviceAdded(CachedBluetoothDevice cachedDevice) {
        if (this.mLastAddedDevice != null && MediaDeviceUtils.getId(cachedDevice) == this.mLastAddedDevice.getId()) {
            dispatchDeviceAdded(this.mLastAddedDevice);
        }
    }

    @Override // com.android.settingslib.bluetooth.BluetoothCallback
    public void onDeviceDeleted(CachedBluetoothDevice cachedDevice) {
        if (!isCachedDeviceConnected(cachedDevice)) {
            removeMediaDevice(cachedDevice);
            dispatchDeviceRemoved(cachedDevice);
        }
    }

    private void removeMediaDevice(CachedBluetoothDevice cachedDevice) {
        MediaDevice mediaDevice = findMediaDevice(MediaDeviceUtils.getId(cachedDevice));
        if (mediaDevice != null) {
            cachedDevice.unregisterCallback(this.mDeviceAttributeChangeCallback);
            this.mLastRemovedDevice = mediaDevice;
            this.mMediaDevices.remove(mediaDevice);
        }
    }

    void dispatchDeviceRemoved(CachedBluetoothDevice cachedDevice) {
        if (this.mLastRemovedDevice != null && MediaDeviceUtils.getId(cachedDevice) == this.mLastRemovedDevice.getId()) {
            dispatchDeviceRemoved(this.mLastRemovedDevice);
        }
    }

    @Override // com.android.settingslib.bluetooth.BluetoothCallback
    public void onProfileConnectionStateChanged(CachedBluetoothDevice cachedDevice, int state, int bluetoothProfile) {
        Log.d(TAG, "onProfileConnectionStateChanged() device: " + cachedDevice + ", state: " + state + ", bluetoothProfile: " + bluetoothProfile);
        updateMediaDeviceListIfNecessary(cachedDevice);
    }

    private void updateMediaDeviceListIfNecessary(CachedBluetoothDevice cachedDevice) {
        if (10 == cachedDevice.getBondState()) {
            removeMediaDevice(cachedDevice);
            dispatchDeviceRemoved(cachedDevice);
        } else if (findMediaDevice(MediaDeviceUtils.getId(cachedDevice)) != null) {
            dispatchDataChanged();
        }
    }

    @Override // com.android.settingslib.bluetooth.BluetoothCallback
    public void onAclConnectionStateChanged(CachedBluetoothDevice cachedDevice, int state) {
        Log.d(TAG, "onAclConnectionStateChanged() device: " + cachedDevice + ", state: " + state);
        updateMediaDeviceListIfNecessary(cachedDevice);
    }

    @Override // com.android.settingslib.bluetooth.BluetoothCallback
    public void onActiveDeviceChanged(CachedBluetoothDevice activeDevice, int bluetoothProfile) {
        String id;
        Log.d(TAG, "onActiveDeviceChanged : device : " + activeDevice + ", profile : " + bluetoothProfile);
        if (21 == bluetoothProfile) {
            if (activeDevice != null) {
                dispatchConnectedDeviceChanged(MediaDeviceUtils.getId(activeDevice));
            }
        } else if (2 == bluetoothProfile) {
            MediaDevice activeHearingAidDevice = findActiveHearingAidDevice();
            if (activeDevice == null) {
                id = activeHearingAidDevice == null ? PhoneMediaDevice.ID : activeHearingAidDevice.getId();
            } else {
                id = MediaDeviceUtils.getId(activeDevice);
            }
            dispatchConnectedDeviceChanged(id);
        }
    }

    private MediaDevice findActiveHearingAidDevice() {
        HearingAidProfile hearingAidProfile = this.mProfileManager.getHearingAidProfile();
        if (hearingAidProfile != null) {
            List<BluetoothDevice> activeDevices = hearingAidProfile.getActiveDevices();
            for (BluetoothDevice btDevice : activeDevices) {
                if (btDevice != null) {
                    return findMediaDevice(MediaDeviceUtils.getId(btDevice));
                }
            }
            return null;
        }
        return null;
    }

    @Override // com.android.settingslib.bluetooth.LocalBluetoothProfileManager.ServiceListener
    public void onServiceConnected() {
        if (!this.mIsA2dpProfileReady || !this.mIsHearingAidProfileReady) {
            buildBluetoothDeviceList();
            dispatchDeviceListAdded();
        }
        if (this.mIsA2dpProfileReady && this.mIsHearingAidProfileReady) {
            this.mProfileManager.removeServiceListener(this);
        }
    }

    @Override // com.android.settingslib.bluetooth.LocalBluetoothProfileManager.ServiceListener
    public void onServiceDisconnected() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class DeviceAttributeChangeCallback implements CachedBluetoothDevice.Callback {
        private DeviceAttributeChangeCallback() {
        }

        @Override // com.android.settingslib.bluetooth.CachedBluetoothDevice.Callback
        public void onDeviceAttributesChanged() {
            BluetoothMediaManager.this.dispatchDataChanged();
        }
    }
}
