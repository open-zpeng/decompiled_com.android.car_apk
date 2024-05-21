package com.android.settingslib.media;

import android.app.Notification;
import android.content.Context;
import android.util.Log;
import androidx.mediarouter.media.MediaRouteSelector;
import androidx.mediarouter.media.MediaRouter;
import com.android.internal.annotations.VisibleForTesting;
/* loaded from: classes3.dex */
public class InfoMediaManager extends MediaManager {
    private static final String TAG = "InfoMediaManager";
    @VisibleForTesting
    MediaRouter mMediaRouter;
    @VisibleForTesting
    final MediaRouterCallback mMediaRouterCallback;
    private String mPackageName;
    @VisibleForTesting
    MediaRouteSelector mSelector;

    InfoMediaManager(Context context, String packageName, Notification notification) {
        super(context, notification);
        this.mMediaRouterCallback = new MediaRouterCallback();
        this.mMediaRouter = MediaRouter.getInstance(context);
        this.mPackageName = packageName;
        this.mSelector = new MediaRouteSelector.Builder().addControlCategory(getControlCategoryByPackageName(this.mPackageName)).build();
    }

    @Override // com.android.settingslib.media.MediaManager
    public void startScan() {
        this.mMediaDevices.clear();
        this.mMediaRouter.addCallback(this.mSelector, this.mMediaRouterCallback, 4);
    }

    @VisibleForTesting
    String getControlCategoryByPackageName(String packageName) {
        return "com.google.android.gms.cast.CATEGORY_CAST/4F8B3483";
    }

    @Override // com.android.settingslib.media.MediaManager
    public void stopScan() {
        this.mMediaRouter.removeCallback(this.mMediaRouterCallback);
    }

    /* loaded from: classes3.dex */
    class MediaRouterCallback extends MediaRouter.Callback {
        MediaRouterCallback() {
        }

        @Override // androidx.mediarouter.media.MediaRouter.Callback
        public void onRouteAdded(MediaRouter router, MediaRouter.RouteInfo route) {
            if (InfoMediaManager.this.findMediaDevice(MediaDeviceUtils.getId(route)) == null) {
                MediaDevice mediaDevice = new InfoMediaDevice(InfoMediaManager.this.mContext, route);
                Log.d(InfoMediaManager.TAG, "onRouteAdded() route : " + route.getName());
                InfoMediaManager.this.mMediaDevices.add(mediaDevice);
                InfoMediaManager.this.dispatchDeviceAdded(mediaDevice);
            }
        }

        @Override // androidx.mediarouter.media.MediaRouter.Callback
        public void onRouteRemoved(MediaRouter router, MediaRouter.RouteInfo route) {
            MediaDevice mediaDevice = InfoMediaManager.this.findMediaDevice(MediaDeviceUtils.getId(route));
            if (mediaDevice != null) {
                Log.d(InfoMediaManager.TAG, "onRouteRemoved() route : " + route.getName());
                InfoMediaManager.this.mMediaDevices.remove(mediaDevice);
                InfoMediaManager.this.dispatchDeviceRemoved(mediaDevice);
            }
        }
    }
}
