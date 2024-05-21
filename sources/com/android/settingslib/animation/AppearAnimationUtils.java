package com.android.settingslib.animation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.view.RenderNodeAnimator;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import androidx.vectordrawable.graphics.drawable.AndroidResources;
import com.android.settingslib.R;
/* loaded from: classes3.dex */
public class AppearAnimationUtils implements AppearAnimationCreator<View> {
    public static final long DEFAULT_APPEAR_DURATION = 220;
    protected boolean mAppearing;
    protected final float mDelayScale;
    private final long mDuration;
    private final Interpolator mInterpolator;
    private final AppearAnimationProperties mProperties;
    protected RowTranslationScaler mRowTranslationScaler;
    private final float mStartTranslation;

    /* loaded from: classes3.dex */
    public interface RowTranslationScaler {
        float getRowTranslationScale(int i, int i2);
    }

    public AppearAnimationUtils(Context ctx) {
        this(ctx, 220L, 1.0f, 1.0f, AnimationUtils.loadInterpolator(ctx, AndroidResources.LINEAR_OUT_SLOW_IN));
    }

    public AppearAnimationUtils(Context ctx, long duration, float translationScaleFactor, float delayScaleFactor, Interpolator interpolator) {
        this.mProperties = new AppearAnimationProperties();
        this.mInterpolator = interpolator;
        this.mStartTranslation = ctx.getResources().getDimensionPixelOffset(R.dimen.appear_y_translation_start) * translationScaleFactor;
        this.mDelayScale = delayScaleFactor;
        this.mDuration = duration;
        this.mAppearing = true;
    }

    public void startAnimation2d(View[][] objects, Runnable finishListener) {
        startAnimation2d(objects, finishListener, this);
    }

    public void startAnimation(View[] objects, Runnable finishListener) {
        startAnimation(objects, finishListener, this);
    }

    public <T> void startAnimation2d(T[][] objects, Runnable finishListener, AppearAnimationCreator<T> creator) {
        AppearAnimationProperties properties = getDelays((Object[][]) objects);
        startAnimations(properties, (Object[][]) objects, finishListener, (AppearAnimationCreator) creator);
    }

    public <T> void startAnimation(T[] objects, Runnable finishListener, AppearAnimationCreator<T> creator) {
        AppearAnimationProperties properties = getDelays(objects);
        startAnimations(properties, objects, finishListener, creator);
    }

    private <T> void startAnimations(AppearAnimationProperties properties, T[] objects, Runnable finishListener, AppearAnimationCreator<T> creator) {
        float f;
        if (properties.maxDelayRowIndex == -1 || properties.maxDelayColIndex == -1) {
            finishListener.run();
            return;
        }
        for (int row = 0; row < properties.delays.length; row++) {
            long[] columns = properties.delays[row];
            long delay = columns[0];
            Runnable endRunnable = null;
            if (properties.maxDelayRowIndex == row && properties.maxDelayColIndex == 0) {
                endRunnable = finishListener;
            }
            RowTranslationScaler rowTranslationScaler = this.mRowTranslationScaler;
            if (rowTranslationScaler != null) {
                f = rowTranslationScaler.getRowTranslationScale(row, properties.delays.length);
            } else {
                f = 1.0f;
            }
            float translationScale = f;
            float translation = translationScale * this.mStartTranslation;
            creator.createAnimation(objects[row], delay, this.mDuration, this.mAppearing ? translation : -translation, this.mAppearing, this.mInterpolator, endRunnable);
        }
    }

    private <T> void startAnimations(AppearAnimationProperties properties, T[][] objects, Runnable finishListener, AppearAnimationCreator<T> creator) {
        float translationScale;
        if (properties.maxDelayRowIndex == -1 || properties.maxDelayColIndex == -1) {
            finishListener.run();
            return;
        }
        for (int row = 0; row < properties.delays.length; row++) {
            long[] columns = properties.delays[row];
            RowTranslationScaler rowTranslationScaler = this.mRowTranslationScaler;
            if (rowTranslationScaler != null) {
                translationScale = rowTranslationScaler.getRowTranslationScale(row, properties.delays.length);
            } else {
                translationScale = 1.0f;
            }
            float translation = this.mStartTranslation * translationScale;
            for (int col = 0; col < columns.length; col++) {
                long delay = columns[col];
                Runnable endRunnable = null;
                if (properties.maxDelayRowIndex == row && properties.maxDelayColIndex == col) {
                    endRunnable = finishListener;
                }
                creator.createAnimation(objects[row][col], delay, this.mDuration, this.mAppearing ? translation : -translation, this.mAppearing, this.mInterpolator, endRunnable);
            }
        }
    }

    private <T> AppearAnimationProperties getDelays(T[] items) {
        long maxDelay = -1;
        AppearAnimationProperties appearAnimationProperties = this.mProperties;
        appearAnimationProperties.maxDelayColIndex = -1;
        appearAnimationProperties.maxDelayRowIndex = -1;
        appearAnimationProperties.delays = new long[items.length];
        for (int row = 0; row < items.length; row++) {
            this.mProperties.delays[row] = new long[1];
            long delay = calculateDelay(row, 0);
            this.mProperties.delays[row][0] = delay;
            if (items[row] != null && delay > maxDelay) {
                maxDelay = delay;
                AppearAnimationProperties appearAnimationProperties2 = this.mProperties;
                appearAnimationProperties2.maxDelayColIndex = 0;
                appearAnimationProperties2.maxDelayRowIndex = row;
            }
        }
        return this.mProperties;
    }

    private <T> AppearAnimationProperties getDelays(T[][] items) {
        long maxDelay = -1;
        AppearAnimationProperties appearAnimationProperties = this.mProperties;
        appearAnimationProperties.maxDelayColIndex = -1;
        appearAnimationProperties.maxDelayRowIndex = -1;
        appearAnimationProperties.delays = new long[items.length];
        for (int row = 0; row < items.length; row++) {
            T[] columns = items[row];
            this.mProperties.delays[row] = new long[columns.length];
            for (int col = 0; col < columns.length; col++) {
                long delay = calculateDelay(row, col);
                this.mProperties.delays[row][col] = delay;
                if (items[row][col] != null && delay > maxDelay) {
                    maxDelay = delay;
                    AppearAnimationProperties appearAnimationProperties2 = this.mProperties;
                    appearAnimationProperties2.maxDelayColIndex = col;
                    appearAnimationProperties2.maxDelayRowIndex = row;
                }
            }
        }
        return this.mProperties;
    }

    protected long calculateDelay(int row, int col) {
        return (long) (((row * 40) + (col * (Math.pow(row, 0.4d) + 0.4d) * 20.0d)) * this.mDelayScale);
    }

    public Interpolator getInterpolator() {
        return this.mInterpolator;
    }

    public float getStartTranslation() {
        return this.mStartTranslation;
    }

    @Override // com.android.settingslib.animation.AppearAnimationCreator
    public void createAnimation(final View view, long delay, long duration, float translationY, boolean appearing, Interpolator interpolator, final Runnable endRunnable) {
        Animator alphaAnim;
        if (view != null) {
            view.setAlpha(appearing ? 0.0f : 1.0f);
            view.setTranslationY(appearing ? translationY : 0.0f);
            float targetAlpha = appearing ? 1.0f : 0.0f;
            if (view.isHardwareAccelerated()) {
                Animator alphaAnim2 = new RenderNodeAnimator(11, targetAlpha);
                alphaAnim2.setTarget(view);
                alphaAnim = alphaAnim2;
            } else {
                alphaAnim = ObjectAnimator.ofFloat(view, View.ALPHA, view.getAlpha(), targetAlpha);
            }
            alphaAnim.setInterpolator(interpolator);
            alphaAnim.setDuration(duration);
            alphaAnim.setStartDelay(delay);
            if (view.hasOverlappingRendering()) {
                view.setLayerType(2, null);
                alphaAnim.addListener(new AnimatorListenerAdapter() { // from class: com.android.settingslib.animation.AppearAnimationUtils.1
                    @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator.AnimatorListener
                    public void onAnimationEnd(Animator animation) {
                        view.setLayerType(0, null);
                    }
                });
            }
            if (endRunnable != null) {
                alphaAnim.addListener(new AnimatorListenerAdapter() { // from class: com.android.settingslib.animation.AppearAnimationUtils.2
                    @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator.AnimatorListener
                    public void onAnimationEnd(Animator animation) {
                        endRunnable.run();
                    }
                });
            }
            alphaAnim.start();
            startTranslationYAnimation(view, delay, duration, appearing ? 0.0f : translationY, interpolator);
        }
    }

    public static void startTranslationYAnimation(View view, long delay, long duration, float endTranslationY, Interpolator interpolator) {
        RenderNodeAnimator translationAnimRt;
        if (view.isHardwareAccelerated()) {
            translationAnimRt = new RenderNodeAnimator(1, endTranslationY);
            translationAnimRt.setTarget(view);
        } else {
            translationAnimRt = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, view.getTranslationY(), endTranslationY);
        }
        translationAnimRt.setInterpolator(interpolator);
        translationAnimRt.setDuration(duration);
        translationAnimRt.setStartDelay(delay);
        translationAnimRt.start();
    }

    /* loaded from: classes3.dex */
    public class AppearAnimationProperties {
        public long[][] delays;
        public int maxDelayColIndex;
        public int maxDelayRowIndex;

        public AppearAnimationProperties() {
        }
    }
}
