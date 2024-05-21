package com.android.settingslib.deviceinfo;

import android.content.Context;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import com.android.settingslib.R;
import com.android.settingslib.core.lifecycle.Lifecycle;
/* loaded from: classes3.dex */
public abstract class AbstractImsStatusPreferenceController extends AbstractConnectivityPreferenceController {
    private static final String[] CONNECTIVITY_INTENTS = {"android.bluetooth.adapter.action.STATE_CHANGED", "android.net.conn.CONNECTIVITY_CHANGE", "android.net.wifi.LINK_CONFIGURATION_CHANGED", "android.net.wifi.STATE_CHANGE"};
    @VisibleForTesting
    static final String KEY_IMS_REGISTRATION_STATE = "ims_reg_state";
    private Preference mImsStatus;

    public AbstractImsStatusPreferenceController(Context context, Lifecycle lifecycle) {
        super(context, lifecycle);
    }

    @Override // com.android.settingslib.core.AbstractPreferenceController
    public boolean isAvailable() {
        CarrierConfigManager configManager = (CarrierConfigManager) this.mContext.getSystemService(CarrierConfigManager.class);
        int subId = SubscriptionManager.getDefaultDataSubscriptionId();
        PersistableBundle config = null;
        if (configManager != null) {
            config = configManager.getConfigForSubId(subId);
        }
        return config != null && config.getBoolean("show_ims_registration_status_bool");
    }

    @Override // com.android.settingslib.core.AbstractPreferenceController, com.android.settingslib.core.ConfirmationDialogController
    public String getPreferenceKey() {
        return KEY_IMS_REGISTRATION_STATE;
    }

    @Override // com.android.settingslib.core.AbstractPreferenceController
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        this.mImsStatus = screen.findPreference(KEY_IMS_REGISTRATION_STATE);
        updateConnectivity();
    }

    @Override // com.android.settingslib.deviceinfo.AbstractConnectivityPreferenceController
    protected String[] getConnectivityIntents() {
        return CONNECTIVITY_INTENTS;
    }

    @Override // com.android.settingslib.deviceinfo.AbstractConnectivityPreferenceController
    protected void updateConnectivity() {
        int subId = SubscriptionManager.getDefaultDataSubscriptionId();
        if (this.mImsStatus != null) {
            TelephonyManager tm = (TelephonyManager) this.mContext.getSystemService(TelephonyManager.class);
            this.mImsStatus.setSummary((tm == null || !tm.isImsRegistered(subId)) ? R.string.ims_reg_status_not_registered : R.string.ims_reg_status_registered);
        }
    }
}
