package com.android.settingslib.datetime;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.icu.text.TimeZoneFormat;
import android.icu.text.TimeZoneNames;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.TtsSpan;
import android.util.Log;
import androidx.annotation.VisibleForTesting;
import androidx.core.text.BidiFormatter;
import androidx.core.text.TextDirectionHeuristicsCompat;
import com.android.car.UptimeTracker;
import com.android.settingslib.R;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import libcore.timezone.TimeZoneFinder;
import org.xmlpull.v1.XmlPullParserException;
/* loaded from: classes3.dex */
public class ZoneGetter {
    @Deprecated
    public static final String KEY_DISPLAYNAME = "name";
    public static final String KEY_DISPLAY_LABEL = "display_label";
    @Deprecated
    public static final String KEY_GMT = "gmt";
    public static final String KEY_ID = "id";
    public static final String KEY_OFFSET = "offset";
    public static final String KEY_OFFSET_LABEL = "offset_label";
    private static final String TAG = "ZoneGetter";
    private static final String XMLTAG_TIMEZONE = "timezone";

    public static CharSequence getTimeZoneOffsetAndName(Context context, TimeZone tz, Date now) {
        Locale locale = context.getResources().getConfiguration().locale;
        TimeZoneFormat tzFormatter = TimeZoneFormat.getInstance(locale);
        CharSequence gmtText = getGmtOffsetText(tzFormatter, locale, tz, now);
        TimeZoneNames timeZoneNames = TimeZoneNames.getInstance(locale);
        String zoneNameString = getZoneLongName(timeZoneNames, tz, now);
        return zoneNameString == null ? gmtText : TextUtils.concat(gmtText, " ", zoneNameString);
    }

    public static List<Map<String, Object>> getZonesList(Context context) {
        Locale locale = context.getResources().getConfiguration().locale;
        Date now = new Date();
        TimeZoneNames timeZoneNames = TimeZoneNames.getInstance(locale);
        ZoneGetterData data = new ZoneGetterData(context);
        boolean useExemplarLocationForLocalNames = shouldUseExemplarLocationForLocalNames(data, timeZoneNames);
        List<Map<String, Object>> zones = new ArrayList<>();
        for (int i = 0; i < data.zoneCount; i++) {
            TimeZone tz = data.timeZones[i];
            CharSequence gmtOffsetText = data.gmtOffsetTexts[i];
            CharSequence displayName = getTimeZoneDisplayName(data, timeZoneNames, useExemplarLocationForLocalNames, tz, data.olsonIdsToDisplay[i]);
            if (TextUtils.isEmpty(displayName)) {
                displayName = gmtOffsetText;
            }
            int offsetMillis = tz.getOffset(now.getTime());
            Map<String, Object> displayEntry = createDisplayEntry(tz, gmtOffsetText, displayName, offsetMillis);
            zones.add(displayEntry);
        }
        return zones;
    }

    private static Map<String, Object> createDisplayEntry(TimeZone tz, CharSequence gmtOffsetText, CharSequence displayName, int offsetMillis) {
        Map<String, Object> map = new HashMap<>();
        map.put(KEY_ID, tz.getID());
        map.put(KEY_DISPLAYNAME, displayName.toString());
        map.put(KEY_DISPLAY_LABEL, displayName);
        map.put(KEY_GMT, gmtOffsetText.toString());
        map.put(KEY_OFFSET_LABEL, gmtOffsetText);
        map.put(KEY_OFFSET, Integer.valueOf(offsetMillis));
        return map;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static List<String> readTimezonesToDisplay(Context context) {
        List<String> olsonIds = new ArrayList<>();
        try {
            XmlResourceParser xrp = context.getResources().getXml(R.xml.timezones);
            while (xrp.next() != 2) {
                try {
                } catch (Throwable th) {
                    try {
                        throw th;
                    } catch (Throwable th2) {
                        if (xrp != null) {
                            try {
                                xrp.close();
                            } catch (Throwable th3) {
                                th.addSuppressed(th3);
                            }
                        }
                        throw th2;
                    }
                }
            }
            xrp.next();
            while (xrp.getEventType() != 3) {
                while (xrp.getEventType() != 2) {
                    if (xrp.getEventType() == 1) {
                        xrp.close();
                        return olsonIds;
                    }
                    xrp.next();
                }
                if (xrp.getName().equals(XMLTAG_TIMEZONE)) {
                    String olsonId = xrp.getAttributeValue(0);
                    olsonIds.add(olsonId);
                }
                while (xrp.getEventType() != 3) {
                    xrp.next();
                }
                xrp.next();
            }
            xrp.close();
        } catch (IOException e) {
            Log.e(TAG, "Unable to read timezones.xml file");
        } catch (XmlPullParserException e2) {
            Log.e(TAG, "Ill-formatted timezones.xml file");
        }
        return olsonIds;
    }

    private static boolean shouldUseExemplarLocationForLocalNames(ZoneGetterData data, TimeZoneNames timeZoneNames) {
        Set<CharSequence> localZoneNames = new HashSet<>();
        Date now = new Date();
        for (int i = 0; i < data.zoneCount; i++) {
            String olsonId = data.olsonIdsToDisplay[i];
            if (data.localZoneIds.contains(olsonId)) {
                TimeZone tz = data.timeZones[i];
                CharSequence displayName = getZoneLongName(timeZoneNames, tz, now);
                if (displayName == null) {
                    displayName = data.gmtOffsetTexts[i];
                }
                boolean nameIsUnique = localZoneNames.add(displayName);
                if (!nameIsUnique) {
                    return true;
                }
            }
        }
        return false;
    }

    private static CharSequence getTimeZoneDisplayName(ZoneGetterData data, TimeZoneNames timeZoneNames, boolean useExemplarLocationForLocalNames, TimeZone tz, String olsonId) {
        Date now = new Date();
        boolean isLocalZoneId = data.localZoneIds.contains(olsonId);
        boolean preferLongName = isLocalZoneId && !useExemplarLocationForLocalNames;
        if (preferLongName) {
            return getZoneLongName(timeZoneNames, tz, now);
        }
        String canonicalZoneId = android.icu.util.TimeZone.getCanonicalID(tz.getID());
        if (canonicalZoneId == null) {
            canonicalZoneId = tz.getID();
        }
        String displayName = timeZoneNames.getExemplarLocationName(canonicalZoneId);
        if (displayName == null || displayName.isEmpty()) {
            return getZoneLongName(timeZoneNames, tz, now);
        }
        return displayName;
    }

    private static String getZoneLongName(TimeZoneNames names, TimeZone tz, Date now) {
        TimeZoneNames.NameType nameType = tz.inDaylightTime(now) ? TimeZoneNames.NameType.LONG_DAYLIGHT : TimeZoneNames.NameType.LONG_STANDARD;
        return names.getDisplayName(tz.getID(), nameType, now.getTime());
    }

    private static void appendWithTtsSpan(SpannableStringBuilder builder, CharSequence content, TtsSpan span) {
        int start = builder.length();
        builder.append(content);
        builder.setSpan(span, start, builder.length(), 0);
    }

    private static String formatDigits(int input, int minDigits, String localizedDigits) {
        int tens = input / 10;
        int units = input % 10;
        StringBuilder builder = new StringBuilder(minDigits);
        if (input >= 10 || minDigits == 2) {
            builder.append(localizedDigits.charAt(tens));
        }
        builder.append(localizedDigits.charAt(units));
        return builder.toString();
    }

    public static CharSequence getGmtOffsetText(TimeZoneFormat tzFormatter, Locale locale, TimeZone tz, Date now) {
        String gmtPatternPrefix;
        String gmtPatternSuffix;
        TimeZoneFormat.GMTOffsetPatternType patternType;
        int placeholderIndex;
        int offsetMinutes;
        int offsetMinutesRemaining;
        boolean negative;
        int numDigits;
        int number;
        String unit;
        SpannableStringBuilder builder = new SpannableStringBuilder();
        String gmtPattern = tzFormatter.getGMTPattern();
        int placeholderIndex2 = gmtPattern.indexOf("{0}");
        if (placeholderIndex2 == -1) {
            gmtPatternPrefix = "GMT";
            gmtPatternSuffix = "";
        } else {
            gmtPatternPrefix = gmtPattern.substring(0, placeholderIndex2);
            gmtPatternSuffix = gmtPattern.substring(placeholderIndex2 + 3);
        }
        if (!gmtPatternPrefix.isEmpty()) {
            appendWithTtsSpan(builder, gmtPatternPrefix, new TtsSpan.TextBuilder(gmtPatternPrefix).build());
        }
        int offsetMillis = tz.getOffset(now.getTime());
        boolean negative2 = offsetMillis < 0;
        if (negative2) {
            offsetMillis = -offsetMillis;
            patternType = TimeZoneFormat.GMTOffsetPatternType.NEGATIVE_HM;
        } else {
            patternType = TimeZoneFormat.GMTOffsetPatternType.POSITIVE_HM;
        }
        String gmtOffsetPattern = tzFormatter.getGMTOffsetPattern(patternType);
        String localizedDigits = tzFormatter.getGMTOffsetDigits();
        int offsetHours = (int) (offsetMillis / UptimeTracker.MINIMUM_SNAPSHOT_INTERVAL_MS);
        int offsetMinutes2 = (int) (offsetMillis / 60000);
        int offsetMinutesRemaining2 = Math.abs(offsetMinutes2) % 60;
        int i = 0;
        while (i < gmtOffsetPattern.length()) {
            char c = gmtOffsetPattern.charAt(i);
            String gmtPattern2 = gmtPattern;
            if (c == '+' || c == '-') {
                placeholderIndex = placeholderIndex2;
                offsetMinutes = offsetMinutes2;
                offsetMinutesRemaining = offsetMinutesRemaining2;
                negative = negative2;
            } else if (c == 8722) {
                placeholderIndex = placeholderIndex2;
                offsetMinutes = offsetMinutes2;
                offsetMinutesRemaining = offsetMinutesRemaining2;
                negative = negative2;
            } else {
                if (c == 'H' || c == 'm') {
                    placeholderIndex = placeholderIndex2;
                    if (i + 1 < gmtOffsetPattern.length() && gmtOffsetPattern.charAt(i + 1) == c) {
                        numDigits = 2;
                        i++;
                    } else {
                        numDigits = 1;
                    }
                    if (c == 'H') {
                        number = offsetHours;
                        offsetMinutes = offsetMinutes2;
                        unit = "hour";
                    } else {
                        number = offsetMinutesRemaining2;
                        offsetMinutes = offsetMinutes2;
                        unit = "minute";
                    }
                    offsetMinutesRemaining = offsetMinutesRemaining2;
                    negative = negative2;
                    appendWithTtsSpan(builder, formatDigits(number, numDigits, localizedDigits), new TtsSpan.MeasureBuilder().setNumber(number).setUnit(unit).build());
                } else {
                    builder.append(c);
                    placeholderIndex = placeholderIndex2;
                    offsetMinutes = offsetMinutes2;
                    offsetMinutesRemaining = offsetMinutesRemaining2;
                    negative = negative2;
                }
                i++;
                gmtPattern = gmtPattern2;
                offsetMinutes2 = offsetMinutes;
                placeholderIndex2 = placeholderIndex;
                offsetMinutesRemaining2 = offsetMinutesRemaining;
                negative2 = negative;
            }
            String sign = String.valueOf(c);
            appendWithTtsSpan(builder, sign, new TtsSpan.VerbatimBuilder(sign).build());
            i++;
            gmtPattern = gmtPattern2;
            offsetMinutes2 = offsetMinutes;
            placeholderIndex2 = placeholderIndex;
            offsetMinutesRemaining2 = offsetMinutesRemaining;
            negative2 = negative;
        }
        if (!gmtPatternSuffix.isEmpty()) {
            appendWithTtsSpan(builder, gmtPatternSuffix, new TtsSpan.TextBuilder(gmtPatternSuffix).build());
        }
        CharSequence gmtText = new SpannableString(builder);
        BidiFormatter bidiFormatter = BidiFormatter.getInstance();
        boolean isRtl = TextUtils.getLayoutDirectionFromLocale(locale) == 1;
        return bidiFormatter.unicodeWrap(gmtText, isRtl ? TextDirectionHeuristicsCompat.RTL : TextDirectionHeuristicsCompat.LTR);
    }

    @VisibleForTesting
    /* loaded from: classes3.dex */
    public static final class ZoneGetterData {
        public final CharSequence[] gmtOffsetTexts;
        public final Set<String> localZoneIds;
        public final String[] olsonIdsToDisplay;
        public final TimeZone[] timeZones;
        public final int zoneCount;

        public ZoneGetterData(Context context) {
            Locale locale = context.getResources().getConfiguration().locale;
            TimeZoneFormat tzFormatter = TimeZoneFormat.getInstance(locale);
            Date now = new Date();
            List<String> olsonIdsToDisplayList = ZoneGetter.readTimezonesToDisplay(context);
            this.zoneCount = olsonIdsToDisplayList.size();
            int i = this.zoneCount;
            this.olsonIdsToDisplay = new String[i];
            this.timeZones = new TimeZone[i];
            this.gmtOffsetTexts = new CharSequence[i];
            for (int i2 = 0; i2 < this.zoneCount; i2++) {
                String olsonId = olsonIdsToDisplayList.get(i2);
                this.olsonIdsToDisplay[i2] = olsonId;
                TimeZone tz = TimeZone.getTimeZone(olsonId);
                this.timeZones[i2] = tz;
                this.gmtOffsetTexts[i2] = ZoneGetter.getGmtOffsetText(tzFormatter, locale, tz, now);
            }
            List<String> zoneIds = lookupTimeZoneIdsByCountry(locale.getCountry());
            this.localZoneIds = zoneIds != null ? new HashSet(zoneIds) : new HashSet();
        }

        @VisibleForTesting
        public List<String> lookupTimeZoneIdsByCountry(String country) {
            return TimeZoneFinder.getInstance().lookupTimeZoneIdsByCountry(country);
        }
    }
}
