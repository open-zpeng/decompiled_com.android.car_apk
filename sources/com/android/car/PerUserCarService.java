package com.android.car;

import android.app.Service;
import android.car.ICarBluetoothUserService;
import android.car.ICarUserService;
import android.car.ILocationManagerProxy;
import android.content.Intent;
import android.os.IBinder;
import android.util.Slog;
/* loaded from: classes3.dex */
public class PerUserCarService extends Service {
    private static final boolean DBG = true;
    private static final String TAG = "CarUserService";
    private volatile CarBluetoothUserService mCarBluetoothUserService;
    private CarUserServiceBinder mCarUserServiceBinder;
    private volatile LocationManagerProxy mLocationManagerProxy;

    @Override // android.app.Service
    public IBinder onBind(Intent intent) {
        Slog.d(TAG, "onBind()");
        if (this.mCarUserServiceBinder == null) {
            Slog.e(TAG, "UserSvcBinder null");
        }
        return this.mCarUserServiceBinder;
    }

    @Override // android.app.Service
    public int onStartCommand(Intent intent, int flags, int startId) {
        Slog.d(TAG, "onStart()");
        return 1;
    }

    @Override // android.app.Service
    public void onCreate() {
        Slog.d(TAG, "onCreate()");
        this.mCarUserServiceBinder = new CarUserServiceBinder();
        this.mCarBluetoothUserService = new CarBluetoothUserService(this);
        this.mLocationManagerProxy = new LocationManagerProxy(this);
        super.onCreate();
    }

    @Override // android.app.Service
    public void onDestroy() {
        Slog.d(TAG, "onDestroy()");
        this.mCarUserServiceBinder = null;
    }

    /* loaded from: classes3.dex */
    private final class CarUserServiceBinder extends ICarUserService.Stub {
        private CarUserServiceBinder() {
        }

        public ICarBluetoothUserService getBluetoothUserService() {
            return PerUserCarService.this.mCarBluetoothUserService;
        }

        public ILocationManagerProxy getLocationManagerProxy() {
            return PerUserCarService.this.mLocationManagerProxy;
        }
    }
}
