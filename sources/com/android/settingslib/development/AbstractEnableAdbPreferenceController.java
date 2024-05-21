package com.android.settingslib.development;

import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import androidx.annotation.VisibleForTesting;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;
import androidx.preference.TwoStatePreference;
import com.android.settingslib.core.ConfirmationDialogController;
/* loaded from: classes3.dex */
public abstract class AbstractEnableAdbPreferenceController extends DeveloperOptionsPreferenceController implements ConfirmationDialogController {
    public static final String ACTION_ENABLE_ADB_STATE_CHANGED = "com.android.settingslib.development.AbstractEnableAdbController.ENABLE_ADB_STATE_CHANGED";
    public static final int ADB_SETTING_OFF = 0;
    public static final int ADB_SETTING_ON = 1;
    private static final String KEY_ENABLE_ADB = "enable_adb";
    protected SwitchPreference mPreference;

    public AbstractEnableAdbPreferenceController(Context context) {
        super(context);
    }

    @Override // com.android.settingslib.development.DeveloperOptionsPreferenceController, com.android.settingslib.core.AbstractPreferenceController
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        if (isAvailable()) {
            this.mPreference = (SwitchPreference) screen.findPreference(KEY_ENABLE_ADB);
        }
    }

    @Override // com.android.settingslib.development.DeveloperOptionsPreferenceController, com.android.settingslib.core.AbstractPreferenceController
    public boolean isAvailable() {
        return ((UserManager) this.mContext.getSystemService(UserManager.class)).isAdminUser();
    }

    @Override // com.android.settingslib.core.AbstractPreferenceController, com.android.settingslib.core.ConfirmationDialogController
    public String getPreferenceKey() {
        return KEY_ENABLE_ADB;
    }

    private boolean isAdbEnabled() {
        ContentResolver cr = this.mContext.getContentResolver();
        return Settings.Global.getInt(cr, "adb_enabled", 0) != 0;
    }

    @Override // com.android.settingslib.core.AbstractPreferenceController
    public void updateState(Preference preference) {
        ((TwoStatePreference) preference).setChecked(isAdbEnabled());
    }

    public void enablePreference(boolean enabled) {
        if (isAvailable()) {
            this.mPreference.setEnabled(enabled);
        }
    }

    public void resetPreference() {
        if (this.mPreference.isChecked()) {
            this.mPreference.setChecked(false);
            handlePreferenceTreeClick(this.mPreference);
        }
    }

    public boolean haveDebugSettings() {
        return isAdbEnabled();
    }

    @Override // com.android.settingslib.core.AbstractPreferenceController
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (!isUserAMonkey() && TextUtils.equals(KEY_ENABLE_ADB, preference.getKey())) {
            if (!isAdbEnabled()) {
                showConfirmationDialog(preference);
                return true;
            }
            writeAdbSetting(false);
            return true;
        }
        return false;
    }

    protected void writeAdbSetting(boolean enabled) {
        Settings.Global.putInt(this.mContext.getContentResolver(), "adb_enabled", enabled ? 1 : 0);
        notifyStateChanged();
    }

    private void notifyStateChanged() {
        LocalBroadcastManager.getInstance(this.mContext).sendBroadcast(new Intent(ACTION_ENABLE_ADB_STATE_CHANGED));
    }

    @VisibleForTesting
    boolean isUserAMonkey() {
        return ActivityManager.isUserAMonkey();
    }
}
