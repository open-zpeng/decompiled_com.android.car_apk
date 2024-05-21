package com.android.settingslib.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.UserHandle;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.android.settingslib.R;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import kotlin.jvm.internal.ShortCompanionObject;
/* loaded from: classes3.dex */
public class BluetoothEventManager {
    private static final String TAG = "BluetoothEventManager";
    private final Context mContext;
    private final CachedBluetoothDeviceManager mDeviceManager;
    private final LocalBluetoothAdapter mLocalAdapter;
    private final android.os.Handler mReceiverHandler;
    private final UserHandle mUserHandle;
    private final BroadcastReceiver mBroadcastReceiver = new BluetoothBroadcastReceiver();
    private final BroadcastReceiver mProfileBroadcastReceiver = new BluetoothBroadcastReceiver();
    private final Collection<BluetoothCallback> mCallbacks = new CopyOnWriteArrayList();
    private final IntentFilter mAdapterIntentFilter = new IntentFilter();
    private final IntentFilter mProfileIntentFilter = new IntentFilter();
    private final Map<String, Handler> mHandlerMap = new HashMap();

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes3.dex */
    public interface Handler {
        void onReceive(Context context, Intent intent, BluetoothDevice bluetoothDevice);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public BluetoothEventManager(LocalBluetoothAdapter adapter, CachedBluetoothDeviceManager deviceManager, Context context, android.os.Handler handler, @Nullable UserHandle userHandle) {
        this.mLocalAdapter = adapter;
        this.mDeviceManager = deviceManager;
        this.mContext = context;
        this.mUserHandle = userHandle;
        this.mReceiverHandler = handler;
        addHandler("android.bluetooth.adapter.action.STATE_CHANGED", new AdapterStateChangedHandler());
        addHandler("android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED", new ConnectionStateChangedHandler());
        addHandler("android.bluetooth.adapter.action.DISCOVERY_STARTED", new ScanningStateChangedHandler(true));
        addHandler("android.bluetooth.adapter.action.DISCOVERY_FINISHED", new ScanningStateChangedHandler(false));
        addHandler("android.bluetooth.device.action.FOUND", new DeviceFoundHandler());
        addHandler("android.bluetooth.device.action.NAME_CHANGED", new NameChangedHandler());
        addHandler("android.bluetooth.device.action.ALIAS_CHANGED", new NameChangedHandler());
        addHandler("android.bluetooth.device.action.BOND_STATE_CHANGED", new BondStateChangedHandler());
        addHandler("android.bluetooth.device.action.CLASS_CHANGED", new ClassChangedHandler());
        addHandler("android.bluetooth.device.action.UUID", new UuidChangedHandler());
        addHandler("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED", new BatteryLevelChangedHandler());
        addHandler("android.bluetooth.a2dp.profile.action.ACTIVE_DEVICE_CHANGED", new ActiveDeviceChangedHandler());
        addHandler("android.bluetooth.headset.profile.action.ACTIVE_DEVICE_CHANGED", new ActiveDeviceChangedHandler());
        addHandler("android.bluetooth.hearingaid.profile.action.ACTIVE_DEVICE_CHANGED", new ActiveDeviceChangedHandler());
        addHandler("android.bluetooth.headset.profile.action.AUDIO_STATE_CHANGED", new AudioModeChangedHandler());
        addHandler("android.intent.action.PHONE_STATE", new AudioModeChangedHandler());
        addHandler("android.bluetooth.device.action.ACL_CONNECTED", new AclStateChangedHandler());
        addHandler("android.bluetooth.device.action.ACL_DISCONNECTED", new AclStateChangedHandler());
        registerAdapterIntentReceiver();
    }

    public void registerCallback(BluetoothCallback callback) {
        this.mCallbacks.add(callback);
    }

    public void unregisterCallback(BluetoothCallback callback) {
        this.mCallbacks.remove(callback);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @VisibleForTesting
    public void registerProfileIntentReceiver() {
        registerIntentReceiver(this.mProfileBroadcastReceiver, this.mProfileIntentFilter);
    }

    @VisibleForTesting
    void registerAdapterIntentReceiver() {
        registerIntentReceiver(this.mBroadcastReceiver, this.mAdapterIntentFilter);
    }

    private void registerIntentReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        UserHandle userHandle = this.mUserHandle;
        if (userHandle == null) {
            this.mContext.registerReceiver(receiver, filter, null, this.mReceiverHandler);
        } else {
            this.mContext.registerReceiverAsUser(receiver, userHandle, filter, null, this.mReceiverHandler);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @VisibleForTesting
    public void addProfileHandler(String action, Handler handler) {
        this.mHandlerMap.put(action, handler);
        this.mProfileIntentFilter.addAction(action);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean readPairedDevices() {
        Set<BluetoothDevice> bondedDevices = this.mLocalAdapter.getBondedDevices();
        if (bondedDevices == null) {
            return false;
        }
        boolean deviceAdded = false;
        for (BluetoothDevice device : bondedDevices) {
            CachedBluetoothDevice cachedDevice = this.mDeviceManager.findDevice(device);
            if (cachedDevice == null) {
                this.mDeviceManager.addDevice(device);
                deviceAdded = true;
            }
        }
        return deviceAdded;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void dispatchDeviceAdded(CachedBluetoothDevice cachedDevice) {
        for (BluetoothCallback callback : this.mCallbacks) {
            callback.onDeviceAdded(cachedDevice);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void dispatchDeviceRemoved(CachedBluetoothDevice cachedDevice) {
        for (BluetoothCallback callback : this.mCallbacks) {
            callback.onDeviceDeleted(cachedDevice);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void dispatchProfileConnectionStateChanged(CachedBluetoothDevice device, int state, int bluetoothProfile) {
        Log.d(TAG, "dispatchProfieConnectionState, device = " + device.getName() + ", profile = " + bluetoothProfile + ", state = " + state);
        for (BluetoothCallback callback : this.mCallbacks) {
            callback.onProfileConnectionStateChanged(device, state, bluetoothProfile);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void dispatchConnectionStateChanged(CachedBluetoothDevice cachedDevice, int state) {
        for (BluetoothCallback callback : this.mCallbacks) {
            callback.onConnectionStateChanged(cachedDevice, state);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void dispatchAudioModeChanged() {
        this.mDeviceManager.dispatchAudioModeChanged();
        for (BluetoothCallback callback : this.mCallbacks) {
            callback.onAudioModeChanged();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void dispatchActiveDeviceChanged(CachedBluetoothDevice activeDevice, int bluetoothProfile) {
        this.mDeviceManager.onActiveDeviceChanged(activeDevice, bluetoothProfile);
        for (BluetoothCallback callback : this.mCallbacks) {
            callback.onActiveDeviceChanged(activeDevice, bluetoothProfile);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void dispatchAclStateChanged(CachedBluetoothDevice activeDevice, int state) {
        for (BluetoothCallback callback : this.mCallbacks) {
            callback.onAclConnectionStateChanged(activeDevice, state);
        }
    }

    @VisibleForTesting
    void addHandler(String action, Handler handler) {
        this.mHandlerMap.put(action, handler);
        this.mAdapterIntentFilter.addAction(action);
    }

    /* loaded from: classes3.dex */
    private class BluetoothBroadcastReceiver extends BroadcastReceiver {
        private BluetoothBroadcastReceiver() {
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
            Handler handler = (Handler) BluetoothEventManager.this.mHandlerMap.get(action);
            if (handler != null) {
                handler.onReceive(context, intent, device);
            }
        }
    }

    /* loaded from: classes3.dex */
    private class AdapterStateChangedHandler implements Handler {
        private AdapterStateChangedHandler() {
        }

        @Override // com.android.settingslib.bluetooth.BluetoothEventManager.Handler
        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            int state = intent.getIntExtra("android.bluetooth.adapter.extra.STATE", Integer.MIN_VALUE);
            BluetoothEventManager.this.mLocalAdapter.setBluetoothStateInt(state);
            for (BluetoothCallback callback : BluetoothEventManager.this.mCallbacks) {
                callback.onBluetoothStateChanged(state);
            }
            BluetoothEventManager.this.mDeviceManager.onBluetoothStateChanged(state);
        }
    }

    /* loaded from: classes3.dex */
    private class ScanningStateChangedHandler implements Handler {
        private final boolean mStarted;

        ScanningStateChangedHandler(boolean started) {
            this.mStarted = started;
        }

        @Override // com.android.settingslib.bluetooth.BluetoothEventManager.Handler
        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            for (BluetoothCallback callback : BluetoothEventManager.this.mCallbacks) {
                callback.onScanningStateChanged(this.mStarted);
            }
            BluetoothEventManager.this.mDeviceManager.onScanningStateChanged(this.mStarted);
        }
    }

    /* loaded from: classes3.dex */
    private class DeviceFoundHandler implements Handler {
        private DeviceFoundHandler() {
        }

        @Override // com.android.settingslib.bluetooth.BluetoothEventManager.Handler
        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            short rssi = intent.getShortExtra("android.bluetooth.device.extra.RSSI", ShortCompanionObject.MIN_VALUE);
            intent.getStringExtra("android.bluetooth.device.extra.NAME");
            CachedBluetoothDevice cachedDevice = BluetoothEventManager.this.mDeviceManager.findDevice(device);
            if (cachedDevice == null) {
                cachedDevice = BluetoothEventManager.this.mDeviceManager.addDevice(device);
                Log.d(BluetoothEventManager.TAG, "DeviceFoundHandler created new CachedBluetoothDevice: " + cachedDevice);
            } else if (cachedDevice.getBondState() == 12 && !cachedDevice.getDevice().isConnected()) {
                BluetoothEventManager.this.dispatchDeviceAdded(cachedDevice);
                Log.d(BluetoothEventManager.TAG, "DeviceFoundHandler found bonded and not connected device:" + cachedDevice);
            } else {
                Log.d(BluetoothEventManager.TAG, "DeviceFoundHandler found existing CachedBluetoothDevice:" + cachedDevice);
            }
            cachedDevice.setRssi(rssi);
            cachedDevice.setJustDiscovered(true);
        }
    }

    /* loaded from: classes3.dex */
    private class ConnectionStateChangedHandler implements Handler {
        private ConnectionStateChangedHandler() {
        }

        @Override // com.android.settingslib.bluetooth.BluetoothEventManager.Handler
        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            CachedBluetoothDevice cachedDevice = BluetoothEventManager.this.mDeviceManager.findDevice(device);
            int state = intent.getIntExtra("android.bluetooth.adapter.extra.CONNECTION_STATE", Integer.MIN_VALUE);
            if (cachedDevice != null) {
                Log.d(BluetoothEventManager.TAG, "ConnectionStateChanged for device " + cachedDevice.getName() + ", new state = " + state);
                BluetoothEventManager.this.dispatchConnectionStateChanged(cachedDevice, state);
            }
        }
    }

    /* loaded from: classes3.dex */
    private class NameChangedHandler implements Handler {
        private NameChangedHandler() {
        }

        @Override // com.android.settingslib.bluetooth.BluetoothEventManager.Handler
        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            BluetoothEventManager.this.mDeviceManager.onDeviceNameUpdated(device);
        }
    }

    /* loaded from: classes3.dex */
    private class BondStateChangedHandler implements Handler {
        private BondStateChangedHandler() {
        }

        @Override // com.android.settingslib.bluetooth.BluetoothEventManager.Handler
        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            if (device == null) {
                Log.e(BluetoothEventManager.TAG, "ACTION_BOND_STATE_CHANGED with no EXTRA_DEVICE");
                return;
            }
            int bondState = intent.getIntExtra("android.bluetooth.device.extra.BOND_STATE", Integer.MIN_VALUE);
            CachedBluetoothDevice cachedDevice = BluetoothEventManager.this.mDeviceManager.findDevice(device);
            if (cachedDevice == null) {
                Log.w(BluetoothEventManager.TAG, "Got bonding state changed for " + device + ", but we have no record of that device.");
                cachedDevice = BluetoothEventManager.this.mDeviceManager.addDevice(device);
            }
            for (BluetoothCallback callback : BluetoothEventManager.this.mCallbacks) {
                callback.onDeviceBondStateChanged(cachedDevice, bondState);
            }
            cachedDevice.onBondingStateChanged(bondState);
            if (bondState == 10) {
                if (cachedDevice.getHiSyncId() != 0) {
                    BluetoothEventManager.this.mDeviceManager.onDeviceUnpaired(cachedDevice);
                }
                int reason = intent.getIntExtra("android.bluetooth.device.extra.REASON", Integer.MIN_VALUE);
                showUnbondMessage(context, cachedDevice.getName(), reason);
            }
        }

        private void showUnbondMessage(Context context, String name, int reason) {
            int errorMsg;
            switch (reason) {
                case 1:
                    errorMsg = R.string.bluetooth_pairing_pin_error_message;
                    break;
                case 2:
                    errorMsg = R.string.bluetooth_pairing_rejected_error_message;
                    break;
                case 3:
                default:
                    Log.w(BluetoothEventManager.TAG, "showUnbondMessage: Not displaying any message for reason: " + reason);
                    return;
                case 4:
                    errorMsg = R.string.bluetooth_pairing_device_down_error_message;
                    break;
                case 5:
                case 6:
                case 7:
                case 8:
                    errorMsg = R.string.bluetooth_pairing_error_message;
                    break;
            }
            BluetoothUtils.showError(context, name, errorMsg);
        }
    }

    /* loaded from: classes3.dex */
    private class ClassChangedHandler implements Handler {
        private ClassChangedHandler() {
        }

        @Override // com.android.settingslib.bluetooth.BluetoothEventManager.Handler
        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            CachedBluetoothDevice cachedDevice = BluetoothEventManager.this.mDeviceManager.findDevice(device);
            if (cachedDevice != null) {
                cachedDevice.refresh();
            }
        }
    }

    /* loaded from: classes3.dex */
    private class UuidChangedHandler implements Handler {
        private UuidChangedHandler() {
        }

        @Override // com.android.settingslib.bluetooth.BluetoothEventManager.Handler
        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            CachedBluetoothDevice cachedDevice = BluetoothEventManager.this.mDeviceManager.findDevice(device);
            if (cachedDevice != null) {
                cachedDevice.onUuidChanged();
            }
        }
    }

    /* loaded from: classes3.dex */
    private class BatteryLevelChangedHandler implements Handler {
        private BatteryLevelChangedHandler() {
        }

        @Override // com.android.settingslib.bluetooth.BluetoothEventManager.Handler
        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            CachedBluetoothDevice cachedDevice = BluetoothEventManager.this.mDeviceManager.findDevice(device);
            if (cachedDevice != null) {
                cachedDevice.refresh();
            }
        }
    }

    /* loaded from: classes3.dex */
    private class ActiveDeviceChangedHandler implements Handler {
        private ActiveDeviceChangedHandler() {
        }

        @Override // com.android.settingslib.bluetooth.BluetoothEventManager.Handler
        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            int bluetoothProfile;
            String action = intent.getAction();
            if (action != null) {
                CachedBluetoothDevice activeDevice = BluetoothEventManager.this.mDeviceManager.findDevice(device);
                if (Objects.equals(action, "android.bluetooth.a2dp.profile.action.ACTIVE_DEVICE_CHANGED")) {
                    bluetoothProfile = 2;
                } else if (Objects.equals(action, "android.bluetooth.headset.profile.action.ACTIVE_DEVICE_CHANGED")) {
                    bluetoothProfile = 1;
                } else if (Objects.equals(action, "android.bluetooth.hearingaid.profile.action.ACTIVE_DEVICE_CHANGED")) {
                    bluetoothProfile = 21;
                } else {
                    Log.w(BluetoothEventManager.TAG, "ActiveDeviceChangedHandler: unknown action " + action);
                    return;
                }
                Log.d(BluetoothEventManager.TAG, "ActiveDevice changed for " + activeDevice.getName() + ", profile = " + bluetoothProfile);
                BluetoothEventManager.this.dispatchActiveDeviceChanged(activeDevice, bluetoothProfile);
                return;
            }
            Log.w(BluetoothEventManager.TAG, "ActiveDeviceChangedHandler: action is null");
        }
    }

    /* loaded from: classes3.dex */
    private class AclStateChangedHandler implements Handler {
        private AclStateChangedHandler() {
        }

        @Override // com.android.settingslib.bluetooth.BluetoothEventManager.Handler
        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            int state;
            if (device != null) {
                if (BluetoothEventManager.this.mDeviceManager.isSubDevice(device)) {
                    return;
                }
                String action = intent.getAction();
                if (action != null) {
                    CachedBluetoothDevice activeDevice = BluetoothEventManager.this.mDeviceManager.findDevice(device);
                    if (activeDevice == null) {
                        Log.w(BluetoothEventManager.TAG, "AclStateChangedHandler: activeDevice is null");
                        return;
                    }
                    char c = 65535;
                    int hashCode = action.hashCode();
                    if (hashCode != -301431627) {
                        if (hashCode == 1821585647 && action.equals("android.bluetooth.device.action.ACL_DISCONNECTED")) {
                            c = 1;
                        }
                    } else if (action.equals("android.bluetooth.device.action.ACL_CONNECTED")) {
                        c = 0;
                    }
                    if (c == 0) {
                        state = 2;
                    } else if (c == 1) {
                        state = 0;
                    } else {
                        Log.w(BluetoothEventManager.TAG, "ActiveDeviceChangedHandler: unknown action " + action);
                        return;
                    }
                    BluetoothEventManager.this.dispatchAclStateChanged(activeDevice, state);
                    return;
                }
                Log.w(BluetoothEventManager.TAG, "AclStateChangedHandler: action is null");
                return;
            }
            Log.w(BluetoothEventManager.TAG, "AclStateChangedHandler: device is null");
        }
    }

    /* loaded from: classes3.dex */
    private class AudioModeChangedHandler implements Handler {
        private AudioModeChangedHandler() {
        }

        @Override // com.android.settingslib.bluetooth.BluetoothEventManager.Handler
        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            String action = intent.getAction();
            if (action != null) {
                BluetoothEventManager.this.dispatchAudioModeChanged();
            } else {
                Log.w(BluetoothEventManager.TAG, "AudioModeChangedHandler() action is null");
            }
        }
    }
}
