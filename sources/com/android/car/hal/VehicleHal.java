package com.android.car.hal;

import android.car.XpDebugLog;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.ICarPropertyEventListener;
import android.content.Context;
import android.hardware.automotive.vehicle.V2_0.IVehicle;
import android.hardware.automotive.vehicle.V2_0.IVehicleCallback;
import android.hardware.automotive.vehicle.V2_0.SubscribeOptions;
import android.hardware.automotive.vehicle.V2_0.VehicleAreaConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.AndroidRuntimeException;
import android.util.ArraySet;
import android.util.Slog;
import android.util.SparseArray;
import android.util.TimeUtils;
import com.android.car.CarLog;
import com.android.car.CarServiceUtils;
import com.android.car.DebugService;
import com.android.internal.annotations.VisibleForTesting;
import com.google.android.collect.Lists;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
/* loaded from: classes3.dex */
public class VehicleHal extends IVehicleCallback.Stub {
    private static final String DATA_DELIMITER = ",";
    private static final boolean DBG = false;
    private static final int INVALID_VALUE = -10;
    private static final int NO_AREA = -1;
    private static final int XPU_REMOTE = 1;
    private static final int XPU_REMOTE_NONE = 0;
    private final HashMap<Integer, VehiclePropConfig> mAllProperties;
    private final ArrayList<HalServiceBase> mAllServices;
    Context mContext;
    Debug mDebug;
    boolean mDebugIsRunning;
    private DiagnosticHalService mDiagnosticHal;
    private final ArrayList<HalServiceBase> mDumpServices;
    private final HashMap<Integer, VehiclePropertyEventInfo> mEventLog;
    private volatile HalClient mHalClient;
    private final ArraySet<HalEvent> mHalEventToDispatch;
    private final HashMap<PropertyHalService, ArrayList<ICarPropertyEventListener>> mHalServiceToClientListener;
    private final HashMap<PropertyHalService, ArrayList<Integer>> mHalServiceToPid;
    private final HandlerThread mHandlerThread;
    private final InputHalService mInputHal;
    private final HashMap<Integer, ArrayList<ICarPropertyEventListener>> mPidToClientListener;
    private final HashMap<Integer, ArrayList<PropertyHalService>> mPidToHalService;
    private final HashMap<Integer, HalEventHandler> mPidToHandler;
    private final HashMap<Integer, HandlerThread> mPidToThread;
    private final PowerHalService mPowerHal;
    private final PropertyHalService mPropertyHal;
    private final SparseArray<HalServiceBase> mPropertyHandlers;
    private final SparseArray<Set<HalServiceBase>> mPropertySetHandlers;
    private final ArraySet<HalServiceBase> mServicesToDispatch;
    private final HashMap<Integer, SubscribeOptions> mSubscribedProperties;
    private final VmsHalService mVmsHal;
    private volatile int mXpuFlag;

    /* loaded from: classes3.dex */
    public class DebugHandler extends Handler {
        public DebugHandler(Looper looper) {
            super(looper, null, true);
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            if (msg.what == 1 && VehicleHal.this.mDebug != null) {
                VehicleHal.this.mDebug.onEvent((HashMap) msg.obj);
            }
        }
    }

    /* loaded from: classes3.dex */
    public class Debug {
        Handler mDebugHandler;
        DebugService mDebugService;
        HandlerThread mDebugThread;

        public Debug(Context context) {
            this.mDebugService = new DebugService(context);
        }

        void onEvent(HashMap<Integer, VehiclePropertyEventInfo> events) {
            this.mDebugService.onEvent(events);
        }

        void sendMsg(HashMap<Integer, VehiclePropertyEventInfo> events) {
            Handler handler = this.mDebugHandler;
            if (handler == null) {
                return;
            }
            handler.sendMessage(Message.obtain(handler, 1, events));
        }

        void start() {
            this.mDebugThread = new HandlerThread("VEHICLE-Debug");
            this.mDebugThread.start();
            this.mDebugHandler = new DebugHandler(this.mDebugThread.getLooper());
            this.mDebugService.init();
        }

        void stop() {
            this.mDebugService.release();
            this.mDebugThread.quit();
            this.mDebugHandler = null;
            this.mDebugThread = null;
        }
    }

    public VehicleHal(Context context, IVehicle vehicle) {
        this.mDiagnosticHal = null;
        this.mXpuFlag = INVALID_VALUE;
        this.mPropertyHandlers = new SparseArray<>();
        this.mPropertySetHandlers = new SparseArray<>();
        this.mAllServices = new ArrayList<>();
        this.mDumpServices = new ArrayList<>();
        this.mSubscribedProperties = new HashMap<>();
        this.mAllProperties = new HashMap<>();
        this.mEventLog = new HashMap<>();
        this.mPidToHalService = new HashMap<>();
        this.mHalServiceToClientListener = new HashMap<>();
        this.mHalServiceToPid = new HashMap<>();
        this.mPidToClientListener = new HashMap<>();
        this.mPidToHandler = new HashMap<>();
        this.mPidToThread = new HashMap<>();
        this.mDebugIsRunning = false;
        this.mServicesToDispatch = new ArraySet<>();
        this.mHalEventToDispatch = new ArraySet<>();
        this.mHandlerThread = new HandlerThread("VEHICLE-HAL");
        this.mHandlerThread.start();
        this.mPowerHal = new PowerHalService(this);
        this.mPropertyHal = new PropertyHalService(this);
        this.mInputHal = new InputHalService(this);
        this.mVmsHal = new VmsHalService(context, this);
        this.mDiagnosticHal = new DiagnosticHalService(this);
        this.mAllServices.addAll(Arrays.asList(this.mPowerHal, this.mInputHal, this.mPropertyHal, this.mDiagnosticHal, this.mVmsHal));
        this.mDumpServices.addAll(Arrays.asList(this.mPowerHal, this.mPropertyHal));
        this.mHalClient = new HalClient(vehicle, this.mHandlerThread.getLooper(), this);
        this.mHalClient.start();
    }

    public int getXpuFlag() {
        return this.mXpuFlag;
    }

    private static String xpuRemoteFlagName(int state) {
        String baseName;
        if (state == 0) {
            baseName = "NOT REMOTE";
        } else if (state == 1) {
            baseName = "REMOTE";
        } else {
            baseName = "<unknown>";
        }
        return baseName + "(" + state + ")";
    }

    public void setXpuFlag(int xpuFlag) {
        this.mXpuFlag = xpuFlag;
        Slog.i(CarLog.TAG_POWER, "Received XPU_FLAG=" + xpuRemoteFlagName(this.mXpuFlag));
    }

    @VisibleForTesting
    public VehicleHal(PowerHalService powerHal, DiagnosticHalService diagnosticHal, HalClient halClient, PropertyHalService propertyHal) {
        this.mDiagnosticHal = null;
        this.mXpuFlag = INVALID_VALUE;
        this.mPropertyHandlers = new SparseArray<>();
        this.mPropertySetHandlers = new SparseArray<>();
        this.mAllServices = new ArrayList<>();
        this.mDumpServices = new ArrayList<>();
        this.mSubscribedProperties = new HashMap<>();
        this.mAllProperties = new HashMap<>();
        this.mEventLog = new HashMap<>();
        this.mPidToHalService = new HashMap<>();
        this.mHalServiceToClientListener = new HashMap<>();
        this.mHalServiceToPid = new HashMap<>();
        this.mPidToClientListener = new HashMap<>();
        this.mPidToHandler = new HashMap<>();
        this.mPidToThread = new HashMap<>();
        this.mDebugIsRunning = false;
        this.mServicesToDispatch = new ArraySet<>();
        this.mHalEventToDispatch = new ArraySet<>();
        this.mHandlerThread = null;
        this.mPowerHal = powerHal;
        this.mPropertyHal = propertyHal;
        this.mDiagnosticHal = diagnosticHal;
        this.mInputHal = null;
        this.mVmsHal = null;
        this.mHalClient = halClient;
        this.mHalClient.start();
    }

    public void setContext(Context context) {
        this.mContext = context;
        if (this.mHalClient != null) {
            this.mHalClient.setContext(context);
        }
    }

    public void vehicleHalReconnected(IVehicle vehicle) {
        synchronized (this) {
            this.mHalClient.stop();
            this.mHalClient = new HalClient(vehicle, this.mHandlerThread.getLooper(), this);
            this.mHalClient.start();
            SubscribeOptions[] options = (SubscribeOptions[]) this.mSubscribedProperties.values().toArray(new SubscribeOptions[0]);
            try {
                this.mHalClient.subscribe(options);
            } catch (RemoteException e) {
                Slog.e(CarLog.TAG_HAL, "Failed to subscribe when re-connecting VHAL");
                throw new RuntimeException("Failed to subscribe: " + Arrays.asList(options), e);
            }
        }
    }

    /* JADX WARN: Multi-variable type inference failed */
    public void init() {
        try {
            Set<VehiclePropConfig> properties = new HashSet<>(this.mHalClient.getAllPropConfigs());
            synchronized (this) {
                for (VehiclePropConfig p : properties) {
                    this.mAllProperties.put(Integer.valueOf(p.prop), p);
                }
            }
            List<Integer> multiplePropertyIds = this.mPropertyHal.getMultiplePropertyIds();
            Iterator<HalServiceBase> it = this.mAllServices.iterator();
            while (it.hasNext()) {
                HalServiceBase service = it.next();
                Collection<VehiclePropConfig> taken = service.takeSupportedProperties(properties);
                if (taken != null) {
                    if (CarLog.isGetLogEnable()) {
                        Slog.i(CarLog.TAG_HAL, "HalService " + service + " take properties " + taken.size());
                    }
                    synchronized (this) {
                        Set<VehiclePropConfig> vehiclePropConfigs = new ArraySet<>();
                        for (VehiclePropConfig p2 : taken) {
                            if (!multiplePropertyIds.contains(Integer.valueOf(p2.prop))) {
                                this.mPropertyHandlers.append(p2.prop, service);
                            } else {
                                Set<HalServiceBase> serviceBases = this.mPropertySetHandlers.get(p2.prop);
                                if (serviceBases == null) {
                                    serviceBases = new ArraySet();
                                    serviceBases.add(service);
                                    this.mPropertySetHandlers.put(p2.prop, serviceBases);
                                }
                                serviceBases.add(service);
                                vehiclePropConfigs.add(p2);
                            }
                        }
                        if (!vehiclePropConfigs.isEmpty()) {
                            taken.removeAll(vehiclePropConfigs);
                        }
                    }
                    properties.removeAll(taken);
                    service.init();
                }
            }
        } catch (RemoteException e) {
            throw new RuntimeException("Unable to retrieve vehicle property configuration", e);
        }
    }

    public void release() {
        for (int i = this.mAllServices.size() - 1; i >= 0; i--) {
            this.mAllServices.get(i).release();
        }
        synchronized (this) {
            for (Integer num : this.mSubscribedProperties.keySet()) {
                int p = num.intValue();
                try {
                    this.mHalClient.unsubscribe(p);
                } catch (RemoteException e) {
                    Slog.w(CarLog.TAG_HAL, "Failed to unsubscribe", e);
                }
            }
            this.mSubscribedProperties.clear();
            this.mAllProperties.clear();
        }
        this.mDebug.stop();
    }

    public DiagnosticHalService getDiagnosticHal() {
        return this.mDiagnosticHal;
    }

    public PowerHalService getPowerHal() {
        return this.mPowerHal;
    }

    public PropertyHalService getPropertyHal() {
        return this.mPropertyHal;
    }

    public InputHalService getInputHal() {
        return this.mInputHal;
    }

    public VmsHalService getVmsHal() {
        return this.mVmsHal;
    }

    private void assertServiceOwnerLocked(HalServiceBase service, int property) {
        if (service != this.mPropertyHandlers.get(property)) {
            Set<HalServiceBase> halServiceBases = this.mPropertySetHandlers.get(property);
            if (halServiceBases == null || !halServiceBases.contains(service)) {
                throw new IllegalArgumentException("Property 0x" + Integer.toHexString(property) + " is not owned by service: " + service);
            }
        }
    }

    public void subscribeProperty(HalServiceBase service, int property) throws IllegalArgumentException {
        subscribeProperty(service, property, 0.0f, 1);
    }

    public void subscribeProperty(HalServiceBase service, int property, float sampleRateHz) throws IllegalArgumentException {
        subscribeProperty(service, property, sampleRateHz, 1);
    }

    public void subscribeProperty(HalServiceBase service, int property, float samplingRateHz, int flags) throws IllegalArgumentException {
        VehiclePropConfig config;
        synchronized (this) {
            config = this.mAllProperties.get(Integer.valueOf(property));
        }
        if (config == null) {
            throw new IllegalArgumentException("subscribe error: config is null for property 0x" + Integer.toHexString(property));
        } else if (isPropertySubscribable(config)) {
            SubscribeOptions opts = new SubscribeOptions();
            opts.propId = property;
            opts.sampleRate = samplingRateHz;
            opts.flags = flags;
            synchronized (this) {
                assertServiceOwnerLocked(service, property);
                this.mSubscribedProperties.put(Integer.valueOf(property), opts);
            }
            try {
                this.mHalClient.subscribe(opts);
            } catch (RemoteException e) {
                Slog.e(CarLog.TAG_HAL, "Failed to subscribe to property: 0x" + Integer.toHexString(property), e);
                throw new AndroidRuntimeException(e);
            }
        } else {
            Slog.e(CarLog.TAG_HAL, "Cannot subscribe to property: " + Integer.toHexString(property));
        }
    }

    public void unsubscribeProperty(HalServiceBase service, int property) {
        VehiclePropConfig config;
        synchronized (this) {
            config = this.mAllProperties.get(Integer.valueOf(property));
        }
        if (config == null) {
            Slog.e(CarLog.TAG_HAL, "unsubscribeProperty: property " + property + " does not exist");
        } else if (isPropertySubscribable(config)) {
            synchronized (this) {
                assertServiceOwnerLocked(service, property);
                this.mSubscribedProperties.remove(Integer.valueOf(property));
            }
            try {
                this.mHalClient.unsubscribe(property);
            } catch (RemoteException e) {
                Slog.e(CarLog.TAG_SERVICE, "Failed to unsubscribe from property: 0x" + Integer.toHexString(property), e);
            }
        } else {
            Slog.e(CarLog.TAG_HAL, "Cannot unsubscribe property: " + Integer.toHexString(property));
        }
    }

    public boolean isPropertySupported(int propertyId) {
        return this.mAllProperties.containsKey(Integer.valueOf(propertyId));
    }

    public Collection<VehiclePropConfig> getAllPropConfigs() {
        return this.mAllProperties.values();
    }

    public VehiclePropValue get(int propertyId) throws PropertyTimeoutException {
        return get(propertyId, -1, true);
    }

    public VehiclePropValue get(int propertyId, boolean showNoDataLog) throws PropertyTimeoutException {
        return get(propertyId, -1, showNoDataLog);
    }

    public VehiclePropValue get(int propertyId, int areaId) throws PropertyTimeoutException {
        return get(propertyId, areaId, true);
    }

    public VehiclePropValue get(int propertyId, int areaId, boolean showNoDataLog) throws PropertyTimeoutException {
        VehiclePropValue propValue = new VehiclePropValue();
        propValue.prop = propertyId;
        propValue.areaId = areaId;
        return this.mHalClient.getValue(propValue, showNoDataLog);
    }

    public <T> T get(Class clazz, int propertyId) throws PropertyTimeoutException {
        return (T) get(clazz, createPropValue(propertyId, -1));
    }

    public <T> T get(Class clazz, int propertyId, int areaId) throws PropertyTimeoutException {
        return (T) get(clazz, createPropValue(propertyId, areaId));
    }

    public <T> T get(Class clazz, VehiclePropValue requestedPropValue) throws PropertyTimeoutException {
        VehiclePropValue propValue = this.mHalClient.getValue(requestedPropValue);
        if (clazz == Integer.class || clazz == Integer.TYPE) {
            return (T) propValue.value.int32Values.get(0);
        }
        if (clazz == Boolean.class || clazz == Boolean.TYPE) {
            return (T) Boolean.valueOf(propValue.value.int32Values.get(0).intValue() == 1);
        } else if (clazz == Float.class || clazz == Float.TYPE) {
            return (T) propValue.value.floatValues.get(0);
        } else {
            if (clazz == Integer[].class) {
                Integer[] intArray = new Integer[propValue.value.int32Values.size()];
                return (T) propValue.value.int32Values.toArray(intArray);
            } else if (clazz == Float[].class) {
                Float[] floatArray = new Float[propValue.value.floatValues.size()];
                return (T) propValue.value.floatValues.toArray(floatArray);
            } else if (clazz == int[].class) {
                return (T) CarServiceUtils.toIntArray(propValue.value.int32Values);
            } else {
                if (clazz == float[].class) {
                    return (T) CarServiceUtils.toFloatArray(propValue.value.floatValues);
                }
                if (clazz == byte[].class) {
                    return (T) CarServiceUtils.toByteArray(propValue.value.bytes);
                }
                if (clazz == String.class) {
                    return (T) propValue.value.stringValue;
                }
                throw new IllegalArgumentException("Unexpected type: " + clazz);
            }
        }
    }

    public VehiclePropValue get(VehiclePropValue requestedPropValue) throws PropertyTimeoutException {
        return this.mHalClient.getValue(requestedPropValue);
    }

    public float getSampleRate(int propId) {
        SubscribeOptions opts = this.mSubscribedProperties.get(Integer.valueOf(propId));
        if (opts == null) {
            return -2.14748365E9f;
        }
        return opts.sampleRate;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void set(VehiclePropValue propValue) throws PropertyTimeoutException {
        this.mHalClient.setValue(propValue);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void setMultiProperties(ArrayList<VehiclePropValue> propValues) throws PropertyTimeoutException {
        this.mHalClient.setMultiValues(propValues);
    }

    VehiclePropValueSetter set(int propId) {
        return new VehiclePropValueSetter(this.mHalClient, propId, -1);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public VehiclePropValueSetter set(int propId, int areaId) {
        return new VehiclePropValueSetter(this.mHalClient, propId, areaId);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static boolean isPropertySubscribable(VehiclePropConfig config) {
        return ((config.access & 1) == 0 || config.changeMode == 0) ? false : true;
    }

    static void dumpProperties(PrintWriter writer, Collection<VehiclePropConfig> configs) {
        for (VehiclePropConfig config : configs) {
            writer.println(String.format("property 0x%x", Integer.valueOf(config.prop)));
        }
    }

    @Override // android.hardware.automotive.vehicle.V2_0.IVehicleCallback
    public void onPropertyEvent(ArrayList<VehiclePropValue> propValues) {
        Debug debug;
        synchronized (this) {
            Iterator<HalServiceBase> it = this.mServicesToDispatch.iterator();
            while (it.hasNext()) {
                it.next().getDispatchList().clear();
            }
            this.mServicesToDispatch.clear();
            Iterator<VehiclePropValue> it2 = propValues.iterator();
            while (it2.hasNext()) {
                VehiclePropValue v = it2.next();
                int prop = v.prop;
                HalServiceBase service = this.mPropertyHandlers.get(prop);
                if (service == null) {
                    Set<HalServiceBase> serviceBases = this.mPropertySetHandlers.get(prop);
                    if (serviceBases != null && !serviceBases.isEmpty()) {
                        for (HalServiceBase serviceBase : serviceBases) {
                            this.mServicesToDispatch.add(serviceBase);
                            serviceBase.getDispatchList().add(v);
                        }
                    }
                    Slog.e(CarLog.TAG_HAL, "HalService not found for prop: 0x" + Integer.toHexString(prop));
                } else {
                    this.mServicesToDispatch.add(service);
                    service.getDispatchList().add(v);
                }
                VehiclePropertyEventInfo info = this.mEventLog.get(Integer.valueOf(prop));
                if (info == null) {
                    this.mEventLog.put(Integer.valueOf(prop), new VehiclePropertyEventInfo(v));
                } else {
                    info.addNewEvent(v);
                }
            }
        }
        if (this.mDebugIsRunning && (debug = this.mDebug) != null) {
            debug.sendMsg(this.mEventLog);
        }
        Iterator<HalServiceBase> it3 = this.mServicesToDispatch.iterator();
        while (it3.hasNext()) {
            HalServiceBase s = it3.next();
            s.handleHalEvents(s.getDispatchList());
            s.getDispatchList().clear();
        }
        this.mServicesToDispatch.clear();
    }

    /* loaded from: classes3.dex */
    public class HalEvent {
        private HalEventHandler mHandler;
        private ArrayList<ICarPropertyEventListener> mListenerSet;
        private int mPid;
        private List<CarPropertyEvent> mPropValues = new ArrayList();

        HalEvent(int pid, ArrayList<ICarPropertyEventListener> listenerSet, HalEventHandler handler) {
            this.mPid = pid;
            this.mListenerSet = listenerSet;
            this.mHandler = handler;
        }

        public void addProp(CarPropertyEvent v) {
            this.mPropValues.add(v);
        }

        public void handleHalEvent() {
            HalEventHandler halEventHandler = this.mHandler;
            halEventHandler.sendMessage(Message.obtain(halEventHandler, 2, this));
        }

        public void dispatchEvent() {
            Iterator<ICarPropertyEventListener> it = this.mListenerSet.iterator();
            while (it.hasNext()) {
                ICarPropertyEventListener l = it.next();
                try {
                    l.onEvent(this.mPropValues);
                    for (CarPropertyEvent event : this.mPropValues) {
                        Slog.d(CarLog.TAG_HAL, "event=" + event.toString());
                    }
                } catch (RemoteException ex) {
                    Slog.e(CarLog.TAG_HAL, "dispatchEvent calling failed: " + ex.getMessage() + " pid=" + this.mPid + " mPropValues=" + this.mPropValues);
                }
            }
        }
    }

    public void onPropertyEventXp(ArrayList<VehiclePropValue> propValues) {
        synchronized (this) {
            Iterator<VehiclePropValue> it = propValues.iterator();
            while (it.hasNext()) {
                VehiclePropValue v = it.next();
                HalServiceBase service = this.mPropertyHandlers.get(v.prop);
                if (service == null) {
                    Slog.e(CarLog.TAG_HAL, "HalService not found for prop: 0x" + Integer.toHexString(v.prop));
                } else {
                    service.getDispatchList().add(v);
                    VehiclePropertyEventInfo info = this.mEventLog.get(Integer.valueOf(v.prop));
                    if (info == null) {
                        this.mEventLog.put(Integer.valueOf(v.prop), new VehiclePropertyEventInfo(v));
                    } else {
                        info.addNewEvent(v);
                    }
                    if (!(service instanceof PropertyHalService)) {
                        this.mServicesToDispatch.add(service);
                    } else {
                        ArrayList<Integer> pidArray = this.mHalServiceToPid.get(service);
                        if (pidArray == null) {
                            Slog.e(CarLog.TAG_HAL, "onPropertyEventXp get pid info error!");
                        } else {
                            Iterator<Integer> it2 = pidArray.iterator();
                            while (it2.hasNext()) {
                                Integer p = it2.next();
                                ArrayList<ICarPropertyEventListener> listenerArray = this.mPidToClientListener.get(p);
                                if (listenerArray == null) {
                                    Slog.e(CarLog.TAG_HAL, "onPropertyEventXp get listener info error!");
                                } else {
                                    HalEvent halEvent = new HalEvent(p.intValue(), listenerArray, this.mPidToHandler.get(p));
                                    CarPropertyEvent event = ((PropertyHalService) service).convertHalEventToCarEvent(v);
                                    if (event == null) {
                                        Slog.e(CarLog.TAG_HAL, "prop not support:" + v.prop + " pid=" + p);
                                    } else {
                                        halEvent.addProp(event);
                                        this.mHalEventToDispatch.add(halEvent);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Iterator<HalEvent> it3 = this.mHalEventToDispatch.iterator();
        while (it3.hasNext()) {
            it3.next().handleHalEvent();
        }
        this.mHalEventToDispatch.clear();
        Iterator<HalServiceBase> it4 = this.mServicesToDispatch.iterator();
        while (it4.hasNext()) {
            HalServiceBase s = it4.next();
            s.handleHalEvents(s.getDispatchList());
            s.getDispatchList().clear();
        }
        this.mServicesToDispatch.clear();
    }

    @Override // android.hardware.automotive.vehicle.V2_0.IVehicleCallback
    public void onPropertySet(VehiclePropValue value) {
    }

    @Override // android.hardware.automotive.vehicle.V2_0.IVehicleCallback
    public void onPropertySetError(int errorCode, int propId, int areaId) {
        if (CarLog.isCallbackLogEnable(propId)) {
            Slog.e(CarLog.TAG_HAL, String.format("onPropertySetError, errorCode: %d, prop: 0x%x, area: 0x%x", Integer.valueOf(errorCode), Integer.valueOf(propId), Integer.valueOf(areaId)));
        }
        if (propId != 0) {
            HalServiceBase service = this.mPropertyHandlers.get(propId);
            if (service != null) {
                service.handlePropertySetError(propId, errorCode);
                return;
            }
            Set<HalServiceBase> serviceBases = this.mPropertySetHandlers.get(propId);
            if (serviceBases != null && !serviceBases.isEmpty()) {
                for (HalServiceBase serviceBase : serviceBases) {
                    if (serviceBase != null) {
                        serviceBase.handlePropertySetError(propId, errorCode);
                    }
                }
            }
        }
    }

    public void dump(PrintWriter writer) {
        if (this.mHalClient != null) {
            this.mHalClient.dump(writer);
        }
        writer.println("**dump HAL services**");
        Iterator<HalServiceBase> it = this.mDumpServices.iterator();
        while (it.hasNext()) {
            HalServiceBase service = it.next();
            service.dump(writer);
        }
        writer.println(String.format("**All Events, now ns:%d, %s**", Long.valueOf(SystemClock.elapsedRealtimeNanos()), TimeUtils.formatDuration(SystemClock.elapsedRealtime())));
        for (VehiclePropertyEventInfo info : this.mEventLog.values()) {
            writer.println(String.format("event count:%d, lastEvent:%s", Integer.valueOf(info.eventCount), dumpVehiclePropValue(info.lastEvent)));
        }
        for (Integer num : this.mSubscribedProperties.keySet()) {
            int property = num.intValue();
            writer.println("subscribed " + XpDebugLog.getPropertyDescription(property));
        }
    }

    public void dumpPropertyValueByCommend(PrintWriter writer, String propId, String areaId) {
        if (propId.equals("")) {
            writer.println("**All property values**");
            for (VehiclePropConfig config : this.mAllProperties.values()) {
                dumpPropertyValueByConfig(writer, config);
            }
        } else if (areaId.equals("")) {
            VehiclePropConfig config2 = this.mAllProperties.get(Integer.valueOf(Integer.parseInt(propId, 16)));
            dumpPropertyValueByConfig(writer, config2);
        } else {
            int id = Integer.parseInt(propId, 16);
            int area = Integer.parseInt(areaId);
            try {
                VehiclePropValue value = get(id, area);
                writer.println(dumpVehiclePropValue(value));
            } catch (Exception e) {
                writer.println("Can not get property value for propertyId: 0x" + propId + ", areaId: " + area);
            }
        }
    }

    private void dumpPropertyValueByConfig(PrintWriter writer, VehiclePropConfig config) {
        if (config.areaConfigs.isEmpty()) {
            try {
                VehiclePropValue value = get(config.prop);
                writer.println(dumpVehiclePropValue(value));
                return;
            } catch (Exception e) {
                writer.println("Can not get property value for propertyId: 0x" + Integer.toHexString(config.prop) + ", areaId: 0");
                return;
            }
        }
        Iterator<VehicleAreaConfig> it = config.areaConfigs.iterator();
        while (it.hasNext()) {
            VehicleAreaConfig areaConfig = it.next();
            int area = areaConfig.areaId;
            try {
                VehiclePropValue value2 = get(config.prop, area);
                writer.println(dumpVehiclePropValue(value2));
            } catch (Exception e2) {
                writer.println("Can not get property value for propertyId: 0x" + Integer.toHexString(config.prop) + ", areaId: " + area);
            }
        }
    }

    public void dumpPropertyConfigs(PrintWriter writer, String propId) {
        List<VehiclePropConfig> configList;
        synchronized (this) {
            configList = new ArrayList<>(this.mAllProperties.values());
        }
        if (propId.equals("")) {
            writer.println("**All properties**");
            for (VehiclePropConfig config : configList) {
                writer.println(dumpPropertyConfigsHelp(config));
            }
            return;
        }
        for (VehiclePropConfig config2 : configList) {
            if (Integer.toHexString(config2.prop).equals(propId)) {
                writer.println(dumpPropertyConfigsHelp(config2));
                return;
            }
        }
    }

    private static String dumpPropertyConfigsHelp(VehiclePropConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("Property:0x");
        sb.append(Integer.toHexString(config.prop));
        sb.append(",Property name:");
        sb.append(VehicleProperty.toString(config.prop));
        sb.append(",access:0x");
        sb.append(Integer.toHexString(config.access));
        sb.append(",changeMode:0x");
        sb.append(Integer.toHexString(config.changeMode));
        sb.append(",config:0x");
        sb.append(Arrays.toString(config.configArray.toArray()));
        sb.append(",fs min:");
        sb.append(config.minSampleRate);
        sb.append(",fs max:");
        StringBuilder builder = sb.append(config.maxSampleRate);
        Iterator<VehicleAreaConfig> it = config.areaConfigs.iterator();
        while (it.hasNext()) {
            VehicleAreaConfig area = it.next();
            builder.append(",areaId :");
            builder.append(Integer.toHexString(area.areaId));
            builder.append(",f min:");
            builder.append(area.minFloatValue);
            builder.append(",f max:");
            builder.append(area.maxFloatValue);
            builder.append(",i min:");
            builder.append(area.minInt32Value);
            builder.append(",i max:");
            builder.append(area.maxInt32Value);
            builder.append(",i64 min:");
            builder.append(area.minInt64Value);
            builder.append(",i64 max:");
            builder.append(area.maxInt64Value);
        }
        return builder.toString();
    }

    public void injectVhalEvent(String property, String zone, String value) throws NumberFormatException {
        if (value == null || zone == null || property == null) {
            return;
        }
        int propId = Integer.decode(property).intValue();
        int zoneId = Integer.decode(zone).intValue();
        VehiclePropValue v = createPropValue(propId, zoneId);
        int propertyType = 16711680 & propId;
        List<String> dataList = new ArrayList<>(Arrays.asList(value.split(DATA_DELIMITER)));
        if (propertyType == 2097152) {
            v.value.int32Values.add(Integer.valueOf(Boolean.valueOf(value).booleanValue() ? 1 : 0));
        } else if (propertyType == 4194304 || propertyType == 4259840) {
            for (String s : dataList) {
                v.value.int32Values.add(Integer.decode(s));
            }
        } else if (propertyType == 6291456 || propertyType == 6356992) {
            for (String s2 : dataList) {
                v.value.floatValues.add(Float.valueOf(Float.parseFloat(s2)));
            }
        } else if (propertyType == 7340032) {
            for (String s3 : dataList) {
                Slog.d(CarLog.TAG_HAL, "byte:" + s3);
                v.value.bytes.add(Byte.decode(s3));
            }
        } else if (propertyType == 8388608 || propertyType == 8454144) {
            for (String s4 : dataList) {
                v.value.doubleValues.add(Double.valueOf(Double.parseDouble(s4)));
            }
        } else {
            Slog.e(CarLog.TAG_HAL, "Property type unsupported:" + propertyType);
            return;
        }
        v.timestamp = SystemClock.elapsedRealtimeNanos();
        onPropertyEvent(Lists.newArrayList(new VehiclePropValue[]{v}));
    }

    public void injectOnPropertySetError(String property, String zone, String errorCode) {
        if (zone == null || property == null || errorCode == null) {
            return;
        }
        int propId = Integer.decode(property).intValue();
        int zoneId = Integer.decode(zone).intValue();
        int errorId = Integer.decode(errorCode).intValue();
        onPropertySetError(errorId, propId, zoneId);
    }

    /* loaded from: classes3.dex */
    public static class VehiclePropertyEventInfo {
        private int eventCount;
        private VehiclePropValue lastEvent;

        private VehiclePropertyEventInfo(VehiclePropValue event) {
            this.eventCount = 1;
            this.lastEvent = event;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void addNewEvent(VehiclePropValue event) {
            this.eventCount++;
            this.lastEvent = event;
        }

        public int getCount() {
            return this.eventCount;
        }

        public VehiclePropValue getLastEvent() {
            return this.lastEvent;
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes3.dex */
    public final class VehiclePropValueSetter {
        final WeakReference<HalClient> mClient;
        final VehiclePropValue mPropValue;

        private VehiclePropValueSetter(HalClient client, int propId, int areaId) {
            this.mClient = new WeakReference<>(client);
            this.mPropValue = new VehiclePropValue();
            VehiclePropValue vehiclePropValue = this.mPropValue;
            vehiclePropValue.prop = propId;
            vehiclePropValue.areaId = areaId;
        }

        void to(boolean value) throws PropertyTimeoutException {
            to(value ? 1 : 0);
        }

        /* JADX INFO: Access modifiers changed from: package-private */
        public void to(int value) throws PropertyTimeoutException {
            this.mPropValue.value.int32Values.add(Integer.valueOf(value));
            submit();
        }

        void to(int[] values) throws PropertyTimeoutException {
            for (int value : values) {
                this.mPropValue.value.int32Values.add(Integer.valueOf(value));
            }
            submit();
        }

        /* JADX INFO: Access modifiers changed from: package-private */
        public void to(byte[] values) throws PropertyTimeoutException {
            for (byte value : values) {
                this.mPropValue.value.bytes.add(Byte.valueOf(value));
            }
            submit();
        }

        void to(Collection<Integer> values) throws PropertyTimeoutException {
            this.mPropValue.value.int32Values.addAll(values);
            submit();
        }

        void submit() throws PropertyTimeoutException {
            HalClient client = this.mClient.get();
            if (client != null) {
                client.setValue(this.mPropValue);
            }
        }
    }

    private static String dumpVehiclePropValue(VehiclePropValue value) {
        int prop = value.prop;
        StringBuilder sb = new StringBuilder();
        sb.append(XpDebugLog.getPropertyDescription(prop));
        sb.append(",timestamp:");
        sb.append(value.timestamp);
        sb.append(",zone:0x");
        sb.append(Integer.toHexString(value.areaId));
        sb.append(",floatValues: ");
        sb.append(Arrays.toString(value.value.floatValues.toArray()));
        sb.append(",int32Values: ");
        sb.append(Arrays.toString(value.value.int32Values.toArray()));
        sb.append(",int64Values: ");
        StringBuilder sb2 = sb.append(Arrays.toString(value.value.int64Values.toArray()));
        if (value.value.bytes.size() > 20) {
            Object[] bytes = Arrays.copyOf(value.value.bytes.toArray(), 20);
            sb2.append(",bytes: ");
            sb2.append(Arrays.toString(bytes));
        } else {
            sb2.append(",bytes: ");
            sb2.append(Arrays.toString(value.value.bytes.toArray()));
        }
        sb2.append(",string: ");
        sb2.append(value.value.stringValue);
        return sb2.toString();
    }

    private static VehiclePropValue createPropValue(int propId, int areaId) {
        VehiclePropValue propValue = new VehiclePropValue();
        propValue.prop = propId;
        propValue.areaId = areaId;
        return propValue;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes3.dex */
    public class HalEventHandler extends Handler {
        private static final int MSG_ON_PROPERTY_EVENT = 2;

        HalEventHandler(Looper looper) {
            super(looper);
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == 2) {
                dispatchEvent((HalEvent) msg.obj);
            }
        }

        private void dispatchEvent(HalEvent event) {
            event.dispatchEvent();
        }
    }

    public int registerClientInfo(int pid, PropertyHalService halService, ICarPropertyEventListener listener) {
        Slog.i(CarLog.TAG_HAL, pid + " registerClientInfo for " + halService);
        synchronized (this) {
            ArrayList<PropertyHalService> services = this.mPidToHalService.get(Integer.valueOf(pid));
            if (services == null) {
                ArrayList<PropertyHalService> hs = new ArrayList<>();
                hs.add(halService);
                this.mPidToHalService.put(Integer.valueOf(pid), hs);
                HandlerThread thr = new HandlerThread(Integer.toString(pid));
                thr.start();
                this.mPidToThread.put(Integer.valueOf(pid), thr);
                HalEventHandler handler = new HalEventHandler(thr.getLooper());
                this.mPidToHandler.put(Integer.valueOf(pid), handler);
                ArrayList<ICarPropertyEventListener> lisenerArray = new ArrayList<>();
                lisenerArray.add(listener);
                this.mHalServiceToClientListener.put(halService, lisenerArray);
                ArrayList<Integer> pidArray = new ArrayList<>();
                pidArray.add(Integer.valueOf(pid));
                this.mHalServiceToPid.put(halService, pidArray);
                ArrayList<ICarPropertyEventListener> lisenerArray4Pid = new ArrayList<>();
                lisenerArray4Pid.add(listener);
                this.mPidToClientListener.put(Integer.valueOf(pid), lisenerArray4Pid);
            } else {
                for (int i = 0; i < services.size(); i++) {
                    if (services.get(i) == halService) {
                        ArrayList<ICarPropertyEventListener> listenerArray = this.mHalServiceToClientListener.get(halService);
                        if (listenerArray == null) {
                            ArrayList<ICarPropertyEventListener> la = new ArrayList<>();
                            la.add(listener);
                            this.mHalServiceToClientListener.put(halService, la);
                        } else {
                            for (int j = 0; j < listenerArray.size(); j++) {
                                if (listenerArray.get(j) == listener) {
                                    Slog.w(CarLog.TAG_HAL, "has registe this listener:" + listener);
                                    return -1;
                                }
                            }
                            listenerArray.add(listener);
                            this.mHalServiceToClientListener.put(halService, listenerArray);
                        }
                    }
                }
                boolean found = false;
                ArrayList<Integer> pidArray2 = this.mHalServiceToPid.get(halService);
                if (pidArray2 == null) {
                    ArrayList<Integer> pa = new ArrayList<>();
                    pa.add(Integer.valueOf(pid));
                    this.mHalServiceToPid.put(halService, pa);
                } else {
                    int i2 = 0;
                    while (true) {
                        if (i2 < pidArray2.size()) {
                            if (pidArray2.get(i2).intValue() != pid) {
                                i2++;
                            } else {
                                Slog.w(CarLog.TAG_HAL, "pid is in the record:" + pid);
                                found = true;
                                break;
                            }
                        } else {
                            break;
                        }
                    }
                    if (!found) {
                        pidArray2.add(Integer.valueOf(pid));
                        this.mHalServiceToPid.put(halService, pidArray2);
                    }
                }
                ArrayList<ICarPropertyEventListener> lisenerArray4Pid2 = this.mPidToClientListener.get(Integer.valueOf(pid));
                if (lisenerArray4Pid2 == null) {
                    ArrayList<ICarPropertyEventListener> array = new ArrayList<>();
                    array.add(listener);
                    this.mPidToClientListener.put(Integer.valueOf(pid), array);
                } else {
                    lisenerArray4Pid2.add(listener);
                    this.mPidToClientListener.put(Integer.valueOf(pid), lisenerArray4Pid2);
                }
            }
            Slog.i(CarLog.TAG_HAL, pid + " registerClientInfo sucess.");
            return 0;
        }
    }

    public void unregisterClientInfo(int pid, PropertyHalService halService, ICarPropertyEventListener listener) {
        Slog.i(CarLog.TAG_HAL, pid + " unregisterClientInfo from " + halService);
        synchronized (this) {
            ArrayList<PropertyHalService> services = this.mPidToHalService.get(Integer.valueOf(pid));
            if (services == null) {
                Slog.w(CarLog.TAG_HAL, "not found info for " + pid);
                return;
            }
            int i = 0;
            int i2 = 0;
            while (true) {
                if (i2 >= services.size()) {
                    break;
                } else if (services.get(i2) != halService) {
                    i2++;
                } else {
                    services.remove(halService);
                    if (services.size() == 0) {
                        this.mPidToHalService.remove(Integer.valueOf(pid));
                        this.mPidToHandler.remove(Integer.valueOf(pid));
                        HandlerThread thread = this.mPidToThread.remove(Integer.valueOf(pid));
                        thread.getThreadHandler().removeMessages(0);
                        thread.quitSafely();
                    }
                }
            }
            ArrayList<ICarPropertyEventListener> listenerArray = this.mHalServiceToClientListener.get(halService);
            if (listenerArray != null) {
                int i3 = 0;
                while (true) {
                    if (i3 >= listenerArray.size()) {
                        break;
                    } else if (listenerArray.get(i3) != listener) {
                        i3++;
                    } else {
                        listenerArray.remove(i3);
                        if (listenerArray.size() == 0) {
                            this.mHalServiceToClientListener.remove(halService);
                        }
                    }
                }
            }
            ArrayList<Integer> pidArray = this.mHalServiceToPid.get(halService);
            if (pidArray != null) {
                int i4 = 0;
                while (true) {
                    if (i4 >= pidArray.size()) {
                        break;
                    }
                    if (pidArray.get(i4).intValue() == pid) {
                        pidArray.remove(i4);
                        if (pidArray.size() == 0) {
                            this.mHalServiceToPid.remove(halService);
                            break;
                        }
                    }
                    i4++;
                }
            }
            ArrayList<ICarPropertyEventListener> lisenerArray4Pid = this.mPidToClientListener.get(Integer.valueOf(pid));
            if (lisenerArray4Pid != null) {
                while (true) {
                    if (i >= lisenerArray4Pid.size()) {
                        break;
                    } else if (lisenerArray4Pid.get(i) != listener) {
                        i++;
                    } else {
                        lisenerArray4Pid.remove(i);
                        if (lisenerArray4Pid.size() == 0) {
                            this.mPidToClientListener.remove(Integer.valueOf(pid));
                        }
                    }
                }
            }
        }
    }

    public void setDebugEnabled(boolean on) {
        synchronized (this) {
            if (on) {
                if (!this.mDebugIsRunning) {
                    Slog.d(CarLog.TAG_HAL, "Start CarService hal Debug thread");
                    if (this.mDebug == null) {
                        this.mDebug = new Debug(this.mContext);
                    }
                    this.mDebug.start();
                    this.mDebugIsRunning = true;
                } else {
                    Slog.d(CarLog.TAG_HAL, "Debug thread already running");
                }
            } else if (this.mDebug != null) {
                Slog.d(CarLog.TAG_HAL, "Stop CarService hal Debug thread");
                this.mDebugIsRunning = false;
                this.mDebug.stop();
            }
        }
    }
}
