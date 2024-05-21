package com.android.settingslib;

import android.content.Context;
import android.provider.Settings;
/* loaded from: classes3.dex */
public class WirelessUtils {
    public static boolean isRadioAllowed(Context context, String type) {
        if (isAirplaneModeOn(context)) {
            String toggleable = Settings.Global.getString(context.getContentResolver(), "airplane_mode_toggleable_radios");
            return toggleable != null && toggleable.contains(type);
        }
        return true;
    }

    public static boolean isAirplaneModeOn(Context context) {
        return Settings.Global.getInt(context.getContentResolver(), "airplane_mode_on", 0) != 0;
    }
}
