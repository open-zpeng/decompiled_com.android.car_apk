package com.android.settingslib.deviceinfo;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.text.format.DateUtils;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;
import java.lang.ref.WeakReference;
/* loaded from: classes3.dex */
public abstract class AbstractUptimePreferenceController extends AbstractPreferenceController implements LifecycleObserver, OnStart, OnStop {
    private static final int EVENT_UPDATE_STATS = 500;
    @VisibleForTesting
    static final String KEY_UPTIME = "up_time";
    private Handler mHandler;
    private Preference mUptime;

    public AbstractUptimePreferenceController(Context context, Lifecycle lifecycle) {
        super(context);
        if (lifecycle != null) {
            lifecycle.addObserver(this);
        }
    }

    @Override // com.android.settingslib.core.lifecycle.events.OnStart
    public void onStart() {
        getHandler().sendEmptyMessage(EVENT_UPDATE_STATS);
    }

    @Override // com.android.settingslib.core.lifecycle.events.OnStop
    public void onStop() {
        getHandler().removeMessages(EVENT_UPDATE_STATS);
    }

    @Override // com.android.settingslib.core.AbstractPreferenceController
    public boolean isAvailable() {
        return true;
    }

    @Override // com.android.settingslib.core.AbstractPreferenceController, com.android.settingslib.core.ConfirmationDialogController
    public String getPreferenceKey() {
        return KEY_UPTIME;
    }

    @Override // com.android.settingslib.core.AbstractPreferenceController
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        this.mUptime = screen.findPreference(KEY_UPTIME);
        updateTimes();
    }

    private Handler getHandler() {
        if (this.mHandler == null) {
            this.mHandler = new MyHandler(this);
        }
        return this.mHandler;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateTimes() {
        this.mUptime.setSummary(DateUtils.formatElapsedTime(SystemClock.elapsedRealtime() / 1000));
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static class MyHandler extends Handler {
        private WeakReference<AbstractUptimePreferenceController> mStatus;

        public MyHandler(AbstractUptimePreferenceController activity) {
            this.mStatus = new WeakReference<>(activity);
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            AbstractUptimePreferenceController status = this.mStatus.get();
            if (status == null) {
                return;
            }
            if (msg.what == AbstractUptimePreferenceController.EVENT_UPDATE_STATS) {
                status.updateTimes();
                sendEmptyMessageDelayed(AbstractUptimePreferenceController.EVENT_UPDATE_STATS, 1000L);
                return;
            }
            throw new IllegalStateException("Unknown message " + msg.what);
        }
    }
}
