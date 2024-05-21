package com.android.settingslib.media;

import android.app.Notification;
import android.content.Context;
import android.util.Log;
import com.android.internal.annotations.VisibleForTesting;
import com.android.settingslib.bluetooth.BluetoothCallback;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.media.MediaManager;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
/* loaded from: classes3.dex */
public class LocalMediaManager implements BluetoothCallback {
    private static final Comparator<MediaDevice> COMPARATOR = Comparator.naturalOrder();
    private static final String TAG = "LocalMediaManager";
    private BluetoothMediaManager mBluetoothMediaManager;
    private Context mContext;
    @VisibleForTesting
    MediaDevice mCurrentConnectedDevice;
    private LocalBluetoothManager mLocalBluetoothManager;
    @VisibleForTesting
    MediaDevice mPhoneDevice;
    private final Collection<DeviceCallback> mCallbacks = new ArrayList();
    @VisibleForTesting
    final MediaDeviceCallback mMediaDeviceCallback = new MediaDeviceCallback();
    @VisibleForTesting
    List<MediaDevice> mMediaDevices = new ArrayList();

    /* loaded from: classes3.dex */
    public interface DeviceCallback {
        void onDeviceListUpdate(List<MediaDevice> list);

        void onSelectedDeviceStateChanged(MediaDevice mediaDevice, int i);
    }

    @Retention(RetentionPolicy.SOURCE)
    /* loaded from: classes3.dex */
    public @interface MediaDeviceState {
        public static final int STATE_CONNECTED = 1;
        public static final int STATE_CONNECTING = 2;
        public static final int STATE_DISCONNECTED = 3;
    }

    public void registerCallback(DeviceCallback callback) {
        synchronized (this.mCallbacks) {
            this.mCallbacks.add(callback);
        }
    }

    public void unregisterCallback(DeviceCallback callback) {
        synchronized (this.mCallbacks) {
            this.mCallbacks.remove(callback);
        }
    }

    public LocalMediaManager(Context context, String packageName, Notification notification) {
        this.mContext = context;
        this.mLocalBluetoothManager = LocalBluetoothManager.getInstance(context, null);
        LocalBluetoothManager localBluetoothManager = this.mLocalBluetoothManager;
        if (localBluetoothManager == null) {
            Log.e(TAG, "Bluetooth is not supported on this device");
        } else {
            this.mBluetoothMediaManager = new BluetoothMediaManager(context, localBluetoothManager, notification);
        }
    }

    @VisibleForTesting
    LocalMediaManager(Context context, LocalBluetoothManager localBluetoothManager, BluetoothMediaManager bluetoothMediaManager, InfoMediaManager infoMediaManager) {
        this.mContext = context;
        this.mLocalBluetoothManager = localBluetoothManager;
        this.mBluetoothMediaManager = bluetoothMediaManager;
    }

    public void connectDevice(MediaDevice connectDevice) {
        MediaDevice device = getMediaDeviceById(this.mMediaDevices, connectDevice.getId());
        if (device instanceof BluetoothMediaDevice) {
            CachedBluetoothDevice cachedDevice = ((BluetoothMediaDevice) device).getCachedDevice();
            if (!cachedDevice.isConnected() && !cachedDevice.isBusy()) {
                cachedDevice.connect(true);
                return;
            }
        }
        MediaDevice mediaDevice = this.mCurrentConnectedDevice;
        if (device == mediaDevice) {
            Log.d(TAG, "connectDevice() this device all ready connected! : " + device.getName());
            return;
        }
        if (mediaDevice != null && !(connectDevice instanceof InfoMediaDevice)) {
            mediaDevice.disconnect();
        }
        boolean isConnected = device.connect();
        if (isConnected) {
            this.mCurrentConnectedDevice = device;
        }
        int state = isConnected ? 1 : 3;
        dispatchSelectedDeviceStateChanged(device, state);
    }

    void dispatchSelectedDeviceStateChanged(MediaDevice device, int state) {
        synchronized (this.mCallbacks) {
            for (DeviceCallback callback : this.mCallbacks) {
                callback.onSelectedDeviceStateChanged(device, state);
            }
        }
    }

    public void startScan() {
        this.mMediaDevices.clear();
        this.mBluetoothMediaManager.registerCallback(this.mMediaDeviceCallback);
        this.mBluetoothMediaManager.startScan();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void addPhoneDeviceIfNecessary() {
        if (this.mMediaDevices.size() > 0 && !this.mMediaDevices.contains(this.mPhoneDevice)) {
            if (this.mPhoneDevice == null) {
                this.mPhoneDevice = new PhoneMediaDevice(this.mContext, this.mLocalBluetoothManager);
            }
            this.mMediaDevices.add(this.mPhoneDevice);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void removePhoneMediaDeviceIfNecessary() {
        if (this.mMediaDevices.size() == 1 && this.mMediaDevices.contains(this.mPhoneDevice)) {
            this.mMediaDevices.clear();
        }
    }

    void dispatchDeviceListUpdate() {
        synchronized (this.mCallbacks) {
            Collections.sort(this.mMediaDevices, COMPARATOR);
            for (DeviceCallback callback : this.mCallbacks) {
                callback.onDeviceListUpdate(new ArrayList(this.mMediaDevices));
            }
        }
    }

    public void stopScan() {
        this.mBluetoothMediaManager.unregisterCallback(this.mMediaDeviceCallback);
        this.mBluetoothMediaManager.stopScan();
    }

    public MediaDevice getMediaDeviceById(List<MediaDevice> devices, String id) {
        for (MediaDevice mediaDevice : devices) {
            if (mediaDevice.getId().equals(id)) {
                return mediaDevice;
            }
        }
        Log.i(TAG, "getMediaDeviceById() can't found device");
        return null;
    }

    public MediaDevice getCurrentConnectedDevice() {
        return this.mCurrentConnectedDevice;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public MediaDevice updateCurrentConnectedDevice() {
        for (MediaDevice device : this.mMediaDevices) {
            if ((device instanceof BluetoothMediaDevice) && isConnected(((BluetoothMediaDevice) device).getCachedDevice())) {
                return device;
            }
        }
        if (this.mMediaDevices.contains(this.mPhoneDevice)) {
            return this.mPhoneDevice;
        }
        return null;
    }

    private boolean isConnected(CachedBluetoothDevice device) {
        return device.isActiveDevice(2) || device.isActiveDevice(21);
    }

    /* loaded from: classes3.dex */
    class MediaDeviceCallback implements MediaManager.MediaDeviceCallback {
        MediaDeviceCallback() {
        }

        @Override // com.android.settingslib.media.MediaManager.MediaDeviceCallback
        public void onDeviceAdded(MediaDevice device) {
            if (!LocalMediaManager.this.mMediaDevices.contains(device)) {
                LocalMediaManager.this.mMediaDevices.add(device);
                LocalMediaManager.this.addPhoneDeviceIfNecessary();
                LocalMediaManager.this.dispatchDeviceListUpdate();
            }
        }

        @Override // com.android.settingslib.media.MediaManager.MediaDeviceCallback
        public void onDeviceListAdded(List<MediaDevice> devices) {
            for (MediaDevice device : devices) {
                LocalMediaManager localMediaManager = LocalMediaManager.this;
                if (localMediaManager.getMediaDeviceById(localMediaManager.mMediaDevices, device.getId()) == null) {
                    LocalMediaManager.this.mMediaDevices.add(device);
                }
            }
            LocalMediaManager.this.addPhoneDeviceIfNecessary();
            LocalMediaManager localMediaManager2 = LocalMediaManager.this;
            localMediaManager2.mCurrentConnectedDevice = localMediaManager2.updateCurrentConnectedDevice();
            updatePhoneMediaDeviceSummary();
            LocalMediaManager.this.dispatchDeviceListUpdate();
        }

        private void updatePhoneMediaDeviceSummary() {
            if (LocalMediaManager.this.mPhoneDevice != null) {
                ((PhoneMediaDevice) LocalMediaManager.this.mPhoneDevice).updateSummary(LocalMediaManager.this.mCurrentConnectedDevice == LocalMediaManager.this.mPhoneDevice);
            }
        }

        @Override // com.android.settingslib.media.MediaManager.MediaDeviceCallback
        public void onDeviceRemoved(MediaDevice device) {
            if (LocalMediaManager.this.mMediaDevices.contains(device)) {
                LocalMediaManager.this.mMediaDevices.remove(device);
                LocalMediaManager.this.removePhoneMediaDeviceIfNecessary();
                LocalMediaManager.this.dispatchDeviceListUpdate();
            }
        }

        @Override // com.android.settingslib.media.MediaManager.MediaDeviceCallback
        public void onDeviceListRemoved(List<MediaDevice> devices) {
            LocalMediaManager.this.mMediaDevices.removeAll(devices);
            LocalMediaManager.this.removePhoneMediaDeviceIfNecessary();
            LocalMediaManager.this.dispatchDeviceListUpdate();
        }

        @Override // com.android.settingslib.media.MediaManager.MediaDeviceCallback
        public void onConnectedDeviceChanged(String id) {
            LocalMediaManager localMediaManager = LocalMediaManager.this;
            MediaDevice connectDevice = localMediaManager.getMediaDeviceById(localMediaManager.mMediaDevices, id);
            if (connectDevice == LocalMediaManager.this.mCurrentConnectedDevice) {
                Log.d(LocalMediaManager.TAG, "onConnectedDeviceChanged() this device all ready connected!");
                return;
            }
            LocalMediaManager.this.mCurrentConnectedDevice = connectDevice;
            updatePhoneMediaDeviceSummary();
            LocalMediaManager.this.dispatchDeviceListUpdate();
        }

        @Override // com.android.settingslib.media.MediaManager.MediaDeviceCallback
        public void onDeviceAttributesChanged() {
            LocalMediaManager.this.addPhoneDeviceIfNecessary();
            LocalMediaManager.this.removePhoneMediaDeviceIfNecessary();
            LocalMediaManager.this.dispatchDeviceListUpdate();
        }
    }
}
