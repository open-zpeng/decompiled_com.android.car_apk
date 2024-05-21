package com.android.car.garagemode;

import android.content.Context;
import com.android.car.R;
import com.android.internal.annotations.VisibleForTesting;
import com.android.settingslib.utils.StringUtil;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
/* loaded from: classes3.dex */
class WakeupPolicy {
    private static final Logger LOG = new Logger("WakeupPolicy");
    private static final Map<Character, Integer> TIME_UNITS_LOOKUP_SEC = new HashMap();
    @VisibleForTesting
    protected int mIndex = 0;
    private LinkedList<WakeupInterval> mWakeupIntervals;

    static {
        TIME_UNITS_LOOKUP_SEC.put('m', 60);
        TIME_UNITS_LOOKUP_SEC.put('h', Integer.valueOf((int) StringUtil.SECONDS_PER_HOUR));
        TIME_UNITS_LOOKUP_SEC.put('d', Integer.valueOf((int) StringUtil.SECONDS_PER_DAY));
    }

    WakeupPolicy(String[] policy) {
        this.mWakeupIntervals = parsePolicy(policy);
    }

    public static WakeupPolicy initFromResources(Context context) {
        LOG.d("Initiating WakupPolicy from resources ...");
        return new WakeupPolicy(context.getResources().getStringArray(R.array.config_garageModeCadence));
    }

    public int getNextWakeUpInterval() {
        if (this.mWakeupIntervals.size() == 0) {
            LOG.e("No wake up policy configuration was loaded.");
            return 0;
        }
        int index = this.mIndex;
        Iterator<WakeupInterval> it = this.mWakeupIntervals.iterator();
        while (it.hasNext()) {
            WakeupInterval wakeupTime = it.next();
            if (index <= wakeupTime.getNumAttempts()) {
                return wakeupTime.getWakeupInterval();
            }
            index -= wakeupTime.getNumAttempts();
        }
        LOG.w("No more garage mode wake ups scheduled; been sleeping too long.");
        return 0;
    }

    protected int getWakupIntervalsAmount() {
        return this.mWakeupIntervals.size();
    }

    private LinkedList<WakeupInterval> parsePolicy(String[] policy) {
        LinkedList<WakeupInterval> intervals = new LinkedList<>();
        if (policy == null || policy.length == 0) {
            LOG.e("Trying to parse empty policies!");
            return intervals;
        }
        for (String rule : policy) {
            WakeupInterval interval = parseRule(rule);
            if (interval == null) {
                LOG.e("Invalid Policy! This rule has bad format: " + rule);
                return new LinkedList<>();
            }
            intervals.add(interval);
        }
        return intervals;
    }

    private WakeupInterval parseRule(String rule) {
        String[] str = rule.split(",");
        if (str.length != 2) {
            Logger logger = LOG;
            logger.e("Policy has bad format: " + rule);
            return null;
        }
        String intervalStr = str[0];
        String timesStr = str[1];
        if (!intervalStr.isEmpty() && !timesStr.isEmpty()) {
            char unit = intervalStr.charAt(intervalStr.length() - 1);
            try {
                int interval = Integer.parseInt(intervalStr.substring(0, intervalStr.length() - 1));
                int times = Integer.parseInt(timesStr);
                if (!TIME_UNITS_LOOKUP_SEC.containsKey(Character.valueOf(unit))) {
                    Logger logger2 = LOG;
                    logger2.e("Time units map does not contain extension " + unit);
                    return null;
                } else if (interval <= 0) {
                    Logger logger3 = LOG;
                    logger3.e("Wake up policy time must be > 0!" + interval);
                    return null;
                } else if (times <= 0) {
                    Logger logger4 = LOG;
                    logger4.e("Wake up attempts in policy must be > 0!" + times);
                    return null;
                } else {
                    return new WakeupInterval(interval * TIME_UNITS_LOOKUP_SEC.get(Character.valueOf(unit)).intValue(), times);
                }
            } catch (NumberFormatException e) {
                Logger logger5 = LOG;
                logger5.d("Invalid input Rule for interval " + rule);
                return null;
            }
        }
        Logger logger6 = LOG;
        logger6.e("One of the values is empty. Please check format: " + rule);
        return null;
    }

    public void incrementCounter() {
        this.mIndex++;
    }

    public void resetCounter() {
        this.mIndex = 0;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class WakeupInterval {
        private int mNumAttempts;
        private int mWakeupInterval;

        WakeupInterval(int wakeupTime, int numAttempts) {
            this.mWakeupInterval = wakeupTime;
            this.mNumAttempts = numAttempts;
        }

        public int getWakeupInterval() {
            return this.mWakeupInterval;
        }

        public int getNumAttempts() {
            return this.mNumAttempts;
        }
    }
}
