package com.android.car.trust;

import android.util.Log;
import android.util.Slog;
import com.android.car.BLEStreamProtos.VersionExchangeProto;
/* loaded from: classes3.dex */
class BLEVersionExchangeResolver {
    private static final int MESSAGING_VERSION = 1;
    private static final int SECURITY_VERSION = 1;
    private static final String TAG = "BLEVersionExchangeResolver";

    /* JADX INFO: Access modifiers changed from: package-private */
    public static boolean hasSupportedVersion(VersionExchangeProto.BLEVersionExchange versionExchange) {
        int minMessagingVersion = versionExchange.getMinSupportedMessagingVersion();
        int minSecurityVersion = versionExchange.getMinSupportedSecurityVersion();
        if (Log.isLoggable(TAG, 3)) {
            Slog.d(TAG, "Checking for supported version on (minMessagingVersion: " + minMessagingVersion + ", minSecurityVersion: " + minSecurityVersion + ")");
        }
        return minMessagingVersion == 1 && minSecurityVersion == 1;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static VersionExchangeProto.BLEVersionExchange makeVersionExchange() {
        return VersionExchangeProto.BLEVersionExchange.newBuilder().setMinSupportedMessagingVersion(1).setMaxSupportedMessagingVersion(1).setMinSupportedSecurityVersion(1).setMaxSupportedSecurityVersion(1).build();
    }

    private BLEVersionExchangeResolver() {
    }
}
