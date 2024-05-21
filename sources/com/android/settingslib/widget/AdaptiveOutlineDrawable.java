package com.android.settingslib.widget;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.DrawableWrapper;
import android.util.PathParser;
import androidx.annotation.VisibleForTesting;
/* loaded from: classes3.dex */
public class AdaptiveOutlineDrawable extends DrawableWrapper {
    private final Bitmap mBitmap;
    private final int mInsetPx;
    @VisibleForTesting
    final Paint mOutlinePaint;
    private Path mPath;

    public AdaptiveOutlineDrawable(Resources resources, Bitmap bitmap) {
        super(new AdaptiveIconShapeDrawable(resources));
        getDrawable().setTint(-1);
        this.mPath = new Path(PathParser.createPathFromPathData(resources.getString(17039747)));
        this.mOutlinePaint = new Paint();
        this.mOutlinePaint.setColor(resources.getColor(R.color.bt_outline_color, null));
        this.mOutlinePaint.setStyle(Paint.Style.STROKE);
        this.mOutlinePaint.setStrokeWidth(resources.getDimension(R.dimen.adaptive_outline_stroke));
        this.mOutlinePaint.setAntiAlias(true);
        this.mInsetPx = resources.getDimensionPixelSize(R.dimen.dashboard_tile_foreground_image_inset);
        this.mBitmap = bitmap;
    }

    @Override // android.graphics.drawable.DrawableWrapper, android.graphics.drawable.Drawable
    public void draw(Canvas canvas) {
        super.draw(canvas);
        Rect bounds = getBounds();
        float scaleX = (bounds.right - bounds.left) / 100.0f;
        float scaleY = (bounds.bottom - bounds.top) / 100.0f;
        int count = canvas.save();
        canvas.scale(scaleX, scaleY);
        canvas.drawPath(this.mPath, this.mOutlinePaint);
        canvas.restoreToCount(count);
        canvas.drawBitmap(this.mBitmap, bounds.left + this.mInsetPx, bounds.top + this.mInsetPx, (Paint) null);
    }

    @Override // android.graphics.drawable.DrawableWrapper, android.graphics.drawable.Drawable
    public int getIntrinsicHeight() {
        return this.mBitmap.getHeight() + (this.mInsetPx * 2);
    }

    @Override // android.graphics.drawable.DrawableWrapper, android.graphics.drawable.Drawable
    public int getIntrinsicWidth() {
        return this.mBitmap.getWidth() + (this.mInsetPx * 2);
    }
}
