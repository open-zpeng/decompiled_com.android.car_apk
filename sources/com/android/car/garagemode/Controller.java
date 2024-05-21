package com.android.car.garagemode;

import android.app.job.JobScheduler;
import android.car.hardware.power.CarPowerManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import com.android.car.CarLocalServices;
import com.android.internal.annotations.VisibleForTesting;
import java.util.List;
import java.util.concurrent.CompletableFuture;
/* loaded from: classes3.dex */
public class Controller implements CarPowerManager.CarPowerStateListenerWithCompletion {
    private static final Logger LOG = new Logger("Controller");
    private CarPowerManager mCarPowerManager;
    private final Context mContext;
    private final GarageMode mGarageMode;
    private final Handler mHandler;
    @VisibleForTesting
    final WakeupPolicy mWakeupPolicy;

    public Controller(Context context, Looper looper) {
        this(context, looper, null, null, null);
    }

    public Controller(Context context, Looper looper, WakeupPolicy wakeupPolicy, Handler handler, GarageMode garageMode) {
        this.mContext = context;
        this.mHandler = handler == null ? new Handler(looper) : handler;
        this.mWakeupPolicy = wakeupPolicy == null ? WakeupPolicy.initFromResources(context) : wakeupPolicy;
        this.mGarageMode = garageMode == null ? new GarageMode(this) : garageMode;
    }

    public void init() {
        this.mCarPowerManager = CarLocalServices.createCarPowerManager(this.mContext);
        this.mCarPowerManager.setListenerWithCompletion(this);
    }

    public void release() {
        CarPowerManager carPowerManager = this.mCarPowerManager;
        if (carPowerManager != null) {
            carPowerManager.clearListener();
        }
    }

    public void onStateChanged(int state, CompletableFuture<Void> future) {
        if (state == 2) {
            LOG.d("CPM state changed to SUSPEND_ENTER");
            handleSuspendEnter();
        } else if (state == 3) {
            LOG.d("CPM state changed to SUSPEND_EXIT");
            handleSuspendExit();
        } else if (state == 5) {
            LOG.d("CPM state changed to SHUTDOWN_ENTER");
            handleShutdownEnter();
        } else if (state == 7) {
            LOG.d("CPM state changed to SHUTDOWN_PREPARE");
            handleShutdownPrepare(future);
        } else if (state == 8) {
            LOG.d("CPM state changed to SHUTDOWN_CANCELLED");
            handleShutdownCancelled();
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean isGarageModeActive() {
        return this.mGarageMode.isGarageModeActive();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public List<String> dump() {
        return this.mGarageMode.dump();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void sendBroadcast(Intent i) {
        Logger logger = LOG;
        logger.d("Sending broadcast with action: " + i.getAction());
        this.mContext.sendBroadcast(i);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public JobScheduler getJobSchedulerService() {
        return (JobScheduler) this.mContext.getSystemService("jobscheduler");
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public Handler getHandler() {
        return this.mHandler;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void initiateGarageMode(CompletableFuture<Void> future) {
        this.mWakeupPolicy.incrementCounter();
        this.mGarageMode.enterGarageMode(future);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void resetGarageMode() {
        this.mGarageMode.cancel();
        this.mWakeupPolicy.resetCounter();
    }

    @VisibleForTesting
    void finishGarageMode() {
        this.mGarageMode.finish();
    }

    @VisibleForTesting
    void setCarPowerManager(CarPowerManager cpm) {
        this.mCarPowerManager = cpm;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void scheduleNextWakeup() {
        if (this.mWakeupPolicy.getNextWakeUpInterval() <= 0) {
            return;
        }
        int seconds = this.mWakeupPolicy.getNextWakeUpInterval();
        this.mCarPowerManager.scheduleNextWakeupTime(seconds);
    }

    private void handleSuspendExit() {
        resetGarageMode();
    }

    private void handleSuspendEnter() {
        resetGarageMode();
    }

    private void handleShutdownEnter() {
        resetGarageMode();
    }

    private void handleShutdownPrepare(CompletableFuture<Void> future) {
        initiateGarageMode(future);
    }

    private void handleShutdownCancelled() {
        resetGarageMode();
    }
}
