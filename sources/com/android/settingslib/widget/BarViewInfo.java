package com.android.settingslib.widget;

import android.graphics.drawable.Drawable;
import android.view.View;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import java.util.Comparator;
import java.util.function.ToIntFunction;
/* loaded from: classes3.dex */
public class BarViewInfo implements Comparable<BarViewInfo> {
    private View.OnClickListener mClickListener;
    @Nullable
    private CharSequence mContentDescription;
    private int mHeight;
    private final Drawable mIcon;
    private int mNormalizedHeight;
    private CharSequence mSummary;
    private CharSequence mTitle;

    public BarViewInfo(Drawable icon, @IntRange(from = 0) int barHeight, @Nullable CharSequence title, CharSequence summary, @Nullable CharSequence contentDescription) {
        this.mIcon = icon;
        this.mHeight = barHeight;
        this.mTitle = title;
        this.mSummary = summary;
        this.mContentDescription = contentDescription;
    }

    public void setClickListener(@Nullable View.OnClickListener listener) {
        this.mClickListener = listener;
    }

    @Override // java.lang.Comparable
    public int compareTo(BarViewInfo other) {
        return Comparator.comparingInt(new ToIntFunction() { // from class: com.android.settingslib.widget.-$$Lambda$BarViewInfo$0E64JyWB2WmVqNcEtw_jyuLCMME
            @Override // java.util.function.ToIntFunction
            public final int applyAsInt(Object obj) {
                int i;
                i = ((BarViewInfo) obj).mHeight;
                return i;
            }
        }).compare(other, this);
    }

    void setHeight(@IntRange(from = 0) int height) {
        this.mHeight = height;
    }

    void setTitle(CharSequence title) {
        this.mTitle = title;
    }

    void setSummary(CharSequence summary) {
        this.mSummary = summary;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public Drawable getIcon() {
        return this.mIcon;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public int getHeight() {
        return this.mHeight;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public View.OnClickListener getClickListener() {
        return this.mClickListener;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @Nullable
    public CharSequence getTitle() {
        return this.mTitle;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public CharSequence getSummary() {
        return this.mSummary;
    }

    @Nullable
    public CharSequence getContentDescription() {
        return this.mContentDescription;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void setNormalizedHeight(@IntRange(from = 0) int barHeight) {
        this.mNormalizedHeight = barHeight;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public int getNormalizedHeight() {
        return this.mNormalizedHeight;
    }
}
