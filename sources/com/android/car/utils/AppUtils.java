package com.android.car.utils;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.util.Log;
import com.android.car.ProcessUtils;
import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
/* loaded from: classes3.dex */
public final class AppUtils {
    private static final char[] HEX_DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    private AppUtils() {
        throw new UnsupportedOperationException("u can't instantiate me...");
    }

    public static boolean isAppDebug() {
        return isAppDebug(XpUtils.getApp().getPackageName());
    }

    public static boolean isAppDebug(String packageName) {
        if (isSpace(packageName)) {
            return false;
        }
        try {
            PackageManager pm = XpUtils.getApp().getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            if (ai != null) {
                return (ai.flags & 2) != 0;
            }
            return false;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isAppSystem() {
        return isAppSystem(XpUtils.getApp().getPackageName());
    }

    public static boolean isAppSystem(String packageName) {
        if (isSpace(packageName)) {
            return false;
        }
        try {
            PackageManager pm = XpUtils.getApp().getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            if (ai != null) {
                return (ai.flags & 1) != 0;
            }
            return false;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isAppForeground(String packageName) {
        return !isSpace(packageName) && packageName.equals(getForegroundProcessName());
    }

    @TargetApi(5)
    public static boolean isAppRunning(String pkgName) {
        PackageManager packageManager = XpUtils.getApp().getPackageManager();
        try {
            ApplicationInfo ai = packageManager.getApplicationInfo(pkgName, 0);
            if (ai == null) {
                return false;
            }
            int uid = ai.uid;
            ActivityManager am = (ActivityManager) XpUtils.getApp().getSystemService("activity");
            if (am != null) {
                List<ActivityManager.RunningTaskInfo> taskInfo = am.getRunningTasks(Integer.MAX_VALUE);
                if (taskInfo != null && taskInfo.size() > 0) {
                    for (ActivityManager.RunningTaskInfo aInfo : taskInfo) {
                        if (pkgName.equals(aInfo.baseActivity.getPackageName())) {
                            return true;
                        }
                    }
                }
                List<ActivityManager.RunningServiceInfo> serviceInfo = am.getRunningServices(Integer.MAX_VALUE);
                if (serviceInfo != null && serviceInfo.size() > 0) {
                    for (ActivityManager.RunningServiceInfo aInfo2 : serviceInfo) {
                        if (uid == aInfo2.uid) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void launchApp(String packageName) {
        if (isSpace(packageName)) {
            return;
        }
        Intent launchAppIntent = getLaunchAppIntent(packageName, true);
        if (launchAppIntent == null) {
            Log.e("AppUtils", "Didn't exist launcher activity.");
        } else {
            XpUtils.getApp().startActivity(launchAppIntent);
        }
    }

    public static void launchApp(Activity activity, String packageName, int requestCode) {
        if (isSpace(packageName)) {
            return;
        }
        Intent launchAppIntent = getLaunchAppIntent(packageName);
        if (launchAppIntent == null) {
            Log.e("AppUtils", "Didn't exist launcher activity.");
        } else {
            activity.startActivityForResult(launchAppIntent, requestCode);
        }
    }

    public static void relaunchApp() {
        relaunchApp(false);
    }

    public static void relaunchApp(boolean isKillProcess) {
        Intent intent = getLaunchAppIntent(XpUtils.getApp().getPackageName(), true);
        if (intent == null) {
            Log.e("AppUtils", "Didn't exist launcher activity.");
            return;
        }
        intent.addFlags(335577088);
        XpUtils.getApp().startActivity(intent);
        if (isKillProcess) {
            Process.killProcess(Process.myPid());
            System.exit(0);
        }
    }

    public static void launchAppDetailsSettings() {
        launchAppDetailsSettings(XpUtils.getApp().getPackageName());
    }

    public static void launchAppDetailsSettings(String packageName) {
        if (isSpace(packageName)) {
            return;
        }
        Intent intent = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS");
        intent.setData(Uri.parse("package:" + packageName));
        XpUtils.getApp().startActivity(intent.addFlags(268435456));
    }

    public static Drawable getAppIcon() {
        return getAppIcon(XpUtils.getApp().getPackageName());
    }

    public static Drawable getAppIcon(String packageName) {
        if (isSpace(packageName)) {
            return null;
        }
        try {
            PackageManager pm = XpUtils.getApp().getPackageManager();
            PackageInfo pi = pm.getPackageInfo(packageName, 0);
            return pi != null ? pi.applicationInfo.loadIcon(pm) : null;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static int getAppIconId() {
        return getAppIconId(XpUtils.getApp().getPackageName());
    }

    public static int getAppIconId(String packageName) {
        if (isSpace(packageName)) {
            return 0;
        }
        try {
            PackageManager pm = XpUtils.getApp().getPackageManager();
            PackageInfo pi = pm.getPackageInfo(packageName, 0);
            return pi != null ? pi.applicationInfo.icon : 0;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public static String getAppPackageName() {
        return XpUtils.getApp().getPackageName();
    }

    public static String getAppName() {
        return getAppName(XpUtils.getApp().getPackageName());
    }

    public static String getAppName(String packageName) {
        if (isSpace(packageName)) {
            return "";
        }
        try {
            PackageManager pm = XpUtils.getApp().getPackageManager();
            PackageInfo pi = pm.getPackageInfo(packageName, 0);
            if (pi == null) {
                return null;
            }
            return pi.applicationInfo.loadLabel(pm).toString();
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return "";
        }
    }

    public static String getAppPath() {
        return getAppPath(XpUtils.getApp().getPackageName());
    }

    public static String getAppPath(String packageName) {
        if (isSpace(packageName)) {
            return "";
        }
        try {
            PackageManager pm = XpUtils.getApp().getPackageManager();
            PackageInfo pi = pm.getPackageInfo(packageName, 0);
            if (pi == null) {
                return null;
            }
            return pi.applicationInfo.sourceDir;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return "";
        }
    }

    public static String getAppVersionName() {
        return getAppVersionName(XpUtils.getApp().getPackageName());
    }

    public static String getAppVersionName(String packageName) {
        if (isSpace(packageName)) {
            return "";
        }
        try {
            PackageManager pm = XpUtils.getApp().getPackageManager();
            PackageInfo pi = pm.getPackageInfo(packageName, 0);
            if (pi == null) {
                return null;
            }
            return pi.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return "";
        }
    }

    public static int getAppVersionCode() {
        return getAppVersionCode(XpUtils.getApp().getPackageName());
    }

    public static int getAppVersionCode(String packageName) {
        if (isSpace(packageName)) {
            return -1;
        }
        try {
            PackageManager pm = XpUtils.getApp().getPackageManager();
            PackageInfo pi = pm.getPackageInfo(packageName, 0);
            if (pi == null) {
                return -1;
            }
            return pi.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public static Signature[] getAppSignature() {
        return getAppSignature(XpUtils.getApp().getPackageName());
    }

    public static Signature[] getAppSignature(String packageName) {
        if (isSpace(packageName)) {
            return null;
        }
        try {
            PackageManager pm = XpUtils.getApp().getPackageManager();
            PackageInfo pi = pm.getPackageInfo(packageName, 64);
            if (pi == null) {
                return null;
            }
            return pi.signatures;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String getAppSignatureSHA1() {
        return getAppSignatureSHA1(XpUtils.getApp().getPackageName());
    }

    public static String getAppSignatureSHA1(String packageName) {
        return getAppSignatureHash(packageName, "SHA1");
    }

    public static String getAppSignatureSHA256() {
        return getAppSignatureSHA256(XpUtils.getApp().getPackageName());
    }

    public static String getAppSignatureSHA256(String packageName) {
        return getAppSignatureHash(packageName, "SHA256");
    }

    public static String getAppSignatureMD5() {
        return getAppSignatureMD5(XpUtils.getApp().getPackageName());
    }

    public static String getAppSignatureMD5(String packageName) {
        return getAppSignatureHash(packageName, "MD5");
    }

    public static int getAppUid() {
        return getAppUid(XpUtils.getApp().getPackageName());
    }

    public static int getAppUid(String pkgName) {
        try {
            ApplicationInfo ai = XpUtils.getApp().getPackageManager().getApplicationInfo(pkgName, 0);
            if (ai != null) {
                return ai.uid;
            }
            return -1;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    private static String getAppSignatureHash(String packageName, String algorithm) {
        Signature[] signature;
        return (isSpace(packageName) || (signature = getAppSignature(packageName)) == null || signature.length <= 0) ? "" : bytes2HexString(hashTemplate(signature[0].toByteArray(), algorithm)).replaceAll("(?<=[0-9A-F]{2})[0-9A-F]{2}", ":$0");
    }

    public static AppInfo getAppInfo() {
        return getAppInfo(XpUtils.getApp().getPackageName());
    }

    public static AppInfo getAppInfo(String packageName) {
        try {
            PackageManager pm = XpUtils.getApp().getPackageManager();
            if (pm == null) {
                return null;
            }
            return getBean(pm, pm.getPackageInfo(packageName, 0));
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static List<AppInfo> getAppsInfo() {
        List<AppInfo> list = new ArrayList<>();
        PackageManager pm = XpUtils.getApp().getPackageManager();
        if (pm == null) {
            return list;
        }
        List<PackageInfo> installedPackages = pm.getInstalledPackages(0);
        for (PackageInfo pi : installedPackages) {
            AppInfo ai = getBean(pm, pi);
            if (ai != null) {
                list.add(ai);
            }
        }
        return list;
    }

    public static AppInfo getApkInfo(File apkFile) {
        if (apkFile == null || !apkFile.isFile() || !apkFile.exists()) {
            return null;
        }
        return getApkInfo(apkFile.getAbsolutePath());
    }

    public static AppInfo getApkInfo(String apkFilePath) {
        PackageManager pm;
        PackageInfo pi;
        if (isSpace(apkFilePath) || (pm = XpUtils.getApp().getPackageManager()) == null || (pi = pm.getPackageArchiveInfo(apkFilePath, 0)) == null) {
            return null;
        }
        ApplicationInfo appInfo = pi.applicationInfo;
        appInfo.sourceDir = apkFilePath;
        appInfo.publicSourceDir = apkFilePath;
        return getBean(pm, pi);
    }

    private static AppInfo getBean(PackageManager pm, PackageInfo pi) {
        if (pi == null) {
            return null;
        }
        ApplicationInfo ai = pi.applicationInfo;
        String packageName = pi.packageName;
        String name = ai.loadLabel(pm).toString();
        Drawable icon = ai.loadIcon(pm);
        String packagePath = ai.sourceDir;
        String versionName = pi.versionName;
        int versionCode = pi.versionCode;
        boolean isSystem = (ai.flags & 1) != 0;
        return new AppInfo(packageName, name, icon, packagePath, versionName, versionCode, isSystem);
    }

    /* loaded from: classes3.dex */
    public static class AppInfo {
        private Drawable icon;
        private boolean isSystem;
        private String name;
        private String packageName;
        private String packagePath;
        private int versionCode;
        private String versionName;

        public Drawable getIcon() {
            return this.icon;
        }

        public void setIcon(Drawable icon) {
            this.icon = icon;
        }

        public boolean isSystem() {
            return this.isSystem;
        }

        public void setSystem(boolean isSystem) {
            this.isSystem = isSystem;
        }

        public String getPackageName() {
            return this.packageName;
        }

        public void setPackageName(String packageName) {
            this.packageName = packageName;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPackagePath() {
            return this.packagePath;
        }

        public void setPackagePath(String packagePath) {
            this.packagePath = packagePath;
        }

        public int getVersionCode() {
            return this.versionCode;
        }

        public void setVersionCode(int versionCode) {
            this.versionCode = versionCode;
        }

        public String getVersionName() {
            return this.versionName;
        }

        public void setVersionName(String versionName) {
            this.versionName = versionName;
        }

        public AppInfo(String packageName, String name, Drawable icon, String packagePath, String versionName, int versionCode, boolean isSystem) {
            setName(name);
            setIcon(icon);
            setPackageName(packageName);
            setPackagePath(packagePath);
            setVersionName(versionName);
            setVersionCode(versionCode);
            setSystem(isSystem);
        }

        public String toString() {
            return "{\n    pkg name: " + getPackageName() + "\n    app icon: " + getIcon() + "\n    app name: " + getName() + "\n    app path: " + getPackagePath() + "\n    app v name: " + getVersionName() + "\n    app v code: " + getVersionCode() + "\n    is system: " + isSystem() + "\n}";
        }
    }

    private static boolean isFileExists(File file) {
        return file != null && file.exists();
    }

    private static File getFileByPath(String filePath) {
        if (isSpace(filePath)) {
            return null;
        }
        return new File(filePath);
    }

    private static boolean isSpace(String s) {
        if (s == null) {
            return true;
        }
        int len = s.length();
        for (int i = 0; i < len; i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static byte[] hashTemplate(byte[] data, String algorithm) {
        if (data == null || data.length <= 0) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            md.update(data);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String bytes2HexString(byte[] bytes) {
        int len;
        if (bytes == null || (len = bytes.length) <= 0) {
            return "";
        }
        char[] ret = new char[len << 1];
        int j = 0;
        for (int i = 0; i < len; i++) {
            int j2 = j + 1;
            char[] cArr = HEX_DIGITS;
            ret[j] = cArr[(bytes[i] >> 4) & 15];
            j = j2 + 1;
            ret[j2] = cArr[bytes[i] & 15];
        }
        return new String(ret);
    }

    private static Intent getUninstallAppIntent(String packageName) {
        return getUninstallAppIntent(packageName, false);
    }

    private static Intent getUninstallAppIntent(String packageName, boolean isNewTask) {
        Intent intent = new Intent("android.intent.action.DELETE");
        intent.setData(Uri.parse("package:" + packageName));
        return isNewTask ? intent.addFlags(268435456) : intent;
    }

    private static Intent getLaunchAppIntent(String packageName) {
        return getLaunchAppIntent(packageName, false);
    }

    @TargetApi(9)
    private static Intent getLaunchAppIntent(String packageName, boolean isNewTask) {
        String launcherActivity = getLauncherActivity(packageName);
        if (!launcherActivity.isEmpty()) {
            Intent intent = new Intent("android.intent.action.MAIN");
            intent.addCategory("android.intent.category.LAUNCHER");
            ComponentName cn = new ComponentName(packageName, launcherActivity);
            intent.setComponent(cn);
            return isNewTask ? intent.addFlags(268435456) : intent;
        }
        return null;
    }

    private static String getLauncherActivity(String pkg) {
        Intent intent = new Intent("android.intent.action.MAIN", (Uri) null);
        intent.addCategory("android.intent.category.LAUNCHER");
        intent.setPackage(pkg);
        PackageManager pm = XpUtils.getApp().getPackageManager();
        List<ResolveInfo> info = pm.queryIntentActivities(intent, 0);
        int size = info.size();
        if (size == 0) {
            return "";
        }
        for (int i = 0; i < size; i++) {
            ResolveInfo ri = info.get(i);
            if (ri.activityInfo.processName.equals(pkg)) {
                return ri.activityInfo.name;
            }
        }
        return info.get(0).activityInfo.name;
    }

    @TargetApi(3)
    private static String getForegroundProcessName() {
        ActivityManager am = (ActivityManager) XpUtils.getApp().getSystemService("activity");
        List<ActivityManager.RunningAppProcessInfo> pInfo = am.getRunningAppProcesses();
        if (pInfo != null && pInfo.size() > 0) {
            for (ActivityManager.RunningAppProcessInfo aInfo : pInfo) {
                if (aInfo.importance == 100) {
                    return aInfo.processName;
                }
            }
        }
        if (Build.VERSION.SDK_INT > 21) {
            PackageManager pm = XpUtils.getApp().getPackageManager();
            Intent intent = new Intent("android.settings.USAGE_ACCESS_SETTINGS");
            List<ResolveInfo> list = pm.queryIntentActivities(intent, 65536);
            Log.i(ProcessUtils.TAG, list.toString());
            if (list.size() <= 0) {
                Log.i(ProcessUtils.TAG, "getForegroundProcessName: noun of access to usage information.");
                return "";
            }
            try {
                ApplicationInfo info = pm.getApplicationInfo(XpUtils.getApp().getPackageName(), 0);
                AppOpsManager aom = (AppOpsManager) XpUtils.getApp().getSystemService("appops");
                if (aom.checkOpNoThrow("android:get_usage_stats", info.uid, info.packageName) != 0) {
                    intent.addFlags(268435456);
                    XpUtils.getApp().startActivity(intent);
                }
                if (aom.checkOpNoThrow("android:get_usage_stats", info.uid, info.packageName) != 0) {
                    Log.i(ProcessUtils.TAG, "getForegroundProcessName: refuse to device usage stats.");
                    return "";
                }
                UsageStatsManager usageStatsManager = (UsageStatsManager) XpUtils.getApp().getSystemService("usagestats");
                List<UsageStats> usageStatsList = null;
                if (usageStatsManager != null) {
                    long endTime = System.currentTimeMillis();
                    long beginTime = endTime - 604800000;
                    usageStatsList = usageStatsManager.queryUsageStats(4, beginTime, endTime);
                }
                if (usageStatsList != null && !usageStatsList.isEmpty()) {
                    UsageStats recentStats = null;
                    for (UsageStats usageStats : usageStatsList) {
                        if (recentStats == null || usageStats.getLastTimeUsed() > recentStats.getLastTimeUsed()) {
                            recentStats = usageStats;
                        }
                    }
                    if (recentStats == null) {
                        return null;
                    }
                    return recentStats.getPackageName();
                }
                return null;
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
        return "";
    }
}
