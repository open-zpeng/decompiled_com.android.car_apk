package com.android.settingslib.accessibility;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import java.util.List;
/* loaded from: classes3.dex */
public class AccessibilityButtonHelper {
    public static boolean isRequestedByMagnification(Context ctx) {
        return Settings.Secure.getInt(ctx.getContentResolver(), "accessibility_display_magnification_navbar_enabled", 0) == 1;
    }

    public static boolean isRequestedByAccessibilityService(Context ctx) {
        AccessibilityManager accessibilityManager = (AccessibilityManager) ctx.getSystemService(AccessibilityManager.class);
        List<AccessibilityServiceInfo> services = accessibilityManager.getEnabledAccessibilityServiceList(-1);
        if (services != null) {
            int size = services.size();
            for (int i = 0; i < size; i++) {
                if ((services.get(i).flags & 256) != 0) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    public static boolean isRequested(Context ctx) {
        return isRequestedByMagnification(ctx) || isRequestedByAccessibilityService(ctx);
    }
}
