package com.android.settingslib.core.instrumentation;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Pair;
import java.util.ArrayList;
import java.util.List;
/* loaded from: classes3.dex */
public class MetricsFeatureProvider {
    public static final String EXTRA_SOURCE_METRICS_CATEGORY = ":settings:source_metrics";
    protected List<LogWriter> mLoggerWriters = new ArrayList();

    public MetricsFeatureProvider() {
        installLogWriters();
    }

    protected void installLogWriters() {
        this.mLoggerWriters.add(new EventLogWriter());
    }

    public int getAttribution(Activity activity) {
        Intent intent;
        if (activity != null && (intent = activity.getIntent()) != null) {
            return intent.getIntExtra(EXTRA_SOURCE_METRICS_CATEGORY, 0);
        }
        return 0;
    }

    public void visible(Context context, int source, int category) {
        for (LogWriter writer : this.mLoggerWriters) {
            writer.visible(context, source, category);
        }
    }

    public void hidden(Context context, int category) {
        for (LogWriter writer : this.mLoggerWriters) {
            writer.hidden(context, category);
        }
    }

    public void action(Context context, int category, Pair<Integer, Object>... taggedData) {
        for (LogWriter writer : this.mLoggerWriters) {
            writer.action(context, category, taggedData);
        }
    }

    public void action(Context context, int category, String pkg) {
        for (LogWriter writer : this.mLoggerWriters) {
            writer.action(context, category, pkg);
        }
    }

    public void action(int attribution, int action, int pageId, String key, int value) {
        for (LogWriter writer : this.mLoggerWriters) {
            writer.action(attribution, action, pageId, key, value);
        }
    }

    public void action(Context context, int category, int value) {
        for (LogWriter writer : this.mLoggerWriters) {
            writer.action(context, category, value);
        }
    }

    public void action(Context context, int category, boolean value) {
        for (LogWriter writer : this.mLoggerWriters) {
            writer.action(context, category, value);
        }
    }

    public int getMetricsCategory(Object object) {
        if (object == null || !(object instanceof Instrumentable)) {
            return 0;
        }
        return ((Instrumentable) object).getMetricsCategory();
    }

    public void logDashboardStartIntent(Context context, Intent intent, int sourceMetricsCategory) {
        if (intent == null) {
            return;
        }
        ComponentName cn = intent.getComponent();
        if (cn == null) {
            String action = intent.getAction();
            if (TextUtils.isEmpty(action)) {
                return;
            }
            action(sourceMetricsCategory, 830, 0, action, 0);
        } else if (TextUtils.equals(cn.getPackageName(), context.getPackageName())) {
        } else {
            action(sourceMetricsCategory, 830, 0, cn.flattenToString(), 0);
        }
    }
}
