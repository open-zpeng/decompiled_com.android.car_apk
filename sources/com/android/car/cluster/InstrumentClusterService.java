package com.android.car.cluster;

import android.annotation.SystemApi;
import android.app.ActivityOptions;
import android.car.cluster.IInstrumentClusterManagerCallback;
import android.car.cluster.IInstrumentClusterManagerService;
import android.car.cluster.renderer.IInstrumentCluster;
import android.car.cluster.renderer.IInstrumentClusterHelper;
import android.car.cluster.renderer.IInstrumentClusterNavigation;
import android.car.encryptionrunner.DummyEncryptionRunner;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Slog;
import android.view.KeyEvent;
import com.android.car.AppFocusService;
import com.android.car.CarInputService;
import com.android.car.CarLocalServices;
import com.android.car.CarServiceBase;
import com.android.car.R;
import com.android.car.am.FixedActivityService;
import com.android.car.user.CarUserService;
import com.android.internal.annotations.GuardedBy;
import java.io.PrintWriter;
import java.util.Objects;
@SystemApi
/* loaded from: classes3.dex */
public class InstrumentClusterService implements CarServiceBase, AppFocusService.FocusOwnershipCallback, CarInputService.KeyEventListener {
    private static final ContextOwner NO_OWNER = new ContextOwner(0, 0);
    private static final String TAG = "CAR.CLUSTER";
    private final AppFocusService mAppFocusService;
    private final CarInputService mCarInputService;
    private final Context mContext;
    @GuardedBy({"mLock"})
    private DeferredRebinder mDeferredRebinder;
    @GuardedBy({"mLock"})
    private IInstrumentCluster mRendererService;
    @Deprecated
    private final ClusterManagerService mClusterManagerService = new ClusterManagerService();
    private final Object mLock = new Object();
    @GuardedBy({"mLock"})
    private ContextOwner mNavContextOwner = NO_OWNER;
    @GuardedBy({"mLock"})
    private boolean mRendererBound = false;
    private final ServiceConnection mRendererServiceConnection = new ServiceConnection() { // from class: com.android.car.cluster.InstrumentClusterService.1
        @Override // android.content.ServiceConnection
        public void onServiceConnected(ComponentName name, IBinder binder) {
            ContextOwner navContextOwner;
            if (Log.isLoggable("CAR.CLUSTER", 3)) {
                Slog.d("CAR.CLUSTER", "onServiceConnected, name: " + name + ", binder: " + binder);
            }
            IInstrumentCluster service = IInstrumentCluster.Stub.asInterface(binder);
            synchronized (InstrumentClusterService.this.mLock) {
                InstrumentClusterService.this.mRendererService = service;
                navContextOwner = InstrumentClusterService.this.mNavContextOwner;
            }
            if (navContextOwner != null && service != null) {
                InstrumentClusterService.notifyNavContextOwnerChanged(service, navContextOwner);
            }
        }

        @Override // android.content.ServiceConnection
        public void onServiceDisconnected(ComponentName name) {
            DeferredRebinder rebinder;
            if (Log.isLoggable("CAR.CLUSTER", 3)) {
                Slog.d("CAR.CLUSTER", "onServiceDisconnected, name: " + name);
            }
            InstrumentClusterService.this.mContext.unbindService(this);
            synchronized (InstrumentClusterService.this.mLock) {
                InstrumentClusterService.this.mRendererBound = false;
                InstrumentClusterService.this.mRendererService = null;
                if (InstrumentClusterService.this.mDeferredRebinder == null) {
                    InstrumentClusterService.this.mDeferredRebinder = new DeferredRebinder();
                }
                rebinder = InstrumentClusterService.this.mDeferredRebinder;
            }
            rebinder.rebind();
        }
    };
    private final IInstrumentClusterHelper mInstrumentClusterHelper = new IInstrumentClusterHelper.Stub() { // from class: com.android.car.cluster.InstrumentClusterService.2
        public boolean startFixedActivityModeForDisplayAndUser(Intent intent, Bundle activityOptionsBundle, int userId) {
            Binder.clearCallingIdentity();
            ActivityOptions options = new ActivityOptions(activityOptionsBundle);
            FixedActivityService service = (FixedActivityService) CarLocalServices.getService(FixedActivityService.class);
            return service.startFixedActivityModeForDisplayAndUser(intent, options, options.getLaunchDisplayId(), userId);
        }

        public void stopFixedActivityMode(int displayId) {
            Binder.clearCallingIdentity();
            FixedActivityService service = (FixedActivityService) CarLocalServices.getService(FixedActivityService.class);
            service.stopFixedActivityMode(displayId);
        }
    };

    public InstrumentClusterService(Context context, AppFocusService appFocusService, CarInputService carInputService) {
        this.mContext = context;
        this.mAppFocusService = appFocusService;
        this.mCarInputService = carInputService;
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
        if (Log.isLoggable("CAR.CLUSTER", 3)) {
            Slog.d("CAR.CLUSTER", DummyEncryptionRunner.INIT);
        }
        this.mAppFocusService.registerContextOwnerChangedCallback(this);
        this.mCarInputService.setInstrumentClusterKeyListener(this);
        ((CarUserService) CarLocalServices.getService(CarUserService.class)).runOnUser0Unlock(new Runnable() { // from class: com.android.car.cluster.-$$Lambda$InstrumentClusterService$Nko0QYkm6E_U9NrbyWroocy7oAE
            @Override // java.lang.Runnable
            public final void run() {
                InstrumentClusterService.this.lambda$init$0$InstrumentClusterService();
            }
        });
    }

    public /* synthetic */ void lambda$init$0$InstrumentClusterService() {
        this.mRendererBound = bindInstrumentClusterRendererService();
    }

    @Override // com.android.car.CarServiceBase
    public void release() {
        if (Log.isLoggable("CAR.CLUSTER", 3)) {
            Slog.d("CAR.CLUSTER", "release");
        }
        this.mAppFocusService.unregisterContextOwnerChangedCallback(this);
        if (this.mRendererBound) {
            this.mContext.unbindService(this.mRendererServiceConnection);
            this.mRendererBound = false;
        }
    }

    @Override // com.android.car.CarServiceBase
    public void dump(PrintWriter writer) {
        writer.println("**" + getClass().getSimpleName() + "**");
        StringBuilder sb = new StringBuilder();
        sb.append("bound with renderer: ");
        sb.append(this.mRendererBound);
        writer.println(sb.toString());
        writer.println("renderer service: " + this.mRendererService);
        writer.println("context owner: " + this.mNavContextOwner);
    }

    @Override // com.android.car.AppFocusService.FocusOwnershipCallback
    public void onFocusAcquired(int appType, int uid, int pid) {
        changeNavContextOwner(appType, uid, pid, true);
    }

    @Override // com.android.car.AppFocusService.FocusOwnershipCallback
    public void onFocusAbandoned(int appType, int uid, int pid) {
        changeNavContextOwner(appType, uid, pid, false);
    }

    private void changeNavContextOwner(int appType, int uid, int pid, boolean acquire) {
        if (appType != 1) {
            return;
        }
        ContextOwner requester = new ContextOwner(uid, pid);
        ContextOwner newOwner = acquire ? requester : NO_OWNER;
        synchronized (this.mLock) {
            if (acquire) {
                try {
                    if (!Objects.equals(this.mNavContextOwner, requester)) {
                    }
                    Slog.w("CAR.CLUSTER", "Invalid nav context owner change (acquiring: " + acquire + "), current owner: [" + this.mNavContextOwner + "], requester: [" + requester + "]");
                } catch (Throwable th) {
                    throw th;
                }
            }
            if (acquire || Objects.equals(this.mNavContextOwner, requester)) {
                this.mNavContextOwner = newOwner;
                IInstrumentCluster service = this.mRendererService;
                if (service != null) {
                    notifyNavContextOwnerChanged(service, newOwner);
                    return;
                }
                return;
            }
            Slog.w("CAR.CLUSTER", "Invalid nav context owner change (acquiring: " + acquire + "), current owner: [" + this.mNavContextOwner + "], requester: [" + requester + "]");
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void notifyNavContextOwnerChanged(IInstrumentCluster service, ContextOwner owner) {
        try {
            service.setNavigationContextOwner(owner.uid, owner.pid);
        } catch (RemoteException e) {
            Slog.e("CAR.CLUSTER", "Failed to call setNavigationContextOwner", e);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean bindInstrumentClusterRendererService() {
        String rendererService = this.mContext.getString(R.string.instrumentClusterRendererService);
        if (TextUtils.isEmpty(rendererService)) {
            Slog.i("CAR.CLUSTER", "Instrument cluster renderer was not configured");
            return false;
        }
        Slog.d("CAR.CLUSTER", "bindInstrumentClusterRendererService, component: " + rendererService);
        Intent intent = new Intent();
        intent.setComponent(ComponentName.unflattenFromString(rendererService));
        Bundle bundle = new Bundle();
        bundle.putBinder("android.car.cluster.renderer.IInstrumentClusterHelper", this.mInstrumentClusterHelper.asBinder());
        intent.putExtra("android.car.cluster.renderer.IInstrumentClusterHelper", bundle);
        return this.mContext.bindServiceAsUser(intent, this.mRendererServiceConnection, 65, UserHandle.SYSTEM);
    }

    public IInstrumentClusterNavigation getNavigationService() {
        try {
            IInstrumentCluster service = getInstrumentClusterRendererService();
            if (service == null) {
                return null;
            }
            return service.getNavigationService();
        } catch (RemoteException e) {
            Slog.e("CAR.CLUSTER", "getNavigationServiceBinder", e);
            return null;
        }
    }

    @Deprecated
    public IInstrumentClusterManagerService.Stub getManagerService() {
        return this.mClusterManagerService;
    }

    @Override // com.android.car.CarInputService.KeyEventListener
    public void onKeyEvent(KeyEvent event) {
        if (Log.isLoggable("CAR.CLUSTER", 3)) {
            Slog.d("CAR.CLUSTER", "InstrumentClusterService#onKeyEvent: " + event);
        }
        IInstrumentCluster service = getInstrumentClusterRendererService();
        if (service != null) {
            try {
                service.onKeyEvent(event);
            } catch (RemoteException e) {
                Slog.e("CAR.CLUSTER", "onKeyEvent", e);
            }
        }
    }

    private IInstrumentCluster getInstrumentClusterRendererService() {
        IInstrumentCluster service;
        synchronized (this.mLock) {
            service = this.mRendererService;
        }
        return service;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static class ContextOwner {
        final int pid;
        final int uid;

        ContextOwner(int uid, int pid) {
            this.uid = uid;
            this.pid = pid;
        }

        public String toString() {
            return "uid: " + this.uid + ", pid: " + this.pid;
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ContextOwner that = (ContextOwner) o;
            if (this.uid == that.uid && this.pid == that.pid) {
                return true;
            }
            return false;
        }

        public int hashCode() {
            return Objects.hash(Integer.valueOf(this.uid), Integer.valueOf(this.pid));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    @Deprecated
    /* loaded from: classes3.dex */
    public class ClusterManagerService extends IInstrumentClusterManagerService.Stub {
        private ClusterManagerService() {
        }

        public void startClusterActivity(Intent intent) throws RemoteException {
        }

        public void registerCallback(IInstrumentClusterManagerCallback callback) throws RemoteException {
        }

        public void unregisterCallback(IInstrumentClusterManagerCallback callback) throws RemoteException {
        }
    }

    /* loaded from: classes3.dex */
    private class DeferredRebinder extends Handler {
        private static final long NEXT_REBIND_ATTEMPT_DELAY_MS = 1000;
        private static final int NUMBER_OF_ATTEMPTS = 10;

        private DeferredRebinder() {
        }

        public void rebind() {
            InstrumentClusterService instrumentClusterService = InstrumentClusterService.this;
            instrumentClusterService.mRendererBound = instrumentClusterService.bindInstrumentClusterRendererService();
            if (!InstrumentClusterService.this.mRendererBound) {
                removeMessages(0);
                sendMessageDelayed(obtainMessage(0, 10, 0), NEXT_REBIND_ATTEMPT_DELAY_MS);
            }
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            InstrumentClusterService instrumentClusterService = InstrumentClusterService.this;
            instrumentClusterService.mRendererBound = instrumentClusterService.bindInstrumentClusterRendererService();
            if (InstrumentClusterService.this.mRendererBound) {
                Slog.w("CAR.CLUSTER", "Failed to bound to render service, next attempt in 1000ms.");
                int attempts = msg.arg1 - 1;
                if (attempts < 0) {
                    Slog.e("CAR.CLUSTER", "Failed to rebind with cluster rendering service");
                } else {
                    sendMessageDelayed(obtainMessage(0, attempts, 0), NEXT_REBIND_ATTEMPT_DELAY_MS);
                }
            }
        }
    }
}
