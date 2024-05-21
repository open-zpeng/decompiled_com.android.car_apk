package com.android.settingslib.widget.apppreference;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ProgressBar;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import com.android.settingslib.widget.R;
/* loaded from: classes3.dex */
public class AppPreference extends Preference {
    private int mProgress;
    private boolean mProgressVisible;

    public AppPreference(Context context) {
        super(context);
        setLayoutResource(R.layout.preference_app);
    }

    public AppPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_app);
    }

    public void setProgress(int amount) {
        this.mProgress = amount;
        this.mProgressVisible = true;
        notifyChanged();
    }

    @Override // androidx.preference.Preference
    public void onBindViewHolder(PreferenceViewHolder view) {
        super.onBindViewHolder(view);
        ProgressBar progress = (ProgressBar) view.findViewById(16908301);
        if (this.mProgressVisible) {
            progress.setProgress(this.mProgress);
            progress.setVisibility(0);
            return;
        }
        progress.setVisibility(8);
    }
}
