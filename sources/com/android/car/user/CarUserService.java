package com.android.car.user;

import android.app.IActivityManager;
import android.app.IStopUserCallback;
import android.car.encryptionrunner.DummyEncryptionRunner;
import android.car.userlib.CarUserManagerHelper;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.location.LocationManager;
import android.os.IProgressListener;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;
import android.util.Slog;
import com.android.car.CarServiceBase;
import com.android.car.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;
/* loaded from: classes3.dex */
public class CarUserService extends BroadcastReceiver implements CarServiceBase {
    private static final String TAG = "CarUserService";
    private final IActivityManager mAm;
    private final CarUserManagerHelper mCarUserManagerHelper;
    private final Context mContext;
    private final int mMaxRunningUsers;
    @GuardedBy({"mLock"})
    private boolean mUser0Unlocked;
    private final UserManager mUserManager;
    private final Object mLock = new Object();
    @GuardedBy({"mLock"})
    private final ArrayList<Runnable> mUser0UnlockTasks = new ArrayList<>();
    @GuardedBy({"mLock"})
    private final ArrayList<Integer> mBackgroundUsersToRestart = new ArrayList<>();
    @GuardedBy({"mLock"})
    private final ArrayList<Integer> mBackgroundUsersRestartedHere = new ArrayList<>();
    private final CopyOnWriteArrayList<UserCallback> mUserCallbacks = new CopyOnWriteArrayList<>();

    /* loaded from: classes3.dex */
    public interface UserCallback {
        void onSwitchUser(int i);

        void onUserLockChanged(int i, boolean z);
    }

    public CarUserService(Context context, CarUserManagerHelper carUserManagerHelper, IActivityManager am, int maxRunningUsers) {
        if (Log.isLoggable(TAG, 3)) {
            Slog.d(TAG, "constructed");
        }
        this.mContext = context;
        this.mCarUserManagerHelper = carUserManagerHelper;
        this.mAm = am;
        this.mMaxRunningUsers = maxRunningUsers;
        this.mUserManager = (UserManager) context.getSystemService("user");
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
        if (Log.isLoggable(TAG, 3)) {
            Slog.d(TAG, DummyEncryptionRunner.INIT);
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.USER_SWITCHED");
        this.mContext.registerReceiver(this, filter);
    }

    @Override // com.android.car.CarServiceBase
    public void release() {
        if (Log.isLoggable(TAG, 3)) {
            Slog.d(TAG, "release");
        }
        this.mContext.unregisterReceiver(this);
    }

    @Override // com.android.car.CarServiceBase
    public void dump(PrintWriter writer) {
        boolean user0Unlocked;
        ArrayList<Integer> backgroundUsersToRestart;
        ArrayList<Integer> backgroundUsersRestarted;
        writer.println(TAG);
        synchronized (this.mLock) {
            user0Unlocked = this.mUser0Unlocked;
            backgroundUsersToRestart = new ArrayList<>(this.mBackgroundUsersToRestart);
            backgroundUsersRestarted = new ArrayList<>(this.mBackgroundUsersRestartedHere);
        }
        writer.println("User0Unlocked: " + user0Unlocked);
        writer.println("maxRunningUsers:" + this.mMaxRunningUsers);
        writer.println("BackgroundUsersToRestart:" + backgroundUsersToRestart);
        writer.println("BackgroundUsersRestarted:" + backgroundUsersRestarted);
        writer.println("Relevant overlayable  properties");
        Resources res = this.mContext.getResources();
        writer.printf("%sowner_name=%s\n", "  ", res.getString(17040546));
        writer.printf("%sdefault_guest_name=%s\n", "  ", res.getString(R.string.default_guest_name));
    }

    private void updateDefaultUserRestriction() {
        if (Settings.Global.getInt(this.mContext.getContentResolver(), "android.car.DEFAULT_USER_RESTRICTIONS_SET", 0) == 0) {
            if (this.mCarUserManagerHelper.isHeadlessSystemUser()) {
                setSystemUserRestrictions();
            }
            this.mCarUserManagerHelper.initDefaultGuestRestrictions();
            Settings.Global.putInt(this.mContext.getContentResolver(), "android.car.DEFAULT_USER_RESTRICTIONS_SET", 1);
        }
    }

    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        int currentUser;
        if (Log.isLoggable(TAG, 3)) {
            Slog.d(TAG, "onReceive " + intent);
        }
        if ("android.intent.action.USER_SWITCHED".equals(intent.getAction()) && (currentUser = intent.getIntExtra("android.intent.extra.user_handle", -1)) > 0) {
            this.mCarUserManagerHelper.setLastActiveUser(currentUser);
        }
    }

    public void addUserCallback(UserCallback callback) {
        this.mUserCallbacks.add(callback);
    }

    public void removeUserCallback(UserCallback callback) {
        this.mUserCallbacks.remove(callback);
    }

    public void setUserLockStatus(int userHandle, boolean unlocked) {
        Iterator<UserCallback> it = this.mUserCallbacks.iterator();
        while (it.hasNext()) {
            UserCallback callback = it.next();
            callback.onUserLockChanged(userHandle, unlocked);
        }
        if (!unlocked) {
            return;
        }
        ArrayList<Runnable> tasks = null;
        synchronized (this.mLock) {
            if (userHandle == 0) {
                if (!this.mUser0Unlocked) {
                    updateDefaultUserRestriction();
                    tasks = new ArrayList<>(this.mUser0UnlockTasks);
                    this.mUser0UnlockTasks.clear();
                    this.mUser0Unlocked = unlocked;
                }
            } else {
                Integer user = Integer.valueOf(userHandle);
                if (this.mCarUserManagerHelper.isPersistentUser(userHandle)) {
                    if (userHandle == this.mCarUserManagerHelper.getCurrentForegroundUserId()) {
                        this.mBackgroundUsersToRestart.remove(user);
                        this.mBackgroundUsersToRestart.add(0, user);
                    }
                    if (this.mBackgroundUsersToRestart.size() > this.mMaxRunningUsers - 1) {
                        int userToDrop = this.mBackgroundUsersToRestart.get(this.mBackgroundUsersToRestart.size() - 1).intValue();
                        Slog.i(TAG, "New user unlocked:" + userHandle + ", dropping least recently user from restart list:" + userToDrop);
                        this.mBackgroundUsersToRestart.remove(this.mBackgroundUsersToRestart.size() + (-1));
                    }
                }
            }
        }
        if (tasks != null && tasks.size() > 0) {
            Slog.d(TAG, "User0 unlocked, run queued tasks:" + tasks.size());
            Iterator<Runnable> it2 = tasks.iterator();
            while (it2.hasNext()) {
                Runnable r = it2.next();
                r.run();
            }
        }
    }

    public ArrayList<Integer> startAllBackgroundUsers() {
        ArrayList<Integer> users;
        synchronized (this.mLock) {
            users = new ArrayList<>(this.mBackgroundUsersToRestart);
            this.mBackgroundUsersRestartedHere.clear();
            this.mBackgroundUsersRestartedHere.addAll(this.mBackgroundUsersToRestart);
        }
        ArrayList<Integer> startedUsers = new ArrayList<>();
        Iterator<Integer> it = users.iterator();
        while (it.hasNext()) {
            Integer user = it.next();
            if (user.intValue() != this.mCarUserManagerHelper.getCurrentForegroundUserId()) {
                try {
                    if (this.mAm.startUserInBackground(user.intValue())) {
                        if (this.mUserManager.isUserUnlockingOrUnlocked(user.intValue())) {
                            startedUsers.add(user);
                        } else if (this.mAm.unlockUser(user.intValue(), (byte[]) null, (byte[]) null, (IProgressListener) null)) {
                            startedUsers.add(user);
                        } else {
                            Slog.w(TAG, "Background user started but cannot be unlocked:" + user);
                            if (this.mUserManager.isUserRunning(user.intValue())) {
                                startedUsers.add(user);
                            }
                        }
                    }
                } catch (RemoteException e) {
                }
            }
        }
        synchronized (this.mLock) {
            ArrayList<Integer> usersToRemove = new ArrayList<>();
            Iterator<Integer> it2 = this.mBackgroundUsersToRestart.iterator();
            while (it2.hasNext()) {
                Integer user2 = it2.next();
                if (!startedUsers.contains(user2)) {
                    usersToRemove.add(user2);
                }
            }
            this.mBackgroundUsersRestartedHere.removeAll(usersToRemove);
        }
        return startedUsers;
    }

    public boolean stopBackgroundUser(int userId) {
        int r;
        if (userId == 0) {
            return false;
        }
        if (userId == this.mCarUserManagerHelper.getCurrentForegroundUserId()) {
            Slog.i(TAG, "stopBackgroundUser, already a fg user:" + userId);
            return false;
        }
        try {
            r = this.mAm.stopUser(userId, true, (IStopUserCallback) null);
        } catch (RemoteException e) {
        }
        if (r == 0) {
            synchronized (this.mLock) {
                Integer user = Integer.valueOf(userId);
                this.mBackgroundUsersRestartedHere.remove(user);
            }
            return true;
        } else if (r == -2) {
            return false;
        } else {
            Slog.i(TAG, "stopBackgroundUser failed, user:" + userId + " err:" + r);
            return false;
        }
    }

    public void onSwitchUser(int userHandle) {
        Iterator<UserCallback> it = this.mUserCallbacks.iterator();
        while (it.hasNext()) {
            UserCallback callback = it.next();
            callback.onSwitchUser(userHandle);
        }
    }

    public void runOnUser0Unlock(Runnable r) {
        boolean runNow = false;
        synchronized (this.mLock) {
            if (this.mUser0Unlocked) {
                runNow = true;
            } else {
                this.mUser0UnlockTasks.add(r);
            }
        }
        if (runNow) {
            r.run();
        }
    }

    @VisibleForTesting
    protected ArrayList<Integer> getBackgroundUsersToRestart() {
        ArrayList<Integer> backgroundUsersToRestart;
        synchronized (this.mLock) {
            backgroundUsersToRestart = new ArrayList<>(this.mBackgroundUsersToRestart);
        }
        return backgroundUsersToRestart;
    }

    private void setSystemUserRestrictions() {
        CarUserManagerHelper carUserManagerHelper = this.mCarUserManagerHelper;
        carUserManagerHelper.setUserRestriction(carUserManagerHelper.getSystemUserInfo(), "no_modify_accounts", true);
        LocationManager locationManager = (LocationManager) this.mContext.getSystemService("location");
        locationManager.setLocationEnabledForUser(true, UserHandle.of(0));
    }
}
