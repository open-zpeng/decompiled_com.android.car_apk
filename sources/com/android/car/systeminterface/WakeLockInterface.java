package com.android.car.systeminterface;

import android.content.Context;
import android.os.PowerManager;
import com.android.car.CarLog;
/* loaded from: classes3.dex */
public interface WakeLockInterface {
    void releaseAllWakeLocks();

    void switchToFullWakeLock();

    void switchToPartialWakeLock();

    /* loaded from: classes3.dex */
    public static class DefaultImpl implements WakeLockInterface {
        private final PowerManager.WakeLock mFullWakeLock;
        private final PowerManager.WakeLock mPartialWakeLock;

        /* JADX INFO: Access modifiers changed from: package-private */
        public DefaultImpl(Context context) {
            PowerManager powerManager = (PowerManager) context.getSystemService("power");
            this.mFullWakeLock = powerManager.newWakeLock(6, CarLog.TAG_POWER);
            this.mPartialWakeLock = powerManager.newWakeLock(1, CarLog.TAG_POWER);
        }

        @Override // com.android.car.systeminterface.WakeLockInterface
        public void switchToPartialWakeLock() {
            if (!this.mPartialWakeLock.isHeld()) {
                this.mPartialWakeLock.acquire();
            }
            if (this.mFullWakeLock.isHeld()) {
                this.mFullWakeLock.release();
            }
        }

        @Override // com.android.car.systeminterface.WakeLockInterface
        public void switchToFullWakeLock() {
            if (!this.mFullWakeLock.isHeld()) {
                this.mFullWakeLock.acquire();
            }
            if (this.mPartialWakeLock.isHeld()) {
                this.mPartialWakeLock.release();
            }
        }

        @Override // com.android.car.systeminterface.WakeLockInterface
        public void releaseAllWakeLocks() {
            if (this.mPartialWakeLock.isHeld()) {
                this.mPartialWakeLock.release();
            }
            if (this.mFullWakeLock.isHeld()) {
                this.mFullWakeLock.release();
            }
        }
    }
}
