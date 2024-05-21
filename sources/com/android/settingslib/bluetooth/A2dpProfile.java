package com.android.settingslib.bluetooth;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothCodecConfig;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;
import com.android.settingslib.R;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
/* loaded from: classes3.dex */
public class A2dpProfile implements LocalBluetoothProfile {
    static final String NAME = "A2DP";
    private static final int ORDINAL = 1;
    static final ParcelUuid[] SINK_UUIDS = {BluetoothUuid.AudioSink, BluetoothUuid.AdvAudioDist};
    private static final String TAG = "A2dpProfile";
    private Context mContext;
    private final CachedBluetoothDeviceManager mDeviceManager;
    private boolean mIsProfileReady;
    private final LocalBluetoothProfileManager mProfileManager;
    private BluetoothA2dp mService;

    /* loaded from: classes3.dex */
    private final class A2dpServiceListener implements BluetoothProfile.ServiceListener {
        private A2dpServiceListener() {
        }

        @Override // android.bluetooth.BluetoothProfile.ServiceListener
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            A2dpProfile.this.mService = (BluetoothA2dp) proxy;
            List<BluetoothDevice> deviceList = A2dpProfile.this.mService.getConnectedDevices();
            while (!deviceList.isEmpty()) {
                BluetoothDevice nextDevice = deviceList.remove(0);
                CachedBluetoothDevice device = A2dpProfile.this.mDeviceManager.findDevice(nextDevice);
                if (device == null) {
                    Log.w(A2dpProfile.TAG, "A2dpProfile found new device: " + nextDevice);
                    device = A2dpProfile.this.mDeviceManager.addDevice(nextDevice);
                }
                device.onProfileStateChanged(A2dpProfile.this, 2);
                device.refresh();
            }
            A2dpProfile.this.mIsProfileReady = true;
            A2dpProfile.this.mProfileManager.callServiceConnectedListeners();
        }

        @Override // android.bluetooth.BluetoothProfile.ServiceListener
        public void onServiceDisconnected(int profile) {
            A2dpProfile.this.mIsProfileReady = false;
        }
    }

    @Override // com.android.settingslib.bluetooth.LocalBluetoothProfile
    public boolean isProfileReady() {
        return this.mIsProfileReady;
    }

    @Override // com.android.settingslib.bluetooth.LocalBluetoothProfile
    public int getProfileId() {
        return 2;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public A2dpProfile(Context context, CachedBluetoothDeviceManager deviceManager, LocalBluetoothProfileManager profileManager) {
        this.mContext = context;
        this.mDeviceManager = deviceManager;
        this.mProfileManager = profileManager;
        BluetoothAdapter.getDefaultAdapter().getProfileProxy(context, new A2dpServiceListener(), 2);
    }

    @Override // com.android.settingslib.bluetooth.LocalBluetoothProfile
    public boolean accessProfileEnabled() {
        return true;
    }

    @Override // com.android.settingslib.bluetooth.LocalBluetoothProfile
    public boolean isAutoConnectable() {
        return true;
    }

    public List<BluetoothDevice> getConnectedDevices() {
        return getDevicesByStates(new int[]{2, 1, 3});
    }

    public List<BluetoothDevice> getConnectableDevices() {
        return getDevicesByStates(new int[]{0, 2, 1, 3});
    }

    private List<BluetoothDevice> getDevicesByStates(int[] states) {
        BluetoothA2dp bluetoothA2dp = this.mService;
        if (bluetoothA2dp == null) {
            return new ArrayList(0);
        }
        return bluetoothA2dp.getDevicesMatchingConnectionStates(states);
    }

    @Override // com.android.settingslib.bluetooth.LocalBluetoothProfile
    public boolean connect(BluetoothDevice device) {
        BluetoothA2dp bluetoothA2dp = this.mService;
        if (bluetoothA2dp == null) {
            return false;
        }
        return bluetoothA2dp.connect(device);
    }

    @Override // com.android.settingslib.bluetooth.LocalBluetoothProfile
    public boolean disconnect(BluetoothDevice device) {
        BluetoothA2dp bluetoothA2dp = this.mService;
        if (bluetoothA2dp == null) {
            return false;
        }
        if (bluetoothA2dp.getPriority(device) > 100) {
            this.mService.setPriority(device, 100);
        }
        return this.mService.disconnect(device);
    }

    @Override // com.android.settingslib.bluetooth.LocalBluetoothProfile
    public int getConnectionStatus(BluetoothDevice device) {
        BluetoothA2dp bluetoothA2dp = this.mService;
        if (bluetoothA2dp == null) {
            return 0;
        }
        return bluetoothA2dp.getConnectionState(device);
    }

    public boolean setActiveDevice(BluetoothDevice device) {
        BluetoothA2dp bluetoothA2dp = this.mService;
        if (bluetoothA2dp == null) {
            return false;
        }
        return bluetoothA2dp.setActiveDevice(device);
    }

    public BluetoothDevice getActiveDevice() {
        BluetoothA2dp bluetoothA2dp = this.mService;
        if (bluetoothA2dp == null) {
            return null;
        }
        return bluetoothA2dp.getActiveDevice();
    }

    @Override // com.android.settingslib.bluetooth.LocalBluetoothProfile
    public boolean isPreferred(BluetoothDevice device) {
        BluetoothA2dp bluetoothA2dp = this.mService;
        return bluetoothA2dp != null && bluetoothA2dp.getPriority(device) > 0;
    }

    @Override // com.android.settingslib.bluetooth.LocalBluetoothProfile
    public int getPreferred(BluetoothDevice device) {
        BluetoothA2dp bluetoothA2dp = this.mService;
        if (bluetoothA2dp == null) {
            return 0;
        }
        return bluetoothA2dp.getPriority(device);
    }

    @Override // com.android.settingslib.bluetooth.LocalBluetoothProfile
    public void setPreferred(BluetoothDevice device, boolean preferred) {
        BluetoothA2dp bluetoothA2dp = this.mService;
        if (bluetoothA2dp == null) {
            return;
        }
        if (preferred) {
            if (bluetoothA2dp.getPriority(device) < 100) {
                this.mService.setPriority(device, 100);
                return;
            }
            return;
        }
        bluetoothA2dp.setPriority(device, 0);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean isA2dpPlaying() {
        BluetoothA2dp bluetoothA2dp = this.mService;
        if (bluetoothA2dp == null) {
            return false;
        }
        List<BluetoothDevice> sinks = bluetoothA2dp.getConnectedDevices();
        for (BluetoothDevice device : sinks) {
            if (this.mService.isA2dpPlaying(device)) {
                return true;
            }
        }
        return false;
    }

    public boolean supportsHighQualityAudio(BluetoothDevice device) {
        int support = this.mService.supportsOptionalCodecs(device);
        return support == 1;
    }

    public boolean isHighQualityAudioEnabled(BluetoothDevice device) {
        int enabled = this.mService.getOptionalCodecsEnabled(device);
        if (enabled != -1) {
            return enabled == 1;
        } else if (getConnectionStatus(device) == 2 || !supportsHighQualityAudio(device)) {
            BluetoothCodecConfig codecConfig = null;
            if (this.mService.getCodecStatus(device) != null) {
                codecConfig = this.mService.getCodecStatus(device).getCodecConfig();
            }
            if (codecConfig == null) {
                return false;
            }
            return !codecConfig.isMandatoryCodec();
        } else {
            return true;
        }
    }

    public void setHighQualityAudioEnabled(BluetoothDevice device, boolean enabled) {
        int prefValue;
        if (enabled) {
            prefValue = 1;
        } else {
            prefValue = 0;
        }
        this.mService.setOptionalCodecsEnabled(device, prefValue);
        if (getConnectionStatus(device) != 2) {
            return;
        }
        if (enabled) {
            this.mService.enableOptionalCodecs(device);
        } else {
            this.mService.disableOptionalCodecs(device);
        }
    }

    public String getHighQualityAudioOptionLabel(BluetoothDevice device) {
        int unknownCodecId = R.string.bluetooth_profile_a2dp_high_quality_unknown_codec;
        if (!supportsHighQualityAudio(device) || getConnectionStatus(device) != 2) {
            return this.mContext.getString(unknownCodecId);
        }
        BluetoothCodecConfig[] selectable = null;
        if (this.mService.getCodecStatus(device) != null) {
            selectable = this.mService.getCodecStatus(device).getCodecsSelectableCapabilities();
            Arrays.sort(selectable, new Comparator() { // from class: com.android.settingslib.bluetooth.-$$Lambda$A2dpProfile$exPXCssgW4cryyr_RqCY5K-rQFI
                @Override // java.util.Comparator
                public final int compare(Object obj, Object obj2) {
                    return A2dpProfile.lambda$getHighQualityAudioOptionLabel$0((BluetoothCodecConfig) obj, (BluetoothCodecConfig) obj2);
                }
            });
        }
        BluetoothCodecConfig codecConfig = (selectable == null || selectable.length < 1) ? null : selectable[0];
        int codecType = (codecConfig == null || codecConfig.isMandatoryCodec()) ? 1000000 : codecConfig.getCodecType();
        int index = -1;
        if (codecType == 0) {
            index = 1;
        } else if (codecType == 1) {
            index = 2;
        } else if (codecType == 2) {
            index = 3;
        } else if (codecType == 3) {
            index = 4;
        } else if (codecType == 4) {
            index = 5;
        }
        if (index < 0) {
            return this.mContext.getString(unknownCodecId);
        }
        return this.mContext.getString(R.string.bluetooth_profile_a2dp_high_quality, this.mContext.getResources().getStringArray(R.array.bluetooth_a2dp_codec_titles)[index]);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ int lambda$getHighQualityAudioOptionLabel$0(BluetoothCodecConfig a, BluetoothCodecConfig b) {
        return b.getCodecPriority() - a.getCodecPriority();
    }

    public String toString() {
        return NAME;
    }

    @Override // com.android.settingslib.bluetooth.LocalBluetoothProfile
    public int getOrdinal() {
        return 1;
    }

    @Override // com.android.settingslib.bluetooth.LocalBluetoothProfile
    public int getNameResource(BluetoothDevice device) {
        return R.string.bluetooth_profile_a2dp;
    }

    @Override // com.android.settingslib.bluetooth.LocalBluetoothProfile
    public int getSummaryResourceForDevice(BluetoothDevice device) {
        int state = getConnectionStatus(device);
        if (state != 0) {
            if (state == 2) {
                return R.string.bluetooth_a2dp_profile_summary_connected;
            }
            return BluetoothUtils.getConnectionStateSummary(state);
        }
        return R.string.bluetooth_a2dp_profile_summary_use_for;
    }

    @Override // com.android.settingslib.bluetooth.LocalBluetoothProfile
    public int getDrawableResource(BluetoothClass btClass) {
        return 17302312;
    }

    protected void finalize() {
        Log.d(TAG, "finalize()");
        if (this.mService != null) {
            try {
                BluetoothAdapter.getDefaultAdapter().closeProfileProxy(2, this.mService);
                this.mService = null;
            } catch (Throwable t) {
                Log.w(TAG, "Error cleaning up A2DP proxy", t);
            }
        }
    }
}
