package com.android.settingslib.fuelgauge;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.KeyValueListParser;
import android.util.Slog;
/* loaded from: classes3.dex */
public class BatterySaverUtils {
    public static final String ACTION_SHOW_AUTO_SAVER_SUGGESTION = "PNW.autoSaverSuggestion";
    public static final String ACTION_SHOW_START_SAVER_CONFIRMATION = "PNW.startSaverConfirmation";
    private static final boolean DEBUG = false;
    public static final String EXTRA_CONFIRM_TEXT_ONLY = "extra_confirm_only";
    public static final String EXTRA_POWER_SAVE_MODE_TRIGGER = "extra_power_save_mode_trigger";
    public static final String EXTRA_POWER_SAVE_MODE_TRIGGER_LEVEL = "extra_power_save_mode_trigger_level";
    private static final String SYSUI_PACKAGE = "com.android.systemui";
    private static final String TAG = "BatterySaverUtils";

    private BatterySaverUtils() {
    }

    /* loaded from: classes3.dex */
    private static class Parameters {
        private static final int AUTO_SAVER_SUGGESTION_END_NTH = 8;
        private static final int AUTO_SAVER_SUGGESTION_START_NTH = 4;
        public final int endNth;
        private final Context mContext;
        public final int startNth;

        public Parameters(Context context) {
            this.mContext = context;
            String newValue = Settings.Global.getString(this.mContext.getContentResolver(), "low_power_mode_suggestion_params");
            KeyValueListParser parser = new KeyValueListParser(',');
            try {
                parser.setString(newValue);
            } catch (IllegalArgumentException e) {
                Slog.wtf(BatterySaverUtils.TAG, "Bad constants: " + newValue);
            }
            this.startNth = parser.getInt("start_nth", 4);
            this.endNth = parser.getInt("end_nth", 8);
        }
    }

    public static synchronized boolean setPowerSaveMode(Context context, boolean enable, boolean needFirstTimeWarning) {
        synchronized (BatterySaverUtils.class) {
            ContentResolver cr = context.getContentResolver();
            Bundle confirmationExtras = new Bundle(1);
            confirmationExtras.putBoolean(EXTRA_CONFIRM_TEXT_ONLY, false);
            if (enable && needFirstTimeWarning && maybeShowBatterySaverConfirmation(context, confirmationExtras)) {
                return false;
            }
            if (enable && !needFirstTimeWarning) {
                setBatterySaverConfirmationAcknowledged(context);
            }
            if (((PowerManager) context.getSystemService(PowerManager.class)).setPowerSaveModeEnabled(enable)) {
                if (enable) {
                    int count = Settings.Secure.getInt(cr, "low_power_manual_activation_count", 0) + 1;
                    Settings.Secure.putInt(cr, "low_power_manual_activation_count", count);
                    Parameters parameters = new Parameters(context);
                    if (count >= parameters.startNth && count <= parameters.endNth && Settings.Global.getInt(cr, "low_power_trigger_level", 0) == 0 && Settings.Secure.getInt(cr, "suppress_auto_battery_saver_suggestion", 0) == 0) {
                        showAutoBatterySaverSuggestion(context, confirmationExtras);
                    }
                }
                return true;
            }
            return false;
        }
    }

    public static boolean maybeShowBatterySaverConfirmation(Context context, Bundle extras) {
        if (Settings.Secure.getInt(context.getContentResolver(), "low_power_warning_acknowledged", 0) != 0) {
            return false;
        }
        context.sendBroadcast(getSystemUiBroadcast(ACTION_SHOW_START_SAVER_CONFIRMATION, extras));
        return true;
    }

    private static void showAutoBatterySaverSuggestion(Context context, Bundle extras) {
        context.sendBroadcast(getSystemUiBroadcast(ACTION_SHOW_AUTO_SAVER_SUGGESTION, extras));
    }

    private static Intent getSystemUiBroadcast(String action, Bundle extras) {
        Intent i = new Intent(action);
        i.setFlags(268435456);
        i.setPackage("com.android.systemui");
        i.putExtras(extras);
        return i;
    }

    private static void setBatterySaverConfirmationAcknowledged(Context context) {
        Settings.Secure.putInt(context.getContentResolver(), "low_power_warning_acknowledged", 1);
    }

    public static void suppressAutoBatterySaver(Context context) {
        Settings.Secure.putInt(context.getContentResolver(), "suppress_auto_battery_saver_suggestion", 1);
    }

    public static void setAutoBatterySaverTriggerLevel(Context context, int level) {
        if (level > 0) {
            suppressAutoBatterySaver(context);
        }
        Settings.Global.putInt(context.getContentResolver(), "low_power_trigger_level", level);
    }

    public static void ensureAutoBatterySaver(Context context, int level) {
        if (Settings.Global.getInt(context.getContentResolver(), "low_power_trigger_level", 0) == 0) {
            setAutoBatterySaverTriggerLevel(context, level);
        }
    }

    public static void revertScheduleToNoneIfNeeded(Context context) {
        ContentResolver resolver = context.getContentResolver();
        int currentMode = Settings.Global.getInt(resolver, "automatic_power_save_mode", 0);
        boolean providerConfigured = !TextUtils.isEmpty(context.getString(17039675));
        if (currentMode == 1 && !providerConfigured) {
            Settings.Global.putInt(resolver, "low_power_trigger_level", 0);
            Settings.Global.putInt(resolver, "automatic_power_save_mode", 0);
        }
    }
}
