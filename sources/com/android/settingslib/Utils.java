package com.android.settingslib;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.UserInfo;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.telephony.ServiceState;
import androidx.core.app.NotificationCompat;
import androidx.mediarouter.media.SystemMediaRouteProvider;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.UserIcons;
import com.android.settingslib.drawable.UserIconDrawable;
import java.text.NumberFormat;
/* loaded from: classes3.dex */
public class Utils {
    private static final String CURRENT_MODE_KEY = "CURRENT_MODE";
    private static final String NEW_MODE_KEY = "NEW_MODE";
    @VisibleForTesting
    static final String STORAGE_MANAGER_ENABLED_PROPERTY = "ro.storage_manager.enabled";
    static final int[] WIFI_PIE = {17302852, 17302853, 17302854, 17302855, 17302856};
    private static String sPermissionControllerPackageName;
    private static String sServicesSystemSharedLibPackageName;
    private static String sSharedSystemSharedLibPackageName;
    private static Signature[] sSystemSignature;

    public static void updateLocationEnabled(Context context, boolean enabled, int userId, int source) {
        int oldMode;
        LocationManager locationManager = (LocationManager) context.getSystemService(LocationManager.class);
        Settings.Secure.putIntForUser(context.getContentResolver(), "location_changer", source, userId);
        Intent intent = new Intent("com.android.settings.location.MODE_CHANGING");
        if (locationManager.isLocationEnabled()) {
            oldMode = 3;
        } else {
            oldMode = 0;
        }
        int newMode = enabled ? 3 : 0;
        intent.putExtra(CURRENT_MODE_KEY, oldMode);
        intent.putExtra(NEW_MODE_KEY, newMode);
        context.sendBroadcastAsUser(intent, UserHandle.of(userId), "android.permission.WRITE_SECURE_SETTINGS");
        locationManager.setLocationEnabledForUser(enabled, UserHandle.of(userId));
    }

    public static int getTetheringLabel(ConnectivityManager cm) {
        String[] usbRegexs = cm.getTetherableUsbRegexs();
        String[] wifiRegexs = cm.getTetherableWifiRegexs();
        String[] bluetoothRegexs = cm.getTetherableBluetoothRegexs();
        boolean usbAvailable = usbRegexs.length != 0;
        boolean wifiAvailable = wifiRegexs.length != 0;
        boolean bluetoothAvailable = bluetoothRegexs.length != 0;
        if (wifiAvailable && usbAvailable && bluetoothAvailable) {
            return R.string.tether_settings_title_all;
        }
        if (wifiAvailable && usbAvailable) {
            return R.string.tether_settings_title_all;
        }
        if (wifiAvailable && bluetoothAvailable) {
            return R.string.tether_settings_title_all;
        }
        if (wifiAvailable) {
            return R.string.tether_settings_title_wifi;
        }
        if (usbAvailable && bluetoothAvailable) {
            return R.string.tether_settings_title_usb_bluetooth;
        }
        if (usbAvailable) {
            return R.string.tether_settings_title_usb;
        }
        return R.string.tether_settings_title_bluetooth;
    }

    public static String getUserLabel(Context context, UserInfo info) {
        String name = info != null ? info.name : null;
        if (info.isManagedProfile()) {
            return context.getString(R.string.managed_user_title);
        }
        if (info.isGuest()) {
            name = context.getString(R.string.user_guest);
        }
        if (name == null) {
            name = Integer.toString(info.id);
        }
        return context.getResources().getString(R.string.running_process_item_user_label, name);
    }

    public static Drawable getUserIcon(Context context, UserManager um, UserInfo user) {
        Bitmap icon;
        int iconSize = UserIconDrawable.getSizeForList(context);
        if (user.isManagedProfile()) {
            Drawable drawable = UserIconDrawable.getManagedUserDrawable(context);
            drawable.setBounds(0, 0, iconSize, iconSize);
            return drawable;
        } else if (user.iconPath != null && (icon = um.getUserIcon(user.id)) != null) {
            return new UserIconDrawable(iconSize).setIcon(icon).bake();
        } else {
            return new UserIconDrawable(iconSize).setIconDrawable(UserIcons.getDefaultUserIcon(context.getResources(), user.id, false)).bake();
        }
    }

    public static String formatPercentage(double percentage, boolean round) {
        int localPercentage = round ? Math.round((float) percentage) : (int) percentage;
        return formatPercentage(localPercentage);
    }

    public static String formatPercentage(long amount, long total) {
        return formatPercentage(amount / total);
    }

    public static String formatPercentage(int percentage) {
        return formatPercentage(percentage / 100.0d);
    }

    public static String formatPercentage(double percentage) {
        return NumberFormat.getPercentInstance().format(percentage);
    }

    public static int getBatteryLevel(Intent batteryChangedIntent) {
        int level = batteryChangedIntent.getIntExtra("level", 0);
        int scale = batteryChangedIntent.getIntExtra("scale", 100);
        return (level * 100) / scale;
    }

    public static String getBatteryStatus(Resources res, Intent batteryChangedIntent) {
        int status = batteryChangedIntent.getIntExtra(NotificationCompat.CATEGORY_STATUS, 1);
        if (status == 2) {
            String statusString = res.getString(R.string.battery_info_status_charging);
            return statusString;
        } else if (status == 3) {
            String statusString2 = res.getString(R.string.battery_info_status_discharging);
            return statusString2;
        } else if (status == 4) {
            String statusString3 = res.getString(R.string.battery_info_status_not_charging);
            return statusString3;
        } else if (status == 5) {
            String statusString4 = res.getString(R.string.battery_info_status_full);
            return statusString4;
        } else {
            String statusString5 = res.getString(R.string.battery_info_status_unknown);
            return statusString5;
        }
    }

    public static ColorStateList getColorAccent(Context context) {
        return getColorAttr(context, 16843829);
    }

    public static ColorStateList getColorError(Context context) {
        return getColorAttr(context, 16844099);
    }

    public static int getColorAccentDefaultColor(Context context) {
        return getColorAttrDefaultColor(context, 16843829);
    }

    public static int getColorErrorDefaultColor(Context context) {
        return getColorAttrDefaultColor(context, 16844099);
    }

    public static int getColorStateListDefaultColor(Context context, int resId) {
        ColorStateList list = context.getResources().getColorStateList(resId, context.getTheme());
        return list.getDefaultColor();
    }

    public static int getDisabled(Context context, int inputColor) {
        return applyAlphaAttr(context, 16842803, inputColor);
    }

    public static int applyAlphaAttr(Context context, int attr, int inputColor) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        float alpha = ta.getFloat(0, 0.0f);
        ta.recycle();
        return applyAlpha(alpha, inputColor);
    }

    public static int applyAlpha(float alpha, int inputColor) {
        return Color.argb((int) (alpha * Color.alpha(inputColor)), Color.red(inputColor), Color.green(inputColor), Color.blue(inputColor));
    }

    public static int getColorAttrDefaultColor(Context context, int attr) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        int colorAccent = ta.getColor(0, 0);
        ta.recycle();
        return colorAccent;
    }

    public static ColorStateList getColorAttr(Context context, int attr) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        try {
            ColorStateList stateList = ta.getColorStateList(0);
            return stateList;
        } finally {
            ta.recycle();
        }
    }

    public static int getThemeAttr(Context context, int attr) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        int theme = ta.getResourceId(0, 0);
        ta.recycle();
        return theme;
    }

    public static Drawable getDrawable(Context context, int attr) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        Drawable drawable = ta.getDrawable(0);
        ta.recycle();
        return drawable;
    }

    public static boolean isSystemPackage(Resources resources, PackageManager pm, PackageInfo pkg) {
        if (sSystemSignature == null) {
            sSystemSignature = new Signature[]{getSystemSignature(pm)};
        }
        if (sPermissionControllerPackageName == null) {
            sPermissionControllerPackageName = pm.getPermissionControllerPackageName();
        }
        if (sServicesSystemSharedLibPackageName == null) {
            sServicesSystemSharedLibPackageName = pm.getServicesSystemSharedLibraryPackageName();
        }
        if (sSharedSystemSharedLibPackageName == null) {
            sSharedSystemSharedLibPackageName = pm.getSharedSystemSharedLibraryPackageName();
        }
        Signature[] signatureArr = sSystemSignature;
        return (signatureArr[0] != null && signatureArr[0].equals(getFirstSignature(pkg))) || pkg.packageName.equals(sPermissionControllerPackageName) || pkg.packageName.equals(sServicesSystemSharedLibPackageName) || pkg.packageName.equals(sSharedSystemSharedLibPackageName) || pkg.packageName.equals("com.android.printspooler") || isDeviceProvisioningPackage(resources, pkg.packageName);
    }

    private static Signature getFirstSignature(PackageInfo pkg) {
        if (pkg != null && pkg.signatures != null && pkg.signatures.length > 0) {
            return pkg.signatures[0];
        }
        return null;
    }

    private static Signature getSystemSignature(PackageManager pm) {
        try {
            PackageInfo sys = pm.getPackageInfo(SystemMediaRouteProvider.PACKAGE_NAME, 64);
            return getFirstSignature(sys);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    public static boolean isDeviceProvisioningPackage(Resources resources, String packageName) {
        String deviceProvisioningPackage = resources.getString(17039720);
        return deviceProvisioningPackage != null && deviceProvisioningPackage.equals(packageName);
    }

    public static int getWifiIconResource(int level) {
        if (level >= 0) {
            int[] iArr = WIFI_PIE;
            if (level < iArr.length) {
                return iArr[level];
            }
        }
        throw new IllegalArgumentException("No Wifi icon found for level: " + level);
    }

    public static int getDefaultStorageManagerDaysToRetain(Resources resources) {
        try {
            int defaultDays = resources.getInteger(17694897);
            return defaultDays;
        } catch (Resources.NotFoundException e) {
            return 90;
        }
    }

    public static boolean isWifiOnly(Context context) {
        return !((ConnectivityManager) context.getSystemService(ConnectivityManager.class)).isNetworkSupported(0);
    }

    public static boolean isStorageManagerEnabled(Context context) {
        boolean isDefaultOn;
        int i;
        try {
            isDefaultOn = SystemProperties.getBoolean(STORAGE_MANAGER_ENABLED_PROPERTY, false);
        } catch (Resources.NotFoundException e) {
            isDefaultOn = false;
        }
        ContentResolver contentResolver = context.getContentResolver();
        if (!isDefaultOn) {
            i = 0;
        } else {
            i = 1;
        }
        if (Settings.Secure.getInt(contentResolver, "automatic_storage_manager_enabled", i) == 0) {
            return false;
        }
        return true;
    }

    public static boolean isAudioModeOngoingCall(Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(AudioManager.class);
        int audioMode = audioManager.getMode();
        return audioMode == 1 || audioMode == 2 || audioMode == 3;
    }

    public static boolean isInService(ServiceState serviceState) {
        int state;
        return (serviceState == null || (state = getCombinedServiceState(serviceState)) == 3 || state == 1 || state == 2) ? false : true;
    }

    public static int getCombinedServiceState(ServiceState serviceState) {
        if (serviceState == null) {
            return 1;
        }
        int state = serviceState.getState();
        int dataState = serviceState.getDataRegState();
        if ((state == 1 || state == 2) && dataState == 0 && serviceState.getDataNetworkType() != 18) {
            return 0;
        }
        return state;
    }
}
