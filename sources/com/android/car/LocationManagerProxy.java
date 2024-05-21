package com.android.car;

import android.car.ILocationManagerProxy;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;
import android.util.Slog;
/* loaded from: classes3.dex */
public class LocationManagerProxy extends ILocationManagerProxy.Stub {
    private final LocationManager mLocationManager;
    private static final String TAG = "LocationManagerProxy";
    private static final boolean DBG = Log.isLoggable(TAG, 3);

    public LocationManagerProxy(Context context) {
        if (DBG) {
            Slog.d(TAG, "constructed.");
        }
        this.mLocationManager = (LocationManager) context.getSystemService("location");
    }

    public boolean isLocationEnabled() {
        return this.mLocationManager.isLocationEnabled();
    }

    public boolean injectLocation(Location location) {
        return this.mLocationManager.injectLocation(location);
    }

    public Location getLastKnownLocation(String provider) {
        if (DBG) {
            Slog.d(TAG, "Getting last known location for provider " + provider);
        }
        return this.mLocationManager.getLastKnownLocation(provider);
    }
}
