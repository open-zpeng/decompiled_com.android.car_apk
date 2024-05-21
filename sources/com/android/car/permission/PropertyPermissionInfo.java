package com.android.car.permission;
/* loaded from: classes3.dex */
public class PropertyPermissionInfo {
    public int mAccess;
    public int mAppFlag;
    public long mCost;
    public long mCostAvg;
    public long mCount;
    public boolean mIsGrant;
    public String mPackageName;
    public int mPropId;
    public String mPropName;

    public String toString() {
        return "{pkg:'" + this.mPackageName + "', name:'" + this.mPropName + "', access:'" + this.mAccess + "', castAvg:'" + this.mCostAvg + "', cast:'" + this.mCost + "', count:'" + this.mCount + "'}";
    }
}
