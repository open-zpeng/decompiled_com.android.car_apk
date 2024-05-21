package com.android.car.utils;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.os.Process;
import android.text.TextUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
/* loaded from: classes3.dex */
public final class Utils {
    @SuppressLint({"StaticFieldLeak"})
    private static Application sApplication;

    private Utils() {
        throw new UnsupportedOperationException("u can't instantiate me...");
    }

    public static void init(Context context) {
        if (context == null) {
            init(getApplicationByReflect());
        } else {
            init((Application) context.getApplicationContext());
        }
    }

    public static void init(Application app) {
        if (sApplication == null) {
            if (app == null) {
                sApplication = getApplicationByReflect();
            } else {
                sApplication = app;
            }
        } else if (app != null && app.getClass() != sApplication.getClass()) {
            sApplication = app;
        }
    }

    public static Application getApp() {
        Application application = sApplication;
        if (application != null) {
            return application;
        }
        Application app = getApplicationByReflect();
        init(app);
        return app;
    }

    static String getCurrentProcessName() {
        String name = getCurrentProcessNameByFile();
        if (TextUtils.isEmpty(name)) {
            String name2 = getCurrentProcessNameByAms();
            return !TextUtils.isEmpty(name2) ? name2 : getCurrentProcessNameByReflect();
        }
        return name;
    }

    private static String getCurrentProcessNameByFile() {
        try {
            File file = new File("/proc/" + Process.myPid() + "/cmdline");
            BufferedReader mBufferedReader = new BufferedReader(new FileReader(file));
            String processName = mBufferedReader.readLine().trim();
            mBufferedReader.close();
            return processName;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    @TargetApi(3)
    private static String getCurrentProcessNameByAms() {
        List<ActivityManager.RunningAppProcessInfo> info;
        ActivityManager am = (ActivityManager) getApp().getSystemService("activity");
        if (am == null || (info = am.getRunningAppProcesses()) == null || info.size() == 0) {
            return "";
        }
        int pid = Process.myPid();
        for (ActivityManager.RunningAppProcessInfo aInfo : info) {
            if (aInfo.pid == pid && aInfo.processName != null) {
                return aInfo.processName;
            }
        }
        return "";
    }

    private static String getCurrentProcessNameByReflect() {
        try {
            Application app = getApp();
            Field loadedApkField = app.getClass().getField("mLoadedApk");
            loadedApkField.setAccessible(true);
            Object loadedApk = loadedApkField.get(app);
            Field activityThreadField = loadedApk.getClass().getDeclaredField("mActivityThread");
            activityThreadField.setAccessible(true);
            Object activityThread = activityThreadField.get(loadedApk);
            Method getProcessName = activityThread.getClass().getDeclaredMethod("getProcessName", new Class[0]);
            String processName = (String) getProcessName.invoke(activityThread, new Object[0]);
            return processName;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private static Application getApplicationByReflect() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object thread = activityThread.getMethod("currentActivityThread", new Class[0]).invoke(null, new Object[0]);
            Object app = activityThread.getMethod("getApplication", new Class[0]).invoke(thread, new Object[0]);
            if (app == null) {
                throw new NullPointerException("u should init first");
            }
            return (Application) app;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new NullPointerException("u should init first");
        } catch (IllegalAccessException e2) {
            e2.printStackTrace();
            throw new NullPointerException("u should init first");
        } catch (NoSuchMethodException e3) {
            e3.printStackTrace();
            throw new NullPointerException("u should init first");
        } catch (InvocationTargetException e4) {
            e4.printStackTrace();
            throw new NullPointerException("u should init first");
        }
    }
}
