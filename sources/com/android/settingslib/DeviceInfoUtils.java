package com.android.settingslib;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.system.Os;
import android.system.StructUtsname;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import androidx.annotation.VisibleForTesting;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/* loaded from: classes3.dex */
public class DeviceInfoUtils {
    private static final String FILENAME_MSV = "/sys/board_properties/soc/msv";
    private static final String TAG = "DeviceInfoUtils";

    private static String readLine(String filename) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filename), 256);
        try {
            return reader.readLine();
        } finally {
            reader.close();
        }
    }

    public static String getFormattedKernelVersion(Context context) {
        return formatKernelVersion(context, Os.uname());
    }

    @VisibleForTesting
    static String formatKernelVersion(Context context, StructUtsname uname) {
        if (uname == null) {
            return context.getString(R.string.status_unavailable);
        }
        Matcher m = Pattern.compile("(#\\d+) (?:.*?)?((Sun|Mon|Tue|Wed|Thu|Fri|Sat).+)").matcher(uname.version);
        if (!m.matches()) {
            Log.e(TAG, "Regex did not match on uname version " + uname.version);
            return context.getString(R.string.status_unavailable);
        }
        return uname.release + "\n" + m.group(1) + " " + m.group(2);
    }

    public static String getMsvSuffix() {
        try {
            String msv = readLine(FILENAME_MSV);
            if (Long.parseLong(msv, 16) == 0) {
                return " (ENGINEERING)";
            }
            return "";
        } catch (IOException | NumberFormatException e) {
            return "";
        }
    }

    public static String getFeedbackReporterPackage(Context context) {
        String feedbackReporter = context.getResources().getString(R.string.oem_preferred_feedback_reporter);
        if (TextUtils.isEmpty(feedbackReporter)) {
            return feedbackReporter;
        }
        Intent intent = new Intent("android.intent.action.BUG_REPORT");
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> resolvedPackages = pm.queryIntentActivities(intent, 64);
        for (ResolveInfo info : resolvedPackages) {
            if (info.activityInfo != null && !TextUtils.isEmpty(info.activityInfo.packageName)) {
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(info.activityInfo.packageName, 0);
                    if ((ai.flags & 1) != 0 && TextUtils.equals(info.activityInfo.packageName, feedbackReporter)) {
                        return feedbackReporter;
                    }
                } catch (PackageManager.NameNotFoundException e) {
                }
            }
        }
        return null;
    }

    public static String getSecurityPatch() {
        String patch = Build.VERSION.SECURITY_PATCH;
        if (!"".equals(patch)) {
            try {
                SimpleDateFormat template = new SimpleDateFormat("yyyy-MM-dd");
                Date patchDate = template.parse(patch);
                String format = DateFormat.getBestDateTimePattern(Locale.getDefault(), "dMMMMyyyy");
                return DateFormat.format(format, patchDate).toString();
            } catch (ParseException e) {
                return patch;
            }
        }
        return null;
    }

    public static String getFormattedPhoneNumber(Context context, SubscriptionInfo subscriptionInfo) {
        if (subscriptionInfo == null) {
            return null;
        }
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService("phone");
        String rawNumber = telephonyManager.getLine1Number(subscriptionInfo.getSubscriptionId());
        if (TextUtils.isEmpty(rawNumber)) {
            return null;
        }
        String formattedNumber = PhoneNumberUtils.formatNumber(rawNumber);
        return formattedNumber;
    }

    public static String getFormattedPhoneNumbers(Context context, List<SubscriptionInfo> subscriptionInfo) {
        StringBuilder sb = new StringBuilder();
        if (subscriptionInfo != null) {
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService("phone");
            int count = subscriptionInfo.size();
            for (int i = 0; i < count; i++) {
                String rawNumber = telephonyManager.getLine1Number(subscriptionInfo.get(i).getSubscriptionId());
                if (!TextUtils.isEmpty(rawNumber)) {
                    sb.append(PhoneNumberUtils.formatNumber(rawNumber));
                    if (i < count - 1) {
                        sb.append("\n");
                    }
                }
            }
        }
        return sb.toString();
    }
}
