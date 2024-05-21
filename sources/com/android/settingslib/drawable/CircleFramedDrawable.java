package com.android.settingslib.drawable;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import androidx.core.view.ViewCompat;
import com.android.settingslib.R;
/* loaded from: classes3.dex */
public class CircleFramedDrawable extends Drawable {
    private final Bitmap mBitmap;
    private RectF mDstRect;
    private final Paint mPaint;
    private float mScale;
    private final int mSize;
    private Rect mSrcRect;

    public static CircleFramedDrawable getInstance(Context context, Bitmap icon) {
        Resources res = context.getResources();
        float iconSize = res.getDimension(R.dimen.circle_avatar_size);
        CircleFramedDrawable instance = new CircleFramedDrawable(icon, (int) iconSize);
        return instance;
    }

    public CircleFramedDrawable(Bitmap icon, int size) {
        this.mSize = size;
        int i = this.mSize;
        this.mBitmap = Bitmap.createBitmap(i, i, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(this.mBitmap);
        int width = icon.getWidth();
        int height = icon.getHeight();
        int square = Math.min(width, height);
        Rect cropRect = new Rect((width - square) / 2, (height - square) / 2, square, square);
        int i2 = this.mSize;
        RectF circleRect = new RectF(0.0f, 0.0f, i2, i2);
        Path fillPath = new Path();
        fillPath.addArc(circleRect, 0.0f, 360.0f);
        canvas.drawColor(0, PorterDuff.Mode.CLEAR);
        this.mPaint = new Paint();
        this.mPaint.setAntiAlias(true);
        this.mPaint.setColor(ViewCompat.MEASURED_STATE_MASK);
        this.mPaint.setStyle(Paint.Style.FILL);
        canvas.drawPath(fillPath, this.mPaint);
        this.mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(icon, cropRect, circleRect, this.mPaint);
        this.mPaint.setXfermode(null);
        this.mScale = 1.0f;
        int i3 = this.mSize;
        this.mSrcRect = new Rect(0, 0, i3, i3);
        int i4 = this.mSize;
        this.mDstRect = new RectF(0.0f, 0.0f, i4, i4);
    }

    @Override // android.graphics.drawable.Drawable
    public void draw(Canvas canvas) {
        float f = this.mScale;
        int i = this.mSize;
        float inside = f * i;
        float pad = (i - inside) / 2.0f;
        this.mDstRect.set(pad, pad, i - pad, i - pad);
        canvas.drawBitmap(this.mBitmap, this.mSrcRect, this.mDstRect, (Paint) null);
    }

    public void setScale(float scale) {
        this.mScale = scale;
    }

    public float getScale() {
        return this.mScale;
    }

    @Override // android.graphics.drawable.Drawable
    public int getOpacity() {
        return -3;
    }

    @Override // android.graphics.drawable.Drawable
    public void setAlpha(int alpha) {
    }

    @Override // android.graphics.drawable.Drawable
    public void setColorFilter(ColorFilter cf) {
    }

    @Override // android.graphics.drawable.Drawable
    public int getIntrinsicWidth() {
        return this.mSize;
    }

    @Override // android.graphics.drawable.Drawable
    public int getIntrinsicHeight() {
        return this.mSize;
    }
}
