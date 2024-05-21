package com.android.settingslib.core.instrumentation;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
/* loaded from: classes3.dex */
public class VisibilityLoggerMixin implements LifecycleObserver {
    private static final String TAG = "VisibilityLoggerMixin";
    private final int mMetricsCategory;
    private MetricsFeatureProvider mMetricsFeature;
    private int mSourceMetricsCategory = 0;
    private long mVisibleTimestamp;

    public VisibilityLoggerMixin(int metricsCategory, MetricsFeatureProvider metricsFeature) {
        this.mMetricsCategory = metricsCategory;
        this.mMetricsFeature = metricsFeature;
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    public void onResume() {
        int i;
        this.mVisibleTimestamp = SystemClock.elapsedRealtime();
        MetricsFeatureProvider metricsFeatureProvider = this.mMetricsFeature;
        if (metricsFeatureProvider != null && (i = this.mMetricsCategory) != 0) {
            metricsFeatureProvider.visible(null, this.mSourceMetricsCategory, i);
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    public void onPause() {
        int i;
        this.mVisibleTimestamp = 0L;
        MetricsFeatureProvider metricsFeatureProvider = this.mMetricsFeature;
        if (metricsFeatureProvider != null && (i = this.mMetricsCategory) != 0) {
            metricsFeatureProvider.hidden(null, i);
        }
    }

    public void setSourceMetricsCategory(Activity activity) {
        Intent intent;
        if (this.mSourceMetricsCategory != 0 || activity == null || (intent = activity.getIntent()) == null) {
            return;
        }
        this.mSourceMetricsCategory = intent.getIntExtra(MetricsFeatureProvider.EXTRA_SOURCE_METRICS_CATEGORY, 0);
    }
}
