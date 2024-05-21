package com.android.internal.car;

import android.app.ActivityManager;
import android.app.IActivityManager;
import android.app.admin.DevicePolicyManager;
import android.car.userlib.CarUserManagerHelper;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.UserInfo;
import android.hardware.automotive.audiocontrol.V1_0.IAudioControl;
import android.hardware.automotive.vehicle.V2_0.IVehicle;
import android.hidl.manager.V1_0.IServiceManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.IProgressListener;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.support.v4.media.session.PlaybackStateCompat;
import android.sysprop.CarProperties;
import android.util.Slog;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.TimingsTraceLog;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.car.ICarServiceHelper;
import com.android.internal.os.ProcessCpuTracker;
import com.android.server.SystemService;
import com.android.server.Watchdog;
import com.android.server.am.ActivityManagerService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
/* loaded from: classes3.dex */
public class CarServiceHelperService extends SystemService {
    private static final List<String> CAR_HAL_INTERFACES_OF_INTEREST = Arrays.asList(IVehicle.kInterfaceName, IAudioControl.kInterfaceName);
    private static final String CAR_SERVICE_INTERFACE = "android.car.ICar";
    private static final boolean DBG = true;
    private static final int ICAR_CALL_SET_CAR_SERVICE_HELPER = 0;
    private static final int ICAR_CALL_SET_SWITCH_USER = 2;
    private static final int ICAR_CALL_SET_USER_UNLOCK_STATUS = 1;
    private static final int ICAR_CALL_SET_WAKEUP_RESULT = 5;
    private static final String PROP_RESTART_RUNTIME = "ro.car.recovery.restart_runtime.enabled";
    private static final String TAG = "CarServiceHelper";
    @GuardedBy({"mLock"})
    private IBinder mCarService;
    private final ServiceConnection mCarServiceConnection;
    private final CarUserManagerHelper mCarUserManagerHelper;
    private final Context mContext;
    private final ICarServiceHelperImpl mHelper;
    @GuardedBy({"mLock"})
    private int mLastSwitchedUser;
    private final Object mLock;
    @GuardedBy({"mLock"})
    private boolean mSystemBootCompleted;
    private final UserManager mUserManager;
    @GuardedBy({"mLock"})
    private final HashMap<Integer, Boolean> mUserUnlockedStatus;

    /* JADX INFO: Access modifiers changed from: private */
    public static native int nativeForceSuspend(int i);

    private native void nativeInit();

    /* JADX INFO: Access modifiers changed from: private */
    public static native void nativeSetAutoSuspend(boolean z);

    public CarServiceHelperService(Context context) {
        this(context, new CarUserManagerHelper(context));
    }

    @VisibleForTesting
    CarServiceHelperService(Context context, CarUserManagerHelper carUserManagerHelper) {
        super(context);
        this.mLastSwitchedUser = -10000;
        this.mHelper = new ICarServiceHelperImpl();
        this.mLock = new Object();
        this.mUserUnlockedStatus = new HashMap<>();
        this.mCarServiceConnection = new ServiceConnection() { // from class: com.android.internal.car.CarServiceHelperService.1
            @Override // android.content.ServiceConnection
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                Slog.d(CarServiceHelperService.TAG, "onServiceConnected:" + iBinder);
                CarServiceHelperService.this.handleCarServiceConnection(iBinder);
            }

            @Override // android.content.ServiceConnection
            public void onServiceDisconnected(ComponentName componentName) {
                CarServiceHelperService.this.handleCarServiceCrash();
            }
        };
        this.mContext = context;
        this.mCarUserManagerHelper = carUserManagerHelper;
        this.mUserManager = UserManager.get(context);
    }

    public void onBootPhase(int phase) {
        Slog.d(TAG, "onBootPhase:" + phase);
        if (phase == 600) {
            checkForCarServiceConnection();
            setupAndStartUsers();
            checkForCarServiceConnection();
        } else if (phase == 1000) {
            TimingsTraceLog t = new TimingsTraceLog(TAG, (long) PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE_ENABLED);
            t.traceBegin("onBootPhase.completed");
            managePreCreatedUsers();
            boolean shouldNotify = false;
            synchronized (this.mLock) {
                this.mSystemBootCompleted = true;
                if (this.mCarService != null) {
                    shouldNotify = true;
                }
            }
            if (shouldNotify) {
                notifyAllUnlockedUsers();
            }
            t.traceEnd();
        }
    }

    public void onStart() {
        Intent intent = new Intent();
        intent.setPackage("com.android.car");
        intent.setAction(CAR_SERVICE_INTERFACE);
        if (!getContext().bindServiceAsUser(intent, this.mCarServiceConnection, 1, UserHandle.SYSTEM)) {
            Slog.wtf(TAG, "cannot start car service");
        }
        System.loadLibrary("car-framework-service-jni");
        nativeInit();
    }

    public void onUnlockUser(int userHandle) {
        handleUserLockStatusChange(userHandle, true);
        Slog.d(TAG, "User" + userHandle + " unlocked");
    }

    public void onStopUser(int userHandle) {
        handleUserLockStatusChange(userHandle, false);
    }

    public void onCleanupUser(int userHandle) {
        handleUserLockStatusChange(userHandle, false);
    }

    public void onSwitchUser(int userHandle) {
        synchronized (this.mLock) {
            this.mLastSwitchedUser = userHandle;
            if (this.mCarService == null) {
                return;
            }
            sendSwitchUserBindercall(userHandle);
        }
    }

    private void checkForCarServiceConnection() {
        synchronized (this.mLock) {
            if (this.mCarService != null) {
                return;
            }
            IBinder iBinder = ServiceManager.checkService("car_service");
            if (iBinder != null) {
                Slog.d(TAG, "Car service found through ServiceManager:" + iBinder);
                handleCarServiceConnection(iBinder);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleCarServiceConnection(IBinder iBinder) {
        synchronized (this.mLock) {
            if (this.mCarService == iBinder) {
                return;
            }
            if (this.mCarService != null) {
                Slog.i(TAG, "car service binder changed, was:" + this.mCarService + " new:" + iBinder);
            }
            this.mCarService = iBinder;
            int lastSwitchedUser = this.mLastSwitchedUser;
            boolean systemBootCompleted = this.mSystemBootCompleted;
            Slog.i(TAG, "**CarService connected**");
            sendSetCarServiceHelperBinderCall();
            if (systemBootCompleted) {
                notifyAllUnlockedUsers();
            }
            if (lastSwitchedUser != -10000) {
                sendSwitchUserBindercall(lastSwitchedUser);
            }
        }
    }

    private void handleUserLockStatusChange(int userHandle, boolean unlocked) {
        boolean shouldNotify = false;
        synchronized (this.mLock) {
            Boolean oldStatus = this.mUserUnlockedStatus.get(Integer.valueOf(userHandle));
            if (oldStatus == null || oldStatus.booleanValue() != unlocked) {
                this.mUserUnlockedStatus.put(Integer.valueOf(userHandle), Boolean.valueOf(unlocked));
                if (this.mCarService != null && this.mSystemBootCompleted) {
                    shouldNotify = true;
                }
            }
        }
        if (shouldNotify) {
            sendSetUserLockStatusBinderCall(userHandle, unlocked);
        }
    }

    private void setupAndStartUsers() {
        int targetUserId;
        DevicePolicyManager devicePolicyManager = (DevicePolicyManager) this.mContext.getSystemService(DevicePolicyManager.class);
        if (devicePolicyManager != null && devicePolicyManager.getUserProvisioningState() != 0) {
            Slog.i(TAG, "DevicePolicyManager active, skip user unlock/switch");
            return;
        }
        if (this.mCarUserManagerHelper.getAllUsers().size() == 0) {
            Slog.i(TAG, "Create new admin user and switch");
            UserInfo admin = this.mCarUserManagerHelper.createNewAdminUser();
            if (admin == null) {
                Slog.e(TAG, "cannot create admin user");
                return;
            }
            targetUserId = admin.id;
        } else {
            targetUserId = this.mCarUserManagerHelper.getInitialUser();
            Slog.i(TAG, "Switching to user " + targetUserId + " on boot");
        }
        IActivityManager am = ActivityManager.getService();
        if (am == null) {
            Slog.wtf(TAG, "cannot get ActivityManagerService");
        } else if (targetUserId == 0) {
        } else {
            TimingsTraceLog t = new TimingsTraceLog(TAG, (long) PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE_ENABLED);
            unlockSystemUser(t, am);
            t.traceBegin("ForegroundUserStart" + targetUserId);
            try {
                if (!am.startUserInForegroundWithListener(targetUserId, (IProgressListener) null)) {
                    Slog.e(TAG, "cannot start foreground user:" + targetUserId);
                } else {
                    this.mCarUserManagerHelper.setLastActiveUser(targetUserId);
                }
            } catch (RemoteException e) {
                Slog.wtf("RemoteException from AMS", e);
            }
            t.traceEnd();
        }
    }

    private void unlockSystemUser(TimingsTraceLog t, IActivityManager am) {
        t.traceBegin("UnlockSystemUser");
        try {
            try {
                boolean started = am.startUserInBackground(0);
                if (!started) {
                    Slog.w(TAG, "could not restart system user in foreground; trying unlock instead");
                    t.traceBegin("forceUnlockSystemUser");
                    boolean unlocked = am.unlockUser(0, (byte[]) null, (byte[]) null, (IProgressListener) null);
                    t.traceEnd();
                    if (!unlocked) {
                        Slog.w(TAG, "could not unlock system user neither");
                    }
                }
            } catch (RemoteException e) {
                Slog.wtf("RemoteException from AMS", e);
            }
        } finally {
            t.traceEnd();
        }
    }

    private void managePreCreatedUsers() {
        int numberRequestedGuests = ((Integer) CarProperties.number_pre_created_guests().orElse(0)).intValue();
        int numberRequestedUsers = ((Integer) CarProperties.number_pre_created_users().orElse(0)).intValue();
        Slog.i(TAG, "managePreCreatedUsers(): OEM asked for " + numberRequestedGuests + " guests and " + numberRequestedUsers + " users");
        if (numberRequestedGuests >= 0 && numberRequestedUsers >= 0) {
            if (numberRequestedGuests == 0 && numberRequestedUsers == 0) {
                Slog.i(TAG, "managePreCreatedUsers(): not defined by OEM");
                return;
            }
            List<UserInfo> allUsers = this.mUserManager.getUsers(true, true, false);
            int allUsersSize = allUsers.size();
            Slog.d(TAG, "preCreateUsers: total users size is " + allUsersSize);
            final SparseBooleanArray invalidUsers = new SparseBooleanArray();
            int numberExistingGuests = 0;
            int numberExistingUsers = 0;
            for (int i = 0; i < allUsersSize; i++) {
                UserInfo user = allUsers.get(i);
                if (user.preCreated) {
                    if (!user.isInitialized()) {
                        Slog.w(TAG, "Found invalid pre-created user that needs to be removed: " + user.toFullString());
                        invalidUsers.append(user.id, true);
                    } else if (user.isGuest()) {
                        numberExistingGuests++;
                    } else {
                        numberExistingUsers++;
                    }
                }
            }
            Slog.i(TAG, "managePreCreatedUsers(): system already has " + numberExistingGuests + " pre-created guests," + numberExistingUsers + " pre-created users, and these invalid users: " + invalidUsers);
            final int numberGuests = numberRequestedGuests - numberExistingGuests;
            final int numberUsers = numberRequestedUsers - numberExistingUsers;
            final int numberInvalidUsers = invalidUsers.size();
            if (numberGuests <= 0 && numberUsers <= 0 && numberInvalidUsers == 0) {
                Slog.i(TAG, "managePreCreatedUsers(): all pre-created and no invalid ones");
                return;
            } else {
                new Thread(new Runnable() { // from class: com.android.internal.car.-$$Lambda$CarServiceHelperService$lSG7ihquHlIynixd2mjj2EWhzL4
                    @Override // java.lang.Runnable
                    public final void run() {
                        CarServiceHelperService.this.lambda$managePreCreatedUsers$0$CarServiceHelperService(numberUsers, numberGuests, numberInvalidUsers, invalidUsers);
                    }
                }, "CarServiceHelperManagePreCreatedUsers").start();
                return;
            }
        }
        Slog.w(TAG, "preCreateUsers(): invalid values provided by OEM; number_pre_created_guests=" + numberRequestedGuests + ", number_pre_created_users=" + numberRequestedUsers);
    }

    public /* synthetic */ void lambda$managePreCreatedUsers$0$CarServiceHelperService(int numberUsers, int numberGuests, int numberInvalidUsers, SparseBooleanArray invalidUsers) {
        TimingsTraceLog t = new TimingsTraceLog(TAG, (long) PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE_ENABLED);
        t.traceBegin("preCreateUsers");
        if (numberUsers > 0) {
            preCreateUsers(t, numberUsers, false);
        }
        if (numberGuests > 0) {
            preCreateUsers(t, numberGuests, true);
        }
        t.traceEnd();
        if (numberInvalidUsers > 0) {
            t.traceBegin("removeInvalidPreCreatedUsers");
            for (int i = 0; i < numberInvalidUsers; i++) {
                int userId = invalidUsers.keyAt(i);
                Slog.i(TAG, "removing invalid pre-created user " + userId);
                this.mUserManager.removeUser(userId);
            }
            t.traceEnd();
        }
    }

    private void preCreateUsers(TimingsTraceLog t, int size, boolean isGuest) {
        StringBuilder sb;
        String str;
        if (isGuest) {
            sb = new StringBuilder();
            str = "preCreateGuests-";
        } else {
            sb = new StringBuilder();
            str = "preCreateUsers-";
        }
        sb.append(str);
        sb.append(size);
        String msg = sb.toString();
        t.traceBegin(msg);
        for (int i = 1; i <= size; i++) {
            UserInfo preCreated = preCreateUsers(t, isGuest);
            if (preCreated == null) {
                StringBuilder sb2 = new StringBuilder();
                sb2.append("Could not pre-create ");
                sb2.append(isGuest ? " guest " : "");
                sb2.append(" user #");
                sb2.append(i);
                Slog.w(TAG, sb2.toString());
            }
        }
        t.traceEnd();
    }

    public UserInfo preCreateUsers(TimingsTraceLog t, boolean isGuest) {
        String traceMsg;
        int flags = 0;
        if (isGuest) {
            flags = 0 | 4;
            traceMsg = "pre-create-guest";
        } else {
            traceMsg = "pre-create-user";
        }
        t.traceBegin(traceMsg);
        UserManager um = (UserManager) this.mContext.getSystemService("user");
        UserInfo user = um.preCreateUser(flags);
        if (user == null) {
            try {
                Slog.w(TAG, "couldn't " + traceMsg);
                return null;
            } finally {
                t.traceEnd();
            }
        }
        return user;
    }

    private void notifyAllUnlockedUsers() {
        LinkedList<Integer> users = new LinkedList<>();
        synchronized (this.mLock) {
            for (Map.Entry<Integer, Boolean> entry : this.mUserUnlockedStatus.entrySet()) {
                if (entry.getValue().booleanValue()) {
                    users.add(entry.getKey());
                }
            }
        }
        Slog.d(TAG, "notifyAllUnlockedUsers:" + users);
        Iterator<Integer> it = users.iterator();
        while (it.hasNext()) {
            Integer i = it.next();
            sendSetUserLockStatusBinderCall(i.intValue(), true);
        }
    }

    private void sendSetCarServiceHelperBinderCall() {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(CAR_SERVICE_INTERFACE);
        data.writeStrongBinder(this.mHelper.asBinder());
        sendBinderCallToCarService(data, 0);
    }

    private void sendSetUserLockStatusBinderCall(int userHandle, boolean unlocked) {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(CAR_SERVICE_INTERFACE);
        data.writeInt(userHandle);
        data.writeInt(unlocked ? 1 : 0);
        sendBinderCallToCarService(data, 1);
    }

    private void sendSwitchUserBindercall(int userHandle) {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(CAR_SERVICE_INTERFACE);
        data.writeInt(userHandle);
        sendBinderCallToCarService(data, 2);
    }

    private void sendBinderCallToCarService(Parcel data, int callNumber) {
        IBinder carService;
        synchronized (this.mLock) {
            carService = this.mCarService;
        }
        try {
            try {
                carService.transact(callNumber + 1, data, null, 1);
            } catch (RemoteException e) {
                Slog.w(TAG, "RemoteException from car service", e);
                handleCarServiceCrash();
            }
        } finally {
            data.recycle();
        }
    }

    private static ArrayList<Integer> getInterestingHalPids() {
        try {
            IServiceManager serviceManager = IServiceManager.getService();
            ArrayList<IServiceManager.InstanceDebugInfo> dump = serviceManager.debugDump();
            HashSet<Integer> pids = new HashSet<>();
            Iterator<IServiceManager.InstanceDebugInfo> it = dump.iterator();
            while (it.hasNext()) {
                IServiceManager.InstanceDebugInfo info = it.next();
                if (info.pid != -1) {
                    if (Watchdog.HAL_INTERFACES_OF_INTEREST.contains(info.interfaceName) || CAR_HAL_INTERFACES_OF_INTEREST.contains(info.interfaceName)) {
                        pids.add(Integer.valueOf(info.pid));
                    }
                }
            }
            return new ArrayList<>(pids);
        } catch (RemoteException e) {
            return new ArrayList<>();
        }
    }

    private static ArrayList<Integer> getInterestingNativePids() {
        ArrayList<Integer> pids = getInterestingHalPids();
        int[] nativePids = Process.getPidsForCommands(Watchdog.NATIVE_STACKS_OF_INTEREST);
        if (nativePids != null) {
            pids.ensureCapacity(pids.size() + nativePids.length);
            for (int i : nativePids) {
                pids.add(Integer.valueOf(i));
            }
        }
        return pids;
    }

    private static void dumpServiceStacks() {
        ArrayList<Integer> pids = new ArrayList<>();
        pids.add(Integer.valueOf(Process.myPid()));
        ActivityManagerService.dumpStackTraces(pids, (ProcessCpuTracker) null, (SparseArray) null, getInterestingNativePids());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleCarServiceCrash() {
        boolean restartOnServiceCrash = SystemProperties.getBoolean(PROP_RESTART_RUNTIME, false);
        dumpServiceStacks();
        if (restartOnServiceCrash) {
            Slog.w(TAG, "*** CARHELPER KILLING SYSTEM PROCESS: CarService crash");
            Slog.w(TAG, "*** GOODBYE!");
            Process.killProcess(Process.myPid());
            System.exit(10);
            return;
        }
        Slog.w(TAG, "*** CARHELPER ignoring: CarService crash");
    }

    private void autoWakeResultFromNative(boolean success) {
        if (this.mCarService != null) {
            sendWakeupResultBindercall(success);
        }
    }

    private void sendWakeupResultBindercall(boolean success) {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(CAR_SERVICE_INTERFACE);
        data.writeBoolean(success);
        sendBinderCallToCarService(data, 5);
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class ICarServiceHelperImpl extends ICarServiceHelper.Stub {
        private ICarServiceHelperImpl() {
        }

        @Override // com.android.internal.car.ICarServiceHelper
        public int forceSuspend(int timeoutMs) {
            CarServiceHelperService.this.mContext.enforceCallingOrSelfPermission("android.permission.DEVICE_POWER", null);
            long ident = Binder.clearCallingIdentity();
            try {
                int retVal = CarServiceHelperService.nativeForceSuspend(timeoutMs);
                return retVal;
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // com.android.internal.car.ICarServiceHelper
        public void setAutoSuspendEnable(boolean enable) {
            CarServiceHelperService.this.mContext.enforceCallingOrSelfPermission("android.permission.DEVICE_POWER", null);
            long ident = Binder.clearCallingIdentity();
            try {
                CarServiceHelperService.nativeSetAutoSuspend(enable);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    }
}
