package com.android.settingslib.widget;

import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
/* loaded from: classes3.dex */
public class AppEntitiesHeaderController {
    @VisibleForTesting
    public static final int MAXIMUM_APPS = 3;
    private static final String TAG = "AppEntitiesHeaderCtl";
    private final View[] mAppEntityViews;
    private final View mAppViewsContainer;
    private final Context mContext;
    private View.OnClickListener mDetailsOnClickListener;
    private CharSequence mHeaderDetails;
    private int mHeaderDetailsRes;
    private final Button mHeaderDetailsView;
    private int mHeaderEmptyRes;
    private final TextView mHeaderEmptyView;
    private int mHeaderTitleRes;
    private final TextView mHeaderTitleView;
    private final AppEntityInfo[] mAppEntityInfos = new AppEntityInfo[3];
    private final ImageView[] mAppIconViews = new ImageView[3];
    private final TextView[] mAppTitleViews = new TextView[3];
    private final TextView[] mAppSummaryViews = new TextView[3];

    public static AppEntitiesHeaderController newInstance(@NonNull Context context, @NonNull View appEntitiesHeaderView) {
        return new AppEntitiesHeaderController(context, appEntitiesHeaderView);
    }

    private AppEntitiesHeaderController(Context context, View appEntitiesHeaderView) {
        this.mContext = context;
        this.mHeaderTitleView = (TextView) appEntitiesHeaderView.findViewById(R.id.header_title);
        this.mHeaderDetailsView = (Button) appEntitiesHeaderView.findViewById(R.id.header_details);
        this.mHeaderEmptyView = (TextView) appEntitiesHeaderView.findViewById(R.id.empty_view);
        this.mAppViewsContainer = appEntitiesHeaderView.findViewById(R.id.app_views_container);
        this.mAppEntityViews = new View[]{appEntitiesHeaderView.findViewById(R.id.app1_view), appEntitiesHeaderView.findViewById(R.id.app2_view), appEntitiesHeaderView.findViewById(R.id.app3_view)};
        for (int index = 0; index < 3; index++) {
            View appView = this.mAppEntityViews[index];
            this.mAppIconViews[index] = (ImageView) appView.findViewById(R.id.app_icon);
            this.mAppTitleViews[index] = (TextView) appView.findViewById(R.id.app_title);
            this.mAppSummaryViews[index] = (TextView) appView.findViewById(R.id.app_summary);
        }
    }

    public AppEntitiesHeaderController setHeaderTitleRes(@StringRes int titleRes) {
        this.mHeaderTitleRes = titleRes;
        return this;
    }

    public AppEntitiesHeaderController setHeaderDetailsRes(@StringRes int detailsRes) {
        this.mHeaderDetailsRes = detailsRes;
        return this;
    }

    public AppEntitiesHeaderController setHeaderDetails(CharSequence detailsText) {
        this.mHeaderDetails = detailsText;
        return this;
    }

    public AppEntitiesHeaderController setHeaderDetailsClickListener(@Nullable View.OnClickListener clickListener) {
        this.mDetailsOnClickListener = clickListener;
        return this;
    }

    public AppEntitiesHeaderController setHeaderEmptyRes(@StringRes int emptyRes) {
        this.mHeaderEmptyRes = emptyRes;
        return this;
    }

    public AppEntitiesHeaderController setAppEntity(int index, @NonNull AppEntityInfo appEntityInfo) {
        this.mAppEntityInfos[index] = appEntityInfo;
        return this;
    }

    public AppEntitiesHeaderController removeAppEntity(int index) {
        this.mAppEntityInfos[index] = null;
        return this;
    }

    public AppEntitiesHeaderController clearAllAppEntities() {
        for (int index = 0; index < 3; index++) {
            removeAppEntity(index);
        }
        return this;
    }

    public void apply() {
        bindHeaderTitleView();
        if (isAppEntityInfosEmpty()) {
            setEmptyViewVisible(true);
            return;
        }
        setEmptyViewVisible(false);
        bindHeaderDetailsView();
        for (int index = 0; index < 3; index++) {
            bindAppEntityView(index);
        }
    }

    private void bindHeaderTitleView() {
        CharSequence titleText = "";
        try {
            titleText = this.mContext.getText(this.mHeaderTitleRes);
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Resource of header title can't not be found!", e);
        }
        this.mHeaderTitleView.setText(titleText);
        this.mHeaderTitleView.setVisibility(TextUtils.isEmpty(titleText) ? 8 : 0);
    }

    private void bindHeaderDetailsView() {
        CharSequence detailsText = this.mHeaderDetails;
        if (TextUtils.isEmpty(detailsText)) {
            try {
                detailsText = this.mContext.getText(this.mHeaderDetailsRes);
            } catch (Resources.NotFoundException e) {
                Log.e(TAG, "Resource of header details can't not be found!", e);
            }
        }
        this.mHeaderDetailsView.setText(detailsText);
        this.mHeaderDetailsView.setVisibility(TextUtils.isEmpty(detailsText) ? 8 : 0);
        this.mHeaderDetailsView.setOnClickListener(this.mDetailsOnClickListener);
    }

    private void bindAppEntityView(int index) {
        AppEntityInfo appEntityInfo = this.mAppEntityInfos[index];
        this.mAppEntityViews[index].setVisibility(appEntityInfo != null ? 0 : 8);
        if (appEntityInfo != null) {
            this.mAppEntityViews[index].setOnClickListener(appEntityInfo.getClickListener());
            this.mAppIconViews[index].setImageDrawable(appEntityInfo.getIcon());
            CharSequence title = appEntityInfo.getTitle();
            this.mAppTitleViews[index].setVisibility(TextUtils.isEmpty(title) ? 4 : 0);
            this.mAppTitleViews[index].setText(title);
            CharSequence summary = appEntityInfo.getSummary();
            this.mAppSummaryViews[index].setVisibility(TextUtils.isEmpty(summary) ? 4 : 0);
            this.mAppSummaryViews[index].setText(summary);
        }
    }

    private void setEmptyViewVisible(boolean visible) {
        int i = this.mHeaderEmptyRes;
        if (i != 0) {
            this.mHeaderEmptyView.setText(i);
        }
        this.mHeaderEmptyView.setVisibility(visible ? 0 : 8);
        this.mHeaderDetailsView.setVisibility(visible ? 8 : 0);
        this.mAppViewsContainer.setVisibility(visible ? 8 : 0);
    }

    private boolean isAppEntityInfosEmpty() {
        AppEntityInfo[] appEntityInfoArr;
        for (AppEntityInfo info : this.mAppEntityInfos) {
            if (info != null) {
                return false;
            }
        }
        return true;
    }
}
