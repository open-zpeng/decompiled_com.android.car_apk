package com.android.car.pm;

import android.car.content.pm.CarAppBlockingPolicy;
import android.car.content.pm.ICarAppBlockingPolicy;
import android.car.content.pm.ICarAppBlockingPolicySetter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Slog;
import com.android.car.CarLog;
import com.android.internal.annotations.GuardedBy;
/* loaded from: classes3.dex */
public class AppBlockingPolicyProxy implements ServiceConnection {
    private static final int MAX_CRASH_RETRY = 2;
    private static final long TIMEOUT_MS = 5000;
    private final Context mContext;
    private final Handler mHandler;
    private final CarPackageManagerService mService;
    private final ServiceInfo mServiceInfo;
    @GuardedBy({"this"})
    private ICarAppBlockingPolicy mPolicyService = null;
    @GuardedBy({"this"})
    private int mCrashCount = 0;
    @GuardedBy({"this"})
    private boolean mBound = false;
    private final Runnable mTimeoutRunnable = new Runnable() { // from class: com.android.car.pm.AppBlockingPolicyProxy.1
        @Override // java.lang.Runnable
        public void run() {
            Slog.w(CarLog.TAG_PACKAGE, "Timeout for policy setting for service:" + AppBlockingPolicyProxy.this.mServiceInfo);
            AppBlockingPolicyProxy.this.disconnect();
            AppBlockingPolicyProxy.this.mService.onPolicyConnectionFailure(AppBlockingPolicyProxy.this);
        }
    };
    private final ICarAppBlockingPolicySetterImpl mSetter = new ICarAppBlockingPolicySetterImpl();

    public AppBlockingPolicyProxy(CarPackageManagerService service, Context context, ServiceInfo serviceInfo) {
        this.mService = service;
        this.mContext = context;
        this.mServiceInfo = serviceInfo;
        this.mHandler = new Handler(this.mService.getLooper());
    }

    public String getPackageName() {
        return this.mServiceInfo.packageName;
    }

    public void connect() {
        Intent intent = new Intent();
        intent.setComponent(this.mServiceInfo.getComponentName());
        this.mContext.bindServiceAsUser(intent, this, 65, UserHandle.CURRENT_OR_SELF);
        synchronized (this) {
            this.mBound = true;
        }
        this.mHandler.postDelayed(this.mTimeoutRunnable, TIMEOUT_MS);
    }

    public void disconnect() {
        synchronized (this) {
            if (this.mBound) {
                this.mBound = false;
                this.mPolicyService = null;
                this.mHandler.removeCallbacks(this.mTimeoutRunnable);
                try {
                    this.mContext.unbindService(this);
                } catch (IllegalArgumentException e) {
                    Slog.w(CarLog.TAG_PACKAGE, "unbind", e);
                }
            }
        }
    }

    @Override // android.content.ServiceConnection
    public void onServiceConnected(ComponentName name, IBinder service) {
        boolean failed = false;
        synchronized (this) {
            this.mPolicyService = ICarAppBlockingPolicy.Stub.asInterface(service);
            ICarAppBlockingPolicy policy = this.mPolicyService;
            if (policy == null) {
                failed = true;
            }
        }
        if (failed) {
            Slog.w(CarLog.TAG_PACKAGE, "Policy service connected with null binder:" + name);
            this.mService.onPolicyConnectionFailure(this);
            return;
        }
        try {
            this.mPolicyService.setAppBlockingPolicySetter(this.mSetter);
        } catch (RemoteException e) {
        }
    }

    @Override // android.content.ServiceConnection
    public void onServiceDisconnected(ComponentName name) {
        boolean failed = false;
        synchronized (this) {
            this.mCrashCount++;
            if (this.mCrashCount > 2) {
                this.mPolicyService = null;
                failed = true;
            }
        }
        if (failed) {
            Slog.w(CarLog.TAG_PACKAGE, "Policy service keep crashing, giving up:" + name);
            this.mService.onPolicyConnectionFailure(this);
        }
    }

    public String toString() {
        return "AppBlockingPolicyProxy [mServiceInfo=" + this.mServiceInfo + ", mCrashCount=" + this.mCrashCount + "]";
    }

    /* loaded from: classes3.dex */
    private class ICarAppBlockingPolicySetterImpl extends ICarAppBlockingPolicySetter.Stub {
        private ICarAppBlockingPolicySetterImpl() {
        }

        public void setAppBlockingPolicy(CarAppBlockingPolicy policy) {
            AppBlockingPolicyProxy.this.mHandler.removeCallbacks(AppBlockingPolicyProxy.this.mTimeoutRunnable);
            if (policy == null) {
                Slog.w(CarLog.TAG_PACKAGE, "setAppBlockingPolicy null policy from policy service:" + AppBlockingPolicyProxy.this.mServiceInfo);
            }
            AppBlockingPolicyProxy.this.mService.onPolicyConnectionAndSet(AppBlockingPolicyProxy.this, policy);
        }
    }
}
