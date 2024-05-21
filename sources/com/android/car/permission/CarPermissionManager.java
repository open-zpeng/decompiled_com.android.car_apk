package com.android.car.permission;

import android.annotation.SuppressLint;
import com.android.car.CarLocalServices;
/* loaded from: classes3.dex */
public class CarPermissionManager {
    @SuppressLint({"StaticFieldLeak"})
    private static CarPermissionManagerService mService;

    public static CarPermissionManagerService get() {
        if (mService == null) {
            mService = (CarPermissionManagerService) CarLocalServices.getService(CarPermissionManagerService.class);
        }
        return mService;
    }
}
