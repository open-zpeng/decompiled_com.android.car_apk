package com.android.settingslib.core.instrumentation;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.VisibleForTesting;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
/* loaded from: classes3.dex */
public class SharedPreferencesLogger implements SharedPreferences {
    private static final String LOG_TAG = "SharedPreferencesLogger";
    private final Context mContext;
    private final MetricsFeatureProvider mMetricsFeature;
    private final Set<String> mPreferenceKeySet = new ConcurrentSkipListSet();
    private final String mTag;

    public SharedPreferencesLogger(Context context, String tag, MetricsFeatureProvider metricsFeature) {
        this.mContext = context;
        this.mTag = tag;
        this.mMetricsFeature = metricsFeature;
    }

    @Override // android.content.SharedPreferences
    public Map<String, ?> getAll() {
        return null;
    }

    @Override // android.content.SharedPreferences
    public String getString(String key, String defValue) {
        return defValue;
    }

    @Override // android.content.SharedPreferences
    public Set<String> getStringSet(String key, Set<String> defValues) {
        return defValues;
    }

    @Override // android.content.SharedPreferences
    public int getInt(String key, int defValue) {
        return defValue;
    }

    @Override // android.content.SharedPreferences
    public long getLong(String key, long defValue) {
        return defValue;
    }

    @Override // android.content.SharedPreferences
    public float getFloat(String key, float defValue) {
        return defValue;
    }

    @Override // android.content.SharedPreferences
    public boolean getBoolean(String key, boolean defValue) {
        return defValue;
    }

    @Override // android.content.SharedPreferences
    public boolean contains(String key) {
        return false;
    }

    @Override // android.content.SharedPreferences
    public SharedPreferences.Editor edit() {
        return new EditorLogger();
    }

    @Override // android.content.SharedPreferences
    public void registerOnSharedPreferenceChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
    }

    @Override // android.content.SharedPreferences
    public void unregisterOnSharedPreferenceChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
    }

    @VisibleForTesting
    protected void logValue(String key, Object value) {
        logValue(key, value, false);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void logValue(String key, Object value, boolean forceLog) {
        boolean intVal;
        int intVal2;
        int intVal3;
        String prefKey = buildPrefKey(this.mTag, key);
        if (!forceLog && !this.mPreferenceKeySet.contains(prefKey)) {
            this.mPreferenceKeySet.add(prefKey);
            return;
        }
        if (value instanceof Long) {
            Long longVal = (Long) value;
            if (longVal.longValue() > 2147483647L) {
                intVal3 = Integer.MAX_VALUE;
            } else if (longVal.longValue() < -2147483648L) {
                intVal3 = Integer.MIN_VALUE;
            } else {
                intVal3 = longVal.intValue();
            }
            intVal = intVal3;
        } else if (value instanceof Integer) {
            intVal = ((Integer) value).intValue();
        } else if (value instanceof Boolean) {
            intVal = ((Boolean) value).booleanValue();
        } else if (value instanceof Float) {
            float floatValue = ((Float) value).floatValue();
            if (floatValue > 2.14748365E9f) {
                intVal2 = Integer.MAX_VALUE;
            } else if (floatValue < -2.14748365E9f) {
                intVal2 = Integer.MIN_VALUE;
            } else {
                intVal2 = (int) floatValue;
            }
            intVal = intVal2;
        } else if (value instanceof String) {
            try {
                int intVal4 = Integer.parseInt((String) value);
                intVal = intVal4;
            } catch (NumberFormatException e) {
                Log.w(LOG_TAG, "Tried to log unloggable object=" + value);
                return;
            }
        } else {
            Log.w(LOG_TAG, "Tried to log unloggable object=" + value);
            return;
        }
        this.mMetricsFeature.action(0, 853, 0, prefKey, intVal);
    }

    @VisibleForTesting
    void logPackageName(String key, String value) {
        String prefKey = this.mTag + "/" + key;
        this.mMetricsFeature.action(0, 853, 0, prefKey + ":" + value, 0);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void safeLogValue(String key, String value) {
        new AsyncPackageCheck().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, key, value);
    }

    public static String buildPrefKey(String tag, String key) {
        return tag + "/" + key;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class AsyncPackageCheck extends AsyncTask<String, Void, Void> {
        private AsyncPackageCheck() {
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.os.AsyncTask
        public Void doInBackground(String... params) {
            String key = params[0];
            String value = params[1];
            PackageManager pm = SharedPreferencesLogger.this.mContext.getPackageManager();
            try {
                ComponentName name = ComponentName.unflattenFromString(value);
                if (value != null) {
                    value = name.getPackageName();
                }
            } catch (Exception e) {
            }
            try {
                pm.getPackageInfo(value, 4194304);
                SharedPreferencesLogger.this.logPackageName(key, value);
                return null;
            } catch (PackageManager.NameNotFoundException e2) {
                SharedPreferencesLogger.this.logValue(key, value, true);
                return null;
            }
        }
    }

    /* loaded from: classes3.dex */
    public class EditorLogger implements SharedPreferences.Editor {
        public EditorLogger() {
        }

        @Override // android.content.SharedPreferences.Editor
        public SharedPreferences.Editor putString(String key, String value) {
            SharedPreferencesLogger.this.safeLogValue(key, value);
            return this;
        }

        @Override // android.content.SharedPreferences.Editor
        public SharedPreferences.Editor putStringSet(String key, Set<String> values) {
            SharedPreferencesLogger.this.safeLogValue(key, TextUtils.join(",", values));
            return this;
        }

        @Override // android.content.SharedPreferences.Editor
        public SharedPreferences.Editor putInt(String key, int value) {
            SharedPreferencesLogger.this.logValue(key, Integer.valueOf(value));
            return this;
        }

        @Override // android.content.SharedPreferences.Editor
        public SharedPreferences.Editor putLong(String key, long value) {
            SharedPreferencesLogger.this.logValue(key, Long.valueOf(value));
            return this;
        }

        @Override // android.content.SharedPreferences.Editor
        public SharedPreferences.Editor putFloat(String key, float value) {
            SharedPreferencesLogger.this.logValue(key, Float.valueOf(value));
            return this;
        }

        @Override // android.content.SharedPreferences.Editor
        public SharedPreferences.Editor putBoolean(String key, boolean value) {
            SharedPreferencesLogger.this.logValue(key, Boolean.valueOf(value));
            return this;
        }

        @Override // android.content.SharedPreferences.Editor
        public SharedPreferences.Editor remove(String key) {
            return this;
        }

        @Override // android.content.SharedPreferences.Editor
        public SharedPreferences.Editor clear() {
            return this;
        }

        @Override // android.content.SharedPreferences.Editor
        public boolean commit() {
            return true;
        }

        @Override // android.content.SharedPreferences.Editor
        public void apply() {
        }
    }
}
