package com.android.car;

import android.car.XpDebugLog;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Slog;
/* loaded from: classes3.dex */
public class CarLog {
    private static final int ENABLE_CALLBACK_LOG = 4;
    private static final int ENABLE_GET_LOG = 2;
    private static final int ENABLE_PERF_LOG = 8;
    private static final int ENABLE_SET_LOG = 1;
    private static final int MAX_TAG_LEN = 23;
    private static final String SYS_CAR_CALLBACK_DEBUG_PROPERTY = "sys.car.callback_debug";
    private static final String SYS_CAR_PERF_LOG_DEBUG_PROPERTY = "sys.car.perf_debug";
    private static final String SYS_CAR_PROP_CALLBACK_DEBUG_PROPERTY = "sys.car.callback_debug_prop";
    private static final String SYS_CAR_SHARED_MEMORY_PERF_LOG_DEBUG_PROPERTY = "persist.sys.car.shm_perf_debug";
    public static final String TAG_AM = "CAR.AM";
    public static final String TAG_APP_FOCUS = "CAR.APP_FOCUS";
    public static final String TAG_AUDIO = "CAR.AUDIO";
    public static final String TAG_CABIN = "CAR.CABIN";
    public static final String TAG_CAMERA = "CAR.CAMERA";
    public static final String TAG_CAN_BUS = "CAR.CAN_BUS";
    public static final String TAG_CLUSTER = "CAR.CLUSTER";
    public static final String TAG_CONDITION = "CAR.CONDITION";
    public static final String TAG_DIAGNOSTIC = "CAR.DIAGNOSTIC";
    public static final String TAG_HAL = "CAR.HAL";
    public static final String TAG_HVAC = "CAR.HVAC";
    public static final String TAG_INFO = "CAR.INFO";
    public static final String TAG_INPUT = "CAR.INPUT";
    public static final String TAG_MEDIA = "CAR.MEDIA";
    public static final String TAG_MONITORING = "CAR.MONITORING";
    public static final String TAG_NAV = "CAR.NAV";
    public static final String TAG_PACKAGE = "CAR.PACKAGE";
    public static final String TAG_POWER = "CAR.POWER";
    public static final String TAG_PROJECTION = "CAR.PROJECTION";
    public static final String TAG_PROPERTY = "CAR.PROPERTY";
    public static final String TAG_SENSOR = "CAR.SENSOR";
    public static final String TAG_SERVICE = "CAR.SERVICE";
    public static final String TAG_STORAGE = "CAR.STORAGE";
    public static final String TAG_SYS = "CAR.SYS";
    public static final String TAG_TEST = "CAR.TEST";
    public static final String TAG_UPDATETIME = "CAR.UPDATETIME";
    public static final String TAG_USER = "CAR.USER";
    public static final String TAG_VENDOR_EXT = "CAR.VENDOR_EXT";
    public static final String TAG_XPDIAG = "CAR.XPDIAG";
    public static final String TAG_XPVEHICLE = "CAR.XPVEHICLE";
    public static volatile int CAR_DEBUG_FLAG = 0;
    private static boolean isCarCallBackDebugLogEnable = false;
    private static boolean isPerfDebugLogEnable = false;
    private static boolean isSharedMemoryPerfDebugLogEnable = false;

    public static boolean isSetLogEnable() {
        return (CAR_DEBUG_FLAG & 1) != 0;
    }

    public static boolean isGetLogEnable() {
        return (CAR_DEBUG_FLAG & 2) != 0;
    }

    public static boolean isCallbackLogEnable(int prop) {
        if ((CAR_DEBUG_FLAG & 4) == 0 && !isCarCallBackDebugLogEnable) {
            boolean z = SystemProperties.getBoolean(SYS_CAR_CALLBACK_DEBUG_PROPERTY, false);
            isCarCallBackDebugLogEnable = z;
            if (!z) {
                return false;
            }
        }
        if (prop <= 0) {
            return true;
        }
        String debugProp = SystemProperties.get(SYS_CAR_PROP_CALLBACK_DEBUG_PROPERTY, "");
        String propName = XpDebugLog.getPropertyName(prop);
        return TextUtils.isEmpty(propName) || TextUtils.isEmpty(debugProp) || debugProp.equals(propName);
    }

    public static boolean isPerfLogEnable() {
        if ((CAR_DEBUG_FLAG & 8) == 0 && !isPerfDebugLogEnable) {
            boolean z = SystemProperties.getBoolean(SYS_CAR_PERF_LOG_DEBUG_PROPERTY, false);
            isPerfDebugLogEnable = z;
            if (!z) {
                return false;
            }
        }
        return true;
    }

    public static boolean isSharedMemoryPerfLogEnable() {
        if (!isSharedMemoryPerfDebugLogEnable) {
            boolean z = SystemProperties.getBoolean(SYS_CAR_SHARED_MEMORY_PERF_LOG_DEBUG_PROPERTY, false);
            isSharedMemoryPerfDebugLogEnable = z;
            if (!z) {
                return false;
            }
        }
        return true;
    }

    public static String concatTag(String tagPrefix, Class clazz) {
        String tag = tagPrefix + "." + clazz.getSimpleName();
        if (tag.length() > 23) {
            return tag.substring(0, 23);
        }
        return tag;
    }

    public static void d(String tag, String msg) {
        if (CAR_DEBUG_FLAG != 0) {
            Slog.d(tag, msg);
        }
    }

    /* loaded from: classes3.dex */
    public static class NoLog {
        static boolean isLoggable(String tag, int level) {
            return false;
        }

        static void i(String tag, String msg) {
        }

        static void d(String tag, String msg) {
        }

        static void e(String tag, String msg, Throwable tr) {
        }

        static void w(String tag, String msg) {
        }

        static void v(String tag, String msg) {
        }
    }
}
