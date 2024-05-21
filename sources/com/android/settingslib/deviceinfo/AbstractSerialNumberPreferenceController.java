package com.android.settingslib.deviceinfo;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import com.android.settingslib.core.AbstractPreferenceController;
/* loaded from: classes3.dex */
public class AbstractSerialNumberPreferenceController extends AbstractPreferenceController {
    @VisibleForTesting
    static final String KEY_SERIAL_NUMBER = "serial_number";
    private final String mSerialNumber;

    public AbstractSerialNumberPreferenceController(Context context) {
        this(context, Build.getSerial());
    }

    @VisibleForTesting
    AbstractSerialNumberPreferenceController(Context context, String serialNumber) {
        super(context);
        this.mSerialNumber = serialNumber;
    }

    @Override // com.android.settingslib.core.AbstractPreferenceController
    public boolean isAvailable() {
        return !TextUtils.isEmpty(this.mSerialNumber);
    }

    @Override // com.android.settingslib.core.AbstractPreferenceController
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        Preference pref = screen.findPreference(KEY_SERIAL_NUMBER);
        if (pref != null) {
            pref.setSummary(this.mSerialNumber);
        }
    }

    @Override // com.android.settingslib.core.AbstractPreferenceController, com.android.settingslib.core.ConfirmationDialogController
    public String getPreferenceKey() {
        return KEY_SERIAL_NUMBER;
    }
}
