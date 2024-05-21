package com.android.car.hal;
/* loaded from: classes3.dex */
public class PropertyTimeoutException extends Exception {
    /* JADX INFO: Access modifiers changed from: package-private */
    public PropertyTimeoutException(int property) {
        super("Property 0x" + Integer.toHexString(property) + " is not ready yet.");
    }
}
