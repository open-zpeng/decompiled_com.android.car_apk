package com.android.settingslib.widget;

import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
/* loaded from: classes3.dex */
public class BarChartInfo {
    private BarViewInfo[] mBarViewInfos;
    @StringRes
    private final int mDetails;
    private final View.OnClickListener mDetailsOnClickListener;
    @StringRes
    private final int mEmptyText;
    @StringRes
    private final int mTitle;

    public int getTitle() {
        return this.mTitle;
    }

    public int getDetails() {
        return this.mDetails;
    }

    public int getEmptyText() {
        return this.mEmptyText;
    }

    public View.OnClickListener getDetailsOnClickListener() {
        return this.mDetailsOnClickListener;
    }

    public BarViewInfo[] getBarViewInfos() {
        return this.mBarViewInfos;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void setBarViewInfos(BarViewInfo[] barViewInfos) {
        this.mBarViewInfos = barViewInfos;
    }

    private BarChartInfo(Builder builder) {
        this.mTitle = builder.mTitle;
        this.mDetails = builder.mDetails;
        this.mEmptyText = builder.mEmptyText;
        this.mDetailsOnClickListener = builder.mDetailsOnClickListener;
        if (builder.mBarViewInfos == null) {
            return;
        }
        this.mBarViewInfos = (BarViewInfo[]) builder.mBarViewInfos.stream().toArray(new IntFunction() { // from class: com.android.settingslib.widget.-$$Lambda$BarChartInfo$2CrHVNAna8TvSeyBIL19oCkthVU
            @Override // java.util.function.IntFunction
            public final Object apply(int i) {
                return BarChartInfo.lambda$new$0(i);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ BarViewInfo[] lambda$new$0(int x$0) {
        return new BarViewInfo[x$0];
    }

    /* loaded from: classes3.dex */
    public static class Builder {
        private List<BarViewInfo> mBarViewInfos;
        @StringRes
        private int mDetails;
        private View.OnClickListener mDetailsOnClickListener;
        @StringRes
        private int mEmptyText;
        @StringRes
        private int mTitle;

        public BarChartInfo build() {
            if (this.mTitle == 0) {
                throw new IllegalStateException("You must call Builder#setTitle() once.");
            }
            return new BarChartInfo(this);
        }

        public Builder setTitle(@StringRes int title) {
            this.mTitle = title;
            return this;
        }

        public Builder setDetails(@StringRes int details) {
            this.mDetails = details;
            return this;
        }

        public Builder setEmptyText(@StringRes int emptyText) {
            this.mEmptyText = emptyText;
            return this;
        }

        public Builder setDetailsOnClickListener(@Nullable View.OnClickListener clickListener) {
            this.mDetailsOnClickListener = clickListener;
            return this;
        }

        public Builder addBarViewInfo(@NonNull BarViewInfo barViewInfo) {
            if (this.mBarViewInfos == null) {
                this.mBarViewInfos = new ArrayList();
            }
            if (this.mBarViewInfos.size() >= 4) {
                throw new IllegalStateException("We only support up to four bar views");
            }
            this.mBarViewInfos.add(barViewInfo);
            return this;
        }
    }
}
