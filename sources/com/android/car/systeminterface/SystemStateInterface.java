package com.android.car.systeminterface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Pair;
import android.util.Slog;
import com.android.car.procfsinspector.ProcessInfo;
import com.android.car.procfsinspector.ProcfsInspector;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.car.ICarServiceHelper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
/* loaded from: classes3.dex */
public interface SystemStateInterface {
    public static final String TAG = SystemStateInterface.class.getSimpleName();

    boolean enterDeepSleep();

    boolean isInteractive();

    void scheduleActionForBootCompleted(Runnable runnable, Duration duration);

    void setAutoSuspendEnable(boolean z);

    void setXpIcmScreenEnable(boolean z);

    void shutdown();

    default boolean isWakeupCausedByTimer() {
        return false;
    }

    default boolean isSystemSupportingDeepSleep() {
        return true;
    }

    default List<ProcessInfo> getRunningProcesses() {
        return ProcfsInspector.readProcessTable();
    }

    default void setCarServiceHelper(ICarServiceHelper helper) {
    }

    @VisibleForTesting
    /* loaded from: classes3.dex */
    public static class DefaultImpl implements SystemStateInterface {
        private static final int MAX_WAIT_FOR_HELPER_SEC = 10;
        private static final Duration MIN_BOOT_COMPLETE_ACTION_DELAY = Duration.ofSeconds(10);
        private static final int SUSPEND_TRY_TIMEOUT_MS = 1000;
        private final Context mContext;
        private ScheduledExecutorService mExecutorService;
        private ICarServiceHelper mICarServiceHelper;
        private final PowerManager mPowerManager;
        private final CountDownLatch mHelperLatch = new CountDownLatch(1);
        private List<Pair<Runnable, Duration>> mActionsList = new ArrayList();
        private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() { // from class: com.android.car.systeminterface.SystemStateInterface.DefaultImpl.1
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context, Intent intent) {
                if ("android.intent.action.BOOT_COMPLETED".equals(intent.getAction())) {
                    for (Pair<Runnable, Duration> action : DefaultImpl.this.mActionsList) {
                        DefaultImpl.this.mExecutorService.schedule((Runnable) action.first, ((Duration) action.second).toMillis(), TimeUnit.MILLISECONDS);
                    }
                }
            }
        };

        @VisibleForTesting
        public DefaultImpl(Context context) {
            this.mContext = context;
            this.mPowerManager = (PowerManager) context.getSystemService("power");
        }

        @Override // com.android.car.systeminterface.SystemStateInterface
        public void shutdown() {
            this.mPowerManager.shutdown(false, "CarServiceShutdown", true);
        }

        @Override // com.android.car.systeminterface.SystemStateInterface
        public boolean enterDeepSleep() {
            if (canInvokeHelper()) {
                try {
                    int retVal = this.mICarServiceHelper.forceSuspend(1000);
                    boolean deviceEnteredSleep = retVal == 0;
                    return deviceEnteredSleep;
                } catch (Exception e) {
                    Slog.e(TAG, "Unable to enter deep sleep", e);
                    return false;
                }
            }
            return false;
        }

        private boolean canInvokeHelper() {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                throw new IllegalStateException("SystemStateInterface.enterDeepSleep() was called from the main thread");
            }
            if (this.mICarServiceHelper != null) {
                return true;
            }
            try {
                this.mHelperLatch.await(10L, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (this.mICarServiceHelper != null) {
                return true;
            }
            Slog.e(TAG, "Unable to enter deep sleep: ICarServiceHelper is still null after waiting 10 seconds");
            return false;
        }

        @Override // com.android.car.systeminterface.SystemStateInterface
        public void scheduleActionForBootCompleted(Runnable action, Duration delay) {
            if (MIN_BOOT_COMPLETE_ACTION_DELAY.compareTo(delay) < 0) {
                delay = MIN_BOOT_COMPLETE_ACTION_DELAY;
            }
            if (this.mActionsList.isEmpty()) {
                this.mExecutorService = Executors.newScheduledThreadPool(1);
                IntentFilter intentFilter = new IntentFilter("android.intent.action.BOOT_COMPLETED");
                this.mContext.registerReceiver(this.mBroadcastReceiver, intentFilter);
            }
            this.mActionsList.add(Pair.create(action, delay));
        }

        @Override // com.android.car.systeminterface.SystemStateInterface
        public boolean isInteractive() {
            return this.mPowerManager.isInteractive();
        }

        @Override // com.android.car.systeminterface.SystemStateInterface
        public void setAutoSuspendEnable(boolean enable) {
            if (!canInvokeHelper()) {
                return;
            }
            try {
                this.mICarServiceHelper.setAutoSuspendEnable(enable);
            } catch (Exception e) {
                Slog.e(TAG, "Unable to enter autosuspend", e);
            }
        }

        @Override // com.android.car.systeminterface.SystemStateInterface
        public void setXpIcmScreenEnable(boolean enable) {
            if (enable) {
                this.mPowerManager.setXpIcmScreenState(2);
                this.mPowerManager.setXpIcmScreenOn(SystemClock.uptimeMillis());
                return;
            }
            this.mPowerManager.setXpIcmScreenState(1);
        }

        @Override // com.android.car.systeminterface.SystemStateInterface
        public void setCarServiceHelper(ICarServiceHelper helper) {
            this.mICarServiceHelper = helper;
            this.mHelperLatch.countDown();
        }
    }
}
