package com.android.car;

import android.car.hardware.property.CarPropertyEvent;
import java.util.function.Function;
/* compiled from: lambda */
/* renamed from: com.android.car.-$$Lambda$o3zP4Oj56DnL7t27aVv1kJbnwAk  reason: invalid class name */
/* loaded from: classes3.dex */
public final /* synthetic */ class $$Lambda$o3zP4Oj56DnL7t27aVv1kJbnwAk implements Function {
    public static final /* synthetic */ $$Lambda$o3zP4Oj56DnL7t27aVv1kJbnwAk INSTANCE = new $$Lambda$o3zP4Oj56DnL7t27aVv1kJbnwAk();

    private /* synthetic */ $$Lambda$o3zP4Oj56DnL7t27aVv1kJbnwAk() {
    }

    @Override // java.util.function.Function
    public final Object apply(Object obj) {
        return ((CarPropertyEvent) obj).getCarPropertyValue();
    }
}
