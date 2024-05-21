package com.android.car;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
/* loaded from: classes3.dex */
class OnShutdownReboot {
    private final Context mContext;
    private final Object mLock = new Object();
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() { // from class: com.android.car.OnShutdownReboot.1
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            Iterator it = OnShutdownReboot.this.mActions.iterator();
            while (it.hasNext()) {
                BiConsumer<Context, Intent> action = (BiConsumer) it.next();
                action.accept(context, intent);
            }
        }
    };
    private final CopyOnWriteArrayList<BiConsumer<Context, Intent>> mActions = new CopyOnWriteArrayList<>();

    /* JADX INFO: Access modifiers changed from: package-private */
    public OnShutdownReboot(Context context) {
        this.mContext = context;
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.ACTION_SHUTDOWN");
        filter.addAction("android.intent.action.REBOOT");
        this.mContext.registerReceiver(this.mReceiver, filter);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public OnShutdownReboot addAction(BiConsumer<Context, Intent> action) {
        this.mActions.add(action);
        return this;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void clearActions() {
        this.mActions.clear();
    }
}
