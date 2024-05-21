package com.android.settingslib.wifi;

import android.content.Context;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.wifi.WifiTracker;
/* loaded from: classes3.dex */
public class WifiTrackerFactory {
    private static WifiTracker sTestingWifiTracker;

    @Keep
    public static void setTestingWifiTracker(WifiTracker tracker) {
        sTestingWifiTracker = tracker;
    }

    public static WifiTracker create(Context context, WifiTracker.WifiListener wifiListener, @NonNull Lifecycle lifecycle, boolean includeSaved, boolean includeScans) {
        WifiTracker wifiTracker = sTestingWifiTracker;
        if (wifiTracker != null) {
            return wifiTracker;
        }
        return new WifiTracker(context, wifiListener, lifecycle, includeSaved, includeScans);
    }
}
