package com.android.settingslib.deviceinfo;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import com.android.settingslib.R;
import com.android.settingslib.core.lifecycle.Lifecycle;
import java.net.InetAddress;
import java.util.Iterator;
/* loaded from: classes3.dex */
public abstract class AbstractIpAddressPreferenceController extends AbstractConnectivityPreferenceController {
    private static final String[] CONNECTIVITY_INTENTS = {"android.net.conn.CONNECTIVITY_CHANGE", "android.net.wifi.LINK_CONFIGURATION_CHANGED", "android.net.wifi.STATE_CHANGE"};
    @VisibleForTesting
    static final String KEY_IP_ADDRESS = "wifi_ip_address";
    private final ConnectivityManager mCM;
    private Preference mIpAddress;

    public AbstractIpAddressPreferenceController(Context context, Lifecycle lifecycle) {
        super(context, lifecycle);
        this.mCM = (ConnectivityManager) context.getSystemService(ConnectivityManager.class);
    }

    @Override // com.android.settingslib.core.AbstractPreferenceController
    public boolean isAvailable() {
        return true;
    }

    @Override // com.android.settingslib.core.AbstractPreferenceController, com.android.settingslib.core.ConfirmationDialogController
    public String getPreferenceKey() {
        return KEY_IP_ADDRESS;
    }

    @Override // com.android.settingslib.core.AbstractPreferenceController
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        this.mIpAddress = screen.findPreference(KEY_IP_ADDRESS);
        updateConnectivity();
    }

    @Override // com.android.settingslib.deviceinfo.AbstractConnectivityPreferenceController
    protected String[] getConnectivityIntents() {
        return CONNECTIVITY_INTENTS;
    }

    @Override // com.android.settingslib.deviceinfo.AbstractConnectivityPreferenceController
    protected void updateConnectivity() {
        String ipAddress = getDefaultIpAddresses(this.mCM);
        if (ipAddress != null) {
            this.mIpAddress.setSummary(ipAddress);
        } else {
            this.mIpAddress.setSummary(R.string.status_unavailable);
        }
    }

    private static String getDefaultIpAddresses(ConnectivityManager cm) {
        LinkProperties prop = cm.getActiveLinkProperties();
        return formatIpAddresses(prop);
    }

    private static String formatIpAddresses(LinkProperties prop) {
        if (prop == null) {
            return null;
        }
        Iterator<InetAddress> iter = prop.getAllAddresses().iterator();
        if (!iter.hasNext()) {
            return null;
        }
        StringBuilder addresses = new StringBuilder();
        while (iter.hasNext()) {
            addresses.append(iter.next().getHostAddress());
            if (iter.hasNext()) {
                addresses.append("\n");
            }
        }
        return addresses.toString();
    }
}
