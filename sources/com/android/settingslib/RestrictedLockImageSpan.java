package com.android.settingslib;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.style.ImageSpan;
/* loaded from: classes3.dex */
public class RestrictedLockImageSpan extends ImageSpan {
    private Context mContext;
    private final float mExtraPadding;
    private final Drawable mRestrictedPadlock;

    public RestrictedLockImageSpan(Context context) {
        super((Drawable) null);
        this.mContext = context;
        this.mExtraPadding = this.mContext.getResources().getDimensionPixelSize(R.dimen.restricted_icon_padding);
        this.mRestrictedPadlock = RestrictedLockUtilsInternal.getRestrictedPadlock(this.mContext);
    }

    @Override // android.text.style.ImageSpan, android.text.style.DynamicDrawableSpan
    public Drawable getDrawable() {
        return this.mRestrictedPadlock;
    }

    @Override // android.text.style.DynamicDrawableSpan, android.text.style.ReplacementSpan
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        Drawable drawable = getDrawable();
        canvas.save();
        float transX = this.mExtraPadding + x;
        float transY = (bottom - drawable.getBounds().bottom) / 2.0f;
        canvas.translate(transX, transY);
        drawable.draw(canvas);
        canvas.restore();
    }

    @Override // android.text.style.DynamicDrawableSpan, android.text.style.ReplacementSpan
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fontMetrics) {
        int size = super.getSize(paint, text, start, end, fontMetrics);
        return (int) (size + (this.mExtraPadding * 2.0f));
    }
}
