package com.android.settingslib.inputmethod;

import android.content.Context;
import androidx.preference.SwitchPreference;
/* loaded from: classes3.dex */
public class SwitchWithNoTextPreference extends SwitchPreference {
    private static final String EMPTY_TEXT = "";

    public SwitchWithNoTextPreference(Context context) {
        super(context);
        setSwitchTextOn(EMPTY_TEXT);
        setSwitchTextOff(EMPTY_TEXT);
    }
}
