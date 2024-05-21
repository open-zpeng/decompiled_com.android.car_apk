package com.android.settingslib.net;

import android.content.Context;
import android.net.NetworkTemplate;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.android.internal.util.ArrayUtils;
/* loaded from: classes3.dex */
public class DataUsageUtils {
    private static final String TAG = "DataUsageUtils";

    public static NetworkTemplate getMobileTemplate(Context context, int subId) {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(TelephonyManager.class);
        SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(SubscriptionManager.class);
        NetworkTemplate mobileAll = NetworkTemplate.buildTemplateMobileAll(telephonyManager.getSubscriberId(subId));
        if (!subscriptionManager.isActiveSubId(subId)) {
            Log.i(TAG, "Subscription is not active: " + subId);
            return mobileAll;
        }
        String[] mergedSubscriberIds = telephonyManager.createForSubscriptionId(subId).getMergedSubscriberIdsFromGroup();
        if (ArrayUtils.isEmpty(mergedSubscriberIds)) {
            Log.i(TAG, "mergedSubscriberIds is null.");
            return mobileAll;
        }
        return NetworkTemplate.normalize(mobileAll, mergedSubscriberIds);
    }
}
