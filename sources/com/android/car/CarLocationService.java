package com.android.car;

import android.app.ActivityManager;
import android.car.ICarUserService;
import android.car.ILocationManagerProxy;
import android.car.drivingstate.CarDrivingStateEvent;
import android.car.drivingstate.ICarDrivingStateChangeListener;
import android.car.encryptionrunner.DummyEncryptionRunner;
import android.car.hardware.power.CarPowerManager;
import android.car.userlib.CarUserManagerHelper;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Slog;
import com.android.car.CarLocationService;
import com.android.car.PerUserCarServiceHelper;
import com.android.car.systeminterface.SystemInterface;
import com.android.internal.annotations.VisibleForTesting;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.concurrent.CompletableFuture;
/* loaded from: classes3.dex */
public class CarLocationService extends BroadcastReceiver implements CarServiceBase, CarPowerManager.CarPowerStateListenerWithCompletion {
    private static final boolean DBG = true;
    private static final String FILENAME = "location_cache.json";
    private static final long GRANULARITY_ONE_DAY_MS = 86400000;
    private static final int MAX_LOCATION_INJECTION_ATTEMPTS = 10;
    private static final String TAG = "CarLocationService";
    private static final long TTL_THIRTY_DAYS_MS = 2592000000L;
    private CarDrivingStateService mCarDrivingStateService;
    private CarPowerManager mCarPowerManager;
    private final CarUserManagerHelper mCarUserManagerHelper;
    private final Context mContext;
    private Handler mHandler;
    private HandlerThread mHandlerThread;
    private ILocationManagerProxy mILocationManagerProxy;
    private PerUserCarServiceHelper mPerUserCarServiceHelper;
    private final Object mLock = new Object();
    private final Object mLocationManagerProxyLock = new Object();
    private int mTaskCount = 0;
    private final PerUserCarServiceHelper.ServiceCallback mUserServiceCallback = new AnonymousClass1();
    private final ICarDrivingStateChangeListener mICarDrivingStateChangeEventListener = new ICarDrivingStateChangeListener.Stub() { // from class: com.android.car.CarLocationService.2
        public void onDrivingStateChanged(CarDrivingStateEvent event) {
            CarLocationService.logd("onDrivingStateChanged " + event);
            if (event != null && event.eventValue == 2) {
                CarLocationService.this.deleteCacheFile();
                if (CarLocationService.this.mCarDrivingStateService != null) {
                    CarLocationService.this.mCarDrivingStateService.unregisterDrivingStateChangeListener(CarLocationService.this.mICarDrivingStateChangeEventListener);
                }
            }
        }
    };

    /* JADX INFO: Access modifiers changed from: package-private */
    /* renamed from: com.android.car.CarLocationService$1  reason: invalid class name */
    /* loaded from: classes3.dex */
    public class AnonymousClass1 implements PerUserCarServiceHelper.ServiceCallback {
        AnonymousClass1() {
        }

        @Override // com.android.car.PerUserCarServiceHelper.ServiceCallback
        public void onServiceConnected(ICarUserService carUserService) {
            CarLocationService.logd("Connected to PerUserCarService");
            if (carUserService == null) {
                CarLocationService.logd("ICarUserService is null. Cannot get location manager proxy");
                return;
            }
            synchronized (CarLocationService.this.mLocationManagerProxyLock) {
                try {
                    CarLocationService.this.mILocationManagerProxy = carUserService.getLocationManagerProxy();
                } catch (RemoteException e) {
                    Slog.e(CarLocationService.TAG, "RemoteException from ICarUserService", e);
                    return;
                }
            }
            int currentUser = ActivityManager.getCurrentUser();
            CarLocationService.logd("Current user: " + currentUser);
            if (CarLocationService.this.mCarUserManagerHelper.isHeadlessSystemUser() && currentUser > 0) {
                CarLocationService.this.asyncOperation(new Runnable() { // from class: com.android.car.-$$Lambda$CarLocationService$1$PJ26-jNRQe6t5bQwFKIA9ddCeVY
                    @Override // java.lang.Runnable
                    public final void run() {
                        CarLocationService.AnonymousClass1.this.lambda$onServiceConnected$0$CarLocationService$1();
                    }
                });
            }
        }

        public /* synthetic */ void lambda$onServiceConnected$0$CarLocationService$1() {
            CarLocationService.this.loadLocation();
        }

        @Override // com.android.car.PerUserCarServiceHelper.ServiceCallback
        public void onPreUnbind() {
            CarLocationService.logd("Before Unbinding from PerCarUserService");
            synchronized (CarLocationService.this.mLocationManagerProxyLock) {
                CarLocationService.this.mILocationManagerProxy = null;
            }
        }

        @Override // com.android.car.PerUserCarServiceHelper.ServiceCallback
        public void onServiceDisconnected() {
            CarLocationService.logd("Disconnected from PerUserCarService");
            synchronized (CarLocationService.this.mLocationManagerProxyLock) {
                CarLocationService.this.mILocationManagerProxy = null;
            }
        }
    }

    public CarLocationService(Context context, CarUserManagerHelper carUserManagerHelper) {
        logd("constructed");
        this.mContext = context;
        this.mCarUserManagerHelper = carUserManagerHelper;
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
        logd(DummyEncryptionRunner.INIT);
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.location.MODE_CHANGED");
        this.mContext.registerReceiver(this, filter);
        this.mCarDrivingStateService = (CarDrivingStateService) CarLocalServices.getService(CarDrivingStateService.class);
        CarDrivingStateService carDrivingStateService = this.mCarDrivingStateService;
        if (carDrivingStateService != null) {
            CarDrivingStateEvent event = carDrivingStateService.getCurrentDrivingState();
            if (event != null && event.eventValue == 2) {
                deleteCacheFile();
            } else {
                this.mCarDrivingStateService.registerDrivingStateChangeListener(this.mICarDrivingStateChangeEventListener);
            }
        }
        this.mCarPowerManager = CarLocalServices.createCarPowerManager(this.mContext);
        CarPowerManager carPowerManager = this.mCarPowerManager;
        if (carPowerManager != null) {
            carPowerManager.setListenerWithCompletion(this);
        }
        this.mPerUserCarServiceHelper = (PerUserCarServiceHelper) CarLocalServices.getService(PerUserCarServiceHelper.class);
        PerUserCarServiceHelper perUserCarServiceHelper = this.mPerUserCarServiceHelper;
        if (perUserCarServiceHelper != null) {
            perUserCarServiceHelper.registerServiceCallback(this.mUserServiceCallback);
        }
    }

    @Override // com.android.car.CarServiceBase
    public void release() {
        logd("release");
        CarPowerManager carPowerManager = this.mCarPowerManager;
        if (carPowerManager != null) {
            carPowerManager.clearListener();
        }
        CarDrivingStateService carDrivingStateService = this.mCarDrivingStateService;
        if (carDrivingStateService != null) {
            carDrivingStateService.unregisterDrivingStateChangeListener(this.mICarDrivingStateChangeEventListener);
        }
        PerUserCarServiceHelper perUserCarServiceHelper = this.mPerUserCarServiceHelper;
        if (perUserCarServiceHelper != null) {
            perUserCarServiceHelper.unregisterServiceCallback(this.mUserServiceCallback);
        }
        this.mContext.unregisterReceiver(this);
    }

    @Override // com.android.car.CarServiceBase
    public void dump(PrintWriter writer) {
        writer.println(TAG);
        writer.println("Context: " + this.mContext);
        writer.println("MAX_LOCATION_INJECTION_ATTEMPTS: 10");
    }

    public void onStateChanged(int state, final CompletableFuture<Void> future) {
        logd("onStateChanged: " + state);
        if (state == 3) {
            CarDrivingStateService carDrivingStateService = this.mCarDrivingStateService;
            if (carDrivingStateService != null) {
                CarDrivingStateEvent event = carDrivingStateService.getCurrentDrivingState();
                if (event != null && event.eventValue == 2) {
                    deleteCacheFile();
                } else {
                    logd("Registering to receive driving state.");
                    this.mCarDrivingStateService.registerDrivingStateChangeListener(this.mICarDrivingStateChangeEventListener);
                }
            }
            if (future != null) {
                future.complete(null);
            }
        } else if (state == 7) {
            asyncOperation(new Runnable() { // from class: com.android.car.-$$Lambda$CarLocationService$LTyswo2Q2YA9ZwNV60FLqVhM8VE
                @Override // java.lang.Runnable
                public final void run() {
                    CarLocationService.this.lambda$onStateChanged$0$CarLocationService(future);
                }
            });
            return;
        }
        if (future != null) {
            future.complete(null);
        }
    }

    public /* synthetic */ void lambda$onStateChanged$0$CarLocationService(CompletableFuture future) {
        storeLocation();
        if (future != null) {
            future.complete(null);
        }
    }

    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        logd("onReceive " + intent);
        if (isCurrentUserHeadlessSystemUser()) {
            logd("Current user is headless system user.");
            return;
        }
        synchronized (this.mLocationManagerProxyLock) {
            if (this.mILocationManagerProxy == null) {
                logd("Null location manager.");
                return;
            }
            String action = intent.getAction();
            try {
                if ("android.location.MODE_CHANGED".equals(action)) {
                    boolean locationEnabled = this.mILocationManagerProxy.isLocationEnabled();
                    logd("isLocationEnabled(): " + locationEnabled);
                    if (!locationEnabled) {
                        deleteCacheFile();
                    }
                } else {
                    logd("Unexpected intent.");
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "RemoteException from ILocationManagerProxy", e);
            }
        }
    }

    private boolean isCurrentUserHeadlessSystemUser() {
        int currentUserId = ActivityManager.getCurrentUser();
        return this.mCarUserManagerHelper.isHeadlessSystemUser() && currentUserId == 0;
    }

    private void storeLocation() {
        Location location = null;
        synchronized (this.mLocationManagerProxyLock) {
            if (this.mILocationManagerProxy == null) {
                logd("Null location manager proxy.");
                return;
            }
            try {
                location = this.mILocationManagerProxy.getLastKnownLocation("gps");
            } catch (RemoteException e) {
                Slog.e(TAG, "RemoteException from ILocationManagerProxy", e);
            }
            if (location == null) {
                logd("Not storing null location");
                return;
            }
            logd("Storing location");
            AtomicFile atomicFile = new AtomicFile(getLocationCacheFile());
            FileOutputStream fos = null;
            try {
                fos = atomicFile.startWrite();
                JsonWriter jsonWriter = new JsonWriter(new OutputStreamWriter(fos, "UTF-8"));
                jsonWriter.beginObject();
                jsonWriter.name("provider").value(location.getProvider());
                jsonWriter.name("latitude").value(location.getLatitude());
                jsonWriter.name("longitude").value(location.getLongitude());
                if (location.hasAltitude()) {
                    jsonWriter.name("altitude").value(location.getAltitude());
                }
                if (location.hasSpeed()) {
                    jsonWriter.name("speed").value(location.getSpeed());
                }
                if (location.hasBearing()) {
                    jsonWriter.name("bearing").value(location.getBearing());
                }
                if (location.hasAccuracy()) {
                    jsonWriter.name("accuracy").value(location.getAccuracy());
                }
                if (location.hasVerticalAccuracy()) {
                    jsonWriter.name("verticalAccuracy").value(location.getVerticalAccuracyMeters());
                }
                if (location.hasSpeedAccuracy()) {
                    jsonWriter.name("speedAccuracy").value(location.getSpeedAccuracyMetersPerSecond());
                }
                if (location.hasBearingAccuracy()) {
                    jsonWriter.name("bearingAccuracy").value(location.getBearingAccuracyDegrees());
                }
                if (location.isFromMockProvider()) {
                    jsonWriter.name("isFromMockProvider").value(true);
                }
                long currentTime = location.getTime();
                jsonWriter.name("captureTime").value(currentTime - (currentTime % GRANULARITY_ONE_DAY_MS));
                jsonWriter.endObject();
                $closeResource(null, jsonWriter);
                atomicFile.finishWrite(fos);
            } catch (IOException e2) {
                Slog.e(TAG, "Unable to write to disk", e2);
                atomicFile.failWrite(fos);
            }
        }
    }

    private static /* synthetic */ void $closeResource(Throwable x0, AutoCloseable x1) {
        if (x0 == null) {
            x1.close();
            return;
        }
        try {
            x1.close();
        } catch (Throwable th) {
            x0.addSuppressed(th);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void loadLocation() {
        Location location = readLocationFromCacheFile();
        logd("Read location from timestamp " + location.getTime());
        long currentTime = System.currentTimeMillis();
        if (location.getTime() + TTL_THIRTY_DAYS_MS < currentTime) {
            logd("Location expired.");
            deleteCacheFile();
            return;
        }
        location.setTime(currentTime);
        long elapsedTime = SystemClock.elapsedRealtimeNanos();
        location.setElapsedRealtimeNanos(elapsedTime);
        if (location.isComplete()) {
            injectLocation(location, 1);
        }
    }

    private Location readLocationFromCacheFile() {
        Location location = new Location((String) null);
        AtomicFile atomicFile = new AtomicFile(getLocationCacheFile());
        try {
            try {
                FileInputStream fis = atomicFile.openRead();
                try {
                    JsonReader reader = new JsonReader(new InputStreamReader(fis, "UTF-8"));
                    reader.beginObject();
                    while (reader.hasNext()) {
                        String name = reader.nextName();
                        if (name.equals("provider")) {
                            location.setProvider(reader.nextString());
                        } else if (name.equals("latitude")) {
                            location.setLatitude(reader.nextDouble());
                        } else if (name.equals("longitude")) {
                            location.setLongitude(reader.nextDouble());
                        } else if (name.equals("altitude")) {
                            location.setAltitude(reader.nextDouble());
                        } else if (name.equals("speed")) {
                            location.setSpeed((float) reader.nextDouble());
                        } else if (name.equals("bearing")) {
                            location.setBearing((float) reader.nextDouble());
                        } else if (name.equals("accuracy")) {
                            location.setAccuracy((float) reader.nextDouble());
                        } else if (name.equals("verticalAccuracy")) {
                            location.setVerticalAccuracyMeters((float) reader.nextDouble());
                        } else if (name.equals("speedAccuracy")) {
                            location.setSpeedAccuracyMetersPerSecond((float) reader.nextDouble());
                        } else if (name.equals("bearingAccuracy")) {
                            location.setBearingAccuracyDegrees((float) reader.nextDouble());
                        } else if (name.equals("isFromMockProvider")) {
                            location.setIsFromMockProvider(reader.nextBoolean());
                        } else if (name.equals("captureTime")) {
                            location.setTime(reader.nextLong());
                        } else {
                            reader.skipValue();
                        }
                    }
                    reader.endObject();
                    if (fis != null) {
                        $closeResource(null, fis);
                    }
                } finally {
                }
            } catch (IllegalStateException | NumberFormatException e) {
                Slog.e(TAG, "Unexpected format", e);
            }
        } catch (FileNotFoundException e2) {
            Slog.d(TAG, "Location cache file not found.");
        } catch (IOException e3) {
            Slog.e(TAG, "Unable to read from disk", e3);
        }
        return location;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void deleteCacheFile() {
        boolean deleted = getLocationCacheFile().delete();
        logd("Deleted cache file: " + deleted);
    }

    private void injectLocation(final Location location, final int attemptCount) {
        boolean success = false;
        synchronized (this.mLocationManagerProxyLock) {
            if (this.mILocationManagerProxy == null) {
                logd("Null location manager proxy.");
            } else {
                try {
                    success = this.mILocationManagerProxy.injectLocation(location);
                } catch (RemoteException e) {
                    Slog.e(TAG, "RemoteException from ILocationManagerProxy", e);
                }
            }
        }
        logd("Injected location with result " + success + " on attempt " + attemptCount);
        if (success) {
            return;
        }
        if (attemptCount <= 10) {
            asyncOperation(new Runnable() { // from class: com.android.car.-$$Lambda$CarLocationService$_hxnlSBPJEEas1P4iAESb9_xKKU
                @Override // java.lang.Runnable
                public final void run() {
                    CarLocationService.this.lambda$injectLocation$1$CarLocationService(location, attemptCount);
                }
            }, attemptCount * 200);
        } else {
            logd("No location injected.");
        }
    }

    public /* synthetic */ void lambda$injectLocation$1$CarLocationService(Location location, int attemptCount) {
        injectLocation(location, attemptCount + 1);
    }

    private File getLocationCacheFile() {
        SystemInterface systemInterface = (SystemInterface) CarLocalServices.getService(SystemInterface.class);
        File file = new File(systemInterface.getSystemCarDir(), FILENAME);
        logd("File: " + file);
        return file;
    }

    @VisibleForTesting
    void asyncOperation(Runnable operation) {
        asyncOperation(operation, 0L);
    }

    private void asyncOperation(final Runnable operation, long delayMillis) {
        synchronized (this.mLock) {
            int i = this.mTaskCount + 1;
            this.mTaskCount = i;
            if (i == 1) {
                this.mHandlerThread = new HandlerThread("CarLocationServiceThread");
                this.mHandlerThread.start();
                this.mHandler = new Handler(this.mHandlerThread.getLooper());
            }
        }
        this.mHandler.postDelayed(new Runnable() { // from class: com.android.car.-$$Lambda$CarLocationService$sBU6c0p7WvrwH6Bwp6eWVlugVmA
            @Override // java.lang.Runnable
            public final void run() {
                CarLocationService.this.lambda$asyncOperation$2$CarLocationService(operation);
            }
        }, delayMillis);
    }

    public /* synthetic */ void lambda$asyncOperation$2$CarLocationService(Runnable operation) {
        try {
            operation.run();
            synchronized (this.mLock) {
                int i = this.mTaskCount - 1;
                this.mTaskCount = i;
                if (i == 0) {
                    this.mHandler.getLooper().quit();
                    this.mHandler = null;
                    this.mHandlerThread = null;
                }
            }
        } catch (Throwable th) {
            synchronized (this.mLock) {
                int i2 = this.mTaskCount - 1;
                this.mTaskCount = i2;
                if (i2 == 0) {
                    this.mHandler.getLooper().quit();
                    this.mHandler = null;
                    this.mHandlerThread = null;
                }
                throw th;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void logd(String msg) {
        Slog.d(TAG, msg);
    }
}
