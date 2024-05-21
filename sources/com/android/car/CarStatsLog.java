package com.android.car;

import android.util.StatsLog;
import com.google.security.cryptauth.lib.securegcm.SecureGcmProto;
/* loaded from: classes3.dex */
public class CarStatsLog {
    public static void logPowerState(int state) {
        StatsLog.write(SecureGcmProto.GcmDeviceInfo.NOTIFICATION_ENABLED_FIELD_NUMBER, state);
    }

    public static void logGarageModeStart() {
        StatsLog.write(204, true);
    }

    public static void logGarageModeStop() {
        StatsLog.write(204, false);
    }
}
