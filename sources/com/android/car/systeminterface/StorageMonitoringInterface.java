package com.android.car.systeminterface;

import com.android.car.storagemonitoring.EMmcWearInformationProvider;
import com.android.car.storagemonitoring.HealthServiceWearInfoProvider;
import com.android.car.storagemonitoring.LifetimeWriteInfoProvider;
import com.android.car.storagemonitoring.ProcfsUidIoStatsProvider;
import com.android.car.storagemonitoring.SysfsLifetimeWriteInfoProvider;
import com.android.car.storagemonitoring.UfsWearInformationProvider;
import com.android.car.storagemonitoring.UidIoStatsProvider;
import com.android.car.storagemonitoring.WearInformationProvider;
/* loaded from: classes3.dex */
public interface StorageMonitoringInterface {

    /* loaded from: classes3.dex */
    public static class DefaultImpl implements StorageMonitoringInterface {
    }

    default WearInformationProvider[] getFlashWearInformationProviders() {
        return new WearInformationProvider[]{new EMmcWearInformationProvider(), new UfsWearInformationProvider(), new HealthServiceWearInfoProvider()};
    }

    default UidIoStatsProvider getUidIoStatsProvider() {
        return new ProcfsUidIoStatsProvider();
    }

    default LifetimeWriteInfoProvider getLifetimeWriteInfoProvider() {
        return new SysfsLifetimeWriteInfoProvider();
    }
}
