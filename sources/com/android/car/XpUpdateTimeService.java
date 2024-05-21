package com.android.car;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.power.CarPowerManager;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.ICarPropertyEventListener;
import android.car.hardware.tbox.ICarTime;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.icu.util.TimeZone;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Slog;
import android.util.TimeUtils;
import com.android.car.XpUpdateTimeService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.telephony.MccTable;
import com.android.settingslib.utils.StringUtil;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.time.temporal.ChronoField;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
/* loaded from: classes3.dex */
public class XpUpdateTimeService extends ICarTime.Stub implements CarServiceBase, CarPowerManager.CarPowerStateListener {
    private static final String ACTION_POLL = "com.android.car.XpUpdateTimeService.action.POLL";
    private static final int ID_TBOX_CONNECT_STATE = 557846543;
    private static final int ID_TBOX_SYNC_UTC_TIME = 558960713;
    private static final int ID_TBOX_TIME_INFO = 557912123;
    private static final int POLL_REQUEST = 0;
    private final boolean TBOX_CAN_UPDATE_TIME;
    private final boolean isFactory;
    private final AlarmManager mAlarmManager;
    private CarPowerManager mCarPowerManager;
    private final Context mContext;
    @GuardedBy({"this"})
    private UpdateTimeHandler mHandler;
    @GuardedBy({"this"})
    private HandlerThread mHandlerThread;
    private final PendingIntent mPendingPollIntent;
    private final long mPollingIntervalMs;
    private final CarPropertyService mPropertyService;
    private final long mTimeErrorThresholdMs;
    public static final long TOTAL_CHECK_SYNC_TBOX_TIME_TIMEOUT = Duration.ofMinutes(2).toMillis();
    public static final long SINGLE_CHECK_SYNC_TBOX_TIME_TIMEOUT = Duration.ofSeconds(3).toMillis();
    private static final int[] INVALID_CAN_DATE_TIME = {2015, 0, 0, 0, 0, 0};
    private AtomicBoolean mTboxTimeSynced = new AtomicBoolean(false);
    private AtomicBoolean mTboxTimeSyncTimeout = new AtomicBoolean(false);
    private AtomicBoolean mLogTboxTimeSynced = new AtomicBoolean(false);
    private final ICarPropertyEventListener mICarPropertyEventListener = new ICarPropertyEventListener.Stub() { // from class: com.android.car.XpUpdateTimeService.1
        public void onEvent(List<CarPropertyEvent> events) throws RemoteException {
            XpUpdateTimeService.this.mHandler.sendVhalEvents(events);
        }
    };

    public XpUpdateTimeService(CarPropertyService service, Context context) {
        this.mPropertyService = service;
        this.mContext = context;
        this.mTimeErrorThresholdMs = this.mContext.getResources().getInteger(R.integer.config_tboxTimeThreshold);
        this.mPollingIntervalMs = this.mContext.getResources().getInteger(R.integer.config_tboxTimePollingInterval);
        this.mAlarmManager = (AlarmManager) this.mContext.getSystemService(AlarmManager.class);
        Intent pollIntent = new Intent(ACTION_POLL, (Uri) null);
        this.mPendingPollIntent = PendingIntent.getBroadcast(this.mContext, 0, pollIntent, 0);
        this.TBOX_CAN_UPDATE_TIME = Build.IS_USER ? true : SystemProperties.getBoolean("persist.sys.tbox_update_time", true);
        this.isFactory = SystemProperties.getInt("ro.xiaopeng.special", 0) == 6;
        Slog.i(CarLog.TAG_UPDATETIME, "TBOX_CAN_UPDATE_TIME: " + this.TBOX_CAN_UPDATE_TIME + ", isFactory: " + this.isFactory);
    }

    private void registerForAlarms() {
        this.mContext.registerReceiver(new BroadcastReceiver() { // from class: com.android.car.XpUpdateTimeService.2
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context, Intent intent) {
                XpUpdateTimeService.this.mHandler.pollTboxTime();
            }
        }, new IntentFilter(ACTION_POLL));
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
        registerForAlarms();
        synchronized (this) {
            this.mHandlerThread = new HandlerThread(CarLog.TAG_UPDATETIME);
            this.mHandlerThread.start();
            this.mHandler = new UpdateTimeHandler(this, this.mHandlerThread.getLooper());
        }
        this.mCarPowerManager = CarLocalServices.createCarPowerManager(this.mContext);
        CarPowerManager carPowerManager = this.mCarPowerManager;
        if (carPowerManager != null) {
            carPowerManager.setListener(this);
        }
        this.mPropertyService.registerListener(557912123, 0.0f, this.mICarPropertyEventListener);
        this.mPropertyService.registerListener(558960713, 0.0f, this.mICarPropertyEventListener);
        this.mPropertyService.registerListener(557846543, 0.0f, this.mICarPropertyEventListener);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void syncTboxTime(boolean checkTimeout) {
        try {
            this.mPropertyService.setLongVectorProperty(558960713, 0, new long[]{1});
        } catch (Exception e) {
        }
        if (this.mTboxTimeSynced.get()) {
            resetAlarm(this.mPollingIntervalMs);
            return;
        }
        if (checkTimeout) {
            this.mHandler.sendTotalSyncTboxTimeTimeout(TOTAL_CHECK_SYNC_TBOX_TIME_TIMEOUT);
        }
        if (this.mTboxTimeSyncTimeout.get()) {
            resetAlarm(this.mPollingIntervalMs);
        } else {
            this.mHandler.sendSingleSyncTboxTimeTimeout(SINGLE_CHECK_SYNC_TBOX_TIME_TIMEOUT);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setTboxTimeSyncTimeout(boolean state) {
        this.mTboxTimeSyncTimeout.set(state);
    }

    @Override // com.android.car.CarServiceBase
    public void release() {
        HandlerThread handlerThread;
        synchronized (this) {
            this.mHandler.removeCallbacksAndMessages(null);
            handlerThread = this.mHandlerThread;
        }
        handlerThread.quitSafely();
        try {
            handlerThread.join(1000L);
        } catch (InterruptedException e) {
            Slog.e(CarLog.TAG_UPDATETIME, "Timeout while joining for handler thread to join.");
        }
        CarPowerManager carPowerManager = this.mCarPowerManager;
        if (carPowerManager != null) {
            carPowerManager.clearListener();
            this.mCarPowerManager = null;
        }
        this.mPropertyService.unregisterListener(558960713, this.mICarPropertyEventListener);
        this.mPropertyService.unregisterListener(557912123, this.mICarPropertyEventListener);
        this.mPropertyService.unregisterListener(557846543, this.mICarPropertyEventListener);
    }

    @Override // com.android.car.CarServiceBase
    public void vehicleHalReconnected() {
    }

    public void setCarTimezone(int type, String timezoneArea) {
        this.mHandler.sendTimezoneToTbox(type, timezoneArea);
    }

    @Override // com.android.car.CarServiceBase
    public void dump(PrintWriter writer) {
        writer.println("**dump XpUpdateTimeService**");
        writer.print("    PollingIntervalMs: ");
        TimeUtils.formatDuration(this.mPollingIntervalMs, writer);
        writer.println();
        writer.print("    TimeErrorThresholdMs: ");
        TimeUtils.formatDuration(this.mTimeErrorThresholdMs, writer);
        writer.println();
        writer.print("    mTboxTimeSynced: ");
        writer.println(this.mTboxTimeSynced.get());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void dispatchTboxTimeSyncEvent(long[] array) {
        long mcc;
        String str;
        String selectZone;
        long tz = array[0];
        long timeS = array[1];
        long timeMs = array[2];
        if (array.length < 4) {
            mcc = 0;
        } else {
            long mcc2 = array[3];
            mcc = mcc2;
        }
        if (timeS < 2147483647L) {
            long totalMs = (1000 * timeS) + timeMs;
            long skew = Math.abs(totalMs - System.currentTimeMillis());
            if (this.TBOX_CAN_UPDATE_TIME && skew >= this.mTimeErrorThresholdMs) {
                boolean isWifi = isWifi();
                if ((!this.isFactory || !isWifi) && SystemClock.setCurrentTimeMillis(totalMs)) {
                    Slog.i(CarLog.TAG_UPDATETIME, "Update tbox time: " + Arrays.toString(array));
                    SystemProperties.set("sys.xiaopeng.usetboxtime", "1");
                    this.mContext.sendBroadcast(new Intent("android.intent.action.TIME_SET"));
                } else {
                    Slog.e(CarLog.TAG_UPDATETIME, "Update tbox time: " + Arrays.toString(array) + " failed, isWifi: " + isWifi + ", isFactory: " + this.isFactory);
                }
            }
            if (isAutoTimezoneEnable()) {
                TimeZone timezone = TimeUtils.getIcuTimeZone((int) Duration.ofMillis(tz).toMillis(), null, totalMs, MccTable.countryCodeForMcc((int) mcc));
                TimeZone defaultTimezone = TimeZone.getDefault();
                if (timezone != null) {
                    selectZone = timezone.getID();
                    str = CarLog.TAG_UPDATETIME;
                } else {
                    str = CarLog.TAG_UPDATETIME;
                    selectZone = getTimezoneID(tz, totalMs, defaultTimezone);
                }
                if (!TextUtils.isEmpty(selectZone) && !selectZone.equals(defaultTimezone.getID())) {
                    this.mAlarmManager.setTimeZone(selectZone);
                    Slog.i(str, "set timezone " + selectZone);
                    SystemProperties.set("sys.xiaopeng.tbox_tz", String.valueOf(tz / 15));
                }
            }
            boolean ret = this.mTboxTimeSynced.compareAndSet(false, true);
            if (ret) {
                this.mPropertyService.unregisterListener(557912123, this.mICarPropertyEventListener);
            }
            removeSyncTboxTimeTimeoutMessages();
        } else {
            Slog.i(CarLog.TAG_UPDATETIME, "Ignoring tbox time update due to invalid data: " + Arrays.toString(array));
        }
        resetAlarm(this.mPollingIntervalMs);
    }

    private String getTimezoneID(long tz, long totalMs, TimeZone defaultTimezone) {
        String[] zones;
        long rawTboxRawOffsetMs = Duration.ofMinutes(tz).toMillis();
        String selectZone = null;
        if (rawTboxRawOffsetMs < 2147483647L) {
            int tboxRawOffsetMs = (int) rawTboxRawOffsetMs;
            int currentTimeZoneOffsetMs = defaultTimezone.getOffset(totalMs);
            if (tboxRawOffsetMs != currentTimeZoneOffsetMs && (zones = TimeZone.getAvailableIDs(tboxRawOffsetMs)) != null && zones.length > 0) {
                int length = zones.length;
                int i = 0;
                while (true) {
                    if (i >= length) {
                        break;
                    }
                    String zone = zones[i];
                    Slog.i(CarLog.TAG_UPDATETIME, "zone: " + zone);
                    if (zone.startsWith("Etc/GMT")) {
                        selectZone = zone;
                        break;
                    }
                    if (selectZone == null) {
                        TimeZone timezoneTemp = TimeZone.getTimeZone(zone);
                        if (timezoneTemp.getOffset(totalMs) == tboxRawOffsetMs) {
                            selectZone = zone;
                        }
                    }
                    i++;
                }
            }
        } else {
            Slog.e(CarLog.TAG_UPDATETIME, "timezone offset(minute) " + tz + " is invalid");
        }
        return selectZone;
    }

    private boolean isAutoTimezoneEnable() {
        return Settings.Global.getInt(this.mContext.getContentResolver(), "auto_time_zone", 0) > 0;
    }

    private boolean isWifi() {
        ConnectivityManager cm = (ConnectivityManager) this.mContext.getSystemService("connectivity");
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected() && ConnectivityManager.isNetworkTypeWifi(networkInfo.getType())) {
            return true;
        }
        return false;
    }

    private void resetAlarm(long intervalMs) {
        cancelTimeSyncAlarm();
        long now = SystemClock.elapsedRealtime();
        long next = now + intervalMs;
        this.mAlarmManager.setExactAndAllowWhileIdle(3, next, this.mPendingPollIntent);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void cancelTimeSyncAlarm() {
        this.mAlarmManager.cancel(this.mPendingPollIntent);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void removeSyncTboxTimeTimeoutMessages() {
        this.mHandler.removeSyncTboxTimeTimeoutMessages();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void dispatchCanTimeEvent(int[] array) {
        if (!this.mTboxTimeSynced.get()) {
            if (isInvalidDateTime(array)) {
                Slog.w(CarLog.TAG_UPDATETIME, "Ignore invalid can time: " + Arrays.toString(array));
                return;
            }
            Calendar c = Calendar.getInstance();
            c.set(array[0], array[1] - 1, array[2], array[3], array[4], array[5]);
            long when = c.getTimeInMillis();
            if (when / 1000 < 2147483647L) {
                long skew = Math.abs(when - System.currentTimeMillis());
                if (skew >= this.mTimeErrorThresholdMs) {
                    if (SystemClock.setCurrentTimeMillis(when)) {
                        Slog.i(CarLog.TAG_UPDATETIME, "Update can time: " + Arrays.toString(array));
                        this.mContext.sendBroadcast(new Intent("android.intent.action.TIME_SET"));
                        return;
                    }
                    Slog.e(CarLog.TAG_UPDATETIME, "Update can time: " + Arrays.toString(array) + " failed");
                    return;
                }
                return;
            }
            Slog.w(CarLog.TAG_UPDATETIME, "Ignoring can time update due to invalid data: " + Arrays.toString(array));
        } else if (this.mLogTboxTimeSynced.compareAndSet(false, true)) {
            Slog.i(CarLog.TAG_UPDATETIME, "Drop the can time due to the tbox time synced");
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void sendTimezoneToTbox(int type, String timezoneArea) {
        int timezone;
        TimeZone timeZone = TimeZone.getTimeZone(timezoneArea);
        if (timeZone != null && "Etc/Unknown".equals(timeZone.getID())) {
            Slog.w(CarLog.TAG_UPDATETIME, "unknown timezoneArea: " + timezoneArea);
            return;
        }
        int rawoffSet = timeZone.getRawOffset() / 1000;
        boolean useDaylightTime = timeZone.useDaylightTime();
        boolean inDaylightTime = timeZone.inDaylightTime(new Date());
        if (useDaylightTime && inDaylightTime) {
            timezone = ((rawoffSet + StringUtil.SECONDS_PER_HOUR) * 4) / StringUtil.SECONDS_PER_HOUR;
        } else {
            int timezone2 = rawoffSet * 4;
            timezone = timezone2 / StringUtil.SECONDS_PER_HOUR;
        }
        Slog.i(CarLog.TAG_UPDATETIME, "timezone: " + timezone + ", type: " + type + ", useDaylightTime: " + useDaylightTime + ", inDaylightTime: " + inDaylightTime + ", timezoneArea: " + timezoneArea + ", currentTimezoneArea: " + SystemProperties.get("persist.sys.timezone"));
        Settings.Global.putInt(this.mContext.getContentResolver(), "auto_time_zone", type > 0 ? 0 : 1);
        this.mAlarmManager.setTimeZone(timezoneArea);
        int[] data = {type, timezone};
        try {
            this.mPropertyService.setIntVectorProperty(VehicleProperty.TBOX_TIMEZONE_REQ, data);
        } catch (Exception e) {
        }
    }

    private boolean isInvalidDateTime(int[] array) {
        if (array != null) {
            int length = array.length;
            int[] iArr = INVALID_CAN_DATE_TIME;
            if (length != iArr.length || Arrays.equals(array, iArr)) {
                return true;
            }
            try {
                ChronoField.YEAR.checkValidValue(array[0]);
                ChronoField.MONTH_OF_YEAR.checkValidValue(array[1]);
                ChronoField.DAY_OF_MONTH.checkValidValue(array[2]);
                ChronoField.HOUR_OF_DAY.checkValidValue(array[3]);
                ChronoField.MINUTE_OF_HOUR.checkValidValue(array[4]);
                ChronoField.SECOND_OF_MINUTE.checkValidValue(array[5]);
                return false;
            } catch (Exception e) {
                return true;
            }
        }
        return true;
    }

    public void onStateChanged(int state) {
        Slog.i(CarLog.TAG_UPDATETIME, "onStateChanged: " + state);
        if (state == 6) {
            int autoTimeZone = Settings.Global.getInt(this.mContext.getContentResolver(), "auto_time_zone", 0);
            setCarTimezone(autoTimeZone <= 0 ? 1 : 0, SystemProperties.get("persist.sys.timezone", ""));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static final class UpdateTimeHandler extends Handler {
        private static final int MSG_POLL_TBOX_TIME = 2;
        private static final int MSG_SEND_TIMEZONE_TO_TBOX = 5;
        private static final int MSG_SINGLE_SYNC_TBOX_TIME_TIMEOUT = 3;
        private static final int MSG_TOTAL_SYNC_TBOX_TIME_TIMEOUT = 4;
        private static final int MSG_VHAL_EVENTS = 1;
        private final WeakReference<XpUpdateTimeService> mService;

        UpdateTimeHandler(XpUpdateTimeService service, Looper looper) {
            super(looper);
            this.mService = new WeakReference<>(service);
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            int i = msg.what;
            if (i == 1) {
                try {
                    handleVhalEvents(msg);
                } catch (Exception e) {
                    Slog.e(CarLog.TAG_UPDATETIME, "handle vhal events with exception: " + e);
                }
            } else if (i == 2) {
                serviceSyncTboxTime();
            } else if (i == 3) {
                serviceSyncTboxTimeIfNotSynced();
            } else if (i == 4) {
                setServiceSyncTboxTimeTimeout();
            } else if (i == 5) {
                serviceSendTimezoneToTbox(msg);
            } else {
                Slog.e(CarLog.TAG_UPDATETIME, "Unexpected message: " + msg.what);
            }
        }

        private void serviceSendTimezoneToTbox(Message msg) {
            XpUpdateTimeService srv = this.mService.get();
            if (srv != null) {
                srv.sendTimezoneToTbox(msg.arg1, (String) msg.obj);
            }
        }

        private void serviceSyncTboxTime() {
            XpUpdateTimeService srv = this.mService.get();
            if (srv != null) {
                srv.syncTboxTime(false);
            }
        }

        private void serviceSyncTboxTimeIfNotSynced() {
            XpUpdateTimeService srv = this.mService.get();
            if (srv != null && !srv.mTboxTimeSynced.get()) {
                srv.syncTboxTime(false);
            }
        }

        void removeSyncTboxTimeTimeoutMessages() {
            removeMessages(3);
            removeMessages(4);
        }

        private void setServiceSyncTboxTimeTimeout() {
            XpUpdateTimeService srv = this.mService.get();
            if (srv != null) {
                srv.setTboxTimeSyncTimeout(true);
            }
        }

        void sendVhalEvents(List<CarPropertyEvent> events) {
            Message msg = obtainMessage(1, events);
            sendMessage(msg);
        }

        void sendTotalSyncTboxTimeTimeout(long timeout) {
            Message msg = obtainMessage(4);
            sendMessageDelayed(msg, timeout);
        }

        void sendSingleSyncTboxTimeTimeout(long timeout) {
            Message msg = obtainMessage(3);
            sendMessageDelayed(msg, timeout);
        }

        void sendTimezoneToTbox(int type, String timezoneArea) {
            removeMessages(5);
            Message msg = obtainMessage(5);
            msg.arg1 = type;
            msg.obj = timezoneArea;
            sendMessage(msg);
        }

        private void handleVhalEvents(Message msg) {
            List<CarPropertyEvent> events = (List) msg.obj;
            if (events != null && !events.isEmpty()) {
                final XpUpdateTimeService srv = this.mService.get();
                if (srv == null) {
                    Slog.e(CarLog.TAG_UPDATETIME, "XpUpdateTimeService is null");
                } else {
                    events.stream().filter(new Predicate() { // from class: com.android.car.-$$Lambda$XpUpdateTimeService$UpdateTimeHandler$iII1aMkJzgOZr02yothKCydlqas
                        @Override // java.util.function.Predicate
                        public final boolean test(Object obj) {
                            return XpUpdateTimeService.UpdateTimeHandler.lambda$handleVhalEvents$0((CarPropertyEvent) obj);
                        }
                    }).map($$Lambda$o3zP4Oj56DnL7t27aVv1kJbnwAk.INSTANCE).filter(new Predicate() { // from class: com.android.car.-$$Lambda$XpUpdateTimeService$UpdateTimeHandler$91pmYOt2juMUysqNoXrrVVVgnRY
                        @Override // java.util.function.Predicate
                        public final boolean test(Object obj) {
                            boolean nonNull;
                            nonNull = Objects.nonNull(((CarPropertyValue) obj).getValue());
                            return nonNull;
                        }
                    }).forEach(new Consumer() { // from class: com.android.car.-$$Lambda$XpUpdateTimeService$UpdateTimeHandler$hIWhjpr8A1GzErf4wQyVfh9hwkY
                        @Override // java.util.function.Consumer
                        public final void accept(Object obj) {
                            XpUpdateTimeService.UpdateTimeHandler.lambda$handleVhalEvents$2(XpUpdateTimeService.this, (CarPropertyValue) obj);
                        }
                    });
                }
            }
        }

        /* JADX INFO: Access modifiers changed from: package-private */
        public static /* synthetic */ boolean lambda$handleVhalEvents$0(CarPropertyEvent v) {
            return v.getEventType() == 0;
        }

        /* JADX INFO: Access modifiers changed from: package-private */
        public static /* synthetic */ void lambda$handleVhalEvents$2(XpUpdateTimeService srv, CarPropertyValue v) {
            int propertyId = v.getPropertyId();
            if (propertyId == 557846543) {
                if (((Integer) v.getValue()).intValue() == 1) {
                    srv.syncTboxTime(true);
                    return;
                }
                srv.cancelTimeSyncAlarm();
                srv.setTboxTimeSyncTimeout(false);
                srv.removeSyncTboxTimeTimeoutMessages();
            } else if (propertyId == 557912123) {
                srv.dispatchCanTimeEvent(CarServiceUtils.toIntArray((Integer[]) v.getValue()));
            } else if (propertyId == 558960713) {
                srv.dispatchTboxTimeSyncEvent(CarServiceUtils.toLongArray((Long[]) v.getValue()));
            } else {
                Slog.e(CarLog.TAG_UPDATETIME, "Unsupported propertyId: " + propertyId);
            }
        }

        void pollTboxTime() {
            Message msg = obtainMessage(2);
            sendMessage(msg);
        }
    }
}
