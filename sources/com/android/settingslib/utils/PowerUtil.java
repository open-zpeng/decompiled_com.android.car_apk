package com.android.settingslib.utils;

import android.content.Context;
import android.icu.text.MeasureFormat;
import android.icu.util.Measure;
import android.icu.util.MeasureUnit;
import android.text.TextUtils;
import android.text.format.DateFormat;
import androidx.annotation.Nullable;
import com.android.settingslib.R;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
/* loaded from: classes3.dex */
public class PowerUtil {
    private static final long SEVEN_MINUTES_MILLIS = TimeUnit.MINUTES.toMillis(7);
    private static final long FIFTEEN_MINUTES_MILLIS = TimeUnit.MINUTES.toMillis(15);
    private static final long ONE_DAY_MILLIS = TimeUnit.DAYS.toMillis(1);
    private static final long TWO_DAYS_MILLIS = TimeUnit.DAYS.toMillis(2);
    private static final long ONE_HOUR_MILLIS = TimeUnit.HOURS.toMillis(1);

    public static String getBatteryRemainingStringFormatted(Context context, long drainTimeMs, @Nullable String percentageString, boolean basedOnUsage) {
        if (drainTimeMs > 0) {
            if (drainTimeMs <= SEVEN_MINUTES_MILLIS) {
                return getShutdownImminentString(context, percentageString);
            }
            long j = FIFTEEN_MINUTES_MILLIS;
            if (drainTimeMs <= j) {
                CharSequence timeString = StringUtil.formatElapsedTime(context, j, false);
                return getUnderFifteenString(context, timeString, percentageString);
            } else if (drainTimeMs >= TWO_DAYS_MILLIS) {
                return getMoreThanTwoDaysString(context, percentageString);
            } else {
                if (drainTimeMs >= ONE_DAY_MILLIS) {
                    return getMoreThanOneDayString(context, drainTimeMs, percentageString, basedOnUsage);
                }
                return getRegularTimeRemainingString(context, drainTimeMs, percentageString, basedOnUsage);
            }
        }
        return null;
    }

    @Nullable
    public static String getBatteryRemainingShortStringFormatted(Context context, long drainTimeMs) {
        if (drainTimeMs <= 0) {
            return null;
        }
        if (drainTimeMs <= ONE_DAY_MILLIS) {
            return getRegularTimeRemainingShortString(context, drainTimeMs);
        }
        return getMoreThanOneDayShortString(context, drainTimeMs, R.string.power_remaining_duration_only_short);
    }

    public static String getBatteryTipStringFormatted(Context context, long drainTimeMs) {
        if (drainTimeMs <= 0) {
            return null;
        }
        if (drainTimeMs <= ONE_DAY_MILLIS) {
            return context.getString(R.string.power_suggestion_extend_battery, getDateTimeStringFromMs(context, drainTimeMs));
        }
        return getMoreThanOneDayShortString(context, drainTimeMs, R.string.power_remaining_only_more_than_subtext);
    }

    private static String getShutdownImminentString(Context context, String percentageString) {
        return TextUtils.isEmpty(percentageString) ? context.getString(R.string.power_remaining_duration_only_shutdown_imminent) : context.getString(R.string.power_remaining_duration_shutdown_imminent, percentageString);
    }

    private static String getUnderFifteenString(Context context, CharSequence timeString, String percentageString) {
        return TextUtils.isEmpty(percentageString) ? context.getString(R.string.power_remaining_less_than_duration_only, timeString) : context.getString(R.string.power_remaining_less_than_duration, timeString, percentageString);
    }

    private static String getMoreThanOneDayString(Context context, long drainTimeMs, String percentageString, boolean basedOnUsage) {
        int id;
        int id2;
        long roundedTimeMs = roundTimeToNearestThreshold(drainTimeMs, ONE_HOUR_MILLIS);
        CharSequence timeString = StringUtil.formatElapsedTime(context, roundedTimeMs, false);
        if (TextUtils.isEmpty(percentageString)) {
            if (basedOnUsage) {
                id2 = R.string.power_remaining_duration_only_enhanced;
            } else {
                id2 = R.string.power_remaining_duration_only;
            }
            return context.getString(id2, timeString);
        }
        if (basedOnUsage) {
            id = R.string.power_discharging_duration_enhanced;
        } else {
            id = R.string.power_discharging_duration;
        }
        return context.getString(id, timeString, percentageString);
    }

    private static String getMoreThanOneDayShortString(Context context, long drainTimeMs, int resId) {
        long roundedTimeMs = roundTimeToNearestThreshold(drainTimeMs, ONE_HOUR_MILLIS);
        CharSequence timeString = StringUtil.formatElapsedTime(context, roundedTimeMs, false);
        return context.getString(resId, timeString);
    }

    private static String getMoreThanTwoDaysString(Context context, String percentageString) {
        Locale currentLocale = context.getResources().getConfiguration().getLocales().get(0);
        MeasureFormat frmt = MeasureFormat.getInstance(currentLocale, MeasureFormat.FormatWidth.SHORT);
        Measure daysMeasure = new Measure(2, MeasureUnit.DAY);
        if (TextUtils.isEmpty(percentageString)) {
            return context.getString(R.string.power_remaining_only_more_than_subtext, frmt.formatMeasures(daysMeasure));
        }
        return context.getString(R.string.power_remaining_more_than_subtext, frmt.formatMeasures(daysMeasure), percentageString);
    }

    private static String getRegularTimeRemainingString(Context context, long drainTimeMs, String percentageString, boolean basedOnUsage) {
        int id;
        int id2;
        CharSequence timeString = getDateTimeStringFromMs(context, drainTimeMs);
        if (TextUtils.isEmpty(percentageString)) {
            if (basedOnUsage) {
                id2 = R.string.power_discharge_by_only_enhanced;
            } else {
                id2 = R.string.power_discharge_by_only;
            }
            return context.getString(id2, timeString);
        }
        if (basedOnUsage) {
            id = R.string.power_discharge_by_enhanced;
        } else {
            id = R.string.power_discharge_by;
        }
        return context.getString(id, timeString, percentageString);
    }

    private static CharSequence getDateTimeStringFromMs(Context context, long drainTimeMs) {
        long roundedTimeOfDayMs = roundTimeToNearestThreshold(System.currentTimeMillis() + drainTimeMs, FIFTEEN_MINUTES_MILLIS);
        String skeleton = DateFormat.getTimeFormatString(context);
        android.icu.text.DateFormat fmt = android.icu.text.DateFormat.getInstanceForSkeleton(skeleton);
        Date date = Date.from(Instant.ofEpochMilli(roundedTimeOfDayMs));
        return fmt.format(date);
    }

    private static String getRegularTimeRemainingShortString(Context context, long drainTimeMs) {
        long roundedTimeOfDayMs = roundTimeToNearestThreshold(System.currentTimeMillis() + drainTimeMs, FIFTEEN_MINUTES_MILLIS);
        String skeleton = DateFormat.getTimeFormatString(context);
        android.icu.text.DateFormat fmt = android.icu.text.DateFormat.getInstanceForSkeleton(skeleton);
        Date date = Date.from(Instant.ofEpochMilli(roundedTimeOfDayMs));
        CharSequence timeString = fmt.format(date);
        return context.getString(R.string.power_discharge_by_only_short, timeString);
    }

    public static long convertUsToMs(long timeUs) {
        return timeUs / 1000;
    }

    public static long convertMsToUs(long timeMs) {
        return 1000 * timeMs;
    }

    public static long roundTimeToNearestThreshold(long drainTime, long threshold) {
        long time = Math.abs(drainTime);
        long multiple = Math.abs(threshold);
        long remainder = time % multiple;
        if (remainder < multiple / 2) {
            return time - remainder;
        }
        return (time - remainder) + multiple;
    }
}
