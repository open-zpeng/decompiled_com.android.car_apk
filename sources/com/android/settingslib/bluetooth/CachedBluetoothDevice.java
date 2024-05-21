package com.android.settingslib.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.IBluetoothGatt;
import android.bluetooth.IBluetoothManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.EventLog;
import android.util.Log;
import androidx.annotation.VisibleForTesting;
import com.android.settingslib.R;
import com.android.settingslib.Utils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
/* loaded from: classes3.dex */
public class CachedBluetoothDevice implements Comparable<CachedBluetoothDevice> {
    private static final int INVALID_GATT_ID = -1;
    private static final long MAX_HEARING_AIDS_DELAY_FOR_AUTO_CONNECT = 15000;
    private static final long MAX_HOGP_DELAY_FOR_AUTO_CONNECT = 30000;
    private static final long MAX_UUID_DELAY_FOR_AUTO_CONNECT = 5000;
    private static final String TAG = "CachedBluetoothDevice";
    private long mConnectAttempted;
    private final Context mContext;
    BluetoothDevice mDevice;
    private long mHiSyncId;
    boolean mJustDiscovered;
    private boolean mLocalNapRoleConnected;
    private final LocalBluetoothProfileManager mProfileManager;
    short mRssi;
    private CachedBluetoothDevice mSubDevice;
    private final Object mProfileLock = new Object();
    private final List<LocalBluetoothProfile> mProfiles = new ArrayList();
    private final List<LocalBluetoothProfile> mRemovedProfiles = new ArrayList();
    private final Collection<Callback> mCallbacks = new CopyOnWriteArrayList();
    private boolean mIsActiveDeviceA2dp = false;
    private boolean mIsActiveDeviceHeadset = false;
    private boolean mIsActiveDeviceHearingAid = false;
    private final BluetoothAdapter mLocalAdapter = BluetoothAdapter.getDefaultAdapter();

    /* loaded from: classes3.dex */
    public interface Callback {
        void onDeviceAttributesChanged();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public CachedBluetoothDevice(Context context, LocalBluetoothProfileManager profileManager, BluetoothDevice device) {
        this.mContext = context;
        this.mProfileManager = profileManager;
        this.mDevice = device;
        fillData();
        this.mHiSyncId = 0L;
    }

    private String describe(LocalBluetoothProfile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("Address:");
        sb.append(this.mDevice);
        if (profile != null) {
            sb.append(" Profile:");
            sb.append(profile);
        }
        return sb.toString();
    }

    private void disconnectGATT(String address) {
        IBinder b = ServiceManager.getService("bluetooth_manager");
        IBluetoothManager managerService = IBluetoothManager.Stub.asInterface(b);
        try {
            IBluetoothGatt iGatt = managerService.getBluetoothGatt();
            iGatt.serverDisconnect(-1, address);
        } catch (RemoteException e) {
            Log.e(TAG, "", e);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void onProfileStateChanged(LocalBluetoothProfile profile, int newProfileState) {
        Log.d(TAG, "onProfileStateChanged: profile " + profile + ", device=" + this.mDevice + ", newProfileState " + newProfileState);
        if (this.mLocalAdapter.getState() == 13) {
            Log.d(TAG, " BT Turninig Off...Profile conn state change ignored...");
            return;
        }
        synchronized (this.mProfileLock) {
            if (newProfileState == 2) {
                if (profile instanceof MapProfile) {
                    profile.setPreferred(this.mDevice, true);
                }
                if (!this.mProfiles.contains(profile)) {
                    this.mRemovedProfiles.remove(profile);
                    this.mProfiles.add(profile);
                    if ((profile instanceof PanProfile) && ((PanProfile) profile).isLocalRoleNap(this.mDevice)) {
                        this.mLocalNapRoleConnected = true;
                    }
                }
            } else if ((profile instanceof MapProfile) && newProfileState == 0) {
                profile.setPreferred(this.mDevice, false);
            } else if (this.mLocalNapRoleConnected && (profile instanceof PanProfile) && ((PanProfile) profile).isLocalRoleNap(this.mDevice) && newProfileState == 0) {
                Log.d(TAG, "Removing PanProfile from device after NAP disconnect");
                this.mProfiles.remove(profile);
                this.mRemovedProfiles.add(profile);
                this.mLocalNapRoleConnected = false;
            }
        }
        fetchActiveDevices();
    }

    public void disconnect() {
        synchronized (this.mProfileLock) {
            for (LocalBluetoothProfile profile : this.mProfiles) {
                disconnect(profile);
            }
        }
        PbapServerProfile PbapProfile = this.mProfileManager.getPbapProfile();
        if (PbapProfile != null && isConnectedProfile(PbapProfile)) {
            PbapProfile.disconnect(this.mDevice);
        }
        disconnectGATT(this.mDevice.getAddress());
    }

    public void disconnect(LocalBluetoothProfile profile) {
        if (profile.disconnect(this.mDevice)) {
            Log.d(TAG, "Command sent successfully:DISCONNECT " + describe(profile));
        }
    }

    public void connect(boolean connectAllProfiles) {
        if (!ensurePaired()) {
            return;
        }
        this.mConnectAttempted = SystemClock.elapsedRealtime();
        connectWithoutResettingTimer(connectAllProfiles);
    }

    public long getHiSyncId() {
        return this.mHiSyncId;
    }

    public void setHiSyncId(long id) {
        Log.d(TAG, "setHiSyncId: mDevice " + this.mDevice + ", id " + id);
        this.mHiSyncId = id;
    }

    public boolean isHearingAidDevice() {
        return this.mHiSyncId != 0;
    }

    void onBondingDockConnect() {
        connect(false);
    }

    /* JADX WARN: Removed duplicated region for block: B:21:0x004f A[Catch: all -> 0x0072, TryCatch #0 {, blocks: (B:4:0x0003, B:6:0x000b, B:7:0x0023, B:9:0x0025, B:10:0x002c, B:12:0x0032, B:14:0x003a, B:19:0x0047, B:21:0x004f, B:17:0x0041, B:23:0x0055, B:25:0x006d, B:26:0x0070), top: B:31:0x0003 }] */
    /* JADX WARN: Removed duplicated region for block: B:36:0x0054 A[SYNTHETIC] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    private void connectWithoutResettingTimer(boolean r6) {
        /*
            r5 = this;
            java.lang.Object r0 = r5.mProfileLock
            monitor-enter(r0)
            java.util.List<com.android.settingslib.bluetooth.LocalBluetoothProfile> r1 = r5.mProfiles     // Catch: java.lang.Throwable -> L72
            boolean r1 = r1.isEmpty()     // Catch: java.lang.Throwable -> L72
            if (r1 == 0) goto L25
            java.lang.String r1 = "CachedBluetoothDevice"
            java.lang.StringBuilder r2 = new java.lang.StringBuilder     // Catch: java.lang.Throwable -> L72
            r2.<init>()     // Catch: java.lang.Throwable -> L72
            java.lang.String r3 = "No profiles. Maybe we will connect later for device "
            r2.append(r3)     // Catch: java.lang.Throwable -> L72
            android.bluetooth.BluetoothDevice r3 = r5.mDevice     // Catch: java.lang.Throwable -> L72
            r2.append(r3)     // Catch: java.lang.Throwable -> L72
            java.lang.String r2 = r2.toString()     // Catch: java.lang.Throwable -> L72
            android.util.Log.d(r1, r2)     // Catch: java.lang.Throwable -> L72
            monitor-exit(r0)     // Catch: java.lang.Throwable -> L72
            return
        L25:
            r1 = 0
            java.util.List<com.android.settingslib.bluetooth.LocalBluetoothProfile> r2 = r5.mProfiles     // Catch: java.lang.Throwable -> L72
            java.util.Iterator r2 = r2.iterator()     // Catch: java.lang.Throwable -> L72
        L2c:
            boolean r3 = r2.hasNext()     // Catch: java.lang.Throwable -> L72
            if (r3 == 0) goto L55
            java.lang.Object r3 = r2.next()     // Catch: java.lang.Throwable -> L72
            com.android.settingslib.bluetooth.LocalBluetoothProfile r3 = (com.android.settingslib.bluetooth.LocalBluetoothProfile) r3     // Catch: java.lang.Throwable -> L72
            if (r6 == 0) goto L41
            boolean r4 = r3.accessProfileEnabled()     // Catch: java.lang.Throwable -> L72
            if (r4 == 0) goto L54
            goto L47
        L41:
            boolean r4 = r3.isAutoConnectable()     // Catch: java.lang.Throwable -> L72
            if (r4 == 0) goto L54
        L47:
            android.bluetooth.BluetoothDevice r4 = r5.mDevice     // Catch: java.lang.Throwable -> L72
            boolean r4 = r3.isPreferred(r4)     // Catch: java.lang.Throwable -> L72
            if (r4 == 0) goto L54
            int r1 = r1 + 1
            r5.connectInt(r3)     // Catch: java.lang.Throwable -> L72
        L54:
            goto L2c
        L55:
            java.lang.String r2 = "CachedBluetoothDevice"
            java.lang.StringBuilder r3 = new java.lang.StringBuilder     // Catch: java.lang.Throwable -> L72
            r3.<init>()     // Catch: java.lang.Throwable -> L72
            java.lang.String r4 = "Preferred profiles = "
            r3.append(r4)     // Catch: java.lang.Throwable -> L72
            r3.append(r1)     // Catch: java.lang.Throwable -> L72
            java.lang.String r3 = r3.toString()     // Catch: java.lang.Throwable -> L72
            android.util.Log.d(r2, r3)     // Catch: java.lang.Throwable -> L72
            if (r1 != 0) goto L70
            r5.connectAutoConnectableProfiles()     // Catch: java.lang.Throwable -> L72
        L70:
            monitor-exit(r0)     // Catch: java.lang.Throwable -> L72
            return
        L72:
            r1 = move-exception
            monitor-exit(r0)     // Catch: java.lang.Throwable -> L72
            throw r1
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.settingslib.bluetooth.CachedBluetoothDevice.connectWithoutResettingTimer(boolean):void");
    }

    private void connectAutoConnectableProfiles() {
        if (!ensurePaired()) {
            return;
        }
        synchronized (this.mProfileLock) {
            for (LocalBluetoothProfile profile : this.mProfiles) {
                if (profile.isAutoConnectable()) {
                    profile.setPreferred(this.mDevice, true);
                    connectInt(profile);
                }
            }
        }
    }

    public void connectProfile(LocalBluetoothProfile profile) {
        this.mConnectAttempted = SystemClock.elapsedRealtime();
        connectInt(profile);
        refresh();
    }

    synchronized void connectInt(LocalBluetoothProfile profile) {
        if (ensurePaired()) {
            if (profile.connect(this.mDevice)) {
                Log.d(TAG, "Command sent successfully:CONNECT " + describe(profile));
                return;
            }
            Log.i(TAG, "Failed to connect " + profile.toString() + " to " + getName());
        }
    }

    private boolean ensurePaired() {
        if (getBondState() == 10) {
            startPairing();
            return false;
        }
        return true;
    }

    public boolean startPairing() {
        if (this.mLocalAdapter.isDiscovering()) {
            this.mLocalAdapter.cancelDiscovery();
        }
        if (!this.mDevice.createBond()) {
            return false;
        }
        return true;
    }

    public void unpair() {
        BluetoothDevice dev;
        int state = getBondState();
        if (state == 11) {
            this.mDevice.cancelBondProcess();
        }
        if (state != 10 && (dev = this.mDevice) != null) {
            boolean successful = dev.removeBond();
            if (successful) {
                Log.d(TAG, "Command sent successfully:REMOVE_BOND " + describe(null));
            }
        }
    }

    public int getProfileConnectionState(LocalBluetoothProfile profile) {
        if (profile != null) {
            return profile.getConnectionStatus(this.mDevice);
        }
        return 0;
    }

    private void fillData() {
        updateProfiles();
        fetchActiveDevices();
        migratePhonebookPermissionChoice();
        migrateMessagePermissionChoice();
        dispatchAttributesChanged();
    }

    public BluetoothDevice getDevice() {
        return this.mDevice;
    }

    public String getAddress() {
        return this.mDevice.getAddress();
    }

    public String getName() {
        String aliasName = this.mDevice.getAliasName();
        return TextUtils.isEmpty(aliasName) ? getAddress() : aliasName;
    }

    public void setName(String name) {
        if (name != null && !TextUtils.equals(name, getName())) {
            this.mDevice.setAlias(name);
            dispatchAttributesChanged();
        }
    }

    public boolean setActive() {
        boolean result = false;
        A2dpProfile a2dpProfile = this.mProfileManager.getA2dpProfile();
        if (a2dpProfile != null && isConnectedProfile(a2dpProfile) && a2dpProfile.setActiveDevice(getDevice())) {
            Log.i(TAG, "OnPreferenceClickListener: A2DP active device=" + this);
            result = true;
        }
        HeadsetProfile headsetProfile = this.mProfileManager.getHeadsetProfile();
        if (headsetProfile != null && isConnectedProfile(headsetProfile) && headsetProfile.setActiveDevice(getDevice())) {
            Log.i(TAG, "OnPreferenceClickListener: Headset active device=" + this);
            result = true;
        }
        HearingAidProfile hearingAidProfile = this.mProfileManager.getHearingAidProfile();
        if (hearingAidProfile != null && isConnectedProfile(hearingAidProfile) && hearingAidProfile.setActiveDevice(getDevice())) {
            Log.i(TAG, "OnPreferenceClickListener: Hearing Aid active device=" + this);
            return true;
        }
        return result;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void refreshName() {
        Log.d(TAG, "Device name: " + getName());
        dispatchAttributesChanged();
    }

    public boolean hasHumanReadableName() {
        return !TextUtils.isEmpty(this.mDevice.getAliasName());
    }

    public int getBatteryLevel() {
        return this.mDevice.getBatteryLevel();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void refresh() {
        dispatchAttributesChanged();
    }

    public void setJustDiscovered(boolean justDiscovered) {
        if (this.mJustDiscovered != justDiscovered) {
            this.mJustDiscovered = justDiscovered;
            dispatchAttributesChanged();
        }
    }

    public int getBondState() {
        return this.mDevice.getBondState();
    }

    public void onActiveDeviceChanged(boolean isActive, int bluetoothProfile) {
        boolean changed = false;
        if (bluetoothProfile == 1) {
            changed = this.mIsActiveDeviceHeadset != isActive;
            this.mIsActiveDeviceHeadset = isActive;
        } else if (bluetoothProfile == 2) {
            changed = this.mIsActiveDeviceA2dp != isActive;
            this.mIsActiveDeviceA2dp = isActive;
        } else if (bluetoothProfile == 21) {
            changed = this.mIsActiveDeviceHearingAid != isActive;
            this.mIsActiveDeviceHearingAid = isActive;
        } else {
            Log.w(TAG, "onActiveDeviceChanged: unknown profile " + bluetoothProfile + " isActive " + isActive);
        }
        if (changed) {
            dispatchAttributesChanged();
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void onAudioModeChanged() {
        dispatchAttributesChanged();
    }

    @VisibleForTesting(otherwise = 3)
    public boolean isActiveDevice(int bluetoothProfile) {
        if (bluetoothProfile != 1) {
            if (bluetoothProfile != 2) {
                if (bluetoothProfile == 21) {
                    return this.mIsActiveDeviceHearingAid;
                }
                Log.w(TAG, "getActiveDevice: unknown profile " + bluetoothProfile);
                return false;
            }
            return this.mIsActiveDeviceA2dp;
        }
        return this.mIsActiveDeviceHeadset;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void setRssi(short rssi) {
        if (this.mRssi != rssi) {
            this.mRssi = rssi;
            dispatchAttributesChanged();
        }
    }

    public boolean isConnected() {
        synchronized (this.mProfileLock) {
            for (LocalBluetoothProfile profile : this.mProfiles) {
                int status = getProfileConnectionState(profile);
                if (status == 2) {
                    return true;
                }
            }
            return false;
        }
    }

    public boolean isConnectedProfile(LocalBluetoothProfile profile) {
        int status = getProfileConnectionState(profile);
        return status == 2;
    }

    /* JADX WARN: Code restructure failed: missing block: B:14:0x0022, code lost:
        return true;
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public boolean isBusy() {
        /*
            r6 = this;
            java.lang.Object r0 = r6.mProfileLock
            monitor-enter(r0)
            java.util.List<com.android.settingslib.bluetooth.LocalBluetoothProfile> r1 = r6.mProfiles     // Catch: java.lang.Throwable -> L2f
            java.util.Iterator r1 = r1.iterator()     // Catch: java.lang.Throwable -> L2f
        L9:
            boolean r2 = r1.hasNext()     // Catch: java.lang.Throwable -> L2f
            r3 = 1
            if (r2 == 0) goto L23
            java.lang.Object r2 = r1.next()     // Catch: java.lang.Throwable -> L2f
            com.android.settingslib.bluetooth.LocalBluetoothProfile r2 = (com.android.settingslib.bluetooth.LocalBluetoothProfile) r2     // Catch: java.lang.Throwable -> L2f
            int r4 = r6.getProfileConnectionState(r2)     // Catch: java.lang.Throwable -> L2f
            if (r4 == r3) goto L21
            r5 = 3
            if (r4 != r5) goto L20
            goto L21
        L20:
            goto L9
        L21:
            monitor-exit(r0)     // Catch: java.lang.Throwable -> L2f
            return r3
        L23:
            int r1 = r6.getBondState()     // Catch: java.lang.Throwable -> L2f
            r2 = 11
            if (r1 != r2) goto L2c
            goto L2d
        L2c:
            r3 = 0
        L2d:
            monitor-exit(r0)     // Catch: java.lang.Throwable -> L2f
            return r3
        L2f:
            r1 = move-exception
            monitor-exit(r0)     // Catch: java.lang.Throwable -> L2f
            throw r1
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.settingslib.bluetooth.CachedBluetoothDevice.isBusy():boolean");
    }

    private boolean updateProfiles() {
        ParcelUuid[] localUuids;
        ParcelUuid[] uuids = this.mDevice.getUuids();
        if (uuids == null || (localUuids = this.mLocalAdapter.getUuids()) == null) {
            return false;
        }
        processPhonebookAccess();
        synchronized (this.mProfileLock) {
            this.mProfileManager.updateProfiles(uuids, localUuids, this.mProfiles, this.mRemovedProfiles, this.mLocalNapRoleConnected, this.mDevice);
        }
        Log.e(TAG, "updating profiles for " + this.mDevice.getAliasName() + ", " + this.mDevice);
        BluetoothClass bluetoothClass = this.mDevice.getBluetoothClass();
        if (bluetoothClass != null) {
            Log.v(TAG, "Class: " + bluetoothClass.toString());
        }
        Log.v(TAG, "UUID:");
        for (ParcelUuid uuid : uuids) {
            Log.v(TAG, "  " + uuid);
        }
        return true;
    }

    private void fetchActiveDevices() {
        A2dpProfile a2dpProfile = this.mProfileManager.getA2dpProfile();
        if (a2dpProfile != null) {
            this.mIsActiveDeviceA2dp = this.mDevice.equals(a2dpProfile.getActiveDevice());
        }
        HeadsetProfile headsetProfile = this.mProfileManager.getHeadsetProfile();
        if (headsetProfile != null) {
            this.mIsActiveDeviceHeadset = this.mDevice.equals(headsetProfile.getActiveDevice());
        }
        HearingAidProfile hearingAidProfile = this.mProfileManager.getHearingAidProfile();
        if (hearingAidProfile != null) {
            this.mIsActiveDeviceHearingAid = hearingAidProfile.getActiveDevices().contains(this.mDevice);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void onUuidChanged() {
        updateProfiles();
        ParcelUuid[] uuids = this.mDevice.getUuids();
        long timeout = MAX_UUID_DELAY_FOR_AUTO_CONNECT;
        if (BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.Hogp)) {
            timeout = MAX_HOGP_DELAY_FOR_AUTO_CONNECT;
        } else if (BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.HearingAid)) {
            timeout = MAX_HEARING_AIDS_DELAY_FOR_AUTO_CONNECT;
        }
        Log.d(TAG, "onUuidChanged: Time since last connect=" + (SystemClock.elapsedRealtime() - this.mConnectAttempted));
        if (!this.mProfiles.isEmpty() && this.mConnectAttempted + timeout > SystemClock.elapsedRealtime()) {
            connectWithoutResettingTimer(false);
        }
        dispatchAttributesChanged();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void onBondingStateChanged(int bondState) {
        if (bondState == 10) {
            synchronized (this.mProfileLock) {
                this.mProfiles.clear();
            }
            this.mDevice.setPhonebookAccessPermission(0);
            this.mDevice.setMessageAccessPermission(0);
            this.mDevice.setSimAccessPermission(0);
        }
        refresh();
        if (bondState == 12) {
            if (this.mDevice.isBluetoothDock()) {
                onBondingDockConnect();
            } else if (this.mDevice.isBondingInitiatedLocally()) {
                connect(false);
            }
        }
    }

    public BluetoothClass getBtClass() {
        return this.mDevice.getBluetoothClass();
    }

    public List<LocalBluetoothProfile> getProfiles() {
        return Collections.unmodifiableList(this.mProfiles);
    }

    public List<LocalBluetoothProfile> getProfileListCopy() {
        return new ArrayList(this.mProfiles);
    }

    public List<LocalBluetoothProfile> getConnectableProfiles() {
        List<LocalBluetoothProfile> connectableProfiles = new ArrayList<>();
        synchronized (this.mProfileLock) {
            for (LocalBluetoothProfile profile : this.mProfiles) {
                if (profile.accessProfileEnabled()) {
                    connectableProfiles.add(profile);
                }
            }
        }
        return connectableProfiles;
    }

    public List<LocalBluetoothProfile> getRemovedProfiles() {
        return this.mRemovedProfiles;
    }

    public void registerCallback(Callback callback) {
        this.mCallbacks.add(callback);
    }

    public void unregisterCallback(Callback callback) {
        this.mCallbacks.remove(callback);
    }

    void dispatchAttributesChanged() {
        for (Callback callback : this.mCallbacks) {
            callback.onDeviceAttributesChanged();
        }
    }

    public String toString() {
        return this.mDevice.toString();
    }

    public boolean equals(Object o) {
        if (o == null || !(o instanceof CachedBluetoothDevice)) {
            return false;
        }
        return this.mDevice.equals(((CachedBluetoothDevice) o).mDevice);
    }

    public int hashCode() {
        return this.mDevice.getAddress().hashCode();
    }

    @Override // java.lang.Comparable
    public int compareTo(CachedBluetoothDevice another) {
        int comparison = (another.isConnected() ? 1 : 0) - (isConnected() ? 1 : 0);
        if (comparison != 0) {
            return comparison;
        }
        int comparison2 = (another.getBondState() == 12 ? 1 : 0) - (getBondState() != 12 ? 0 : 1);
        if (comparison2 != 0) {
            return comparison2;
        }
        int comparison3 = (another.mJustDiscovered ? 1 : 0) - (this.mJustDiscovered ? 1 : 0);
        if (comparison3 != 0) {
            return comparison3;
        }
        int comparison4 = another.mRssi - this.mRssi;
        return comparison4 != 0 ? comparison4 : getName().compareTo(another.getName());
    }

    private void migratePhonebookPermissionChoice() {
        SharedPreferences preferences = this.mContext.getSharedPreferences("bluetooth_phonebook_permission", 0);
        if (!preferences.contains(this.mDevice.getAddress())) {
            return;
        }
        if (this.mDevice.getPhonebookAccessPermission() == 0) {
            int oldPermission = preferences.getInt(this.mDevice.getAddress(), 0);
            if (oldPermission == 1) {
                this.mDevice.setPhonebookAccessPermission(1);
            } else if (oldPermission == 2) {
                this.mDevice.setPhonebookAccessPermission(2);
            }
        }
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove(this.mDevice.getAddress());
        editor.commit();
    }

    private void migrateMessagePermissionChoice() {
        SharedPreferences preferences = this.mContext.getSharedPreferences("bluetooth_message_permission", 0);
        if (!preferences.contains(this.mDevice.getAddress())) {
            return;
        }
        if (this.mDevice.getMessageAccessPermission() == 0) {
            int oldPermission = preferences.getInt(this.mDevice.getAddress(), 0);
            if (oldPermission == 1) {
                this.mDevice.setMessageAccessPermission(1);
            } else if (oldPermission == 2) {
                this.mDevice.setMessageAccessPermission(2);
            }
        }
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove(this.mDevice.getAddress());
        editor.commit();
    }

    private void processPhonebookAccess() {
        if (this.mDevice.getBondState() != 12) {
            return;
        }
        ParcelUuid[] uuids = this.mDevice.getUuids();
        if (BluetoothUuid.containsAnyUuid(uuids, PbapServerProfile.PBAB_CLIENT_UUIDS) && this.mDevice.getPhonebookAccessPermission() == 0) {
            if (this.mDevice.getBluetoothClass().getDeviceClass() == 1032 || this.mDevice.getBluetoothClass().getDeviceClass() == 1028) {
                EventLog.writeEvent(1397638484, "138529441", -1, "");
            }
            this.mDevice.setPhonebookAccessPermission(2);
        }
    }

    public int getMaxConnectionState() {
        int maxState = 0;
        synchronized (this.mProfileLock) {
            for (LocalBluetoothProfile profile : getProfiles()) {
                int connectionStatus = getProfileConnectionState(profile);
                if (connectionStatus > maxState) {
                    maxState = connectionStatus;
                }
            }
        }
        return maxState;
    }

    public String getConnectionSummary() {
        return getConnectionSummary(false);
    }

    public String getConnectionSummary(boolean shortSummary) {
        boolean profileConnected = false;
        boolean a2dpConnected = true;
        boolean hfpConnected = true;
        boolean hearingAidConnected = true;
        int leftBattery = -1;
        int rightBattery = -1;
        synchronized (this.mProfileLock) {
            for (LocalBluetoothProfile profile : getProfiles()) {
                int connectionStatus = getProfileConnectionState(profile);
                if (connectionStatus != 0) {
                    if (connectionStatus != 1) {
                        if (connectionStatus == 2) {
                            profileConnected = true;
                        } else if (connectionStatus != 3) {
                        }
                    }
                    return this.mContext.getString(BluetoothUtils.getConnectionStateSummary(connectionStatus));
                } else if (profile.isProfileReady()) {
                    if (!(profile instanceof A2dpProfile) && !(profile instanceof A2dpSinkProfile)) {
                        if (!(profile instanceof HeadsetProfile) && !(profile instanceof HfpClientProfile)) {
                            if (profile instanceof HearingAidProfile) {
                                hearingAidConnected = false;
                            }
                        }
                        hfpConnected = false;
                    }
                    a2dpConnected = false;
                }
            }
            String batteryLevelPercentageString = null;
            int batteryLevel = getBatteryLevel();
            if (batteryLevel != -1) {
                batteryLevelPercentageString = Utils.formatPercentage(batteryLevel);
            }
            int stringRes = R.string.bluetooth_pairing;
            if (profileConnected) {
                if (BluetoothUtils.getBooleanMetaData(this.mDevice, 6)) {
                    leftBattery = BluetoothUtils.getIntMetaData(this.mDevice, 10);
                    rightBattery = BluetoothUtils.getIntMetaData(this.mDevice, 11);
                }
                if (isTwsBatteryAvailable(leftBattery, rightBattery)) {
                    stringRes = R.string.bluetooth_battery_level_untethered;
                } else if (batteryLevelPercentageString != null) {
                    stringRes = R.string.bluetooth_battery_level;
                }
                if (a2dpConnected || hfpConnected || hearingAidConnected) {
                    boolean isOnCall = Utils.isAudioModeOngoingCall(this.mContext);
                    if (this.mIsActiveDeviceHearingAid || ((this.mIsActiveDeviceHeadset && isOnCall) || (this.mIsActiveDeviceA2dp && !isOnCall))) {
                        if (isTwsBatteryAvailable(leftBattery, rightBattery) && !shortSummary) {
                            stringRes = R.string.bluetooth_active_battery_level_untethered;
                        } else if (batteryLevelPercentageString != null && !shortSummary) {
                            stringRes = R.string.bluetooth_active_battery_level;
                        } else {
                            stringRes = R.string.bluetooth_active_no_battery_level;
                        }
                    }
                }
            }
            if (stringRes != R.string.bluetooth_pairing || getBondState() == 11) {
                return isTwsBatteryAvailable(leftBattery, rightBattery) ? this.mContext.getString(stringRes, Utils.formatPercentage(leftBattery), Utils.formatPercentage(rightBattery)) : this.mContext.getString(stringRes, batteryLevelPercentageString);
            }
            return null;
        }
    }

    private boolean isTwsBatteryAvailable(int leftBattery, int rightBattery) {
        return leftBattery >= 0 && rightBattery >= 0;
    }

    public String getCarConnectionSummary() {
        String activeDeviceString;
        boolean profileConnected = false;
        boolean a2dpNotConnected = false;
        boolean hfpNotConnected = false;
        boolean hearingAidNotConnected = false;
        synchronized (this.mProfileLock) {
            for (LocalBluetoothProfile profile : getProfiles()) {
                int connectionStatus = getProfileConnectionState(profile);
                if (connectionStatus != 0) {
                    if (connectionStatus != 1) {
                        if (connectionStatus == 2) {
                            profileConnected = true;
                        } else if (connectionStatus != 3) {
                        }
                    }
                    return this.mContext.getString(BluetoothUtils.getConnectionStateSummary(connectionStatus));
                } else if (profile.isProfileReady()) {
                    if (!(profile instanceof A2dpProfile) && !(profile instanceof A2dpSinkProfile)) {
                        if (!(profile instanceof HeadsetProfile) && !(profile instanceof HfpClientProfile)) {
                            if (profile instanceof HearingAidProfile) {
                                hearingAidNotConnected = true;
                            }
                        }
                        hfpNotConnected = true;
                    }
                    a2dpNotConnected = true;
                }
            }
            String batteryLevelPercentageString = null;
            int batteryLevel = getBatteryLevel();
            if (batteryLevel != -1) {
                batteryLevelPercentageString = Utils.formatPercentage(batteryLevel);
            }
            String[] activeDeviceStringsArray = this.mContext.getResources().getStringArray(R.array.bluetooth_audio_active_device_summaries);
            String activeDeviceString2 = activeDeviceStringsArray[0];
            if (this.mIsActiveDeviceA2dp && this.mIsActiveDeviceHeadset) {
                activeDeviceString = activeDeviceStringsArray[1];
            } else {
                if (this.mIsActiveDeviceA2dp) {
                    activeDeviceString2 = activeDeviceStringsArray[2];
                }
                if (!this.mIsActiveDeviceHeadset) {
                    activeDeviceString = activeDeviceString2;
                } else {
                    activeDeviceString = activeDeviceStringsArray[3];
                }
            }
            if (!hearingAidNotConnected && this.mIsActiveDeviceHearingAid) {
                String activeDeviceString3 = activeDeviceStringsArray[1];
                return this.mContext.getString(R.string.bluetooth_connected, activeDeviceString3);
            } else if (profileConnected) {
                return (a2dpNotConnected && hfpNotConnected) ? batteryLevelPercentageString != null ? this.mContext.getString(R.string.bluetooth_connected_no_headset_no_a2dp_battery_level, batteryLevelPercentageString, activeDeviceString) : this.mContext.getString(R.string.bluetooth_connected_no_headset_no_a2dp, activeDeviceString) : a2dpNotConnected ? batteryLevelPercentageString != null ? this.mContext.getString(R.string.bluetooth_connected_no_a2dp_battery_level, batteryLevelPercentageString, activeDeviceString) : this.mContext.getString(R.string.bluetooth_connected_no_a2dp, activeDeviceString) : hfpNotConnected ? batteryLevelPercentageString != null ? this.mContext.getString(R.string.bluetooth_connected_no_headset_battery_level, batteryLevelPercentageString, activeDeviceString) : this.mContext.getString(R.string.bluetooth_connected_no_headset, activeDeviceString) : batteryLevelPercentageString != null ? this.mContext.getString(R.string.bluetooth_connected_battery_level, batteryLevelPercentageString, activeDeviceString) : this.mContext.getString(R.string.bluetooth_connected, activeDeviceString);
            } else if (getBondState() == 11) {
                return this.mContext.getString(R.string.bluetooth_pairing);
            } else {
                return null;
            }
        }
    }

    public boolean isConnectedA2dpDevice() {
        A2dpProfile a2dpProfile = this.mProfileManager.getA2dpProfile();
        return a2dpProfile != null && a2dpProfile.getConnectionStatus(this.mDevice) == 2;
    }

    public boolean isConnectedHfpDevice() {
        HeadsetProfile headsetProfile = this.mProfileManager.getHeadsetProfile();
        return headsetProfile != null && headsetProfile.getConnectionStatus(this.mDevice) == 2;
    }

    public boolean isConnectedHearingAidDevice() {
        HearingAidProfile hearingAidProfile = this.mProfileManager.getHearingAidProfile();
        return hearingAidProfile != null && hearingAidProfile.getConnectionStatus(this.mDevice) == 2;
    }

    public CachedBluetoothDevice getSubDevice() {
        return this.mSubDevice;
    }

    public void setSubDevice(CachedBluetoothDevice subDevice) {
        this.mSubDevice = subDevice;
    }

    public void switchSubDeviceContent() {
        BluetoothDevice tmpDevice = this.mDevice;
        short tmpRssi = this.mRssi;
        boolean tmpJustDiscovered = this.mJustDiscovered;
        CachedBluetoothDevice cachedBluetoothDevice = this.mSubDevice;
        this.mDevice = cachedBluetoothDevice.mDevice;
        this.mRssi = cachedBluetoothDevice.mRssi;
        this.mJustDiscovered = cachedBluetoothDevice.mJustDiscovered;
        cachedBluetoothDevice.mDevice = tmpDevice;
        cachedBluetoothDevice.mRssi = tmpRssi;
        cachedBluetoothDevice.mJustDiscovered = tmpJustDiscovered;
        fetchActiveDevices();
    }
}
