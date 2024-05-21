package com.android.settingslib.widget.settingsspinner;

import android.content.Context;
import android.widget.ArrayAdapter;
import com.android.settingslib.widget.R;
/* loaded from: classes3.dex */
public class SettingsSpinnerAdapter<T> extends ArrayAdapter<T> {
    public SettingsSpinnerAdapter(Context context) {
        super(context, R.layout.settings_spinner_view);
        setDropDownViewResource(17367049);
    }
}
