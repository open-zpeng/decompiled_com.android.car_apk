package com.android.settingslib.applications;

import android.content.res.Configuration;
import android.content.res.Resources;
/* loaded from: classes3.dex */
public class InterestingConfigChanges {
    private final int mFlags;
    private final Configuration mLastConfiguration;
    private int mLastDensity;

    public InterestingConfigChanges() {
        this(-2147482876);
    }

    public InterestingConfigChanges(int flags) {
        this.mLastConfiguration = new Configuration();
        this.mFlags = flags;
    }

    public boolean applyNewConfig(Resources res) {
        Configuration configuration = this.mLastConfiguration;
        int configChanges = configuration.updateFrom(Configuration.generateDelta(configuration, res.getConfiguration()));
        boolean densityChanged = this.mLastDensity != res.getDisplayMetrics().densityDpi;
        if (densityChanged || (this.mFlags & configChanges) != 0) {
            this.mLastDensity = res.getDisplayMetrics().densityDpi;
            return true;
        }
        return false;
    }
}
