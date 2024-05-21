package com.android.settingslib.graph;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import androidx.annotation.VisibleForTesting;
import com.android.settingslib.R;
import com.android.settingslib.Utils;
/* loaded from: classes3.dex */
public class BluetoothDeviceLayerDrawable extends LayerDrawable {
    private BluetoothDeviceLayerDrawableState mState;

    private BluetoothDeviceLayerDrawable(Drawable[] layers) {
        super(layers);
    }

    public static BluetoothDeviceLayerDrawable createLayerDrawable(Context context, int resId, int batteryLevel) {
        return createLayerDrawable(context, resId, batteryLevel, 1.0f);
    }

    public static BluetoothDeviceLayerDrawable createLayerDrawable(Context context, int resId, int batteryLevel, float iconScale) {
        Drawable deviceDrawable = context.getDrawable(resId);
        BatteryMeterDrawable batteryDrawable = new BatteryMeterDrawable(context, context.getColor(R.color.meter_background_color), batteryLevel);
        int pad = context.getResources().getDimensionPixelSize(R.dimen.bt_battery_padding);
        batteryDrawable.setPadding(pad, pad, pad, pad);
        BluetoothDeviceLayerDrawable drawable = new BluetoothDeviceLayerDrawable(new Drawable[]{deviceDrawable, batteryDrawable});
        drawable.setLayerGravity(0, 8388611);
        drawable.setLayerInsetStart(1, deviceDrawable.getIntrinsicWidth());
        drawable.setLayerInsetTop(1, (int) (deviceDrawable.getIntrinsicHeight() * (1.0f - iconScale)));
        drawable.setConstantState(context, resId, batteryLevel, iconScale);
        return drawable;
    }

    public void setConstantState(Context context, int resId, int batteryLevel, float iconScale) {
        this.mState = new BluetoothDeviceLayerDrawableState(context, resId, batteryLevel, iconScale);
    }

    @Override // android.graphics.drawable.LayerDrawable, android.graphics.drawable.Drawable
    public Drawable.ConstantState getConstantState() {
        return this.mState;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @VisibleForTesting
    /* loaded from: classes3.dex */
    public static class BatteryMeterDrawable extends BatteryMeterDrawableBase {
        private final float mAspectRatio;
        @VisibleForTesting
        int mFrameColor;

        public BatteryMeterDrawable(Context context, int frameColor, int batteryLevel) {
            super(context, frameColor);
            Resources resources = context.getResources();
            this.mButtonHeightFraction = resources.getFraction(R.fraction.bt_battery_button_height_fraction, 1, 1);
            this.mAspectRatio = resources.getFraction(R.fraction.bt_battery_ratio_fraction, 1, 1);
            int tintColor = Utils.getColorAttrDefaultColor(context, 16843817);
            setColorFilter(new PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN));
            setBatteryLevel(batteryLevel);
            this.mFrameColor = frameColor;
        }

        @Override // com.android.settingslib.graph.BatteryMeterDrawableBase
        protected float getAspectRatio() {
            return this.mAspectRatio;
        }

        @Override // com.android.settingslib.graph.BatteryMeterDrawableBase
        protected float getRadiusRatio() {
            return 0.0f;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static class BluetoothDeviceLayerDrawableState extends Drawable.ConstantState {
        int batteryLevel;
        Context context;
        float iconScale;
        int resId;

        public BluetoothDeviceLayerDrawableState(Context context, int resId, int batteryLevel, float iconScale) {
            this.context = context;
            this.resId = resId;
            this.batteryLevel = batteryLevel;
            this.iconScale = iconScale;
        }

        @Override // android.graphics.drawable.Drawable.ConstantState
        public Drawable newDrawable() {
            return BluetoothDeviceLayerDrawable.createLayerDrawable(this.context, this.resId, this.batteryLevel, this.iconScale);
        }

        @Override // android.graphics.drawable.Drawable.ConstantState
        public int getChangingConfigurations() {
            return 0;
        }
    }
}
