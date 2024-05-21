package com.android.car;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.util.Slog;
import com.android.internal.annotations.GuardedBy;
import java.util.HashSet;
import java.util.Set;
/* loaded from: classes3.dex */
final class CanBusErrorNotifier {
    private static final boolean IS_RELEASE_BUILD = "user".equals(Build.TYPE);
    private static final int NOTIFICATION_ID = 1;
    private static final String TAG = "CAR.CAN_BUS.NOTIFIER";
    private final Context mContext;
    private final NotificationManager mNotificationManager;
    @GuardedBy({"this"})
    private final Set<Object> mReportedObjects = new HashSet();

    /* JADX INFO: Access modifiers changed from: package-private */
    public CanBusErrorNotifier(Context context) {
        this.mNotificationManager = (NotificationManager) context.getSystemService("notification");
        this.mContext = context;
    }

    public void removeFailureReport(Object sender) {
        setCanBusFailure(false, sender);
    }

    public void reportFailure(Object sender) {
        setCanBusFailure(true, sender);
    }

    private void setCanBusFailure(boolean failed, Object sender) {
        synchronized (this) {
            if (failed ? this.mReportedObjects.add(sender) : this.mReportedObjects.remove(sender)) {
                boolean changed = !this.mReportedObjects.isEmpty();
                if (Log.isLoggable(TAG, 4)) {
                    Slog.i(TAG, "Changing CAN bus failure state to " + changed);
                }
                if (changed) {
                    showNotification();
                } else {
                    hideNotification();
                }
            }
        }
    }

    private void showNotification() {
        if (IS_RELEASE_BUILD) {
            return;
        }
        Notification notification = new Notification.Builder(this.mContext, "miscellaneous").setContentTitle(this.mContext.getString(R.string.car_can_bus_failure)).setContentText(this.mContext.getString(R.string.car_can_bus_failure_desc)).setSmallIcon(R.drawable.car_ic_error).setOngoing(true).build();
        this.mNotificationManager.notify(TAG, 1, notification);
    }

    private void hideNotification() {
        if (IS_RELEASE_BUILD) {
            return;
        }
        this.mNotificationManager.cancel(TAG, 1);
    }
}
