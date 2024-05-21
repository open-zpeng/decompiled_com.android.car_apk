package com.android.settingslib.widget;

import android.app.ActionBar;
import android.app.Activity;
import android.view.View;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
/* loaded from: classes3.dex */
public class ActionBarShadowController implements LifecycleObserver {
    @VisibleForTesting
    static final float ELEVATION_HIGH = 8.0f;
    @VisibleForTesting
    static final float ELEVATION_LOW = 0.0f;
    private boolean mIsScrollWatcherAttached;
    @VisibleForTesting
    ScrollChangeWatcher mScrollChangeWatcher;
    private View mScrollView;

    public static ActionBarShadowController attachToView(Activity activity, Lifecycle lifecycle, View scrollView) {
        return new ActionBarShadowController(activity, lifecycle, scrollView);
    }

    public static ActionBarShadowController attachToView(View anchorView, Lifecycle lifecycle, View scrollView) {
        return new ActionBarShadowController(anchorView, lifecycle, scrollView);
    }

    private ActionBarShadowController(Activity activity, Lifecycle lifecycle, View scrollView) {
        this.mScrollChangeWatcher = new ScrollChangeWatcher(activity);
        this.mScrollView = scrollView;
        attachScrollWatcher();
        lifecycle.addObserver(this);
    }

    private ActionBarShadowController(View anchorView, Lifecycle lifecycle, View scrollView) {
        this.mScrollChangeWatcher = new ScrollChangeWatcher(anchorView);
        this.mScrollView = scrollView;
        attachScrollWatcher();
        lifecycle.addObserver(this);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    private void attachScrollWatcher() {
        if (!this.mIsScrollWatcherAttached) {
            this.mIsScrollWatcherAttached = true;
            this.mScrollView.setOnScrollChangeListener(this.mScrollChangeWatcher);
            this.mScrollChangeWatcher.updateDropShadow(this.mScrollView);
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    private void detachScrollWatcher() {
        this.mScrollView.setOnScrollChangeListener(null);
        this.mIsScrollWatcherAttached = false;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes3.dex */
    public final class ScrollChangeWatcher implements View.OnScrollChangeListener {
        private final Activity mActivity;
        private final View mAnchorView;

        ScrollChangeWatcher(Activity activity) {
            this.mActivity = activity;
            this.mAnchorView = null;
        }

        ScrollChangeWatcher(View anchorView) {
            this.mAnchorView = anchorView;
            this.mActivity = null;
        }

        @Override // android.view.View.OnScrollChangeListener
        public void onScrollChange(View view, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
            updateDropShadow(view);
        }

        public void updateDropShadow(View view) {
            ActionBar actionBar;
            boolean shouldShowShadow = view.canScrollVertically(-1);
            View view2 = this.mAnchorView;
            float f = ActionBarShadowController.ELEVATION_HIGH;
            if (view2 != null) {
                if (!shouldShowShadow) {
                    f = 0.0f;
                }
                view2.setElevation(f);
                return;
            }
            Activity activity = this.mActivity;
            if (activity != null && (actionBar = activity.getActionBar()) != null) {
                if (!shouldShowShadow) {
                    f = 0.0f;
                }
                actionBar.setElevation(f);
            }
        }
    }
}
