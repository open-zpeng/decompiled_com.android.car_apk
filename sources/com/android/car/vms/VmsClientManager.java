package com.android.car.vms;

import android.car.vms.IVmsPublisherClient;
import android.car.vms.IVmsSubscriberClient;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;
import android.util.Slog;
import com.android.car.CarServiceBase;
import com.android.car.Manifest;
import com.android.car.R;
import com.android.car.VmsPublisherService;
import com.android.car.hal.VmsHalService;
import com.android.car.stats.CarStatsService;
import com.android.car.stats.VmsClientLogger;
import com.android.car.user.CarUserService;
import com.android.car.vms.VmsClientManager;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
/* loaded from: classes3.dex */
public class VmsClientManager implements CarServiceBase {
    private static final boolean DBG = false;
    private static final String HAL_CLIENT_NAME = "HalClient";
    private static final String TAG = "VmsClientManager";
    private static final String UNKNOWN_PACKAGE = "UnknownPackage";
    @GuardedBy({"mLock"})
    private final VmsBrokerService mBrokerService;
    private final Context mContext;
    @GuardedBy({"mLock"})
    private int mCurrentUser;
    @GuardedBy({"mLock"})
    private final Map<String, PublisherConnection> mCurrentUserClients;
    private final IntSupplier mGetCallingUid;
    @GuardedBy({"mLock"})
    private IVmsPublisherClient mHalClient;
    private final Handler mHandler;
    private final Object mLock;
    private final int mMillisBeforeRebind;
    private final PackageManager mPackageManager;
    @GuardedBy({"mLock"})
    private VmsPublisherService mPublisherService;
    private final CarStatsService mStatsService;
    @GuardedBy({"mLock"})
    private final Map<IBinder, SubscriberConnection> mSubscribers;
    @GuardedBy({"mLock"})
    private final Map<String, PublisherConnection> mSystemClients;
    @GuardedBy({"mLock"})
    private boolean mSystemUserUnlocked;
    @VisibleForTesting
    final Runnable mSystemUserUnlockedListener;
    @VisibleForTesting
    public final CarUserService.UserCallback mUserCallback;
    private final UserManager mUserManager;
    private final CarUserService mUserService;

    public /* synthetic */ void lambda$new$0$VmsClientManager() {
        synchronized (this.mLock) {
            this.mSystemUserUnlocked = true;
        }
        bindToSystemClients();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* renamed from: com.android.car.vms.VmsClientManager$1  reason: invalid class name */
    /* loaded from: classes3.dex */
    public class AnonymousClass1 implements CarUserService.UserCallback {
        AnonymousClass1() {
        }

        @Override // com.android.car.user.CarUserService.UserCallback
        public void onSwitchUser(int userId) {
            synchronized (VmsClientManager.this.mLock) {
                if (VmsClientManager.this.mCurrentUser != userId) {
                    VmsClientManager.this.mCurrentUser = userId;
                    VmsClientManager.this.terminate(VmsClientManager.this.mCurrentUserClients);
                    VmsClientManager.this.terminate(VmsClientManager.this.mSubscribers.values().stream().filter(new Predicate() { // from class: com.android.car.vms.-$$Lambda$VmsClientManager$1$R26R0irhK7az4kJsQtT8OBU5-Wg
                        @Override // java.util.function.Predicate
                        public final boolean test(Object obj) {
                            return VmsClientManager.AnonymousClass1.this.lambda$onSwitchUser$0$VmsClientManager$1((VmsClientManager.SubscriberConnection) obj);
                        }
                    }).filter(new Predicate() { // from class: com.android.car.vms.-$$Lambda$VmsClientManager$1$G0oUBWMFy5GwUPwwL6e3nkVNwyI
                        @Override // java.util.function.Predicate
                        public final boolean test(Object obj) {
                            return VmsClientManager.AnonymousClass1.lambda$onSwitchUser$1((VmsClientManager.SubscriberConnection) obj);
                        }
                    }));
                }
            }
            VmsClientManager.this.bindToUserClients();
        }

        public /* synthetic */ boolean lambda$onSwitchUser$0$VmsClientManager$1(SubscriberConnection subscriber) {
            return subscriber.mUserId != VmsClientManager.this.mCurrentUser;
        }

        /* JADX INFO: Access modifiers changed from: package-private */
        public static /* synthetic */ boolean lambda$onSwitchUser$1(SubscriberConnection subscriber) {
            return subscriber.mUserId != 0;
        }

        @Override // com.android.car.user.CarUserService.UserCallback
        public void onUserLockChanged(int userId, boolean unlocked) {
            synchronized (VmsClientManager.this.mLock) {
                if (VmsClientManager.this.mCurrentUser == userId && unlocked) {
                    VmsClientManager.this.bindToUserClients();
                }
            }
        }
    }

    public VmsClientManager(Context context, CarStatsService statsService, CarUserService userService, VmsBrokerService brokerService, VmsHalService halService) {
        this(context, statsService, userService, brokerService, halService, new Handler(Looper.getMainLooper()), new IntSupplier() { // from class: com.android.car.vms.-$$Lambda$OLUSIA110KxM3wbFP4L-5xrTvHw
            @Override // java.util.function.IntSupplier
            public final int getAsInt() {
                return Binder.getCallingUid();
            }
        });
    }

    @VisibleForTesting
    VmsClientManager(Context context, CarStatsService statsService, CarUserService userService, VmsBrokerService brokerService, VmsHalService halService, Handler handler, IntSupplier getCallingUid) {
        this.mLock = new Object();
        this.mSystemClients = new ArrayMap();
        this.mCurrentUserClients = new ArrayMap();
        this.mSubscribers = new ArrayMap();
        this.mSystemUserUnlockedListener = new Runnable() { // from class: com.android.car.vms.-$$Lambda$VmsClientManager$83IFADDQ3vicfnjrIuuda5zCh7g
            @Override // java.lang.Runnable
            public final void run() {
                VmsClientManager.this.lambda$new$0$VmsClientManager();
            }
        };
        this.mUserCallback = new AnonymousClass1();
        this.mContext = context;
        this.mPackageManager = context.getPackageManager();
        this.mUserManager = (UserManager) context.getSystemService("user");
        this.mStatsService = statsService;
        this.mUserService = userService;
        this.mCurrentUser = -10000;
        this.mBrokerService = brokerService;
        this.mHandler = handler;
        this.mGetCallingUid = getCallingUid;
        this.mMillisBeforeRebind = context.getResources().getInteger(R.integer.millisecondsBeforeRebindToVmsPublisher);
        halService.setClientManager(this);
    }

    public void setPublisherService(VmsPublisherService publisherService) {
        synchronized (this.mLock) {
            this.mPublisherService = publisherService;
        }
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
        this.mUserService.runOnUser0Unlock(this.mSystemUserUnlockedListener);
        this.mUserService.addUserCallback(this.mUserCallback);
    }

    @Override // com.android.car.CarServiceBase
    public void release() {
        this.mUserService.removeUserCallback(this.mUserCallback);
        synchronized (this.mLock) {
            if (this.mHalClient != null) {
                this.mPublisherService.onClientDisconnected(HAL_CLIENT_NAME);
            }
            terminate(this.mSystemClients);
            terminate(this.mCurrentUserClients);
            terminate(this.mSubscribers.values().stream());
        }
    }

    @Override // com.android.car.CarServiceBase
    public void dump(PrintWriter writer) {
        writer.println("*" + getClass().getSimpleName() + "*");
        synchronized (this.mLock) {
            writer.println("mCurrentUser:" + this.mCurrentUser);
            StringBuilder sb = new StringBuilder();
            sb.append("mHalClient: ");
            sb.append(this.mHalClient != null ? "connected" : "disconnected");
            writer.println(sb.toString());
            writer.println("mSystemClients:");
            dumpConnections(writer, this.mSystemClients);
            writer.println("mCurrentUserClients:");
            dumpConnections(writer, this.mCurrentUserClients);
            writer.println("mSubscribers:");
            for (SubscriberConnection subscriber : this.mSubscribers.values()) {
                writer.printf("\t%s\n", subscriber);
            }
        }
    }

    public void addSubscriber(IVmsSubscriberClient subscriberClient) {
        if (subscriberClient == null) {
            Slog.e(TAG, "Trying to add a null subscriber: " + getCallingPackage(this.mGetCallingUid.getAsInt()));
            throw new IllegalArgumentException("subscriber cannot be null.");
        }
        synchronized (this.mLock) {
            IBinder subscriberBinder = subscriberClient.asBinder();
            if (this.mSubscribers.containsKey(subscriberBinder)) {
                return;
            }
            int callingUid = this.mGetCallingUid.getAsInt();
            int subscriberUserId = UserHandle.getUserId(callingUid);
            if (subscriberUserId != this.mCurrentUser && subscriberUserId != 0) {
                throw new SecurityException("Caller must be foreground user or system");
            }
            SubscriberConnection subscriber = new SubscriberConnection(subscriberClient, callingUid, getCallingPackage(callingUid), subscriberUserId);
            try {
                subscriberBinder.linkToDeath(subscriber, 0);
                this.mSubscribers.put(subscriberBinder, subscriber);
            } catch (RemoteException e) {
                throw new IllegalStateException("Subscriber already dead: " + subscriber, e);
            }
        }
    }

    public void removeSubscriber(IVmsSubscriberClient subscriberClient) {
        synchronized (this.mLock) {
            SubscriberConnection subscriber = this.mSubscribers.get(subscriberClient.asBinder());
            if (subscriber != null) {
                subscriber.terminate();
            }
        }
    }

    public Collection<IVmsSubscriberClient> getAllSubscribers() {
        Collection<IVmsSubscriberClient> collection;
        synchronized (this.mLock) {
            collection = (Collection) this.mSubscribers.values().stream().map(new Function() { // from class: com.android.car.vms.-$$Lambda$VmsClientManager$oA4IgIma_VtPc__6JGugXh1SNi8
                @Override // java.util.function.Function
                public final Object apply(Object obj) {
                    IVmsSubscriberClient iVmsSubscriberClient;
                    iVmsSubscriberClient = ((VmsClientManager.SubscriberConnection) obj).mClient;
                    return iVmsSubscriberClient;
                }
            }).collect(Collectors.toList());
        }
        return collection;
    }

    public int getSubscriberUid(IVmsSubscriberClient subscriberClient) {
        int i;
        synchronized (this.mLock) {
            SubscriberConnection subscriber = this.mSubscribers.get(subscriberClient.asBinder());
            i = subscriber != null ? subscriber.mUid : -1;
        }
        return i;
    }

    public String getPackageName(IVmsSubscriberClient subscriberClient) {
        String str;
        synchronized (this.mLock) {
            SubscriberConnection subscriber = this.mSubscribers.get(subscriberClient.asBinder());
            str = subscriber != null ? subscriber.mPackageName : UNKNOWN_PACKAGE;
        }
        return str;
    }

    public void onHalConnected(IVmsPublisherClient publisherClient, IVmsSubscriberClient subscriberClient) {
        synchronized (this.mLock) {
            this.mHalClient = publisherClient;
            this.mPublisherService.onClientConnected(HAL_CLIENT_NAME, this.mHalClient);
            this.mSubscribers.put(subscriberClient.asBinder(), new SubscriberConnection(subscriberClient, Process.myUid(), HAL_CLIENT_NAME, 0));
        }
        this.mStatsService.getVmsClientLogger(Process.myUid()).logConnectionState(2);
    }

    public void onHalDisconnected() {
        synchronized (this.mLock) {
            if (this.mHalClient != null) {
                this.mPublisherService.onClientDisconnected(HAL_CLIENT_NAME);
                this.mStatsService.getVmsClientLogger(Process.myUid()).logConnectionState(3);
            }
            this.mHalClient = null;
            terminate(this.mSubscribers.values().stream().filter(new Predicate() { // from class: com.android.car.vms.-$$Lambda$VmsClientManager$t4wPaJRGBPId2--C_iiuZUaPfxw
                @Override // java.util.function.Predicate
                public final boolean test(Object obj) {
                    boolean equals;
                    equals = VmsClientManager.HAL_CLIENT_NAME.equals(((VmsClientManager.SubscriberConnection) obj).mPackageName);
                    return equals;
                }
            }));
        }
    }

    private void dumpConnections(PrintWriter writer, Map<String, PublisherConnection> connectionMap) {
        for (PublisherConnection connection : connectionMap.values()) {
            Object[] objArr = new Object[2];
            objArr[0] = connection.mName.getPackageName();
            objArr[1] = connection.mIsBound ? "connected" : "disconnected";
            writer.printf("\t%s: %s\n", objArr);
        }
    }

    private void bindToSystemClients() {
        String[] clientNames = this.mContext.getResources().getStringArray(R.array.vmsPublisherSystemClients);
        synchronized (this.mLock) {
            if (this.mSystemUserUnlocked) {
                Slog.i(TAG, "Attempting to bind " + clientNames.length + " system client(s)");
                for (String clientName : clientNames) {
                    bind(this.mSystemClients, clientName, UserHandle.SYSTEM);
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void bindToUserClients() {
        bindToSystemClients();
        synchronized (this.mLock) {
            if (this.mCurrentUser == -10000) {
                Slog.e(TAG, "Unknown user in foreground.");
            } else if (this.mCurrentUser == 0) {
                Slog.e(TAG, "System user in foreground. Userspace clients will not be bound.");
            } else if (!this.mUserManager.isUserUnlockingOrUnlocked(this.mCurrentUser)) {
                Slog.i(TAG, "Waiting for foreground user " + this.mCurrentUser + " to be unlocked.");
            } else {
                String[] clientNames = this.mContext.getResources().getStringArray(R.array.vmsPublisherUserClients);
                Slog.i(TAG, "Attempting to bind " + clientNames.length + " user client(s)");
                UserHandle currentUserHandle = UserHandle.of(this.mCurrentUser);
                for (String clientName : clientNames) {
                    bind(this.mCurrentUserClients, clientName, currentUserHandle);
                }
            }
        }
    }

    private void bind(Map<String, PublisherConnection> connectionMap, String clientName, UserHandle userHandle) {
        if (connectionMap.containsKey(clientName)) {
            Slog.i(TAG, "Already bound: " + clientName);
            return;
        }
        ComponentName name = ComponentName.unflattenFromString(clientName);
        if (name == null) {
            Slog.e(TAG, "Invalid client name: " + clientName);
            return;
        }
        try {
            ServiceInfo serviceInfo = this.mContext.getPackageManager().getServiceInfo(name, 268435456);
            VmsClientLogger statsLog = this.mStatsService.getVmsClientLogger(UserHandle.getUid(userHandle.getIdentifier(), serviceInfo.applicationInfo.uid));
            if (!Manifest.permission.BIND_VMS_CLIENT.equals(serviceInfo.permission)) {
                Slog.e(TAG, "Client service: " + clientName + " does not require " + Manifest.permission.BIND_VMS_CLIENT + " permission");
                statsLog.logConnectionState(5);
                return;
            }
            PublisherConnection connection = new PublisherConnection(name, userHandle, statsLog);
            if (connection.bind()) {
                Slog.i(TAG, "Client bound: " + connection);
                connectionMap.put(clientName, connection);
                return;
            }
            Slog.e(TAG, "Binding failed: " + connection);
        } catch (PackageManager.NameNotFoundException e) {
            Slog.w(TAG, "Client not installed: " + clientName);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void terminate(Map<String, PublisherConnection> connectionMap) {
        connectionMap.values().forEach(new Consumer() { // from class: com.android.car.vms.-$$Lambda$QPGcmArSOeHNXADDYqsSrHQJnSo
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                ((VmsClientManager.PublisherConnection) obj).terminate();
            }
        });
        connectionMap.clear();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes3.dex */
    public class PublisherConnection implements ServiceConnection {
        private IVmsPublisherClient mClientService;
        private final String mFullName;
        private final ComponentName mName;
        private final VmsClientLogger mStatsLog;
        private final UserHandle mUser;
        private boolean mIsBound = false;
        private boolean mIsTerminated = false;
        private boolean mRebindScheduled = false;

        PublisherConnection(ComponentName name, UserHandle user, VmsClientLogger statsLog) {
            this.mName = name;
            this.mUser = user;
            this.mFullName = this.mName.flattenToString() + " U=" + this.mUser.getIdentifier();
            this.mStatsLog = statsLog;
        }

        synchronized boolean bind() {
            if (this.mIsBound) {
                return true;
            }
            if (this.mIsTerminated) {
                return false;
            }
            this.mStatsLog.logConnectionState(1);
            Intent intent = new Intent();
            intent.setComponent(this.mName);
            try {
                this.mIsBound = VmsClientManager.this.mContext.bindServiceAsUser(intent, this, 1, VmsClientManager.this.mHandler, this.mUser);
            } catch (SecurityException e) {
                Slog.e(VmsClientManager.TAG, "While binding " + this.mFullName, e);
            }
            if (!this.mIsBound) {
                this.mStatsLog.logConnectionState(5);
            }
            return this.mIsBound;
        }

        synchronized void unbind() {
            if (this.mIsBound) {
                VmsClientManager.this.mContext.unbindService(this);
                this.mIsBound = false;
            }
        }

        synchronized void scheduleRebind() {
            if (this.mRebindScheduled) {
                return;
            }
            VmsClientManager.this.mHandler.postDelayed(new Runnable() { // from class: com.android.car.vms.-$$Lambda$xJ4lZcWagwMOLrGKDO2vmn3JGSE
                @Override // java.lang.Runnable
                public final void run() {
                    VmsClientManager.PublisherConnection.this.doRebind();
                }
            }, VmsClientManager.this.mMillisBeforeRebind);
            this.mRebindScheduled = true;
        }

        /* JADX INFO: Access modifiers changed from: package-private */
        public synchronized void doRebind() {
            this.mRebindScheduled = false;
            if (!this.mIsTerminated && this.mClientService == null) {
                Slog.i(VmsClientManager.TAG, "Rebinding: " + this.mFullName);
                unbind();
                bind();
            }
        }

        /* JADX INFO: Access modifiers changed from: package-private */
        public synchronized void terminate() {
            this.mIsTerminated = true;
            notifyOnDisconnect(4);
            unbind();
        }

        synchronized void notifyOnDisconnect(int connectionState) {
            if (this.mClientService != null) {
                VmsClientManager.this.mPublisherService.onClientDisconnected(this.mFullName);
                this.mClientService = null;
                this.mStatsLog.logConnectionState(connectionState);
            }
        }

        @Override // android.content.ServiceConnection
        public void onServiceConnected(ComponentName name, IBinder service) {
            this.mClientService = IVmsPublisherClient.Stub.asInterface(service);
            VmsClientManager.this.mPublisherService.onClientConnected(this.mFullName, this.mClientService);
            this.mStatsLog.logConnectionState(2);
        }

        @Override // android.content.ServiceConnection
        public void onServiceDisconnected(ComponentName name) {
            notifyOnDisconnect(3);
            scheduleRebind();
        }

        @Override // android.content.ServiceConnection
        public void onBindingDied(ComponentName name) {
            notifyOnDisconnect(3);
            scheduleRebind();
        }

        public String toString() {
            return this.mFullName;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void terminate(Stream<SubscriberConnection> subscribers) {
        ((List) subscribers.collect(Collectors.toList())).forEach(new Consumer() { // from class: com.android.car.vms.-$$Lambda$dN9fi-L-CKhct4e48nsf5mt2pSw
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                ((VmsClientManager.SubscriberConnection) obj).terminate();
            }
        });
    }

    private String getCallingPackage(int uid) {
        String packageName = this.mPackageManager.getNameForUid(uid);
        if (packageName == null) {
            return UNKNOWN_PACKAGE;
        }
        return packageName;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class SubscriberConnection implements IBinder.DeathRecipient {
        private final IVmsSubscriberClient mClient;
        private final String mPackageName;
        private final int mUid;
        private final int mUserId;

        SubscriberConnection(IVmsSubscriberClient subscriberClient, int uid, String packageName, int userId) {
            this.mClient = subscriberClient;
            this.mUid = uid;
            this.mPackageName = packageName;
            this.mUserId = userId;
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            terminate();
        }

        public String toString() {
            return this.mPackageName + " U=" + this.mUserId;
        }

        /* JADX INFO: Access modifiers changed from: package-private */
        public void terminate() {
            synchronized (VmsClientManager.this.mLock) {
                VmsClientManager.this.mBrokerService.removeDeadSubscriber(this.mClient);
                IBinder subscriberBinder = this.mClient.asBinder();
                try {
                    subscriberBinder.unlinkToDeath(this, 0);
                } catch (NoSuchElementException e) {
                }
                VmsClientManager.this.mSubscribers.remove(subscriberBinder);
            }
        }
    }
}
