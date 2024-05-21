package com.android.settingslib.widget;

import android.content.Context;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceScreen;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.SetPreferenceScreen;
@Deprecated
/* loaded from: classes3.dex */
public class FooterPreferenceMixin implements LifecycleObserver, SetPreferenceScreen {
    private FooterPreference mFooterPreference;
    private final PreferenceFragment mFragment;

    public FooterPreferenceMixin(PreferenceFragment fragment, Lifecycle lifecycle) {
        this.mFragment = fragment;
        lifecycle.addObserver(this);
    }

    @Override // com.android.settingslib.core.lifecycle.events.SetPreferenceScreen
    public void setPreferenceScreen(PreferenceScreen preferenceScreen) {
        FooterPreference footerPreference = this.mFooterPreference;
        if (footerPreference != null) {
            preferenceScreen.addPreference(footerPreference);
        }
    }

    public FooterPreference createFooterPreference() {
        PreferenceScreen screen = this.mFragment.getPreferenceScreen();
        FooterPreference footerPreference = this.mFooterPreference;
        if (footerPreference != null && screen != null) {
            screen.removePreference(footerPreference);
        }
        this.mFooterPreference = new FooterPreference(getPrefContext());
        if (screen != null) {
            screen.addPreference(this.mFooterPreference);
        }
        return this.mFooterPreference;
    }

    private Context getPrefContext() {
        return this.mFragment.getPreferenceManager().getContext();
    }

    public boolean hasFooter() {
        return this.mFooterPreference != null;
    }
}
