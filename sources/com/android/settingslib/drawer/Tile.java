package com.android.settingslib.drawer;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.VisibleForTesting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
/* loaded from: classes3.dex */
public class Tile implements Parcelable {
    private static final String TAG = "Tile";
    private ActivityInfo mActivityInfo;
    private final String mActivityName;
    private final String mActivityPackage;
    private String mCategory;
    private final Intent mIntent;
    @VisibleForTesting
    long mLastUpdateTime;
    private Bundle mMetaData;
    private CharSequence mSummaryOverride;
    public ArrayList<UserHandle> userHandle = new ArrayList<>();
    public static final Parcelable.Creator<Tile> CREATOR = new Parcelable.Creator<Tile>() { // from class: com.android.settingslib.drawer.Tile.1
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public Tile createFromParcel(Parcel source) {
            return new Tile(source);
        }

        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public Tile[] newArray(int size) {
            return new Tile[size];
        }
    };
    public static final Comparator<Tile> TILE_COMPARATOR = new Comparator() { // from class: com.android.settingslib.drawer.-$$Lambda$Tile$5_ETnVHzVG6DF0RKPoy76eRI-QM
        @Override // java.util.Comparator
        public final int compare(Object obj, Object obj2) {
            return Tile.lambda$static$0((Tile) obj, (Tile) obj2);
        }
    };

    public Tile(ActivityInfo activityInfo, String category) {
        this.mActivityInfo = activityInfo;
        this.mActivityPackage = this.mActivityInfo.packageName;
        this.mActivityName = this.mActivityInfo.name;
        this.mMetaData = activityInfo.metaData;
        this.mCategory = category;
        this.mIntent = new Intent().setClassName(this.mActivityPackage, this.mActivityName);
    }

    Tile(Parcel in) {
        this.mActivityPackage = in.readString();
        this.mActivityName = in.readString();
        this.mIntent = new Intent().setClassName(this.mActivityPackage, this.mActivityName);
        int number = in.readInt();
        for (int i = 0; i < number; i++) {
            this.userHandle.add((UserHandle) UserHandle.CREATOR.createFromParcel(in));
        }
        this.mCategory = in.readString();
        this.mMetaData = in.readBundle();
    }

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.mActivityPackage);
        dest.writeString(this.mActivityName);
        int size = this.userHandle.size();
        dest.writeInt(size);
        for (int i = 0; i < size; i++) {
            this.userHandle.get(i).writeToParcel(dest, flags);
        }
        dest.writeString(this.mCategory);
        dest.writeBundle(this.mMetaData);
    }

    public int getId() {
        return Objects.hash(this.mActivityPackage, this.mActivityName);
    }

    public String getDescription() {
        return this.mActivityPackage + "/" + this.mActivityName;
    }

    public String getPackageName() {
        return this.mActivityPackage;
    }

    public Intent getIntent() {
        return this.mIntent;
    }

    public String getCategory() {
        return this.mCategory;
    }

    public void setCategory(String newCategoryKey) {
        this.mCategory = newCategoryKey;
    }

    public int getOrder() {
        if (hasOrder()) {
            return this.mMetaData.getInt(TileUtils.META_DATA_KEY_ORDER);
        }
        return 0;
    }

    public boolean hasOrder() {
        return this.mMetaData.containsKey(TileUtils.META_DATA_KEY_ORDER) && (this.mMetaData.get(TileUtils.META_DATA_KEY_ORDER) instanceof Integer);
    }

    public CharSequence getTitle(Context context) {
        CharSequence title = null;
        ensureMetadataNotStale(context);
        PackageManager packageManager = context.getPackageManager();
        if (this.mMetaData.containsKey(TileUtils.META_DATA_PREFERENCE_TITLE)) {
            if (this.mMetaData.get(TileUtils.META_DATA_PREFERENCE_TITLE) instanceof Integer) {
                try {
                    Resources res = packageManager.getResourcesForApplication(this.mActivityPackage);
                    title = res.getString(this.mMetaData.getInt(TileUtils.META_DATA_PREFERENCE_TITLE));
                } catch (PackageManager.NameNotFoundException | Resources.NotFoundException e) {
                    Log.w(TAG, "Couldn't find info", e);
                }
            } else {
                title = this.mMetaData.getString(TileUtils.META_DATA_PREFERENCE_TITLE);
            }
        }
        if (title == null) {
            ActivityInfo activityInfo = getActivityInfo(context);
            if (activityInfo == null) {
                return null;
            }
            return activityInfo.loadLabel(packageManager);
        }
        return title;
    }

    public void overrideSummary(CharSequence summaryOverride) {
        this.mSummaryOverride = summaryOverride;
    }

    public CharSequence getSummary(Context context) {
        CharSequence charSequence = this.mSummaryOverride;
        if (charSequence != null) {
            return charSequence;
        }
        ensureMetadataNotStale(context);
        PackageManager packageManager = context.getPackageManager();
        Bundle bundle = this.mMetaData;
        if (bundle == null || bundle.containsKey(TileUtils.META_DATA_PREFERENCE_SUMMARY_URI) || !this.mMetaData.containsKey(TileUtils.META_DATA_PREFERENCE_SUMMARY)) {
            return null;
        }
        if (this.mMetaData.get(TileUtils.META_DATA_PREFERENCE_SUMMARY) instanceof Integer) {
            try {
                Resources res = packageManager.getResourcesForApplication(this.mActivityPackage);
                CharSequence summary = res.getString(this.mMetaData.getInt(TileUtils.META_DATA_PREFERENCE_SUMMARY));
                return summary;
            } catch (PackageManager.NameNotFoundException | Resources.NotFoundException e) {
                Log.d(TAG, "Couldn't find info", e);
                return null;
            }
        }
        CharSequence summary2 = this.mMetaData.getString(TileUtils.META_DATA_PREFERENCE_SUMMARY);
        return summary2;
    }

    public void setMetaData(Bundle metaData) {
        this.mMetaData = metaData;
    }

    public Bundle getMetaData() {
        return this.mMetaData;
    }

    public String getKey(Context context) {
        if (!hasKey()) {
            return null;
        }
        ensureMetadataNotStale(context);
        if (this.mMetaData.get(TileUtils.META_DATA_PREFERENCE_KEYHINT) instanceof Integer) {
            return context.getResources().getString(this.mMetaData.getInt(TileUtils.META_DATA_PREFERENCE_KEYHINT));
        }
        return this.mMetaData.getString(TileUtils.META_DATA_PREFERENCE_KEYHINT);
    }

    public boolean hasKey() {
        Bundle bundle = this.mMetaData;
        return bundle != null && bundle.containsKey(TileUtils.META_DATA_PREFERENCE_KEYHINT);
    }

    public Icon getIcon(Context context) {
        if (context == null || this.mMetaData == null) {
            return null;
        }
        ensureMetadataNotStale(context);
        ActivityInfo activityInfo = getActivityInfo(context);
        if (activityInfo == null) {
            Log.w(TAG, "Cannot find ActivityInfo for " + getDescription());
            return null;
        }
        int iconResId = this.mMetaData.getInt(TileUtils.META_DATA_PREFERENCE_ICON);
        if (iconResId == 0 && !this.mMetaData.containsKey(TileUtils.META_DATA_PREFERENCE_ICON_URI)) {
            iconResId = activityInfo.icon;
        }
        if (iconResId == 0) {
            return null;
        }
        Icon icon = Icon.createWithResource(activityInfo.packageName, iconResId);
        if (isIconTintable(context)) {
            TypedArray a = context.obtainStyledAttributes(new int[]{16843817});
            int tintColor = a.getColor(0, 0);
            a.recycle();
            icon.setTint(tintColor);
        }
        return icon;
    }

    public boolean isIconTintable(Context context) {
        ensureMetadataNotStale(context);
        Bundle bundle = this.mMetaData;
        if (bundle != null && bundle.containsKey(TileUtils.META_DATA_PREFERENCE_ICON_TINTABLE)) {
            return this.mMetaData.getBoolean(TileUtils.META_DATA_PREFERENCE_ICON_TINTABLE);
        }
        return false;
    }

    private void ensureMetadataNotStale(Context context) {
        PackageManager pm = context.getApplicationContext().getPackageManager();
        try {
            long lastUpdateTime = pm.getPackageInfo(this.mActivityPackage, 128).lastUpdateTime;
            if (lastUpdateTime == this.mLastUpdateTime) {
                return;
            }
            this.mActivityInfo = null;
            getActivityInfo(context);
            this.mLastUpdateTime = lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "Can't find package, probably uninstalled.");
        }
    }

    private ActivityInfo getActivityInfo(Context context) {
        if (this.mActivityInfo == null) {
            PackageManager pm = context.getApplicationContext().getPackageManager();
            Intent intent = new Intent().setClassName(this.mActivityPackage, this.mActivityName);
            List<ResolveInfo> infoList = pm.queryIntentActivities(intent, 128);
            if (infoList != null && !infoList.isEmpty()) {
                this.mActivityInfo = infoList.get(0).activityInfo;
                this.mMetaData = this.mActivityInfo.metaData;
            } else {
                Log.e(TAG, "Cannot find package info for " + intent.getComponent().flattenToString());
            }
        }
        return this.mActivityInfo;
    }

    public boolean isPrimaryProfileOnly() {
        Bundle bundle = this.mMetaData;
        String profile = TileUtils.PROFILE_ALL;
        String profile2 = bundle != null ? bundle.getString(TileUtils.META_DATA_KEY_PROFILE) : TileUtils.PROFILE_ALL;
        if (profile2 != null) {
            profile = profile2;
        }
        return TextUtils.equals(profile, TileUtils.PROFILE_PRIMARY);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ int lambda$static$0(Tile lhs, Tile rhs) {
        return rhs.getOrder() - lhs.getOrder();
    }
}
