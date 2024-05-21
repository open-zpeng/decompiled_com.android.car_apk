package com.android.settingslib.display;

import android.content.Context;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.MathUtils;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;
import com.android.settingslib.R;
import java.util.Arrays;
/* loaded from: classes3.dex */
public class DisplayDensityUtils {
    private static final String LOG_TAG = "DisplayDensityUtils";
    private static final float MAX_SCALE = 1.5f;
    private static final int MIN_DIMENSION_DP = 320;
    private static final float MIN_SCALE = 0.85f;
    private static final float MIN_SCALE_INTERVAL = 0.09f;
    private final int mCurrentIndex;
    private final int mDefaultDensity;
    private final String[] mEntries;
    private final int[] mValues;
    public static final int SUMMARY_DEFAULT = R.string.screen_zoom_summary_default;
    private static final int SUMMARY_CUSTOM = R.string.screen_zoom_summary_custom;
    private static final int[] SUMMARIES_SMALLER = {R.string.screen_zoom_summary_small};
    private static final int[] SUMMARIES_LARGER = {R.string.screen_zoom_summary_large, R.string.screen_zoom_summary_very_large, R.string.screen_zoom_summary_extremely_large};

    public DisplayDensityUtils(Context context) {
        int currentDensityIndex;
        int newLength;
        int defaultDensity = getDefaultDisplayDensity(0);
        if (defaultDensity <= 0) {
            this.mEntries = null;
            this.mValues = null;
            this.mDefaultDensity = 0;
            this.mCurrentIndex = -1;
            return;
        }
        Resources res = context.getResources();
        DisplayMetrics metrics = new DisplayMetrics();
        context.getDisplay().getRealMetrics(metrics);
        int currentDensity = metrics.densityDpi;
        int currentDensityIndex2 = -1;
        int minDimensionPx = Math.min(metrics.widthPixels, metrics.heightPixels);
        int maxDensity = (minDimensionPx * 160) / MIN_DIMENSION_DP;
        float maxScale = Math.min((float) MAX_SCALE, maxDensity / defaultDensity);
        int numLarger = (int) MathUtils.constrain((maxScale - 1.0f) / MIN_SCALE_INTERVAL, 0.0f, SUMMARIES_LARGER.length);
        int numSmaller = (int) MathUtils.constrain(1.6666664f, 0.0f, SUMMARIES_SMALLER.length);
        String[] entries = new String[numSmaller + 1 + numLarger];
        int[] values = new int[entries.length];
        int curIndex = 0;
        if (numSmaller > 0) {
            float interval = 0.14999998f / numSmaller;
            int i = numSmaller - 1;
            while (i >= 0) {
                DisplayMetrics metrics2 = metrics;
                int density = ((int) (defaultDensity * (1.0f - ((i + 1) * interval)))) & (-2);
                if (currentDensity == density) {
                    currentDensityIndex2 = curIndex;
                }
                entries[curIndex] = res.getString(SUMMARIES_SMALLER[i]);
                values[curIndex] = density;
                curIndex++;
                i--;
                metrics = metrics2;
            }
        }
        currentDensityIndex2 = currentDensity == defaultDensity ? curIndex : currentDensityIndex2;
        values[curIndex] = defaultDensity;
        entries[curIndex] = res.getString(SUMMARY_DEFAULT);
        int curIndex2 = curIndex + 1;
        if (numLarger <= 0) {
            currentDensityIndex = currentDensityIndex2;
        } else {
            float interval2 = (maxScale - 1.0f) / numLarger;
            for (int i2 = 0; i2 < numLarger; i2++) {
                int currentDensityIndex3 = currentDensityIndex2;
                int density2 = ((int) (defaultDensity * (((i2 + 1) * interval2) + 1.0f))) & (-2);
                if (currentDensity != density2) {
                    currentDensityIndex2 = currentDensityIndex3;
                } else {
                    currentDensityIndex2 = curIndex2;
                }
                values[curIndex2] = density2;
                entries[curIndex2] = res.getString(SUMMARIES_LARGER[i2]);
                curIndex2++;
            }
            currentDensityIndex = currentDensityIndex2;
        }
        if (currentDensityIndex >= 0) {
            newLength = currentDensityIndex;
        } else {
            int displayIndex = values.length;
            int newLength2 = displayIndex + 1;
            values = Arrays.copyOf(values, newLength2);
            values[curIndex2] = currentDensity;
            entries = (String[]) Arrays.copyOf(entries, newLength2);
            entries[curIndex2] = res.getString(SUMMARY_CUSTOM, Integer.valueOf(currentDensity));
            newLength = curIndex2;
        }
        this.mDefaultDensity = defaultDensity;
        this.mCurrentIndex = newLength;
        this.mEntries = entries;
        this.mValues = values;
    }

    public String[] getEntries() {
        return this.mEntries;
    }

    public int[] getValues() {
        return this.mValues;
    }

    public int getCurrentIndex() {
        return this.mCurrentIndex;
    }

    public int getDefaultDensity() {
        return this.mDefaultDensity;
    }

    private static int getDefaultDisplayDensity(int displayId) {
        try {
            IWindowManager wm = WindowManagerGlobal.getWindowManagerService();
            return wm.getInitialDisplayDensity(displayId);
        } catch (RemoteException e) {
            return -1;
        }
    }

    public static void clearForcedDisplayDensity(final int displayId) {
        final int userId = UserHandle.myUserId();
        AsyncTask.execute(new Runnable() { // from class: com.android.settingslib.display.-$$Lambda$DisplayDensityUtils$FjSo_v2dJihYeklLmCubVRPf_nw
            @Override // java.lang.Runnable
            public final void run() {
                DisplayDensityUtils.lambda$clearForcedDisplayDensity$0(displayId, userId);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ void lambda$clearForcedDisplayDensity$0(int displayId, int userId) {
        try {
            IWindowManager wm = WindowManagerGlobal.getWindowManagerService();
            wm.clearForcedDisplayDensityForUser(displayId, userId);
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "Unable to clear forced display density setting");
        }
    }

    public static void setForcedDisplayDensity(final int displayId, final int density) {
        final int userId = UserHandle.myUserId();
        AsyncTask.execute(new Runnable() { // from class: com.android.settingslib.display.-$$Lambda$DisplayDensityUtils$jbnNZEy3zYf8rJTNV5wQSa3Z5eQ
            @Override // java.lang.Runnable
            public final void run() {
                DisplayDensityUtils.lambda$setForcedDisplayDensity$1(displayId, density, userId);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ void lambda$setForcedDisplayDensity$1(int displayId, int density, int userId) {
        try {
            IWindowManager wm = WindowManagerGlobal.getWindowManagerService();
            wm.setForcedDisplayDensityForUser(displayId, density, userId);
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "Unable to save forced display density setting");
        }
    }
}
