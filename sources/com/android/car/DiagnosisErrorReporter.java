package com.android.car;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Slog;
import android.util.SparseArray;
import com.xiaopeng.aftersales.manager.AfterSalesManager;
import java.util.concurrent.atomic.AtomicBoolean;
/* loaded from: classes3.dex */
public class DiagnosisErrorReporter {
    private static final int CARSERVICE_ERROR_AUTOPILOT_REGISTER_PROPERTIES_FAILED = 14002;
    private static final int CARSERVICE_ERROR_ICM_NOT_CONNECTED = 14001;
    private static final int CARSERVICE_ERROR_TBOX_NOT_CONNECTED = 14000;
    private static final int FIRST_CARSERVICE_ERROR = 14000;
    private static final String TAG = "DiagnosisErrorReporter";
    private static volatile DiagnosisErrorReporter sDiagnosisErrorReporter;
    private static final SparseArray<String> sErrorDescription = new SparseArray<>();
    private final AfterSalesManager mAfterSalesManager;
    private AtomicBoolean mIsRemoteServiceBinded = new AtomicBoolean(false);

    static {
        sErrorDescription.put(14000, "Connect TBOX timeout");
        sErrorDescription.put(CARSERVICE_ERROR_ICM_NOT_CONNECTED, "Connect ICM timeout");
        sErrorDescription.put(CARSERVICE_ERROR_AUTOPILOT_REGISTER_PROPERTIES_FAILED, "Autopilot register properties callback failed");
    }

    private DiagnosisErrorReporter(Context context) {
        ServiceConnection afterSalesServiceConnection = new ServiceConnection() { // from class: com.android.car.DiagnosisErrorReporter.1
            @Override // android.content.ServiceConnection
            public void onServiceConnected(ComponentName name, IBinder service) {
                Slog.i(DiagnosisErrorReporter.TAG, "AfterSales onServiceConnected, name: " + name + ", service: " + service);
                DiagnosisErrorReporter.this.mIsRemoteServiceBinded.set(true);
            }

            @Override // android.content.ServiceConnection
            public void onServiceDisconnected(ComponentName name) {
                Slog.i(DiagnosisErrorReporter.TAG, "AfterSales onServiceDisconnected, name: " + name);
                DiagnosisErrorReporter.this.mIsRemoteServiceBinded.set(false);
            }
        };
        this.mAfterSalesManager = AfterSalesManager.createAfterSalesManager(context.getApplicationContext(), afterSalesServiceConnection);
        this.mAfterSalesManager.connect();
    }

    public static DiagnosisErrorReporter getInstance(Context context) {
        if (sDiagnosisErrorReporter == null) {
            synchronized (DiagnosisErrorReporter.class) {
                if (sDiagnosisErrorReporter == null) {
                    sDiagnosisErrorReporter = new DiagnosisErrorReporter(context);
                }
            }
        }
        return sDiagnosisErrorReporter;
    }

    private void recordDiagnosisError(int errorCode, String errorMsg, boolean alert) {
        this.mAfterSalesManager.recordDiagnosisError(14, errorCode, System.currentTimeMillis(), errorMsg, alert);
    }

    public void reportIcmNotConnected() {
        Slog.w(TAG, "Connect ICM timeout");
        if (!this.mIsRemoteServiceBinded.get()) {
            Slog.w(TAG, "After sales service not bound, ignore");
        } else {
            recordDiagnosisError(CARSERVICE_ERROR_ICM_NOT_CONNECTED, sErrorDescription.get(CARSERVICE_ERROR_ICM_NOT_CONNECTED), true);
        }
    }

    public void reportTboxNotConnected() {
        Slog.w(TAG, "Connect TBOX timeout");
        if (!this.mIsRemoteServiceBinded.get()) {
            Slog.w(TAG, "After sales service not bound, ignore");
        } else {
            recordDiagnosisError(14000, sErrorDescription.get(14000), true);
        }
    }

    public void reportAutopilotRegisterPropertiesFailed() {
        if (this.mIsRemoteServiceBinded.get()) {
            recordDiagnosisError(CARSERVICE_ERROR_AUTOPILOT_REGISTER_PROPERTIES_FAILED, sErrorDescription.get(CARSERVICE_ERROR_AUTOPILOT_REGISTER_PROPERTIES_FAILED), true);
        } else {
            Slog.w(TAG, "After sales service not bound, ignore");
        }
    }
}
