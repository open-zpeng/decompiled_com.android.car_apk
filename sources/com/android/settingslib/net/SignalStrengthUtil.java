package com.android.settingslib.net;

import android.content.Context;
import android.telephony.SubscriptionManager;
/* loaded from: classes3.dex */
public class SignalStrengthUtil {
    public static boolean shouldInflateSignalStrength(Context context, int subscriptionId) {
        return SubscriptionManager.getResourcesForSubId(context, subscriptionId).getBoolean(17891471);
    }
}
