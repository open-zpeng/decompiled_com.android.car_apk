package com.android.car.garagemode;

import android.content.Context;
import android.os.Looper;
import com.android.car.CarServiceBase;
import com.android.internal.annotations.VisibleForTesting;
import java.io.PrintWriter;
import java.util.List;
/* loaded from: classes3.dex */
public class GarageModeService implements CarServiceBase {
    private static final Logger LOG = new Logger("Service");
    private final Context mContext;
    private final Controller mController;

    public GarageModeService(Context context) {
        this(context, null);
    }

    @VisibleForTesting
    protected GarageModeService(Context context, Controller controller) {
        this.mContext = context;
        this.mController = controller != null ? controller : new Controller(context, Looper.myLooper());
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
        this.mController.init();
    }

    @Override // com.android.car.CarServiceBase
    public void release() {
        this.mController.release();
    }

    @Override // com.android.car.CarServiceBase
    public void dump(PrintWriter writer) {
        boolean isActive = this.mController.isGarageModeActive();
        writer.println("GarageModeInProgress " + isActive);
        List<String> status = this.mController.dump();
        for (int idx = 0; idx < status.size(); idx++) {
            writer.println(status.get(idx));
        }
    }

    public boolean isGarageModeActive() {
        return this.mController.isGarageModeActive();
    }

    public void forceStartGarageMode() {
        this.mController.init();
        this.mController.initiateGarageMode(null);
    }

    public void stopAndResetGarageMode() {
        this.mController.resetGarageMode();
        this.mController.release();
    }
}
