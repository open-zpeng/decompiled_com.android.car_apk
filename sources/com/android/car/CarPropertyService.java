package com.android.car;

import android.car.ValueUnavailableException;
import android.car.XpDebugLog;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.eps.IEpsEventListener;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.ICarProperty;
import android.car.hardware.property.ICarPropertyEventListener;
import android.car.hardware.scu.IScuEventListener;
import android.car.hardware.vcu.IVcuEventListener;
import android.content.Context;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.ArraySet;
import android.util.Slog;
import android.util.SparseArray;
import com.android.car.CarPropertyService;
import com.android.car.Manifest;
import com.android.car.hal.PropertyHalService;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
/* loaded from: classes3.dex */
public class CarPropertyService extends ICarProperty.Stub implements CarServiceBase, PropertyHalService.PropertyHalListener {
    private static final String AUTOPILOT_PACKAGE_NAME = "com.xiaopeng.autopilot";
    private static final boolean DBG = true;
    public static final long MAX_SEND_PROPS_INTERVAL_MS = Duration.ofMillis(50).toMillis();
    private static final String TAG = "Property.service";
    private final CallbackStatistics mCallbackStatistics;
    private Map<Integer, CarPropertyConfig<?>> mConfigs;
    private final Context mContext;
    private final DiagnosisErrorReporter mDiagnosisErrorReporter;
    private final PropertyHalService mHal;
    private final int mMyPid;
    private final Map<IBinder, Client> mClientMap = new ConcurrentHashMap();
    private final Map<Integer, List<Client>> mPropIdClientMap = new ConcurrentHashMap();
    private final Object mLock = new Object();
    @Deprecated
    private final Map<IBinder, IScuEventListener> mScuListenersMap = new ConcurrentHashMap();
    @Deprecated
    private final Map<IBinder, ScuDeathRecipient> mScuDeathRecipientMap = new ConcurrentHashMap();
    @Deprecated
    private final Map<IBinder, IVcuEventListener> mVcuListenersMap = new ConcurrentHashMap();
    @Deprecated
    private final Map<IBinder, VcuDeathRecipient> mVcuDeathRecipientMap = new ConcurrentHashMap();
    @Deprecated
    private final Map<IBinder, IEpsEventListener> mEpsListenersMap = new ConcurrentHashMap();
    @Deprecated
    private final Map<IBinder, EpsDeathRecipient> mEpsDeathRecipientMap = new ConcurrentHashMap();
    private final ArraySet<Integer> mEventPropertyIds = new ArraySet<>(Arrays.asList(Integer.valueOf((int) VehicleProperty.TBOX_SYNC_UTC_TIME), Integer.valueOf((int) VehicleProperty.TBOX_TIME_INFO)));
    private final Map<Integer, ArraySet<Integer>> mAutopilotRegisteredProperties = new ConcurrentHashMap();
    private boolean mListenerIsSet = false;

    public CarPropertyService(Context context, PropertyHalService hal) {
        Slog.i(TAG, "CarPropertyService started!");
        this.mHal = hal;
        this.mContext = context;
        this.mCallbackStatistics = new CallbackStatistics(TAG, true);
        this.mMyPid = this.mCallbackStatistics.getMyPid();
        this.mDiagnosisErrorReporter = DiagnosisErrorReporter.getInstance(context);
    }

    private static float[] toFloatArray(Float[] input) {
        int len = input.length;
        float[] arr = new float[len];
        for (int i = 0; i < len; i++) {
            arr[i] = input[i].floatValue();
        }
        return arr;
    }

    private static <E> E getValue(CarPropertyValue propertyValue) {
        return (E) propertyValue.getValue();
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
        if (this.mConfigs == null) {
            this.mConfigs = this.mHal.getPropertyList();
            Slog.i(TAG, "cache CarPropertyConfigs " + this.mConfigs.size());
        }
    }

    @Override // com.android.car.CarServiceBase
    public void release() {
        for (Client c : this.mClientMap.values()) {
            c.release();
        }
        this.mClientMap.clear();
        this.mPropIdClientMap.clear();
        this.mCallbackStatistics.release();
        this.mHal.setListener(null);
        this.mListenerIsSet = false;
    }

    @Override // com.android.car.CarServiceBase
    public void dump(final PrintWriter writer) {
        writer.println("**dump CarPropertyService**");
        writer.println("    Client Info: " + this.mClientMap.size());
        this.mClientMap.values().stream().sorted(Comparator.comparingInt(new ToIntFunction() { // from class: com.android.car.-$$Lambda$CarPropertyService$F9pK1JmsVVrH5hk0FuUJzYOE-C8
            @Override // java.util.function.ToIntFunction
            public final int applyAsInt(Object obj) {
                int i;
                i = ((CarPropertyService.Client) obj).pid;
                return i;
            }
        })).filter(new Predicate() { // from class: com.android.car.-$$Lambda$CarPropertyService$k1dgrBrQL-LbmuKkan8LQcg0IBA
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return CarPropertyService.this.lambda$dump$1$CarPropertyService((CarPropertyService.Client) obj);
            }
        }).forEach(new Consumer() { // from class: com.android.car.-$$Lambda$CarPropertyService$F6S_1qM3s0bUlC91w489_HnkEws
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                ((CarPropertyService.Client) obj).dump(writer);
            }
        });
        writer.println("    mPropertyToClientsMap: " + this.mPropIdClientMap.size());
        this.mPropIdClientMap.entrySet().stream().sorted(Comparator.comparing(new Function() { // from class: com.android.car.-$$Lambda$CarPropertyService$pwhxcJCkEad7aENFomCJC6ZF7hs
            @Override // java.util.function.Function
            public final Object apply(Object obj) {
                Integer valueOf;
                valueOf = Integer.valueOf(((List) ((Map.Entry) obj).getValue()).size());
                return valueOf;
            }
        }, Comparator.reverseOrder())).forEach(new Consumer() { // from class: com.android.car.-$$Lambda$CarPropertyService$rYSIZqKHvfW5YDFuKmJVq_UuDyw
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                CarPropertyService.this.lambda$dump$6$CarPropertyService(writer, (Map.Entry) obj);
            }
        });
        this.mCallbackStatistics.dump(writer);
    }

    public /* synthetic */ boolean lambda$dump$1$CarPropertyService(Client v) {
        return v.pid != this.mMyPid;
    }

    public /* synthetic */ void lambda$dump$6$CarPropertyService(final PrintWriter writer, Map.Entry cs) {
        List<Client> clients = (List) cs.getValue();
        int prop = ((Integer) cs.getKey()).intValue();
        writer.println("        " + XpDebugLog.getPropertyDescription(prop) + " Listeners size: " + clients.size());
        clients.stream().filter(new Predicate() { // from class: com.android.car.-$$Lambda$CarPropertyService$7It2MrWvym-L3RaKyXzNVrydGUA
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return CarPropertyService.this.lambda$dump$4$CarPropertyService((CarPropertyService.Client) obj);
            }
        }).forEach(new Consumer() { // from class: com.android.car.-$$Lambda$CarPropertyService$IokmSODRKG1IwdBRuvF77S26GLE
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                CarPropertyService.Client client = (CarPropertyService.Client) obj;
                writer.println("            pid: " + client.pid + "(" + client.processName + "), mListenerBinder: " + client.mListenerBinder);
            }
        });
    }

    public /* synthetic */ boolean lambda$dump$4$CarPropertyService(Client v) {
        return v.pid != this.mMyPid;
    }

    private boolean isMonitoredAutoPilotAppId(int id, String callingPkgName) {
        return isAutopilotApp(callingPkgName) && (id == 557847561 || id == 557852187);
    }

    public void registerListener(int propId, float rate, ICarPropertyEventListener listener) {
        Client client;
        ArraySet<Integer> ids;
        int pid = Binder.getCallingPid();
        int uid = Binder.getCallingUid();
        String processName = ProcessUtils.getProcessName(this.mContext, pid, uid);
        if (listener == null) {
            Slog.e(TAG, "Process " + processName + " Client (uid:" + uid + ", pid: " + pid + ") registerListener: Listener is null for property: " + XpDebugLog.getPropertyDescription(propId));
            if (isMonitoredAutoPilotAppId(propId, processName)) {
                this.mDiagnosisErrorReporter.reportAutopilotRegisterPropertiesFailed();
            }
            throw new IllegalArgumentException("listener cannot be null.");
        } else if (this.mConfigs.get(Integer.valueOf(propId)) == null) {
            Slog.e(TAG, "Process " + processName + " Client (uid:" + uid + ", pid: " + pid + ") registerListener: propId is not in config list:" + XpDebugLog.getPropertyDescription(propId));
            if (isMonitoredAutoPilotAppId(propId, processName)) {
                this.mDiagnosisErrorReporter.reportAutopilotRegisterPropertiesFailed();
            }
        } else if (!ICarImpl.checkCarPermission(this.mContext, propId, 1)) {
        } else {
            ICarImpl.assertPermission(this.mContext, this.mHal.getReadPermission(propId));
            IBinder listenerBinder = listener.asBinder();
            if (pid != this.mMyPid) {
                Slog.i(TAG, "Process " + processName + " Client (uid:" + uid + ", pid: " + pid + ") registerListener: propId=" + XpDebugLog.getPropertyDescription(propId) + " rate=" + rate + " listener:" + listenerBinder);
            }
            synchronized (this.mLock) {
                client = this.mClientMap.get(listenerBinder);
                if (client == null) {
                    client = new Client(listener);
                    this.mClientMap.put(client.mListenerBinder, client);
                }
                client.addProperty(propId, rate);
                List<Client> clients = this.mPropIdClientMap.get(Integer.valueOf(propId));
                if (clients == null) {
                    clients = new CopyOnWriteArrayList();
                    this.mPropIdClientMap.put(Integer.valueOf(propId), clients);
                }
                if (!clients.contains(client)) {
                    clients.add(client);
                }
                if (!this.mListenerIsSet) {
                    this.mHal.setListener(this);
                }
                if (rate > this.mHal.getSampleRate(propId)) {
                    try {
                        this.mHal.subscribeProperty(propId, rate);
                    } catch (Exception e) {
                        if (isAutopilotApp(processName)) {
                            Slog.e(TAG, "autopilot app registerListener: " + XpDebugLog.getPropertyDescription(propId) + " with exception: " + e);
                            this.mDiagnosisErrorReporter.reportAutopilotRegisterPropertiesFailed();
                        }
                        if (e instanceof IllegalArgumentException) {
                            throw e;
                        }
                    }
                }
                if (client.isAutopilotClient && propId == 557847045 && ((ids = this.mAutopilotRegisteredProperties.get(Integer.valueOf(pid))) == null || !ids.contains(Integer.valueOf((int) VehicleProperty.MCU_IG_DATA)))) {
                    Slog.e(TAG, "autopilot app not registerListener for ig data, registered: " + CarServiceUtils.idsToString(ids));
                    this.mDiagnosisErrorReporter.reportAutopilotRegisterPropertiesFailed();
                }
            }
            if (rate >= 0.0f) {
                callbackLatestValue(propId, listener, client);
                return;
            }
            int flags = (int) (-rate);
            if ((flags & 1) == 0) {
                callbackLatestValue(propId, listener, client);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isAutopilotApp(String processName) {
        return Objects.equals(processName, AUTOPILOT_PACKAGE_NAME);
    }

    private void callbackLatestValue(int propId, ICarPropertyEventListener listener, Client client) {
        if (this.mEventPropertyIds.contains(Integer.valueOf(propId))) {
            return;
        }
        List<CarPropertyEvent> events = new LinkedList<>();
        try {
            CarPropertyValue value = this.mHal.getProperty(propId, 0, false);
            if (value != null) {
                CarPropertyEvent event = new CarPropertyEvent(0, value);
                events.add(event);
            }
        } catch (Exception e) {
            if (CarLog.isGetLogEnable()) {
                Slog.e(TAG, "get prop data failed, won't callback");
            }
        }
        if (!events.isEmpty()) {
            try {
                listener.onEvent(events);
                if (isMonitoredAutoPilotAppId(propId, client.processName)) {
                    Slog.i(TAG, "callback latest " + XpDebugLog.getPropertyDescription(propId) + " for autopilot");
                }
                this.mCallbackStatistics.addPropMethodCallCount(propId, client.processName, client.pid, client.mListenerBinder);
            } catch (RemoteException ex) {
                Slog.e(TAG, "onEvent calling failed: " + ex.getMessage() + " for " + XpDebugLog.getPropertyDescription(propId));
            }
        } else if (isMonitoredAutoPilotAppId(propId, client.processName)) {
            Slog.i(TAG, "won't callback latest " + XpDebugLog.getPropertyDescription(propId) + " for autopilot due to no data");
        }
    }

    @Deprecated
    public void registerScuListener(IScuEventListener listener) {
        Slog.i(TAG, "registerScuListener");
        if (listener == null) {
            Slog.e(TAG, "registerScuListener: Listener is null.");
            throw new IllegalArgumentException("listener cannot be null.");
        }
        IBinder listenerBinder = listener.asBinder();
        if (this.mScuListenersMap.containsKey(listenerBinder)) {
            return;
        }
        ScuDeathRecipient deathRecipient = new ScuDeathRecipient(listenerBinder);
        try {
            listenerBinder.linkToDeath(deathRecipient, 0);
            this.mScuDeathRecipientMap.put(listenerBinder, deathRecipient);
            this.mScuListenersMap.put(listenerBinder, listener);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to link death for recipient. " + e.getMessage());
        }
    }

    @Deprecated
    public void unregisterScuListener(IScuEventListener listener) {
        Slog.i(TAG, "unregisterScuListener");
        if (listener == null) {
            Slog.e(TAG, "unregisterScuListener: listener was not registered");
            throw new IllegalArgumentException("Listener is null");
        }
        IBinder listenerBinder = listener.asBinder();
        if (!this.mScuListenersMap.containsKey(listenerBinder)) {
            Slog.e(TAG, "unregisterScuListener: Listener was not previously registered.");
        }
        unregisterScuListenerLocked(listenerBinder);
    }

    /* JADX INFO: Access modifiers changed from: private */
    @Deprecated
    public void unregisterScuListenerLocked(IBinder listenerBinder) {
        Object status = this.mScuListenersMap.remove(listenerBinder);
        if (status != null) {
            this.mScuDeathRecipientMap.get(listenerBinder).release();
            this.mScuDeathRecipientMap.remove(listenerBinder);
        }
    }

    @Deprecated
    public void onAccEvent(int acc) {
        for (IScuEventListener l : this.mScuListenersMap.values()) {
            try {
                l.onAccEvent(acc);
            } catch (RemoteException ex) {
                Slog.e(TAG, "onAccEvent calling failed: " + ex.getMessage());
            }
        }
    }

    @Deprecated
    public void registerVcuListener(IVcuEventListener listener) {
        Slog.i(TAG, "registerVcuListener");
        if (listener == null) {
            Slog.e(TAG, "registerVcuListener: Listener is null.");
            throw new IllegalArgumentException("listener cannot be null.");
        }
        IBinder listenerBinder = listener.asBinder();
        if (this.mVcuListenersMap.containsKey(listenerBinder)) {
            return;
        }
        VcuDeathRecipient deathRecipient = new VcuDeathRecipient(listenerBinder);
        try {
            listenerBinder.linkToDeath(deathRecipient, 0);
            this.mVcuDeathRecipientMap.put(listenerBinder, deathRecipient);
            this.mVcuListenersMap.put(listenerBinder, listener);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to link death for recipient. " + e.getMessage());
        }
    }

    @Deprecated
    public void unregisterVcuListener(IVcuEventListener listener) {
        Slog.i(TAG, "unregisterVcuListener");
        if (listener == null) {
            Slog.e(TAG, "unregisterVcuListener: listener was not registered");
            throw new IllegalArgumentException("Listener is null");
        }
        IBinder listenerBinder = listener.asBinder();
        if (!this.mVcuListenersMap.containsKey(listenerBinder)) {
            Slog.e(TAG, "unregisterVcuListener: Listener was not previously registered.");
        }
        unregisterVcuListenerLocked(listenerBinder);
    }

    /* JADX INFO: Access modifiers changed from: private */
    @Deprecated
    public void unregisterVcuListenerLocked(IBinder listenerBinder) {
        Object status = this.mVcuListenersMap.remove(listenerBinder);
        if (status != null) {
            this.mVcuDeathRecipientMap.get(listenerBinder).release();
            this.mVcuDeathRecipientMap.remove(listenerBinder);
        }
    }

    @Deprecated
    public void onVcuGearEvent(int gear) {
        for (IVcuEventListener l : this.mVcuListenersMap.values()) {
            try {
                l.onVcuGearEvent(gear);
            } catch (RemoteException ex) {
                Slog.e(TAG, "onVcuGearEvent calling failed: " + ex.getMessage());
            }
        }
    }

    @Deprecated
    public void onVcuRawCarSpeedEvent(float speed) {
        for (IVcuEventListener l : this.mVcuListenersMap.values()) {
            try {
                l.onVcuRawCarSpeedEvent(speed);
            } catch (RemoteException ex) {
                Slog.e(TAG, "onVcuRawCarSpeedEvent calling failed: " + ex.getMessage());
            }
        }
    }

    @Deprecated
    public void onVcuCruiseControlStatusEvent(int status) {
        for (IVcuEventListener l : this.mVcuListenersMap.values()) {
            try {
                l.onVcuCruiseControlStatusEvent(status);
            } catch (RemoteException ex) {
                Slog.e(TAG, "onVcuCruiseControlStatusEvent calling failed: " + ex.getMessage());
            }
        }
    }

    @Deprecated
    public void onVcuChargeStatusEvent(int status) {
        for (IVcuEventListener l : this.mVcuListenersMap.values()) {
            try {
                l.onVcuChargeStatusEvent(status);
            } catch (RemoteException ex) {
                Slog.e(TAG, "onVcuChargeStatusEvent calling failed: " + ex.getMessage());
            }
        }
    }

    @Deprecated
    public void registerEpsListener(IEpsEventListener listener) {
        Slog.i(TAG, "registerEpsListener");
        if (listener == null) {
            Slog.e(TAG, "registerEpsListener: Listener is null.");
            throw new IllegalArgumentException("listener cannot be null.");
        }
        IBinder listenerBinder = listener.asBinder();
        if (this.mEpsListenersMap.containsKey(listenerBinder)) {
            return;
        }
        EpsDeathRecipient deathRecipient = new EpsDeathRecipient(listenerBinder);
        try {
            listenerBinder.linkToDeath(deathRecipient, 0);
            this.mEpsDeathRecipientMap.put(listenerBinder, deathRecipient);
            this.mEpsListenersMap.put(listenerBinder, listener);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to link death for recipient. " + e.getMessage());
        }
    }

    @Deprecated
    public void unregisterEpsListener(IEpsEventListener listener) {
        Slog.i(TAG, "unregisterEpsListener");
        if (listener == null) {
            Slog.e(TAG, "unregisterEpsListener: listener was not registered");
            throw new IllegalArgumentException("Listener is null");
        }
        IBinder listenerBinder = listener.asBinder();
        if (!this.mEpsListenersMap.containsKey(listenerBinder)) {
            Slog.e(TAG, "unregisterEpsListener: Listener was not previously registered.");
        }
        unregisterEpsListenerLocked(listenerBinder);
    }

    /* JADX INFO: Access modifiers changed from: private */
    @Deprecated
    public void unregisterEpsListenerLocked(IBinder listenerBinder) {
        Object status = this.mEpsListenersMap.remove(listenerBinder);
        if (status != null) {
            this.mEpsDeathRecipientMap.get(listenerBinder).release();
            this.mEpsDeathRecipientMap.remove(listenerBinder);
        }
    }

    public void onEpsSteeringAngleEvent(float angle) {
        for (IEpsEventListener l : this.mEpsListenersMap.values()) {
            try {
                l.onEpsSteeringAngleEvent(angle);
            } catch (RemoteException ex) {
                Slog.e(TAG, "onEpsSteeringAngleEvent calling failed: " + ex.getMessage());
            }
        }
    }

    public void onEpsSteeringAngleSpeedEvent(float angleSpeed) {
        for (IEpsEventListener l : this.mEpsListenersMap.values()) {
            try {
                l.onEpsSteeringAngleSpeedEvent(angleSpeed);
            } catch (RemoteException ex) {
                Slog.e(TAG, "onEpsSteeringAngleSpeedEvent calling failed: " + ex.getMessage());
            }
        }
    }

    public void unregisterListener(int propId, ICarPropertyEventListener listener) {
        if (!ICarImpl.checkCarPermission(this.mContext, propId, 1)) {
            return;
        }
        ICarImpl.assertPermission(this.mContext, this.mHal.getReadPermission(propId));
        if (listener == null) {
            int pid = Binder.getCallingPid();
            int uid = Binder.getCallingUid();
            Slog.e(TAG, "Client (uid:" + uid + ", pid: " + pid + ") unregisterListener: Listener is null.");
            throw new IllegalArgumentException("Listener is null");
        }
        IBinder listenerBinder = listener.asBinder();
        synchronized (this.mLock) {
            unregisterListenerBinderLocked(propId, listenerBinder);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void unregisterListenerBinderLocked(int propId, IBinder listenerBinder) {
        Client client = this.mClientMap.get(listenerBinder);
        List<Client> propertyClients = this.mPropIdClientMap.get(Integer.valueOf(propId));
        int pid = client == null ? Binder.getCallingPid() : client.pid;
        int uid = client == null ? Binder.getCallingUid() : client.uid;
        String processName = ProcessUtils.getProcessName(this.mContext, pid, uid);
        if (this.mConfigs.get(Integer.valueOf(propId)) == null) {
            Slog.e(TAG, "Process " + processName + " Client (uid:" + uid + ", pid: " + pid + ") unregisterListener: propId is not in config list:" + XpDebugLog.getPropertyDescription(propId));
        } else if (client == null || propertyClients == null) {
            Slog.e(TAG, "unregisterListenerBinderLocked: Listener was not previously registered.");
        } else {
            if (pid != this.mMyPid) {
                Slog.i(TAG, "Process " + processName + " Client (uid:" + uid + ", pid: " + pid + ") unregisterListener: propId=" + XpDebugLog.getPropertyDescription(propId) + " listener: " + listenerBinder);
            }
            if (propertyClients.remove(client)) {
                client.removeProperty(propId);
            } else {
                Slog.e(TAG, "unregisterListenerBinderLocked: Listener was not registered for " + XpDebugLog.getPropertyDescription(propId));
            }
            if (propertyClients.isEmpty()) {
                this.mHal.unsubscribeProperty(propId);
                this.mPropIdClientMap.remove(Integer.valueOf(propId));
                if (this.mPropIdClientMap.isEmpty()) {
                    this.mHal.setListener(null);
                    this.mListenerIsSet = false;
                    return;
                }
                return;
            }
            float maxRate = 0.0f;
            for (Client c : propertyClients) {
                float rate = c.getRate(propId);
                if (rate > maxRate) {
                    maxRate = rate;
                }
            }
            this.mHal.subscribeProperty(propId, maxRate);
        }
    }

    public List<CarPropertyConfig> getPropertyList() {
        List<CarPropertyConfig> returnList = new ArrayList<>();
        for (CarPropertyConfig c : this.mConfigs.values()) {
            if (ICarImpl.hasPermission(this.mContext, this.mHal.getReadPermission(c.getPropertyId()))) {
                returnList.add(c);
            }
        }
        int callingPid = Binder.getCallingPid();
        if (callingPid != this.mMyPid) {
            Slog.i(TAG, ProcessUtils.getProcessName(this.mContext, callingPid, Binder.getCallingUid()) + " getPropertyList returns " + returnList.size() + " configs");
        }
        return returnList;
    }

    public CarPropertyConfig[] getPropertyArray() {
        List<CarPropertyConfig> returnList = new ArrayList<>();
        for (CarPropertyConfig c : this.mConfigs.values()) {
            if (ICarImpl.hasPermission(this.mContext, this.mHal.getReadPermission(c.getPropertyId()))) {
                returnList.add(c);
            }
        }
        Slog.i(TAG, "getPropertyArray returns " + returnList.size() + " configs");
        return (CarPropertyConfig[]) returnList.toArray(new CarPropertyConfig[0]);
    }

    public CarPropertyValue getProperty(int propId, int zone) {
        if (this.mConfigs.get(Integer.valueOf(propId)) == null) {
            int pid = Binder.getCallingPid();
            int uid = Binder.getCallingUid();
            String processName = ProcessUtils.getProcessName(this.mContext, pid, uid);
            Slog.e(TAG, "Process " + processName + " Client (uid:" + uid + ", pid: " + pid + ") getProperty: propId is not in config list:" + XpDebugLog.getPropertyDescription(propId));
            throw new ValueUnavailableException(3);
        } else if (!ICarImpl.checkCarPermission(this.mContext, propId, 1)) {
            throw new ValueUnavailableException(3);
        } else {
            ICarImpl.assertPermission(this.mContext, this.mHal.getReadPermission(propId));
            return this.mHal.getProperty(propId, zone);
        }
    }

    public String getReadPermission(int propId) {
        if (this.mConfigs.get(Integer.valueOf(propId)) == null) {
            Slog.e(TAG, "getReadPermission: propId is not in config list:" + XpDebugLog.getPropertyDescription(propId));
            return null;
        }
        return this.mHal.getReadPermission(propId);
    }

    public String getWritePermission(int propId) {
        if (this.mConfigs.get(Integer.valueOf(propId)) == null) {
            Slog.e(TAG, "getWritePermission: propId is not in config list:" + XpDebugLog.getPropertyDescription(propId));
            return null;
        }
        return this.mHal.getWritePermission(propId);
    }

    public void setProperty(CarPropertyValue prop) {
        int propId = prop.getPropertyId();
        int pid = Binder.getCallingPid();
        prop.setTxPid(pid);
        if (this.mConfigs.get(Integer.valueOf(propId)) == null) {
            int uid = Binder.getCallingUid();
            String processName = ProcessUtils.getProcessName(this.mContext, pid, uid);
            Slog.e(TAG, "Process " + processName + " Client (uid:" + uid + ", pid: " + pid + ") setProperty: propId is not in config list:" + XpDebugLog.getPropertyDescription(propId));
        } else if (!ICarImpl.checkCarPermission(this.mContext, propId, 2)) {
        } else {
            ICarImpl.assertPermission(this.mContext, this.mHal.getWritePermission(propId));
            if (this.mHal.isDisplayUnitsProperty(propId)) {
                ICarImpl.assertPermission(this.mContext, Manifest.permission.CAR_VENDOR_EXTENSION);
            }
            this.mHal.setProperty(prop);
        }
    }

    public void setMultiProperties(List<CarPropertyValue> props) {
        if (props != null) {
            int pid = Binder.getCallingPid();
            int uid = Binder.getCallingUid();
            String processName = ProcessUtils.getProcessName(this.mContext, pid, uid);
            String clientDesc = null;
            for (CarPropertyValue prop : props) {
                int propId = prop.getPropertyId();
                prop.setTxPid(pid);
                if (this.mConfigs.get(Integer.valueOf(propId)) == null) {
                    if (uid == 0) {
                        uid = Binder.getCallingUid();
                    }
                    if (clientDesc == null) {
                        clientDesc = "Process " + processName + " Client (uid:" + uid + ", pid: " + pid + ")";
                    }
                    Slog.e(TAG, clientDesc + " setMultiProperties: propId is not in config list:" + XpDebugLog.getPropertyDescription(propId));
                } else if (!ICarImpl.checkCarPermission(this.mContext, propId, 2)) {
                    return;
                } else {
                    ICarImpl.assertPermission(this.mContext, this.mHal.getWritePermission(propId));
                }
            }
            this.mHal.setMultiProperties(props);
        }
    }

    /*  JADX ERROR: JadxRuntimeException in pass: BlockProcessor
        jadx.core.utils.exceptions.JadxRuntimeException: Unreachable block: B:72:0x01bb
        	at jadx.core.dex.visitors.blocks.BlockProcessor.checkForUnreachableBlocks(BlockProcessor.java:81)
        	at jadx.core.dex.visitors.blocks.BlockProcessor.processBlocksTree(BlockProcessor.java:47)
        	at jadx.core.dex.visitors.blocks.BlockProcessor.visit(BlockProcessor.java:39)
        */
    @Override // com.android.car.hal.PropertyHalService.PropertyHalListener
    public void onPropertyChange(java.util.List<android.car.hardware.property.CarPropertyEvent> r22) {
        /*
            Method dump skipped, instructions count: 717
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.car.CarPropertyService.onPropertyChange(java.util.List):void");
    }

    @Override // com.android.car.hal.PropertyHalService.PropertyHalListener
    public void onPropertySetError(int property, int errorCode) {
        List<Client> clients;
        boolean callbackLogEnable = CarLog.isCallbackLogEnable(property);
        if (callbackLogEnable) {
            Slog.w(TAG, "onPropertySetError " + XpDebugLog.getPropertyDescription(property) + " errorCode:" + errorCode);
        }
        synchronized (this.mLock) {
            clients = this.mPropIdClientMap.get(Integer.valueOf(property));
        }
        if (clients != null) {
            List<CarPropertyEvent> eventList = new LinkedList<>();
            eventList.add(CarPropertyEvent.createErrorEvent(property, errorCode));
            for (Client c : clients) {
                try {
                    boolean perfLogEnable = CarLog.isPerfLogEnable();
                    if (perfLogEnable) {
                        Slog.i(TAG, "++onPropertySetError client: " + c.getDescriptionString() + " eventList: " + eventList);
                    }
                    c.getListener().onEvent(eventList);
                    if (perfLogEnable) {
                        Slog.i(TAG, "--onPropertySetError client: " + c.getDescriptionString() + " eventList: " + eventList);
                    }
                } catch (RemoteException ex) {
                    Slog.e(TAG, "onEvent for" + XpDebugLog.getPropertyDescription(property) + " calling failed: " + ex.getMessage());
                }
            }
        } else if (callbackLogEnable) {
            Slog.w(TAG, "onPropertySetError called with no listener registered for " + XpDebugLog.getPropertyDescription(property) + " errorCode:" + errorCode);
        }
    }

    public <E> CarPropertyValue<E> getProperty(Class<E> clazz, int propId, int area) {
        Class<?> actualClass;
        CarPropertyValue<E> propVal = getProperty(propId, area);
        if (propVal != null && propVal.getValue() != null && (actualClass = propVal.getValue().getClass()) != clazz) {
            throw new IllegalArgumentException("Invalid property type. Expected: " + clazz + ", but was: " + actualClass);
        }
        return propVal;
    }

    public int getIntProperty(int prop, int area) {
        CarPropertyValue<Integer> carProp = getProperty(Integer.class, prop, area);
        if (carProp == null) {
            throw new ValueUnavailableException();
        }
        return ((Integer) carProp.getValue()).intValue();
    }

    public boolean getBooleanProperty(int prop, int area) {
        CarPropertyValue<Boolean> carProp = getProperty(Boolean.class, prop, area);
        if (carProp == null) {
            throw new ValueUnavailableException();
        }
        return ((Boolean) carProp.getValue()).booleanValue();
    }

    public float getFloatProperty(int prop, int area) {
        CarPropertyValue<Float> carProp = getProperty(Float.class, prop, area);
        if (carProp == null) {
            throw new ValueUnavailableException();
        }
        return ((Float) carProp.getValue()).floatValue();
    }

    public <E> void setProperty(Class<E> clazz, int propId, int area, E val) {
        setProperty(new CarPropertyValue(propId, area, val));
    }

    public void setBooleanProperty(int prop, int area, boolean val) {
        setProperty(Boolean.class, prop, area, Boolean.valueOf(val));
    }

    public void setFloatProperty(int prop, int area, float val) {
        setProperty(Float.class, prop, area, Float.valueOf(val));
    }

    public void setIntProperty(int prop, int area, int val) {
        setProperty(Integer.class, prop, area, Integer.valueOf(val));
    }

    public void setIntPropertyWithDefaultArea(int prop, int val) {
        setProperty(Integer.class, prop, 0, Integer.valueOf(val));
    }

    public void setIntVectorProperty(int property, int[] values) {
        setProperty(int[].class, property, 0, values);
    }

    public void setStringProperty(int property, String values) {
        setProperty(String.class, property, 0, values);
    }

    public int[] getIntVectorProperty(int property) {
        CarPropertyValue<Integer[]> carProp = getProperty(Integer[].class, property, 0);
        if (carProp == null) {
            throw new ValueUnavailableException();
        }
        return CarServiceUtils.toIntArray((Integer[]) carProp.getValue());
    }

    public long getLongProperty(int prop, int area) {
        CarPropertyValue<Long> carProp = getProperty(Long.class, prop, area);
        if (carProp == null) {
            throw new ValueUnavailableException();
        }
        return ((Long) carProp.getValue()).longValue();
    }

    public long[] getLongVectorProperty(int property) {
        CarPropertyValue<Long[]> carProp = getProperty(Long[].class, property, 0);
        if (carProp == null) {
            throw new ValueUnavailableException();
        }
        return toLongArray((Long[]) carProp.getValue());
    }

    private long[] toLongArray(Long[] input) {
        int len = input.length;
        long[] arr = new long[len];
        for (int i = 0; i < len; i++) {
            arr[i] = input[i].longValue();
        }
        return arr;
    }

    public void setByteVectorProperty(int property, byte[] values) {
        setProperty(byte[].class, property, 0, values);
    }

    public void setLongProperty(int prop, int area, long val) {
        setProperty(Long.class, prop, area, Long.valueOf(val));
    }

    public void setLongVectorProperty(int prop, int area, long[] val) {
        setProperty(long[].class, prop, area, val);
    }

    public float[] getFloatVectorProperty(int property) {
        CarPropertyValue<Float[]> carProp = getProperty(Float[].class, property, 0);
        if (carProp == null) {
            throw new ValueUnavailableException();
        }
        return toFloatArray((Float[]) carProp.getValue());
    }

    public void setFloatVectorProperty(int property, float[] values) {
        setProperty(float[].class, property, 0, values);
    }

    public byte[] getByteVectorProperty(int property) {
        CarPropertyValue<byte[]> carProp = getProperty(byte[].class, property, 0);
        if (carProp == null) {
            throw new ValueUnavailableException();
        }
        return (byte[]) carProp.getValue();
    }

    public String getStringProperty(int property) {
        CarPropertyValue<String> carProp = getProperty(String.class, property, 0);
        if (carProp == null) {
            throw new ValueUnavailableException();
        }
        return (String) carProp.getValue();
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class Client implements IBinder.DeathRecipient {
        private final boolean isAutopilotClient;
        private final ICarPropertyEventListener mListener;
        private final IBinder mListenerBinder;
        final String processName;
        private final SparseArray<Float> mRateMap = new SparseArray<>();
        final int pid = Binder.getCallingPid();
        final int uid = Binder.getCallingUid();

        static /* synthetic */ IBinder access$000(Client x0) {
            return x0.mListenerBinder;
        }

        Client(ICarPropertyEventListener listener) {
            this.mListener = listener;
            this.mListenerBinder = listener.asBinder();
            this.processName = ProcessUtils.getProcessName(CarPropertyService.this.mContext, this.pid, this.uid);
            this.isAutopilotClient = CarPropertyService.this.isAutopilotApp(this.processName);
            try {
                this.mListenerBinder.linkToDeath(this, 0);
            } catch (RemoteException e) {
                Slog.e(CarPropertyService.TAG, "Failed to link death for recipient. " + e.getMessage());
                throw new IllegalStateException("CarNotConnected");
            }
        }

        void addProperty(int propId, float rate) {
            this.mRateMap.put(propId, Float.valueOf(rate));
            if (this.isAutopilotClient) {
                ((ArraySet) CarPropertyService.this.mAutopilotRegisteredProperties.computeIfAbsent(Integer.valueOf(this.pid), new Function() { // from class: com.android.car.-$$Lambda$CarPropertyService$Client$djwGbPCcOlsH0LgCCyQKsBkx7j4
                    @Override // java.util.function.Function
                    public final Object apply(Object obj) {
                        return CarPropertyService.Client.lambda$addProperty$0((Integer) obj);
                    }
                })).add(Integer.valueOf(propId));
            }
        }

        /* JADX INFO: Access modifiers changed from: package-private */
        public static /* synthetic */ ArraySet lambda$addProperty$0(Integer k) {
            return new ArraySet();
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            ArraySet<Integer> ids;
            Slog.i(CarPropertyService.TAG, "binderDied " + getDescriptionString() + ", binder: " + this.mListenerBinder);
            while (this.mRateMap.size() != 0) {
                int propId = this.mRateMap.keyAt(0);
                synchronized (CarPropertyService.this.mLock) {
                    CarPropertyService.this.unregisterListenerBinderLocked(propId, this.mListenerBinder);
                    if (this.isAutopilotClient && (ids = (ArraySet) CarPropertyService.this.mAutopilotRegisteredProperties.get(Integer.valueOf(this.pid))) != null) {
                        ids.remove(Integer.valueOf(propId));
                        if (ids.isEmpty()) {
                            CarPropertyService.this.mAutopilotRegisteredProperties.remove(Integer.valueOf(this.pid));
                        }
                    }
                }
            }
            CarPropertyService.this.mCallbackStatistics.removeProcess(this.processName, this.pid, this.mListenerBinder);
        }

        ICarPropertyEventListener getListener() {
            return this.mListener;
        }

        IBinder getListenerBinder() {
            return this.mListenerBinder;
        }

        float getRate(int propId) {
            return this.mRateMap.get(propId, Float.valueOf(0.0f)).floatValue();
        }

        void release() {
            this.mListenerBinder.unlinkToDeath(this, 0);
            CarPropertyService.this.mClientMap.remove(this.mListenerBinder);
        }

        void removeProperty(int propId) {
            this.mRateMap.remove(propId);
            if (this.mRateMap.size() == 0) {
                release();
            }
        }

        public void dump(PrintWriter writer) {
            StringBuilder sb = new StringBuilder();
            sb.append("        ");
            sb.append(this.processName);
            sb.append("(Pid:");
            sb.append(this.pid);
            sb.append(") mListenerBinder:");
            sb.append(this.mListenerBinder);
            sb.append(" mRateMap:");
            StringBuilder builder = sb.append(rateMapToString());
            writer.println(builder.toString());
        }

        public String getDescriptionString() {
            return "Client(" + this.processName + ":" + this.pid + ", " + this.mListenerBinder + ")";
        }

        private String rateMapToString() {
            synchronized (CarPropertyService.this.mLock) {
                int size = this.mRateMap.size();
                if (size <= 0) {
                    return "{}";
                }
                StringBuilder buffer = new StringBuilder(size * 48);
                buffer.append('{');
                for (int i = 0; i < size; i++) {
                    if (i > 0) {
                        buffer.append(", ");
                    }
                    int key = this.mRateMap.keyAt(i);
                    buffer.append(XpDebugLog.getPropertyName(key));
                    buffer.append(" : ");
                    buffer.append(this.mRateMap.valueAt(i));
                }
                buffer.append('}');
                return buffer.toString();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes3.dex */
    public class ScuDeathRecipient implements IBinder.DeathRecipient {
        private static final String TAG = "ScuDeathRecipient";
        private IBinder mListenerBinder;

        ScuDeathRecipient(IBinder listenerBinder) {
            this.mListenerBinder = listenerBinder;
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            Slog.i(TAG, "binderDied " + this.mListenerBinder);
            CarPropertyService.this.unregisterScuListenerLocked(this.mListenerBinder);
        }

        void release() {
            this.mListenerBinder.unlinkToDeath(this, 0);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes3.dex */
    public class VcuDeathRecipient implements IBinder.DeathRecipient {
        private static final String TAG = "VcuDeathRecipient";
        private IBinder mListenerBinder;

        VcuDeathRecipient(IBinder listenerBinder) {
            this.mListenerBinder = listenerBinder;
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            Slog.i(TAG, "binderDied " + this.mListenerBinder);
            CarPropertyService.this.unregisterVcuListenerLocked(this.mListenerBinder);
        }

        void release() {
            this.mListenerBinder.unlinkToDeath(this, 0);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes3.dex */
    public class EpsDeathRecipient implements IBinder.DeathRecipient {
        private static final String TAG = "EpsDeathRecipient";
        private IBinder mListenerBinder;

        EpsDeathRecipient(IBinder listenerBinder) {
            this.mListenerBinder = listenerBinder;
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            Slog.i(TAG, "binderDied " + this.mListenerBinder);
            CarPropertyService.this.unregisterEpsListenerLocked(this.mListenerBinder);
        }

        void release() {
            this.mListenerBinder.unlinkToDeath(this, 0);
        }
    }
}
