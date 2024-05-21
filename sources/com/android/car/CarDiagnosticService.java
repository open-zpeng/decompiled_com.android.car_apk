package com.android.car;

import android.car.diagnostic.CarDiagnosticEvent;
import android.car.diagnostic.ICarDiagnostic;
import android.car.diagnostic.ICarDiagnosticEventListener;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Slog;
import com.android.car.CarDiagnosticService;
import com.android.car.Listeners;
import com.android.car.Manifest;
import com.android.car.hal.DiagnosticHalService;
import com.android.car.internal.CarPermission;
import com.android.internal.annotations.GuardedBy;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
/* loaded from: classes3.dex */
public class CarDiagnosticService extends ICarDiagnostic.Stub implements CarServiceBase, DiagnosticHalService.DiagnosticListener {
    private final Context mContext;
    private final CarPermission mDiagnosticClearPermission;
    private final DiagnosticHalService mDiagnosticHal;
    private final CarPermission mDiagnosticReadPermission;
    private final ReentrantLock mDiagnosticLock = new ReentrantLock();
    @GuardedBy({"mDiagnosticLock"})
    private final LinkedList<DiagnosticClient> mClients = new LinkedList<>();
    @GuardedBy({"mDiagnosticLock"})
    private final HashMap<Integer, Listeners<DiagnosticClient>> mDiagnosticListeners = new HashMap<>();
    @GuardedBy({"mDiagnosticLock"})
    private final LiveFrameRecord mLiveFrameDiagnosticRecord = new LiveFrameRecord(this.mDiagnosticLock);
    @GuardedBy({"mDiagnosticLock"})
    private final FreezeFrameRecord mFreezeFrameDiagnosticRecords = new FreezeFrameRecord(this.mDiagnosticLock);

    public CarDiagnosticService(Context context, DiagnosticHalService diagnosticHal) {
        this.mContext = context;
        this.mDiagnosticHal = diagnosticHal;
        this.mDiagnosticReadPermission = new CarPermission(this.mContext, Manifest.permission.CAR_DIAGNOSTICS);
        this.mDiagnosticClearPermission = new CarPermission(this.mContext, Manifest.permission.CLEAR_CAR_DIAGNOSTICS);
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
        this.mDiagnosticLock.lock();
        try {
            this.mDiagnosticHal.setDiagnosticListener(this);
            setInitialLiveFrame();
            setInitialFreezeFrames();
        } finally {
            this.mDiagnosticLock.unlock();
        }
    }

    private CarDiagnosticEvent setInitialLiveFrame() {
        if (!this.mDiagnosticHal.getDiagnosticCapabilities().isLiveFrameSupported()) {
            return null;
        }
        CarDiagnosticEvent liveFrame = setRecentmostLiveFrame(this.mDiagnosticHal.getCurrentLiveFrame());
        return liveFrame;
    }

    private void setInitialFreezeFrames() {
        long[] timestamps;
        if (this.mDiagnosticHal.getDiagnosticCapabilities().isFreezeFrameSupported() && this.mDiagnosticHal.getDiagnosticCapabilities().isFreezeFrameInfoSupported() && (timestamps = this.mDiagnosticHal.getFreezeFrameTimestamps()) != null) {
            for (long timestamp : timestamps) {
                setRecentmostFreezeFrame(this.mDiagnosticHal.getFreezeFrame(timestamp));
            }
        }
    }

    private CarDiagnosticEvent setRecentmostLiveFrame(CarDiagnosticEvent event) {
        if (event != null) {
            return this.mLiveFrameDiagnosticRecord.update(event.checkLiveFrame());
        }
        return null;
    }

    private CarDiagnosticEvent setRecentmostFreezeFrame(CarDiagnosticEvent event) {
        if (event != null) {
            return this.mFreezeFrameDiagnosticRecords.update(event.checkFreezeFrame());
        }
        return null;
    }

    @Override // com.android.car.CarServiceBase
    public void release() {
        this.mDiagnosticLock.lock();
        try {
            this.mDiagnosticListeners.forEach(new BiConsumer() { // from class: com.android.car.-$$Lambda$CarDiagnosticService$RWaSYphwlmCsrO3T7AJmx5iJvgE
                @Override // java.util.function.BiConsumer
                public final void accept(Object obj, Object obj2) {
                    Integer num = (Integer) obj;
                    ((Listeners) obj2).release();
                }
            });
            this.mDiagnosticListeners.clear();
            this.mLiveFrameDiagnosticRecord.disableIfNeeded();
            this.mFreezeFrameDiagnosticRecords.disableIfNeeded();
            this.mClients.clear();
        } finally {
            this.mDiagnosticLock.unlock();
        }
    }

    private void processDiagnosticData(List<CarDiagnosticEvent> events) {
        Listeners<DiagnosticClient> listeners;
        ArrayMap<DiagnosticClient, List<CarDiagnosticEvent>> eventsByClient = new ArrayMap<>();
        this.mDiagnosticLock.lock();
        try {
            for (CarDiagnosticEvent event : events) {
                if (event.isLiveFrame()) {
                    setRecentmostLiveFrame(event);
                    listeners = this.mDiagnosticListeners.get(0);
                } else if (event.isFreezeFrame()) {
                    setRecentmostFreezeFrame(event);
                    listeners = this.mDiagnosticListeners.get(1);
                } else {
                    Slog.w(CarLog.TAG_DIAGNOSTIC, String.format("received unknown diagnostic event: %s", event));
                }
                if (listeners != null) {
                    for (Listeners.ClientWithRate<DiagnosticClient> clientWithRate : listeners.getClients()) {
                        DiagnosticClient client = clientWithRate.getClient();
                        List<CarDiagnosticEvent> clientEvents = eventsByClient.computeIfAbsent(client, new Function() { // from class: com.android.car.-$$Lambda$CarDiagnosticService$K3aBd1oODaMxNDR94wAy8IBGg9g
                            @Override // java.util.function.Function
                            public final Object apply(Object obj) {
                                return CarDiagnosticService.lambda$processDiagnosticData$1((CarDiagnosticService.DiagnosticClient) obj);
                            }
                        });
                        clientEvents.add(event);
                    }
                }
            }
            this.mDiagnosticLock.unlock();
            for (Map.Entry<DiagnosticClient, List<CarDiagnosticEvent>> entry : eventsByClient.entrySet()) {
                DiagnosticClient client2 = entry.getKey();
                List<CarDiagnosticEvent> clientEvents2 = entry.getValue();
                client2.dispatchDiagnosticUpdate(clientEvents2);
            }
        } catch (Throwable th) {
            this.mDiagnosticLock.unlock();
            throw th;
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ List lambda$processDiagnosticData$1(DiagnosticClient diagnosticClient) {
        return new LinkedList();
    }

    @Override // com.android.car.hal.DiagnosticHalService.DiagnosticListener
    public void onDiagnosticEvents(List<CarDiagnosticEvent> events) {
        processDiagnosticData(events);
    }

    public boolean registerOrUpdateDiagnosticListener(int frameType, int rate, ICarDiagnosticEventListener listener) {
        boolean shouldStartDiagnostics = false;
        Integer oldRate = null;
        this.mDiagnosticLock.lock();
        try {
            this.mDiagnosticReadPermission.assertGranted();
            DiagnosticClient diagnosticClient = findDiagnosticClientLocked(listener);
            Listeners.ClientWithRate<DiagnosticClient> diagnosticClientWithRate = null;
            if (diagnosticClient == null) {
                diagnosticClient = new DiagnosticClient(listener);
                listener.asBinder().linkToDeath(diagnosticClient, 0);
                this.mClients.add(diagnosticClient);
            }
            Listeners<DiagnosticClient> diagnosticListeners = this.mDiagnosticListeners.get(Integer.valueOf(frameType));
            if (diagnosticListeners == null) {
                diagnosticListeners = new Listeners<>(rate);
                this.mDiagnosticListeners.put(Integer.valueOf(frameType), diagnosticListeners);
                shouldStartDiagnostics = true;
            } else {
                oldRate = Integer.valueOf(diagnosticListeners.getRate());
                diagnosticClientWithRate = diagnosticListeners.findClientWithRate(diagnosticClient);
            }
            if (diagnosticClientWithRate == null) {
                Listeners.ClientWithRate<DiagnosticClient> diagnosticClientWithRate2 = new Listeners.ClientWithRate<>(diagnosticClient, rate);
                diagnosticListeners.addClientWithRate(diagnosticClientWithRate2);
            } else {
                diagnosticClientWithRate.setRate(rate);
            }
            if (diagnosticListeners.getRate() > rate) {
                diagnosticListeners.setRate(rate);
                shouldStartDiagnostics = true;
            }
            diagnosticClient.addDiagnostic(frameType);
            this.mDiagnosticLock.unlock();
            Slog.i(CarLog.TAG_DIAGNOSTIC, String.format("shouldStartDiagnostics = %s for %s at rate %d", Boolean.valueOf(shouldStartDiagnostics), Integer.valueOf(frameType), Integer.valueOf(rate)));
            if (!shouldStartDiagnostics || startDiagnostic(frameType, rate)) {
                return true;
            }
            Slog.w(CarLog.TAG_DIAGNOSTIC, "startDiagnostic failed");
            this.mDiagnosticLock.lock();
            try {
                diagnosticClient.removeDiagnostic(frameType);
                if (oldRate != null) {
                    diagnosticListeners.setRate(oldRate.intValue());
                } else {
                    this.mDiagnosticListeners.remove(Integer.valueOf(frameType));
                }
                return false;
            } finally {
            }
        } catch (RemoteException e) {
            Slog.w(CarLog.TAG_DIAGNOSTIC, String.format("received RemoteException trying to register listener for %s", Integer.valueOf(frameType)));
            return false;
        } finally {
        }
    }

    private boolean startDiagnostic(int frameType, int rate) {
        Slog.i(CarLog.TAG_DIAGNOSTIC, String.format("starting diagnostic %s at rate %d", Integer.valueOf(frameType), Integer.valueOf(rate)));
        DiagnosticHalService diagnosticHal = getDiagnosticHal();
        if (diagnosticHal != null) {
            if (!diagnosticHal.isReady()) {
                Slog.w(CarLog.TAG_DIAGNOSTIC, "diagnosticHal not ready");
                return false;
            } else if (frameType != 0) {
                if (frameType == 1) {
                    if (this.mFreezeFrameDiagnosticRecords.isEnabled()) {
                        return true;
                    }
                    if (diagnosticHal.requestDiagnosticStart(1, rate)) {
                        this.mFreezeFrameDiagnosticRecords.enable();
                        return true;
                    }
                }
            } else if (this.mLiveFrameDiagnosticRecord.isEnabled()) {
                return true;
            } else {
                if (diagnosticHal.requestDiagnosticStart(0, rate)) {
                    this.mLiveFrameDiagnosticRecord.enable();
                    return true;
                }
            }
        }
        return false;
    }

    public void unregisterDiagnosticListener(int frameType, ICarDiagnosticEventListener listener) {
        boolean shouldStopDiagnostic = false;
        boolean shouldRestartDiagnostic = false;
        int newRate = 0;
        this.mDiagnosticLock.lock();
        try {
            DiagnosticClient diagnosticClient = findDiagnosticClientLocked(listener);
            if (diagnosticClient == null) {
                Slog.i(CarLog.TAG_DIAGNOSTIC, String.format("trying to unregister diagnostic client %s for %s which is not registered", listener, Integer.valueOf(frameType)));
                return;
            }
            diagnosticClient.removeDiagnostic(frameType);
            if (diagnosticClient.getNumberOfActiveDiagnostic() == 0) {
                diagnosticClient.release();
                this.mClients.remove(diagnosticClient);
            }
            Listeners<DiagnosticClient> diagnosticListeners = this.mDiagnosticListeners.get(Integer.valueOf(frameType));
            if (diagnosticListeners == null) {
                return;
            }
            Listeners.ClientWithRate<DiagnosticClient> clientWithRate = diagnosticListeners.findClientWithRate(diagnosticClient);
            if (clientWithRate == null) {
                return;
            }
            diagnosticListeners.removeClientWithRate(clientWithRate);
            if (diagnosticListeners.getNumberOfClients() == 0) {
                shouldStopDiagnostic = true;
                this.mDiagnosticListeners.remove(Integer.valueOf(frameType));
            } else if (diagnosticListeners.updateRate()) {
                newRate = diagnosticListeners.getRate();
                shouldRestartDiagnostic = true;
            }
            this.mDiagnosticLock.unlock();
            Slog.i(CarLog.TAG_DIAGNOSTIC, String.format("shouldStopDiagnostic = %s, shouldRestartDiagnostic = %s for type %s", Boolean.valueOf(shouldStopDiagnostic), Boolean.valueOf(shouldRestartDiagnostic), Integer.valueOf(frameType)));
            if (shouldStopDiagnostic) {
                stopDiagnostic(frameType);
            } else if (shouldRestartDiagnostic) {
                startDiagnostic(frameType, newRate);
            }
        } finally {
            this.mDiagnosticLock.unlock();
        }
    }

    private void stopDiagnostic(int frameType) {
        DiagnosticHalService diagnosticHal = getDiagnosticHal();
        if (diagnosticHal == null || !diagnosticHal.isReady()) {
            Slog.w(CarLog.TAG_DIAGNOSTIC, "diagnosticHal not ready");
        } else if (frameType == 0) {
            if (this.mLiveFrameDiagnosticRecord.disableIfNeeded()) {
                diagnosticHal.requestDiagnosticStop(0);
            }
        } else if (frameType == 1 && this.mFreezeFrameDiagnosticRecords.disableIfNeeded()) {
            diagnosticHal.requestDiagnosticStop(1);
        }
    }

    private DiagnosticHalService getDiagnosticHal() {
        return this.mDiagnosticHal;
    }

    public boolean isLiveFrameSupported() {
        return getDiagnosticHal().getDiagnosticCapabilities().isLiveFrameSupported();
    }

    public boolean isFreezeFrameNotificationSupported() {
        return getDiagnosticHal().getDiagnosticCapabilities().isFreezeFrameSupported();
    }

    public boolean isGetFreezeFrameSupported() {
        DiagnosticHalService.DiagnosticCapabilities diagnosticCapabilities = getDiagnosticHal().getDiagnosticCapabilities();
        return diagnosticCapabilities.isFreezeFrameInfoSupported() && diagnosticCapabilities.isFreezeFrameSupported();
    }

    public boolean isClearFreezeFramesSupported() {
        DiagnosticHalService.DiagnosticCapabilities diagnosticCapabilities = getDiagnosticHal().getDiagnosticCapabilities();
        return diagnosticCapabilities.isFreezeFrameClearSupported() && diagnosticCapabilities.isFreezeFrameSupported();
    }

    public boolean isSelectiveClearFreezeFramesSupported() {
        DiagnosticHalService.DiagnosticCapabilities diagnosticCapabilities = getDiagnosticHal().getDiagnosticCapabilities();
        return isClearFreezeFramesSupported() && diagnosticCapabilities.isSelectiveClearFreezeFramesSupported();
    }

    public CarDiagnosticEvent getLatestLiveFrame() {
        this.mLiveFrameDiagnosticRecord.lock();
        CarDiagnosticEvent liveFrame = this.mLiveFrameDiagnosticRecord.getLastEvent();
        this.mLiveFrameDiagnosticRecord.unlock();
        return liveFrame;
    }

    public long[] getFreezeFrameTimestamps() {
        this.mFreezeFrameDiagnosticRecords.lock();
        long[] timestamps = this.mFreezeFrameDiagnosticRecords.getFreezeFrameTimestamps();
        this.mFreezeFrameDiagnosticRecords.unlock();
        return timestamps;
    }

    public CarDiagnosticEvent getFreezeFrame(long timestamp) {
        this.mFreezeFrameDiagnosticRecords.lock();
        CarDiagnosticEvent freezeFrame = this.mFreezeFrameDiagnosticRecords.getEvent(timestamp);
        this.mFreezeFrameDiagnosticRecords.unlock();
        return freezeFrame;
    }

    public boolean clearFreezeFrames(long... timestamps) {
        this.mDiagnosticClearPermission.assertGranted();
        if (isClearFreezeFramesSupported()) {
            if (timestamps == null || timestamps.length == 0 || isSelectiveClearFreezeFramesSupported()) {
                this.mFreezeFrameDiagnosticRecords.lock();
                this.mDiagnosticHal.clearFreezeFrames(timestamps);
                this.mFreezeFrameDiagnosticRecords.clearEvents();
                this.mFreezeFrameDiagnosticRecords.unlock();
                return true;
            }
            return false;
        }
        return false;
    }

    @GuardedBy({"mDiagnosticLock"})
    private DiagnosticClient findDiagnosticClientLocked(ICarDiagnosticEventListener listener) {
        IBinder binder = listener.asBinder();
        Iterator<DiagnosticClient> it = this.mClients.iterator();
        while (it.hasNext()) {
            DiagnosticClient diagnosticClient = it.next();
            if (diagnosticClient.isHoldingListenerBinder(binder)) {
                return diagnosticClient;
            }
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void removeClient(DiagnosticClient diagnosticClient) {
        int[] diagnosticArray;
        this.mDiagnosticLock.lock();
        try {
            for (int diagnostic : diagnosticClient.getDiagnosticArray()) {
                unregisterDiagnosticListener(diagnostic, diagnosticClient.getICarDiagnosticEventListener());
            }
            this.mClients.remove(diagnosticClient);
        } finally {
            this.mDiagnosticLock.unlock();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class DiagnosticClient implements Listeners.IListener {
        private final ICarDiagnosticEventListener mListener;
        private final Set<Integer> mActiveDiagnostics = new HashSet();
        private volatile boolean mActive = true;

        DiagnosticClient(ICarDiagnosticEventListener listener) {
            this.mListener = listener;
        }

        public boolean equals(Object o) {
            return (o instanceof DiagnosticClient) && this.mListener.asBinder() == ((DiagnosticClient) o).mListener.asBinder();
        }

        boolean isHoldingListenerBinder(IBinder listenerBinder) {
            return this.mListener.asBinder() == listenerBinder;
        }

        void addDiagnostic(int frameType) {
            this.mActiveDiagnostics.add(Integer.valueOf(frameType));
        }

        void removeDiagnostic(int frameType) {
            this.mActiveDiagnostics.remove(Integer.valueOf(frameType));
        }

        int getNumberOfActiveDiagnostic() {
            return this.mActiveDiagnostics.size();
        }

        int[] getDiagnosticArray() {
            return this.mActiveDiagnostics.stream().mapToInt(new ToIntFunction() { // from class: com.android.car.-$$Lambda$UV1wDVoVlbcxpr8zevj_aMFtUGw
                @Override // java.util.function.ToIntFunction
                public final int applyAsInt(Object obj) {
                    return ((Integer) obj).intValue();
                }
            }).toArray();
        }

        ICarDiagnosticEventListener getICarDiagnosticEventListener() {
            return this.mListener;
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            this.mListener.asBinder().unlinkToDeath(this, 0);
            CarDiagnosticService.this.removeClient(this);
        }

        void dispatchDiagnosticUpdate(List<CarDiagnosticEvent> events) {
            if (events.size() != 0 && this.mActive) {
                try {
                    this.mListener.onDiagnosticEvents(events);
                } catch (RemoteException e) {
                }
            }
        }

        @Override // com.android.car.Listeners.IListener
        public void release() {
            if (this.mActive) {
                this.mListener.asBinder().unlinkToDeath(this, 0);
                this.mActiveDiagnostics.clear();
                this.mActive = false;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static abstract class DiagnosticRecord {
        protected boolean mEnabled = false;
        private final ReentrantLock mLock;

        abstract boolean disableIfNeeded();

        abstract CarDiagnosticEvent update(CarDiagnosticEvent carDiagnosticEvent);

        DiagnosticRecord(ReentrantLock lock) {
            this.mLock = lock;
        }

        void lock() {
            this.mLock.lock();
        }

        void unlock() {
            this.mLock.unlock();
        }

        boolean isEnabled() {
            return this.mEnabled;
        }

        void enable() {
            this.mEnabled = true;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static class LiveFrameRecord extends DiagnosticRecord {
        CarDiagnosticEvent mLastEvent;

        LiveFrameRecord(ReentrantLock lock) {
            super(lock);
            this.mLastEvent = null;
        }

        @Override // com.android.car.CarDiagnosticService.DiagnosticRecord
        boolean disableIfNeeded() {
            if (this.mEnabled) {
                this.mEnabled = false;
                this.mLastEvent = null;
                return true;
            }
            return false;
        }

        @Override // com.android.car.CarDiagnosticService.DiagnosticRecord
        CarDiagnosticEvent update(CarDiagnosticEvent newEvent) {
            Objects.requireNonNull(newEvent);
            CarDiagnosticEvent carDiagnosticEvent = this.mLastEvent;
            if (carDiagnosticEvent == null || carDiagnosticEvent.isEarlierThan(newEvent)) {
                this.mLastEvent = newEvent;
            }
            return this.mLastEvent;
        }

        CarDiagnosticEvent getLastEvent() {
            return this.mLastEvent;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static class FreezeFrameRecord extends DiagnosticRecord {
        HashMap<Long, CarDiagnosticEvent> mEvents;

        FreezeFrameRecord(ReentrantLock lock) {
            super(lock);
            this.mEvents = new HashMap<>();
        }

        @Override // com.android.car.CarDiagnosticService.DiagnosticRecord
        boolean disableIfNeeded() {
            if (this.mEnabled) {
                this.mEnabled = false;
                clearEvents();
                return true;
            }
            return false;
        }

        void clearEvents() {
            this.mEvents.clear();
        }

        @Override // com.android.car.CarDiagnosticService.DiagnosticRecord
        CarDiagnosticEvent update(CarDiagnosticEvent newEvent) {
            this.mEvents.put(Long.valueOf(newEvent.timestamp), newEvent);
            return newEvent;
        }

        long[] getFreezeFrameTimestamps() {
            return this.mEvents.keySet().stream().mapToLong(new ToLongFunction() { // from class: com.android.car.-$$Lambda$ELHKvd8JMVRD8rbALqYPKbDX2mM
                @Override // java.util.function.ToLongFunction
                public final long applyAsLong(Object obj) {
                    return ((Long) obj).longValue();
                }
            }).toArray();
        }

        CarDiagnosticEvent getEvent(long timestamp) {
            return this.mEvents.get(Long.valueOf(timestamp));
        }

        Iterable<CarDiagnosticEvent> getEvents() {
            return this.mEvents.values();
        }
    }

    @Override // com.android.car.CarServiceBase
    public void dump(final PrintWriter writer) {
        writer.println("*CarDiagnosticService*");
        writer.println("**last events for diagnostics**");
        if (this.mLiveFrameDiagnosticRecord.getLastEvent() != null) {
            writer.println("last live frame event: ");
            writer.println(this.mLiveFrameDiagnosticRecord.getLastEvent());
        }
        writer.println("freeze frame events: ");
        Iterable<CarDiagnosticEvent> events = this.mFreezeFrameDiagnosticRecords.getEvents();
        Objects.requireNonNull(writer);
        events.forEach(new Consumer() { // from class: com.android.car.-$$Lambda$iIXtk07mwjd9ZcasP_C4yfwPh0g
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                writer.println((CarDiagnosticEvent) obj);
            }
        });
        writer.println("**clients**");
        try {
            Iterator<DiagnosticClient> it = this.mClients.iterator();
            while (it.hasNext()) {
                DiagnosticClient client = it.next();
                if (client != null) {
                    try {
                        writer.println("binder:" + client.mListener + " active diagnostics:" + Arrays.toString(client.getDiagnosticArray()));
                    } catch (ConcurrentModificationException e) {
                        writer.println("concurrent modification happened");
                    }
                } else {
                    writer.println("null client");
                }
            }
        } catch (ConcurrentModificationException e2) {
            writer.println("concurrent modification happened");
        }
        writer.println("**diagnostic listeners**");
        try {
            for (Integer num : this.mDiagnosticListeners.keySet()) {
                int diagnostic = num.intValue();
                Listeners diagnosticListeners = this.mDiagnosticListeners.get(Integer.valueOf(diagnostic));
                if (diagnosticListeners != null) {
                    writer.println(" Diagnostic:" + diagnostic + " num client:" + diagnosticListeners.getNumberOfClients() + " rate:" + diagnosticListeners.getRate());
                }
            }
        } catch (ConcurrentModificationException e3) {
            writer.println("concurrent modification happened");
        }
    }
}
