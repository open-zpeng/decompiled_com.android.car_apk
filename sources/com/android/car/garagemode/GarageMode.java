package com.android.car.garagemode;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.job.JobSnapshot;
import android.content.Intent;
import android.os.Handler;
import android.util.ArraySet;
import com.android.car.CarLocalServices;
import com.android.car.CarStatsLog;
import com.android.car.user.CarUserService;
import com.android.internal.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
/* JADX INFO: Access modifiers changed from: package-private */
/* loaded from: classes3.dex */
public class GarageMode {
    public static final String ACTION_GARAGE_MODE_OFF = "com.android.server.jobscheduler.GARAGE_MODE_OFF";
    public static final String ACTION_GARAGE_MODE_ON = "com.android.server.jobscheduler.GARAGE_MODE_ON";
    private static final int ADDITIONAL_CHECKS_TO_DO = 1;
    @VisibleForTesting
    static final long JOB_SNAPSHOT_INITIAL_UPDATE_MS = 10000;
    private static final long JOB_SNAPSHOT_UPDATE_FREQUENCY_MS = 1000;
    private static final Logger LOG = new Logger("GarageMode");
    private static final long USER_STOP_CHECK_INTERVAL = 10000;
    private final Controller mController;
    private CompletableFuture<Void> mFuture;
    private Handler mHandler;
    private JobScheduler mJobScheduler;
    private int mAdditionalChecksToDo = 1;
    private Runnable mRunnable = new Runnable() { // from class: com.android.car.garagemode.GarageMode.1
        @Override // java.lang.Runnable
        public void run() {
            int numberRunning = GarageMode.this.numberOfIdleJobsRunning();
            if (numberRunning > 0) {
                Logger logger = GarageMode.LOG;
                logger.d("" + numberRunning + " jobs are still running. Need to wait more ...");
                GarageMode.this.mAdditionalChecksToDo = 1;
            } else {
                int numberReadyToRun = GarageMode.this.numberOfJobsPending();
                if (numberReadyToRun == 0) {
                    GarageMode.LOG.d("No jobs are running. No jobs are pending. Exiting Garage Mode.");
                    GarageMode.this.finish();
                    return;
                } else if (GarageMode.this.mAdditionalChecksToDo == 0) {
                    Logger logger2 = GarageMode.LOG;
                    logger2.d("No jobs are running. Waited too long for " + numberReadyToRun + " pending jobs. Exiting Garage Mode.");
                    GarageMode.this.finish();
                    return;
                } else {
                    Logger logger3 = GarageMode.LOG;
                    logger3.d("No jobs are running. Waiting " + GarageMode.this.mAdditionalChecksToDo + " more cycles for " + numberReadyToRun + " pending jobs.");
                    GarageMode.access$210(GarageMode.this);
                }
            }
            GarageMode.this.mHandler.postDelayed(GarageMode.this.mRunnable, GarageMode.JOB_SNAPSHOT_UPDATE_FREQUENCY_MS);
        }
    };
    private final Runnable mStopUserCheckRunnable = new Runnable() { // from class: com.android.car.garagemode.GarageMode.2
        @Override // java.lang.Runnable
        public void run() {
            synchronized (this) {
                int remainingUsersToStop = GarageMode.this.mStartedBackgroundUsers.size();
                if (remainingUsersToStop > 0) {
                    int userToStop = ((Integer) GarageMode.this.mStartedBackgroundUsers.valueAt(0)).intValue();
                    if (GarageMode.this.numberOfIdleJobsRunning() != 0) {
                        Logger logger = GarageMode.LOG;
                        logger.i("Waiting for jobs to finish, remaining users:" + remainingUsersToStop);
                    } else {
                        if (userToStop != 0) {
                            ((CarUserService) CarLocalServices.getService(CarUserService.class)).stopBackgroundUser(userToStop);
                            Logger logger2 = GarageMode.LOG;
                            StringBuilder sb = new StringBuilder();
                            sb.append("Stopping background user:");
                            sb.append(userToStop);
                            sb.append(" remaining users:");
                            sb.append(remainingUsersToStop - 1);
                            logger2.i(sb.toString());
                        }
                        synchronized (this) {
                            GarageMode.this.mStartedBackgroundUsers.remove(Integer.valueOf(userToStop));
                            if (GarageMode.this.mStartedBackgroundUsers.size() == 0) {
                                GarageMode.LOG.i("all background users stopped");
                                return;
                            }
                        }
                    }
                    GarageMode.this.mHandler.postDelayed(GarageMode.this.mStopUserCheckRunnable, 10000L);
                }
            }
        }
    };
    private ArraySet<Integer> mStartedBackgroundUsers = new ArraySet<>();
    private boolean mGarageModeActive = false;

    static /* synthetic */ int access$210(GarageMode x0) {
        int i = x0.mAdditionalChecksToDo;
        x0.mAdditionalChecksToDo = i - 1;
        return i;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public GarageMode(Controller controller) {
        this.mController = controller;
        this.mJobScheduler = controller.getJobSchedulerService();
        this.mHandler = controller.getHandler();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean isGarageModeActive() {
        return this.mGarageModeActive;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public List<String> dump() {
        List<String> outString = new ArrayList<>();
        if (!this.mGarageModeActive) {
            return outString;
        }
        List<String> jobList = new ArrayList<>();
        int numJobs = getListOfIdleJobsRunning(jobList);
        if (numJobs > 0) {
            outString.add("GarageMode is waiting for " + numJobs + " jobs:");
            for (int idx = 0; idx < jobList.size(); idx++) {
                outString.add("   " + (idx + 1) + ": " + jobList.get(idx));
            }
        } else {
            getListOfPendingJobs(jobList);
            outString.add("GarageMode is waiting for " + jobList.size() + " pending idle jobs:");
            for (int idx2 = 0; idx2 < jobList.size(); idx2++) {
                outString.add("   " + (idx2 + 1) + ": " + jobList.get(idx2));
            }
        }
        return outString;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void enterGarageMode(CompletableFuture<Void> future) {
        LOG.d("Entering GarageMode");
        synchronized (this) {
            this.mGarageModeActive = true;
        }
        updateFuture(future);
        broadcastSignalToJobSchedulerTo(true);
        CarStatsLog.logGarageModeStart();
        startMonitoringThread();
        ArrayList<Integer> startedUsers = ((CarUserService) CarLocalServices.getService(CarUserService.class)).startAllBackgroundUsers();
        synchronized (this) {
            this.mStartedBackgroundUsers.addAll(startedUsers);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public synchronized void cancel() {
        broadcastSignalToJobSchedulerTo(false);
        if (this.mFuture != null && !this.mFuture.isDone()) {
            this.mFuture.cancel(true);
        }
        this.mFuture = null;
        startBackgroundUserStopping();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public synchronized void finish() {
        broadcastSignalToJobSchedulerTo(false);
        CarStatsLog.logGarageModeStop();
        this.mController.scheduleNextWakeup();
        synchronized (this) {
            try {
                if (this.mFuture != null) {
                    try {
                        if (!this.mFuture.isDone()) {
                            this.mFuture.complete(null);
                        }
                    } catch (Throwable th) {
                        th = th;
                        throw th;
                    }
                }
                this.mFuture = null;
                startBackgroundUserStopping();
            } catch (Throwable th2) {
                th = th2;
            }
        }
    }

    private void cleanupGarageMode() {
        LOG.d("Cleaning up GarageMode");
        synchronized (this) {
            this.mGarageModeActive = false;
        }
        stopMonitoringThread();
        this.mHandler.removeCallbacks(this.mRunnable);
        startBackgroundUserStopping();
    }

    private void startBackgroundUserStopping() {
        synchronized (this) {
            if (this.mStartedBackgroundUsers.size() > 0) {
                this.mHandler.postDelayed(this.mStopUserCheckRunnable, 10000L);
            }
        }
    }

    private void updateFuture(CompletableFuture<Void> future) {
        synchronized (this) {
            this.mFuture = future;
        }
        CompletableFuture<Void> completableFuture = this.mFuture;
        if (completableFuture != null) {
            completableFuture.whenComplete(new BiConsumer() { // from class: com.android.car.garagemode.-$$Lambda$GarageMode$EJDPPpU8PkORJG5W6FKZ0KO7wbQ
                @Override // java.util.function.BiConsumer
                public final void accept(Object obj, Object obj2) {
                    GarageMode.this.lambda$updateFuture$0$GarageMode((Void) obj, (Throwable) obj2);
                }
            });
        }
    }

    public /* synthetic */ void lambda$updateFuture$0$GarageMode(Void result, Throwable exception) {
        if (exception == null) {
            LOG.d("GarageMode completed normally");
        } else if (exception instanceof CancellationException) {
            LOG.d("GarageMode was canceled");
        } else {
            LOG.e("GarageMode ended due to exception: ", exception);
        }
        cleanupGarageMode();
    }

    private void broadcastSignalToJobSchedulerTo(boolean enableGarageMode) {
        Intent i = new Intent();
        if (enableGarageMode) {
            i.setAction(ACTION_GARAGE_MODE_ON);
        } else {
            i.setAction(ACTION_GARAGE_MODE_OFF);
        }
        i.setFlags(1207959552);
        this.mController.sendBroadcast(i);
    }

    private synchronized void startMonitoringThread() {
        this.mAdditionalChecksToDo = 1;
        this.mHandler.postDelayed(this.mRunnable, 10000L);
    }

    private synchronized void stopMonitoringThread() {
        this.mHandler.removeCallbacks(this.mRunnable);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int numberOfIdleJobsRunning() {
        return getListOfIdleJobsRunning(null);
    }

    private int getListOfIdleJobsRunning(List<String> jobList) {
        if (jobList != null) {
            jobList.clear();
        }
        List<JobInfo> startedJobs = this.mJobScheduler.getStartedJobs();
        if (startedJobs == null) {
            return 0;
        }
        int count = 0;
        for (int idx = 0; idx < startedJobs.size(); idx++) {
            JobInfo jobInfo = startedJobs.get(idx);
            if (jobInfo.isRequireDeviceIdle()) {
                count++;
                if (jobList != null) {
                    jobList.add(jobInfo.toString());
                }
            }
        }
        return count;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int numberOfJobsPending() {
        return getListOfPendingJobs(null);
    }

    private int getListOfPendingJobs(List<String> jobList) {
        if (jobList != null) {
            jobList.clear();
        }
        List<JobSnapshot> allScheduledJobs = this.mJobScheduler.getAllJobSnapshots();
        if (allScheduledJobs == null) {
            return 0;
        }
        int numberPending = 0;
        for (int idx = 0; idx < allScheduledJobs.size(); idx++) {
            JobSnapshot scheduledJob = allScheduledJobs.get(idx);
            JobInfo jobInfo = scheduledJob.getJobInfo();
            if (scheduledJob.isRunnable() && jobInfo.isRequireDeviceIdle()) {
                numberPending++;
                if (jobList != null) {
                    jobList.add(jobInfo.toString());
                }
            }
        }
        return numberPending;
    }
}
