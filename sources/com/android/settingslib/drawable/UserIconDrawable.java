package com.android.settingslib.drawable;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import com.android.settingslib.R;
/* loaded from: classes3.dex */
public class UserIconDrawable extends Drawable implements Drawable.Callback {
    private Drawable mBadge;
    private float mBadgeMargin;
    private float mBadgeRadius;
    private Bitmap mBitmap;
    private Paint mClearPaint;
    private float mDisplayRadius;
    private ColorStateList mFrameColor;
    private float mFramePadding;
    private Paint mFramePaint;
    private float mFrameWidth;
    private final Matrix mIconMatrix;
    private final Paint mIconPaint;
    private float mIntrinsicRadius;
    private boolean mInvalidated;
    private float mPadding;
    private final Paint mPaint;
    private int mSize;
    private ColorStateList mTintColor;
    private PorterDuff.Mode mTintMode;
    private Drawable mUserDrawable;
    private Bitmap mUserIcon;

    public static Drawable getManagedUserDrawable(Context context) {
        return getDrawableForDisplayDensity(context, 17302377);
    }

    private static Drawable getDrawableForDisplayDensity(Context context, int drawable) {
        int density = context.getResources().getDisplayMetrics().densityDpi;
        return context.getResources().getDrawableForDensity(drawable, density, context.getTheme());
    }

    public static int getSizeForList(Context context) {
        return (int) context.getResources().getDimension(R.dimen.circle_avatar_size);
    }

    public UserIconDrawable() {
        this(0);
    }

    public UserIconDrawable(int intrinsicSize) {
        this.mIconPaint = new Paint();
        this.mPaint = new Paint();
        this.mIconMatrix = new Matrix();
        this.mPadding = 0.0f;
        this.mSize = 0;
        this.mInvalidated = true;
        this.mTintColor = null;
        this.mTintMode = PorterDuff.Mode.SRC_ATOP;
        this.mFrameColor = null;
        this.mIconPaint.setAntiAlias(true);
        this.mIconPaint.setFilterBitmap(true);
        this.mPaint.setFilterBitmap(true);
        this.mPaint.setAntiAlias(true);
        if (intrinsicSize > 0) {
            setBounds(0, 0, intrinsicSize, intrinsicSize);
            setIntrinsicSize(intrinsicSize);
        }
        setIcon(null);
    }

    public UserIconDrawable setIcon(Bitmap icon) {
        Drawable drawable = this.mUserDrawable;
        if (drawable != null) {
            drawable.setCallback(null);
            this.mUserDrawable = null;
        }
        this.mUserIcon = icon;
        if (this.mUserIcon == null) {
            this.mIconPaint.setShader(null);
            this.mBitmap = null;
        } else {
            this.mIconPaint.setShader(new BitmapShader(icon, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        }
        onBoundsChange(getBounds());
        return this;
    }

    public UserIconDrawable setIconDrawable(Drawable icon) {
        Drawable drawable = this.mUserDrawable;
        if (drawable != null) {
            drawable.setCallback(null);
        }
        this.mUserIcon = null;
        this.mUserDrawable = icon;
        Drawable drawable2 = this.mUserDrawable;
        if (drawable2 == null) {
            this.mBitmap = null;
        } else {
            drawable2.setCallback(this);
        }
        onBoundsChange(getBounds());
        return this;
    }

    public UserIconDrawable setBadge(Drawable badge) {
        this.mBadge = badge;
        if (this.mBadge != null) {
            if (this.mClearPaint == null) {
                this.mClearPaint = new Paint();
                this.mClearPaint.setAntiAlias(true);
                this.mClearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                this.mClearPaint.setStyle(Paint.Style.FILL);
            }
            onBoundsChange(getBounds());
        } else {
            invalidateSelf();
        }
        return this;
    }

    public UserIconDrawable setBadgeIfManagedUser(Context context, int userId) {
        Drawable badge = null;
        if (userId != -10000) {
            boolean isManaged = ((DevicePolicyManager) context.getSystemService(DevicePolicyManager.class)).getProfileOwnerAsUser(userId) != null;
            if (isManaged) {
                badge = getDrawableForDisplayDensity(context, 17302368);
            }
        }
        return setBadge(badge);
    }

    public void setBadgeRadius(float radius) {
        this.mBadgeRadius = radius;
        onBoundsChange(getBounds());
    }

    public void setBadgeMargin(float margin) {
        this.mBadgeMargin = margin;
        onBoundsChange(getBounds());
    }

    public void setPadding(float padding) {
        this.mPadding = padding;
        onBoundsChange(getBounds());
    }

    private void initFramePaint() {
        if (this.mFramePaint == null) {
            this.mFramePaint = new Paint();
            this.mFramePaint.setStyle(Paint.Style.STROKE);
            this.mFramePaint.setAntiAlias(true);
        }
    }

    public void setFrameWidth(float width) {
        initFramePaint();
        this.mFrameWidth = width;
        this.mFramePaint.setStrokeWidth(width);
        onBoundsChange(getBounds());
    }

    public void setFramePadding(float padding) {
        initFramePaint();
        this.mFramePadding = padding;
        onBoundsChange(getBounds());
    }

    public void setFrameColor(int color) {
        initFramePaint();
        this.mFramePaint.setColor(color);
        invalidateSelf();
    }

    public void setFrameColor(ColorStateList colorList) {
        initFramePaint();
        this.mFrameColor = colorList;
        invalidateSelf();
    }

    public void setIntrinsicSize(int size) {
        this.mSize = size;
    }

    @Override // android.graphics.drawable.Drawable
    public void draw(Canvas canvas) {
        if (this.mInvalidated) {
            rebake();
        }
        if (this.mBitmap != null) {
            ColorStateList colorStateList = this.mTintColor;
            if (colorStateList == null) {
                this.mPaint.setColorFilter(null);
            } else {
                int color = colorStateList.getColorForState(getState(), this.mTintColor.getDefaultColor());
                if (shouldUpdateColorFilter(color, this.mTintMode)) {
                    this.mPaint.setColorFilter(new PorterDuffColorFilter(color, this.mTintMode));
                }
            }
            canvas.drawBitmap(this.mBitmap, 0.0f, 0.0f, this.mPaint);
        }
    }

    private boolean shouldUpdateColorFilter(int color, PorterDuff.Mode mode) {
        ColorFilter colorFilter = this.mPaint.getColorFilter();
        if (colorFilter instanceof PorterDuffColorFilter) {
            PorterDuffColorFilter porterDuffColorFilter = (PorterDuffColorFilter) colorFilter;
            int currentColor = porterDuffColorFilter.getColor();
            PorterDuff.Mode currentMode = porterDuffColorFilter.getMode();
            return (currentColor == color && currentMode == mode) ? false : true;
        }
        return true;
    }

    @Override // android.graphics.drawable.Drawable
    public void setAlpha(int alpha) {
        this.mPaint.setAlpha(alpha);
        super.invalidateSelf();
    }

    @Override // android.graphics.drawable.Drawable
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override // android.graphics.drawable.Drawable
    public void setTintList(ColorStateList tintList) {
        this.mTintColor = tintList;
        super.invalidateSelf();
    }

    @Override // android.graphics.drawable.Drawable
    public void setTintMode(PorterDuff.Mode mode) {
        this.mTintMode = mode;
        super.invalidateSelf();
    }

    @Override // android.graphics.drawable.Drawable
    public Drawable.ConstantState getConstantState() {
        return new BitmapDrawable(this.mBitmap).getConstantState();
    }

    public UserIconDrawable bake() {
        int i = this.mSize;
        if (i <= 0) {
            throw new IllegalStateException("Baking requires an explicit intrinsic size");
        }
        onBoundsChange(new Rect(0, 0, i, i));
        rebake();
        this.mFrameColor = null;
        this.mFramePaint = null;
        this.mClearPaint = null;
        Drawable drawable = this.mUserDrawable;
        if (drawable != null) {
            drawable.setCallback(null);
            this.mUserDrawable = null;
        } else {
            Bitmap bitmap = this.mUserIcon;
            if (bitmap != null) {
                bitmap.recycle();
                this.mUserIcon = null;
            }
        }
        return this;
    }

    private void rebake() {
        this.mInvalidated = false;
        if (this.mBitmap != null) {
            if (this.mUserDrawable == null && this.mUserIcon == null) {
                return;
            }
            Canvas canvas = new Canvas(this.mBitmap);
            canvas.drawColor(0, PorterDuff.Mode.CLEAR);
            Drawable drawable = this.mUserDrawable;
            if (drawable != null) {
                drawable.draw(canvas);
            } else if (this.mUserIcon != null) {
                int saveId = canvas.save();
                canvas.concat(this.mIconMatrix);
                canvas.drawCircle(this.mUserIcon.getWidth() * 0.5f, this.mUserIcon.getHeight() * 0.5f, this.mIntrinsicRadius, this.mIconPaint);
                canvas.restoreToCount(saveId);
            }
            ColorStateList colorStateList = this.mFrameColor;
            if (colorStateList != null) {
                this.mFramePaint.setColor(colorStateList.getColorForState(getState(), 0));
            }
            float f = this.mFrameWidth;
            if (this.mFramePadding + f > 0.001f) {
                float radius = (this.mDisplayRadius - this.mPadding) - (f * 0.5f);
                canvas.drawCircle(getBounds().exactCenterX(), getBounds().exactCenterY(), radius, this.mFramePaint);
            }
            if (this.mBadge != null) {
                float f2 = this.mBadgeRadius;
                if (f2 > 0.001f) {
                    float badgeDiameter = f2 * 2.0f;
                    float badgeTop = this.mBitmap.getHeight() - badgeDiameter;
                    float badgeLeft = this.mBitmap.getWidth() - badgeDiameter;
                    this.mBadge.setBounds((int) badgeLeft, (int) badgeTop, (int) (badgeLeft + badgeDiameter), (int) (badgeTop + badgeDiameter));
                    float borderRadius = (this.mBadge.getBounds().width() * 0.5f) + this.mBadgeMargin;
                    float f3 = this.mBadgeRadius;
                    canvas.drawCircle(badgeLeft + f3, f3 + badgeTop, borderRadius, this.mClearPaint);
                    this.mBadge.draw(canvas);
                }
            }
        }
    }

    @Override // android.graphics.drawable.Drawable
    protected void onBoundsChange(Rect bounds) {
        if (bounds.isEmpty()) {
            return;
        }
        if (this.mUserIcon == null && this.mUserDrawable == null) {
            return;
        }
        float newDisplayRadius = Math.min(bounds.width(), bounds.height()) * 0.5f;
        int size = (int) (newDisplayRadius * 2.0f);
        if (this.mBitmap == null || size != ((int) (this.mDisplayRadius * 2.0f))) {
            this.mDisplayRadius = newDisplayRadius;
            Bitmap bitmap = this.mBitmap;
            if (bitmap != null) {
                bitmap.recycle();
            }
            this.mBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        }
        this.mDisplayRadius = Math.min(bounds.width(), bounds.height()) * 0.5f;
        float iconRadius = ((this.mDisplayRadius - this.mFrameWidth) - this.mFramePadding) - this.mPadding;
        RectF dstRect = new RectF(bounds.exactCenterX() - iconRadius, bounds.exactCenterY() - iconRadius, bounds.exactCenterX() + iconRadius, bounds.exactCenterY() + iconRadius);
        if (this.mUserDrawable != null) {
            Rect rounded = new Rect();
            dstRect.round(rounded);
            this.mIntrinsicRadius = Math.min(this.mUserDrawable.getIntrinsicWidth(), this.mUserDrawable.getIntrinsicHeight()) * 0.5f;
            this.mUserDrawable.setBounds(rounded);
        } else {
            Bitmap bitmap2 = this.mUserIcon;
            if (bitmap2 != null) {
                float iconCX = bitmap2.getWidth() * 0.5f;
                float iconCY = this.mUserIcon.getHeight() * 0.5f;
                this.mIntrinsicRadius = Math.min(iconCX, iconCY);
                float f = this.mIntrinsicRadius;
                RectF srcRect = new RectF(iconCX - f, iconCY - f, iconCX + f, f + iconCY);
                this.mIconMatrix.setRectToRect(srcRect, dstRect, Matrix.ScaleToFit.FILL);
            }
        }
        invalidateSelf();
    }

    @Override // android.graphics.drawable.Drawable
    public void invalidateSelf() {
        super.invalidateSelf();
        this.mInvalidated = true;
    }

    @Override // android.graphics.drawable.Drawable
    public boolean isStateful() {
        ColorStateList colorStateList = this.mFrameColor;
        return colorStateList != null && colorStateList.isStateful();
    }

    @Override // android.graphics.drawable.Drawable
    public int getOpacity() {
        return -3;
    }

    @Override // android.graphics.drawable.Drawable
    public int getIntrinsicWidth() {
        int i = this.mSize;
        return i <= 0 ? ((int) this.mIntrinsicRadius) * 2 : i;
    }

    @Override // android.graphics.drawable.Drawable
    public int getIntrinsicHeight() {
        return getIntrinsicWidth();
    }

    @Override // android.graphics.drawable.Drawable.Callback
    public void invalidateDrawable(Drawable who) {
        invalidateSelf();
    }

    @Override // android.graphics.drawable.Drawable.Callback
    public void scheduleDrawable(Drawable who, Runnable what, long when) {
        scheduleSelf(what, when);
    }

    @Override // android.graphics.drawable.Drawable.Callback
    public void unscheduleDrawable(Drawable who, Runnable what) {
        unscheduleSelf(what);
    }
}
