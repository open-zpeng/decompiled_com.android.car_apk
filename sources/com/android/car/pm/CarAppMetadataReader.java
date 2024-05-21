package com.android.car.pm;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Slog;
import com.android.car.CarLog;
import java.util.ArrayList;
import java.util.List;
/* loaded from: classes3.dex */
public class CarAppMetadataReader {
    private static final String DO_METADATA_ATTRIBUTE = "distractionOptimized";

    public static String[] findDistractionOptimizedActivitiesAsUser(Context context, String packageName, int userId) throws PackageManager.NameNotFoundException {
        PackageManager pm = context.getPackageManager();
        PackageInfo pkgInfo = pm.getPackageInfoAsUser(packageName, 787073, userId);
        if (pkgInfo == null) {
            return null;
        }
        ActivityInfo[] activities = pkgInfo.activities;
        if (activities == null) {
            if (Log.isLoggable(CarLog.TAG_PACKAGE, 3)) {
                Slog.d(CarLog.TAG_PACKAGE, "Null Activities for " + packageName);
            }
            return null;
        }
        List<String> optimizedActivityList = new ArrayList<>();
        for (ActivityInfo activity : activities) {
            Bundle mData = activity.metaData;
            if (mData != null && mData.getBoolean(DO_METADATA_ATTRIBUTE, false)) {
                if (Log.isLoggable(CarLog.TAG_PACKAGE, 3)) {
                    Slog.d(CarLog.TAG_PACKAGE, "DO Activity:" + activity.packageName + "/" + activity.name);
                }
                optimizedActivityList.add(activity.name);
            }
        }
        if (optimizedActivityList.isEmpty()) {
            return null;
        }
        return (String[]) optimizedActivityList.toArray(new String[optimizedActivityList.size()]);
    }
}
