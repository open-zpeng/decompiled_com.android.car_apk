package com.android.settingslib.utils;

import android.content.Context;
import android.icu.text.DisplayContext;
import android.icu.text.MeasureFormat;
import android.icu.text.RelativeDateTimeFormatter;
import android.icu.util.Measure;
import android.icu.util.MeasureUnit;
import android.icu.util.ULocale;
import android.text.SpannableStringBuilder;
import android.text.style.TtsSpan;
import com.android.settingslib.R;
import java.util.ArrayList;
import java.util.Locale;
/* loaded from: classes3.dex */
public class StringUtil {
    public static final int SECONDS_PER_DAY = 86400;
    public static final int SECONDS_PER_HOUR = 3600;
    public static final int SECONDS_PER_MINUTE = 60;

    public static CharSequence formatElapsedTime(Context context, double millis, boolean withSeconds) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int seconds = (int) Math.floor(millis / 1000.0d);
        if (!withSeconds) {
            seconds += 30;
        }
        int days = 0;
        int hours = 0;
        int minutes = 0;
        if (seconds >= 86400) {
            days = seconds / SECONDS_PER_DAY;
            seconds -= SECONDS_PER_DAY * days;
        }
        if (seconds >= 3600) {
            hours = seconds / SECONDS_PER_HOUR;
            seconds -= hours * SECONDS_PER_HOUR;
        }
        if (seconds >= 60) {
            minutes = seconds / 60;
            seconds -= minutes * 60;
        }
        ArrayList<Measure> measureList = new ArrayList<>(4);
        if (days > 0) {
            measureList.add(new Measure(Integer.valueOf(days), MeasureUnit.DAY));
        }
        if (hours > 0) {
            measureList.add(new Measure(Integer.valueOf(hours), MeasureUnit.HOUR));
        }
        if (minutes > 0) {
            measureList.add(new Measure(Integer.valueOf(minutes), MeasureUnit.MINUTE));
        }
        if (withSeconds && seconds > 0) {
            measureList.add(new Measure(Integer.valueOf(seconds), MeasureUnit.SECOND));
        }
        if (measureList.size() == 0) {
            measureList.add(new Measure(0, withSeconds ? MeasureUnit.SECOND : MeasureUnit.MINUTE));
        }
        Measure[] measureArray = (Measure[]) measureList.toArray(new Measure[measureList.size()]);
        Locale locale = context.getResources().getConfiguration().locale;
        MeasureFormat measureFormat = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT);
        sb.append((CharSequence) measureFormat.formatMeasures(measureArray));
        if (measureArray.length == 1 && MeasureUnit.MINUTE.equals(measureArray[0].getUnit())) {
            TtsSpan ttsSpan = new TtsSpan.MeasureBuilder().setNumber(minutes).setUnit("minute").build();
            sb.setSpan(ttsSpan, 0, sb.length(), 33);
        }
        return sb;
    }

    public static CharSequence formatRelativeTime(Context context, double millis, boolean withSeconds, RelativeDateTimeFormatter.Style formatStyle) {
        RelativeDateTimeFormatter.RelativeUnit unit;
        int value;
        int seconds = (int) Math.floor(millis / 1000.0d);
        if (withSeconds && seconds < 120) {
            return context.getResources().getString(R.string.time_unit_just_now);
        }
        if (seconds < 7200) {
            unit = RelativeDateTimeFormatter.RelativeUnit.MINUTES;
            value = (seconds + 30) / 60;
        } else if (seconds < 172800) {
            unit = RelativeDateTimeFormatter.RelativeUnit.HOURS;
            value = (seconds + 1800) / SECONDS_PER_HOUR;
        } else {
            unit = RelativeDateTimeFormatter.RelativeUnit.DAYS;
            value = (43200 + seconds) / SECONDS_PER_DAY;
        }
        Locale locale = context.getResources().getConfiguration().locale;
        RelativeDateTimeFormatter formatter = RelativeDateTimeFormatter.getInstance(ULocale.forLocale(locale), null, formatStyle, DisplayContext.CAPITALIZATION_FOR_MIDDLE_OF_SENTENCE);
        return formatter.format(value, RelativeDateTimeFormatter.Direction.LAST, unit);
    }

    @Deprecated
    public static CharSequence formatRelativeTime(Context context, double millis, boolean withSeconds) {
        return formatRelativeTime(context, millis, withSeconds, RelativeDateTimeFormatter.Style.LONG);
    }
}
