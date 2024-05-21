package com.android.car;

import android.car.drivingstate.CarDrivingStateEvent;
import android.car.drivingstate.ICarDrivingState;
import android.car.drivingstate.ICarDrivingStateChangeListener;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.ICarPropertyEventListener;
import android.content.Context;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Slog;
import com.android.car.Manifest;
import com.android.car.Utils;
import com.android.internal.annotations.VisibleForTesting;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
/* loaded from: classes3.dex */
public class CarDrivingStateService extends ICarDrivingState.Stub implements CarServiceBase {
    private static final boolean DBG = false;
    private static final int MAX_TRANSITION_LOG_SIZE = 20;
    private static final int NOT_RECEIVED = -1;
    private static final int PROPERTY_UPDATE_RATE = 5;
    private static final int[] REQUIRED_PROPERTIES = {VehicleProperty.PERF_VEHICLE_SPEED, VehicleProperty.GEAR_SELECTION, VehicleProperty.PARKING_BRAKE_ON};
    private static final String TAG = "CarDrivingState";
    private final Handler mClientDispatchHandler;
    private final Context mContext;
    private int mLastGear;
    private boolean mLastParkingBrakeState;
    private float mLastSpeed;
    private CarPropertyService mPropertyService;
    private List<Integer> mSupportedGears;
    private final List<DrivingStateClient> mDrivingStateClients = new CopyOnWriteArrayList();
    private final LinkedList<Utils.TransitionLog> mTransitionLogs = new LinkedList<>();
    private long mLastGearTimestamp = -1;
    private long mLastSpeedTimestamp = -1;
    private long mLastParkingBrakeTimestamp = -1;
    private final ICarPropertyEventListener mICarPropertyEventListener = new ICarPropertyEventListener.Stub() { // from class: com.android.car.CarDrivingStateService.1
        public void onEvent(List<CarPropertyEvent> events) throws RemoteException {
            for (CarPropertyEvent event : events) {
                CarDrivingStateService.this.handlePropertyEvent(event);
            }
        }
    };
    private CarDrivingStateEvent mCurrentDrivingState = createDrivingStateEvent(-1);
    private final HandlerThread mClientDispatchThread = new HandlerThread("ClientDispatchThread");

    public CarDrivingStateService(Context context, CarPropertyService propertyService) {
        this.mContext = context;
        this.mPropertyService = propertyService;
        this.mClientDispatchThread.start();
        this.mClientDispatchHandler = new Handler(this.mClientDispatchThread.getLooper());
    }

    @Override // com.android.car.CarServiceBase
    public synchronized void init() {
        if (!checkPropertySupport()) {
            Slog.e(TAG, "init failure.  Driving state will always be fully restrictive");
            return;
        }
        subscribeToProperties();
        this.mCurrentDrivingState = createDrivingStateEvent(inferDrivingStateLocked());
        addTransitionLog("CarDrivingState Boot", -1, this.mCurrentDrivingState.eventValue, this.mCurrentDrivingState.timeStamp);
    }

    @Override // com.android.car.CarServiceBase
    public synchronized void release() {
        int[] iArr;
        for (int property : REQUIRED_PROPERTIES) {
            this.mPropertyService.unregisterListener(property, this.mICarPropertyEventListener);
        }
        for (DrivingStateClient client : this.mDrivingStateClients) {
            client.listenerBinder.unlinkToDeath(client, 0);
        }
        this.mDrivingStateClients.clear();
        this.mCurrentDrivingState = createDrivingStateEvent(-1);
    }

    private synchronized boolean checkPropertySupport() {
        int[] iArr;
        List<CarPropertyConfig> configs = this.mPropertyService.getPropertyList();
        for (int propertyId : REQUIRED_PROPERTIES) {
            boolean found = false;
            Iterator<CarPropertyConfig> it = configs.iterator();
            while (true) {
                if (!it.hasNext()) {
                    break;
                }
                CarPropertyConfig config = it.next();
                if (config.getPropertyId() == propertyId) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                Slog.e(TAG, "Required property not supported: " + propertyId);
                return false;
            }
        }
        return true;
    }

    private synchronized void subscribeToProperties() {
        int[] iArr;
        for (int propertyId : REQUIRED_PROPERTIES) {
            this.mPropertyService.registerListener(propertyId, 5.0f, this.mICarPropertyEventListener);
        }
    }

    public synchronized void registerDrivingStateChangeListener(ICarDrivingStateChangeListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener is null");
        }
        if (findDrivingStateClient(listener) == null) {
            DrivingStateClient client = new DrivingStateClient(listener);
            try {
                listener.asBinder().linkToDeath(client, 0);
                this.mDrivingStateClients.add(client);
            } catch (RemoteException e) {
                Slog.e(TAG, "Cannot link death recipient to binder " + e);
            }
        }
    }

    private DrivingStateClient findDrivingStateClient(ICarDrivingStateChangeListener listener) {
        IBinder binder = listener.asBinder();
        for (DrivingStateClient client : this.mDrivingStateClients) {
            if (client.isHoldingBinder(binder)) {
                return client;
            }
        }
        return null;
    }

    public synchronized void unregisterDrivingStateChangeListener(ICarDrivingStateChangeListener listener) {
        if (listener == null) {
            Slog.e(TAG, "unregisterDrivingStateChangeListener(): listener null");
            throw new IllegalArgumentException("Listener is null");
        }
        DrivingStateClient client = findDrivingStateClient(listener);
        if (client == null) {
            Slog.e(TAG, "unregisterDrivingStateChangeListener(): listener was not previously registered");
            return;
        }
        listener.asBinder().unlinkToDeath(client, 0);
        this.mDrivingStateClients.remove(client);
    }

    public synchronized CarDrivingStateEvent getCurrentDrivingState() {
        return this.mCurrentDrivingState;
    }

    public void injectDrivingState(CarDrivingStateEvent event) {
        ICarImpl.assertPermission(this.mContext, Manifest.permission.CONTROL_APP_BLOCKING);
        for (DrivingStateClient client : this.mDrivingStateClients) {
            client.dispatchEventToClients(event);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class DrivingStateClient implements IBinder.DeathRecipient {
        private final ICarDrivingStateChangeListener listener;
        private final IBinder listenerBinder;

        public DrivingStateClient(ICarDrivingStateChangeListener l) {
            this.listener = l;
            this.listenerBinder = l.asBinder();
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            this.listenerBinder.unlinkToDeath(this, 0);
            CarDrivingStateService.this.mDrivingStateClients.remove(this);
        }

        public boolean isHoldingBinder(IBinder binder) {
            return this.listenerBinder == binder;
        }

        public void dispatchEventToClients(CarDrivingStateEvent event) {
            if (event == null) {
                return;
            }
            try {
                this.listener.onDrivingStateChanged(event);
            } catch (RemoteException e) {
            }
        }
    }

    @Override // com.android.car.CarServiceBase
    public void dump(PrintWriter writer) {
        writer.println("*CarDrivingStateService*");
        writer.println("Driving state change log:");
        Iterator<Utils.TransitionLog> it = this.mTransitionLogs.iterator();
        while (it.hasNext()) {
            Utils.TransitionLog tLog = it.next();
            writer.println(tLog);
        }
        writer.println("Current Driving State: " + this.mCurrentDrivingState.eventValue);
        if (this.mSupportedGears != null) {
            writer.println("Supported gears:");
            for (Integer gear : this.mSupportedGears) {
                writer.print("Gear:" + gear);
            }
        }
    }

    @VisibleForTesting
    synchronized void handlePropertyEvent(CarPropertyEvent event) {
        if (event.getEventType() != 0) {
            return;
        }
        CarPropertyValue value = event.getCarPropertyValue();
        int propId = value.getPropertyId();
        long curTimestamp = value.getTimestamp();
        if (propId == 287310850) {
            boolean curParkingBrake = ((Boolean) value.getValue()).booleanValue();
            if (curTimestamp > this.mLastParkingBrakeTimestamp) {
                this.mLastParkingBrakeTimestamp = curTimestamp;
                this.mLastParkingBrakeState = curParkingBrake;
            }
        } else if (propId == 289408000) {
            if (this.mSupportedGears == null) {
                this.mSupportedGears = getSupportedGears();
            }
            ((Integer) value.getValue()).intValue();
            if (curTimestamp > this.mLastGearTimestamp) {
                this.mLastGearTimestamp = curTimestamp;
                this.mLastGear = ((Integer) value.getValue()).intValue();
            }
        } else if (propId == 291504647) {
            float curSpeed = ((Float) value.getValue()).floatValue();
            if (curTimestamp > this.mLastSpeedTimestamp) {
                this.mLastSpeedTimestamp = curTimestamp;
                this.mLastSpeed = curSpeed;
            }
        } else {
            Slog.e(TAG, "Received property event for unhandled propId=" + propId);
        }
        int drivingState = inferDrivingStateLocked();
        if (drivingState != this.mCurrentDrivingState.eventValue) {
            addTransitionLog(TAG, this.mCurrentDrivingState.eventValue, drivingState, System.currentTimeMillis());
            this.mCurrentDrivingState = createDrivingStateEvent(drivingState);
            final CarDrivingStateEvent currentDrivingStateEvent = this.mCurrentDrivingState;
            this.mClientDispatchHandler.post(new Runnable() { // from class: com.android.car.-$$Lambda$CarDrivingStateService$lXO-TU_65qLxIAEQ4rm40bfCis8
                @Override // java.lang.Runnable
                public final void run() {
                    CarDrivingStateService.this.lambda$handlePropertyEvent$0$CarDrivingStateService(currentDrivingStateEvent);
                }
            });
        }
    }

    public /* synthetic */ void lambda$handlePropertyEvent$0$CarDrivingStateService(CarDrivingStateEvent currentDrivingStateEvent) {
        for (DrivingStateClient client : this.mDrivingStateClients) {
            client.dispatchEventToClients(currentDrivingStateEvent);
        }
    }

    private List<Integer> getSupportedGears() {
        List<CarPropertyConfig> properyList = this.mPropertyService.getPropertyList();
        for (CarPropertyConfig p : properyList) {
            if (p.getPropertyId() == 289408000) {
                return p.getConfigArray();
            }
        }
        return null;
    }

    private void addTransitionLog(String name, int from, int to, long timestamp) {
        if (this.mTransitionLogs.size() >= 20) {
            this.mTransitionLogs.remove();
        }
        Utils.TransitionLog tLog = new Utils.TransitionLog(name, Integer.valueOf(from), Integer.valueOf(to), timestamp);
        this.mTransitionLogs.add(tLog);
    }

    private int inferDrivingStateLocked() {
        updateVehiclePropertiesIfNeeded();
        if (isVehicleKnownToBeParked()) {
            return 0;
        }
        if (this.mLastSpeedTimestamp != -1) {
            float f = this.mLastSpeed;
            if (f < 0.0f) {
                return -1;
            }
            if (f == 0.0f) {
                return 1;
            }
            return 2;
        }
        return -1;
    }

    private boolean isVehicleKnownToBeParked() {
        if (this.mLastGearTimestamp != -1 && this.mLastGear == 4) {
            return true;
        }
        if (this.mLastParkingBrakeTimestamp != -1 && isCarManualTransmissionType()) {
            return this.mLastParkingBrakeState;
        }
        return false;
    }

    private boolean isCarManualTransmissionType() {
        List<Integer> list = this.mSupportedGears;
        if (list != null && !list.isEmpty() && !this.mSupportedGears.contains(4)) {
            return true;
        }
        return false;
    }

    private void updateVehiclePropertiesIfNeeded() {
        CarPropertyValue propertyValue;
        CarPropertyValue propertyValue2;
        CarPropertyValue propertyValue3;
        if (this.mLastGearTimestamp == -1 && (propertyValue3 = this.mPropertyService.getProperty(VehicleProperty.GEAR_SELECTION, 0)) != null) {
            this.mLastGear = ((Integer) propertyValue3.getValue()).intValue();
            this.mLastGearTimestamp = propertyValue3.getTimestamp();
        }
        if (this.mLastParkingBrakeTimestamp == -1 && (propertyValue2 = this.mPropertyService.getProperty(VehicleProperty.PARKING_BRAKE_ON, 0)) != null) {
            this.mLastParkingBrakeState = ((Boolean) propertyValue2.getValue()).booleanValue();
            this.mLastParkingBrakeTimestamp = propertyValue2.getTimestamp();
        }
        if (this.mLastSpeedTimestamp == -1 && (propertyValue = this.mPropertyService.getProperty(VehicleProperty.PERF_VEHICLE_SPEED, 0)) != null) {
            this.mLastSpeed = ((Float) propertyValue.getValue()).floatValue();
            this.mLastSpeedTimestamp = propertyValue.getTimestamp();
        }
    }

    private static CarDrivingStateEvent createDrivingStateEvent(int eventValue) {
        return new CarDrivingStateEvent(eventValue, SystemClock.elapsedRealtimeNanos());
    }
}
