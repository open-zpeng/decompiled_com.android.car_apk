package com.android.car;

import java.io.PrintWriter;
/* loaded from: classes3.dex */
public interface CarServiceBase {
    void dump(PrintWriter printWriter);

    void init();

    void release();

    default void vehicleHalReconnected() {
    }
}
