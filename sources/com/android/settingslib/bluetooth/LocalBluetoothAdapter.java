package com.android.settingslib.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.Context;
import android.os.ParcelUuid;
import java.util.List;
import java.util.Set;
@Deprecated
/* loaded from: classes3.dex */
public class LocalBluetoothAdapter {
    private static final int SCAN_EXPIRATION_MS = 300000;
    private static final String TAG = "LocalBluetoothAdapter";
    private static LocalBluetoothAdapter sInstance;
    private final BluetoothAdapter mAdapter;
    private long mLastScan;
    private LocalBluetoothProfileManager mProfileManager;
    private int mState = Integer.MIN_VALUE;

    private LocalBluetoothAdapter(BluetoothAdapter adapter) {
        this.mAdapter = adapter;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void setProfileManager(LocalBluetoothProfileManager manager) {
        this.mProfileManager = manager;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static synchronized LocalBluetoothAdapter getInstance() {
        LocalBluetoothAdapter localBluetoothAdapter;
        BluetoothAdapter adapter;
        synchronized (LocalBluetoothAdapter.class) {
            if (sInstance == null && (adapter = BluetoothAdapter.getDefaultAdapter()) != null) {
                sInstance = new LocalBluetoothAdapter(adapter);
            }
            localBluetoothAdapter = sInstance;
        }
        return localBluetoothAdapter;
    }

    public void cancelDiscovery() {
        this.mAdapter.cancelDiscovery();
    }

    public boolean enable() {
        return this.mAdapter.enable();
    }

    public boolean disable() {
        return this.mAdapter.disable();
    }

    public String getAddress() {
        return this.mAdapter.getAddress();
    }

    void getProfileProxy(Context context, BluetoothProfile.ServiceListener listener, int profile) {
        this.mAdapter.getProfileProxy(context, listener, profile);
    }

    public Set<BluetoothDevice> getBondedDevices() {
        return this.mAdapter.getBondedDevices();
    }

    public String getName() {
        return this.mAdapter.getName();
    }

    public int getScanMode() {
        return this.mAdapter.getScanMode();
    }

    public BluetoothLeScanner getBluetoothLeScanner() {
        return this.mAdapter.getBluetoothLeScanner();
    }

    public int getState() {
        return this.mAdapter.getState();
    }

    public ParcelUuid[] getUuids() {
        return this.mAdapter.getUuids();
    }

    public boolean isDiscovering() {
        return this.mAdapter.isDiscovering();
    }

    public boolean isEnabled() {
        return this.mAdapter.isEnabled();
    }

    public int getConnectionState() {
        return this.mAdapter.getConnectionState();
    }

    public void setDiscoverableTimeout(int timeout) {
        this.mAdapter.setDiscoverableTimeout(timeout);
    }

    public long getDiscoveryEndMillis() {
        return this.mAdapter.getDiscoveryEndMillis();
    }

    public void setName(String name) {
        this.mAdapter.setName(name);
    }

    public void setScanMode(int mode) {
        this.mAdapter.setScanMode(mode);
    }

    public boolean setScanMode(int mode, int duration) {
        return this.mAdapter.setScanMode(mode, duration);
    }

    public void startScanning(boolean force) {
        if (!this.mAdapter.isDiscovering()) {
            if (!force) {
                if (this.mLastScan + 300000 > System.currentTimeMillis()) {
                    return;
                }
                A2dpProfile a2dp = this.mProfileManager.getA2dpProfile();
                if (a2dp != null && a2dp.isA2dpPlaying()) {
                    return;
                }
                A2dpSinkProfile a2dpSink = this.mProfileManager.getA2dpSinkProfile();
                if (a2dpSink != null && a2dpSink.isA2dpPlaying()) {
                    return;
                }
            }
            if (this.mAdapter.startDiscovery()) {
                this.mLastScan = System.currentTimeMillis();
            }
        }
    }

    public void stopScanning() {
        if (this.mAdapter.isDiscovering()) {
            this.mAdapter.cancelDiscovery();
        }
    }

    public synchronized int getBluetoothState() {
        syncBluetoothState();
        return this.mState;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void setBluetoothStateInt(int state) {
        LocalBluetoothProfileManager localBluetoothProfileManager;
        synchronized (this) {
            if (this.mState == state) {
                return;
            }
            this.mState = state;
            if (state == 12 && (localBluetoothProfileManager = this.mProfileManager) != null) {
                localBluetoothProfileManager.setBluetoothStateOn();
            }
        }
    }

    boolean syncBluetoothState() {
        int currentState = this.mAdapter.getState();
        if (currentState != this.mState) {
            setBluetoothStateInt(this.mAdapter.getState());
            return true;
        }
        return false;
    }

    public boolean setBluetoothEnabled(boolean enabled) {
        boolean success;
        int i;
        if (enabled) {
            success = this.mAdapter.enable();
        } else {
            success = this.mAdapter.disable();
        }
        if (success) {
            if (enabled) {
                i = 11;
            } else {
                i = 13;
            }
            setBluetoothStateInt(i);
        } else {
            syncBluetoothState();
        }
        return success;
    }

    public BluetoothDevice getRemoteDevice(String address) {
        return this.mAdapter.getRemoteDevice(address);
    }

    public List<Integer> getSupportedProfiles() {
        return this.mAdapter.getSupportedProfiles();
    }
}
