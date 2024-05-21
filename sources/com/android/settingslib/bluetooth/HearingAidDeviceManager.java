package com.android.settingslib.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.util.Log;
import com.android.internal.annotations.VisibleForTesting;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
/* loaded from: classes3.dex */
public class HearingAidDeviceManager {
    private static final boolean DEBUG = true;
    private static final String TAG = "HearingAidDeviceManager";
    private final LocalBluetoothManager mBtManager;
    private final List<CachedBluetoothDevice> mCachedDevices;

    /* JADX INFO: Access modifiers changed from: package-private */
    public HearingAidDeviceManager(LocalBluetoothManager localBtManager, List<CachedBluetoothDevice> CachedDevices) {
        this.mBtManager = localBtManager;
        this.mCachedDevices = CachedDevices;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void initHearingAidDeviceIfNeeded(CachedBluetoothDevice newDevice) {
        long hiSyncId = getHiSyncId(newDevice.getDevice());
        if (isValidHiSyncId(hiSyncId)) {
            newDevice.setHiSyncId(hiSyncId);
        }
    }

    private long getHiSyncId(BluetoothDevice device) {
        LocalBluetoothProfileManager profileManager = this.mBtManager.getProfileManager();
        HearingAidProfile profileProxy = profileManager.getHearingAidProfile();
        if (profileProxy != null) {
            return profileProxy.getHiSyncId(device);
        }
        return 0L;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean setSubDeviceIfNeeded(CachedBluetoothDevice newDevice) {
        CachedBluetoothDevice hearingAidDevice;
        long hiSyncId = newDevice.getHiSyncId();
        if (isValidHiSyncId(hiSyncId) && (hearingAidDevice = getCachedDevice(hiSyncId)) != null) {
            hearingAidDevice.setSubDevice(newDevice);
            return true;
        }
        return false;
    }

    private boolean isValidHiSyncId(long hiSyncId) {
        return hiSyncId != 0;
    }

    private CachedBluetoothDevice getCachedDevice(long hiSyncId) {
        for (int i = this.mCachedDevices.size() - 1; i >= 0; i--) {
            CachedBluetoothDevice cachedDevice = this.mCachedDevices.get(i);
            if (cachedDevice.getHiSyncId() == hiSyncId) {
                return cachedDevice;
            }
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void updateHearingAidsDevices() {
        Set<Long> newSyncIdSet = new HashSet<>();
        for (CachedBluetoothDevice cachedDevice : this.mCachedDevices) {
            if (!isValidHiSyncId(cachedDevice.getHiSyncId())) {
                long newHiSyncId = getHiSyncId(cachedDevice.getDevice());
                if (isValidHiSyncId(newHiSyncId)) {
                    cachedDevice.setHiSyncId(newHiSyncId);
                    newSyncIdSet.add(Long.valueOf(newHiSyncId));
                }
            }
        }
        for (Long syncId : newSyncIdSet) {
            onHiSyncIdChanged(syncId.longValue());
        }
    }

    @VisibleForTesting
    void onHiSyncIdChanged(long hiSyncId) {
        CachedBluetoothDevice mainDevice;
        int indexToRemoveFromUi;
        CachedBluetoothDevice subDevice;
        int firstMatchedIndex = -1;
        for (int i = this.mCachedDevices.size() - 1; i >= 0; i--) {
            CachedBluetoothDevice cachedDevice = this.mCachedDevices.get(i);
            if (cachedDevice.getHiSyncId() == hiSyncId) {
                if (firstMatchedIndex == -1) {
                    firstMatchedIndex = i;
                } else {
                    if (cachedDevice.isConnected()) {
                        mainDevice = cachedDevice;
                        indexToRemoveFromUi = firstMatchedIndex;
                        subDevice = this.mCachedDevices.get(firstMatchedIndex);
                    } else {
                        mainDevice = this.mCachedDevices.get(firstMatchedIndex);
                        indexToRemoveFromUi = i;
                        subDevice = cachedDevice;
                    }
                    mainDevice.setSubDevice(subDevice);
                    this.mCachedDevices.remove(indexToRemoveFromUi);
                    log("onHiSyncIdChanged: removed from UI device =" + subDevice + ", with hiSyncId=" + hiSyncId);
                    this.mBtManager.getEventManager().dispatchDeviceRemoved(subDevice);
                    return;
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean onProfileConnectionStateChangedIfProcessed(CachedBluetoothDevice cachedDevice, int state) {
        if (state == 0) {
            CachedBluetoothDevice mainDevice = findMainDevice(cachedDevice);
            if (mainDevice != null) {
                mainDevice.refresh();
                return true;
            }
            CachedBluetoothDevice subDevice = cachedDevice.getSubDevice();
            if (subDevice != null && subDevice.isConnected()) {
                this.mBtManager.getEventManager().dispatchDeviceRemoved(cachedDevice);
                cachedDevice.switchSubDeviceContent();
                cachedDevice.refresh();
                this.mBtManager.getEventManager().dispatchDeviceAdded(cachedDevice);
                return true;
            }
            return false;
        } else if (state == 2) {
            onHiSyncIdChanged(cachedDevice.getHiSyncId());
            CachedBluetoothDevice mainDevice2 = findMainDevice(cachedDevice);
            if (mainDevice2 != null) {
                if (mainDevice2.isConnected()) {
                    mainDevice2.refresh();
                    return true;
                }
                this.mBtManager.getEventManager().dispatchDeviceRemoved(mainDevice2);
                mainDevice2.switchSubDeviceContent();
                mainDevice2.refresh();
                this.mBtManager.getEventManager().dispatchDeviceAdded(mainDevice2);
                return true;
            }
            return false;
        } else {
            return false;
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public CachedBluetoothDevice findMainDevice(CachedBluetoothDevice device) {
        CachedBluetoothDevice subDevice;
        for (CachedBluetoothDevice cachedDevice : this.mCachedDevices) {
            if (isValidHiSyncId(cachedDevice.getHiSyncId()) && (subDevice = cachedDevice.getSubDevice()) != null && subDevice.equals(device)) {
                return cachedDevice;
            }
        }
        return null;
    }

    private void log(String msg) {
        Log.d(TAG, msg);
    }
}
