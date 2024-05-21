package com.android.settingslib.core;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
/* loaded from: classes3.dex */
public abstract class AbstractPreferenceController {
    private static final String TAG = "AbstractPrefController";
    protected final Context mContext;

    public abstract String getPreferenceKey();

    public abstract boolean isAvailable();

    public AbstractPreferenceController(Context context) {
        this.mContext = context;
    }

    public void displayPreference(PreferenceScreen screen) {
        String prefKey = getPreferenceKey();
        if (TextUtils.isEmpty(prefKey)) {
            Log.w(TAG, "Skipping displayPreference because key is empty:" + getClass().getName());
        } else if (isAvailable()) {
            setVisible(screen, prefKey, true);
            if (this instanceof Preference.OnPreferenceChangeListener) {
                Preference preference = screen.findPreference(prefKey);
                preference.setOnPreferenceChangeListener((Preference.OnPreferenceChangeListener) this);
            }
        } else {
            setVisible(screen, prefKey, false);
        }
    }

    public void updateState(Preference preference) {
        refreshSummary(preference);
    }

    protected void refreshSummary(Preference preference) {
        CharSequence summary;
        if (preference == null || (summary = getSummary()) == null) {
            return;
        }
        preference.setSummary(summary);
    }

    public boolean handlePreferenceTreeClick(Preference preference) {
        return false;
    }

    protected final void setVisible(PreferenceGroup group, String key, boolean isVisible) {
        Preference pref = group.findPreference(key);
        if (pref != null) {
            pref.setVisible(isVisible);
        }
    }

    public CharSequence getSummary() {
        return null;
    }
}
