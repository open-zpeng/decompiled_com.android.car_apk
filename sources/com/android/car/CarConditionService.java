package com.android.car;

import android.annotation.TargetApi;
import android.car.XpDebugLog;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.condition.CarConditionInfo;
import android.car.hardware.condition.ICarCondition;
import android.car.hardware.condition.ICarConditionEventListener;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.ICarPropertyEventListener;
import android.content.Context;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.ArraySet;
import android.util.Log;
import android.util.Slog;
import com.android.car.CarConditionService;
import com.android.car.condition.CarConditionStatus;
import com.android.car.condition.CarConditionUtils;
import com.android.car.hal.PropertyHalService;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
@TargetApi(24)
/* loaded from: classes3.dex */
public class CarConditionService extends ICarCondition.Stub implements CarServiceBase {
    public static final boolean DEBUG = Log.isLoggable(CarLog.TAG_CONDITION, 3);
    private static final String TAG = "CarConditionService";
    private final Context mContext;
    private final PropertyHalService mHal;
    private CarConditionHandler mHandler;
    private HandlerThread mHandlerThread;
    private final CarPropertyService mPropertyService;
    private final Map<Integer, CopyOnWriteArrayList<Client>> mPropIdClientMap = new ConcurrentHashMap();
    private final Map<IBinder, Set<Integer>> mBindIdMap = new ConcurrentHashMap();
    private final Map<IBinder, Client> mClientMap = new ConcurrentHashMap();
    private final Map<Client, CopyOnWriteArrayList<CarConditionStatus>> mConditionMap = new ConcurrentHashMap();
    private final Object mLock = new Object();
    private final ICarPropertyEventListener mCarPropertyEventListener = new ICarPropertyEventListener.Stub() { // from class: com.android.car.CarConditionService.1
        public void onEvent(List<CarPropertyEvent> events) throws RemoteException {
            CarConditionService.this.mHandler.sendHalEvents(events);
        }
    };
    private final CallbackStatistics mCallbackStatistics = new CallbackStatistics(TAG, true);
    private final int mMyPid = this.mCallbackStatistics.getMyPid();

    public CarConditionService(Context context, CarPropertyService service, PropertyHalService hal) {
        this.mPropertyService = service;
        this.mHal = hal;
        this.mContext = context;
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
        synchronized (this) {
            this.mHandlerThread = new HandlerThread(TAG);
            this.mHandlerThread.start();
            this.mHandler = new CarConditionHandler(this, this.mHandlerThread.getLooper());
        }
    }

    @Override // com.android.car.CarServiceBase
    public void release() {
        Handler handler;
        HandlerThread handlerThread;
        for (Client c : this.mClientMap.values()) {
            c.release();
        }
        for (Integer id : this.mPropIdClientMap.keySet()) {
            this.mPropertyService.unregisterListener(id.intValue(), this.mCarPropertyEventListener);
        }
        this.mPropIdClientMap.clear();
        this.mClientMap.clear();
        this.mConditionMap.clear();
        this.mBindIdMap.clear();
        this.mCallbackStatistics.release();
        synchronized (this) {
            handler = this.mHandler;
            handlerThread = this.mHandlerThread;
        }
        handler.removeCallbacksAndMessages(null);
        handlerThread.quitSafely();
        try {
            handlerThread.join(1000L);
        } catch (InterruptedException e) {
            Slog.e(CarLog.TAG_UPDATETIME, "Timeout while joining for handler thread to join.");
        }
    }

    @Override // com.android.car.CarServiceBase
    public void dump(final PrintWriter writer) {
        writer.println("**dump CarConditionService**");
        synchronized (this.mLock) {
            writer.println("  mPropIdClientMap: " + this.mPropIdClientMap.size());
            this.mPropIdClientMap.entrySet().forEach(new Consumer() { // from class: com.android.car.-$$Lambda$CarConditionService$sjkukUkgOLkavHg9812tt0eawTo
                @Override // java.util.function.Consumer
                public final void accept(Object obj) {
                    CarConditionService.this.lambda$dump$2$CarConditionService(writer, (Map.Entry) obj);
                }
            });
            writer.println("  ConditionInfo: " + this.mConditionMap.size());
            this.mConditionMap.entrySet().forEach(new Consumer() { // from class: com.android.car.-$$Lambda$CarConditionService$8RbKPXRzHClipvNyISW3f3Nnkpc
                @Override // java.util.function.Consumer
                public final void accept(Object obj) {
                    CarConditionService.lambda$dump$4(writer, (Map.Entry) obj);
                }
            });
            this.mCallbackStatistics.dump(writer);
        }
    }

    public /* synthetic */ void lambda$dump$2$CarConditionService(final PrintWriter writer, Map.Entry cs) {
        List<Client> clients = (List) cs.getValue();
        int prop = ((Integer) cs.getKey()).intValue();
        writer.println("        " + XpDebugLog.getPropertyDescription(prop) + ", clients size: " + clients.size());
        clients.stream().filter(new Predicate() { // from class: com.android.car.-$$Lambda$CarConditionService$DoCUFpnJp2SGQ37UFvmbZ1Jj_0M
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return CarConditionService.this.lambda$dump$0$CarConditionService((CarConditionService.Client) obj);
            }
        }).forEach(new Consumer() { // from class: com.android.car.-$$Lambda$CarConditionService$4jwYjOVcD1M6Xg4fecAvPf2ASwY
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                CarConditionService.Client client = (CarConditionService.Client) obj;
                writer.println("           pid: " + client.pid + "(" + client.processName + "), mCallbackBinder: " + client.mCallbackBinder);
            }
        });
    }

    public /* synthetic */ boolean lambda$dump$0$CarConditionService(Client v) {
        return v.pid != this.mMyPid;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ void lambda$dump$4(final PrintWriter writer, Map.Entry cs) {
        Client client = (Client) cs.getKey();
        List<CarConditionStatus> list = (List) cs.getValue();
        writer.println("        " + client.processName + "(" + client.pid + ", condition size: " + list.size() + ")");
        list.forEach(new Consumer() { // from class: com.android.car.-$$Lambda$CarConditionService$ayFoetcNvgb3A_U4WksFGq-8jbI
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                CarConditionStatus carConditionStatus = (CarConditionStatus) obj;
                writer.println("            " + carConditionStatus.toString());
            }
        });
    }

    public void registerCondition(List propId, CarConditionInfo condition, ICarConditionEventListener listener) throws RemoteException {
        Client client;
        int uid = Binder.getCallingUid();
        int pid = Binder.getCallingPid();
        if (listener == null) {
            Slog.e(CarLog.TAG_CONDITION, "Client (uid:" + uid + ", pid: " + pid + ") registerCondition: Listener is null!");
            throw new IllegalArgumentException("listener cannot be null.");
        } else if (condition == null) {
            Slog.e(CarLog.TAG_CONDITION, "Client (uid:" + uid + ", pid: " + pid + ") registerCondition: condition is null!");
            throw new IllegalArgumentException("condition cannot be null.");
        } else if (propId == null || propId.isEmpty()) {
            Slog.e(CarLog.TAG_CONDITION, "Client (uid:" + uid + ", pid: " + pid + ") registerCondition: list is invalid!");
            throw new IllegalArgumentException("list is invalid");
        } else {
            IBinder listenerBinder = listener.asBinder();
            synchronized (this.mLock) {
                Client client2 = this.mClientMap.get(listenerBinder);
                if (client2 == null) {
                    client = new Client(listener);
                } else {
                    client = client2;
                }
                CarConditionStatus conditionStatus = CarConditionStatus.createFromCarConditionInfo(condition, client.processName);
                Set<Integer> propIdSet = this.mBindIdMap.get(listenerBinder);
                if (propIdSet == null) {
                    propIdSet = new ArraySet();
                    this.mBindIdMap.put(listenerBinder, propIdSet);
                }
                propIdSet.addAll(propId);
                CopyOnWriteArrayList<CarConditionStatus> conditionStatusList = this.mConditionMap.get(client);
                if (conditionStatusList == null) {
                    conditionStatusList = new CopyOnWriteArrayList<>();
                    this.mConditionMap.put(client, conditionStatusList);
                }
                if (!conditionStatusList.contains(conditionStatus)) {
                    conditionStatusList.add(conditionStatus);
                }
            }
            Iterator it = propId.iterator();
            while (it.hasNext()) {
                int id = ((Integer) it.next()).intValue();
                boolean register = false;
                synchronized (this.mLock) {
                    CopyOnWriteArrayList<Client> clients = this.mPropIdClientMap.get(Integer.valueOf(id));
                    if (clients == null) {
                        clients = new CopyOnWriteArrayList<>();
                        this.mPropIdClientMap.put(Integer.valueOf(id), clients);
                        register = true;
                    }
                    if (!clients.contains(client)) {
                        clients.add(client);
                    }
                }
                if (register) {
                    this.mPropertyService.registerListener(id, 0.0f, this.mCarPropertyEventListener);
                } else {
                    List<CarPropertyEvent> events = new LinkedList<>();
                    try {
                        CarPropertyValue value = this.mHal.getProperty(id, 0, false);
                        if (value != null) {
                            CarPropertyEvent event = new CarPropertyEvent(0, value);
                            events.add(event);
                        }
                    } catch (Exception e) {
                        if (CarLog.isGetLogEnable()) {
                            Slog.e(CarLog.TAG_CONDITION, "get prop data failed, won't listener");
                        }
                    }
                    if (!events.isEmpty()) {
                        this.mHandler.sendHalEvents(events);
                    }
                }
            }
            Slog.i(TAG, "registerCondition: listenerBinder=" + listenerBinder + " client.size=" + this.mConditionMap.size());
        }
    }

    public void unregisterCondition(ICarConditionEventListener listener) throws RemoteException {
        if (listener == null) {
            int pid = Binder.getCallingPid();
            int uid = Binder.getCallingUid();
            Slog.e(CarLog.TAG_CONDITION, "Client (uid:" + uid + ", pid: " + pid + ") unregisterListener: listener is null.");
            throw new IllegalArgumentException("listener is null");
        }
        IBinder callBinder = listener.asBinder();
        synchronized (this.mLock) {
            unregisterConditionBinderLocked(callBinder);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void unregisterConditionBinderLocked(IBinder callBinder) {
        Set<Integer> propIdSet = this.mBindIdMap.remove(callBinder);
        Client client = this.mClientMap.get(callBinder);
        if (client == null) {
            Slog.e(CarLog.TAG_CONDITION, "unregisterConditionBinderLocked: client was not previously registered.");
            return;
        }
        if (propIdSet != null) {
            for (Integer propId : propIdSet) {
                List<Client> propertyClients = this.mPropIdClientMap.get(propId);
                if (propertyClients == null) {
                    Slog.e(CarLog.TAG_CONDITION, "unregisterListenerBinderLocked: propertyClients was not previously registered.");
                } else {
                    propertyClients.remove(client);
                    if (propertyClients.isEmpty()) {
                        this.mPropertyService.unregisterListener(propId.intValue(), this.mCarPropertyEventListener);
                        this.mPropIdClientMap.remove(propId);
                    }
                }
            }
        }
        client.release();
        this.mConditionMap.remove(client);
        this.mCallbackStatistics.removeProcess(client.processName, client.pid, callBinder);
        Slog.i(TAG, "unregisterConditionBinderLocked: callBinder=" + callBinder + " client.size=" + this.mConditionMap.size());
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class Client implements IBinder.DeathRecipient {
        final IBinder mCallbackBinder;
        final ICarConditionEventListener mListener;
        final String processName;
        final int pid = Binder.getCallingPid();
        final int uid = Binder.getCallingUid();

        public Client(ICarConditionEventListener listener) {
            this.mListener = listener;
            this.mCallbackBinder = listener.asBinder();
            this.processName = ProcessUtils.getProcessName(CarConditionService.this.mContext, this.pid, this.uid);
            try {
                this.mCallbackBinder.linkToDeath(this, 0);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            CarConditionService.this.mClientMap.put(this.mCallbackBinder, this);
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            synchronized (CarConditionService.this.mLock) {
                CarConditionService.this.unregisterConditionBinderLocked(this.mCallbackBinder);
            }
        }

        public void release() {
            this.mCallbackBinder.unlinkToDeath(this, 0);
            CarConditionService.this.mClientMap.remove(this.mCallbackBinder);
        }

        ICarConditionEventListener getListener() {
            return this.mListener;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void dispatchCarLimitEvent(int propId, CarPropertyValue value) {
        Set<Map.Entry<Client, CopyOnWriteArrayList<CarConditionStatus>>> keySet = this.mConditionMap.entrySet();
        for (Map.Entry<Client, CopyOnWriteArrayList<CarConditionStatus>> entry : keySet) {
            List<CarConditionStatus> statusList = entry.getValue();
            Client client = entry.getKey();
            if (statusList != null) {
                for (CarConditionStatus status : statusList) {
                    if (CarConditionUtils.isConditionLimitSatisfied(propId, status, value)) {
                        ICarConditionEventListener listener = entry.getKey().getListener();
                        try {
                            listener.onChangeEvent(value);
                            this.mCallbackStatistics.addPropMethodCallCount(value.getPropertyId(), client.processName, client.pid, client.mCallbackBinder);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    /* loaded from: classes3.dex */
    private static final class CarConditionHandler extends Handler {
        private static final int MSG_HAL_EVENTS = 1;
        private WeakReference<CarConditionService> mService;

        public CarConditionHandler(CarConditionService service, Looper looper) {
            super(looper);
            this.mService = new WeakReference<>(service);
        }

        void sendHalEvents(List<CarPropertyEvent> events) {
            Message msg = obtainMessage(1, events);
            sendMessage(msg);
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == 1) {
                try {
                    handleHalEvents(msg);
                } catch (Exception e) {
                    Slog.e(CarLog.TAG_CONDITION, "handle vhal events with exception: " + e);
                }
            }
        }

        private void handleHalEvents(Message msg) {
            List<CarPropertyEvent> events = (List) msg.obj;
            if (events != null && !events.isEmpty()) {
                final CarConditionService srv = this.mService.get();
                if (srv == null) {
                    Slog.e(CarLog.TAG_CONDITION, "CarConditionService is null");
                } else {
                    events.stream().filter(new Predicate() { // from class: com.android.car.-$$Lambda$CarConditionService$CarConditionHandler$tP5soSiTfEFtME45HhFDCiBuoS0
                        @Override // java.util.function.Predicate
                        public final boolean test(Object obj) {
                            return CarConditionService.CarConditionHandler.lambda$handleHalEvents$0((CarPropertyEvent) obj);
                        }
                    }).map($$Lambda$o3zP4Oj56DnL7t27aVv1kJbnwAk.INSTANCE).filter(new Predicate() { // from class: com.android.car.-$$Lambda$CarConditionService$CarConditionHandler$SrumNRm0UFtjlCxNO_Y4dv8tziI
                        @Override // java.util.function.Predicate
                        public final boolean test(Object obj) {
                            boolean nonNull;
                            nonNull = Objects.nonNull(((CarPropertyValue) obj).getValue());
                            return nonNull;
                        }
                    }).forEach(new Consumer() { // from class: com.android.car.-$$Lambda$CarConditionService$CarConditionHandler$SpgO1lyyyCxDvWFgWBPNWniwyE4
                        @Override // java.util.function.Consumer
                        public final void accept(Object obj) {
                            CarConditionService.CarConditionHandler.lambda$handleHalEvents$2(CarConditionService.this, (CarPropertyValue) obj);
                        }
                    });
                }
            }
        }

        /* JADX INFO: Access modifiers changed from: package-private */
        public static /* synthetic */ boolean lambda$handleHalEvents$0(CarPropertyEvent v) {
            return v.getEventType() == 0;
        }

        /* JADX INFO: Access modifiers changed from: package-private */
        public static /* synthetic */ void lambda$handleHalEvents$2(CarConditionService srv, CarPropertyValue v) {
            int propertyId = v.getPropertyId();
            srv.dispatchCarLimitEvent(propertyId, v);
        }
    }
}
