package com.android.car;

import android.car.IAppFocus;
import android.car.IAppFocusListener;
import android.car.IAppFocusOwnershipCallback;
import android.content.Context;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Slog;
import com.android.car.BinderInterfaceContainer;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
/* loaded from: classes3.dex */
public class AppFocusService extends IAppFocus.Stub implements CarServiceBase, BinderInterfaceContainer.BinderEventHandler<IAppFocusOwnershipCallback> {
    private static final boolean DBG = false;
    private static final boolean DBG_EVENT = false;
    private DispatchHandler mDispatchHandler;
    private HandlerThread mHandlerThread;
    private final SystemActivityMonitoringService mSystemActivityMonitoringService;
    private final HashMap<Integer, OwnershipClientInfo> mFocusOwners = new HashMap<>();
    private final Set<Integer> mActiveAppTypes = new HashSet();
    private final CopyOnWriteArrayList<FocusOwnershipCallback> mFocusOwnershipCallbacks = new CopyOnWriteArrayList<>();
    private final BinderInterfaceContainer.BinderEventHandler<IAppFocusListener> mAllBinderEventHandler = new BinderInterfaceContainer.BinderEventHandler() { // from class: com.android.car.-$$Lambda$AppFocusService$AiDDPZgEZaDV_-UI2aNW_sgF2_U
        @Override // com.android.car.BinderInterfaceContainer.BinderEventHandler
        public final void onBinderDeath(BinderInterfaceContainer.BinderInterface binderInterface) {
            AppFocusService.lambda$new$0(binderInterface);
        }
    };
    private final ClientHolder mAllChangeClients = new ClientHolder(this.mAllBinderEventHandler);
    private final OwnershipClientHolder mAllOwnershipClients = new OwnershipClientHolder();

    /* loaded from: classes3.dex */
    public interface FocusOwnershipCallback {
        void onFocusAbandoned(int i, int i2, int i3);

        void onFocusAcquired(int i, int i2, int i3);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ void lambda$new$0(BinderInterfaceContainer.BinderInterface bInterface) {
    }

    public AppFocusService(Context context, SystemActivityMonitoringService systemActivityMonitoringService) {
        this.mSystemActivityMonitoringService = systemActivityMonitoringService;
    }

    public void registerFocusListener(IAppFocusListener listener, int appType) {
        synchronized (this) {
            ClientInfo info = (ClientInfo) this.mAllChangeClients.getBinderInterface(listener);
            if (info == null) {
                this.mAllChangeClients.addBinderInterface(new ClientInfo(this.mAllChangeClients, listener, Binder.getCallingUid(), Binder.getCallingPid(), appType));
            } else {
                info.addAppType(Integer.valueOf(appType));
            }
        }
    }

    public void unregisterFocusListener(IAppFocusListener listener, int appType) {
        synchronized (this) {
            ClientInfo info = (ClientInfo) this.mAllChangeClients.getBinderInterface(listener);
            if (info == null) {
                return;
            }
            info.removeAppType(Integer.valueOf(appType));
            if (info.getAppTypes().isEmpty()) {
                this.mAllChangeClients.removeBinder(listener);
            }
        }
    }

    public int[] getActiveAppTypes() {
        int[] intArray;
        synchronized (this) {
            intArray = toIntArray(this.mActiveAppTypes);
        }
        return intArray;
    }

    public boolean isOwningFocus(IAppFocusOwnershipCallback callback, int appType) {
        synchronized (this) {
            OwnershipClientInfo info = (OwnershipClientInfo) this.mAllOwnershipClients.getBinderInterface(callback);
            if (info == null) {
                return false;
            }
            return info.getOwnedAppTypes().contains(Integer.valueOf(appType));
        }
    }

    public int requestAppFocus(IAppFocusOwnershipCallback callback, int appType) {
        synchronized (this) {
            OwnershipClientInfo info = (OwnershipClientInfo) this.mAllOwnershipClients.getBinderInterface(callback);
            if (info == null) {
                info = new OwnershipClientInfo(this.mAllOwnershipClients, callback, Binder.getCallingUid(), Binder.getCallingPid());
                this.mAllOwnershipClients.addBinderInterface(info);
            }
            Set<Integer> alreadyOwnedAppTypes = info.getOwnedAppTypes();
            if (!alreadyOwnedAppTypes.contains(Integer.valueOf(appType))) {
                OwnershipClientInfo ownerInfo = this.mFocusOwners.get(Integer.valueOf(appType));
                if (ownerInfo != null && ownerInfo != info) {
                    if (this.mSystemActivityMonitoringService.isInForeground(ownerInfo.getPid(), ownerInfo.getUid()) && !this.mSystemActivityMonitoringService.isInForeground(info.getPid(), info.getUid())) {
                        Slog.w(CarLog.TAG_APP_FOCUS, "Focus request failed for non-foreground app(pid=" + info.getPid() + ", uid=" + info.getUid() + ").Foreground app (pid=" + ownerInfo.getPid() + ", uid=" + ownerInfo.getUid() + ") owns it.");
                        return 0;
                    }
                    ownerInfo.removeOwnedAppType(Integer.valueOf(appType));
                    this.mDispatchHandler.requestAppFocusOwnershipLossDispatch(ownerInfo.binderInterface, appType);
                }
                updateFocusOwner(appType, info);
            }
            info.addOwnedAppType(Integer.valueOf(appType));
            this.mDispatchHandler.requestAppFocusOwnershipGrantDispatch(info.binderInterface, appType);
            if (this.mActiveAppTypes.add(Integer.valueOf(appType))) {
                for (BinderInterfaceContainer.BinderInterface<IAppFocusListener> client : this.mAllChangeClients.getInterfaces()) {
                    ClientInfo clientInfo = (ClientInfo) client;
                    if (clientInfo.getAppTypes().contains(Integer.valueOf(appType))) {
                        this.mDispatchHandler.requestAppFocusChangeDispatch(clientInfo.binderInterface, appType, true);
                    }
                }
            }
            return 1;
        }
    }

    public void abandonAppFocus(IAppFocusOwnershipCallback callback, int appType) {
        synchronized (this) {
            OwnershipClientInfo info = (OwnershipClientInfo) this.mAllOwnershipClients.getBinderInterface(callback);
            if (info == null) {
                return;
            }
            if (this.mActiveAppTypes.contains(Integer.valueOf(appType))) {
                Set<Integer> currentlyOwnedAppTypes = info.getOwnedAppTypes();
                if (currentlyOwnedAppTypes.contains(Integer.valueOf(appType))) {
                    if (this.mFocusOwners.remove(Integer.valueOf(appType)) != null) {
                        this.mActiveAppTypes.remove(Integer.valueOf(appType));
                        info.removeOwnedAppType(Integer.valueOf(appType));
                        Iterator<FocusOwnershipCallback> it = this.mFocusOwnershipCallbacks.iterator();
                        while (it.hasNext()) {
                            FocusOwnershipCallback ownershipCallback = it.next();
                            ownershipCallback.onFocusAbandoned(appType, info.mUid, info.mPid);
                        }
                        for (BinderInterfaceContainer.BinderInterface<IAppFocusListener> client : this.mAllChangeClients.getInterfaces()) {
                            ClientInfo clientInfo = (ClientInfo) client;
                            if (clientInfo.getAppTypes().contains(Integer.valueOf(appType))) {
                                this.mDispatchHandler.requestAppFocusChangeDispatch(clientInfo.binderInterface, appType, false);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
        synchronized (this) {
            this.mHandlerThread = new HandlerThread(AppFocusService.class.getSimpleName());
            this.mHandlerThread.start();
            this.mDispatchHandler = new DispatchHandler(this.mHandlerThread.getLooper());
        }
    }

    @Override // com.android.car.CarServiceBase
    public void release() {
        synchronized (this) {
            this.mHandlerThread.quitSafely();
            try {
                this.mHandlerThread.join(1000L);
            } catch (InterruptedException e) {
                Slog.e(CarLog.TAG_APP_FOCUS, "Timeout while waiting for handler thread to join.");
            }
            this.mDispatchHandler = null;
            this.mAllChangeClients.clear();
            this.mAllOwnershipClients.clear();
            this.mFocusOwners.clear();
            this.mActiveAppTypes.clear();
        }
    }

    @Override // com.android.car.BinderInterfaceContainer.BinderEventHandler
    public void onBinderDeath(BinderInterfaceContainer.BinderInterface<IAppFocusOwnershipCallback> bInterface) {
        OwnershipClientInfo info = (OwnershipClientInfo) bInterface;
        for (Integer appType : info.getOwnedAppTypes()) {
            abandonAppFocus(bInterface.binderInterface, appType.intValue());
        }
    }

    @Override // com.android.car.CarServiceBase
    public void dump(PrintWriter writer) {
        writer.println("**AppFocusService**");
        synchronized (this) {
            writer.println("mActiveAppTypes:" + this.mActiveAppTypes);
            for (BinderInterfaceContainer.BinderInterface<IAppFocusOwnershipCallback> client : this.mAllOwnershipClients.getInterfaces()) {
                OwnershipClientInfo clientInfo = (OwnershipClientInfo) client;
                writer.println(clientInfo.toString());
            }
        }
    }

    public boolean isFocusOwner(int uid, int pid, int appType) {
        synchronized (this) {
            boolean z = false;
            if (this.mFocusOwners.containsKey(Integer.valueOf(appType))) {
                OwnershipClientInfo clientInfo = this.mFocusOwners.get(Integer.valueOf(appType));
                if (clientInfo.getUid() == uid && clientInfo.getPid() == pid) {
                    z = true;
                }
                return z;
            }
            return false;
        }
    }

    public void registerContextOwnerChangedCallback(FocusOwnershipCallback callback) {
        HashSet<Map.Entry<Integer, OwnershipClientInfo>> owners;
        this.mFocusOwnershipCallbacks.add(callback);
        synchronized (this) {
            owners = new HashSet<>(this.mFocusOwners.entrySet());
        }
        Iterator<Map.Entry<Integer, OwnershipClientInfo>> it = owners.iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, OwnershipClientInfo> entry = it.next();
            OwnershipClientInfo clientInfo = entry.getValue();
            callback.onFocusAcquired(entry.getKey().intValue(), clientInfo.getUid(), clientInfo.getPid());
        }
    }

    public void unregisterContextOwnerChangedCallback(FocusOwnershipCallback callback) {
        this.mFocusOwnershipCallbacks.remove(callback);
    }

    private void updateFocusOwner(final int appType, final OwnershipClientInfo owner) {
        CarServiceUtils.runOnMain(new Runnable() { // from class: com.android.car.-$$Lambda$AppFocusService$Exg-V1b2ywBToPzPFRw0drXnRNk
            @Override // java.lang.Runnable
            public final void run() {
                AppFocusService.this.lambda$updateFocusOwner$1$AppFocusService(appType, owner);
            }
        });
    }

    public /* synthetic */ void lambda$updateFocusOwner$1$AppFocusService(int appType, OwnershipClientInfo owner) {
        synchronized (this) {
            this.mFocusOwners.put(Integer.valueOf(appType), owner);
        }
        Iterator<FocusOwnershipCallback> it = this.mFocusOwnershipCallbacks.iterator();
        while (it.hasNext()) {
            FocusOwnershipCallback callback = it.next();
            callback.onFocusAcquired(appType, owner.getUid(), owner.getPid());
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void dispatchAppFocusOwnershipLoss(IAppFocusOwnershipCallback callback, int appType) {
        try {
            callback.onAppFocusOwnershipLost(appType);
        } catch (RemoteException e) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void dispatchAppFocusOwnershipGrant(IAppFocusOwnershipCallback callback, int appType) {
        try {
            callback.onAppFocusOwnershipGranted(appType);
        } catch (RemoteException e) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void dispatchAppFocusChange(IAppFocusListener listener, int appType, boolean active) {
        try {
            listener.onAppFocusChanged(appType, active);
        } catch (RemoteException e) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static class ClientHolder extends BinderInterfaceContainer<IAppFocusListener> {
        private ClientHolder(BinderInterfaceContainer.BinderEventHandler<IAppFocusListener> holder) {
            super(holder);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static class OwnershipClientHolder extends BinderInterfaceContainer<IAppFocusOwnershipCallback> {
        private OwnershipClientHolder(AppFocusService service) {
            super(service);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static class ClientInfo extends BinderInterfaceContainer.BinderInterface<IAppFocusListener> {
        private final Set<Integer> mAppTypes;
        private final int mPid;
        private final int mUid;

        private ClientInfo(ClientHolder holder, IAppFocusListener binder, int uid, int pid, int appType) {
            super(holder, binder);
            this.mAppTypes = new HashSet();
            this.mUid = uid;
            this.mPid = pid;
            this.mAppTypes.add(Integer.valueOf(appType));
        }

        /* JADX INFO: Access modifiers changed from: private */
        public synchronized Set<Integer> getAppTypes() {
            return this.mAppTypes;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public synchronized boolean addAppType(Integer appType) {
            return this.mAppTypes.add(appType);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public synchronized boolean removeAppType(Integer appType) {
            return this.mAppTypes.remove(appType);
        }

        public String toString() {
            String str;
            synchronized (this) {
                str = "ClientInfo{mUid=" + this.mUid + ",mPid=" + this.mPid + ",appTypes=" + this.mAppTypes + "}";
            }
            return str;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static class OwnershipClientInfo extends BinderInterfaceContainer.BinderInterface<IAppFocusOwnershipCallback> {
        private final Set<Integer> mOwnedAppTypes;
        private final int mPid;
        private final int mUid;

        private OwnershipClientInfo(OwnershipClientHolder holder, IAppFocusOwnershipCallback binder, int uid, int pid) {
            super(holder, binder);
            this.mOwnedAppTypes = new HashSet();
            this.mUid = uid;
            this.mPid = pid;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public synchronized Set<Integer> getOwnedAppTypes() {
            return this.mOwnedAppTypes;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public synchronized boolean addOwnedAppType(Integer appType) {
            return this.mOwnedAppTypes.add(appType);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public synchronized boolean removeOwnedAppType(Integer appType) {
            return this.mOwnedAppTypes.remove(appType);
        }

        int getUid() {
            return this.mUid;
        }

        int getPid() {
            return this.mPid;
        }

        public String toString() {
            String str;
            synchronized (this) {
                str = "ClientInfo{mUid=" + this.mUid + ",mPid=" + this.mPid + ",owned=" + this.mOwnedAppTypes + "}";
            }
            return str;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class DispatchHandler extends Handler {
        private static final int MSG_DISPATCH_FOCUS_CHANGE = 2;
        private static final int MSG_DISPATCH_OWNERSHIP_GRANT = 1;
        private static final int MSG_DISPATCH_OWNERSHIP_LOSS = 0;

        private DispatchHandler(Looper looper) {
            super(looper);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void requestAppFocusOwnershipLossDispatch(IAppFocusOwnershipCallback callback, int appType) {
            Message msg = obtainMessage(0, appType, 0, callback);
            sendMessage(msg);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void requestAppFocusOwnershipGrantDispatch(IAppFocusOwnershipCallback callback, int appType) {
            Message msg = obtainMessage(1, appType, 0, callback);
            sendMessage(msg);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void requestAppFocusChangeDispatch(IAppFocusListener listener, int appType, boolean active) {
            Message msg = obtainMessage(2, appType, active ? 1 : 0, listener);
            sendMessage(msg);
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            int i = msg.what;
            if (i == 0) {
                AppFocusService.this.dispatchAppFocusOwnershipLoss((IAppFocusOwnershipCallback) msg.obj, msg.arg1);
                return;
            }
            if (i == 1) {
                AppFocusService.this.dispatchAppFocusOwnershipGrant((IAppFocusOwnershipCallback) msg.obj, msg.arg1);
            } else if (i == 2) {
                AppFocusService.this.dispatchAppFocusChange((IAppFocusListener) msg.obj, msg.arg1, msg.arg2 == 1);
            } else {
                Slog.e(CarLog.TAG_APP_FOCUS, "Can't dispatch message: " + msg);
            }
        }
    }

    private static int[] toIntArray(Set<Integer> intSet) {
        int[] intArr = new int[intSet.size()];
        int index = 0;
        for (Integer value : intSet) {
            intArr[index] = value.intValue();
            index++;
        }
        return intArr;
    }
}
