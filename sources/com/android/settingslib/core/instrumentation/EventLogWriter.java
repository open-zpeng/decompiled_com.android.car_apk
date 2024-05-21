package com.android.settingslib.core.instrumentation;

import android.content.Context;
import android.metrics.LogMaker;
import android.text.TextUtils;
import android.util.Pair;
import com.android.internal.logging.MetricsLogger;
/* loaded from: classes3.dex */
public class EventLogWriter implements LogWriter {
    @Override // com.android.settingslib.core.instrumentation.LogWriter
    public void visible(Context context, int source, int category) {
        LogMaker logMaker = new LogMaker(category).setType(1).addTaggedData(833, Integer.valueOf(source));
        MetricsLogger.action(logMaker);
    }

    @Override // com.android.settingslib.core.instrumentation.LogWriter
    public void hidden(Context context, int category) {
        MetricsLogger.hidden(context, category);
    }

    @Override // com.android.settingslib.core.instrumentation.LogWriter
    public void action(Context context, int category, Pair<Integer, Object>... taggedData) {
        LogMaker logMaker = new LogMaker(category).setType(4);
        if (taggedData != null) {
            for (Pair<Integer, Object> pair : taggedData) {
                logMaker.addTaggedData(((Integer) pair.first).intValue(), pair.second);
            }
        }
        MetricsLogger.action(logMaker);
    }

    @Override // com.android.settingslib.core.instrumentation.LogWriter
    public void action(Context context, int category, int value) {
        MetricsLogger.action(context, category, value);
    }

    @Override // com.android.settingslib.core.instrumentation.LogWriter
    public void action(Context context, int category, boolean value) {
        MetricsLogger.action(context, category, value);
    }

    @Override // com.android.settingslib.core.instrumentation.LogWriter
    public void action(Context context, int category, String pkg) {
        LogMaker logMaker = new LogMaker(category).setType(4).setPackageName(pkg);
        MetricsLogger.action(logMaker);
    }

    @Override // com.android.settingslib.core.instrumentation.LogWriter
    public void action(int attribution, int action, int pageId, String key, int value) {
        LogMaker logMaker = new LogMaker(action).setType(4);
        if (attribution != 0) {
            logMaker.addTaggedData(833, Integer.valueOf(pageId));
        }
        if (!TextUtils.isEmpty(key)) {
            logMaker.addTaggedData(854, key);
            logMaker.addTaggedData(1089, Integer.valueOf(value));
        }
        MetricsLogger.action(logMaker);
    }
}
