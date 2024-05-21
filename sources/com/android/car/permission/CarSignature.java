package com.android.car.permission;

import android.annotation.TargetApi;
import android.text.TextUtils;
import android.util.Slog;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
@TargetApi(23)
/* loaded from: classes3.dex */
public class CarSignature {
    public static final String SALT = "signature";
    public static final String TAG = "CarPermissionManager";
    public static final int VEHICLE_PROPERTY_ACCESS_NONE = 0;
    public static final int VEHICLE_PROPERTY_ACCESS_READ = 1;
    public static final int VEHICLE_PROPERTY_ACCESS_READ_WRITE = 3;
    public static final int VEHICLE_PROPERTY_ACCESS_WRITE = 2;
    public int mAppFlag;
    public String mAppId;
    public String mAppId2;
    public String mEncode;
    public String mName;
    public String mPackageName;
    public String mSha1;
    public String mSha12;
    public int mUid;
    public int mEncodeType = 0;
    public int mDebug = 0;
    public int mVersion = 0;
    public Map<String, PropAccess> mPropAccessMap = new HashMap();

    /* loaded from: classes3.dex */
    public enum Access {
        NONE(0),
        READ(1),
        WRITE(2),
        READ_WRITE(3);
        
        int mAccess;

        Access(int value) {
            this.mAccess = value;
        }
    }

    /* loaded from: classes3.dex */
    public static class PropAccess {
        public int mAccess;
        public int mGranted;
        public String mName;

        public PropAccess() {
        }

        public PropAccess(String name, int access) {
            this.mName = name;
            this.mAccess = access;
        }

        public String toString() {
            return "PropAccess{mName='" + this.mName + "', mAccess=" + this.mAccess + ", mGranted=" + this.mGranted + '}';
        }
    }

    @TargetApi(23)
    public CarSignature(String sha1, String name, Map<String, PropAccess> map) {
        this.mSha1 = sha1;
        this.mPackageName = name;
        this.mPropAccessMap.putAll(map);
    }

    public CarSignature() {
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean checkPropAccess(String propName, int access) {
        PropAccess propAccess = this.mPropAccessMap.get(propName);
        if (propAccess == null || propAccess.mAccess <= 0 || access > propAccess.mAccess || propAccess.mGranted == 0) {
            Slog.i(TAG, "checkPropAccess propName=" + propName + "; access=" + access + "; propAccess=" + propAccess);
            return false;
        }
        return true;
    }

    public static int getAccessType(String value) {
        if (TextUtils.isEmpty(value)) {
            return 1;
        }
        String target = value.toUpperCase(Locale.ROOT);
        char c = 65535;
        int hashCode = target.hashCode();
        if (hashCode != 82) {
            if (hashCode != 87) {
                if (hashCode != 2629) {
                    if (hashCode != 2511254) {
                        if (hashCode != 82862015) {
                            if (hashCode == 1247349718 && target.equals("READ_WRITE")) {
                                c = 2;
                            }
                        } else if (target.equals("WRITE")) {
                            c = 0;
                        }
                    } else if (target.equals("READ")) {
                        c = 4;
                    }
                } else if (target.equals("RW")) {
                    c = 3;
                }
            } else if (target.equals("W")) {
                c = 1;
            }
        } else if (target.equals("R")) {
            c = 5;
        }
        if (c == 0 || c == 1) {
            return 2;
        }
        return (c == 2 || c == 3) ? 3 : 1;
    }

    public String toString() {
        return "CarSignature{mPackageName='" + this.mPackageName + "', mSha1='" + this.mSha1 + "', mAppId='" + this.mAppId + "', mPropAccessMap=" + this.mPropAccessMap + '}';
    }
}
