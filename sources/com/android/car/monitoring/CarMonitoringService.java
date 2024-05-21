package com.android.car.monitoring;

import android.annotation.SystemApi;
import android.car.encryptionrunner.DummyEncryptionRunner;
import android.content.Context;
import android.util.Slog;
import com.android.car.CarServiceBase;
import com.android.car.SystemActivityMonitoringService;
import java.io.PrintWriter;
@SystemApi
/* loaded from: classes3.dex */
public class CarMonitoringService implements CarServiceBase {
    private static final Boolean DBG = true;
    private static final int MONITORING_SLEEP_TIME_MS = 30000;
    private static final String TAG = "CAR.MONITORING";
    private final Context mContext;
    private final SystemActivityMonitoringService mSystemActivityMonitoringService;

    public CarMonitoringService(Context context, SystemActivityMonitoringService systemActivityMonitoringService) {
        this.mContext = context;
        this.mSystemActivityMonitoringService = systemActivityMonitoringService;
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
        if (DBG.booleanValue()) {
            Slog.d("CAR.MONITORING", DummyEncryptionRunner.INIT);
        }
    }

    @Override // com.android.car.CarServiceBase
    public void release() {
        if (DBG.booleanValue()) {
            Slog.d("CAR.MONITORING", "release");
        }
    }

    @Override // com.android.car.CarServiceBase
    public void dump(PrintWriter writer) {
        writer.println("**" + getClass().getSimpleName() + "**");
    }
}
