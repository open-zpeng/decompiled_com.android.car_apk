package com.android.settingslib.animation;

import android.content.Context;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import androidx.vectordrawable.graphics.drawable.AndroidResources;
import com.android.settingslib.animation.AppearAnimationUtils;
/* loaded from: classes3.dex */
public class DisappearAnimationUtils extends AppearAnimationUtils {
    private static final AppearAnimationUtils.RowTranslationScaler ROW_TRANSLATION_SCALER = new AppearAnimationUtils.RowTranslationScaler() { // from class: com.android.settingslib.animation.DisappearAnimationUtils.1
        @Override // com.android.settingslib.animation.AppearAnimationUtils.RowTranslationScaler
        public float getRowTranslationScale(int row, int numRows) {
            return (float) (Math.pow(numRows - row, 2.0d) / numRows);
        }
    };

    public DisappearAnimationUtils(Context ctx) {
        this(ctx, 220L, 1.0f, 1.0f, AnimationUtils.loadInterpolator(ctx, AndroidResources.FAST_OUT_LINEAR_IN));
    }

    public DisappearAnimationUtils(Context ctx, long duration, float translationScaleFactor, float delayScaleFactor, Interpolator interpolator) {
        this(ctx, duration, translationScaleFactor, delayScaleFactor, interpolator, ROW_TRANSLATION_SCALER);
    }

    public DisappearAnimationUtils(Context ctx, long duration, float translationScaleFactor, float delayScaleFactor, Interpolator interpolator, AppearAnimationUtils.RowTranslationScaler rowScaler) {
        super(ctx, duration, translationScaleFactor, delayScaleFactor, interpolator);
        this.mRowTranslationScaler = rowScaler;
        this.mAppearing = false;
    }

    @Override // com.android.settingslib.animation.AppearAnimationUtils
    protected long calculateDelay(int row, int col) {
        return (long) (((row * 60) + (col * (Math.pow(row, 0.4d) + 0.4d) * 10.0d)) * this.mDelayScale);
    }
}
