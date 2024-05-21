package com.android.car.hal;

import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.os.FileUtils;
import android.os.SystemProperties;
import android.util.Slog;
import com.android.car.CarLog;
import com.android.internal.annotations.VisibleForTesting;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/* loaded from: classes3.dex */
public class PowerHalService extends HalServiceBase {
    public static final int BATT_NORMAL = 4;
    public static final int BATT_ONE_LEVEL_LOW = 3;
    public static final int BATT_ONE_LEVEL_OVER = 5;
    public static final int BATT_TWO_LEVEL_LOW = 2;
    public static final int BATT_TWO_LEVEL_OVER = 6;
    private static final int INVALID_VALUE = -10;
    public static final boolean IS_XP_POWER = true;
    public static final int MAX_BRIGHTNESS = 100;
    private static final int MAX_LOG = 24;
    private static final int PRODUCT_D55 = 0;
    private static final String RESUME_PROF = "/sys/xpeng_performance/resume_prof";
    private static final String RESUME_PROF_SCREEN_ON = "PM_SCREEN_STATE_ON";
    @VisibleForTesting
    public static final int SET_DEEP_SLEEP_ENTRY = 2;
    @VisibleForTesting
    public static final int SET_DEEP_SLEEP_EXIT = 3;
    @VisibleForTesting
    public static final int SET_ON = 6;
    @VisibleForTesting
    public static final int SET_SHUTDOWN_CANCELLED = 8;
    @VisibleForTesting
    public static final int SET_SHUTDOWN_POSTPONE = 4;
    @VisibleForTesting
    public static final int SET_SHUTDOWN_PREPARE = 7;
    @VisibleForTesting
    public static final int SET_SHUTDOWN_START = 5;
    @VisibleForTesting
    public static final int SET_WAIT_FOR_VHAL = 1;
    @VisibleForTesting
    public static final int SHUTDOWN_CAN_SLEEP = 2;
    @VisibleForTesting
    public static final int SHUTDOWN_IMMEDIATELY = 1;
    @VisibleForTesting
    public static final int SHUTDOWN_ONLY = 3;
    private static final int THERMAL_NORMAL = 0;
    private static final int THERMAL_RUNAWAY = 1;
    private static final String WAKEUP_STATE_FILE = "/sys/xpeng/gpio_indicator/lcd_gpio";
    private static final int XPU_REMOTE = 1;
    private static final int XPU_REMOTE_NONE = 0;
    private static final String XP_HMI_GPIO_INDICATORS = "/sys/xpeng/gpio_indicator/hmi_gpio";
    private static final String XP_SLEEP_GPIO_INDICATORS_FILE = "/sys/xpeng/gpio_indicator/sleep_gpio";
    private final VehicleHal mHal;
    private PowerEventListener mListener;
    private int mMaxDisplayBrightness;
    private LinkedList<VehiclePropValue> mQueuedEvents;
    private static final int HW_VERSION = SystemProperties.getInt("ro.boot.hw_version", 3);
    private static final int PRODUCT_MAJOR = SystemProperties.getInt("ro.boot.xp_product_major", -1);
    private static final int PRODUCT_MINOR = SystemProperties.getInt("ro.boot.xp_product_minor", -1);
    private volatile int mThermalState = INVALID_VALUE;
    private Map<Integer, Integer> mStateMap = new ConcurrentHashMap(10);
    private volatile boolean mDebugScreen = false;
    private int mLogControl = 0;
    private final HashMap<Integer, VehiclePropConfig> mProperties = new HashMap<>();

    /* loaded from: classes3.dex */
    public interface PowerEventListener {
        void onApPowerStateChange(PowerState powerState);

        void onDisplayBrightnessChange(int i);

        void onDisplayChange(String str, int i, boolean z);
    }

    private static String powerStateReportName(int state) {
        String baseName;
        switch (state) {
            case 1:
                baseName = "WAIT_FOR_VHAL";
                break;
            case 2:
                baseName = "DEEP_SLEEP_ENTRY";
                break;
            case 3:
                baseName = "DEEP_SLEEP_EXIT";
                break;
            case 4:
                baseName = "SHUTDOWN_POSTPONE";
                break;
            case 5:
                baseName = "SHUTDOWN_START";
                break;
            case 6:
                baseName = "ON";
                break;
            case 7:
                baseName = "SHUTDOWN_PREPARE";
                break;
            case 8:
                baseName = "SHUTDOWN_CANCELLED";
                break;
            default:
                baseName = "<unknown>";
                break;
        }
        return baseName + "(" + state + ")";
    }

    private static String xpuRemoteFlagName(int state) {
        String baseName;
        if (state == 0) {
            baseName = "NOT REMOTE";
        } else if (state == 1) {
            baseName = "REMOTE";
        } else {
            baseName = "<unknown>";
        }
        return baseName + "(" + state + ")";
    }

    private static String thermalRunawayName(int state) {
        String baseName;
        if (state == 0) {
            baseName = "NORMAL";
        } else if (state == 1) {
            baseName = "RUNAWAY";
        } else {
            baseName = "<unknown>";
        }
        return baseName + "(" + state + ")";
    }

    public void setScreenDebug(boolean flag) {
        SystemProperties.set("xp.debug.display", Boolean.toString(flag));
        this.mDebugScreen = flag;
    }

    public void simulateIviScreenEnable(boolean enable) {
        int screenState = enable ? 1 : 2;
        this.mStateMap.put(Integer.valueOf((int) VehicleProperty.PM_SCREEN_STATE), Integer.valueOf(screenState));
        PowerEventListener powerEventListener = this.mListener;
        if (powerEventListener != null) {
            powerEventListener.onApPowerStateChange(getCurrentPmPowerState(getPmState(), screenState));
        }
    }

    public void simulatePsnScreenEnable(boolean enable) {
        PowerEventListener powerEventListener = this.mListener;
        if (powerEventListener != null) {
            powerEventListener.onDisplayChange("xp_mt_psg", 4, enable);
        }
    }

    private static String powerStateReqName(int state) {
        String baseName;
        if (state == 0) {
            baseName = "ON";
        } else if (state == 1) {
            baseName = "SHUTDOWN_PREPARE";
        } else if (state == 2) {
            baseName = "CANCEL_SHUTDOWN";
        } else if (state == 3) {
            baseName = "FINISHED";
        } else {
            baseName = "<unknown>";
        }
        return baseName + "(" + state + ")";
    }

    private static String mcuPmStateName(int state) {
        String baseName;
        if (state == 1) {
            baseName = "NORMAL";
        } else if (state == 2) {
            baseName = "SLEEP";
        } else if (state == 3) {
            baseName = "SHUTDOWN";
        } else {
            baseName = "<unknown>";
        }
        return baseName + "(" + state + ")";
    }

    private static String screenStateName(int state) {
        String baseName;
        if (state == 1) {
            baseName = "ON";
        } else if (state == 2) {
            baseName = "OFF";
        } else {
            baseName = "<unknown>";
        }
        return baseName + "(" + state + ")";
    }

    private static String batteryStateName(int state) {
        String baseName;
        if (state == 2) {
            baseName = "TWO_LEVEL_LOW";
        } else if (state == 3) {
            baseName = "ONE_LEVEL_LOW";
        } else if (state == 4) {
            baseName = "NORMAL";
        } else if (state == 5) {
            baseName = "ONE_LEVEL_OVER";
        } else if (state == 6) {
            baseName = "TWO_LEVEL_OVER";
        } else {
            baseName = "<unknown>";
        }
        return baseName + "(" + state + ")";
    }

    /* loaded from: classes3.dex */
    public static final class PowerState {
        public final int mParam;
        public final int mState;

        public PowerState(int state, int param) {
            this.mState = state;
            this.mParam = param;
        }

        public boolean canEnterDeepSleep() {
            if (this.mState == 1) {
                return this.mParam == 2;
            }
            throw new IllegalStateException("wrong state");
        }

        public boolean canPostponeShutdown() {
            if (this.mState == 1) {
                return this.mParam != 1;
            }
            throw new IllegalStateException("wrong state");
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o instanceof PowerState) {
                PowerState that = (PowerState) o;
                return this.mState == that.mState && this.mParam == that.mParam;
            }
            return false;
        }

        public String toString() {
            return "PowerState state:" + this.mState + ", param:" + this.mParam;
        }
    }

    public PowerHalService(VehicleHal hal) {
        this.mHal = hal;
    }

    public void setListener(PowerEventListener listener) {
        LinkedList<VehiclePropValue> eventsToDispatch = null;
        synchronized (this) {
            this.mListener = listener;
            if (this.mQueuedEvents != null && this.mQueuedEvents.size() > 0) {
                eventsToDispatch = this.mQueuedEvents;
            }
            this.mQueuedEvents = null;
        }
        if (eventsToDispatch != null) {
            dispatchEvents(eventsToDispatch, listener);
        }
    }

    public void sendWaitForVhal() {
    }

    public void sendSleepEntry(int wakeupTimeSec) {
        setPowerState(21, 0);
    }

    public void sendSleepExit() {
        setPowerState(1, 0);
    }

    public void sendShutdownPostpone(int postponeTimeMs) {
    }

    public void sendShutdownStart(int wakeupTimeSec) {
        Slog.i(CarLog.TAG_POWER, "send shutdown start");
    }

    public void sendOn() {
    }

    public void sendShutdownPrepare() {
    }

    public void sendShutdownCancel() {
    }

    public void sendDisplayBrightness(int brightness) {
        if (brightness < 0) {
            brightness = 0;
        } else if (brightness > 100) {
            brightness = 100;
        }
        VehiclePropConfig prop = this.mProperties.get(Integer.valueOf((int) VehicleProperty.DISPLAY_BRIGHTNESS));
        if (prop == null) {
            return;
        }
        try {
            this.mHal.set(VehicleProperty.DISPLAY_BRIGHTNESS, 0).to(brightness);
            Slog.i(CarLog.TAG_POWER, "send display brightness = " + brightness);
        } catch (PropertyTimeoutException e) {
            Slog.e(CarLog.TAG_POWER, "cannot set DISPLAY_BRIGHTNESS", e);
        }
    }

    private void setPowerState(int state, int additionalParam) {
        if (isPowerStateSupported()) {
            byte[] values = {(byte) state, 0, 0, 0, 0, 0, 0, 0};
            try {
                this.mHal.set(VehicleProperty.MCU_PM_STATE, 0).to(values);
            } catch (PropertyTimeoutException e) {
                Slog.e(CarLog.TAG_POWER, "cannot set to MCU_PM_STATE", e);
            }
        }
    }

    public PowerState getCurrentPowerState() {
        return getCurrentPowerState(getBatteryState(), getPmState(), getScreenState());
    }

    public synchronized boolean isPowerStateSupported() {
        return this.mProperties.get(Integer.valueOf((int) VehicleProperty.MCU_PM_STATE)) != null;
    }

    private synchronized boolean isConfigFlagSet(int flag) {
        VehiclePropConfig config = this.mProperties.get(Integer.valueOf((int) VehicleProperty.MCU_PM_STATE));
        if (config == null) {
            return false;
        }
        if (config.configArray.size() < 1) {
            return false;
        }
        return (config.configArray.get(0).intValue() & flag) != 0;
    }

    public boolean isDeepSleepAllowed() {
        return isConfigFlagSet(1);
    }

    public boolean isTimedWakeupAllowed() {
        return isConfigFlagSet(2);
    }

    private void logResumeProfEvent(String resumeevent) {
        try {
            FileOutputStream fbp = new FileOutputStream(RESUME_PROF);
            fbp.write(resumeevent.getBytes());
            fbp.flush();
            fbp.close();
        } catch (FileNotFoundException e) {
            Slog.e("RESUME_PROF", "Failure open /sys/xpeng_performance/resume_prof, not found!", e);
        } catch (IOException e2) {
            Slog.e("RESUME_PROF", "Failure open /sys/xpeng_performance/resume_prof entry", e2);
        }
    }

    @Override // com.android.car.hal.HalServiceBase
    public synchronized void init() {
        for (VehiclePropConfig config : this.mProperties.values()) {
            if (VehicleHal.isPropertySubscribable(config)) {
                this.mHal.subscribeProperty(this, config.prop);
            }
        }
        VehiclePropConfig brightnessProperty = this.mProperties.get(Integer.valueOf((int) VehicleProperty.DISPLAY_BRIGHTNESS));
        if (brightnessProperty != null) {
            this.mMaxDisplayBrightness = brightnessProperty.areaConfigs.size() > 0 ? brightnessProperty.areaConfigs.get(0).maxInt32Value : 0;
            if (this.mMaxDisplayBrightness <= 0) {
                Slog.w(CarLog.TAG_POWER, "Max display brightness from vehicle HAL is invalid:" + this.mMaxDisplayBrightness);
                this.mMaxDisplayBrightness = 1;
            }
        }
    }

    @Override // com.android.car.hal.HalServiceBase
    public synchronized void release() {
        this.mProperties.clear();
    }

    @Override // com.android.car.hal.HalServiceBase
    public synchronized Collection<VehiclePropConfig> takeSupportedProperties(Collection<VehiclePropConfig> allProperties) {
        for (VehiclePropConfig config : allProperties) {
            switch (config.prop) {
                case VehicleProperty.DISPLAY_BRIGHTNESS /* 289409539 */:
                case VehicleProperty.AP_POWER_STATE_REQ /* 289475072 */:
                case VehicleProperty.AP_POWER_STATE_REPORT /* 289475073 */:
                case VehicleProperty.PM_SCREEN_STATE /* 557847657 */:
                case VehicleProperty.MCU_PSN_SCREEN_STATE /* 557847681 */:
                case VehicleProperty.MCU_PM_STATE /* 560993283 */:
                case VehicleProperty.MCU_BATTERY_DATA /* 560993288 */:
                    this.mProperties.put(Integer.valueOf(config.prop), config);
                    break;
            }
        }
        return new LinkedList(this.mProperties.values());
    }

    @Override // com.android.car.hal.HalServiceBase
    public void handleHalEvents(List<VehiclePropValue> values) {
        synchronized (this) {
            if (this.mListener == null) {
                if (this.mQueuedEvents == null) {
                    this.mQueuedEvents = new LinkedList<>();
                }
                this.mQueuedEvents.addAll(values);
                return;
            }
            PowerEventListener listener = this.mListener;
            dispatchEvents(values, listener);
        }
    }

    private void dispatchEvents(List<VehiclePropValue> values, PowerEventListener listener) {
        int maxBrightness;
        for (VehiclePropValue v : values) {
            switch (v.prop) {
                case VehicleProperty.DISPLAY_BRIGHTNESS /* 289409539 */:
                    synchronized (this) {
                        maxBrightness = this.mMaxDisplayBrightness;
                    }
                    int brightness = (v.value.int32Values.get(0).intValue() * 100) / maxBrightness;
                    if (brightness < 0) {
                        Slog.e(CarLog.TAG_POWER, "invalid brightness: " + brightness + ", set to 0");
                        brightness = 0;
                    } else if (brightness > 100) {
                        Slog.e(CarLog.TAG_POWER, "invalid brightness: " + brightness + ", set to 100");
                        brightness = 100;
                    }
                    Slog.i(CarLog.TAG_POWER, "Received DISPLAY_BRIGHTNESS=" + brightness);
                    listener.onDisplayBrightnessChange(brightness);
                    break;
                case VehicleProperty.AP_POWER_STATE_REQ /* 289475072 */:
                    int state = v.value.int32Values.get(0).intValue();
                    int param = v.value.int32Values.get(1).intValue();
                    Slog.i(CarLog.TAG_POWER, "Received AP_POWER_STATE_REQ=" + powerStateReqName(state) + " param=" + param);
                    listener.onApPowerStateChange(new PowerState(state, param));
                    break;
                case VehicleProperty.PM_SCREEN_STATE /* 557847657 */:
                    if (this.mDebugScreen) {
                        return;
                    }
                    int screenState = v.value.int32Values.get(0).intValue();
                    boolean isScreenStateChange = screenState != this.mStateMap.getOrDefault(Integer.valueOf((int) VehicleProperty.PM_SCREEN_STATE), Integer.valueOf((int) INVALID_VALUE)).intValue();
                    if (isScreenStateChange) {
                        Slog.i(CarLog.TAG_POWER, "Received SCREEN_STATE=" + screenStateName(screenState) + ", MCU_PM_STATE=" + mcuPmStateName(getPmState()) + ", BATTERY_STATE=" + batteryStateName(getBatteryState()) + ", XPU_FLAG=" + xpuRemoteFlagName(this.mHal.getXpuFlag()));
                    }
                    if (isScreenStateValid(screenState) && isScreenStateChange) {
                        this.mStateMap.put(Integer.valueOf((int) VehicleProperty.PM_SCREEN_STATE), Integer.valueOf(screenState));
                        if (screenState == 1) {
                            logResumeProfEvent(RESUME_PROF_SCREEN_ON);
                        }
                        listener.onApPowerStateChange(getCurrentPowerState(getBatteryState(), getPmState(), screenState));
                        break;
                    }
                    break;
                case VehicleProperty.MCU_PSN_SCREEN_STATE /* 557847681 */:
                    if (this.mDebugScreen) {
                        return;
                    }
                    int psnScreenState = v.value.int32Values.get(0).intValue();
                    boolean isPsnScreenStateChange = psnScreenState != this.mStateMap.getOrDefault(Integer.valueOf((int) VehicleProperty.MCU_PSN_SCREEN_STATE), Integer.valueOf((int) INVALID_VALUE)).intValue();
                    if (isPsnScreenStateChange) {
                        Slog.i(CarLog.TAG_POWER, "Received PSN_SCREEN_STATE=" + screenStateName(psnScreenState));
                    }
                    if (isScreenStateValid(psnScreenState) && isPsnScreenStateChange) {
                        this.mStateMap.put(Integer.valueOf((int) VehicleProperty.MCU_PSN_SCREEN_STATE), Integer.valueOf(psnScreenState));
                        listener.onDisplayChange("xp_mt_psg", 4, psnScreenState == 1);
                        break;
                    }
                    break;
                case VehicleProperty.MCU_PM_STATE /* 560993283 */:
                    int powerState = v.value.bytes.get(0).byteValue();
                    boolean isPowerStateChange = powerState != this.mStateMap.getOrDefault(Integer.valueOf((int) VehicleProperty.MCU_PM_STATE), Integer.valueOf((int) INVALID_VALUE)).intValue();
                    setPowerState(powerState, 0);
                    if (isMcuPmStateValid(powerState)) {
                        int screenState2 = getScreenState();
                        if (powerState == 1 && this.mStateMap.getOrDefault(Integer.valueOf((int) VehicleProperty.MCU_PM_STATE), Integer.valueOf((int) INVALID_VALUE)).intValue() == 2 && isCanUseLcdGpio()) {
                            screenState2 = 1;
                            this.mStateMap.put(Integer.valueOf((int) VehicleProperty.PM_SCREEN_STATE), 1);
                        }
                        if (canPrintLog() || isPowerStateChange) {
                            Slog.i(CarLog.TAG_POWER, "Received MCU_PM_STATE=" + mcuPmStateName(powerState) + ", SCREEN_STATE=" + screenStateName(screenState2) + ", BATTERY_STATE=" + batteryStateName(getBatteryState()) + ", XPU_FLAG=" + xpuRemoteFlagName(this.mHal.getXpuFlag()));
                        }
                        if (isPowerStateChange) {
                            this.mStateMap.put(Integer.valueOf((int) VehicleProperty.MCU_PM_STATE), Integer.valueOf(powerState));
                            listener.onApPowerStateChange(getCurrentPowerState(getBatteryState(), powerState, screenState2));
                            break;
                        } else {
                            break;
                        }
                    } else {
                        break;
                    }
                case VehicleProperty.MCU_BATTERY_DATA /* 560993288 */:
                    int batState = v.value.bytes.get(0).byteValue();
                    boolean isBatteryStateChange = batState != this.mStateMap.getOrDefault(Integer.valueOf((int) VehicleProperty.MCU_BATTERY_DATA), Integer.valueOf((int) INVALID_VALUE)).intValue();
                    if (isBatteryStateChange) {
                        Slog.i(CarLog.TAG_POWER, "Received BATTERY_STATE=" + batteryStateName(batState) + ", MCU_PM_STATE=" + mcuPmStateName(getPmState()) + ", SCREEN_STATE=" + screenStateName(getScreenState()) + ", XPU_FLAG=" + xpuRemoteFlagName(this.mHal.getXpuFlag()));
                    }
                    if (isBatteryStateValid(batState) && isBatteryStateChange) {
                        this.mStateMap.put(Integer.valueOf((int) VehicleProperty.MCU_BATTERY_DATA), Integer.valueOf(batState));
                        listener.onApPowerStateChange(getCurrentPowerState(batState, getPmState(), getScreenState()));
                        break;
                    }
                    break;
            }
        }
    }

    public boolean isShouldSleep() {
        try {
            String status = FileUtils.readTextFile(new File(XP_SLEEP_GPIO_INDICATORS_FILE), 2, "").trim();
            Slog.i(CarLog.TAG_POWER, "gpio indicator sleep status: " + status);
            if ("1".equals(status)) {
                return true;
            }
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean isShouldAwakeOn() {
        try {
            String status = FileUtils.readTextFile(new File(WAKEUP_STATE_FILE), 2, "").trim();
            Slog.i(CarLog.TAG_POWER, "gpio indicator lcd status: " + status);
            if ("1".equals(status)) {
                return true;
            }
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean isIcmShouldSilent() {
        try {
            String status = FileUtils.readTextFile(new File(XP_HMI_GPIO_INDICATORS), 2, "").trim();
            Slog.i(CarLog.TAG_POWER, "HMI gpio indicator status: " + status);
            if ("1".equals(status)) {
                return false;
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return true;
        }
    }

    public boolean isCanUseLcdGpio() {
        if (PRODUCT_MAJOR == 0 && PRODUCT_MINOR == 0 && HW_VERSION <= 3) {
            return false;
        }
        return isShouldAwakeOn();
    }

    private boolean isMcuPmStateValid(int pmState) {
        return pmState == 1 || pmState == 3 || pmState == 2;
    }

    private boolean isScreenStateValid(int screenState) {
        return screenState == 1 || screenState == 2;
    }

    private boolean isBatteryStateValid(int batState) {
        return batState == 4 || batState == 3 || batState == 5 || batState == 2 || batState == 6;
    }

    private boolean isThermalStateValid(int thermalState) {
        return thermalState == 0 || thermalState == 1;
    }

    private int getPmState() {
        int pmState = this.mStateMap.getOrDefault(Integer.valueOf((int) VehicleProperty.MCU_PM_STATE), Integer.valueOf((int) INVALID_VALUE)).intValue();
        if (pmState != INVALID_VALUE) {
            return pmState;
        }
        try {
            VehiclePropValue pmValue = this.mHal.get(VehicleProperty.MCU_PM_STATE);
            pmState = pmValue.value.bytes.get(0).byteValue();
        } catch (Exception e) {
            Slog.e(CarLog.TAG_POWER, "handleBatteryEvent failed!", e);
        }
        if (isMcuPmStateValid(pmState)) {
            return pmState;
        }
        return 1;
    }

    private int getScreenState() {
        int screenState = this.mStateMap.getOrDefault(Integer.valueOf((int) VehicleProperty.PM_SCREEN_STATE), Integer.valueOf((int) INVALID_VALUE)).intValue();
        if (screenState != INVALID_VALUE) {
            return screenState;
        }
        try {
            VehiclePropValue screenValue = this.mHal.get(VehicleProperty.PM_SCREEN_STATE);
            screenState = screenValue.value.int32Values.get(0).intValue();
        } catch (Exception e) {
            Slog.e(CarLog.TAG_POWER, "get screen status failed!", e);
        }
        if (isScreenStateValid(screenState)) {
            return screenState;
        }
        return isShouldAwakeOn() ? 1 : 2;
    }

    public int getBatteryState() {
        int batteryState = this.mStateMap.getOrDefault(Integer.valueOf((int) VehicleProperty.MCU_BATTERY_DATA), Integer.valueOf((int) INVALID_VALUE)).intValue();
        if (batteryState != INVALID_VALUE) {
            return batteryState;
        }
        try {
            VehiclePropValue batteryValue = this.mHal.get(VehicleProperty.MCU_BATTERY_DATA);
            batteryState = batteryValue.value.bytes.get(0).byteValue();
        } catch (Exception e) {
            Slog.e(CarLog.TAG_POWER, "get battery status failed!", e);
        }
        if (isBatteryStateValid(batteryState)) {
            return batteryState;
        }
        return 4;
    }

    private PowerState getCurrentPowerState(int batteryState, int pmState, int screenState) {
        if (batteryState != 2) {
            if (batteryState == 3 || batteryState == 5) {
                if (pmState == 2 || pmState == 3) {
                    return getCurrentPmPowerState(pmState, screenState);
                }
                return new PowerState(4, 2);
            } else if (batteryState != 6) {
                return getCurrentPmPowerState(pmState, screenState);
            }
        }
        return new PowerState(1, 1);
    }

    private PowerState getCurrentPmPowerState(int pmState, int screenState) {
        if (pmState != 2) {
            if (pmState == 3) {
                return new PowerState(1, 1);
            }
            return new PowerState(screenState == 1 ? 0 : 4, screenState);
        }
        return new PowerState(1, 2);
    }

    @Override // com.android.car.hal.HalServiceBase
    public void dump(PrintWriter writer) {
        writer.println("*Power HAL*");
        writer.println("isPowerStateSupported:" + isPowerStateSupported() + ",isDeepSleepAllowed:" + isDeepSleepAllowed());
    }

    private boolean canPrintLog() {
        int i = this.mLogControl;
        if (i % 24 == 0) {
            this.mLogControl = 1;
            return true;
        }
        this.mLogControl = i + 1;
        return false;
    }
}
