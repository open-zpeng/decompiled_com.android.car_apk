package com.android.car;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothUuid;
import android.car.ICarBluetoothUserService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;
import com.android.internal.annotations.GuardedBy;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
/* loaded from: classes3.dex */
public class BluetoothProfileDeviceManager {
    private static final int AUTO_CONNECT_TIMEOUT_MS = 8000;
    private static final String SETTINGS_DELIMITER = ",";
    @GuardedBy({"mAutoConnectLock"})
    private int mAutoConnectPriority;
    @GuardedBy({"mAutoConnectLock"})
    private ArrayList<BluetoothDevice> mAutoConnectingDevices;
    private final BluetoothAdapter mBluetoothAdapter;
    private final BluetoothBroadcastReceiver mBluetoothBroadcastReceiver;
    private final ICarBluetoothUserService mBluetoothUserProxies;
    private final Context mContext;
    private final String mProfileConnectionAction;
    private final int mProfileId;
    private final int[] mProfileTriggers;
    private final ParcelUuid[] mProfileUuids;
    private final String mSettingsKey;
    private final int mUserId;
    private static final String TAG = "BluetoothProfileDeviceManager";
    private static final boolean DBG = Log.isLoggable(TAG, 3);
    private static final Object AUTO_CONNECT_TOKEN = new Object();
    private static final SparseArray<BluetoothProfileInfo> sProfileActions = new SparseArray<>();
    private final Object mPrioritizedDevicesLock = new Object();
    private final Object mAutoConnectLock = new Object();
    @GuardedBy({"mAutoConnectLock"})
    private boolean mConnecting = false;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    @GuardedBy({"mPrioritizedDevicesLock"})
    private ArrayList<BluetoothDevice> mPrioritizedDevices = new ArrayList<>();

    static {
        sProfileActions.put(11, new BluetoothProfileInfo("android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED", "android.car.KEY_BLUETOOTH_A2DP_SINK_DEVICES", new ParcelUuid[]{BluetoothUuid.AudioSource}, new int[0]));
        sProfileActions.put(16, new BluetoothProfileInfo("android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED", "android.car.KEY_BLUETOOTH_HFP_CLIENT_DEVICES", new ParcelUuid[]{BluetoothUuid.Handsfree_AG, BluetoothUuid.HSP_AG}, new int[]{18, 17}));
        sProfileActions.put(18, new BluetoothProfileInfo("android.bluetooth.mapmce.profile.action.CONNECTION_STATE_CHANGED", "android.car.KEY_BLUETOOTH_MAP_CLIENT_DEVICES", new ParcelUuid[]{BluetoothUuid.MAS}, new int[0]));
        sProfileActions.put(5, new BluetoothProfileInfo("android.bluetooth.pan.profile.action.CONNECTION_STATE_CHANGED", "android.car.KEY_BLUETOOTH_PAN_DEVICES", new ParcelUuid[]{BluetoothUuid.PANU}, new int[0]));
        sProfileActions.put(17, new BluetoothProfileInfo("android.bluetooth.pbapclient.profile.action.CONNECTION_STATE_CHANGED", "android.car.KEY_BLUETOOTH_PBAP_CLIENT_DEVICES", new ParcelUuid[]{BluetoothUuid.PBAP_PSE}, new int[0]));
    }

    /* loaded from: classes3.dex */
    private static class BluetoothProfileInfo {
        final String mConnectionAction;
        final int[] mProfileTriggers;
        final String mSettingsKey;
        final ParcelUuid[] mUuids;

        private BluetoothProfileInfo(String action, String settingsKey, ParcelUuid[] uuids, int[] profileTriggers) {
            this.mSettingsKey = settingsKey;
            this.mConnectionAction = action;
            this.mUuids = uuids;
            this.mProfileTriggers = profileTriggers;
        }
    }

    /* loaded from: classes3.dex */
    private class BluetoothBroadcastReceiver extends BroadcastReceiver {
        private BluetoothBroadcastReceiver() {
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothProfileDeviceManager.this.mProfileConnectionAction.equals(action)) {
                BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
                int state = intent.getIntExtra("android.bluetooth.profile.extra.STATE", 0);
                BluetoothProfileDeviceManager.this.handleDeviceConnectionStateChange(device, state);
            } else if ("android.bluetooth.device.action.BOND_STATE_CHANGED".equals(action)) {
                BluetoothDevice device2 = (BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
                int state2 = intent.getIntExtra("android.bluetooth.device.extra.BOND_STATE", Integer.MIN_VALUE);
                BluetoothProfileDeviceManager.this.handleDeviceBondStateChange(device2, state2);
            } else if ("android.bluetooth.device.action.UUID".equals(action)) {
                BluetoothDevice device3 = (BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
                Parcelable[] uuids = intent.getParcelableArrayExtra("android.bluetooth.device.extra.UUID");
                BluetoothProfileDeviceManager.this.handleDeviceUuidEvent(device3, uuids);
            } else if ("android.bluetooth.adapter.action.STATE_CHANGED".equals(action)) {
                int state3 = intent.getIntExtra("android.bluetooth.adapter.extra.STATE", -1);
                BluetoothProfileDeviceManager.this.handleAdapterStateChange(state3);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleDeviceConnectionStateChange(BluetoothDevice device, int state) {
        logd("Connection state changed [device: " + device + ", state: " + Utils.getConnectionStateName(state) + "]");
        if (state == 2) {
            if (isAutoConnecting() && isAutoConnectingDevice(device)) {
                continueAutoConnecting();
                return;
            }
            if (getProfilePriority(device) >= 100) {
                addDevice(device);
            }
            triggerConnections(device);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleDeviceBondStateChange(BluetoothDevice device, int state) {
        logd("Bond state has changed [device: " + device + ", state: " + Utils.getBondStateName(state) + "]");
        if (state == 10) {
            removeDevice(device);
        } else if (state == 12) {
            addBondedDeviceIfSupported(device);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleDeviceUuidEvent(BluetoothDevice device, Parcelable[] uuids) {
        logd("UUIDs found, device: " + device);
        if (uuids != null) {
            ParcelUuid[] uuidsToSend = new ParcelUuid[uuids.length];
            for (int i = 0; i < uuidsToSend.length; i++) {
                uuidsToSend[i] = (ParcelUuid) uuids[i];
            }
            provisionDeviceIfSupported(device, uuidsToSend);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleAdapterStateChange(int state) {
        logd("Bluetooth Adapter state changed: " + Utils.getAdapterStateName(state));
        if (state != 12) {
            cancelAutoConnecting();
        }
        if (state == 10) {
            commit();
        }
    }

    public static BluetoothProfileDeviceManager create(Context context, int userId, ICarBluetoothUserService bluetoothUserProxies, int profileId) {
        try {
            return new BluetoothProfileDeviceManager(context, userId, bluetoothUserProxies, profileId);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    private BluetoothProfileDeviceManager(Context context, int userId, ICarBluetoothUserService bluetoothUserProxies, int profileId) {
        this.mContext = (Context) Objects.requireNonNull(context);
        this.mUserId = userId;
        this.mBluetoothUserProxies = bluetoothUserProxies;
        BluetoothProfileInfo bpi = sProfileActions.get(profileId);
        if (bpi == null) {
            throw new IllegalArgumentException("Provided profile " + Utils.getProfileName(profileId) + " is unrecognized");
        }
        this.mProfileId = profileId;
        this.mSettingsKey = bpi.mSettingsKey;
        this.mProfileConnectionAction = bpi.mConnectionAction;
        this.mProfileUuids = bpi.mUuids;
        this.mProfileTriggers = bpi.mProfileTriggers;
        this.mBluetoothBroadcastReceiver = new BluetoothBroadcastReceiver();
        this.mBluetoothAdapter = (BluetoothAdapter) Objects.requireNonNull(BluetoothAdapter.getDefaultAdapter());
    }

    public void start() {
        logd("Starting device management");
        load();
        synchronized (this.mAutoConnectLock) {
            this.mConnecting = false;
            this.mAutoConnectPriority = -1;
            this.mAutoConnectingDevices = null;
        }
        IntentFilter profileFilter = new IntentFilter();
        profileFilter.addAction(this.mProfileConnectionAction);
        profileFilter.addAction("android.bluetooth.adapter.action.STATE_CHANGED");
        profileFilter.addAction("android.bluetooth.device.action.BOND_STATE_CHANGED");
        profileFilter.addAction("android.bluetooth.device.action.UUID");
        this.mContext.registerReceiverAsUser(this.mBluetoothBroadcastReceiver, UserHandle.CURRENT, profileFilter, null, null);
    }

    public void stop() {
        Context context;
        logd("Stopping device management");
        BluetoothBroadcastReceiver bluetoothBroadcastReceiver = this.mBluetoothBroadcastReceiver;
        if (bluetoothBroadcastReceiver != null && (context = this.mContext) != null) {
            context.unregisterReceiver(bluetoothBroadcastReceiver);
        }
        cancelAutoConnecting();
        commit();
    }

    private boolean load() {
        List<String> deviceList;
        logd("Loading device priority list snapshot using key '" + this.mSettingsKey + "'");
        String devicesStr = Settings.Secure.getStringForUser(this.mContext.getContentResolver(), this.mSettingsKey, this.mUserId);
        logd("Found Device String: '" + devicesStr + "'");
        if (devicesStr == null || "".equals(devicesStr) || (deviceList = Arrays.asList(devicesStr.split(SETTINGS_DELIMITER))) == null) {
            return false;
        }
        ArrayList<BluetoothDevice> devices = new ArrayList<>();
        for (String address : deviceList) {
            try {
                BluetoothDevice device = this.mBluetoothAdapter.getRemoteDevice(address);
                devices.add(device);
            } catch (IllegalArgumentException e) {
                logw("Unable to parse address '" + address + "' to a device");
            }
        }
        synchronized (this.mPrioritizedDevicesLock) {
            this.mPrioritizedDevices = devices;
        }
        logd("Loaded Priority list: " + devices);
        return true;
    }

    private boolean commit() {
        StringBuilder sb = new StringBuilder();
        String delimiter = "";
        synchronized (this.mPrioritizedDevicesLock) {
            Iterator<BluetoothDevice> it = this.mPrioritizedDevices.iterator();
            while (it.hasNext()) {
                BluetoothDevice device = it.next();
                sb.append(delimiter);
                sb.append(device.getAddress());
                delimiter = SETTINGS_DELIMITER;
            }
        }
        String devicesStr = sb.toString();
        Settings.Secure.putStringForUser(this.mContext.getContentResolver(), this.mSettingsKey, devicesStr, this.mUserId);
        logd("Committed key: " + this.mSettingsKey + ", value: '" + devicesStr + "'");
        return true;
    }

    private void sync() {
        logd("Syncing the priority list with the adapter's list of bonded devices");
        Set<BluetoothDevice> bondedDevices = this.mBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : bondedDevices) {
            addDevice(device);
        }
        synchronized (this.mPrioritizedDevicesLock) {
            ArrayList<BluetoothDevice> devices = getDeviceListSnapshot();
            Iterator<BluetoothDevice> it = devices.iterator();
            while (it.hasNext()) {
                BluetoothDevice device2 = it.next();
                if (!bondedDevices.contains(device2)) {
                    removeDevice(device2);
                }
            }
        }
    }

    public ArrayList<BluetoothDevice> getDeviceListSnapshot() {
        ArrayList<BluetoothDevice> devices;
        new ArrayList();
        synchronized (this.mPrioritizedDevicesLock) {
            devices = (ArrayList) this.mPrioritizedDevices.clone();
        }
        return devices;
    }

    public void addDevice(BluetoothDevice device) {
        if (device == null) {
            return;
        }
        synchronized (this.mPrioritizedDevicesLock) {
            if (this.mPrioritizedDevices.contains(device)) {
                return;
            }
            logd("Add device " + device);
            this.mPrioritizedDevices.add(device);
            commit();
        }
    }

    public void removeDevice(BluetoothDevice device) {
        if (device == null) {
            return;
        }
        synchronized (this.mPrioritizedDevicesLock) {
            if (this.mPrioritizedDevices.contains(device)) {
                logd("Remove device " + device);
                this.mPrioritizedDevices.remove(device);
                commit();
            }
        }
    }

    public int getDeviceConnectionPriority(BluetoothDevice device) {
        int indexOf;
        if (device == null) {
            return -1;
        }
        logd("Get connection priority of " + device);
        synchronized (this.mPrioritizedDevicesLock) {
            indexOf = this.mPrioritizedDevices.indexOf(device);
        }
        return indexOf;
    }

    public void setDeviceConnectionPriority(BluetoothDevice device, int priority) {
        synchronized (this.mPrioritizedDevicesLock) {
            if (device != null && priority >= 0) {
                if (priority <= this.mPrioritizedDevices.size() && getDeviceConnectionPriority(device) != priority) {
                    if (this.mPrioritizedDevices.contains(device)) {
                        this.mPrioritizedDevices.remove(device);
                        if (priority > this.mPrioritizedDevices.size()) {
                            priority = this.mPrioritizedDevices.size();
                        }
                    }
                    logd("Set connection priority of " + device + " to " + priority);
                    this.mPrioritizedDevices.add(priority, device);
                    commit();
                }
            }
        }
    }

    private boolean connect(BluetoothDevice device) {
        logd("Connecting " + device);
        try {
            return this.mBluetoothUserProxies.bluetoothConnectToProfile(this.mProfileId, device);
        } catch (RemoteException e) {
            logw("Failed to connect " + device + ", Reason: " + e);
            return false;
        }
    }

    private boolean disconnect(BluetoothDevice device) {
        logd("Disconnecting " + device);
        try {
            return this.mBluetoothUserProxies.bluetoothDisconnectFromProfile(this.mProfileId, device);
        } catch (RemoteException e) {
            logw("Failed to disconnect " + device + ", Reason: " + e);
            return false;
        }
    }

    private int getProfilePriority(BluetoothDevice device) {
        try {
            return this.mBluetoothUserProxies.getProfilePriority(this.mProfileId, device);
        } catch (RemoteException e) {
            logw("Failed to get bluetooth stack priority for " + device + ", Reason: " + e);
            return -1;
        }
    }

    private boolean setProfilePriority(BluetoothDevice device, int priority) {
        logd("Set " + device + " stack priority to " + Utils.getProfilePriorityName(priority));
        try {
            this.mBluetoothUserProxies.setProfilePriority(this.mProfileId, device, priority);
            return true;
        } catch (RemoteException e) {
            logw("Failed to set bluetooth stack priority for " + device + ", Reason: " + e);
            return false;
        }
    }

    public void beginAutoConnecting() {
        logd("Request to begin auto connection process");
        synchronized (this.mAutoConnectLock) {
            if (isAutoConnecting()) {
                logd("Auto connect requested while we are already auto connecting.");
            } else if (this.mBluetoothAdapter.getState() != 12) {
                logd("Bluetooth Adapter is not on, cannot connect devices");
            } else {
                this.mAutoConnectingDevices = getDeviceListSnapshot();
                if (this.mAutoConnectingDevices.size() == 0) {
                    logd("No saved devices to auto-connect to.");
                    cancelAutoConnecting();
                    return;
                }
                this.mConnecting = true;
                this.mAutoConnectPriority = 0;
                autoConnectWithTimeout();
            }
        }
    }

    private void autoConnectWithTimeout() {
        synchronized (this.mAutoConnectLock) {
            if (!isAutoConnecting()) {
                logd("Autoconnect process was cancelled, skipping connecting next device.");
                return;
            }
            if (this.mAutoConnectPriority >= 0 && this.mAutoConnectPriority < this.mAutoConnectingDevices.size()) {
                final BluetoothDevice device = this.mAutoConnectingDevices.get(this.mAutoConnectPriority);
                logd("Auto connecting (" + this.mAutoConnectPriority + ") device: " + device);
                this.mHandler.post(new Runnable() { // from class: com.android.car.-$$Lambda$BluetoothProfileDeviceManager$23nhKXoQh2fGVENYIWoX4hjzZws
                    @Override // java.lang.Runnable
                    public final void run() {
                        BluetoothProfileDeviceManager.this.lambda$autoConnectWithTimeout$0$BluetoothProfileDeviceManager(device);
                    }
                });
                this.mHandler.postDelayed(new Runnable() { // from class: com.android.car.-$$Lambda$BluetoothProfileDeviceManager$4VUdKmTWIP86WOXhagJ5MWhuh0g
                    @Override // java.lang.Runnable
                    public final void run() {
                        BluetoothProfileDeviceManager.this.lambda$autoConnectWithTimeout$1$BluetoothProfileDeviceManager(device);
                    }
                }, AUTO_CONNECT_TOKEN, 8000L);
            }
        }
    }

    public /* synthetic */ void lambda$autoConnectWithTimeout$0$BluetoothProfileDeviceManager(BluetoothDevice device) {
        boolean connectStatus = connect(device);
        if (!connectStatus) {
            logw("Connection attempt immediately failed, moving to the next device");
            continueAutoConnecting();
        }
    }

    public /* synthetic */ void lambda$autoConnectWithTimeout$1$BluetoothProfileDeviceManager(BluetoothDevice device) {
        logw("Auto connect process has timed out connecting to " + device);
        continueAutoConnecting();
    }

    private void continueAutoConnecting() {
        logd("Continue auto-connect process on next device");
        synchronized (this.mAutoConnectLock) {
            if (!isAutoConnecting()) {
                logd("Autoconnect process was cancelled, no need to continue.");
                return;
            }
            this.mHandler.removeCallbacksAndMessages(AUTO_CONNECT_TOKEN);
            this.mAutoConnectPriority++;
            if (this.mAutoConnectPriority >= this.mAutoConnectingDevices.size()) {
                logd("No more devices to connect to");
                cancelAutoConnecting();
                return;
            }
            autoConnectWithTimeout();
        }
    }

    private void cancelAutoConnecting() {
        logd("Cleaning up any auto-connect process");
        synchronized (this.mAutoConnectLock) {
            if (isAutoConnecting()) {
                this.mHandler.removeCallbacksAndMessages(AUTO_CONNECT_TOKEN);
                this.mConnecting = false;
                this.mAutoConnectPriority = -1;
                this.mAutoConnectingDevices = null;
            }
        }
    }

    public boolean isAutoConnecting() {
        boolean z;
        synchronized (this.mAutoConnectLock) {
            z = this.mConnecting;
        }
        return z;
    }

    private boolean isAutoConnectingDevice(BluetoothDevice device) {
        synchronized (this.mAutoConnectLock) {
            if (this.mAutoConnectingDevices == null) {
                return false;
            }
            return this.mAutoConnectingDevices.get(this.mAutoConnectPriority).equals(device);
        }
    }

    private void addBondedDeviceIfSupported(BluetoothDevice device) {
        logd("Add device " + device + " if it is supported");
        if (device.getBondState() == 12 && BluetoothUuid.containsAnyUuid(device.getUuids(), this.mProfileUuids) && getProfilePriority(device) >= 100) {
            addDevice(device);
        }
    }

    private void provisionDeviceIfSupported(BluetoothDevice device, ParcelUuid[] uuids) {
        logd("Checking UUIDs for device: " + device);
        if (BluetoothUuid.containsAnyUuid(uuids, this.mProfileUuids)) {
            int devicePriority = getProfilePriority(device);
            logd("Device " + device + " supports this profile. Priority: " + Utils.getProfilePriorityName(devicePriority));
            if (devicePriority == -1) {
                setProfilePriority(device, 100);
                return;
            }
        }
        logd("Provisioning of " + device + " has ended without priority being set");
    }

    private void triggerConnections(BluetoothDevice device) {
        int[] iArr;
        for (int profile : this.mProfileTriggers) {
            logd("Trigger connection to " + Utils.getProfileName(profile) + "on " + device);
            try {
                this.mBluetoothUserProxies.bluetoothConnectToProfile(profile, device);
            } catch (RemoteException e) {
                logw("Failed to connect " + device + ", Reason: " + e);
            }
        }
    }

    public void dump(PrintWriter writer, String indent) {
        writer.println(indent + "BluetoothProfileDeviceManager [" + Utils.getProfileName(this.mProfileId) + "]");
        StringBuilder sb = new StringBuilder();
        sb.append(indent);
        sb.append("\tUser: ");
        sb.append(this.mUserId);
        writer.println(sb.toString());
        writer.println(indent + "\tSettings Location: " + this.mSettingsKey);
        StringBuilder sb2 = new StringBuilder();
        sb2.append(indent);
        sb2.append("\tUser Proxies Exist: ");
        sb2.append(this.mBluetoothUserProxies != null ? "Yes" : "No");
        writer.println(sb2.toString());
        StringBuilder sb3 = new StringBuilder();
        sb3.append(indent);
        sb3.append("\tAuto-Connecting: ");
        sb3.append(isAutoConnecting() ? "Yes" : "No");
        writer.println(sb3.toString());
        writer.println(indent + "\tPriority List:");
        ArrayList<BluetoothDevice> devices = getDeviceListSnapshot();
        Iterator<BluetoothDevice> it = devices.iterator();
        while (it.hasNext()) {
            BluetoothDevice device = it.next();
            writer.println(indent + "\t\t" + device.getAddress() + " - " + device.getName());
        }
    }

    private void logd(String msg) {
        if (DBG) {
            Slog.d(TAG, "[" + Utils.getProfileName(this.mProfileId) + " - User: " + this.mUserId + "] " + msg);
        }
    }

    private void logw(String msg) {
        Slog.w(TAG, "[" + Utils.getProfileName(this.mProfileId) + " - User: " + this.mUserId + "] " + msg);
    }
}
