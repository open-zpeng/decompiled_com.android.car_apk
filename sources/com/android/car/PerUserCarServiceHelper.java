package com.android.car;

import android.car.ICarUserService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.UserHandle;
import android.util.Slog;
import com.android.internal.annotations.GuardedBy;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
/* loaded from: classes3.dex */
public class PerUserCarServiceHelper implements CarServiceBase {
    private static boolean DBG = false;
    private static final String EXTRA_USER_HANDLE = "android.intent.extra.user_handle";
    private static final String TAG = "PerUserCarSvcHelper";
    private ICarUserService mCarUserService;
    private Context mContext;
    private IntentFilter mUserSwitchFilter;
    private final Object mServiceBindLock = new Object();
    @GuardedBy({"mServiceBindLock"})
    private boolean mBound = false;
    private final ServiceConnection mUserServiceConnection = new ServiceConnection() { // from class: com.android.car.PerUserCarServiceHelper.1
        @Override // android.content.ServiceConnection
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            List<ServiceCallback> callbacks;
            if (PerUserCarServiceHelper.DBG) {
                Slog.d(PerUserCarServiceHelper.TAG, "Connected to User Service");
            }
            PerUserCarServiceHelper.this.mCarUserService = ICarUserService.Stub.asInterface(service);
            if (PerUserCarServiceHelper.this.mCarUserService != null) {
                synchronized (this) {
                    callbacks = new ArrayList<>(PerUserCarServiceHelper.this.mServiceCallbacks);
                }
                for (ServiceCallback callback : callbacks) {
                    callback.onServiceConnected(PerUserCarServiceHelper.this.mCarUserService);
                }
            }
        }

        @Override // android.content.ServiceConnection
        public void onServiceDisconnected(ComponentName componentName) {
            List<ServiceCallback> callbacks;
            if (PerUserCarServiceHelper.DBG) {
                Slog.d(PerUserCarServiceHelper.TAG, "Disconnected from User Service");
            }
            synchronized (this) {
                callbacks = new ArrayList<>(PerUserCarServiceHelper.this.mServiceCallbacks);
            }
            for (ServiceCallback callback : callbacks) {
                callback.onServiceDisconnected();
            }
        }
    };
    private List<ServiceCallback> mServiceCallbacks = new ArrayList();
    private UserSwitchBroadcastReceiver mReceiver = new UserSwitchBroadcastReceiver();

    /* loaded from: classes3.dex */
    public interface ServiceCallback {
        void onPreUnbind();

        void onServiceConnected(ICarUserService iCarUserService);

        void onServiceDisconnected();
    }

    public PerUserCarServiceHelper(Context context) {
        this.mContext = context;
        setupUserSwitchListener();
    }

    @Override // com.android.car.CarServiceBase
    public synchronized void init() {
        bindToPerUserCarService();
    }

    @Override // com.android.car.CarServiceBase
    public synchronized void release() {
        unbindFromPerUserCarService();
    }

    private void setupUserSwitchListener() {
        this.mUserSwitchFilter = new IntentFilter();
        this.mUserSwitchFilter.addAction("android.intent.action.USER_SWITCHED");
        this.mContext.registerReceiver(this.mReceiver, this.mUserSwitchFilter);
        if (DBG) {
            Slog.d(TAG, "UserSwitch Listener Registered");
        }
    }

    /* loaded from: classes3.dex */
    public class UserSwitchBroadcastReceiver extends BroadcastReceiver {
        public UserSwitchBroadcastReceiver() {
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            List<ServiceCallback> callbacks;
            if (PerUserCarServiceHelper.DBG) {
                Slog.d(PerUserCarServiceHelper.TAG, "User Switch Happened");
                boolean userSwitched = intent.getAction().equals("android.intent.action.USER_SWITCHED");
                int user = intent.getExtras().getInt(PerUserCarServiceHelper.EXTRA_USER_HANDLE);
                if (userSwitched) {
                    Slog.d(PerUserCarServiceHelper.TAG, "New User " + user);
                }
            }
            synchronized (this) {
                callbacks = new ArrayList<>(PerUserCarServiceHelper.this.mServiceCallbacks);
            }
            for (ServiceCallback callback : callbacks) {
                callback.onPreUnbind();
            }
            PerUserCarServiceHelper.this.unbindFromPerUserCarService();
            PerUserCarServiceHelper.this.bindToPerUserCarService();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void bindToPerUserCarService() {
        if (DBG) {
            Slog.d(TAG, "Binding to User service");
        }
        Intent startIntent = new Intent(this.mContext, PerUserCarService.class);
        synchronized (this.mServiceBindLock) {
            this.mBound = true;
            Context context = this.mContext;
            ServiceConnection serviceConnection = this.mUserServiceConnection;
            Context context2 = this.mContext;
            boolean bindSuccess = context.bindServiceAsUser(startIntent, serviceConnection, 1, UserHandle.CURRENT);
            if (!bindSuccess) {
                Slog.e(TAG, "bindToPerUserCarService() failed to get valid connection");
                unbindFromPerUserCarService();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void unbindFromPerUserCarService() {
        synchronized (this.mServiceBindLock) {
            if (this.mBound) {
                if (DBG) {
                    Slog.d(TAG, "Unbinding from User Service");
                }
                this.mContext.unbindService(this.mUserServiceConnection);
                this.mBound = false;
            }
        }
    }

    public void registerServiceCallback(ServiceCallback listener) {
        if (listener != null) {
            if (DBG) {
                Slog.d(TAG, "Registering PerUserCarService Listener");
            }
            synchronized (this) {
                this.mServiceCallbacks.add(listener);
            }
        }
    }

    public void unregisterServiceCallback(ServiceCallback listener) {
        if (DBG) {
            Slog.d(TAG, "Unregistering PerUserCarService Listener");
        }
        if (listener != null) {
            synchronized (this) {
                this.mServiceCallbacks.remove(listener);
            }
        }
    }

    @Override // com.android.car.CarServiceBase
    public synchronized void dump(PrintWriter writer) {
    }
}
