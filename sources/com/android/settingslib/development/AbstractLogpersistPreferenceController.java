package com.android.settingslib.development;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.SystemProperties;
import android.text.TextUtils;
import androidx.annotation.VisibleForTesting;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import com.android.settingslib.R;
import com.android.settingslib.core.ConfirmationDialogController;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnCreate;
import com.android.settingslib.core.lifecycle.events.OnDestroy;
/* loaded from: classes3.dex */
public abstract class AbstractLogpersistPreferenceController extends DeveloperOptionsPreferenceController implements Preference.OnPreferenceChangeListener, LifecycleObserver, OnCreate, OnDestroy, ConfirmationDialogController {
    @VisibleForTesting
    static final String ACTUAL_LOGPERSIST_PROPERTY = "logd.logpersistd";
    @VisibleForTesting
    static final String ACTUAL_LOGPERSIST_PROPERTY_BUFFER = "logd.logpersistd.buffer";
    private static final String ACTUAL_LOGPERSIST_PROPERTY_ENABLE = "logd.logpersistd.enable";
    private static final String SELECT_LOGPERSIST_KEY = "select_logpersist";
    private static final String SELECT_LOGPERSIST_PROPERTY = "persist.logd.logpersistd";
    private static final String SELECT_LOGPERSIST_PROPERTY_BUFFER = "persist.logd.logpersistd.buffer";
    private static final String SELECT_LOGPERSIST_PROPERTY_CLEAR = "clear";
    @VisibleForTesting
    static final String SELECT_LOGPERSIST_PROPERTY_SERVICE = "logcatd";
    private static final String SELECT_LOGPERSIST_PROPERTY_STOP = "stop";
    private ListPreference mLogpersist;
    private boolean mLogpersistCleared;
    private final BroadcastReceiver mReceiver;

    public AbstractLogpersistPreferenceController(Context context, Lifecycle lifecycle) {
        super(context);
        this.mReceiver = new BroadcastReceiver() { // from class: com.android.settingslib.development.AbstractLogpersistPreferenceController.1
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context2, Intent intent) {
                String currentValue = intent.getStringExtra(AbstractLogdSizePreferenceController.EXTRA_CURRENT_LOGD_VALUE);
                AbstractLogpersistPreferenceController.this.onLogdSizeSettingUpdate(currentValue);
            }
        };
        if (isAvailable() && lifecycle != null) {
            lifecycle.addObserver(this);
        }
    }

    @Override // com.android.settingslib.development.DeveloperOptionsPreferenceController, com.android.settingslib.core.AbstractPreferenceController
    public boolean isAvailable() {
        return TextUtils.equals(SystemProperties.get("ro.debuggable", "0"), "1");
    }

    @Override // com.android.settingslib.core.AbstractPreferenceController, com.android.settingslib.core.ConfirmationDialogController
    public String getPreferenceKey() {
        return SELECT_LOGPERSIST_KEY;
    }

    @Override // com.android.settingslib.development.DeveloperOptionsPreferenceController, com.android.settingslib.core.AbstractPreferenceController
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        if (isAvailable()) {
            this.mLogpersist = (ListPreference) screen.findPreference(SELECT_LOGPERSIST_KEY);
        }
    }

    @Override // androidx.preference.Preference.OnPreferenceChangeListener
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == this.mLogpersist) {
            writeLogpersistOption(newValue, false);
            return true;
        }
        return false;
    }

    @Override // com.android.settingslib.core.lifecycle.events.OnCreate
    public void onCreate(Bundle savedInstanceState) {
        LocalBroadcastManager.getInstance(this.mContext).registerReceiver(this.mReceiver, new IntentFilter(AbstractLogdSizePreferenceController.ACTION_LOGD_SIZE_UPDATED));
    }

    @Override // com.android.settingslib.core.lifecycle.events.OnDestroy
    public void onDestroy() {
        LocalBroadcastManager.getInstance(this.mContext).unregisterReceiver(this.mReceiver);
    }

    public void enablePreference(boolean enabled) {
        if (isAvailable()) {
            this.mLogpersist.setEnabled(enabled);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onLogdSizeSettingUpdate(String currentValue) {
        if (this.mLogpersist != null) {
            String currentLogpersistEnable = SystemProperties.get(ACTUAL_LOGPERSIST_PROPERTY_ENABLE);
            if (currentLogpersistEnable == null || !currentLogpersistEnable.equals("true") || currentValue.equals("32768")) {
                writeLogpersistOption(null, true);
                this.mLogpersist.setEnabled(false);
            } else if (DevelopmentSettingsEnabler.isDevelopmentSettingsEnabled(this.mContext)) {
                this.mLogpersist.setEnabled(true);
            }
        }
    }

    public void updateLogpersistValues() {
        if (this.mLogpersist == null) {
            return;
        }
        String currentValue = SystemProperties.get(ACTUAL_LOGPERSIST_PROPERTY);
        if (currentValue == null) {
            currentValue = "";
        }
        String currentBuffers = SystemProperties.get(ACTUAL_LOGPERSIST_PROPERTY_BUFFER);
        currentBuffers = (currentBuffers == null || currentBuffers.length() == 0) ? "all" : "all";
        int index = 0;
        if (currentValue.equals(SELECT_LOGPERSIST_PROPERTY_SERVICE)) {
            index = 1;
            if (currentBuffers.equals("kernel")) {
                index = 3;
            } else if (!currentBuffers.equals("all") && !currentBuffers.contains("radio") && currentBuffers.contains("security") && currentBuffers.contains("kernel")) {
                index = 2;
                if (!currentBuffers.contains("default")) {
                    String[] contains = {"main", "events", "system", "crash"};
                    int length = contains.length;
                    int i = 0;
                    while (true) {
                        if (i >= length) {
                            break;
                        }
                        String type = contains[i];
                        if (currentBuffers.contains(type)) {
                            i++;
                        } else {
                            index = 1;
                            break;
                        }
                    }
                }
            }
        }
        this.mLogpersist.setValue(this.mContext.getResources().getStringArray(R.array.select_logpersist_values)[index]);
        this.mLogpersist.setSummary(this.mContext.getResources().getStringArray(R.array.select_logpersist_summaries)[index]);
        if (index != 0) {
            this.mLogpersistCleared = false;
        } else if (!this.mLogpersistCleared) {
            SystemProperties.set(ACTUAL_LOGPERSIST_PROPERTY, SELECT_LOGPERSIST_PROPERTY_CLEAR);
            SystemPropPoker.getInstance().poke();
            this.mLogpersistCleared = true;
        }
    }

    protected void setLogpersistOff(boolean update) {
        String currentValue;
        SystemProperties.set(SELECT_LOGPERSIST_PROPERTY_BUFFER, "");
        SystemProperties.set(ACTUAL_LOGPERSIST_PROPERTY_BUFFER, "");
        SystemProperties.set(SELECT_LOGPERSIST_PROPERTY, "");
        SystemProperties.set(ACTUAL_LOGPERSIST_PROPERTY, update ? "" : SELECT_LOGPERSIST_PROPERTY_STOP);
        SystemPropPoker.getInstance().poke();
        if (update) {
            updateLogpersistValues();
            return;
        }
        for (int i = 0; i < 3 && (currentValue = SystemProperties.get(ACTUAL_LOGPERSIST_PROPERTY)) != null && !currentValue.equals(""); i++) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
            }
        }
    }

    public void writeLogpersistOption(Object newValue, boolean skipWarning) {
        String currentValue;
        String currentValue2;
        if (this.mLogpersist == null) {
            return;
        }
        String currentTag = SystemProperties.get("persist.log.tag");
        if (currentTag != null && currentTag.startsWith("Settings")) {
            newValue = null;
            skipWarning = true;
        }
        if (newValue == null || newValue.toString().equals("")) {
            if (skipWarning) {
                this.mLogpersistCleared = false;
            } else if (!this.mLogpersistCleared && (currentValue = SystemProperties.get(ACTUAL_LOGPERSIST_PROPERTY)) != null && currentValue.equals(SELECT_LOGPERSIST_PROPERTY_SERVICE)) {
                showConfirmationDialog(this.mLogpersist);
                return;
            }
            setLogpersistOff(true);
            return;
        }
        String currentBuffer = SystemProperties.get(ACTUAL_LOGPERSIST_PROPERTY_BUFFER);
        if (currentBuffer != null && !currentBuffer.equals(newValue.toString())) {
            setLogpersistOff(false);
        }
        SystemProperties.set(SELECT_LOGPERSIST_PROPERTY_BUFFER, newValue.toString());
        SystemProperties.set(SELECT_LOGPERSIST_PROPERTY, SELECT_LOGPERSIST_PROPERTY_SERVICE);
        SystemPropPoker.getInstance().poke();
        for (int i = 0; i < 3 && ((currentValue2 = SystemProperties.get(ACTUAL_LOGPERSIST_PROPERTY)) == null || !currentValue2.equals(SELECT_LOGPERSIST_PROPERTY_SERVICE)); i++) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
            }
        }
        updateLogpersistValues();
    }
}
