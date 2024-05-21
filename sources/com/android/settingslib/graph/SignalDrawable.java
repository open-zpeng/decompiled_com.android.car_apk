package com.android.settingslib.graph;

import android.animation.ArgbEvaluator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.DrawableWrapper;
import android.os.Handler;
import android.util.PathParser;
import com.android.settingslib.R;
import com.android.settingslib.Utils;
/* loaded from: classes3.dex */
public class SignalDrawable extends DrawableWrapper {
    private static final long DOT_DELAY = 1000;
    private static final float DOT_PADDING = 0.0625f;
    private static final float DOT_SIZE = 0.125f;
    private static final int LEVEL_MASK = 255;
    private static final int NUM_DOTS = 3;
    private static final int NUM_LEVEL_MASK = 65280;
    private static final int NUM_LEVEL_SHIFT = 8;
    private static final float PAD = 0.083333336f;
    private static final int STATE_CARRIER_CHANGE = 3;
    private static final int STATE_CUT = 2;
    private static final int STATE_MASK = 16711680;
    private static final int STATE_SHIFT = 16;
    private static final String TAG = "SignalDrawable";
    private static final float VIEWPORT = 24.0f;
    private boolean mAnimating;
    private final Runnable mChangeDot;
    private int mCurrentDot;
    private final float mCutoutHeightFraction;
    private final Path mCutoutPath;
    private final float mCutoutWidthFraction;
    private float mDarkIntensity;
    private final int mDarkModeFillColor;
    private final Paint mForegroundPaint;
    private final Path mForegroundPath;
    private final Handler mHandler;
    private final int mIntrinsicSize;
    private final int mLightModeFillColor;
    private final Path mScaledXPath;
    private final Paint mTransparentPaint;
    private final Path mXPath;
    private final Matrix mXScaleMatrix;

    static /* synthetic */ int access$004(SignalDrawable x0) {
        int i = x0.mCurrentDot + 1;
        x0.mCurrentDot = i;
        return i;
    }

    public SignalDrawable(Context context) {
        super(context.getDrawable(17302806));
        this.mForegroundPaint = new Paint(1);
        this.mTransparentPaint = new Paint(1);
        this.mCutoutPath = new Path();
        this.mForegroundPath = new Path();
        this.mXPath = new Path();
        this.mXScaleMatrix = new Matrix();
        this.mScaledXPath = new Path();
        this.mDarkIntensity = -1.0f;
        this.mChangeDot = new Runnable() { // from class: com.android.settingslib.graph.SignalDrawable.1
            @Override // java.lang.Runnable
            public void run() {
                if (SignalDrawable.access$004(SignalDrawable.this) == 3) {
                    SignalDrawable.this.mCurrentDot = 0;
                }
                SignalDrawable.this.invalidateSelf();
                SignalDrawable.this.mHandler.postDelayed(SignalDrawable.this.mChangeDot, SignalDrawable.DOT_DELAY);
            }
        };
        String xPathString = context.getString(17039776);
        this.mXPath.set(PathParser.createPathFromPathData(xPathString));
        updateScaledXPath();
        this.mCutoutWidthFraction = context.getResources().getFloat(17105079);
        this.mCutoutHeightFraction = context.getResources().getFloat(17105078);
        this.mDarkModeFillColor = Utils.getColorStateListDefaultColor(context, R.color.dark_mode_icon_color_single_tone);
        this.mLightModeFillColor = Utils.getColorStateListDefaultColor(context, R.color.light_mode_icon_color_single_tone);
        this.mIntrinsicSize = context.getResources().getDimensionPixelSize(R.dimen.signal_icon_size);
        this.mTransparentPaint.setColor(context.getColor(17170445));
        this.mTransparentPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        this.mHandler = new Handler();
        setDarkIntensity(0.0f);
    }

    private void updateScaledXPath() {
        if (getBounds().isEmpty()) {
            this.mXScaleMatrix.setScale(1.0f, 1.0f);
        } else {
            this.mXScaleMatrix.setScale(getBounds().width() / VIEWPORT, getBounds().height() / VIEWPORT);
        }
        this.mXPath.transform(this.mXScaleMatrix, this.mScaledXPath);
    }

    @Override // android.graphics.drawable.DrawableWrapper, android.graphics.drawable.Drawable
    public int getIntrinsicWidth() {
        return this.mIntrinsicSize;
    }

    @Override // android.graphics.drawable.DrawableWrapper, android.graphics.drawable.Drawable
    public int getIntrinsicHeight() {
        return this.mIntrinsicSize;
    }

    private void updateAnimation() {
        boolean shouldAnimate = isInState(3) && isVisible();
        if (shouldAnimate == this.mAnimating) {
            return;
        }
        this.mAnimating = shouldAnimate;
        if (shouldAnimate) {
            this.mChangeDot.run();
        } else {
            this.mHandler.removeCallbacks(this.mChangeDot);
        }
    }

    @Override // android.graphics.drawable.DrawableWrapper, android.graphics.drawable.Drawable
    protected boolean onLevelChange(int packedState) {
        super.onLevelChange(unpackLevel(packedState));
        updateAnimation();
        setTintList(ColorStateList.valueOf(this.mForegroundPaint.getColor()));
        invalidateSelf();
        return true;
    }

    private int unpackLevel(int packedState) {
        int numBins = (65280 & packedState) >> 8;
        int levelOffset = numBins == 6 ? 10 : 0;
        int level = packedState & 255;
        return level + levelOffset;
    }

    public void setDarkIntensity(float darkIntensity) {
        if (darkIntensity == this.mDarkIntensity) {
            return;
        }
        setTintList(ColorStateList.valueOf(getFillColor(darkIntensity)));
    }

    @Override // android.graphics.drawable.DrawableWrapper, android.graphics.drawable.Drawable
    public void setTintList(ColorStateList tint) {
        super.setTintList(tint);
        int colorForeground = this.mForegroundPaint.getColor();
        this.mForegroundPaint.setColor(tint.getDefaultColor());
        if (colorForeground != this.mForegroundPaint.getColor()) {
            invalidateSelf();
        }
    }

    private int getFillColor(float darkIntensity) {
        return getColorForDarkIntensity(darkIntensity, this.mLightModeFillColor, this.mDarkModeFillColor);
    }

    private int getColorForDarkIntensity(float darkIntensity, int lightColor, int darkColor) {
        return ((Integer) ArgbEvaluator.getInstance().evaluate(darkIntensity, Integer.valueOf(lightColor), Integer.valueOf(darkColor))).intValue();
    }

    @Override // android.graphics.drawable.DrawableWrapper, android.graphics.drawable.Drawable
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        updateScaledXPath();
        invalidateSelf();
    }

    @Override // android.graphics.drawable.DrawableWrapper, android.graphics.drawable.Drawable
    public void draw(Canvas canvas) {
        canvas.saveLayer(null, null);
        float width = getBounds().width();
        float height = getBounds().height();
        boolean isRtl = getLayoutDirection() == 1;
        if (isRtl) {
            canvas.save();
            canvas.translate(width, 0.0f);
            canvas.scale(-1.0f, 1.0f);
        }
        super.draw(canvas);
        this.mCutoutPath.reset();
        this.mCutoutPath.setFillType(Path.FillType.WINDING);
        float padding = Math.round(PAD * width);
        if (!isInState(3)) {
            if (isInState(2)) {
                float cutX = (this.mCutoutWidthFraction * width) / VIEWPORT;
                float cutY = (this.mCutoutHeightFraction * height) / VIEWPORT;
                this.mCutoutPath.moveTo(width, height);
                this.mCutoutPath.rLineTo(-cutX, 0.0f);
                this.mCutoutPath.rLineTo(0.0f, -cutY);
                this.mCutoutPath.rLineTo(cutX, 0.0f);
                this.mCutoutPath.rLineTo(0.0f, cutY);
                canvas.drawPath(this.mCutoutPath, this.mTransparentPaint);
                canvas.drawPath(this.mScaledXPath, this.mForegroundPaint);
            }
        } else {
            float dotSize = height * DOT_SIZE;
            float dotPadding = height * DOT_PADDING;
            float dotSpacing = dotPadding + dotSize;
            float x = (width - padding) - dotSize;
            float y = (height - padding) - dotSize;
            this.mForegroundPath.reset();
            drawDotAndPadding(x, y, dotPadding, dotSize, 2);
            drawDotAndPadding(x - dotSpacing, y, dotPadding, dotSize, 1);
            drawDotAndPadding(x - (2.0f * dotSpacing), y, dotPadding, dotSize, 0);
            canvas.drawPath(this.mCutoutPath, this.mTransparentPaint);
            canvas.drawPath(this.mForegroundPath, this.mForegroundPaint);
        }
        if (isRtl) {
            canvas.restore();
        }
        canvas.restore();
    }

    private void drawDotAndPadding(float x, float y, float dotPadding, float dotSize, int i) {
        if (i == this.mCurrentDot) {
            this.mForegroundPath.addRect(x, y, x + dotSize, y + dotSize, Path.Direction.CW);
            this.mCutoutPath.addRect(x - dotPadding, y - dotPadding, x + dotSize + dotPadding, y + dotSize + dotPadding, Path.Direction.CW);
        }
    }

    @Override // android.graphics.drawable.DrawableWrapper, android.graphics.drawable.Drawable
    public void setAlpha(int alpha) {
        super.setAlpha(alpha);
        this.mForegroundPaint.setAlpha(alpha);
    }

    @Override // android.graphics.drawable.DrawableWrapper, android.graphics.drawable.Drawable
    public void setColorFilter(ColorFilter colorFilter) {
        super.setColorFilter(colorFilter);
        this.mForegroundPaint.setColorFilter(colorFilter);
    }

    @Override // android.graphics.drawable.DrawableWrapper, android.graphics.drawable.Drawable
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        updateAnimation();
        return changed;
    }

    private boolean isInState(int state) {
        return getState(getLevel()) == state;
    }

    public static int getState(int fullState) {
        return (16711680 & fullState) >> 16;
    }

    public static int getState(int level, int numLevels, boolean cutOut) {
        return ((cutOut ? 2 : 0) << 16) | (numLevels << 8) | level;
    }

    public static int getEmptyState(int numLevels) {
        return getState(0, numLevels, true);
    }

    public static int getCarrierChangeState(int numLevels) {
        return (numLevels << 8) | 196608;
    }
}
