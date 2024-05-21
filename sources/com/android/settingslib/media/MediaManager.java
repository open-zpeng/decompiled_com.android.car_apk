package com.android.settingslib.media;

import android.app.Notification;
import android.content.Context;
import android.util.Log;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
/* loaded from: classes3.dex */
public abstract class MediaManager {
    private static final String TAG = "MediaManager";
    protected Context mContext;
    protected Notification mNotification;
    protected final Collection<MediaDeviceCallback> mCallbacks = new ArrayList();
    protected final List<MediaDevice> mMediaDevices = new ArrayList();

    /* loaded from: classes3.dex */
    public interface MediaDeviceCallback {
        void onConnectedDeviceChanged(String str);

        void onDeviceAdded(MediaDevice mediaDevice);

        void onDeviceAttributesChanged();

        void onDeviceListAdded(List<MediaDevice> list);

        void onDeviceListRemoved(List<MediaDevice> list);

        void onDeviceRemoved(MediaDevice mediaDevice);
    }

    public abstract void startScan();

    public abstract void stopScan();

    /* JADX INFO: Access modifiers changed from: package-private */
    public MediaManager(Context context, Notification notification) {
        this.mContext = context;
        this.mNotification = notification;
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public void registerCallback(MediaDeviceCallback callback) {
        synchronized (this.mCallbacks) {
            if (!this.mCallbacks.contains(callback)) {
                this.mCallbacks.add(callback);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public void unregisterCallback(MediaDeviceCallback callback) {
        synchronized (this.mCallbacks) {
            if (this.mCallbacks.contains(callback)) {
                this.mCallbacks.remove(callback);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public MediaDevice findMediaDevice(String id) {
        for (MediaDevice mediaDevice : this.mMediaDevices) {
            if (mediaDevice.getId().equals(id)) {
                return mediaDevice;
            }
        }
        Log.e(TAG, "findMediaDevice() can't found device");
        return null;
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public void dispatchDeviceAdded(MediaDevice mediaDevice) {
        synchronized (this.mCallbacks) {
            for (MediaDeviceCallback callback : this.mCallbacks) {
                callback.onDeviceAdded(mediaDevice);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public void dispatchDeviceRemoved(MediaDevice mediaDevice) {
        synchronized (this.mCallbacks) {
            for (MediaDeviceCallback callback : this.mCallbacks) {
                callback.onDeviceRemoved(mediaDevice);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public void dispatchDeviceListAdded() {
        synchronized (this.mCallbacks) {
            for (MediaDeviceCallback callback : this.mCallbacks) {
                callback.onDeviceListAdded(new ArrayList(this.mMediaDevices));
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public void dispatchDeviceListRemoved(List<MediaDevice> devices) {
        synchronized (this.mCallbacks) {
            for (MediaDeviceCallback callback : this.mCallbacks) {
                callback.onDeviceListRemoved(devices);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public void dispatchConnectedDeviceChanged(String id) {
        synchronized (this.mCallbacks) {
            for (MediaDeviceCallback callback : this.mCallbacks) {
                callback.onConnectedDeviceChanged(id);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public void dispatchDataChanged() {
        synchronized (this.mCallbacks) {
            for (MediaDeviceCallback callback : this.mCallbacks) {
                callback.onDeviceAttributesChanged();
            }
        }
    }
}
