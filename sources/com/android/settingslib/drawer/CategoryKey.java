package com.android.settingslib.drawer;

import java.util.HashMap;
import java.util.Map;
/* loaded from: classes3.dex */
public final class CategoryKey {
    public static final String CATEGORY_ABOUT_LEGAL = "com.android.settings.category.ia.about_legal";
    public static final String CATEGORY_ACCOUNT = "com.android.settings.category.ia.accounts";
    public static final String CATEGORY_ACCOUNT_DETAIL = "com.android.settings.category.ia.account_detail";
    public static final String CATEGORY_APPS = "com.android.settings.category.ia.apps";
    public static final String CATEGORY_APPS_DEFAULT = "com.android.settings.category.ia.apps.default";
    public static final String CATEGORY_BATTERY = "com.android.settings.category.ia.battery";
    public static final String CATEGORY_BATTERY_SAVER_SETTINGS = "com.android.settings.category.ia.battery_saver_settings";
    public static final String CATEGORY_CONNECT = "com.android.settings.category.ia.connect";
    public static final String CATEGORY_DEVICE = "com.android.settings.category.ia.device";
    public static final String CATEGORY_DISPLAY = "com.android.settings.category.ia.display";
    public static final String CATEGORY_DO_NOT_DISTURB = "com.android.settings.category.ia.dnd";
    public static final String CATEGORY_ENTERPRISE_PRIVACY = "com.android.settings.category.ia.enterprise_privacy";
    public static final String CATEGORY_GESTURES = "com.android.settings.category.ia.gestures";
    public static final String CATEGORY_HOMEPAGE = "com.android.settings.category.ia.homepage";
    public static final String CATEGORY_MY_DEVICE_INFO = "com.android.settings.category.ia.my_device_info";
    public static final String CATEGORY_NETWORK = "com.android.settings.category.ia.wireless";
    public static final String CATEGORY_NIGHT_DISPLAY = "com.android.settings.category.ia.night_display";
    public static final String CATEGORY_NOTIFICATIONS = "com.android.settings.category.ia.notifications";
    public static final String CATEGORY_PRIVACY = "com.android.settings.category.ia.privacy";
    public static final String CATEGORY_SECURITY = "com.android.settings.category.ia.security";
    public static final String CATEGORY_SECURITY_LOCKSCREEN = "com.android.settings.category.ia.lockscreen";
    public static final String CATEGORY_SOUND = "com.android.settings.category.ia.sound";
    public static final String CATEGORY_STORAGE = "com.android.settings.category.ia.storage";
    public static final String CATEGORY_SYSTEM = "com.android.settings.category.ia.system";
    public static final String CATEGORY_SYSTEM_DEVELOPMENT = "com.android.settings.category.ia.development";
    public static final String CATEGORY_SYSTEM_LANGUAGE = "com.android.settings.category.ia.language";
    public static final Map<String, String> KEY_COMPAT_MAP = new HashMap();

    static {
        KEY_COMPAT_MAP.put("com.android.settings.category.wireless", CATEGORY_NETWORK);
        KEY_COMPAT_MAP.put("com.android.settings.category.device", CATEGORY_SYSTEM);
        KEY_COMPAT_MAP.put("com.android.settings.category.personal", CATEGORY_SYSTEM);
        KEY_COMPAT_MAP.put("com.android.settings.category.system", CATEGORY_SYSTEM);
    }
}
