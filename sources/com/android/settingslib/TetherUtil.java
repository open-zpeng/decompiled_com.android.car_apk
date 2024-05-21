package com.android.settingslib;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.telephony.CarrierConfigManager;
import androidx.annotation.VisibleForTesting;
/* loaded from: classes3.dex */
public class TetherUtil {
    @VisibleForTesting
    static boolean isEntitlementCheckRequired(Context context) {
        CarrierConfigManager configManager = (CarrierConfigManager) context.getSystemService("carrier_config");
        if (configManager == null || configManager.getConfig() == null) {
            return true;
        }
        return configManager.getConfig().getBoolean("require_entitlement_checks_bool");
    }

    public static boolean isProvisioningNeeded(Context context) {
        String[] provisionApp = context.getResources().getStringArray(17236048);
        return !SystemProperties.getBoolean("net.tethering.noprovisioning", false) && provisionApp != null && isEntitlementCheckRequired(context) && provisionApp.length == 2;
    }

    public static boolean isTetherAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(ConnectivityManager.class);
        boolean tetherConfigDisallowed = RestrictedLockUtilsInternal.checkIfRestrictionEnforced(context, "no_config_tethering", UserHandle.myUserId()) != null;
        boolean hasBaseUserRestriction = RestrictedLockUtilsInternal.hasBaseUserRestriction(context, "no_config_tethering", UserHandle.myUserId());
        return (cm.isTetheringSupported() || tetherConfigDisallowed) && !hasBaseUserRestriction;
    }
}
