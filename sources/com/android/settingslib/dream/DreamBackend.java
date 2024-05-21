package com.android.settingslib.dream;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Drawable;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.service.dreams.IDreamManager;
import android.util.Log;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
/* loaded from: classes3.dex */
public class DreamBackend {
    private static final boolean DEBUG = false;
    public static final int EITHER = 2;
    public static final int NEVER = 3;
    private static final String TAG = "DreamBackend";
    public static final int WHILE_CHARGING = 0;
    public static final int WHILE_DOCKED = 1;
    private static DreamBackend sInstance;
    private final Context mContext;
    private final boolean mDreamsActivatedOnDockByDefault;
    private final boolean mDreamsActivatedOnSleepByDefault;
    private final boolean mDreamsEnabledByDefault;
    private final IDreamManager mDreamManager = IDreamManager.Stub.asInterface(ServiceManager.getService("dreams"));
    private final DreamInfoComparator mComparator = new DreamInfoComparator(getDefaultDream());

    @Retention(RetentionPolicy.SOURCE)
    /* loaded from: classes3.dex */
    public @interface WhenToDream {
    }

    /* loaded from: classes3.dex */
    public static class DreamInfo {
        public CharSequence caption;
        public ComponentName componentName;
        public Drawable icon;
        public boolean isActive;
        public ComponentName settingsComponentName;

        public String toString() {
            StringBuilder sb = new StringBuilder(DreamInfo.class.getSimpleName());
            sb.append('[');
            sb.append(this.caption);
            if (this.isActive) {
                sb.append(",active");
            }
            sb.append(',');
            sb.append(this.componentName);
            if (this.settingsComponentName != null) {
                sb.append("settings=");
                sb.append(this.settingsComponentName);
            }
            sb.append(']');
            return sb.toString();
        }
    }

    public static DreamBackend getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new DreamBackend(context);
        }
        return sInstance;
    }

    public DreamBackend(Context context) {
        this.mContext = context.getApplicationContext();
        this.mDreamsEnabledByDefault = this.mContext.getResources().getBoolean(17891425);
        this.mDreamsActivatedOnSleepByDefault = this.mContext.getResources().getBoolean(17891424);
        this.mDreamsActivatedOnDockByDefault = this.mContext.getResources().getBoolean(17891423);
    }

    public List<DreamInfo> getDreamInfos() {
        logd("getDreamInfos()", new Object[0]);
        ComponentName activeDream = getActiveDream();
        PackageManager pm = this.mContext.getPackageManager();
        Intent dreamIntent = new Intent("android.service.dreams.DreamService");
        List<ResolveInfo> resolveInfos = pm.queryIntentServices(dreamIntent, 128);
        List<DreamInfo> dreamInfos = new ArrayList<>(resolveInfos.size());
        for (ResolveInfo resolveInfo : resolveInfos) {
            if (resolveInfo.serviceInfo != null) {
                DreamInfo dreamInfo = new DreamInfo();
                dreamInfo.caption = resolveInfo.loadLabel(pm);
                dreamInfo.icon = resolveInfo.loadIcon(pm);
                dreamInfo.componentName = getDreamComponentName(resolveInfo);
                dreamInfo.isActive = dreamInfo.componentName.equals(activeDream);
                dreamInfo.settingsComponentName = getSettingsComponentName(pm, resolveInfo);
                dreamInfos.add(dreamInfo);
            }
        }
        Collections.sort(dreamInfos, this.mComparator);
        return dreamInfos;
    }

    public ComponentName getDefaultDream() {
        IDreamManager iDreamManager = this.mDreamManager;
        if (iDreamManager == null) {
            return null;
        }
        try {
            return iDreamManager.getDefaultDreamComponent();
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to get default dream", e);
            return null;
        }
    }

    public CharSequence getActiveDreamName() {
        ComponentName cn = getActiveDream();
        if (cn != null) {
            PackageManager pm = this.mContext.getPackageManager();
            try {
                ServiceInfo ri = pm.getServiceInfo(cn, 0);
                if (ri != null) {
                    return ri.loadLabel(pm);
                }
            } catch (PackageManager.NameNotFoundException e) {
                return null;
            }
        }
        return null;
    }

    public int getWhenToDreamSetting() {
        if (isEnabled()) {
            if (isActivatedOnDock() && isActivatedOnSleep()) {
                return 2;
            }
            if (isActivatedOnDock()) {
                return 1;
            }
            return isActivatedOnSleep() ? 0 : 3;
        }
        return 3;
    }

    public void setWhenToDream(int whenToDream) {
        setEnabled(whenToDream != 3);
        if (whenToDream == 0) {
            setActivatedOnDock(false);
            setActivatedOnSleep(true);
        } else if (whenToDream == 1) {
            setActivatedOnDock(true);
            setActivatedOnSleep(false);
        } else if (whenToDream == 2) {
            setActivatedOnDock(true);
            setActivatedOnSleep(true);
        }
    }

    public boolean isEnabled() {
        return getBoolean("screensaver_enabled", this.mDreamsEnabledByDefault);
    }

    public void setEnabled(boolean value) {
        logd("setEnabled(%s)", Boolean.valueOf(value));
        setBoolean("screensaver_enabled", value);
    }

    public boolean isActivatedOnDock() {
        return getBoolean("screensaver_activate_on_dock", this.mDreamsActivatedOnDockByDefault);
    }

    public void setActivatedOnDock(boolean value) {
        logd("setActivatedOnDock(%s)", Boolean.valueOf(value));
        setBoolean("screensaver_activate_on_dock", value);
    }

    public boolean isActivatedOnSleep() {
        return getBoolean("screensaver_activate_on_sleep", this.mDreamsActivatedOnSleepByDefault);
    }

    public void setActivatedOnSleep(boolean value) {
        logd("setActivatedOnSleep(%s)", Boolean.valueOf(value));
        setBoolean("screensaver_activate_on_sleep", value);
    }

    private boolean getBoolean(String key, boolean def) {
        return Settings.Secure.getInt(this.mContext.getContentResolver(), key, def ? 1 : 0) == 1;
    }

    private void setBoolean(String key, boolean value) {
        Settings.Secure.putInt(this.mContext.getContentResolver(), key, value ? 1 : 0);
    }

    public void setActiveDream(ComponentName dream) {
        logd("setActiveDream(%s)", dream);
        IDreamManager iDreamManager = this.mDreamManager;
        if (iDreamManager == null) {
            return;
        }
        try {
            ComponentName[] dreams = {dream};
            iDreamManager.setDreamComponents(dream == null ? null : dreams);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to set active dream to " + dream, e);
        }
    }

    public ComponentName getActiveDream() {
        IDreamManager iDreamManager = this.mDreamManager;
        if (iDreamManager == null) {
            return null;
        }
        try {
            ComponentName[] dreams = iDreamManager.getDreamComponents();
            if (dreams == null || dreams.length <= 0) {
                return null;
            }
            return dreams[0];
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to get active dream", e);
            return null;
        }
    }

    public void launchSettings(Context uiContext, DreamInfo dreamInfo) {
        logd("launchSettings(%s)", dreamInfo);
        if (dreamInfo == null || dreamInfo.settingsComponentName == null) {
            return;
        }
        uiContext.startActivity(new Intent().setComponent(dreamInfo.settingsComponentName));
    }

    public void preview(DreamInfo dreamInfo) {
        logd("preview(%s)", dreamInfo);
        if (this.mDreamManager == null || dreamInfo == null || dreamInfo.componentName == null) {
            return;
        }
        try {
            this.mDreamManager.testDream(dreamInfo.componentName);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to preview " + dreamInfo, e);
        }
    }

    public void startDreaming() {
        logd("startDreaming()", new Object[0]);
        IDreamManager iDreamManager = this.mDreamManager;
        if (iDreamManager == null) {
            return;
        }
        try {
            iDreamManager.dream();
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to dream", e);
        }
    }

    private static ComponentName getDreamComponentName(ResolveInfo resolveInfo) {
        if (resolveInfo == null || resolveInfo.serviceInfo == null) {
            return null;
        }
        return new ComponentName(resolveInfo.serviceInfo.packageName, resolveInfo.serviceInfo.name);
    }

    /* JADX WARN: Code restructure failed: missing block: B:35:0x0076, code lost:
        if (0 == 0) goto L30;
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    private static android.content.ComponentName getSettingsComponentName(android.content.pm.PackageManager r11, android.content.pm.ResolveInfo r12) {
        /*
            java.lang.String r0 = "DreamBackend"
            r1 = 0
            if (r12 == 0) goto Lbe
            android.content.pm.ServiceInfo r2 = r12.serviceInfo
            if (r2 == 0) goto Lbe
            android.content.pm.ServiceInfo r2 = r12.serviceInfo
            android.os.Bundle r2 = r2.metaData
            if (r2 != 0) goto L11
            goto Lbe
        L11:
            r2 = 0
            r3 = 0
            r4 = 0
            android.content.pm.ServiceInfo r5 = r12.serviceInfo     // Catch: java.lang.Throwable -> L6d java.lang.Throwable -> L74
            java.lang.String r6 = "android.service.dream"
            android.content.res.XmlResourceParser r5 = r5.loadXmlMetaData(r11, r6)     // Catch: java.lang.Throwable -> L6d java.lang.Throwable -> L74
            r3 = r5
            if (r3 != 0) goto L2b
            java.lang.String r5 = "No android.service.dream meta-data"
            android.util.Log.w(r0, r5)     // Catch: java.lang.Throwable -> L6d java.lang.Throwable -> L74
            if (r3 == 0) goto L2a
            r3.close()
        L2a:
            return r1
        L2b:
            android.content.pm.ServiceInfo r5 = r12.serviceInfo     // Catch: java.lang.Throwable -> L6d java.lang.Throwable -> L74
            android.content.pm.ApplicationInfo r5 = r5.applicationInfo     // Catch: java.lang.Throwable -> L6d java.lang.Throwable -> L74
            android.content.res.Resources r5 = r11.getResourcesForApplication(r5)     // Catch: java.lang.Throwable -> L6d java.lang.Throwable -> L74
            android.util.AttributeSet r6 = android.util.Xml.asAttributeSet(r3)     // Catch: java.lang.Throwable -> L6d java.lang.Throwable -> L74
        L37:
            int r7 = r3.next()     // Catch: java.lang.Throwable -> L6d java.lang.Throwable -> L74
            r8 = r7
            r9 = 1
            if (r7 == r9) goto L43
            r7 = 2
            if (r8 == r7) goto L43
            goto L37
        L43:
            java.lang.String r7 = r3.getName()     // Catch: java.lang.Throwable -> L6d java.lang.Throwable -> L74
            java.lang.String r9 = "dream"
            boolean r9 = r9.equals(r7)     // Catch: java.lang.Throwable -> L6d java.lang.Throwable -> L74
            if (r9 != 0) goto L59
            java.lang.String r9 = "Meta-data does not start with dream tag"
            android.util.Log.w(r0, r9)     // Catch: java.lang.Throwable -> L6d java.lang.Throwable -> L74
            r3.close()
            return r1
        L59:
            int[] r9 = com.android.internal.R.styleable.Dream     // Catch: java.lang.Throwable -> L6d java.lang.Throwable -> L74
            android.content.res.TypedArray r9 = r5.obtainAttributes(r6, r9)     // Catch: java.lang.Throwable -> L6d java.lang.Throwable -> L74
            r10 = 0
            java.lang.String r10 = r9.getString(r10)     // Catch: java.lang.Throwable -> L6d java.lang.Throwable -> L74
            r2 = r10
            r9.recycle()     // Catch: java.lang.Throwable -> L6d java.lang.Throwable -> L74
        L69:
            r3.close()
            goto L79
        L6d:
            r0 = move-exception
            if (r3 == 0) goto L73
            r3.close()
        L73:
            throw r0
        L74:
            r5 = move-exception
            r4 = r5
            if (r3 == 0) goto L79
            goto L69
        L79:
            if (r4 == 0) goto L94
            java.lang.StringBuilder r5 = new java.lang.StringBuilder
            r5.<init>()
            java.lang.String r6 = "Error parsing : "
            r5.append(r6)
            android.content.pm.ServiceInfo r6 = r12.serviceInfo
            java.lang.String r6 = r6.packageName
            r5.append(r6)
            java.lang.String r5 = r5.toString()
            android.util.Log.w(r0, r5, r4)
            return r1
        L94:
            if (r2 == 0) goto Lb6
            r0 = 47
            int r0 = r2.indexOf(r0)
            if (r0 >= 0) goto Lb6
            java.lang.StringBuilder r0 = new java.lang.StringBuilder
            r0.<init>()
            android.content.pm.ServiceInfo r5 = r12.serviceInfo
            java.lang.String r5 = r5.packageName
            r0.append(r5)
            java.lang.String r5 = "/"
            r0.append(r5)
            r0.append(r2)
            java.lang.String r2 = r0.toString()
        Lb6:
            if (r2 != 0) goto Lb9
            goto Lbd
        Lb9:
            android.content.ComponentName r1 = android.content.ComponentName.unflattenFromString(r2)
        Lbd:
            return r1
        Lbe:
            return r1
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.settingslib.dream.DreamBackend.getSettingsComponentName(android.content.pm.PackageManager, android.content.pm.ResolveInfo):android.content.ComponentName");
    }

    private static void logd(String msg, Object... args) {
    }

    /* loaded from: classes3.dex */
    private static class DreamInfoComparator implements Comparator<DreamInfo> {
        private final ComponentName mDefaultDream;

        public DreamInfoComparator(ComponentName defaultDream) {
            this.mDefaultDream = defaultDream;
        }

        @Override // java.util.Comparator
        public int compare(DreamInfo lhs, DreamInfo rhs) {
            return sortKey(lhs).compareTo(sortKey(rhs));
        }

        private String sortKey(DreamInfo di) {
            StringBuilder sb = new StringBuilder();
            sb.append(di.componentName.equals(this.mDefaultDream) ? '0' : '1');
            sb.append(di.caption);
            return sb.toString();
        }
    }
}
