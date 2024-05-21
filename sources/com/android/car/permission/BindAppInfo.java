package com.android.car.permission;
/* loaded from: classes3.dex */
public class BindAppInfo {
    public static final int APP_FLAG_3RD = 4;
    public static final int APP_FLAG_ALL = 7;
    public static final int APP_FLAG_NONE = 0;
    public static final int APP_FLAG_SYSTEM_SIGN = 2;
    public static final int APP_FLAG_SYSTEM_UID = 1;
    public int mAppFlag = 0;
    public String mPackageName;
    public int mPid;
    public long mPropCount;
    public int mUid;

    public BindAppInfo(int uid, int pid, String packageName) {
        this.mUid = uid;
        this.mPid = pid;
        this.mPackageName = packageName;
    }

    public String toString() {
        return "BindAppInfo{mPackageName='" + this.mPackageName + "', mUid=" + this.mUid + ", mPid=" + this.mPid + ", mPropCount=" + this.mPropCount + ", mAppFlag=" + this.mAppFlag + '}';
    }
}
