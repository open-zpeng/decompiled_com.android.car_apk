package com.android.settingslib.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;
import com.android.internal.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
/* loaded from: classes3.dex */
public class CachedBluetoothDeviceManager {
    private static final boolean DEBUG = true;
    private static final String TAG = "CachedBluetoothDeviceManager";
    private final LocalBluetoothManager mBtManager;
    @VisibleForTesting
    final List<CachedBluetoothDevice> mCachedDevices = new ArrayList();
    private Context mContext;
    @VisibleForTesting
    HearingAidDeviceManager mHearingAidDeviceManager;

    /* JADX INFO: Access modifiers changed from: package-private */
    public CachedBluetoothDeviceManager(Context context, LocalBluetoothManager localBtManager) {
        this.mContext = context;
        this.mBtManager = localBtManager;
        this.mHearingAidDeviceManager = new HearingAidDeviceManager(localBtManager, this.mCachedDevices);
    }

    public synchronized Collection<CachedBluetoothDevice> getCachedDevicesCopy() {
        return new ArrayList(this.mCachedDevices);
    }

    public static boolean onDeviceDisappeared(CachedBluetoothDevice cachedDevice) {
        cachedDevice.setJustDiscovered(false);
        return cachedDevice.getBondState() == 10;
    }

    public void onDeviceNameUpdated(BluetoothDevice device) {
        CachedBluetoothDevice cachedDevice = findDevice(device);
        if (cachedDevice != null) {
            cachedDevice.refreshName();
        }
    }

    public synchronized CachedBluetoothDevice findDevice(BluetoothDevice device) {
        for (CachedBluetoothDevice cachedDevice : this.mCachedDevices) {
            if (cachedDevice.getDevice().equals(device)) {
                return cachedDevice;
            }
            CachedBluetoothDevice subDevice = cachedDevice.getSubDevice();
            if (subDevice != null && subDevice.getDevice().equals(device)) {
                return subDevice;
            }
        }
        return null;
    }

    public CachedBluetoothDevice addDevice(BluetoothDevice device) {
        LocalBluetoothProfileManager profileManager = this.mBtManager.getProfileManager();
        CachedBluetoothDevice newDevice = new CachedBluetoothDevice(this.mContext, profileManager, device);
        this.mHearingAidDeviceManager.initHearingAidDeviceIfNeeded(newDevice);
        synchronized (this) {
            if (!this.mHearingAidDeviceManager.setSubDeviceIfNeeded(newDevice)) {
                this.mCachedDevices.add(newDevice);
                this.mBtManager.getEventManager().dispatchDeviceAdded(newDevice);
            }
        }
        return newDevice;
    }

    public synchronized String getSubDeviceSummary(CachedBluetoothDevice device) {
        CachedBluetoothDevice subDevice = device.getSubDevice();
        if (subDevice != null && subDevice.isConnected()) {
            return subDevice.getConnectionSummary();
        }
        return null;
    }

    public synchronized boolean isSubDevice(BluetoothDevice device) {
        CachedBluetoothDevice subDevice;
        for (CachedBluetoothDevice cachedDevice : this.mCachedDevices) {
            if (!cachedDevice.getDevice().equals(device) && (subDevice = cachedDevice.getSubDevice()) != null && subDevice.getDevice().equals(device)) {
                return true;
            }
        }
        return false;
    }

    public synchronized void updateHearingAidsDevices() {
        this.mHearingAidDeviceManager.updateHearingAidsDevices();
    }

    public String getName(BluetoothDevice device) {
        CachedBluetoothDevice cachedDevice = findDevice(device);
        if (cachedDevice != null && cachedDevice.getName() != null) {
            return cachedDevice.getName();
        }
        String name = device.getAliasName();
        if (name != null) {
            return name;
        }
        return device.getAddress();
    }

    public synchronized void clearNonBondedDevices() {
        clearNonBondedSubDevices();
        this.mCachedDevices.removeIf(new Predicate() { // from class: com.android.settingslib.bluetooth.-$$Lambda$CachedBluetoothDeviceManager$1n6G0RUX5KnCwfoBdpyaC68q3xA
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return CachedBluetoothDeviceManager.lambda$clearNonBondedDevices$0((CachedBluetoothDevice) obj);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ boolean lambda$clearNonBondedDevices$0(CachedBluetoothDevice cachedDevice) {
        return cachedDevice.getBondState() == 10;
    }

    private void clearNonBondedSubDevices() {
        for (int i = this.mCachedDevices.size() - 1; i >= 0; i--) {
            CachedBluetoothDevice cachedDevice = this.mCachedDevices.get(i);
            CachedBluetoothDevice subDevice = cachedDevice.getSubDevice();
            if (subDevice != null && subDevice.getDevice().getBondState() == 10) {
                cachedDevice.setSubDevice(null);
            }
        }
    }

    public synchronized void onScanningStateChanged(boolean started) {
        if (started) {
            for (int i = this.mCachedDevices.size() - 1; i >= 0; i--) {
                CachedBluetoothDevice cachedDevice = this.mCachedDevices.get(i);
                cachedDevice.setJustDiscovered(false);
                CachedBluetoothDevice subDevice = cachedDevice.getSubDevice();
                if (subDevice != null) {
                    subDevice.setJustDiscovered(false);
                }
            }
        }
    }

    public synchronized void onBluetoothStateChanged(int bluetoothState) {
        if (bluetoothState == 13) {
            for (int i = this.mCachedDevices.size() - 1; i >= 0; i--) {
                CachedBluetoothDevice cachedDevice = this.mCachedDevices.get(i);
                CachedBluetoothDevice subDevice = cachedDevice.getSubDevice();
                if (subDevice != null && subDevice.getBondState() != 12) {
                    cachedDevice.setSubDevice(null);
                }
                if (cachedDevice.getBondState() != 12) {
                    cachedDevice.setJustDiscovered(false);
                    this.mCachedDevices.remove(i);
                }
            }
        }
    }

    public synchronized void onActiveDeviceChanged(CachedBluetoothDevice activeDevice, int bluetoothProfile) {
        for (CachedBluetoothDevice cachedDevice : this.mCachedDevices) {
            boolean isActive = Objects.equals(cachedDevice, activeDevice);
            cachedDevice.onActiveDeviceChanged(isActive, bluetoothProfile);
        }
    }

    public synchronized boolean onProfileConnectionStateChangedIfProcessed(CachedBluetoothDevice cachedDevice, int state) {
        return this.mHearingAidDeviceManager.onProfileConnectionStateChangedIfProcessed(cachedDevice, state);
    }

    public synchronized void onDeviceUnpaired(CachedBluetoothDevice device) {
        CachedBluetoothDevice mainDevice = this.mHearingAidDeviceManager.findMainDevice(device);
        CachedBluetoothDevice subDevice = device.getSubDevice();
        if (subDevice != null) {
            subDevice.unpair();
            device.setSubDevice(null);
        } else if (mainDevice != null) {
            mainDevice.unpair();
            mainDevice.setSubDevice(null);
        }
    }

    public synchronized void dispatchAudioModeChanged() {
        for (CachedBluetoothDevice cachedDevice : this.mCachedDevices) {
            cachedDevice.onAudioModeChanged();
        }
    }

    private void log(String msg) {
        Log.d(TAG, msg);
    }
}
