package com.android.settingslib.net;

import android.content.Context;
import android.util.SparseArray;
/* loaded from: classes3.dex */
public class UidDetailProvider {
    public static final int OTHER_USER_RANGE_START = -2000;
    private static final String TAG = "DataUsage";
    private final Context mContext;
    private final SparseArray<UidDetail> mUidDetailCache = new SparseArray<>();

    public static int buildKeyForUser(int userHandle) {
        return (-2000) - userHandle;
    }

    public static boolean isKeyForUser(int key) {
        return key <= -2000;
    }

    public static int getUserIdForKey(int key) {
        return (-2000) - key;
    }

    public UidDetailProvider(Context context) {
        this.mContext = context.getApplicationContext();
    }

    public void clearCache() {
        synchronized (this.mUidDetailCache) {
            this.mUidDetailCache.clear();
        }
    }

    public UidDetail getUidDetail(int uid, boolean blocking) {
        UidDetail detail;
        synchronized (this.mUidDetailCache) {
            detail = this.mUidDetailCache.get(uid);
        }
        if (detail != null) {
            return detail;
        }
        if (!blocking) {
            return null;
        }
        UidDetail detail2 = buildUidDetail(uid);
        synchronized (this.mUidDetailCache) {
            this.mUidDetailCache.put(uid, detail2);
        }
        return detail2;
    }

    /* JADX WARN: Removed duplicated region for block: B:67:0x017c  */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    private com.android.settingslib.net.UidDetail buildUidDetail(int r21) {
        /*
            Method dump skipped, instructions count: 472
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.settingslib.net.UidDetailProvider.buildUidDetail(int):com.android.settingslib.net.UidDetail");
    }
}
