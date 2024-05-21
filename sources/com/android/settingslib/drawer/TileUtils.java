package com.android.settingslib.drawer;

import android.app.ActivityManager;
import android.content.Context;
import android.content.IContentProvider;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import androidx.annotation.VisibleForTesting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
/* loaded from: classes3.dex */
public class TileUtils {
    private static final boolean DEBUG_TIMING = false;
    private static final String EXTRA_CATEGORY_KEY = "com.android.settings.category";
    private static final String EXTRA_PREFERENCE_ICON_PACKAGE = "com.android.settings.icon_package";
    public static final String EXTRA_SETTINGS_ACTION = "com.android.settings.action.EXTRA_SETTINGS";
    public static final String IA_SETTINGS_ACTION = "com.android.settings.action.IA_SETTINGS";
    private static final String LOG_TAG = "TileUtils";
    private static final String MANUFACTURER_DEFAULT_CATEGORY = "com.android.settings.category.device";
    private static final String MANUFACTURER_SETTINGS = "com.android.settings.MANUFACTURER_APPLICATION_SETTING";
    public static final String META_DATA_KEY_ORDER = "com.android.settings.order";
    public static final String META_DATA_KEY_PROFILE = "com.android.settings.profile";
    public static final String META_DATA_PREFERENCE_ICON = "com.android.settings.icon";
    public static final String META_DATA_PREFERENCE_ICON_BACKGROUND_ARGB = "com.android.settings.bg.argb";
    public static final String META_DATA_PREFERENCE_ICON_BACKGROUND_HINT = "com.android.settings.bg.hint";
    public static final String META_DATA_PREFERENCE_ICON_TINTABLE = "com.android.settings.icon_tintable";
    public static final String META_DATA_PREFERENCE_ICON_URI = "com.android.settings.icon_uri";
    public static final String META_DATA_PREFERENCE_KEYHINT = "com.android.settings.keyhint";
    public static final String META_DATA_PREFERENCE_SUMMARY = "com.android.settings.summary";
    public static final String META_DATA_PREFERENCE_SUMMARY_URI = "com.android.settings.summary_uri";
    public static final String META_DATA_PREFERENCE_TITLE = "com.android.settings.title";
    private static final String OPERATOR_DEFAULT_CATEGORY = "com.android.settings.category.wireless";
    private static final String OPERATOR_SETTINGS = "com.android.settings.OPERATOR_APPLICATION_SETTING";
    public static final String PROFILE_ALL = "all_profiles";
    public static final String PROFILE_PRIMARY = "primary_profile_only";
    private static final String SETTINGS_ACTION = "com.android.settings.action.SETTINGS";
    @VisibleForTesting
    static final String SETTING_PKG = "com.android.settings";

    public static List<DashboardCategory> getCategories(Context context, Map<Pair<String, String>, Tile> cache) {
        System.currentTimeMillis();
        boolean setup = Settings.Global.getInt(context.getContentResolver(), "device_provisioned", 0) != 0;
        ArrayList<Tile> tiles = new ArrayList<>();
        UserManager userManager = (UserManager) context.getSystemService("user");
        for (UserHandle user : userManager.getUserProfiles()) {
            if (user.getIdentifier() == ActivityManager.getCurrentUser()) {
                getTilesForAction(context, user, SETTINGS_ACTION, cache, null, tiles, true);
                getTilesForAction(context, user, OPERATOR_SETTINGS, cache, OPERATOR_DEFAULT_CATEGORY, tiles, false);
                getTilesForAction(context, user, MANUFACTURER_SETTINGS, cache, MANUFACTURER_DEFAULT_CATEGORY, tiles, false);
            }
            if (setup) {
                getTilesForAction(context, user, EXTRA_SETTINGS_ACTION, cache, null, tiles, false);
                getTilesForAction(context, user, IA_SETTINGS_ACTION, cache, null, tiles, false);
            }
        }
        HashMap<String, DashboardCategory> categoryMap = new HashMap<>();
        Iterator<Tile> it = tiles.iterator();
        while (it.hasNext()) {
            Tile tile = it.next();
            String categoryKey = tile.getCategory();
            DashboardCategory category = categoryMap.get(categoryKey);
            if (category == null) {
                category = new DashboardCategory(categoryKey);
                categoryMap.put(categoryKey, category);
            }
            category.addTile(tile);
        }
        ArrayList<DashboardCategory> categories = new ArrayList<>(categoryMap.values());
        Iterator<DashboardCategory> it2 = categories.iterator();
        while (it2.hasNext()) {
            it2.next().sortTiles();
        }
        return categories;
    }

    @VisibleForTesting
    static void getTilesForAction(Context context, UserHandle user, String action, Map<Pair<String, String>, Tile> addedCache, String defaultCategory, List<Tile> outTiles, boolean requireSettings) {
        Intent intent = new Intent(action);
        if (requireSettings) {
            intent.setPackage(SETTING_PKG);
        }
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> results = pm.queryIntentActivitiesAsUser(intent, 128, user.getIdentifier());
        for (ResolveInfo resolved : results) {
            if (resolved.system) {
                ActivityInfo activityInfo = resolved.activityInfo;
                Bundle metaData = activityInfo.metaData;
                String str = EXTRA_CATEGORY_KEY;
                if ((metaData == null || !metaData.containsKey(EXTRA_CATEGORY_KEY)) && defaultCategory == null) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Found ");
                    sb.append(resolved.activityInfo.name);
                    sb.append(" for intent ");
                    sb.append(intent);
                    sb.append(" missing metadata ");
                    if (metaData == null) {
                        str = "";
                    }
                    sb.append(str);
                    Log.w(LOG_TAG, sb.toString());
                } else {
                    String categoryKey = metaData.getString(EXTRA_CATEGORY_KEY);
                    Pair<String, String> key = new Pair<>(activityInfo.packageName, activityInfo.name);
                    Tile tile = addedCache.get(key);
                    if (tile == null) {
                        tile = new Tile(activityInfo, categoryKey);
                        addedCache.put(key, tile);
                    } else {
                        tile.setMetaData(metaData);
                    }
                    if (!tile.userHandle.contains(user)) {
                        tile.userHandle.add(user);
                    }
                    if (!outTiles.contains(tile)) {
                        outTiles.add(tile);
                    }
                }
            }
        }
    }

    public static Pair<String, Integer> getIconFromUri(Context context, String packageName, String uriString, Map<String, IContentProvider> providerMap) {
        Bundle bundle = getBundleFromUri(context, uriString, providerMap);
        if (bundle == null) {
            return null;
        }
        String iconPackageName = bundle.getString(EXTRA_PREFERENCE_ICON_PACKAGE);
        if (TextUtils.isEmpty(iconPackageName)) {
            return null;
        }
        int resId = bundle.getInt(META_DATA_PREFERENCE_ICON, 0);
        if (resId == 0) {
            return null;
        }
        if (!iconPackageName.equals(packageName) && !iconPackageName.equals(context.getPackageName())) {
            return null;
        }
        return Pair.create(iconPackageName, Integer.valueOf(bundle.getInt(META_DATA_PREFERENCE_ICON, 0)));
    }

    public static String getTextFromUri(Context context, String uriString, Map<String, IContentProvider> providerMap, String key) {
        Bundle bundle = getBundleFromUri(context, uriString, providerMap);
        if (bundle != null) {
            return bundle.getString(key);
        }
        return null;
    }

    private static Bundle getBundleFromUri(Context context, String uriString, Map<String, IContentProvider> providerMap) {
        IContentProvider provider;
        if (TextUtils.isEmpty(uriString)) {
            return null;
        }
        Uri uri = Uri.parse(uriString);
        String method = getMethodFromUri(uri);
        if (TextUtils.isEmpty(method) || (provider = getProviderFromUri(context, uri, providerMap)) == null) {
            return null;
        }
        try {
            return provider.call(context.getPackageName(), uri.getAuthority(), method, uriString, (Bundle) null);
        } catch (RemoteException e) {
            return null;
        }
    }

    private static IContentProvider getProviderFromUri(Context context, Uri uri, Map<String, IContentProvider> providerMap) {
        if (uri == null) {
            return null;
        }
        String authority = uri.getAuthority();
        if (TextUtils.isEmpty(authority)) {
            return null;
        }
        if (!providerMap.containsKey(authority)) {
            providerMap.put(authority, context.getContentResolver().acquireUnstableProvider(uri));
        }
        return providerMap.get(authority);
    }

    static String getMethodFromUri(Uri uri) {
        List<String> pathSegments;
        if (uri == null || (pathSegments = uri.getPathSegments()) == null || pathSegments.isEmpty()) {
            return null;
        }
        return pathSegments.get(0);
    }
}
