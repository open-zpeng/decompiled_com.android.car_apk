package com.android.settingslib.utils;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import androidx.annotation.VisibleForTesting;
import androidx.collection.ArrayMap;
/* loaded from: classes3.dex */
public class IconCache {
    private final Context mContext;
    @VisibleForTesting
    final ArrayMap<Icon, Drawable> mMap = new ArrayMap<>();

    public IconCache(Context context) {
        this.mContext = context;
    }

    public Drawable getIcon(Icon icon) {
        if (icon == null) {
            return null;
        }
        Drawable drawable = this.mMap.get(icon);
        if (drawable == null) {
            Drawable drawable2 = icon.loadDrawable(this.mContext);
            updateIcon(icon, drawable2);
            return drawable2;
        }
        return drawable;
    }

    public void updateIcon(Icon icon, Drawable drawable) {
        this.mMap.put(icon, drawable);
    }
}
