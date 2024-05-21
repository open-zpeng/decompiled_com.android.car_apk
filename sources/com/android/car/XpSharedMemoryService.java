package com.android.car;

import android.car.XpDebugLog;
import android.car.hardware.CarPropertyConfig;
import android.car.xpsharedmemory.ISharedMemoryDataListener;
import android.car.xpsharedmemory.IXpSharedMemory;
import android.content.Context;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.os.Binder;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.ArraySet;
import android.util.Slog;
import com.android.car.Manifest;
import com.android.car.XpSharedMemoryService;
import com.android.car.hal.PropertyHalService;
import com.android.car.hal.VehicleHal;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
/* loaded from: classes3.dex */
public class XpSharedMemoryService extends IXpSharedMemory.Stub implements CarServiceBase {
    private static final boolean DEBUG = true;
    private static final Duration MAX_INTERVAL_MS = Duration.ofMillis(40);
    private static final String TAG = "XpSharedMemoryService";
    private volatile Map<Integer, CarPropertyConfig<?>> mConfigs;
    private final Context mContext;
    private final VehicleHal mHal;
    private final Map<IBinder, Client> mClientMap = new ConcurrentHashMap();
    private final Object mLock = new Object();
    private final Map<Integer, List<Client>> mPropIdClientMap = new ConcurrentHashMap();
    private final ArraySet<Integer> mProperties = new ArraySet<>(Arrays.asList(Integer.valueOf((int) VehicleProperty.XPU_SR_PK_PERIOD_DATA), Integer.valueOf((int) VehicleProperty.XPU_SR_PK_EVENT_DATA), Integer.valueOf((int) VehicleProperty.XPU_SR_RD_PERIOD_DATA), Integer.valueOf((int) VehicleProperty.XPU_SR_RD_EVENT_DATA), Integer.valueOf((int) VehicleProperty.XPU_HD_MAP_DATA), Integer.valueOf((int) VehicleProperty.XPU_NGP_TRAJECTOY_DATA), Integer.valueOf((int) VehicleProperty.XPU_CAMERA_DATA), Integer.valueOf((int) VehicleProperty.XPU_HD_MAP_PERIOD_DATA), Integer.valueOf((int) VehicleProperty.ICM_NAVIGATION), Integer.valueOf((int) VehicleProperty.XPU_TRANSFER_MAP_DATA), Integer.valueOf((int) VehicleProperty.XPU_NAVI_ROUTING_INFO), Integer.valueOf((int) VehicleProperty.HOST_ICM_SD_PERIOD_DATA)));
    private final CallbackStatistics mCallbackStatistics = new CallbackStatistics(TAG, true);
    private final int mMyPid = this.mCallbackStatistics.getMyPid();

    private native void addListener(int i);

    public static native void initWithMmapSize(long j);

    private native void nativeDestroy();

    private native void nativeInit();

    private native void nativeQueueBuffer(int i, int i2, int i3);

    /* JADX INFO: Access modifiers changed from: private */
    public native void removeListener(int i);

    static {
        System.loadLibrary("jni_xpsharedmemory");
    }

    public XpSharedMemoryService(Context context, VehicleHal hal) {
        this.mContext = context;
        this.mHal = hal;
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
        Slog.i(TAG, "start to call native...");
        nativeInit();
    }

    @Override // com.android.car.CarServiceBase
    public void release() {
        nativeDestroy();
        for (Client c : this.mClientMap.values()) {
            c.release();
        }
        this.mClientMap.clear();
        this.mCallbackStatistics.release();
        for (List<Client> c2 : this.mPropIdClientMap.values()) {
            c2.clear();
        }
        this.mPropIdClientMap.clear();
    }

    @Override // com.android.car.CarServiceBase
    public void dump(final PrintWriter writer) {
        writer.println("**dump XpSharedMemoryService**");
        writer.println("    Client Info: " + this.mClientMap.size());
        this.mClientMap.values().stream().sorted(Comparator.comparingInt(new ToIntFunction() { // from class: com.android.car.-$$Lambda$XpSharedMemoryService$a8e4swX8CehlWWZHubCtixN-epk
            @Override // java.util.function.ToIntFunction
            public final int applyAsInt(Object obj) {
                int i;
                i = ((XpSharedMemoryService.Client) obj).pid;
                return i;
            }
        })).filter(new Predicate() { // from class: com.android.car.-$$Lambda$XpSharedMemoryService$4Frx6uttZJHCk2zLNHU5umnxMuk
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return XpSharedMemoryService.this.lambda$dump$1$XpSharedMemoryService((XpSharedMemoryService.Client) obj);
            }
        }).forEach(new Consumer() { // from class: com.android.car.-$$Lambda$XpSharedMemoryService$6-72u8eaUaGkB4pq1oPQIqWR__w
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                ((XpSharedMemoryService.Client) obj).dump(writer);
            }
        });
        writer.println("    mPropertyToClientsMap: " + this.mPropIdClientMap.size());
        this.mPropIdClientMap.entrySet().stream().sorted(Comparator.comparing(new Function() { // from class: com.android.car.-$$Lambda$XpSharedMemoryService$6q2JJugO3Dyk5bimeQSL9PSHdqs
            @Override // java.util.function.Function
            public final Object apply(Object obj) {
                Integer valueOf;
                valueOf = Integer.valueOf(((List) ((Map.Entry) obj).getValue()).size());
                return valueOf;
            }
        }, Comparator.reverseOrder())).forEach(new Consumer() { // from class: com.android.car.-$$Lambda$XpSharedMemoryService$TZp-n4xL2ove6lIt8037gzyVbY0
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                XpSharedMemoryService.this.lambda$dump$6$XpSharedMemoryService(writer, (Map.Entry) obj);
            }
        });
        this.mCallbackStatistics.dump(writer);
    }

    public /* synthetic */ boolean lambda$dump$1$XpSharedMemoryService(Client v) {
        return v.pid != this.mMyPid;
    }

    public /* synthetic */ void lambda$dump$6$XpSharedMemoryService(final PrintWriter writer, Map.Entry cs) {
        List<Client> clients = (List) cs.getValue();
        int prop = ((Integer) cs.getKey()).intValue();
        writer.println("        " + XpDebugLog.getPropertyDescription(prop) + " Listeners size: " + clients.size());
        clients.stream().filter(new Predicate() { // from class: com.android.car.-$$Lambda$XpSharedMemoryService$UyxktOfSCv944N4baZKzonSU2VU
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return XpSharedMemoryService.this.lambda$dump$4$XpSharedMemoryService((XpSharedMemoryService.Client) obj);
            }
        }).forEach(new Consumer() { // from class: com.android.car.-$$Lambda$XpSharedMemoryService$CZMbZiX2BvrirCXdaRthAkHUHSs
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                XpSharedMemoryService.Client client = (XpSharedMemoryService.Client) obj;
                writer.println("            pid: " + client.pid + "(" + client.processName + "), mListenerBinder: " + client.mListenerBinder);
            }
        });
    }

    public /* synthetic */ boolean lambda$dump$4$XpSharedMemoryService(Client v) {
        return v.pid != this.mMyPid;
    }

    public CarPropertyConfig[] getPropertyArray() {
        List<CarPropertyConfig> returnList = new ArrayList<>();
        PropertyHalService propertyHal = this.mHal.getPropertyHal();
        if (this.mConfigs == null) {
            synchronized (this.mLock) {
                if (this.mConfigs == null) {
                    Map<Integer, CarPropertyConfig<?>> configTemp = new HashMap<>();
                    Map<Integer, CarPropertyConfig<?>> configs = propertyHal.getPropertyList();
                    Iterator<Integer> it = this.mProperties.iterator();
                    while (it.hasNext()) {
                        Integer propertyId = it.next();
                        if (configs.containsKey(propertyId)) {
                            configTemp.put(propertyId, configs.get(propertyId));
                        }
                    }
                    this.mConfigs = configTemp;
                }
            }
        }
        for (CarPropertyConfig c : this.mConfigs.values()) {
            if (ICarImpl.hasPermission(this.mContext, propertyHal.getReadPermission(c.getPropertyId()))) {
                returnList.add(c);
            }
        }
        return (CarPropertyConfig[]) returnList.toArray(new CarPropertyConfig[0]);
    }

    public void queueMemoryBuffer(int prop, ParcelFileDescriptor pfd, int size) {
        ICarImpl.assertPermission(this.mContext, Manifest.permission.CAR_VENDOR_EXTENSION);
        if (pfd == null || size <= 0) {
            Slog.e(TAG, "invalid pfd or size");
            throw new IllegalArgumentException("Unexpected pfd or size");
        }
        int fd = pfd.getFileDescriptor().getInt$();
        CarLog.d(TAG, "queueMemoryBuffer fd=" + fd + ", size=" + size);
        try {
            CarLog.d(TAG, "native transfer begin");
            nativeQueueBuffer(prop, fd, size);
            CarLog.d(TAG, "native transfer end");
        } catch (Exception e) {
            Slog.e(TAG, "Cannot queue the buffer: " + e.toString());
        }
        try {
            pfd.close();
        } catch (IOException e2) {
            Slog.e(TAG, "cannot close the pfd: " + e2.toString());
        }
        CarLog.d(TAG, "end");
    }

    /* JADX WARN: Removed duplicated region for block: B:74:0x0231 A[Catch: all -> 0x02bc, TRY_ENTER, TryCatch #14 {all -> 0x02bc, blocks: (B:56:0x0187, B:58:0x0191, B:62:0x01fc, B:63:0x020a, B:60:0x01c7, B:74:0x0231, B:75:0x0260, B:84:0x02a8), top: B:132:0x00de }] */
    /* JADX WARN: Removed duplicated region for block: B:75:0x0260 A[Catch: all -> 0x02bc, TryCatch #14 {all -> 0x02bc, blocks: (B:56:0x0187, B:58:0x0191, B:62:0x01fc, B:63:0x020a, B:60:0x01c7, B:74:0x0231, B:75:0x0260, B:84:0x02a8), top: B:132:0x00de }] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public void onDataCallback(long r27, int r29, int r30, int r31) {
        /*
            Method dump skipped, instructions count: 758
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.car.XpSharedMemoryService.onDataCallback(long, int, int, int):void");
    }

    public void registerListener(int propId, ISharedMemoryDataListener listener) {
        int pid = Binder.getCallingPid();
        int uid = Binder.getCallingUid();
        if (listener == null) {
            Slog.e(TAG, "Client (uid:" + uid + ", pid: " + pid + ") registerListener: Listener is null.");
            throw new IllegalArgumentException("listener cannot be null.");
        } else if (this.mConfigs == null || this.mConfigs.get(Integer.valueOf(propId)) == null) {
            Slog.e(TAG, "Client (uid:" + uid + ", pid: " + pid + ") registerListener: " + XpDebugLog.getPropertyDescription(propId) + " is not in the config list");
        } else {
            Slog.i(TAG, "Client (uid:" + uid + ", pid: " + pid + ") call registerListener: " + XpDebugLog.getPropertyDescription(propId) + " listener:" + listener.asBinder());
            ICarImpl.assertPermission(this.mContext, this.mHal.getPropertyHal().getReadPermission(propId));
            IBinder listenerBinder = listener.asBinder();
            boolean shouldAddListener = false;
            synchronized (this.mLock) {
                Client client = this.mClientMap.get(listenerBinder);
                if (client == null) {
                    client = new Client(listener);
                }
                client.addProperty(propId);
                List<Client> clients = this.mPropIdClientMap.get(Integer.valueOf(propId));
                if (clients == null) {
                    clients = new CopyOnWriteArrayList();
                    this.mPropIdClientMap.put(Integer.valueOf(propId), clients);
                    shouldAddListener = true;
                }
                if (!clients.contains(client)) {
                    clients.add(client);
                }
            }
            if (shouldAddListener) {
                Slog.i(TAG, "native register Listener for " + XpDebugLog.getPropertyDescription(propId));
                addListener(propId);
            }
        }
    }

    public void unregisterListener(int propId, ISharedMemoryDataListener listener) {
        boolean shouldRemove;
        if (listener == null) {
            int pid = Binder.getCallingPid();
            int uid = Binder.getCallingUid();
            Slog.e(TAG, "Client (uid:" + uid + ", pid: " + pid + ") unregisterListener: Listener is null.");
            throw new IllegalArgumentException("Listener is null");
        }
        ICarImpl.assertPermission(this.mContext, this.mHal.getPropertyHal().getReadPermission(propId));
        IBinder listenerBinder = listener.asBinder();
        synchronized (this.mLock) {
            shouldRemove = unregisterListenerBinderLocked(propId, listenerBinder);
        }
        if (shouldRemove) {
            Slog.i(TAG, "native unregister Listener for " + XpDebugLog.getPropertyDescription(propId));
            removeListener(propId);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean unregisterListenerBinderLocked(int propId, IBinder listenerBinder) {
        Client client = this.mClientMap.get(listenerBinder);
        List<Client> propertyClients = this.mPropIdClientMap.get(Integer.valueOf(propId));
        int pid = client == null ? Binder.getCallingPid() : client.pid;
        int uid = client == null ? Binder.getCallingUid() : client.uid;
        if (this.mConfigs == null || this.mConfigs.get(Integer.valueOf(propId)) == null) {
            Slog.e(TAG, "Client (uid:" + uid + ", pid: " + pid + ") unregistering " + XpDebugLog.getPropertyDescription(propId) + " is not in the config list");
            return false;
        } else if (client == null || propertyClients == null) {
            Slog.e(TAG, "Client (uid:" + uid + ", pid: " + pid + ") unregistering listener was not previously registered.");
            return false;
        } else if (propertyClients.remove(client)) {
            Slog.i(TAG, "Client (uid:" + uid + ", pid: " + pid + ") call unregisterListener: " + XpDebugLog.getPropertyDescription(propId) + ") listener:" + listenerBinder);
            client.removeProperty(propId);
            if (propertyClients.isEmpty()) {
                this.mPropIdClientMap.remove(Integer.valueOf(propId));
                return true;
            }
            return false;
        } else {
            Slog.e(TAG, "Client (uid:" + uid + ", pid: " + pid + ") unregistering listener was not registered for " + XpDebugLog.getPropertyDescription(propId));
            return false;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class Client implements IBinder.DeathRecipient {
        private final ISharedMemoryDataListener mListener;
        private final IBinder mListenerBinder;
        final String processName;
        private final ArraySet<Integer> mRateMap = new ArraySet<>();
        final int pid = Binder.getCallingPid();
        final int uid = Binder.getCallingUid();

        Client(ISharedMemoryDataListener listener) {
            this.mListener = listener;
            this.mListenerBinder = listener.asBinder();
            this.processName = ProcessUtils.getProcessName(XpSharedMemoryService.this.mContext, this.pid, this.uid);
            try {
                this.mListenerBinder.linkToDeath(this, 0);
                XpSharedMemoryService.this.mClientMap.put(this.mListenerBinder, this);
            } catch (RemoteException e) {
                Slog.e(XpSharedMemoryService.TAG, "Failed to link death for recipient. " + e.getMessage());
                throw new IllegalStateException("CarNotConnected");
            }
        }

        void addProperty(int propId) {
            this.mRateMap.add(Integer.valueOf(propId));
        }

        ISharedMemoryDataListener getListener() {
            return this.mListener;
        }

        IBinder getListenerBinder() {
            return this.mListenerBinder;
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            boolean shouldRemove;
            Slog.i(XpSharedMemoryService.TAG, "binderDied " + getDescriptionString() + ", binder: " + this.mListenerBinder);
            while (this.mRateMap.size() != 0) {
                int propId = this.mRateMap.valueAt(0).intValue();
                synchronized (XpSharedMemoryService.this.mLock) {
                    shouldRemove = XpSharedMemoryService.this.unregisterListenerBinderLocked(propId, this.mListenerBinder);
                }
                if (shouldRemove) {
                    Slog.i(XpSharedMemoryService.TAG, "native unregister Listener for " + XpDebugLog.getPropertyDescription(propId));
                    XpSharedMemoryService.this.removeListener(propId);
                }
            }
            XpSharedMemoryService.this.mCallbackStatistics.removeProcess(this.processName, this.pid, this.mListenerBinder);
        }

        void release() {
            this.mListenerBinder.unlinkToDeath(this, 0);
            XpSharedMemoryService.this.mClientMap.remove(this.mListenerBinder);
        }

        void removeProperty(int propId) {
            this.mRateMap.remove(Integer.valueOf(propId));
            if (this.mRateMap.size() == 0) {
                release();
            }
        }

        public void dump(PrintWriter writer) {
            String builder = "        " + this.processName + "(Pid:" + this.pid + ") mListenerBinder:" + this.mListenerBinder + " mRateMap:" + rateMapToString();
            writer.println(builder);
        }

        private String rateMapToString() {
            synchronized (XpSharedMemoryService.this.mLock) {
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
                    int key = this.mRateMap.valueAt(i).intValue();
                    buffer.append(XpDebugLog.getPropertyName(key));
                }
                buffer.append('}');
                return buffer.toString();
            }
        }

        public String getDescriptionString() {
            return "Client(" + this.processName + ":" + this.pid + ", " + this.mListenerBinder + ")";
        }
    }
}
