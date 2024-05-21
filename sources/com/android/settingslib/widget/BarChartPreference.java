package com.android.settingslib.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import java.util.Arrays;
/* loaded from: classes3.dex */
public class BarChartPreference extends Preference {
    private static final int[] BAR_VIEWS = {R.id.bar_view1, R.id.bar_view2, R.id.bar_view3, R.id.bar_view4};
    public static final int MAXIMUM_BAR_VIEWS = 4;
    private static final String TAG = "BarChartPreference";
    private BarChartInfo mBarChartInfo;
    private boolean mIsLoading;
    private int mMaxBarHeight;

    public BarChartPreference(Context context) {
        super(context);
        init();
    }

    public BarChartPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BarChartPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public BarChartPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public void initializeBarChart(@NonNull BarChartInfo barChartInfo) {
        this.mBarChartInfo = barChartInfo;
        notifyChanged();
    }

    public void setBarViewInfos(@Nullable BarViewInfo[] barViewInfos) {
        if (barViewInfos != null && barViewInfos.length > 4) {
            throw new IllegalStateException("We only support up to four bar views");
        }
        this.mBarChartInfo.setBarViewInfos(barViewInfos);
        notifyChanged();
    }

    public void updateLoadingState(boolean isLoading) {
        this.mIsLoading = isLoading;
        notifyChanged();
    }

    @Override // androidx.preference.Preference
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.setDividerAllowedAbove(true);
        holder.setDividerAllowedBelow(true);
        bindChartTitleView(holder);
        bindChartDetailsView(holder);
        if (this.mIsLoading) {
            holder.itemView.setVisibility(4);
            return;
        }
        holder.itemView.setVisibility(0);
        BarViewInfo[] barViewInfos = this.mBarChartInfo.getBarViewInfos();
        if (barViewInfos == null || barViewInfos.length == 0) {
            setEmptyViewVisible(holder, true);
            return;
        }
        setEmptyViewVisible(holder, false);
        updateBarChart(holder);
    }

    private void init() {
        setSelectable(false);
        setLayoutResource(R.layout.settings_bar_chart);
        this.mMaxBarHeight = getContext().getResources().getDimensionPixelSize(R.dimen.settings_bar_view_max_height);
    }

    private void bindChartTitleView(PreferenceViewHolder holder) {
        TextView titleView = (TextView) holder.findViewById(R.id.bar_chart_title);
        titleView.setText(this.mBarChartInfo.getTitle());
    }

    private void bindChartDetailsView(PreferenceViewHolder holder) {
        Button detailsView = (Button) holder.findViewById(R.id.bar_chart_details);
        int details = this.mBarChartInfo.getDetails();
        if (details == 0) {
            detailsView.setVisibility(8);
            return;
        }
        detailsView.setVisibility(0);
        detailsView.setText(details);
        detailsView.setOnClickListener(this.mBarChartInfo.getDetailsOnClickListener());
    }

    private void updateBarChart(PreferenceViewHolder holder) {
        normalizeBarViewHeights();
        BarViewInfo[] barViewInfos = this.mBarChartInfo.getBarViewInfos();
        for (int index = 0; index < 4; index++) {
            BarView barView = (BarView) holder.findViewById(BAR_VIEWS[index]);
            if (barViewInfos == null || index >= barViewInfos.length) {
                barView.setVisibility(8);
            } else {
                barView.setVisibility(0);
                barView.updateView(barViewInfos[index]);
            }
        }
    }

    private void normalizeBarViewHeights() {
        BarViewInfo[] barViewInfos = this.mBarChartInfo.getBarViewInfos();
        if (barViewInfos == null || barViewInfos.length == 0) {
            return;
        }
        Arrays.sort(barViewInfos);
        int maxBarHeight = barViewInfos[0].getHeight();
        int unit = maxBarHeight == 0 ? 0 : this.mMaxBarHeight / maxBarHeight;
        for (BarViewInfo barView : barViewInfos) {
            barView.setNormalizedHeight(barView.getHeight() * unit);
        }
    }

    private void setEmptyViewVisible(PreferenceViewHolder holder, boolean visible) {
        View barViewsContainer = holder.findViewById(R.id.bar_views_container);
        TextView emptyView = (TextView) holder.findViewById(R.id.empty_view);
        int emptyTextRes = this.mBarChartInfo.getEmptyText();
        if (emptyTextRes != 0) {
            emptyView.setText(emptyTextRes);
        }
        emptyView.setVisibility(visible ? 0 : 8);
        barViewsContainer.setVisibility(visible ? 8 : 0);
    }
}
