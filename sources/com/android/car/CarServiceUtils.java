package com.android.car;

import android.car.XpDebugLog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.util.Slog;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
/* loaded from: classes3.dex */
public final class CarServiceUtils {
    private static final String CAR_CONNECT_STATUS_PROPERTY = "sys.car_connect.status";
    private static final String PACKAGE_NOT_FOUND = "Package not found:";

    private CarServiceUtils() {
    }

    public static void assertPackageName(Context context, String packageName) throws IllegalArgumentException, SecurityException {
        if (packageName == null) {
            throw new IllegalArgumentException("Package name null");
        }
        try {
            ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(packageName, 0);
            if (appInfo == null) {
                throw new SecurityException(PACKAGE_NOT_FOUND + packageName);
            }
            int uid = Binder.getCallingUid();
            if (uid != appInfo.uid) {
                throw new SecurityException("Wrong package name:" + packageName + ", The package does not belong to caller's uid:" + uid);
            }
        } catch (PackageManager.NameNotFoundException e) {
            String msg = PACKAGE_NOT_FOUND + packageName;
            Slog.w(CarLog.TAG_SERVICE, msg, e);
            throw new SecurityException(msg, e);
        }
    }

    public static void runOnMain(Runnable action) {
        runOnLooper(Looper.getMainLooper(), action);
    }

    public static void runOnLooper(Looper looper, Runnable action) {
        new Handler(looper).post(action);
    }

    public static void runOnMainSync(Runnable action) {
        runOnLooperSync(Looper.getMainLooper(), action);
    }

    public static void runOnLooperSync(Looper looper, Runnable action) {
        if (Looper.myLooper() == looper) {
            action.run();
            return;
        }
        Handler handler = new Handler(looper);
        SyncRunnable sr = new SyncRunnable(action);
        handler.post(sr);
        sr.waitForComplete();
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static final class SyncRunnable implements Runnable {
        private volatile boolean mComplete = false;
        private final Runnable mTarget;

        public SyncRunnable(Runnable target) {
            this.mTarget = target;
        }

        @Override // java.lang.Runnable
        public void run() {
            this.mTarget.run();
            synchronized (this) {
                this.mComplete = true;
                notifyAll();
            }
        }

        public void waitForComplete() {
            synchronized (this) {
                while (!this.mComplete) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
        }
    }

    public static float[] toFloatArray(List<Float> list) {
        if (list != null) {
            int size = list.size();
            float[] array = new float[size];
            for (int i = 0; i < size; i++) {
                array[i] = list.get(i).floatValue();
            }
            return array;
        }
        return null;
    }

    public static int[] toIntArray(List<Integer> list) {
        if (list != null) {
            int size = list.size();
            int[] array = new int[size];
            for (int i = 0; i < size; i++) {
                array[i] = list.get(i).intValue();
            }
            return array;
        }
        return null;
    }

    public static byte[] toByteArray(List<Byte> list) {
        if (list != null) {
            int size = list.size();
            byte[] array = new byte[size];
            for (int i = 0; i < size; i++) {
                array[i] = list.get(i).byteValue();
            }
            return array;
        }
        return null;
    }

    public static int[] toIntArray(Integer[] input) {
        if (input != null) {
            int len = input.length;
            int[] arr = new int[len];
            for (int i = 0; i < len; i++) {
                arr[i] = input[i].intValue();
            }
            return arr;
        }
        return null;
    }

    public static long[] toLongArray(Long[] input) {
        if (input != null) {
            int len = input.length;
            long[] arr = new long[len];
            for (int i = 0; i < len; i++) {
                arr[i] = input[i].longValue();
            }
            return arr;
        }
        return null;
    }

    public static boolean isCduConnectedToCar() {
        String prop = SystemProperties.get(CAR_CONNECT_STATUS_PROPERTY, "");
        return "connect".equals(prop);
    }

    public static String idsToString(Collection<Integer> ids) {
        if (ids == null) {
            return "null";
        }
        Iterator<Integer> it = ids.iterator();
        if (!it.hasNext()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        while (true) {
            int e = it.next().intValue();
            sb.append(XpDebugLog.getPropertyName(e));
            if (!it.hasNext()) {
                sb.append(']');
                return sb.toString();
            }
            sb.append(',');
            sb.append(' ');
        }
    }
}
