package com.android.settingslib.location;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.PermissionChecker;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.IconDrawableFactory;
import android.util.Log;
import androidx.annotation.VisibleForTesting;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
/* loaded from: classes3.dex */
public class RecentLocationAccesses {
    @VisibleForTesting
    static final String ANDROID_SYSTEM_PACKAGE_NAME = "android";
    private static final long RECENT_TIME_INTERVAL_MILLIS = 86400000;
    public static final int TRUSTED_STATE_FLAGS = 13;
    private final Clock mClock;
    private final Context mContext;
    private final IconDrawableFactory mDrawableFactory;
    private final PackageManager mPackageManager;
    private static final String TAG = RecentLocationAccesses.class.getSimpleName();
    @VisibleForTesting
    static final int[] LOCATION_OPS = {1, 0};

    public RecentLocationAccesses(Context context) {
        this(context, Clock.systemDefaultZone());
    }

    @VisibleForTesting
    RecentLocationAccesses(Context context, Clock clock) {
        this.mContext = context;
        this.mPackageManager = context.getPackageManager();
        this.mDrawableFactory = IconDrawableFactory.newInstance(context);
        this.mClock = clock;
    }

    public List<Access> getAppList() {
        AppOpsManager aoManager;
        List<AppOpsManager.PackageOps> appOps;
        PackageManager pm;
        int appOpsCount;
        Access access;
        PackageManager pm2 = this.mContext.getPackageManager();
        AppOpsManager aoManager2 = (AppOpsManager) this.mContext.getSystemService("appops");
        List<AppOpsManager.PackageOps> appOps2 = aoManager2.getPackagesForOps(LOCATION_OPS);
        int appOpsCount2 = appOps2 != null ? appOps2.size() : 0;
        ArrayList<Access> accesses = new ArrayList<>(appOpsCount2);
        long now = this.mClock.millis();
        UserManager um = (UserManager) this.mContext.getSystemService("user");
        List<UserHandle> profiles = um.getUserProfiles();
        int i = 0;
        while (i < appOpsCount2) {
            AppOpsManager.PackageOps ops = appOps2.get(i);
            String packageName = ops.getPackageName();
            int uid = ops.getUid();
            UserHandle user = UserHandle.getUserHandleForUid(uid);
            if (!profiles.contains(user)) {
                pm = pm2;
                aoManager = aoManager2;
                appOps = appOps2;
                appOpsCount = appOpsCount2;
            } else {
                boolean showApp = true;
                int[] iArr = LOCATION_OPS;
                aoManager = aoManager2;
                int length = iArr.length;
                appOps = appOps2;
                int i2 = 0;
                while (true) {
                    if (i2 >= length) {
                        pm = pm2;
                        appOpsCount = appOpsCount2;
                        break;
                    }
                    int op = iArr[i2];
                    int i3 = length;
                    String permission = AppOpsManager.opToPermission(op);
                    int[] iArr2 = iArr;
                    int permissionFlags = pm2.getPermissionFlags(permission, packageName, user);
                    pm = pm2;
                    appOpsCount = appOpsCount2;
                    if (PermissionChecker.checkPermissionForPreflight(this.mContext, permission, -1, uid, packageName) == 0) {
                        if ((permissionFlags & 256) != 0) {
                            i2++;
                            length = i3;
                            iArr = iArr2;
                            pm2 = pm;
                            appOpsCount2 = appOpsCount;
                        } else {
                            showApp = false;
                            break;
                        }
                    } else if ((permissionFlags & 512) != 0) {
                        i2++;
                        length = i3;
                        iArr = iArr2;
                        pm2 = pm;
                        appOpsCount2 = appOpsCount;
                    } else {
                        showApp = false;
                        break;
                    }
                }
                if (showApp && (access = getAccessFromOps(now, ops)) != null) {
                    accesses.add(access);
                }
            }
            i++;
            aoManager2 = aoManager;
            appOps2 = appOps;
            pm2 = pm;
            appOpsCount2 = appOpsCount;
        }
        return accesses;
    }

    public List<Access> getAppListSorted() {
        List<Access> accesses = getAppList();
        Collections.sort(accesses, Collections.reverseOrder(new Comparator<Access>() { // from class: com.android.settingslib.location.RecentLocationAccesses.1
            @Override // java.util.Comparator
            public int compare(Access access1, Access access2) {
                return Long.compare(access1.accessFinishTime, access2.accessFinishTime);
            }
        }));
        return accesses;
    }

    private Access getAccessFromOps(long now, AppOpsManager.PackageOps ops) {
        int userId;
        CharSequence badgedAppLabel;
        String packageName = ops.getPackageName();
        List<AppOpsManager.OpEntry> entries = ops.getOps();
        long recentLocationCutoffTime = now - RECENT_TIME_INTERVAL_MILLIS;
        long locationAccessFinishTime = 0;
        for (AppOpsManager.OpEntry entry : entries) {
            locationAccessFinishTime = entry.getLastAccessTime(13);
        }
        if (locationAccessFinishTime < recentLocationCutoffTime) {
            return null;
        }
        int uid = ops.getUid();
        int userId2 = UserHandle.getUserId(uid);
        try {
            ApplicationInfo appInfo = this.mPackageManager.getApplicationInfoAsUser(packageName, 128, userId2);
            if (appInfo == null) {
                try {
                    Log.w(TAG, "Null application info retrieved for package " + packageName + ", userId " + userId2);
                    return null;
                } catch (PackageManager.NameNotFoundException e) {
                    userId = userId2;
                }
            } else {
                UserHandle userHandle = new UserHandle(userId2);
                Drawable icon = this.mDrawableFactory.getBadgedIcon(appInfo, userId2);
                CharSequence appLabel = this.mPackageManager.getApplicationLabel(appInfo);
                CharSequence badgedAppLabel2 = this.mPackageManager.getUserBadgedLabel(appLabel, userHandle);
                if (!appLabel.toString().contentEquals(badgedAppLabel2)) {
                    badgedAppLabel = badgedAppLabel2;
                } else {
                    badgedAppLabel = null;
                }
                userId = userId2;
                try {
                    Access access = new Access(packageName, userHandle, icon, appLabel, badgedAppLabel, locationAccessFinishTime);
                    return access;
                } catch (PackageManager.NameNotFoundException e2) {
                }
            }
        } catch (PackageManager.NameNotFoundException e3) {
            userId = userId2;
        }
        Log.w(TAG, "package name not found for " + packageName + ", userId " + userId);
        return null;
    }

    /* loaded from: classes3.dex */
    public static class Access {
        public final long accessFinishTime;
        public final CharSequence contentDescription;
        public final Drawable icon;
        public final CharSequence label;
        public final String packageName;
        public final UserHandle userHandle;

        public Access(String packageName, UserHandle userHandle, Drawable icon, CharSequence label, CharSequence contentDescription, long accessFinishTime) {
            this.packageName = packageName;
            this.userHandle = userHandle;
            this.icon = icon;
            this.label = label;
            this.contentDescription = contentDescription;
            this.accessFinishTime = accessFinishTime;
        }
    }
}
