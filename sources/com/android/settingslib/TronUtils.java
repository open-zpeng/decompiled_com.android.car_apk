package com.android.settingslib;

import android.content.Context;
import com.android.internal.logging.MetricsLogger;
/* loaded from: classes3.dex */
public final class TronUtils {
    private static final String TAG = "TronUtils";

    private TronUtils() {
    }

    public static void logWifiSettingsSpeed(Context context, int speedEnum) {
        MetricsLogger.histogram(context, "settings_wifi_speed_labels", speedEnum);
    }
}
