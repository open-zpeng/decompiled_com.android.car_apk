package com.android.settingslib.deviceinfo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import com.android.internal.util.ArrayUtils;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;
import java.lang.ref.WeakReference;
/* loaded from: classes3.dex */
public abstract class AbstractConnectivityPreferenceController extends AbstractPreferenceController implements LifecycleObserver, OnStart, OnStop {
    private static final int EVENT_UPDATE_CONNECTIVITY = 600;
    private final BroadcastReceiver mConnectivityReceiver;
    private Handler mHandler;

    protected abstract String[] getConnectivityIntents();

    protected abstract void updateConnectivity();

    public AbstractConnectivityPreferenceController(Context context, Lifecycle lifecycle) {
        super(context);
        this.mConnectivityReceiver = new BroadcastReceiver() { // from class: com.android.settingslib.deviceinfo.AbstractConnectivityPreferenceController.1
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context2, Intent intent) {
                String action = intent.getAction();
                if (ArrayUtils.contains(AbstractConnectivityPreferenceController.this.getConnectivityIntents(), action)) {
                    AbstractConnectivityPreferenceController.this.getHandler().sendEmptyMessage(AbstractConnectivityPreferenceController.EVENT_UPDATE_CONNECTIVITY);
                }
            }
        };
        if (lifecycle != null) {
            lifecycle.addObserver(this);
        }
    }

    @Override // com.android.settingslib.core.lifecycle.events.OnStop
    public void onStop() {
        this.mContext.unregisterReceiver(this.mConnectivityReceiver);
    }

    @Override // com.android.settingslib.core.lifecycle.events.OnStart
    public void onStart() {
        IntentFilter connectivityIntentFilter = new IntentFilter();
        String[] intents = getConnectivityIntents();
        for (String intent : intents) {
            connectivityIntentFilter.addAction(intent);
        }
        this.mContext.registerReceiver(this.mConnectivityReceiver, connectivityIntentFilter, "android.permission.CHANGE_NETWORK_STATE", null);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public Handler getHandler() {
        if (this.mHandler == null) {
            this.mHandler = new ConnectivityEventHandler(this);
        }
        return this.mHandler;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static class ConnectivityEventHandler extends Handler {
        private WeakReference<AbstractConnectivityPreferenceController> mPreferenceController;

        public ConnectivityEventHandler(AbstractConnectivityPreferenceController activity) {
            this.mPreferenceController = new WeakReference<>(activity);
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            AbstractConnectivityPreferenceController preferenceController = this.mPreferenceController.get();
            if (preferenceController == null) {
                return;
            }
            if (msg.what == AbstractConnectivityPreferenceController.EVENT_UPDATE_CONNECTIVITY) {
                preferenceController.updateConnectivity();
                return;
            }
            throw new IllegalStateException("Unknown message " + msg.what);
        }
    }
}
