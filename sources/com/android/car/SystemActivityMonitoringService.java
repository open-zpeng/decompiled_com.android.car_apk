package com.android.car;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.IActivityManager;
import android.app.IProcessObserver;
import android.app.TaskStackListener;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import android.util.Slog;
import android.util.SparseArray;
import com.android.car.pm.CarPackageManagerService;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
/* loaded from: classes3.dex */
public class SystemActivityMonitoringService implements CarServiceBase {
    private static final int INVALID_STACK_ID = -1;
    private ActivityLaunchListener mActivityLaunchListener;
    private final IActivityManager mAm;
    private final Context mContext;
    private final ActivityMonitorHandler mHandler;
    private final ProcessObserver mProcessObserver;
    private final TaskListener mTaskListener;
    private final SparseArray<TopTaskInfoContainer> mTopTasks = new SparseArray<>();
    private final Map<Integer, Set<Integer>> mForegroundUidPids = new ArrayMap();
    private int mFocusedStackId = -1;
    private final HandlerThread mMonitorHandlerThread = new HandlerThread(CarLog.TAG_AM);

    /* loaded from: classes3.dex */
    public interface ActivityLaunchListener {
        void onActivityLaunch(TopTaskInfoContainer topTaskInfoContainer);
    }

    /* loaded from: classes3.dex */
    public static class TopTaskInfoContainer {
        public final int displayId;
        public final int position;
        public final ActivityManager.StackInfo stackInfo;
        public final int taskId;
        public final ComponentName topActivity;

        private TopTaskInfoContainer(ComponentName topActivity, int taskId, int displayId, int position, ActivityManager.StackInfo stackInfo) {
            this.topActivity = topActivity;
            this.taskId = taskId;
            this.displayId = displayId;
            this.position = position;
            this.stackInfo = stackInfo;
        }

        public boolean isMatching(TopTaskInfoContainer taskInfo) {
            return taskInfo != null && Objects.equals(this.topActivity, taskInfo.topActivity) && this.taskId == taskInfo.taskId && this.displayId == taskInfo.displayId && this.position == taskInfo.position && this.stackInfo.userId == taskInfo.stackInfo.userId;
        }

        public String toString() {
            return String.format("TaskInfoContainer [topActivity=%s, taskId=%d, stackId=%d, userId=%d, displayId=%d, position=%d", this.topActivity, Integer.valueOf(this.taskId), Integer.valueOf(this.stackInfo.stackId), Integer.valueOf(this.stackInfo.userId), Integer.valueOf(this.displayId), Integer.valueOf(this.position));
        }
    }

    public SystemActivityMonitoringService(Context context) {
        this.mContext = context;
        this.mMonitorHandlerThread.start();
        this.mHandler = new ActivityMonitorHandler(this.mMonitorHandlerThread.getLooper());
        this.mProcessObserver = new ProcessObserver();
        this.mTaskListener = new TaskListener();
        this.mAm = ActivityManager.getService();
        try {
            this.mAm.registerProcessObserver(this.mProcessObserver);
            this.mAm.registerTaskStackListener(this.mTaskListener);
            updateTasks();
        } catch (RemoteException e) {
            Slog.e(CarLog.TAG_AM, "cannot register activity monitoring", e);
            throw new RuntimeException(e);
        }
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
    }

    @Override // com.android.car.CarServiceBase
    public void release() {
    }

    @Override // com.android.car.CarServiceBase
    public void dump(PrintWriter writer) {
        writer.println("*SystemActivityMonitoringService*");
        writer.println(" Top Tasks per display:");
        synchronized (this) {
            for (int i = 0; i < this.mTopTasks.size(); i++) {
                int displayId = this.mTopTasks.keyAt(i);
                TopTaskInfoContainer info = this.mTopTasks.valueAt(i);
                if (info != null) {
                    writer.println("display id " + displayId + ": " + info);
                }
            }
            writer.println(" Foreground uid-pids:");
            for (Integer key : this.mForegroundUidPids.keySet()) {
                Set<Integer> pids = this.mForegroundUidPids.get(key);
                if (pids != null) {
                    writer.println("uid:" + key + ", pids:" + Arrays.toString(pids.toArray()));
                }
            }
            writer.println(" focused stack:" + this.mFocusedStackId);
        }
    }

    public void blockActivity(TopTaskInfoContainer currentTask, Intent newActivityIntent) {
        this.mHandler.requestBlockActivity(currentTask, newActivityIntent);
    }

    public List<TopTaskInfoContainer> getTopTasks() {
        LinkedList<TopTaskInfoContainer> tasks = new LinkedList<>();
        synchronized (this) {
            for (int i = 0; i < this.mTopTasks.size(); i++) {
                TopTaskInfoContainer topTask = this.mTopTasks.valueAt(i);
                if (topTask == null) {
                    Slog.e(CarLog.TAG_AM, "Top tasks contains null. Full content is: " + this.mTopTasks.toString());
                } else {
                    tasks.add(topTask);
                }
            }
        }
        return tasks;
    }

    public boolean isInForeground(int pid, int uid) {
        synchronized (this) {
            Set<Integer> pids = this.mForegroundUidPids.get(Integer.valueOf(uid));
            if (pids == null) {
                return false;
            }
            if (!pids.contains(Integer.valueOf(pid))) {
                return false;
            }
            return true;
        }
    }

    /* JADX WARN: Code restructure failed: missing block: B:11:0x0026, code lost:
        r1 = r4.taskNames[r5];
        r2 = r4.userId;
     */
    /* JADX WARN: Code restructure failed: missing block: B:12:0x0033, code lost:
        if (android.util.Log.isLoggable(com.android.car.CarLog.TAG_AM, 3) == false) goto L17;
     */
    /* JADX WARN: Code restructure failed: missing block: B:13:0x0035, code lost:
        android.util.Slog.d(com.android.car.CarLog.TAG_AM, "Root activity is " + r1);
        android.util.Slog.d(com.android.car.CarLog.TAG_AM, "User id is " + r2);
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public void restartTask(int r8) {
        /*
            r7 = this;
            java.lang.String r0 = "CAR.AM"
            r1 = 0
            r2 = 0
            android.app.IActivityManager r3 = r7.mAm     // Catch: android.os.RemoteException -> Lb2
            java.util.List r3 = r3.getAllStackInfos()     // Catch: android.os.RemoteException -> Lb2
            java.util.Iterator r3 = r3.iterator()     // Catch: android.os.RemoteException -> Lb2
        Le:
            boolean r4 = r3.hasNext()     // Catch: android.os.RemoteException -> Lb2
            if (r4 == 0) goto L62
            java.lang.Object r4 = r3.next()     // Catch: android.os.RemoteException -> Lb2
            android.app.ActivityManager$StackInfo r4 = (android.app.ActivityManager.StackInfo) r4     // Catch: android.os.RemoteException -> Lb2
            r5 = 0
        L1b:
            int[] r6 = r4.taskIds     // Catch: android.os.RemoteException -> Lb2
            int r6 = r6.length     // Catch: android.os.RemoteException -> Lb2
            if (r5 >= r6) goto L61
            int[] r6 = r4.taskIds     // Catch: android.os.RemoteException -> Lb2
            r6 = r6[r5]     // Catch: android.os.RemoteException -> Lb2
            if (r6 != r8) goto L5e
            java.lang.String[] r3 = r4.taskNames     // Catch: android.os.RemoteException -> Lb2
            r3 = r3[r5]     // Catch: android.os.RemoteException -> Lb2
            r1 = r3
            int r3 = r4.userId     // Catch: android.os.RemoteException -> Lb2
            r2 = r3
            r3 = 3
            boolean r3 = android.util.Log.isLoggable(r0, r3)     // Catch: android.os.RemoteException -> Lb2
            if (r3 == 0) goto L62
            java.lang.StringBuilder r3 = new java.lang.StringBuilder     // Catch: android.os.RemoteException -> Lb2
            r3.<init>()     // Catch: android.os.RemoteException -> Lb2
            java.lang.String r6 = "Root activity is "
            r3.append(r6)     // Catch: android.os.RemoteException -> Lb2
            r3.append(r1)     // Catch: android.os.RemoteException -> Lb2
            java.lang.String r3 = r3.toString()     // Catch: android.os.RemoteException -> Lb2
            android.util.Slog.d(r0, r3)     // Catch: android.os.RemoteException -> Lb2
            java.lang.StringBuilder r3 = new java.lang.StringBuilder     // Catch: android.os.RemoteException -> Lb2
            r3.<init>()     // Catch: android.os.RemoteException -> Lb2
            java.lang.String r6 = "User id is "
            r3.append(r6)     // Catch: android.os.RemoteException -> Lb2
            r3.append(r2)     // Catch: android.os.RemoteException -> Lb2
            java.lang.String r3 = r3.toString()     // Catch: android.os.RemoteException -> Lb2
            android.util.Slog.d(r0, r3)     // Catch: android.os.RemoteException -> Lb2
            goto L62
        L5e:
            int r5 = r5 + 1
            goto L1b
        L61:
            goto Le
        L62:
            if (r1 != 0) goto L7a
            java.lang.StringBuilder r3 = new java.lang.StringBuilder
            r3.<init>()
            java.lang.String r4 = "Could not find root activity with task id "
            r3.append(r4)
            r3.append(r8)
            java.lang.String r3 = r3.toString()
            android.util.Slog.e(r0, r3)
            return
        L7a:
            android.content.Intent r3 = new android.content.Intent
            r3.<init>()
            android.content.ComponentName r4 = android.content.ComponentName.unflattenFromString(r1)
            r3.setComponent(r4)
            r4 = 268468224(0x10008000, float:2.5342157E-29)
            r3.addFlags(r4)
            r4 = 4
            boolean r4 = android.util.Log.isLoggable(r0, r4)
            if (r4 == 0) goto La7
            java.lang.StringBuilder r4 = new java.lang.StringBuilder
            r4.<init>()
            java.lang.String r5 = "restarting root activity with user id "
            r4.append(r5)
            r4.append(r2)
            java.lang.String r4 = r4.toString()
            android.util.Slog.i(r0, r4)
        La7:
            android.content.Context r0 = r7.mContext
            android.os.UserHandle r4 = new android.os.UserHandle
            r4.<init>(r2)
            r0.startActivityAsUser(r3, r4)
            return
        Lb2:
            r3 = move-exception
            java.lang.String r4 = "Could not get stack info"
            android.util.Slog.e(r0, r4, r3)
            return
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.car.SystemActivityMonitoringService.restartTask(int):void");
    }

    public void registerActivityLaunchListener(ActivityLaunchListener listener) {
        synchronized (this) {
            this.mActivityLaunchListener = listener;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateTasks() {
        ActivityLaunchListener listener;
        try {
            List<ActivityManager.StackInfo> infos = this.mAm.getAllStackInfos();
            try {
                ActivityManager.StackInfo focusedStackInfo = this.mAm.getFocusedStackInfo();
                if (focusedStackInfo != null) {
                    int i = focusedStackInfo.stackId;
                }
                SparseArray<TopTaskInfoContainer> topTasks = new SparseArray<>();
                synchronized (this) {
                    listener = this.mActivityLaunchListener;
                    for (ActivityManager.StackInfo info : infos) {
                        int displayId = info.displayId;
                        if (info.taskNames.length != 0 && info.visible) {
                            TopTaskInfoContainer newTopTaskInfo = new TopTaskInfoContainer(info.topActivity, info.taskIds[info.taskIds.length - 1], info.displayId, info.position, info);
                            TopTaskInfoContainer currentTopTaskInfo = topTasks.get(displayId);
                            if (currentTopTaskInfo == null || newTopTaskInfo.position > currentTopTaskInfo.position) {
                                topTasks.put(displayId, newTopTaskInfo);
                                if (Log.isLoggable(CarLog.TAG_AM, 4)) {
                                    Slog.i(CarLog.TAG_AM, "Updating top task to: " + newTopTaskInfo);
                                }
                            }
                        }
                    }
                    for (int i2 = 0; i2 < topTasks.size(); i2++) {
                        int displayId2 = topTasks.keyAt(i2);
                        this.mTopTasks.put(displayId2, topTasks.valueAt(i2));
                    }
                }
                if (listener != null) {
                    for (int i3 = 0; i3 < topTasks.size(); i3++) {
                        TopTaskInfoContainer topTask = topTasks.valueAt(i3);
                        if (Log.isLoggable(CarLog.TAG_AM, 4)) {
                            Slog.i(CarLog.TAG_AM, "Notifying about top task: " + topTask.toString());
                        }
                        listener.onActivityLaunch(topTask);
                    }
                }
            } catch (RemoteException e) {
                Slog.e(CarLog.TAG_AM, "cannot getFocusedStackId", e);
            }
        } catch (RemoteException e2) {
            Slog.e(CarLog.TAG_AM, "cannot getTasks", e2);
        }
    }

    public ActivityManager.StackInfo getFocusedStackForTopActivity(ComponentName activity) {
        try {
            ActivityManager.StackInfo focusedStack = this.mAm.getFocusedStackInfo();
            if (focusedStack.taskNames.length == 0) {
                return null;
            }
            ComponentName topActivity = ComponentName.unflattenFromString(focusedStack.taskNames[focusedStack.taskNames.length - 1]);
            if (!topActivity.equals(activity)) {
                return null;
            }
            return focusedStack;
        } catch (RemoteException e) {
            Slog.e(CarLog.TAG_AM, "cannot getFocusedStackId", e);
            return null;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities) {
        synchronized (this) {
            if (foregroundActivities) {
                Set<Integer> pids = this.mForegroundUidPids.get(Integer.valueOf(uid));
                if (pids == null) {
                    pids = new ArraySet();
                    this.mForegroundUidPids.put(Integer.valueOf(uid), pids);
                }
                pids.add(Integer.valueOf(pid));
            } else {
                doHandlePidGoneLocked(pid, uid);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleProcessDied(int pid, int uid) {
        synchronized (this) {
            doHandlePidGoneLocked(pid, uid);
        }
    }

    private void doHandlePidGoneLocked(int pid, int uid) {
        Set<Integer> pids = this.mForegroundUidPids.get(Integer.valueOf(uid));
        if (pids != null) {
            pids.remove(Integer.valueOf(pid));
            if (pids.isEmpty()) {
                this.mForegroundUidPids.remove(Integer.valueOf(uid));
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleBlockActivity(TopTaskInfoContainer currentTask, Intent newActivityIntent) {
        int displayId = newActivityIntent.getIntExtra(CarPackageManagerService.BLOCKING_INTENT_EXTRA_DISPLAY_ID, 0);
        if (Log.isLoggable(CarLog.TAG_AM, 3)) {
            Slog.d(CarLog.TAG_AM, "Launching blocking activity on display: " + displayId);
        }
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(displayId);
        this.mContext.startActivityAsUser(newActivityIntent, options.toBundle(), new UserHandle(currentTask.stackInfo.userId));
        findTaskAndGrantFocus(newActivityIntent.getComponent());
    }

    private void findTaskAndGrantFocus(ComponentName activity) {
        try {
            List<ActivityManager.StackInfo> infos = this.mAm.getAllStackInfos();
            for (ActivityManager.StackInfo info : infos) {
                if (info.taskNames.length != 0) {
                    ComponentName topActivity = ComponentName.unflattenFromString(info.taskNames[info.taskNames.length - 1]);
                    if (activity.equals(topActivity)) {
                        try {
                            this.mAm.setFocusedStack(info.stackId);
                            return;
                        } catch (RemoteException e) {
                            Slog.e(CarLog.TAG_AM, "cannot setFocusedStack to stack:" + info.stackId, e);
                            return;
                        }
                    }
                }
            }
            Slog.i(CarLog.TAG_AM, "cannot give focus, cannot find Activity:" + activity);
        } catch (RemoteException e2) {
            Slog.e(CarLog.TAG_AM, "cannot getTasks", e2);
        }
    }

    /* loaded from: classes3.dex */
    private class ProcessObserver extends IProcessObserver.Stub {
        private ProcessObserver() {
        }

        public void onForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities) {
            if (Log.isLoggable(CarLog.TAG_AM, 4)) {
                Slog.i(CarLog.TAG_AM, String.format("onForegroundActivitiesChanged uid %d pid %d fg %b", Integer.valueOf(uid), Integer.valueOf(pid), Boolean.valueOf(foregroundActivities)));
            }
            SystemActivityMonitoringService.this.mHandler.requestForegroundActivitiesChanged(pid, uid, foregroundActivities);
        }

        public void onForegroundServicesChanged(int pid, int uid, int fgServiceTypes) {
        }

        public void onProcessDied(int pid, int uid) {
            SystemActivityMonitoringService.this.mHandler.requestProcessDied(pid, uid);
        }
    }

    /* loaded from: classes3.dex */
    private class TaskListener extends TaskStackListener {
        private TaskListener() {
        }

        public void onTaskStackChanged() {
            if (Log.isLoggable(CarLog.TAG_AM, 4)) {
                Slog.i(CarLog.TAG_AM, "onTaskStackChanged");
            }
            SystemActivityMonitoringService.this.mHandler.requestUpdatingTask();
        }
    }

    /* loaded from: classes3.dex */
    private class ActivityMonitorHandler extends Handler {
        private static final int MSG_BLOCK_ACTIVITY = 3;
        private static final int MSG_FOREGROUND_ACTIVITIES_CHANGED = 1;
        private static final int MSG_PROCESS_DIED = 2;
        private static final int MSG_UPDATE_TASKS = 0;

        private ActivityMonitorHandler(Looper looper) {
            super(looper);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void requestUpdatingTask() {
            Message msg = obtainMessage(0);
            sendMessage(msg);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void requestForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities) {
            Message msg = obtainMessage(1, pid, uid, Boolean.valueOf(foregroundActivities));
            sendMessage(msg);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void requestProcessDied(int pid, int uid) {
            Message msg = obtainMessage(2, pid, uid);
            sendMessage(msg);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void requestBlockActivity(TopTaskInfoContainer currentTask, Intent newActivityIntent) {
            Message msg = obtainMessage(3, new Pair(currentTask, newActivityIntent));
            sendMessage(msg);
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            int i = msg.what;
            if (i == 0) {
                SystemActivityMonitoringService.this.updateTasks();
            } else if (i == 1) {
                SystemActivityMonitoringService.this.handleForegroundActivitiesChanged(msg.arg1, msg.arg2, ((Boolean) msg.obj).booleanValue());
                SystemActivityMonitoringService.this.updateTasks();
            } else if (i == 2) {
                SystemActivityMonitoringService.this.handleProcessDied(msg.arg1, msg.arg2);
            } else if (i == 3) {
                Pair<TopTaskInfoContainer, Intent> pair = (Pair) msg.obj;
                SystemActivityMonitoringService.this.handleBlockActivity((TopTaskInfoContainer) pair.first, (Intent) pair.second);
            }
        }
    }
}
