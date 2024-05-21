package com.android.settingslib.widget;

import android.graphics.drawable.Drawable;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
/* loaded from: classes3.dex */
public class AppEntityInfo {
    private final View.OnClickListener mClickListener;
    private final Drawable mIcon;
    private final CharSequence mSummary;
    private final CharSequence mTitle;

    public Drawable getIcon() {
        return this.mIcon;
    }

    public CharSequence getTitle() {
        return this.mTitle;
    }

    public CharSequence getSummary() {
        return this.mSummary;
    }

    public View.OnClickListener getClickListener() {
        return this.mClickListener;
    }

    private AppEntityInfo(Builder builder) {
        this.mIcon = builder.mIcon;
        this.mTitle = builder.mTitle;
        this.mSummary = builder.mSummary;
        this.mClickListener = builder.mClickListener;
    }

    /* loaded from: classes3.dex */
    public static class Builder {
        private View.OnClickListener mClickListener;
        private Drawable mIcon;
        private CharSequence mSummary;
        private CharSequence mTitle;

        public AppEntityInfo build() {
            return new AppEntityInfo(this);
        }

        public Builder setIcon(@NonNull Drawable icon) {
            this.mIcon = icon;
            return this;
        }

        public Builder setTitle(@Nullable CharSequence title) {
            this.mTitle = title;
            return this;
        }

        public Builder setSummary(@Nullable CharSequence summary) {
            this.mSummary = summary;
            return this;
        }

        public Builder setOnClickListener(@Nullable View.OnClickListener clickListener) {
            this.mClickListener = clickListener;
            return this;
        }
    }
}
