package com.android.settingslib.location;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.PermissionChecker;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.IconDrawableFactory;
import androidx.annotation.VisibleForTesting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
/* loaded from: classes3.dex */
public class RecentLocationApps {
    @VisibleForTesting
    static final String ANDROID_SYSTEM_PACKAGE_NAME = "android";
    private static final long RECENT_TIME_INTERVAL_MILLIS = 86400000;
    private final Context mContext;
    private final IconDrawableFactory mDrawableFactory;
    private final PackageManager mPackageManager;
    private static final String TAG = RecentLocationApps.class.getSimpleName();
    @VisibleForTesting
    static final int[] LOCATION_REQUEST_OPS = {41, 42};
    @VisibleForTesting
    static final int[] LOCATION_PERMISSION_OPS = {1, 0};

    public RecentLocationApps(Context context) {
        this.mContext = context;
        this.mPackageManager = context.getPackageManager();
        this.mDrawableFactory = IconDrawableFactory.newInstance(context);
    }

    public List<Request> getAppList(boolean showSystemApps) {
        PackageManager pm;
        AppOpsManager aoManager;
        List<AppOpsManager.PackageOps> appOps;
        int appOpsCount;
        Request request;
        PackageManager pm2 = this.mContext.getPackageManager();
        AppOpsManager aoManager2 = (AppOpsManager) this.mContext.getSystemService("appops");
        List<AppOpsManager.PackageOps> appOps2 = aoManager2.getPackagesForOps(LOCATION_REQUEST_OPS);
        int appOpsCount2 = appOps2 != null ? appOps2.size() : 0;
        ArrayList<Request> requests = new ArrayList<>(appOpsCount2);
        long now = System.currentTimeMillis();
        UserManager um = (UserManager) this.mContext.getSystemService("user");
        List<UserHandle> profiles = um.getUserProfiles();
        int i = 0;
        while (i < appOpsCount2) {
            AppOpsManager.PackageOps ops = appOps2.get(i);
            String packageName = ops.getPackageName();
            int uid = ops.getUid();
            UserHandle user = UserHandle.getUserHandleForUid(uid);
            boolean isAndroidOs = uid == 1000 && "android".equals(packageName);
            if (isAndroidOs) {
                pm = pm2;
                aoManager = aoManager2;
                appOps = appOps2;
                appOpsCount = appOpsCount2;
            } else if (!profiles.contains(user)) {
                pm = pm2;
                aoManager = aoManager2;
                appOps = appOps2;
                appOpsCount = appOpsCount2;
            } else {
                boolean showApp = true;
                if (showSystemApps) {
                    pm = pm2;
                    aoManager = aoManager2;
                    appOps = appOps2;
                    appOpsCount = appOpsCount2;
                } else {
                    aoManager = aoManager2;
                    int[] iArr = LOCATION_PERMISSION_OPS;
                    appOps = appOps2;
                    int length = iArr.length;
                    int i2 = 0;
                    while (true) {
                        if (i2 >= length) {
                            pm = pm2;
                            appOpsCount = appOpsCount2;
                            break;
                        }
                        int op = iArr[i2];
                        int[] iArr2 = iArr;
                        String permission = AppOpsManager.opToPermission(op);
                        int i3 = length;
                        int permissionFlags = pm2.getPermissionFlags(permission, packageName, user);
                        pm = pm2;
                        appOpsCount = appOpsCount2;
                        if (PermissionChecker.checkPermissionForPreflight(this.mContext, permission, -1, uid, packageName) == 0) {
                            if ((permissionFlags & 256) != 0) {
                                i2++;
                                iArr = iArr2;
                                length = i3;
                                pm2 = pm;
                                appOpsCount2 = appOpsCount;
                            } else {
                                showApp = false;
                                break;
                            }
                        } else if ((permissionFlags & 512) != 0) {
                            i2++;
                            iArr = iArr2;
                            length = i3;
                            pm2 = pm;
                            appOpsCount2 = appOpsCount;
                        } else {
                            showApp = false;
                            break;
                        }
                    }
                }
                if (showApp && (request = getRequestFromOps(now, ops)) != null) {
                    requests.add(request);
                }
            }
            i++;
            aoManager2 = aoManager;
            appOps2 = appOps;
            pm2 = pm;
            appOpsCount2 = appOpsCount;
        }
        return requests;
    }

    public List<Request> getAppListSorted(boolean showSystemApps) {
        List<Request> requests = getAppList(showSystemApps);
        Collections.sort(requests, Collections.reverseOrder(new Comparator<Request>() { // from class: com.android.settingslib.location.RecentLocationApps.1
            @Override // java.util.Comparator
            public int compare(Request request1, Request request2) {
                return Long.compare(request1.requestFinishTime, request2.requestFinishTime);
            }
        }));
        return requests;
    }

    /* JADX WARN: Incorrect condition in loop: B:4:0x0023 */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    private com.android.settingslib.location.RecentLocationApps.Request getRequestFromOps(long r29, android.app.AppOpsManager.PackageOps r31) {
        /*
            Method dump skipped, instructions count: 278
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.settingslib.location.RecentLocationApps.getRequestFromOps(long, android.app.AppOpsManager$PackageOps):com.android.settingslib.location.RecentLocationApps$Request");
    }

    /* loaded from: classes3.dex */
    public static class Request {
        public final CharSequence contentDescription;
        public final Drawable icon;
        public final boolean isHighBattery;
        public final CharSequence label;
        public final String packageName;
        public final long requestFinishTime;
        public final UserHandle userHandle;

        private Request(String packageName, UserHandle userHandle, Drawable icon, CharSequence label, boolean isHighBattery, CharSequence contentDescription, long requestFinishTime) {
            this.packageName = packageName;
            this.userHandle = userHandle;
            this.icon = icon;
            this.label = label;
            this.isHighBattery = isHighBattery;
            this.contentDescription = contentDescription;
            this.requestFinishTime = requestFinishTime;
        }
    }
}
