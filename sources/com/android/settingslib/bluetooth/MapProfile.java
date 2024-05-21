package com.android.settingslib.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothMap;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;
import com.android.settingslib.R;
import java.util.ArrayList;
import java.util.List;
/* loaded from: classes3.dex */
public class MapProfile implements LocalBluetoothProfile {
    static final String NAME = "MAP";
    private static final String TAG = "MapProfile";
    static final ParcelUuid[] UUIDS = {BluetoothUuid.MAP, BluetoothUuid.MNS, BluetoothUuid.MAS};
    private final CachedBluetoothDeviceManager mDeviceManager;
    private boolean mIsProfileReady;
    private final LocalBluetoothProfileManager mProfileManager;
    private BluetoothMap mService;

    /* loaded from: classes3.dex */
    private final class MapServiceListener implements BluetoothProfile.ServiceListener {
        private MapServiceListener() {
        }

        @Override // android.bluetooth.BluetoothProfile.ServiceListener
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            MapProfile.this.mService = (BluetoothMap) proxy;
            List<BluetoothDevice> deviceList = MapProfile.this.mService.getConnectedDevices();
            while (!deviceList.isEmpty()) {
                BluetoothDevice nextDevice = deviceList.remove(0);
                CachedBluetoothDevice device = MapProfile.this.mDeviceManager.findDevice(nextDevice);
                if (device == null) {
                    Log.w(MapProfile.TAG, "MapProfile found new device: " + nextDevice);
                    device = MapProfile.this.mDeviceManager.addDevice(nextDevice);
                }
                device.onProfileStateChanged(MapProfile.this, 2);
                device.refresh();
            }
            MapProfile.this.mProfileManager.callServiceConnectedListeners();
            MapProfile.this.mIsProfileReady = true;
        }

        @Override // android.bluetooth.BluetoothProfile.ServiceListener
        public void onServiceDisconnected(int profile) {
            MapProfile.this.mProfileManager.callServiceDisconnectedListeners();
            MapProfile.this.mIsProfileReady = false;
        }
    }

    @Override // com.android.settingslib.bluetooth.LocalBluetoothProfile
    public boolean isProfileReady() {
        Log.d(TAG, "isProfileReady(): " + this.mIsProfileReady);
        return this.mIsProfileReady;
    }

    @Override // com.android.settingslib.bluetooth.LocalBluetoothProfile
    public int getProfileId() {
        return 9;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public MapProfile(Context context, CachedBluetoothDeviceManager deviceManager, LocalBluetoothProfileManager profileManager) {
        this.mDeviceManager = deviceManager;
        this.mProfileManager = profileManager;
        BluetoothAdapter.getDefaultAdapter().getProfileProxy(context, new MapServiceListener(), 9);
    }

    @Override // com.android.settingslib.bluetooth.LocalBluetoothProfile
    public boolean accessProfileEnabled() {
        return true;
    }

    @Override // com.android.settingslib.bluetooth.LocalBluetoothProfile
    public boolean isAutoConnectable() {
        return true;
    }

    @Override // com.android.settingslib.bluetooth.LocalBluetoothProfile
    public boolean connect(BluetoothDevice device) {
        Log.d(TAG, "connect() - should not get called");
        return false;
    }

    @Override // com.android.settingslib.bluetooth.LocalBluetoothProfile
    public boolean disconnect(BluetoothDevice device) {
        BluetoothMap bluetoothMap = this.mService;
        if (bluetoothMap == null) {
            return false;
        }
        if (bluetoothMap.getPriority(device) > 100) {
            this.mService.setPriority(device, 100);
        }
        return this.mService.disconnect(device);
    }

    @Override // com.android.settingslib.bluetooth.LocalBluetoothProfile
    public int getConnectionStatus(BluetoothDevice device) {
        BluetoothMap bluetoothMap = this.mService;
        if (bluetoothMap == null) {
            return 0;
        }
        return bluetoothMap.getConnectionState(device);
    }

    @Override // com.android.settingslib.bluetooth.LocalBluetoothProfile
    public boolean isPreferred(BluetoothDevice device) {
        BluetoothMap bluetoothMap = this.mService;
        return bluetoothMap != null && bluetoothMap.getPriority(device) > 0;
    }

    @Override // com.android.settingslib.bluetooth.LocalBluetoothProfile
    public int getPreferred(BluetoothDevice device) {
        BluetoothMap bluetoothMap = this.mService;
        if (bluetoothMap == null) {
            return 0;
        }
        return bluetoothMap.getPriority(device);
    }

    @Override // com.android.settingslib.bluetooth.LocalBluetoothProfile
    public void setPreferred(BluetoothDevice device, boolean preferred) {
        BluetoothMap bluetoothMap = this.mService;
        if (bluetoothMap == null) {
            return;
        }
        if (preferred) {
            if (bluetoothMap.getPriority(device) < 100) {
                this.mService.setPriority(device, 100);
                return;
            }
            return;
        }
        bluetoothMap.setPriority(device, 0);
    }

    public List<BluetoothDevice> getConnectedDevices() {
        BluetoothMap bluetoothMap = this.mService;
        if (bluetoothMap == null) {
            return new ArrayList(0);
        }
        return bluetoothMap.getDevicesMatchingConnectionStates(new int[]{2, 1, 3});
    }

    public String toString() {
        return NAME;
    }

    @Override // com.android.settingslib.bluetooth.LocalBluetoothProfile
    public int getOrdinal() {
        return 9;
    }

    @Override // com.android.settingslib.bluetooth.LocalBluetoothProfile
    public int getNameResource(BluetoothDevice device) {
        return R.string.bluetooth_profile_map;
    }

    @Override // com.android.settingslib.bluetooth.LocalBluetoothProfile
    public int getSummaryResourceForDevice(BluetoothDevice device) {
        int state = getConnectionStatus(device);
        if (state != 0) {
            if (state == 2) {
                return R.string.bluetooth_map_profile_summary_connected;
            }
            return BluetoothUtils.getConnectionStateSummary(state);
        }
        return R.string.bluetooth_map_profile_summary_use_for;
    }

    @Override // com.android.settingslib.bluetooth.LocalBluetoothProfile
    public int getDrawableResource(BluetoothClass btClass) {
        return 17302773;
    }

    protected void finalize() {
        Log.d(TAG, "finalize()");
        if (this.mService != null) {
            try {
                BluetoothAdapter.getDefaultAdapter().closeProfileProxy(9, this.mService);
                this.mService = null;
            } catch (Throwable t) {
                Log.w(TAG, "Error cleaning up MAP proxy", t);
            }
        }
    }
}
