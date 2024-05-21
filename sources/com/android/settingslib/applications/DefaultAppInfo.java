package com.android.settingslib.applications;

import android.app.AppGlobals;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.RemoteException;
import android.util.IconDrawableFactory;
import com.android.settingslib.widget.CandidateInfo;
/* loaded from: classes3.dex */
public class DefaultAppInfo extends CandidateInfo {
    public final ComponentName componentName;
    private final Context mContext;
    protected final PackageManager mPm;
    public final PackageItemInfo packageItemInfo;
    public final String summary;
    public final int userId;

    public DefaultAppInfo(Context context, PackageManager pm, int uid, ComponentName cn) {
        this(context, pm, uid, cn, (String) null, true);
    }

    public DefaultAppInfo(Context context, PackageManager pm, int uid, PackageItemInfo info) {
        this(context, pm, uid, info, (String) null, true);
    }

    public DefaultAppInfo(Context context, PackageManager pm, int uid, ComponentName cn, String summary, boolean enabled) {
        super(enabled);
        this.mContext = context;
        this.mPm = pm;
        this.packageItemInfo = null;
        this.userId = uid;
        this.componentName = cn;
        this.summary = summary;
    }

    public DefaultAppInfo(Context context, PackageManager pm, int uid, PackageItemInfo info, String summary, boolean enabled) {
        super(enabled);
        this.mContext = context;
        this.mPm = pm;
        this.userId = uid;
        this.packageItemInfo = info;
        this.componentName = null;
        this.summary = summary;
    }

    @Override // com.android.settingslib.widget.CandidateInfo
    public CharSequence loadLabel() {
        if (this.componentName != null) {
            try {
                ComponentInfo componentInfo = getComponentInfo();
                if (componentInfo != null) {
                    return componentInfo.loadLabel(this.mPm);
                }
                ApplicationInfo appInfo = this.mPm.getApplicationInfoAsUser(this.componentName.getPackageName(), 0, this.userId);
                return appInfo.loadLabel(this.mPm);
            } catch (PackageManager.NameNotFoundException e) {
                return null;
            }
        }
        PackageItemInfo packageItemInfo = this.packageItemInfo;
        if (packageItemInfo != null) {
            return packageItemInfo.loadLabel(this.mPm);
        }
        return null;
    }

    @Override // com.android.settingslib.widget.CandidateInfo
    public Drawable loadIcon() {
        IconDrawableFactory factory = IconDrawableFactory.newInstance(this.mContext);
        if (this.componentName != null) {
            try {
                ComponentInfo componentInfo = getComponentInfo();
                ApplicationInfo appInfo = this.mPm.getApplicationInfoAsUser(this.componentName.getPackageName(), 0, this.userId);
                if (componentInfo != null) {
                    return factory.getBadgedIcon(componentInfo, appInfo, this.userId);
                }
                return factory.getBadgedIcon(appInfo);
            } catch (PackageManager.NameNotFoundException e) {
                return null;
            }
        }
        PackageItemInfo packageItemInfo = this.packageItemInfo;
        if (packageItemInfo != null) {
            try {
                return factory.getBadgedIcon(this.packageItemInfo, this.mPm.getApplicationInfoAsUser(packageItemInfo.packageName, 0, this.userId), this.userId);
            } catch (PackageManager.NameNotFoundException e2) {
                return null;
            }
        }
        return null;
    }

    @Override // com.android.settingslib.widget.CandidateInfo
    public String getKey() {
        ComponentName componentName = this.componentName;
        if (componentName != null) {
            return componentName.flattenToString();
        }
        PackageItemInfo packageItemInfo = this.packageItemInfo;
        if (packageItemInfo != null) {
            return packageItemInfo.packageName;
        }
        return null;
    }

    private ComponentInfo getComponentInfo() {
        try {
            ComponentInfo componentInfo = AppGlobals.getPackageManager().getActivityInfo(this.componentName, 0, this.userId);
            if (componentInfo == null) {
                return AppGlobals.getPackageManager().getServiceInfo(this.componentName, 0, this.userId);
            }
            return componentInfo;
        } catch (RemoteException e) {
            return null;
        }
    }
}
