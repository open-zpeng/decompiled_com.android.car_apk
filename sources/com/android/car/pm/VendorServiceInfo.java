package com.android.car.pm;

import android.content.ComponentName;
import android.content.Intent;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
/* JADX INFO: Access modifiers changed from: package-private */
/* loaded from: classes3.dex */
public class VendorServiceInfo {
    private static final int BIND = 0;
    private static final String KEY_BIND = "bind";
    private static final String KEY_TRIGGER = "trigger";
    private static final String KEY_USER_SCOPE = "user";
    private static final int START = 1;
    private static final int START_FOREGROUND = 2;
    private static final int TRIGGER_ASAP = 0;
    private static final int TRIGGER_UNLOCKED = 1;
    private static final int USER_SCOPE_ALL = 0;
    private static final int USER_SCOPE_FOREGROUND = 2;
    private static final int USER_SCOPE_SYSTEM = 1;
    private final int mBind;
    private final ComponentName mComponentName;
    private final int mTrigger;
    private final int mUserScope;

    @Retention(RetentionPolicy.SOURCE)
    /* loaded from: classes3.dex */
    @interface Bind {
    }

    @Retention(RetentionPolicy.SOURCE)
    /* loaded from: classes3.dex */
    @interface Trigger {
    }

    @Retention(RetentionPolicy.SOURCE)
    /* loaded from: classes3.dex */
    @interface UserScope {
    }

    private VendorServiceInfo(ComponentName componentName, int bind, int userScope, int trigger) {
        this.mComponentName = componentName;
        this.mUserScope = userScope;
        this.mTrigger = trigger;
        this.mBind = bind;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean isSystemUserService() {
        int i = this.mUserScope;
        return i == 0 || i == 1;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean isForegroundUserService() {
        int i = this.mUserScope;
        return i == 0 || i == 2;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean shouldStartOnUnlock() {
        return this.mTrigger == 1;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean shouldStartAsap() {
        return this.mTrigger == 0;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean shouldBeBound() {
        return this.mBind == 0;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean shouldBeStartedInForeground() {
        return this.mBind == 2;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public Intent getIntent() {
        Intent intent = new Intent();
        intent.setComponent(this.mComponentName);
        return intent;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* JADX WARN: Removed duplicated region for block: B:33:0x007c  */
    /* JADX WARN: Removed duplicated region for block: B:58:0x0101  */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static com.android.car.pm.VendorServiceInfo parse(java.lang.String r17) {
        /*
            Method dump skipped, instructions count: 376
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.car.pm.VendorServiceInfo.parse(java.lang.String):com.android.car.pm.VendorServiceInfo");
    }

    public String toString() {
        return "VendorService{component=" + this.mComponentName + ", bind=" + this.mBind + ", trigger=" + this.mTrigger + ", user=" + this.mUserScope + '}';
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public String toShortString() {
        ComponentName componentName = this.mComponentName;
        return componentName != null ? componentName.toShortString() : "";
    }
}
