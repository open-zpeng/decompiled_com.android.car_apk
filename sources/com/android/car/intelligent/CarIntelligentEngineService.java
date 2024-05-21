package com.android.car.intelligent;

import android.car.intelligent.CarIntelligentEngineManager;
import android.car.intelligent.CarSceneEvent;
import android.car.intelligent.ICarDrivingSceneListener;
import android.car.intelligent.ICarIntelligentEngine;
import android.car.intelligent.ICarSceneListener;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Slog;
import com.android.car.CarPropertyService;
import com.android.car.CarServiceBase;
import com.android.car.ICarImpl;
import com.android.car.Manifest;
import com.android.car.Utils;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
/* loaded from: classes3.dex */
public class CarIntelligentEngineService extends ICarIntelligentEngine.Stub implements CarServiceBase {
    private static final boolean DBG = true;
    private static final int MAX_TRANSITION_LOG_SIZE = 20;
    private static final String TAG = "CarIntelligentEngine";
    private CarSceneEvent mCacheDrivingSceneEvent;
    private CarSceneEvent mCacheWelcomeSceneEvent;
    private CarDrivingScene mCarDrivingScene;
    private CarWelcomeScene mCarWelcomeScene;
    private final Handler mClientDispatchHandler;
    private final Context mContext;
    private final List<CarWelcomeSceneClient> mCarWelcomeSceneClients = new CopyOnWriteArrayList();
    private final Map<IBinder, CarDrivingSceneClient> mCarDrivingSceneClients = new ConcurrentHashMap();
    private final LinkedList<Utils.TransitionLog> mTransitionLogs = new LinkedList<>();
    private final CarIntelligentEngineManager.CarSceneListener mCarWelcomeSceneListener = new CarIntelligentEngineManager.CarSceneListener() { // from class: com.android.car.intelligent.-$$Lambda$6brY9sBYOQ1sSVV1Pfv5Z2HuWOg
        public final void onWelcomeSceneChanged(CarSceneEvent carSceneEvent) {
            CarIntelligentEngineService.this.dispatchEventToCarWelcomeSceneClients(carSceneEvent);
        }
    };
    private final CarIntelligentEngineManager.CarDrivingSceneListener mCarDrivingSceneListener = new CarIntelligentEngineManager.CarDrivingSceneListener() { // from class: com.android.car.intelligent.-$$Lambda$F7hKz-VxmizAh8hplg7B7iOZlVs
        public final void onCarDrivingSceneChanged(CarSceneEvent carSceneEvent) {
            CarIntelligentEngineService.this.dispatchEventToCarDrivingSceneClients(carSceneEvent);
        }
    };
    private final HandlerThread mClientDispatchThread = new HandlerThread("IntelClientDispatchThread");

    public CarIntelligentEngineService(Context context, CarPropertyService propertyService) {
        this.mContext = context;
        this.mClientDispatchThread.start();
        this.mClientDispatchHandler = new Handler(this.mClientDispatchThread.getLooper());
        this.mCarWelcomeScene = new CarWelcomeScene(context, propertyService);
        this.mCarDrivingScene = new CarDrivingScene(context, propertyService);
        this.mCacheWelcomeSceneEvent = createDefaultCarSceneEvent();
        this.mCacheDrivingSceneEvent = createDefaultCarDrivingSceneEvent();
    }

    @Override // com.android.car.CarServiceBase
    public synchronized void init() {
        this.mCarWelcomeScene.init(this.mCarWelcomeSceneListener);
        this.mCarDrivingScene.init(this.mCarDrivingSceneListener);
        addTransitionLog("CarIntelligentEngine Boot", this.mCacheWelcomeSceneEvent.getSceneAction(), this.mCacheDrivingSceneEvent.getSceneAction(), 0L);
    }

    @Override // com.android.car.CarServiceBase
    public synchronized void release() {
        this.mCarWelcomeScene.release();
        this.mCarDrivingScene.release();
        for (CarWelcomeSceneClient client : this.mCarWelcomeSceneClients) {
            client.listenerBinder.unlinkToDeath(client, 0);
        }
        for (CarDrivingSceneClient client2 : this.mCarDrivingSceneClients.values()) {
            client2.mBinder.unlinkToDeath(client2, 0);
        }
        this.mCarWelcomeSceneClients.clear();
        this.mCarDrivingSceneClients.clear();
    }

    private CarWelcomeSceneClient findCarSceneClient(ICarSceneListener listener) {
        IBinder binder = listener.asBinder();
        for (CarWelcomeSceneClient client : this.mCarWelcomeSceneClients) {
            if (client.isHoldingBinder(binder)) {
                return client;
            }
        }
        return null;
    }

    public synchronized void registerCarSceneListener(ICarSceneListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener is null");
        }
        if (findCarSceneClient(listener) == null) {
            CarWelcomeSceneClient client = new CarWelcomeSceneClient(listener);
            try {
                listener.asBinder().linkToDeath(client, 0);
                this.mCarWelcomeSceneClients.add(client);
                Slog.i(TAG, "registerCarSceneListener(): listener=" + listener + " client.size=" + this.mCarWelcomeSceneClients.size());
            } catch (RemoteException e) {
                Slog.e(TAG, "Cannot link death recipient to binder " + e);
            }
        }
    }

    public synchronized void unregisterCarSceneListener(ICarSceneListener listener) {
        if (listener == null) {
            Slog.e(TAG, "unregisterCarSceneListener(): listener null");
            throw new IllegalArgumentException("Listener is null");
        }
        CarWelcomeSceneClient client = findCarSceneClient(listener);
        if (client == null) {
            Slog.e(TAG, "unregisterCarSceneListener(): listener was not previously registered");
            return;
        }
        listener.asBinder().unlinkToDeath(client, 0);
        this.mCarWelcomeSceneClients.remove(client);
        Slog.i(TAG, "unregisterCarSceneListener(): listener=" + listener + " client.size=" + this.mCarWelcomeSceneClients.size());
    }

    public void registerCarDrivingSceneListener(ICarDrivingSceneListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener cannot be null.");
        }
        IBinder binder = listener.asBinder();
        CarDrivingSceneClient client = this.mCarDrivingSceneClients.get(binder);
        if (client == null) {
            CarDrivingSceneClient client2 = new CarDrivingSceneClient(listener);
            this.mCarDrivingSceneClients.put(listener.asBinder(), client2);
        }
        Slog.i(TAG, "registerCarDrivingSceneListener(): binder=" + binder + " size=" + this.mCarDrivingSceneClients.size());
    }

    public void unregisterCarDrivingSceneListener(ICarDrivingSceneListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener cannot be null.");
        }
        IBinder binder = listener.asBinder();
        CarDrivingSceneClient client = this.mCarDrivingSceneClients.remove(binder);
        if (client == null) {
            Slog.e(TAG, "unregisterCarDrivingSceneListener client not exist. binder:" + binder + " size:" + this.mCarDrivingSceneClients.size());
            return;
        }
        Slog.i(TAG, "unregisterCarDrivingSceneListener: binder:" + binder + " size:" + this.mCarDrivingSceneClients.size());
    }

    public void setCarDrivingSceneNRALevel(int level) {
        Slog.i(TAG, "setCarDrivingSceneNRALevel: level:" + level);
        Settings.Global.putInt(this.mContext.getContentResolver(), "android.car.VALUE_USER_SWITCH_CAR_DRIVING_SCENE_NRA_LEVEL", level);
        this.mCarDrivingScene.setCarDrivingSceneNRALevel(level);
    }

    public int getCarDrivingSceneNRALevel() {
        int level = Settings.Global.getInt(this.mContext.getContentResolver(), "android.car.VALUE_USER_SWITCH_CAR_DRIVING_SCENE_NRA_LEVEL", 2);
        Slog.i(TAG, "getCarDrivingSceneNRALevel: level:" + level);
        return level;
    }

    public void injectCarSceneAction(int type, int action, int pos) {
        if (Build.IS_USER) {
            return;
        }
        Slog.i(TAG, "injectCarSceneAction() called with: type = [" + type + "], action = [" + action + "], pos = [" + pos + "]");
        CarSceneEvent event = createDefaultCarSceneEvent();
        event.setSceneType(type);
        event.setSceneAction(action);
        event.setScenePosition(pos);
        if (type == 1) {
            dispatchEventToCarWelcomeSceneClients(event);
        } else if (type == 2 && getCarDrivingSceneNRALevel() != 0) {
            dispatchEventToCarDrivingSceneClients(event);
        }
    }

    public void injectCarSceneCalibrationData(float[] front, float[] rear) {
        if (Build.IS_USER) {
            return;
        }
        this.mCarDrivingScene.injectCarSceneCalibrationData(front, rear);
    }

    public void dispatchEventToCarWelcomeSceneClients(final CarSceneEvent event) {
        ICarImpl.assertPermission(this.mContext, Manifest.permission.CONTROL_APP_BLOCKING);
        addTransitionLog("CarIntelligentEngine dispatchEventToCarWelcomeSceneClients", event.getSceneAction(), event.getSceneAction(), event.getTimeStamp());
        this.mClientDispatchHandler.post(new Runnable() { // from class: com.android.car.intelligent.-$$Lambda$CarIntelligentEngineService$Xj0eV7Hw529BJKiNHJfdJ3LwztY
            @Override // java.lang.Runnable
            public final void run() {
                CarIntelligentEngineService.this.lambda$dispatchEventToCarWelcomeSceneClients$0$CarIntelligentEngineService(event);
            }
        });
        this.mCacheWelcomeSceneEvent = event;
    }

    public /* synthetic */ void lambda$dispatchEventToCarWelcomeSceneClients$0$CarIntelligentEngineService(CarSceneEvent event) {
        for (CarWelcomeSceneClient client : this.mCarWelcomeSceneClients) {
            client.dispatchEventToClients(event);
        }
    }

    public void dispatchEventToCarDrivingSceneClients(final CarSceneEvent event) {
        ICarImpl.assertPermission(this.mContext, Manifest.permission.CONTROL_APP_BLOCKING);
        addTransitionLog("CarIntelligentEngine dispatchEventToCarDrivingSceneClients", event.getSceneAction(), event.getSceneAction(), event.getTimeStamp());
        this.mClientDispatchHandler.post(new Runnable() { // from class: com.android.car.intelligent.-$$Lambda$CarIntelligentEngineService$wkcIyMTcDBOlppbzc_LLGb7L-zY
            @Override // java.lang.Runnable
            public final void run() {
                CarIntelligentEngineService.this.lambda$dispatchEventToCarDrivingSceneClients$1$CarIntelligentEngineService(event);
            }
        });
        this.mCacheDrivingSceneEvent = event;
    }

    public /* synthetic */ void lambda$dispatchEventToCarDrivingSceneClients$1$CarIntelligentEngineService(CarSceneEvent event) {
        for (CarDrivingSceneClient client : this.mCarDrivingSceneClients.values()) {
            if (client == null || client.mListener == null) {
                Slog.i(TAG, "dispatchEventToCarDrivingSceneClients: binder fatal client:" + client);
            } else {
                try {
                    client.mListener.onCarDrivingSceneChanged(event);
                } catch (RemoteException e) {
                    Slog.i(TAG, "dispatchEventToCarDrivingSceneClients to listener failed e:" + e.getMessage());
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class CarWelcomeSceneClient implements IBinder.DeathRecipient {
        private final ICarSceneListener listener;
        private final IBinder listenerBinder;

        public CarWelcomeSceneClient(ICarSceneListener l) {
            this.listener = l;
            this.listenerBinder = l.asBinder();
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            Slog.d(CarIntelligentEngineService.TAG, "Binder died " + this.listenerBinder);
            this.listenerBinder.unlinkToDeath(this, 0);
            CarIntelligentEngineService.this.mCarWelcomeSceneClients.remove(this);
        }

        public boolean isHoldingBinder(IBinder binder) {
            return this.listenerBinder == binder;
        }

        public void dispatchEventToClients(CarSceneEvent event) {
            if (event == null) {
                return;
            }
            try {
                this.listener.onWelcomeSceneChanged(event);
            } catch (RemoteException e) {
                Slog.e(CarIntelligentEngineService.TAG, "Dispatch to listener failed");
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class CarDrivingSceneClient implements IBinder.DeathRecipient {
        private final IBinder mBinder;
        private final ICarDrivingSceneListener mListener;

        public CarDrivingSceneClient(ICarDrivingSceneListener l) {
            this.mListener = l;
            this.mBinder = l.asBinder();
            try {
                this.mBinder.linkToDeath(this, 0);
            } catch (RemoteException e) {
                Slog.e(CarIntelligentEngineService.TAG, "Failed to link death for recipient. " + e.getMessage());
                throw new IllegalStateException("CarNotConnected");
            }
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            Slog.d(CarIntelligentEngineService.TAG, "Binder died " + this.mBinder);
            this.mBinder.unlinkToDeath(this, 0);
            CarIntelligentEngineService.this.unregisterCarDrivingSceneListener(this.mListener);
        }
    }

    @Override // com.android.car.CarServiceBase
    public void dump(PrintWriter writer) {
        writer.println("*IntelligentEngineService*");
        writer.println("Scene action change log:");
        Iterator<Utils.TransitionLog> it = this.mTransitionLogs.iterator();
        while (it.hasNext()) {
            Utils.TransitionLog tLog = it.next();
            writer.println(tLog);
        }
    }

    private void addTransitionLog(String name, int from, int to, long timestamp) {
        if (this.mTransitionLogs.size() >= 20) {
            this.mTransitionLogs.remove();
        }
        Utils.TransitionLog tLog = new Utils.TransitionLog(name, Integer.valueOf(from), Integer.valueOf(to), timestamp);
        this.mTransitionLogs.add(tLog);
    }

    private static CarSceneEvent createDefaultCarSceneEvent() {
        return new CarSceneEvent(SystemClock.elapsedRealtimeNanos(), 1, -1, 1);
    }

    private static CarSceneEvent createDefaultCarDrivingSceneEvent() {
        return new CarSceneEvent(SystemClock.elapsedRealtimeNanos(), 2, -1, 1);
    }
}
