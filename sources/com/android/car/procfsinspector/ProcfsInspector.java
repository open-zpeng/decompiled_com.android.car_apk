package com.android.car.procfsinspector;

import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import com.android.car.procfsinspector.IProcfsInspector;
import java.util.Collections;
import java.util.List;
/* loaded from: classes3.dex */
public final class ProcfsInspector {
    private static final String SERVICE_NAME = "com.android.car.procfsinspector";
    private static final String TAG = "car.procfsinspector";
    private final IProcfsInspector mService;

    private ProcfsInspector(IProcfsInspector service) {
        this.mService = service;
    }

    private static IProcfsInspector tryGet() {
        return IProcfsInspector.Stub.asInterface(ServiceManager.getService(SERVICE_NAME));
    }

    public static List<ProcessInfo> readProcessTable() {
        IProcfsInspector procfsInspector = tryGet();
        if (procfsInspector != null) {
            try {
                return procfsInspector.readProcessTable();
            } catch (RemoteException e) {
                Log.w(TAG, "caught RemoteException", e);
            }
        }
        return Collections.emptyList();
    }
}
