package com.android.car.pm;

import android.car.userlib.CarUserManagerHelper;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.Slog;
import com.android.car.CarLocalServices;
import com.android.car.CarLog;
import com.android.car.R;
import com.android.car.user.CarUserService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
/* loaded from: classes3.dex */
class VendorServiceController implements CarUserService.UserCallback {
    private static final boolean DBG = true;
    private static final int MSG_SWITCH_USER = 1;
    private static final int MSG_USER_LOCK_CHANGED = 2;
    private CarUserService mCarUserService;
    private final Context mContext;
    private final Handler mHandler;
    private final UserManager mUserManager;
    private final CarUserManagerHelper mUserManagerHelper;
    private final List<VendorServiceInfo> mVendorServiceInfos = new ArrayList();
    private final HashMap<ConnectionKey, VendorServiceConnection> mConnections = new HashMap<>();

    /* JADX INFO: Access modifiers changed from: package-private */
    public VendorServiceController(Context context, Looper looper, CarUserManagerHelper userManagerHelper) {
        this.mContext = context;
        this.mUserManager = (UserManager) context.getSystemService(UserManager.class);
        this.mUserManagerHelper = userManagerHelper;
        this.mHandler = new Handler(looper) { // from class: com.android.car.pm.VendorServiceController.1
            @Override // android.os.Handler
            public void handleMessage(Message msg) {
                VendorServiceController.this.handleMessage(msg);
            }
        };
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleMessage(Message msg) {
        int i = msg.what;
        if (i == 1) {
            int userId = msg.arg1;
            doSwitchUser(userId);
        } else if (i == 2) {
            int userId2 = msg.arg1;
            boolean locked = msg.arg2 == 1;
            doUserLockChanged(userId2, locked);
        } else {
            Slog.e(CarLog.TAG_PACKAGE, "Unexpected message " + msg);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void init() {
        if (!loadXmlConfiguration()) {
            return;
        }
        this.mCarUserService = (CarUserService) CarLocalServices.getService(CarUserService.class);
        this.mCarUserService.addUserCallback(this);
        startOrBindServicesIfNeeded();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void release() {
        CarUserService carUserService = this.mCarUserService;
        if (carUserService != null) {
            carUserService.removeUserCallback(this);
        }
        for (ConnectionKey key : this.mConnections.keySet()) {
            stopOrUnbindService(key.mVendorServiceInfo, key.mUserHandle);
        }
        this.mVendorServiceInfos.clear();
        this.mConnections.clear();
    }

    private void doSwitchUser(int userId) {
        int fgUser = this.mUserManagerHelper.getCurrentForegroundUserId();
        if (fgUser != userId) {
            Slog.w(CarLog.TAG_PACKAGE, "Received userSwitch event for user " + userId + " while current foreground user is " + fgUser + ". Ignore the switch user event.");
            return;
        }
        for (VendorServiceConnection connection : this.mConnections.values()) {
            int connectedUserId = connection.mUser.getIdentifier();
            if (connectedUserId != 0 && connectedUserId != userId) {
                connection.stopOrUnbindService();
            }
        }
        if (userId == 0) {
            Slog.e(CarLog.TAG_PACKAGE, "Unexpected to receive switch user event for system user");
        } else {
            startOrBindServicesForUser(UserHandle.of(userId));
        }
    }

    private void doUserLockChanged(int userId, boolean unlocked) {
        int currentUserId = this.mUserManagerHelper.getCurrentForegroundUserId();
        Slog.i(CarLog.TAG_PACKAGE, "onUserLockedChanged, user: " + userId + ", unlocked: " + unlocked + ", currentUser: " + currentUserId);
        if (unlocked && (userId == currentUserId || userId == 0)) {
            startOrBindServicesForUser(UserHandle.of(userId));
        } else if (!unlocked && userId != 0) {
            for (ConnectionKey key : this.mConnections.keySet()) {
                if (key.mUserHandle.getIdentifier() == userId) {
                    stopOrUnbindService(key.mVendorServiceInfo, key.mUserHandle);
                }
            }
        }
    }

    private void startOrBindServicesForUser(UserHandle user) {
        boolean unlocked = this.mUserManager.isUserUnlockingOrUnlocked(user);
        boolean systemUser = UserHandle.SYSTEM.equals(user);
        for (VendorServiceInfo service : this.mVendorServiceInfos) {
            boolean triggerChecked = true;
            boolean userScopeChecked = (!systemUser && service.isForegroundUserService()) || (systemUser && service.isSystemUserService());
            if (!service.shouldStartAsap() && (!unlocked || !service.shouldStartOnUnlock())) {
                triggerChecked = false;
            }
            if (userScopeChecked && triggerChecked) {
                startOrBindService(service, user);
            }
        }
    }

    private void startOrBindServicesIfNeeded() {
        int userId = this.mUserManagerHelper.getCurrentForegroundUserId();
        startOrBindServicesForUser(UserHandle.SYSTEM);
        if (userId > 0) {
            startOrBindServicesForUser(UserHandle.of(userId));
        }
    }

    @Override // com.android.car.user.CarUserService.UserCallback
    public void onUserLockChanged(int userId, boolean unlocked) {
        Message msg = this.mHandler.obtainMessage(2, userId, unlocked ? 1 : 0);
        this.mHandler.executeOrSendMessage(msg);
    }

    @Override // com.android.car.user.CarUserService.UserCallback
    public void onSwitchUser(int userId) {
        this.mHandler.removeMessages(1);
        Message msg = this.mHandler.obtainMessage(1, userId, 0);
        this.mHandler.executeOrSendMessage(msg);
    }

    private void startOrBindService(VendorServiceInfo service, UserHandle user) {
        ConnectionKey key = ConnectionKey.of(service, user);
        VendorServiceConnection connection = getOrCreateConnection(key);
        if (!connection.startOrBindService()) {
            Slog.e(CarLog.TAG_PACKAGE, "Failed to start or bind service " + service);
            this.mConnections.remove(key);
        }
    }

    private void stopOrUnbindService(VendorServiceInfo service, UserHandle user) {
        ConnectionKey key = ConnectionKey.of(service, user);
        VendorServiceConnection connection = this.mConnections.get(key);
        if (connection != null) {
            connection.stopOrUnbindService();
        }
    }

    private VendorServiceConnection getOrCreateConnection(ConnectionKey key) {
        VendorServiceConnection connection = this.mConnections.get(key);
        if (connection == null) {
            VendorServiceConnection connection2 = new VendorServiceConnection(this.mContext, this.mHandler, this.mUserManagerHelper, key.mVendorServiceInfo, key.mUserHandle);
            this.mConnections.put(key, connection2);
            return connection2;
        }
        return connection;
    }

    private boolean loadXmlConfiguration() {
        String[] stringArray;
        Resources res = this.mContext.getResources();
        for (String rawServiceInfo : res.getStringArray(R.array.config_earlyStartupServices)) {
            if (!TextUtils.isEmpty(rawServiceInfo)) {
                VendorServiceInfo service = VendorServiceInfo.parse(rawServiceInfo);
                this.mVendorServiceInfos.add(service);
                Slog.i(CarLog.TAG_PACKAGE, "Registered vendor service: " + service);
            }
        }
        Slog.i(CarLog.TAG_PACKAGE, "Found " + this.mVendorServiceInfos.size() + " services to be started/bound");
        return !this.mVendorServiceInfos.isEmpty();
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static class VendorServiceConnection implements ServiceConnection {
        private static final int FAILURE_COUNTER_RESET_TIMEOUT = 300000;
        private static final int MAX_RECENT_FAILURES = 5;
        private static final int MSG_FAILURE_COUNTER_RESET = 1;
        private static final int MSG_REBIND = 0;
        private static final int REBIND_DELAY_MS = 5000;
        private final Context mContext;
        private final Handler mFailureHandler;
        private final Handler mHandler;
        private final UserHandle mUser;
        private final CarUserManagerHelper mUserManagerHelper;
        private final VendorServiceInfo mVendorServiceInfo;
        private int mRecentFailures = 0;
        private boolean mBound = false;
        private boolean mStarted = false;
        private boolean mStopRequested = false;

        VendorServiceConnection(Context context, Handler handler, CarUserManagerHelper userManagerHelper, VendorServiceInfo vendorServiceInfo, UserHandle user) {
            this.mContext = context;
            this.mHandler = handler;
            this.mUserManagerHelper = userManagerHelper;
            this.mVendorServiceInfo = vendorServiceInfo;
            this.mUser = user;
            this.mFailureHandler = new Handler(handler.getLooper()) { // from class: com.android.car.pm.VendorServiceController.VendorServiceConnection.1
                @Override // android.os.Handler
                public void handleMessage(Message msg) {
                    VendorServiceConnection.this.handleFailureMessage(msg);
                }
            };
        }

        boolean startOrBindService() {
            if (this.mStarted || this.mBound) {
                return true;
            }
            Slog.d(CarLog.TAG_PACKAGE, "startOrBindService " + this.mVendorServiceInfo.toShortString() + ", as user: " + this.mUser + ", bind: " + this.mVendorServiceInfo.shouldBeBound() + ", stack:  " + Debug.getCallers(5));
            this.mStopRequested = false;
            Intent intent = this.mVendorServiceInfo.getIntent();
            if (this.mVendorServiceInfo.shouldBeBound()) {
                return this.mContext.bindServiceAsUser(intent, this, 1, this.mHandler, this.mUser);
            }
            if (this.mVendorServiceInfo.shouldBeStartedInForeground()) {
                this.mStarted = this.mContext.startForegroundServiceAsUser(intent, this.mUser) != null;
                return this.mStarted;
            }
            this.mStarted = this.mContext.startServiceAsUser(intent, this.mUser) != null;
            return this.mStarted;
        }

        void stopOrUnbindService() {
            this.mStopRequested = true;
            if (this.mStarted) {
                this.mContext.stopServiceAsUser(this.mVendorServiceInfo.getIntent(), this.mUser);
                this.mStarted = false;
            } else if (this.mBound) {
                this.mContext.unbindService(this);
                this.mBound = false;
            }
        }

        @Override // android.content.ServiceConnection
        public void onServiceConnected(ComponentName name, IBinder service) {
            this.mBound = true;
            Slog.d(CarLog.TAG_PACKAGE, "onServiceConnected, name: " + name);
            if (this.mStopRequested) {
                stopOrUnbindService();
            }
        }

        @Override // android.content.ServiceConnection
        public void onServiceDisconnected(ComponentName name) {
            this.mBound = false;
            Slog.d(CarLog.TAG_PACKAGE, "onServiceDisconnected, name: " + name);
            tryToRebind();
        }

        @Override // android.content.ServiceConnection
        public void onBindingDied(ComponentName name) {
            this.mBound = false;
            tryToRebind();
        }

        private void tryToRebind() {
            if (this.mStopRequested) {
                return;
            }
            if (UserHandle.of(this.mUserManagerHelper.getCurrentForegroundUserId()).equals(this.mUser) || UserHandle.SYSTEM.equals(this.mUser)) {
                Handler handler = this.mFailureHandler;
                handler.sendMessageDelayed(handler.obtainMessage(0), 5000L);
                scheduleResetFailureCounter();
                return;
            }
            Slog.w(CarLog.TAG_PACKAGE, "No need to rebind anymore as the user " + this.mUser + " is no longer in foreground.");
        }

        private void scheduleResetFailureCounter() {
            this.mFailureHandler.removeMessages(1);
            Handler handler = this.mFailureHandler;
            handler.sendMessageDelayed(handler.obtainMessage(1), 300000L);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void handleFailureMessage(Message msg) {
            int i = msg.what;
            if (i != 0) {
                if (i == 1) {
                    this.mRecentFailures = 0;
                    return;
                }
                Slog.e(CarLog.TAG_PACKAGE, "Unexpected message received in failure handler: " + msg.what);
            } else if (this.mRecentFailures < 5 && !this.mBound) {
                Slog.i(CarLog.TAG_PACKAGE, "Attempting to rebind to the service " + this.mVendorServiceInfo.toShortString());
                this.mRecentFailures = this.mRecentFailures + 1;
                startOrBindService();
            } else {
                Slog.w(CarLog.TAG_PACKAGE, "Exceeded maximum number of attempts to rebindto the service " + this.mVendorServiceInfo.toShortString());
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static class ConnectionKey {
        private final UserHandle mUserHandle;
        private final VendorServiceInfo mVendorServiceInfo;

        private ConnectionKey(VendorServiceInfo service, UserHandle user) {
            this.mVendorServiceInfo = service;
            this.mUserHandle = user;
        }

        static ConnectionKey of(VendorServiceInfo service, UserHandle user) {
            return new ConnectionKey(service, user);
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o instanceof ConnectionKey) {
                ConnectionKey that = (ConnectionKey) o;
                return Objects.equals(this.mUserHandle, that.mUserHandle) && Objects.equals(this.mVendorServiceInfo, that.mVendorServiceInfo);
            }
            return false;
        }

        public int hashCode() {
            return Objects.hash(this.mUserHandle, this.mVendorServiceInfo);
        }
    }
}
