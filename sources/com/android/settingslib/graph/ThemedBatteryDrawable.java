package com.android.settingslib.graph;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.PathParser;
import com.android.settingslib.R;
import com.android.settingslib.Utils;
import com.android.settingslib.datetime.ZoneGetter;
import kotlin.Metadata;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
/* compiled from: ThemedBatteryDrawable.kt */
@Metadata(bv = {1, 0, 3}, d1 = {"\u0000j\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0006\n\u0002\u0010\u0015\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0010\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\n\n\u0002\u0018\u0002\n\u0002\b\u000f\n\u0002\u0018\u0002\n\u0002\b\f\b\u0016\u0018\u0000 \\2\u00020\u0001:\u0001\\B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005¢\u0006\u0002\u0010\u0006J\u0010\u0010=\u001a\u00020\u00052\u0006\u0010>\u001a\u00020\u0005H\u0002J\u0010\u0010?\u001a\u00020)2\u0006\u0010@\u001a\u00020AH\u0016J\u0006\u0010B\u001a\u00020\u0005J\u0010\u0010C\u001a\u00020\u00052\u0006\u0010>\u001a\u00020\u0005H\u0002J\b\u0010D\u001a\u00020\u0005H\u0016J\b\u0010E\u001a\u00020\u0005H\u0016J\b\u0010F\u001a\u00020\u0005H\u0016J\b\u0010G\u001a\u00020)H\u0002J\u0012\u0010H\u001a\u00020)2\b\u0010I\u001a\u0004\u0018\u00010/H\u0014J\b\u0010J\u001a\u00020)H\u0002J\u0010\u0010K\u001a\u00020)2\u0006\u0010L\u001a\u00020\u0005H\u0016J\u0010\u0010M\u001a\u00020)2\u0006\u0010N\u001a\u00020\u0005H\u0016J\u0012\u0010O\u001a\u00020)2\b\u0010P\u001a\u0004\u0018\u00010QH\u0016J\u001e\u0010R\u001a\u00020)2\u0006\u0010S\u001a\u00020\u00052\u0006\u0010T\u001a\u00020\u00052\u0006\u0010U\u001a\u00020\u0005J&\u0010V\u001a\u00020)2\u0006\u0010W\u001a\u00020\u00052\u0006\u0010X\u001a\u00020\u00052\u0006\u0010Y\u001a\u00020\u00052\u0006\u0010Z\u001a\u00020\u0005J\b\u0010[\u001a\u00020)H\u0002R\u000e\u0010\u0007\u001a\u00020\u0005X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0005X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\nX\u0082\u0004¢\u0006\u0002\n\u0000R$\u0010\r\u001a\u00020\f2\u0006\u0010\u000b\u001a\u00020\f@FX\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u000e\u0010\u000f\"\u0004\b\u0010\u0010\u0011R\u000e\u0010\u0012\u001a\u00020\u0013X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004¢\u0006\u0002\n\u0000R\u001a\u0010\u0014\u001a\u00020\u0005X\u0096\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u0015\u0010\u0016\"\u0004\b\u0017\u0010\u0018R\u000e\u0010\u0019\u001a\u00020\fX\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u001a\u001a\u00020\u001bX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\u001c\u001a\u00020\u001bX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\u001d\u001a\u00020\nX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\u001e\u001a\u00020\u0005X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u001f\u001a\u00020\u001bX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010 \u001a\u00020\u001bX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010!\u001a\u00020\nX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\"\u001a\u00020\u001bX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010#\u001a\u00020$X\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010%\u001a\u00020\u0005X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010&\u001a\u00020\u0005X\u0082\u000e¢\u0006\u0002\n\u0000R\u0014\u0010'\u001a\b\u0012\u0004\u0012\u00020)0(X\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010*\u001a\u00020\fX\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010+\u001a\u00020\u0005X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010,\u001a\u00020\nX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010-\u001a\u00020$X\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010.\u001a\u00020/X\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u00100\u001a\u00020\nX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u00101\u001a\u00020\nX\u0082\u0004¢\u0006\u0002\n\u0000R$\u00102\u001a\u00020\f2\u0006\u0010\u000b\u001a\u00020\f@FX\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b3\u0010\u000f\"\u0004\b4\u0010\u0011R\u000e\u00105\u001a\u000206X\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u00107\u001a\u00020\nX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u00108\u001a\u00020\nX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u00109\u001a\u00020\nX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010:\u001a\u00020\nX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010;\u001a\u00020\nX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010<\u001a\u00020\nX\u0082\u0004¢\u0006\u0002\n\u0000¨\u0006]"}, d2 = {"Lcom/android/settingslib/graph/ThemedBatteryDrawable;", "Landroid/graphics/drawable/Drawable;", "context", "Landroid/content/Context;", "frameColor", "", "(Landroid/content/Context;I)V", "backgroundColor", "batteryLevel", "boltPath", "Landroid/graphics/Path;", "value", "", "charging", "getCharging", "()Z", "setCharging", "(Z)V", "colorLevels", "", "criticalLevel", "getCriticalLevel", "()I", "setCriticalLevel", "(I)V", "dualTone", "dualToneBackgroundFill", "Landroid/graphics/Paint;", "errorPaint", "errorPerimeterPath", "fillColor", "fillColorStrokePaint", "fillColorStrokeProtection", "fillMask", "fillPaint", "fillRect", "Landroid/graphics/RectF;", "intrinsicHeight", "intrinsicWidth", "invalidateRunnable", "Lkotlin/Function0;", "", "invertFillIcon", "levelColor", "levelPath", "levelRect", "padding", "Landroid/graphics/Rect;", "perimeterPath", "plusPath", "powerSaveEnabled", "getPowerSaveEnabled", "setPowerSaveEnabled", "scaleMatrix", "Landroid/graphics/Matrix;", "scaledBolt", "scaledErrorPerimeter", "scaledFill", "scaledPerimeter", "scaledPlus", "unifiedPath", "batteryColorForLevel", "level", "draw", "c", "Landroid/graphics/Canvas;", "getBatteryLevel", "getColorForLevel", "getIntrinsicHeight", "getIntrinsicWidth", "getOpacity", "loadPaths", "onBoundsChange", "bounds", "postInvalidate", "setAlpha", "alpha", "setBatteryLevel", "l", "setColorFilter", "colorFilter", "Landroid/graphics/ColorFilter;", "setColors", "fgColor", "bgColor", "singleToneColor", "setPadding", "left", "top", "right", "bottom", "updateSize", "Companion", ZoneGetter.KEY_DISPLAYNAME}, k = 1, mv = {1, 1, 13})
/* loaded from: classes3.dex */
public class ThemedBatteryDrawable extends Drawable {
    private static final int CRITICAL_LEVEL = 15;
    public static final Companion Companion = new Companion(null);
    private static final float HEIGHT = 20.0f;
    private static final float PROTECTION_MIN_STROKE_WIDTH = 6.0f;
    private static final float PROTECTION_STROKE_WIDTH = 3.0f;
    private static final String TAG = "ThemedBatteryDrawable";
    private static final float WIDTH = 12.0f;
    private int backgroundColor;
    private int batteryLevel;
    private final Path boltPath;
    private boolean charging;
    private int[] colorLevels;
    private final Context context;
    private int criticalLevel;
    private boolean dualTone;
    private final Paint dualToneBackgroundFill;
    private final Paint errorPaint;
    private final Path errorPerimeterPath;
    private int fillColor;
    private final Paint fillColorStrokePaint;
    private final Paint fillColorStrokeProtection;
    private final Path fillMask;
    private final Paint fillPaint;
    private final RectF fillRect;
    private int intrinsicHeight;
    private int intrinsicWidth;
    private final Function0<Unit> invalidateRunnable;
    private boolean invertFillIcon;
    private int levelColor;
    private final Path levelPath;
    private final RectF levelRect;
    private final Rect padding;
    private final Path perimeterPath;
    private final Path plusPath;
    private boolean powerSaveEnabled;
    private final Matrix scaleMatrix;
    private final Path scaledBolt;
    private final Path scaledErrorPerimeter;
    private final Path scaledFill;
    private final Path scaledPerimeter;
    private final Path scaledPlus;
    private final Path unifiedPath;

    public ThemedBatteryDrawable(@NotNull Context context, int frameColor) {
        Intrinsics.checkParameterIsNotNull(context, "context");
        this.context = context;
        this.perimeterPath = new Path();
        this.scaledPerimeter = new Path();
        this.errorPerimeterPath = new Path();
        this.scaledErrorPerimeter = new Path();
        this.fillMask = new Path();
        this.scaledFill = new Path();
        this.fillRect = new RectF();
        this.levelRect = new RectF();
        this.levelPath = new Path();
        this.scaleMatrix = new Matrix();
        this.padding = new Rect();
        this.unifiedPath = new Path();
        this.boltPath = new Path();
        this.scaledBolt = new Path();
        this.plusPath = new Path();
        this.scaledPlus = new Path();
        this.fillColor = -65281;
        this.backgroundColor = -65281;
        this.levelColor = -65281;
        this.invalidateRunnable = new Function0<Unit>() { // from class: com.android.settingslib.graph.ThemedBatteryDrawable$invalidateRunnable$1
            /* JADX INFO: Access modifiers changed from: package-private */
            {
                super(0);
            }

            @Override // kotlin.jvm.functions.Function0
            public /* bridge */ /* synthetic */ Unit invoke() {
                invoke2();
                return Unit.INSTANCE;
            }

            /* renamed from: invoke  reason: avoid collision after fix types in other method */
            public final void invoke2() {
                ThemedBatteryDrawable.this.invalidateSelf();
            }
        };
        this.criticalLevel = this.context.getResources().getInteger(17694763);
        Paint p = new Paint(1);
        p.setColor(frameColor);
        p.setDither(true);
        p.setStrokeWidth(5.0f);
        p.setStyle(Paint.Style.STROKE);
        p.setBlendMode(BlendMode.SRC);
        p.setStrokeMiter(5.0f);
        p.setStrokeJoin(Paint.Join.ROUND);
        this.fillColorStrokePaint = p;
        Paint p2 = new Paint(1);
        p2.setDither(true);
        p2.setStrokeWidth(5.0f);
        p2.setStyle(Paint.Style.STROKE);
        p2.setBlendMode(BlendMode.CLEAR);
        p2.setStrokeMiter(5.0f);
        p2.setStrokeJoin(Paint.Join.ROUND);
        this.fillColorStrokeProtection = p2;
        Paint p3 = new Paint(1);
        p3.setColor(frameColor);
        p3.setAlpha(255);
        p3.setDither(true);
        p3.setStrokeWidth(0.0f);
        p3.setStyle(Paint.Style.FILL_AND_STROKE);
        this.fillPaint = p3;
        Paint p4 = new Paint(1);
        p4.setColor(Utils.getColorStateListDefaultColor(this.context, R.color.batterymeter_plus_color));
        p4.setAlpha(255);
        p4.setDither(true);
        p4.setStrokeWidth(0.0f);
        p4.setStyle(Paint.Style.FILL_AND_STROKE);
        p4.setBlendMode(BlendMode.SRC);
        this.errorPaint = p4;
        Paint p5 = new Paint(1);
        p5.setColor(frameColor);
        p5.setAlpha(255);
        p5.setDither(true);
        p5.setStrokeWidth(0.0f);
        p5.setStyle(Paint.Style.FILL_AND_STROKE);
        this.dualToneBackgroundFill = p5;
        Resources resources = this.context.getResources();
        Intrinsics.checkExpressionValueIsNotNull(resources, "context.resources");
        float density = resources.getDisplayMetrics().density;
        this.intrinsicHeight = (int) (HEIGHT * density);
        this.intrinsicWidth = (int) (WIDTH * density);
        Resources res = this.context.getResources();
        TypedArray levels = res.obtainTypedArray(R.array.batterymeter_color_levels);
        TypedArray colors = res.obtainTypedArray(R.array.batterymeter_color_values);
        int N = levels.length();
        this.colorLevels = new int[N * 2];
        for (int i = 0; i < N; i++) {
            this.colorLevels[i * 2] = levels.getInt(i, 0);
            if (colors.getType(i) != 2) {
                this.colorLevels[(i * 2) + 1] = colors.getColor(i, 0);
            } else {
                this.colorLevels[(i * 2) + 1] = Utils.getColorAttrDefaultColor(this.context, colors.getThemeAttributeId(i, 0));
            }
        }
        levels.recycle();
        colors.recycle();
        loadPaths();
    }

    public int getCriticalLevel() {
        return this.criticalLevel;
    }

    public void setCriticalLevel(int i) {
        this.criticalLevel = i;
    }

    public final boolean getCharging() {
        return this.charging;
    }

    public final void setCharging(boolean value) {
        this.charging = value;
        postInvalidate();
    }

    public final boolean getPowerSaveEnabled() {
        return this.powerSaveEnabled;
    }

    public final void setPowerSaveEnabled(boolean value) {
        this.powerSaveEnabled = value;
        postInvalidate();
    }

    @Override // android.graphics.drawable.Drawable
    public void draw(@NotNull Canvas c) {
        float fillTop;
        Intrinsics.checkParameterIsNotNull(c, "c");
        c.saveLayer(null, null);
        this.unifiedPath.reset();
        this.levelPath.reset();
        this.levelRect.set(this.fillRect);
        int i = this.batteryLevel;
        float fillFraction = i / 100.0f;
        if (i >= 95) {
            fillTop = this.fillRect.top;
        } else {
            fillTop = this.fillRect.top + (this.fillRect.height() * (1 - fillFraction));
        }
        this.levelRect.top = (float) Math.floor(fillTop);
        this.levelPath.addRect(this.levelRect, Path.Direction.CCW);
        this.unifiedPath.addPath(this.scaledPerimeter);
        if (!this.dualTone) {
            this.unifiedPath.op(this.levelPath, Path.Op.UNION);
        }
        this.fillPaint.setColor(this.levelColor);
        if (this.charging) {
            this.unifiedPath.op(this.scaledBolt, Path.Op.DIFFERENCE);
            if (!this.invertFillIcon) {
                c.drawPath(this.scaledBolt, this.fillPaint);
            }
        }
        if (this.dualTone) {
            c.drawPath(this.unifiedPath, this.dualToneBackgroundFill);
            c.save();
            c.clipRect(0.0f, getBounds().bottom - (getBounds().height() * fillFraction), getBounds().right, getBounds().bottom);
            c.drawPath(this.unifiedPath, this.fillPaint);
            c.restore();
        } else {
            this.fillPaint.setColor(this.fillColor);
            c.drawPath(this.unifiedPath, this.fillPaint);
            this.fillPaint.setColor(this.levelColor);
            if (this.batteryLevel <= 15 && !this.charging) {
                c.save();
                c.clipPath(this.scaledFill);
                c.drawPath(this.levelPath, this.fillPaint);
                c.restore();
            }
        }
        if (this.charging) {
            c.clipOutPath(this.scaledBolt);
            if (this.invertFillIcon) {
                c.drawPath(this.scaledBolt, this.fillColorStrokePaint);
            } else {
                c.drawPath(this.scaledBolt, this.fillColorStrokeProtection);
            }
        } else if (this.powerSaveEnabled) {
            c.drawPath(this.scaledErrorPerimeter, this.errorPaint);
            c.drawPath(this.scaledPlus, this.errorPaint);
        }
        c.restore();
    }

    private final int batteryColorForLevel(int level) {
        if (this.charging || this.powerSaveEnabled) {
            return this.fillColor;
        }
        return getColorForLevel(level);
    }

    private final int getColorForLevel(int level) {
        int color = 0;
        int i = 0;
        while (true) {
            int[] iArr = this.colorLevels;
            if (i < iArr.length) {
                int thresh = iArr[i];
                color = iArr[i + 1];
                if (level <= thresh) {
                    if (i == iArr.length - 2) {
                        return this.fillColor;
                    }
                    return color;
                }
                i += 2;
            } else {
                return color;
            }
        }
    }

    @Override // android.graphics.drawable.Drawable
    public void setAlpha(int alpha) {
    }

    @Override // android.graphics.drawable.Drawable
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        this.fillPaint.setColorFilter(colorFilter);
        this.fillColorStrokePaint.setColorFilter(colorFilter);
        this.dualToneBackgroundFill.setColorFilter(colorFilter);
    }

    @Override // android.graphics.drawable.Drawable
    public int getOpacity() {
        return -1;
    }

    @Override // android.graphics.drawable.Drawable
    public int getIntrinsicHeight() {
        return this.intrinsicHeight;
    }

    @Override // android.graphics.drawable.Drawable
    public int getIntrinsicWidth() {
        return this.intrinsicWidth;
    }

    public void setBatteryLevel(int l) {
        this.invertFillIcon = l >= 67 ? true : l <= 33 ? false : this.invertFillIcon;
        this.batteryLevel = l;
        this.levelColor = batteryColorForLevel(this.batteryLevel);
        invalidateSelf();
    }

    public final int getBatteryLevel() {
        return this.batteryLevel;
    }

    @Override // android.graphics.drawable.Drawable
    protected void onBoundsChange(@Nullable Rect bounds) {
        super.onBoundsChange(bounds);
        updateSize();
    }

    public final void setPadding(int left, int top, int right, int bottom) {
        Rect rect = this.padding;
        rect.left = left;
        rect.top = top;
        rect.right = right;
        rect.bottom = bottom;
        updateSize();
    }

    public final void setColors(int fgColor, int bgColor, int singleToneColor) {
        this.fillColor = this.dualTone ? fgColor : singleToneColor;
        this.fillPaint.setColor(this.fillColor);
        this.fillColorStrokePaint.setColor(this.fillColor);
        this.backgroundColor = bgColor;
        this.dualToneBackgroundFill.setColor(bgColor);
        this.levelColor = batteryColorForLevel(this.batteryLevel);
        invalidateSelf();
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r1v1, types: [com.android.settingslib.graph.ThemedBatteryDrawable$sam$java_lang_Runnable$0] */
    /* JADX WARN: Type inference failed for: r1v2, types: [com.android.settingslib.graph.ThemedBatteryDrawable$sam$java_lang_Runnable$0] */
    private final void postInvalidate() {
        final Function0<Unit> function0 = this.invalidateRunnable;
        if (function0 != null) {
            function0 = new Runnable() { // from class: com.android.settingslib.graph.ThemedBatteryDrawable$sam$java_lang_Runnable$0
                @Override // java.lang.Runnable
                public final /* synthetic */ void run() {
                    Intrinsics.checkExpressionValueIsNotNull(Function0.this.invoke(), "invoke(...)");
                }
            };
        }
        unscheduleSelf((Runnable) function0);
        final Function0<Unit> function02 = this.invalidateRunnable;
        if (function02 != null) {
            function02 = new Runnable() { // from class: com.android.settingslib.graph.ThemedBatteryDrawable$sam$java_lang_Runnable$0
                @Override // java.lang.Runnable
                public final /* synthetic */ void run() {
                    Intrinsics.checkExpressionValueIsNotNull(Function0.this.invoke(), "invoke(...)");
                }
            };
        }
        scheduleSelf((Runnable) function02, 0L);
    }

    private final void updateSize() {
        Rect b = getBounds();
        Intrinsics.checkExpressionValueIsNotNull(b, "b");
        if (b.isEmpty()) {
            this.scaleMatrix.setScale(1.0f, 1.0f);
        } else {
            this.scaleMatrix.setScale(b.right / WIDTH, b.bottom / HEIGHT);
        }
        this.perimeterPath.transform(this.scaleMatrix, this.scaledPerimeter);
        this.errorPerimeterPath.transform(this.scaleMatrix, this.scaledErrorPerimeter);
        this.fillMask.transform(this.scaleMatrix, this.scaledFill);
        this.scaledFill.computeBounds(this.fillRect, true);
        this.boltPath.transform(this.scaleMatrix, this.scaledBolt);
        this.plusPath.transform(this.scaleMatrix, this.scaledPlus);
        float scaledStrokeWidth = Math.max((b.right / WIDTH) * PROTECTION_STROKE_WIDTH, (float) PROTECTION_MIN_STROKE_WIDTH);
        this.fillColorStrokePaint.setStrokeWidth(scaledStrokeWidth);
        this.fillColorStrokeProtection.setStrokeWidth(scaledStrokeWidth);
    }

    private final void loadPaths() {
        String pathString = this.context.getResources().getString(17039679);
        this.perimeterPath.set(PathParser.createPathFromPathData(pathString));
        this.perimeterPath.computeBounds(new RectF(), true);
        String errorPathString = this.context.getResources().getString(17039677);
        this.errorPerimeterPath.set(PathParser.createPathFromPathData(errorPathString));
        this.errorPerimeterPath.computeBounds(new RectF(), true);
        String fillMaskString = this.context.getResources().getString(17039678);
        this.fillMask.set(PathParser.createPathFromPathData(fillMaskString));
        this.fillMask.computeBounds(this.fillRect, true);
        String boltPathString = this.context.getResources().getString(17039676);
        this.boltPath.set(PathParser.createPathFromPathData(boltPathString));
        String plusPathString = this.context.getResources().getString(17039680);
        this.plusPath.set(PathParser.createPathFromPathData(plusPathString));
        this.dualTone = this.context.getResources().getBoolean(17891374);
    }

    /* compiled from: ThemedBatteryDrawable.kt */
    @Metadata(bv = {1, 0, 3}, d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u0007\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0006X\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0006X\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\nX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\u0006X\u0082T¢\u0006\u0002\n\u0000¨\u0006\f"}, d2 = {"Lcom/android/settingslib/graph/ThemedBatteryDrawable$Companion;", "", "()V", "CRITICAL_LEVEL", "", "HEIGHT", "", "PROTECTION_MIN_STROKE_WIDTH", "PROTECTION_STROKE_WIDTH", "TAG", "", "WIDTH", ZoneGetter.KEY_DISPLAYNAME}, k = 1, mv = {1, 1, 13})
    /* loaded from: classes3.dex */
    public static final class Companion {
        private Companion() {
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }
}
