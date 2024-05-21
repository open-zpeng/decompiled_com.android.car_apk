package com.android.settingslib.bluetooth;

import android.content.Context;
import android.os.Handler;
import android.os.UserHandle;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import java.lang.ref.WeakReference;
/* loaded from: classes3.dex */
public class LocalBluetoothManager {
    private static final String TAG = "LocalBluetoothManager";
    private static LocalBluetoothManager sInstance;
    private final CachedBluetoothDeviceManager mCachedDeviceManager;
    private final Context mContext;
    private final BluetoothEventManager mEventManager;
    private WeakReference<Context> mForegroundActivity;
    private final LocalBluetoothAdapter mLocalAdapter;
    private final LocalBluetoothProfileManager mProfileManager;

    /* loaded from: classes3.dex */
    public interface BluetoothManagerCallback {
        void onBluetoothManagerInitialized(Context context, LocalBluetoothManager localBluetoothManager);
    }

    @Nullable
    public static synchronized LocalBluetoothManager getInstance(Context context, BluetoothManagerCallback onInitCallback) {
        synchronized (LocalBluetoothManager.class) {
            if (sInstance == null) {
                LocalBluetoothAdapter adapter = LocalBluetoothAdapter.getInstance();
                if (adapter == null) {
                    return null;
                }
                sInstance = new LocalBluetoothManager(adapter, context, null, null);
                if (onInitCallback != null) {
                    onInitCallback.onBluetoothManagerInitialized(context.getApplicationContext(), sInstance);
                }
            }
            return sInstance;
        }
    }

    @Nullable
    public static LocalBluetoothManager create(Context context, Handler handler) {
        LocalBluetoothAdapter adapter = LocalBluetoothAdapter.getInstance();
        if (adapter == null) {
            return null;
        }
        return new LocalBluetoothManager(adapter, context, handler, null);
    }

    @Nullable
    @RequiresPermission("android.permission.INTERACT_ACROSS_USERS_FULL")
    public static LocalBluetoothManager create(Context context, Handler handler, UserHandle userHandle) {
        LocalBluetoothAdapter adapter = LocalBluetoothAdapter.getInstance();
        if (adapter == null) {
            return null;
        }
        return new LocalBluetoothManager(adapter, context, handler, userHandle);
    }

    private LocalBluetoothManager(LocalBluetoothAdapter adapter, Context context, Handler handler, UserHandle userHandle) {
        this.mContext = context.getApplicationContext();
        this.mLocalAdapter = adapter;
        this.mCachedDeviceManager = new CachedBluetoothDeviceManager(this.mContext, this);
        this.mEventManager = new BluetoothEventManager(this.mLocalAdapter, this.mCachedDeviceManager, this.mContext, handler, userHandle);
        this.mProfileManager = new LocalBluetoothProfileManager(this.mContext, this.mLocalAdapter, this.mCachedDeviceManager, this.mEventManager);
        this.mProfileManager.updateLocalProfiles();
        this.mEventManager.readPairedDevices();
    }

    public LocalBluetoothAdapter getBluetoothAdapter() {
        return this.mLocalAdapter;
    }

    public Context getContext() {
        return this.mContext;
    }

    public Context getForegroundActivity() {
        WeakReference<Context> weakReference = this.mForegroundActivity;
        if (weakReference == null) {
            return null;
        }
        return weakReference.get();
    }

    public boolean isForegroundActivity() {
        WeakReference<Context> weakReference = this.mForegroundActivity;
        return (weakReference == null || weakReference.get() == null) ? false : true;
    }

    public synchronized void setForegroundActivity(Context context) {
        if (context != null) {
            Log.d(TAG, "setting foreground activity to non-null context");
            this.mForegroundActivity = new WeakReference<>(context);
        } else if (this.mForegroundActivity != null) {
            Log.d(TAG, "setting foreground activity to null");
            this.mForegroundActivity = null;
        }
    }

    public CachedBluetoothDeviceManager getCachedDeviceManager() {
        return this.mCachedDeviceManager;
    }

    public BluetoothEventManager getEventManager() {
        return this.mEventManager;
    }

    public LocalBluetoothProfileManager getProfileManager() {
        return this.mProfileManager;
    }
}
