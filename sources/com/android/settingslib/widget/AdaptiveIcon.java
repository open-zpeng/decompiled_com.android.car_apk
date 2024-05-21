package com.android.settingslib.widget;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.VisibleForTesting;
import com.android.settingslib.drawer.Tile;
import com.android.settingslib.drawer.TileUtils;
/* loaded from: classes3.dex */
public class AdaptiveIcon extends LayerDrawable {
    private static final String TAG = "AdaptiveHomepageIcon";
    private AdaptiveConstantState mAdaptiveConstantState;
    @VisibleForTesting(otherwise = 5)
    int mBackgroundColor;

    public AdaptiveIcon(Context context, Drawable foreground) {
        super(new Drawable[]{new AdaptiveIconShapeDrawable(context.getResources()), foreground});
        this.mBackgroundColor = -1;
        int insetPx = context.getResources().getDimensionPixelSize(R.dimen.dashboard_tile_foreground_image_inset);
        setLayerInset(1, insetPx, insetPx, insetPx, insetPx);
        this.mAdaptiveConstantState = new AdaptiveConstantState(context, foreground);
    }

    public void setBackgroundColor(Context context, Tile tile) {
        int colorRes;
        Bundle metaData = tile.getMetaData();
        if (metaData != null) {
            try {
                int bgColor = metaData.getInt(TileUtils.META_DATA_PREFERENCE_ICON_BACKGROUND_ARGB, 0);
                if (bgColor == 0 && (colorRes = metaData.getInt(TileUtils.META_DATA_PREFERENCE_ICON_BACKGROUND_HINT, 0)) != 0) {
                    bgColor = context.getPackageManager().getResourcesForApplication(tile.getPackageName()).getColor(colorRes, null);
                }
                if (bgColor != 0) {
                    setBackgroundColor(bgColor);
                    return;
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Failed to set background color for " + tile.getPackageName());
            }
        }
        setBackgroundColor(context.getColor(R.color.homepage_generic_icon_background));
    }

    public void setBackgroundColor(int color) {
        this.mBackgroundColor = color;
        getDrawable(0).setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
        Log.d(TAG, "Setting background color " + this.mBackgroundColor);
        this.mAdaptiveConstantState.mColor = color;
    }

    @Override // android.graphics.drawable.LayerDrawable, android.graphics.drawable.Drawable
    public Drawable.ConstantState getConstantState() {
        return this.mAdaptiveConstantState;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @VisibleForTesting
    /* loaded from: classes3.dex */
    public static class AdaptiveConstantState extends Drawable.ConstantState {
        int mColor;
        Context mContext;
        Drawable mDrawable;

        AdaptiveConstantState(Context context, Drawable drawable) {
            this.mContext = context;
            this.mDrawable = drawable;
        }

        @Override // android.graphics.drawable.Drawable.ConstantState
        public Drawable newDrawable() {
            AdaptiveIcon icon = new AdaptiveIcon(this.mContext, this.mDrawable);
            icon.setBackgroundColor(this.mColor);
            return icon;
        }

        @Override // android.graphics.drawable.Drawable.ConstantState
        public int getChangingConfigurations() {
            return 0;
        }
    }
}
