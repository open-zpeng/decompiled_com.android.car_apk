package com.android.car;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Debug;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.util.Slog;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
@TargetApi(24)
/* loaded from: classes3.dex */
public class ProcessUtils {
    public static final String TAG = "ProcessUtils";
    public static final String UNKNOWN_PROCESS_NAME = "unknown";
    private static final Map<Pair<Integer, Integer>, String> sPidNames = new ConcurrentHashMap(32);

    private static boolean isFileExists(File file) {
        return file != null && file.exists();
    }

    public static String readFile2List(File file) {
        if (isFileExists(file)) {
            BufferedReader reader = null;
            try {
                try {
                    reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
                    String trim = reader.readLine().trim();
                    try {
                        reader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return trim;
                } catch (IOException e2) {
                    Slog.e(TAG, "read file: " + file + " failed: " + e2.getMessage());
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (IOException e3) {
                            e3.printStackTrace();
                        }
                    }
                    return "";
                }
            } catch (Throwable th) {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e4) {
                        e4.printStackTrace();
                    }
                }
                throw th;
            }
        }
        return "";
    }

    public static String getNameFromProc(int pid) {
        File procDir = new File("/proc", Integer.toString(pid));
        File cmdlineFile = new File(procDir, "cmdline");
        String cmdName = readFile2List(cmdlineFile);
        if (TextUtils.isEmpty(cmdName)) {
            return "unknown";
        }
        return cmdName;
    }

    public static String getProcessName(Context ctx, int pid, int uid, boolean checkProc) {
        String processName = "unknown";
        if (checkProc) {
            processName = getNameFromProc(pid);
        }
        if ("unknown".equals(processName)) {
            String processName2 = getProcessNameFromAm(ctx, pid, uid);
            return processName2;
        }
        return processName;
    }

    public static String getProcessName(final Context ctx, final int pid, final int uid) {
        return sPidNames.computeIfAbsent(new Pair<>(Integer.valueOf(pid), Integer.valueOf(uid)), new Function() { // from class: com.android.car.-$$Lambda$ProcessUtils$bH4EMGAG430O3JSl_4_g8Ew56Hk
            @Override // java.util.function.Function
            public final Object apply(Object obj) {
                String processName;
                Pair pair = (Pair) obj;
                processName = ProcessUtils.getProcessName(ctx, pid, uid, true);
                return processName;
            }
        });
    }

    public static String getPackageOrElfName(Context ctx, int pid, int uid) {
        String processName = getProcessNameFromAm(ctx, pid, uid);
        if ("unknown".equals(processName)) {
            String processName2 = getNameFromProc(pid);
            if (processName2.contains("/")) {
                String[] pro = processName2.split("/");
                return pro[pro.length - 1];
            }
            return processName2;
        }
        return processName;
    }

    public static String getProcessNameFromAm(Context ctx, int pid, int uid) {
        ActivityManager am = (ActivityManager) ctx.getSystemService("activity");
        List<ActivityManager.RunningAppProcessInfo> runningApps = am.getRunningAppProcesses();
        if (runningApps == null || runningApps.size() <= 0) {
            return "unknown";
        }
        for (ActivityManager.RunningAppProcessInfo app : runningApps) {
            if (app.pid == pid && app.uid == uid) {
                String processName = app.processName;
                return processName;
            }
        }
        return "unknown";
    }

    public static boolean dumpJavaTrace(int pid, String fileName, int timeOutSeconds) {
        boolean ret = Debug.dumpJavaBacktraceToFileTimeout(pid, fileName, timeOutSeconds);
        if (!ret && !(ret = Debug.dumpNativeBacktraceToFileTimeout(pid, fileName, timeOutSeconds))) {
            Log.w("ProcessUtil", "dump native trace fail");
        }
        return ret;
    }
}
