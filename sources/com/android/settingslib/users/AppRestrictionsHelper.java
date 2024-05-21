package com.android.settingslib.users;

import android.app.AppGlobals;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ParceledListSlice;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.IntentCompat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
/* loaded from: classes3.dex */
public class AppRestrictionsHelper {
    private static final boolean DEBUG = false;
    private static final String TAG = "AppRestrictionsHelper";
    private final Context mContext;
    private final IPackageManager mIPm;
    private final Injector mInjector;
    private boolean mLeanback;
    private final PackageManager mPackageManager;
    private final boolean mRestrictedProfile;
    HashMap<String, Boolean> mSelectedPackages;
    private final UserHandle mUser;
    private final UserManager mUserManager;
    private List<SelectableAppInfo> mVisibleApps;

    /* loaded from: classes3.dex */
    public interface OnDisableUiForPackageListener {
        void onDisableUiForPackage(String str);
    }

    public AppRestrictionsHelper(Context context, UserHandle user) {
        this(new Injector(context, user));
    }

    @VisibleForTesting
    AppRestrictionsHelper(Injector injector) {
        this.mSelectedPackages = new HashMap<>();
        this.mInjector = injector;
        this.mContext = this.mInjector.getContext();
        this.mPackageManager = this.mInjector.getPackageManager();
        this.mIPm = this.mInjector.getIPackageManager();
        this.mUser = this.mInjector.getUser();
        this.mUserManager = this.mInjector.getUserManager();
        this.mRestrictedProfile = this.mUserManager.getUserInfo(this.mUser.getIdentifier()).isRestricted();
    }

    public void setPackageSelected(String packageName, boolean selected) {
        this.mSelectedPackages.put(packageName, Boolean.valueOf(selected));
    }

    public boolean isPackageSelected(String packageName) {
        return this.mSelectedPackages.get(packageName).booleanValue();
    }

    public void setLeanback(boolean isLeanback) {
        this.mLeanback = isLeanback;
    }

    public List<SelectableAppInfo> getVisibleApps() {
        return this.mVisibleApps;
    }

    public void applyUserAppsStates(OnDisableUiForPackageListener listener) {
        if (!this.mRestrictedProfile && this.mUser.getIdentifier() != UserHandle.myUserId()) {
            Log.e(TAG, "Cannot apply application restrictions on another user!");
            return;
        }
        for (Map.Entry<String, Boolean> entry : this.mSelectedPackages.entrySet()) {
            String packageName = entry.getKey();
            boolean enabled = entry.getValue().booleanValue();
            applyUserAppState(packageName, enabled, listener);
        }
    }

    public void applyUserAppState(String packageName, boolean enabled, OnDisableUiForPackageListener listener) {
        int userId = this.mUser.getIdentifier();
        if (enabled) {
            try {
                ApplicationInfo info = this.mIPm.getApplicationInfo(packageName, 4194304, userId);
                if (info == null || !info.enabled || (info.flags & 8388608) == 0) {
                    this.mIPm.installExistingPackageAsUser(packageName, this.mUser.getIdentifier(), 4194304, 0, (List) null);
                }
                if (info != null && (1 & info.privateFlags) != 0 && (info.flags & 8388608) != 0) {
                    listener.onDisableUiForPackage(packageName);
                    this.mIPm.setApplicationHiddenSettingAsUser(packageName, false, userId);
                    return;
                }
                return;
            } catch (RemoteException e) {
                return;
            }
        }
        try {
            if (this.mIPm.getApplicationInfo(packageName, 0, userId) != null) {
                if (this.mRestrictedProfile) {
                    this.mPackageManager.deletePackageAsUser(packageName, null, 4, this.mUser.getIdentifier());
                } else {
                    listener.onDisableUiForPackage(packageName);
                    this.mIPm.setApplicationHiddenSettingAsUser(packageName, true, userId);
                }
            }
        } catch (RemoteException e2) {
        }
    }

    public void fetchAndMergeApps() {
        this.mVisibleApps = new ArrayList();
        PackageManager pm = this.mPackageManager;
        IPackageManager ipm = this.mIPm;
        HashSet<String> excludePackages = new HashSet<>();
        addSystemImes(excludePackages);
        Intent launcherIntent = new Intent("android.intent.action.MAIN");
        if (this.mLeanback) {
            launcherIntent.addCategory(IntentCompat.CATEGORY_LEANBACK_LAUNCHER);
        } else {
            launcherIntent.addCategory("android.intent.category.LAUNCHER");
        }
        addSystemApps(this.mVisibleApps, launcherIntent, excludePackages);
        Intent widgetIntent = new Intent("android.appwidget.action.APPWIDGET_UPDATE");
        addSystemApps(this.mVisibleApps, widgetIntent, excludePackages);
        List<ApplicationInfo> installedApps = pm.getInstalledApplications(4194304);
        for (ApplicationInfo app : installedApps) {
            if ((8388608 & app.flags) != 0) {
                if ((app.flags & 1) == 0 && (app.flags & 128) == 0) {
                    SelectableAppInfo info = new SelectableAppInfo();
                    info.packageName = app.packageName;
                    info.appName = app.loadLabel(pm);
                    info.activityName = info.appName;
                    info.icon = app.loadIcon(pm);
                    this.mVisibleApps.add(info);
                } else {
                    try {
                        PackageInfo pi = pm.getPackageInfo(app.packageName, 0);
                        if (this.mRestrictedProfile && pi.requiredAccountType != null && pi.restrictedAccountType == null) {
                            this.mSelectedPackages.put(app.packageName, false);
                        }
                    } catch (PackageManager.NameNotFoundException e) {
                    }
                }
            }
        }
        List<ApplicationInfo> userApps = null;
        try {
            ParceledListSlice<ApplicationInfo> listSlice = ipm.getInstalledApplications(8192, this.mUser.getIdentifier());
            if (listSlice != null) {
                userApps = listSlice.getList();
            }
        } catch (RemoteException e2) {
        }
        if (userApps != null) {
            for (ApplicationInfo app2 : userApps) {
                if ((app2.flags & 8388608) != 0 && (app2.flags & 1) == 0 && (app2.flags & 128) == 0) {
                    SelectableAppInfo info2 = new SelectableAppInfo();
                    info2.packageName = app2.packageName;
                    info2.appName = app2.loadLabel(pm);
                    info2.activityName = info2.appName;
                    info2.icon = app2.loadIcon(pm);
                    this.mVisibleApps.add(info2);
                }
            }
        }
        Collections.sort(this.mVisibleApps, new AppLabelComparator());
        Set<String> dedupPackageSet = new HashSet<>();
        for (int i = this.mVisibleApps.size() - 1; i >= 0; i--) {
            SelectableAppInfo info3 = this.mVisibleApps.get(i);
            String both = info3.packageName + "+" + ((Object) info3.activityName);
            if (!TextUtils.isEmpty(info3.packageName) && !TextUtils.isEmpty(info3.activityName) && dedupPackageSet.contains(both)) {
                this.mVisibleApps.remove(i);
            } else {
                dedupPackageSet.add(both);
            }
        }
        HashMap<String, SelectableAppInfo> packageMap = new HashMap<>();
        for (SelectableAppInfo info4 : this.mVisibleApps) {
            if (packageMap.containsKey(info4.packageName)) {
                info4.masterEntry = packageMap.get(info4.packageName);
            } else {
                packageMap.put(info4.packageName, info4);
            }
        }
    }

    private void addSystemImes(Set<String> excludePackages) {
        List<InputMethodInfo> imis = this.mInjector.getInputMethodList();
        for (InputMethodInfo imi : imis) {
            try {
                if (imi.isDefault(this.mContext) && isSystemPackage(imi.getPackageName())) {
                    excludePackages.add(imi.getPackageName());
                }
            } catch (Resources.NotFoundException e) {
            }
        }
    }

    private void addSystemApps(List<SelectableAppInfo> visibleApps, Intent intent, Set<String> excludePackages) {
        int enabled;
        ApplicationInfo targetUserAppInfo;
        PackageManager pm = this.mPackageManager;
        List<ResolveInfo> launchableApps = pm.queryIntentActivities(intent, 8704);
        for (ResolveInfo app : launchableApps) {
            if (app.activityInfo != null && app.activityInfo.applicationInfo != null) {
                String packageName = app.activityInfo.packageName;
                int flags = app.activityInfo.applicationInfo.flags;
                if ((flags & 1) != 0 || (flags & 128) != 0) {
                    if (!excludePackages.contains(packageName) && (((enabled = pm.getApplicationEnabledSetting(packageName)) != 4 && enabled != 2) || ((targetUserAppInfo = getAppInfoForUser(packageName, 0, this.mUser)) != null && (targetUserAppInfo.flags & 8388608) != 0))) {
                        SelectableAppInfo info = new SelectableAppInfo();
                        info.packageName = app.activityInfo.packageName;
                        info.appName = app.activityInfo.applicationInfo.loadLabel(pm);
                        info.icon = app.activityInfo.loadIcon(pm);
                        info.activityName = app.activityInfo.loadLabel(pm);
                        if (info.activityName == null) {
                            info.activityName = info.appName;
                        }
                        visibleApps.add(info);
                    }
                }
            }
        }
    }

    private boolean isSystemPackage(String packageName) {
        PackageInfo pi;
        try {
            pi = this.mPackageManager.getPackageInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
        }
        if (pi.applicationInfo == null) {
            return false;
        }
        int flags = pi.applicationInfo.flags;
        if ((flags & 1) == 0 && (flags & 128) == 0) {
            return false;
        }
        return true;
    }

    private ApplicationInfo getAppInfoForUser(String packageName, int flags, UserHandle user) {
        try {
            return this.mIPm.getApplicationInfo(packageName, flags, user.getIdentifier());
        } catch (RemoteException e) {
            return null;
        }
    }

    /* loaded from: classes3.dex */
    public static class SelectableAppInfo {
        public CharSequence activityName;
        public CharSequence appName;
        public Drawable icon;
        public SelectableAppInfo masterEntry;
        public String packageName;

        public String toString() {
            return this.packageName + ": appName=" + ((Object) this.appName) + "; activityName=" + ((Object) this.activityName) + "; icon=" + this.icon + "; masterEntry=" + this.masterEntry;
        }
    }

    /* loaded from: classes3.dex */
    private static class AppLabelComparator implements Comparator<SelectableAppInfo> {
        private AppLabelComparator() {
        }

        @Override // java.util.Comparator
        public int compare(SelectableAppInfo lhs, SelectableAppInfo rhs) {
            String lhsLabel = lhs.activityName.toString();
            String rhsLabel = rhs.activityName.toString();
            return lhsLabel.toLowerCase().compareTo(rhsLabel.toLowerCase());
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @VisibleForTesting
    /* loaded from: classes3.dex */
    public static class Injector {
        private Context mContext;
        private UserHandle mUser;

        Injector(Context context, UserHandle user) {
            this.mContext = context;
            this.mUser = user;
        }

        Context getContext() {
            return this.mContext;
        }

        UserHandle getUser() {
            return this.mUser;
        }

        PackageManager getPackageManager() {
            return this.mContext.getPackageManager();
        }

        IPackageManager getIPackageManager() {
            return AppGlobals.getPackageManager();
        }

        UserManager getUserManager() {
            return (UserManager) this.mContext.getSystemService(UserManager.class);
        }

        List<InputMethodInfo> getInputMethodList() {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService("input_method");
            return imm.getInputMethodListAsUser(this.mUser.getIdentifier());
        }
    }
}
