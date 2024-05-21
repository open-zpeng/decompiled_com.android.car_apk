package com.android.car;

import android.app.Service;
import android.content.Intent;
import android.hardware.automotive.vehicle.V2_0.IVehicle;
import android.os.Build;
import android.os.IBinder;
import android.os.IHwBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Slog;
import com.android.car.systeminterface.SystemInterface;
import com.android.car.utils.XpUtils;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.RingBufferIndices;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.NoSuchElementException;
/* loaded from: classes3.dex */
public class CarService extends Service {
    private static final int HW_BINDER_BUFFER_SIZE = 2080768;
    private static final boolean IS_USER_BUILD = "user".equals(Build.TYPE);
    private static final boolean RESTART_CAR_SERVICE_WHEN_VHAL_CRASH = false;
    private static final String SYSPROP_START_COUNT = "sys.car_service.start_count";
    public static final String TAG = "CarService";
    private static final long WAIT_FOR_VEHICLE_HAL_TIMEOUT_MS = 10000;
    private CanBusErrorNotifier mCanBusErrorNotifier;
    private ICarImpl mICarImpl;
    private IVehicle mVehicle;
    private String mVehicleInterfaceName;
    private final CrashTracker mVhalCrashTracker = new CrashTracker(50, 600000, new Runnable() { // from class: com.android.car.-$$Lambda$CarService$tboqskEr8RKbrUTbWkpr_EbXcvs
        @Override // java.lang.Runnable
        public final void run() {
            CarService.this.lambda$new$0$CarService();
        }
    });
    private final VehicleDeathRecipient mVehicleDeathRecipient = new VehicleDeathRecipient();

    public /* synthetic */ void lambda$new$0$CarService() {
        if (IS_USER_BUILD) {
            Slog.e(CarLog.TAG_SERVICE, "Vehicle HAL keeps crashing, notifying user...");
            this.mCanBusErrorNotifier.reportFailure(this);
            return;
        }
        throw new RuntimeException("Vehicle HAL crashed too many times in a given time frame");
    }

    @Override // android.app.Service
    public void onCreate() {
        Slog.i(CarLog.TAG_SERVICE, "Service onCreate");
        this.mCanBusErrorNotifier = new CanBusErrorNotifier(this);
        XpSharedMemoryService.initWithMmapSize(2080768L);
        this.mVehicle = getVehicle();
        IVehicle iVehicle = this.mVehicle;
        if (iVehicle == null) {
            throw new IllegalStateException("Vehicle HAL service is not available.");
        }
        try {
            this.mVehicleInterfaceName = iVehicle.interfaceDescriptor();
            Slog.i(CarLog.TAG_SERVICE, "Connected to " + this.mVehicleInterfaceName);
            int startCount = SystemProperties.getInt(SYSPROP_START_COUNT, 0) + 1;
            SystemProperties.set(SYSPROP_START_COUNT, String.valueOf(startCount));
            XpUtils.init(getApplication());
            this.mICarImpl = new ICarImpl(this, this.mVehicle, SystemInterface.Builder.defaultSystemInterface(this).build(), this.mCanBusErrorNotifier, this.mVehicleInterfaceName);
            this.mICarImpl.init();
            linkToDeath(this.mVehicle, this.mVehicleDeathRecipient);
            ServiceManager.addService("car_service", this.mICarImpl);
            ServiceManager.addService("car_stats", this.mICarImpl.getStatsService());
            SystemProperties.set("boot.car_service_created", "1");
            Slog.i(CarLog.TAG_SERVICE, "Service onCreate end");
            super.onCreate();
        } catch (RemoteException e) {
            throw new IllegalStateException("Unable to get Vehicle HAL interface descriptor", e);
        }
    }

    @Override // android.app.Service
    public void onDestroy() {
        Slog.i(CarLog.TAG_SERVICE, "Service onDestroy");
        this.mICarImpl.release();
        this.mCanBusErrorNotifier.removeFailureReport(this);
        IVehicle iVehicle = this.mVehicle;
        if (iVehicle != null) {
            try {
                iVehicle.unlinkToDeath(this.mVehicleDeathRecipient);
                this.mVehicle = null;
            } catch (RemoteException e) {
            }
        }
        super.onDestroy();
    }

    @Override // android.app.Service
    public int onStartCommand(Intent intent, int flags, int startId) {
        return 1;
    }

    @Override // android.app.Service
    public IBinder onBind(Intent intent) {
        return this.mICarImpl;
    }

    @Override // android.app.Service
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        this.mICarImpl.dump(fd, writer, args);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public IVehicle getVehicleWithTimeout(long waitMilliseconds) {
        IVehicle vehicle = getVehicle();
        long start = SystemClock.elapsedRealtime();
        while (vehicle == null && start + waitMilliseconds > SystemClock.elapsedRealtime()) {
            try {
                Thread.sleep(100L);
                vehicle = getVehicle();
            } catch (InterruptedException e) {
                throw new RuntimeException("Sleep was interrupted", e);
            }
        }
        if (vehicle != null) {
            this.mCanBusErrorNotifier.removeFailureReport(this);
        }
        return vehicle;
    }

    private static IVehicle getVehicle() {
        try {
            return IVehicle.getService();
        } catch (RemoteException e) {
            Slog.e(CarLog.TAG_SERVICE, "Failed to get IVehicle service: " + e.getMessage());
            return null;
        } catch (NoSuchElementException e2) {
            Slog.e(CarLog.TAG_SERVICE, "IVehicle service not registered yet");
            return null;
        }
    }

    /* loaded from: classes3.dex */
    private class VehicleDeathRecipient implements IHwBinder.DeathRecipient {
        private int deathCount;

        private VehicleDeathRecipient() {
            this.deathCount = 0;
        }

        public void serviceDied(long cookie) {
            Slog.e(CarLog.TAG_SERVICE, "***Vehicle HAL died.***");
            try {
                CarService.this.mVehicle.unlinkToDeath(this);
            } catch (RemoteException e) {
                Slog.e(CarLog.TAG_SERVICE, "Failed to unlinkToDeath" + e.getMessage());
            }
            CarService.this.mVehicle = null;
            CarService.this.mVhalCrashTracker.crashDetected();
            Slog.i(CarLog.TAG_SERVICE, "Trying to reconnect to Vehicle HAL: " + CarService.this.mVehicleInterfaceName);
            CarService carService = CarService.this;
            carService.mVehicle = carService.getVehicleWithTimeout(CarService.WAIT_FOR_VEHICLE_HAL_TIMEOUT_MS);
            if (CarService.this.mVehicle != null) {
                CarService.linkToDeath(CarService.this.mVehicle, this);
                Slog.i(CarLog.TAG_SERVICE, "Notifying car service Vehicle HAL reconnected...");
                CarService.this.mICarImpl.vehicleHalReconnected(CarService.this.mVehicle);
                return;
            }
            throw new IllegalStateException("Failed to reconnect to Vehicle HAL");
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void linkToDeath(IVehicle vehicle, IHwBinder.DeathRecipient recipient) {
        try {
            vehicle.linkToDeath(recipient, 0L);
        } catch (RemoteException e) {
            throw new IllegalStateException("Failed to linkToDeath Vehicle HAL");
        }
    }

    @VisibleForTesting
    /* loaded from: classes3.dex */
    static class CrashTracker {
        private final Runnable mCallback;
        private final long[] mCrashTimestamps;
        private final RingBufferIndices mCrashTimestampsIndices;
        private final int mMaxCrashCountLimit;
        private final int mSlidingWindowMillis;

        CrashTracker(int maxCrashCountLimit, int slidingWindowMillis, Runnable callback) {
            this.mMaxCrashCountLimit = maxCrashCountLimit;
            this.mSlidingWindowMillis = slidingWindowMillis;
            this.mCallback = callback;
            this.mCrashTimestamps = new long[maxCrashCountLimit];
            this.mCrashTimestampsIndices = new RingBufferIndices(this.mMaxCrashCountLimit);
        }

        void crashDetected() {
            long lastCrash = SystemClock.elapsedRealtime();
            this.mCrashTimestamps[this.mCrashTimestampsIndices.add()] = lastCrash;
            if (this.mCrashTimestampsIndices.size() == this.mMaxCrashCountLimit) {
                long firstCrash = this.mCrashTimestamps[this.mCrashTimestampsIndices.indexOf(0)];
                if (lastCrash - firstCrash < this.mSlidingWindowMillis) {
                    this.mCallback.run();
                }
            }
        }
    }
}
