package com.android.car;

import android.car.test.ICarTest;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Slog;
import com.android.car.Manifest;
import com.android.internal.annotations.GuardedBy;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
/* JADX INFO: Access modifiers changed from: package-private */
/* loaded from: classes3.dex */
public class CarTestService extends ICarTest.Stub implements CarServiceBase {
    private static final String TAG = CarTestService.class.getSimpleName();
    private final Context mContext;
    private final ICarImpl mICarImpl;
    private final Object mLock = new Object();
    @GuardedBy({"mLock"})
    private final Map<IBinder, TokenDeathRecipient> mTokens = new HashMap();

    /* JADX INFO: Access modifiers changed from: package-private */
    public CarTestService(Context context, ICarImpl carImpl) {
        this.mContext = context;
        this.mICarImpl = carImpl;
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
    }

    @Override // com.android.car.CarServiceBase
    public void release() {
    }

    @Override // com.android.car.CarServiceBase
    public void dump(PrintWriter writer) {
        writer.println("*CarTestService*");
        writer.println(" mTokens:" + Arrays.toString(this.mTokens.entrySet().toArray()));
    }

    @Override // android.car.test.ICarTest
    public void stopCarService(IBinder token) throws RemoteException {
        String str = TAG;
        Slog.d(str, "stopCarService, token: " + token);
        ICarImpl.assertPermission(this.mContext, Manifest.permission.CAR_TEST_SERVICE);
        synchronized (this.mLock) {
            if (this.mTokens.containsKey(token)) {
                Slog.w(TAG, "Calling stopCarService twice with the same token.");
                return;
            }
            TokenDeathRecipient deathRecipient = new TokenDeathRecipient(token);
            this.mTokens.put(token, deathRecipient);
            token.linkToDeath(deathRecipient, 0);
            if (this.mTokens.size() == 1) {
                final ICarImpl iCarImpl = this.mICarImpl;
                Objects.requireNonNull(iCarImpl);
                CarServiceUtils.runOnMainSync(new Runnable() { // from class: com.android.car.-$$Lambda$rJVG2LFuQMAsk2dzT2OK3JzYefI
                    @Override // java.lang.Runnable
                    public final void run() {
                        ICarImpl.this.release();
                    }
                });
            }
        }
    }

    @Override // android.car.test.ICarTest
    public void startCarService(IBinder token) throws RemoteException {
        String str = TAG;
        Slog.d(str, "startCarService, token: " + token);
        ICarImpl.assertPermission(this.mContext, Manifest.permission.CAR_TEST_SERVICE);
        releaseToken(token);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void releaseToken(IBinder token) {
        String str = TAG;
        Slog.d(str, "releaseToken, token: " + token);
        synchronized (this.mLock) {
            IBinder.DeathRecipient deathRecipient = this.mTokens.remove(token);
            if (deathRecipient != null) {
                token.unlinkToDeath(deathRecipient, 0);
            }
            if (this.mTokens.size() == 0) {
                final ICarImpl iCarImpl = this.mICarImpl;
                Objects.requireNonNull(iCarImpl);
                CarServiceUtils.runOnMainSync(new Runnable() { // from class: com.android.car.-$$Lambda$XJaTjjBdpEGkF6kNvjfmMa8z4jY
                    @Override // java.lang.Runnable
                    public final void run() {
                        ICarImpl.this.init();
                    }
                });
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class TokenDeathRecipient implements IBinder.DeathRecipient {
        private final IBinder mToken;

        TokenDeathRecipient(IBinder token) throws RemoteException {
            this.mToken = token;
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            CarTestService.this.releaseToken(this.mToken);
        }
    }
}
