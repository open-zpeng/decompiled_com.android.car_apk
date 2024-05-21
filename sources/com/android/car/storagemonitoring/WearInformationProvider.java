package com.android.car.storagemonitoring;
/* loaded from: classes3.dex */
public interface WearInformationProvider {
    WearInformation load();

    default int convertLifetime(int lifetime) {
        if (lifetime <= 0 || lifetime > 11) {
            return -1;
        }
        return (lifetime - 1) * 10;
    }

    default int adjustEol(int eol) {
        if (eol <= 0 || eol > 3) {
            return 0;
        }
        return eol;
    }
}
