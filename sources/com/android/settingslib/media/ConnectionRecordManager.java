package com.android.settingslib.media;

import android.content.Context;
import android.content.SharedPreferences;
/* loaded from: classes3.dex */
public class ConnectionRecordManager {
    private static final String KEY_LAST_SELECTED_DEVICE = "last_selected_device";
    private static final String SHARED_PREFERENCES_NAME = "seamless_transfer_record";
    private static final String TAG = "ConnectionRecordManager";
    private static ConnectionRecordManager sInstance;
    private static final Object sInstanceSync = new Object();
    private String mLastSelectedDevice;

    public static ConnectionRecordManager getInstance() {
        synchronized (sInstanceSync) {
            if (sInstance == null) {
                sInstance = new ConnectionRecordManager();
            }
        }
        return sInstance;
    }

    private SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(SHARED_PREFERENCES_NAME, 0);
    }

    public synchronized int fetchConnectionRecord(Context context, String id) {
        return getSharedPreferences(context).getInt(id, 0);
    }

    public synchronized void fetchLastSelectedDevice(Context context) {
        this.mLastSelectedDevice = getSharedPreferences(context).getString(KEY_LAST_SELECTED_DEVICE, null);
    }

    public synchronized void setConnectionRecord(Context context, String id, int record) {
        SharedPreferences.Editor editor = getSharedPreferences(context).edit();
        this.mLastSelectedDevice = id;
        editor.putInt(this.mLastSelectedDevice, record);
        editor.putString(KEY_LAST_SELECTED_DEVICE, this.mLastSelectedDevice);
        editor.apply();
    }

    public synchronized String getLastSelectedDevice() {
        return this.mLastSelectedDevice;
    }
}
