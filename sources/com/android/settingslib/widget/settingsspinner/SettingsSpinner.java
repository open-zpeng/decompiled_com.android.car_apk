package com.android.settingslib.widget.settingsspinner;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Spinner;
import com.android.settingslib.widget.R;
/* loaded from: classes3.dex */
public class SettingsSpinner extends Spinner {
    public SettingsSpinner(Context context) {
        super(context);
        setBackgroundResource(R.drawable.settings_spinner_background);
    }

    public SettingsSpinner(Context context, int mode) {
        super(context, mode);
        setBackgroundResource(R.drawable.settings_spinner_background);
    }

    public SettingsSpinner(Context context, AttributeSet attrs) {
        super(context, attrs);
        setBackgroundResource(R.drawable.settings_spinner_background);
    }

    public SettingsSpinner(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setBackgroundResource(R.drawable.settings_spinner_background);
    }

    public SettingsSpinner(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes, int mode) {
        super(context, attrs, defStyleAttr, defStyleRes, mode, null);
    }
}
