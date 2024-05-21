package com.android.car;

import android.car.ValueUnavailableException;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.XpVehicle.IXpVehicle;
import android.car.hardware.eps.IEpsEventListener;
import android.car.hardware.scu.IScuEventListener;
import android.car.hardware.vcu.IVcuEventListener;
import android.content.Context;
import android.content.res.Resources;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Slog;
import androidx.core.internal.view.SupportMenu;
import androidx.core.view.MotionEventCompat;
import androidx.core.view.ViewCompat;
import com.android.car.hal.PropertyTimeoutException;
import com.android.car.hal.VehicleHal;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;
import kotlin.UByte;
import kotlin.jvm.internal.ByteCompanionObject;
/* JADX INFO: Access modifiers changed from: package-private */
/* loaded from: classes3.dex */
public class XpVehicleService extends IXpVehicle.Stub implements CarServiceBase {
    public static final int HVAC_MAX_TEMPERATURE = 32;
    private static final int HVAC_MIN_FAN_SPEED_LEVEL = 1;
    public static final int HVAC_MIN_TEMPERATURE = 18;
    private static final boolean IMU_LOG = false;
    private static final String TAG = "XpVehicleService";
    private final Context mContext;
    private final VehicleHal mHal;
    private final int mHvacMaxFanSpeedLevel;
    private final boolean mIcmUseSomeIp;
    CarPropertyService mPropertyService;
    private static final boolean demoFlag = SystemProperties.getBoolean("persist.atl.demo", false);
    private static boolean AtlOpen = true;
    private static final int mCanID = SystemProperties.getInt("persist.pc.demo.canid", 343);
    private static final int mDataLen = SystemProperties.getInt("persist.pc.demo.datalen", 10);
    private static final int mBotRate = SystemProperties.getInt("persist.pc.demo.botrate", 2);
    private static final int mCanDev = SystemProperties.getInt("persist.pc.demo.candev", 3);
    private ServerThread serverThread = null;
    private ServerSocket serverSocket = null;
    private Socket socket = null;
    private DataInputStream inputStream = null;
    private DataOutputStream outputStream = null;
    private HandlerThread handlethread = null;
    private OutPutHandler outHandler = null;
    private ArrayList<OutMessage> messageArray = new ArrayList<>();
    private Object lock = new Object();

    /* loaded from: classes3.dex */
    public class Icm_Wheelkey_22 {
        public int wheelKey;

        public Icm_Wheelkey_22() {
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public XpVehicleService(Context ctx, CarPropertyService service, VehicleHal hal) {
        this.mContext = ctx;
        this.mPropertyService = service;
        this.mHal = hal;
        Resources res = this.mContext.getResources();
        this.mIcmUseSomeIp = res.getBoolean(R.bool.icmUseSomeIp);
        this.mHvacMaxFanSpeedLevel = res.getInteger(R.integer.hvacMaxFanSpeedLevel);
        Slog.i(TAG, String.format("new XpVehicleService(%b, %d)", Boolean.valueOf(this.mIcmUseSomeIp), Integer.valueOf(this.mHvacMaxFanSpeedLevel)));
        if (demoFlag) {
            openSocketThread();
        }
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
    }

    @Override // com.android.car.CarServiceBase
    public void release() {
    }

    @Override // com.android.car.CarServiceBase
    public void dump(PrintWriter writer) {
        writer.println("**dump XpVehicleService**");
        writer.println("    icm use someip:" + this.mIcmUseSomeIp + ", max HVAC fan speed level:" + this.mHvacMaxFanSpeedLevel);
    }

    public int getMcuBurglarAlarmState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.INFO_DRIVER_SEAT, 0);
    }

    public void setMcuIgOn() {
        int[] values = {0, 1};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.INFO_DRIVER_SEAT, values);
    }

    public void setMcuIgOff() {
        int[] values = {0, 1};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.INFO_DRIVER_SEAT, values);
    }

    public void setIgHeartBeat() {
        int[] values = {0, 2};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.INFO_DRIVER_SEAT, values);
    }

    public void setTheftHeartBeatOn() {
        int[] values = {2, 0};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.INFO_DRIVER_SEAT, values);
    }

    public void setTheftHeartBeatOff() {
        int[] values = {1, 0};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.INFO_DRIVER_SEAT, values);
    }

    public void setMcuIsWakeUpByPhone(int isWakeUp) {
        this.mPropertyService.setIntProperty(VehicleProperty.INFO_DRIVER_SEAT, 0, isWakeUp);
    }

    public String getMcuHardWareId() {
        return "";
    }

    public void setMqttLogInfo(String clientId, String userName, String passWord, String sslAddr) {
        this.mPropertyService.setStringProperty(VehicleProperty.INFO_DRIVER_SEAT, clientId);
        this.mPropertyService.setStringProperty(VehicleProperty.INFO_DRIVER_SEAT, userName);
        this.mPropertyService.setStringProperty(VehicleProperty.INFO_DRIVER_SEAT, passWord);
        this.mPropertyService.setStringProperty(VehicleProperty.INFO_DRIVER_SEAT, sslAddr);
    }

    public float getCpuTemperature() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.AND_SOC_TEMPERATURE, 0);
    }

    public void setMcuHorn(int on) {
        int[] cmd = {0, on};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MCU_FLASH_HORN, cmd);
    }

    public void setMcuFlash(int on) {
        int[] cmd = {on, 0};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MCU_FLASH_HORN, cmd);
    }

    public void setDrivingMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_DRIVE_MODE, 0, mode);
    }

    public int getHardwareVersion() {
        return 0;
    }

    public void sendTestMsgToMcu(int[] msg) {
        this.mPropertyService.setIntVectorProperty(VehicleProperty.INFO_DRIVER_SEAT, msg);
    }

    public void sendPwrDebugMsgToMcu(int[] msg) {
        this.mPropertyService.setIntVectorProperty(VehicleProperty.INFO_DRIVER_SEAT, msg);
    }

    public void sendDugReqMsgToMcu(int[] msg) {
        this.mPropertyService.setIntVectorProperty(VehicleProperty.INFO_DRIVER_SEAT, msg);
    }

    public void sendDisplayTypeMsgToMcu(int msg) {
        this.mPropertyService.setIntProperty(VehicleProperty.INFO_DRIVER_SEAT, 0, msg);
    }

    public void sendPmSilentMsgToMcu(int msg) {
        this.mPropertyService.setIntProperty(VehicleProperty.INFO_DRIVER_SEAT, 0, msg);
    }

    public void sendMcuBmsMsgToMcu(int msg) {
        this.mPropertyService.setIntProperty(VehicleProperty.INFO_DRIVER_SEAT, 0, msg);
    }

    public void sendOta1MsgToMcu(int[] msg) {
        this.mPropertyService.setIntVectorProperty(VehicleProperty.INFO_DRIVER_SEAT, msg);
    }

    public void sendPsuOtaMsgToMcu(int[] msg) {
        if (msg == null) {
            Slog.w(TAG, "input data is null for sendPsuOtaMsgToMcu");
        } else if (msg.length <= 2) {
            Slog.w(TAG, "the length of the input data is incorrect for sendPsuOtaMsgToMcu");
        } else {
            byte[] data = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
            data[0] = (byte) (msg[0] & 255);
            data[1] = (byte) ((msg[0] >> 8) & 255);
            data[2] = (byte) ((msg[0] >> 16) & 255);
            data[3] = (byte) ((msg[0] >> 24) & 255);
            data[4] = (byte) (msg[1] & 255);
            data[5] = (byte) ((msg[1] >> 8) & 255);
            data[6] = (byte) ((msg[1] >> 16) & 255);
            this.mPropertyService.setByteVectorProperty(VehicleProperty.MCU_PSU_OTA_MSG, data);
        }
    }

    public int[] getMcuPsuOtaFeedbackMsg() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.MCU_DORMANCY_TIME);
    }

    public void sendSecretKeyToMcu(byte[] msg) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.INFO_DRIVER_SEAT, msg);
    }

    public void sendRequestWakeToMcu(int event) {
        int[] cmd = {event, 0};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.INFO_DRIVER_SEAT, cmd);
    }

    public void sendDiagnoseMsgToMcu(int[] cmd) {
        this.mPropertyService.setIntVectorProperty(VehicleProperty.INFO_DRIVER_SEAT, cmd);
    }

    public void sendReset4gMsgToMcu(int[] msg) {
        this.mPropertyService.setIntVectorProperty(VehicleProperty.INFO_DRIVER_SEAT, msg);
    }

    public void sendResetModemMsgToMcu(int msg) {
        this.mPropertyService.setIntProperty(VehicleProperty.INFO_DRIVER_SEAT, 0, msg);
    }

    public void sendGpsInfoMsgToMcu(int[] msg) {
        this.mPropertyService.setIntVectorProperty(VehicleProperty.INFO_DRIVER_SEAT, msg);
    }

    public void updateMcuBin(String path) {
    }

    public long getMcuRtcTime() {
        return this.mPropertyService.getLongProperty(VehicleProperty.INFO_DRIVER_SEAT, 0);
    }

    public void setMcuRtcTime(long rtcTime) {
        this.mPropertyService.setLongProperty(VehicleProperty.INFO_DRIVER_SEAT, 0, rtcTime);
    }

    public int[] getMcuDtcReportEv() {
        int[] ret = new int[4];
        byte[] src = this.mPropertyService.getByteVectorProperty(VehicleProperty.MCU_DTC_REPORT_EV);
        if (src != null) {
            for (int i = 0; i < 4; i++) {
                ret[i] = src[i];
            }
        }
        return ret;
    }

    public String getMcuFactoryDisplayTypeMsgToMcu() {
        return this.mPropertyService.getStringProperty(VehicleProperty.MCU_CMD_DISPLAY);
    }

    public byte[] getMcuFaultInfo() {
        return this.mPropertyService.getByteVectorProperty(VehicleProperty.MCU_SYNC_DATA);
    }

    public void setMcuTimeZone(int timeZoneValue) {
        this.mPropertyService.setIntProperty(VehicleProperty.INFO_DRIVER_SEAT, 0, timeZoneValue);
    }

    public int getPmStatus() {
        Slog.i(TAG, "getPmStatus");
        try {
            VehiclePropValue pmValue = this.mHal.get(VehicleProperty.MCU_PM_STATE);
            int pmState = pmValue.value.bytes.get(0).byteValue();
            return pmState;
        } catch (PropertyTimeoutException e) {
            throw new ValueUnavailableException();
        }
    }

    public byte[] getPmStatusWithParameter() {
        byte[] ret = new byte[2];
        try {
            VehiclePropValue pmValue = this.mHal.get(VehicleProperty.MCU_PM_STATE);
            ret[0] = pmValue.value.bytes.get(0).byteValue();
            ret[1] = pmValue.value.bytes.get(1).byteValue();
            return ret;
        } catch (PropertyTimeoutException e) {
            throw new ValueUnavailableException();
        }
    }

    public int[] getGSensorOffset() {
        int[] gSensorOffset = new int[3];
        byte[] src = this.mPropertyService.getByteVectorProperty(VehicleProperty.MCU_G_SENSOR_EV);
        if (src != null) {
            for (int n = 0; n < 3; n++) {
                gSensorOffset[n] = bytesToShort(src, n * 2);
            }
        }
        return gSensorOffset;
    }

    public int[] getScreenTempValue() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.INFO_DRIVER_SEAT);
    }

    public int getMcuBatteryStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_BATTERY_DATA, 0);
    }

    public int getMcuIgState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_IG_DATA, 0);
    }

    public int getMcuCameraStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_R_CAMERA_DATA, 0);
    }

    public int getMcuLampStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_R_LIGHT_DATA, 0);
    }

    public int getMcuChargeStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_BATCHARG_DATA, 0);
    }

    public int getMcuUpdateReqStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.OTA_MCU_REQ_STATUS, 0);
    }

    public void setMcuUpdateReqStatus(int data) {
        this.mPropertyService.setIntProperty(VehicleProperty.OTA_MCU_REQ_STATUS, 0, data);
    }

    public int getOtaMcuReqUpdatefile() {
        return this.mPropertyService.getIntProperty(VehicleProperty.OTA_MCU_REQ_UPDATEFILE, 0);
    }

    public void setOtaMcuReqUpdatefile(int data) {
        this.mPropertyService.setIntProperty(VehicleProperty.OTA_MCU_REQ_UPDATEFILE, 0, data);
    }

    public void setOtaMcuSendUpdatefile(String file) {
        this.mPropertyService.setStringProperty(VehicleProperty.OTA_MCU_SEND_UPDATEFILE, file);
    }

    public int getOtaMcuUpdateStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.OTA_MCU_UPDATE_STATUS, 0);
    }

    public void setMcuRepairMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.MCU_ENTER_REPAIR_MODE, 0, mode);
    }

    public void setMcuPsuTestReq(int req) {
        this.mPropertyService.setIntProperty(VehicleProperty.MCU_PSU_TEST, 0, req);
    }

    public int getMcuPsuTestResult() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_PSU_TEST, 0);
    }

    public float getDvTestMcuTemp() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.MCU_DVTEST_MCU_TM, 0);
    }

    public byte[] getMcuDvBattMsg() {
        return this.mPropertyService.getByteVectorProperty(VehicleProperty.MCU_DVTEST_BBAT);
    }

    public float getDvTestBatTemp() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.MCU_DVTEST_BAT_TM, 0);
    }

    public float getDvTestPcbTemp() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.MCU_DVTEST_PCB_TM, 0);
    }

    public void setMcuDvTestReq(int req) {
        this.mPropertyService.setIntProperty(VehicleProperty.MCU_DVTEST_SW, 0, req);
    }

    public void setMcuDvTempSamplingPeriod(int second) {
        this.mPropertyService.setIntProperty(VehicleProperty.MCU_DVTEST_TMP_CYCLE, 0, second);
    }

    public int getMcuChairWelcomeMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMD_MCU_WELCOME, 0);
    }

    public void setMcuChairWelcomeMode(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMD_MCU_WELCOME, 0, enable);
    }

    public void setMcuRemoteControlFeedback(int status) {
        byte[] cmd = {(byte) (status & 255)};
        this.mPropertyService.setByteVectorProperty(VehicleProperty.MCU_REMOTE_FLAG, cmd);
    }

    public int getMcuOcuState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_REPORT_OCU_ST, 0);
    }

    public int getMcuCiuState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_REPORT_CIU_ST, 0);
    }

    public int getMcuAtlsState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_ATLS_STATUS, 0);
    }

    public void setMcuFaceIdSw(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.CDU_IBCM_FACEIDREADYSW, 0, enable);
    }

    public int getMcuFaceIdSwState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CDU_IBCM_FACEIDREADYSW, 0);
    }

    public void setFaceIdModeState(int state) {
        this.mPropertyService.setIntProperty(VehicleProperty.CDU_IBCM_FACEIDMODE, 0, state);
    }

    public int getMcuFaceIdMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CDU_IBCM_FACEIDMODE, 0);
    }

    public void setMcuAndroidOtaStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.OS_OTA_STATUS, 0, status);
    }

    public void setMcuAutoPowerOffSw(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.MCU_POWERDOWN_SWITCH, 0, enable);
    }

    public int getMcuAutoPowerOffSwitchState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_POWERDOWN_SWITCH, 0);
    }

    public void setMcuPowerOffCountdownAction(int action) {
        this.mPropertyService.setIntProperty(VehicleProperty.MCU_POWERDOWN_ACTION, 0, action);
    }

    public int getMcuPowerOffCountdownNotice() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_POWERDOWN_ACTION, 0);
    }

    public String getMcuVersion() {
        return this.mPropertyService.getStringProperty(VehicleProperty.MCU_OTA_RESP_VER);
    }

    public void setMcuHornsStates(int lfHornSt, int lrHornSt, int rfHornSt, int rrHornSt) {
        int[] data = {lfHornSt, lrHornSt, rfHornSt, rrHornSt};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MCU_DIAG_HORN_INFO, data);
    }

    public void sendChargeCompleteTimeToMcu(int min) {
        this.mPropertyService.setIntProperty(VehicleProperty.MCU_TIME_TO_COMPLETE_CHARGE, 0, min);
    }

    public int getMcuRequestedMessage() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_GET_INFO_MSG, 0);
    }

    public void setMcuMonitorSwitch(int mode, int timeInMinutes) {
        int[] data = {mode, timeInMinutes};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MCU_STOP_MONITOR, data);
    }

    public int getMcuMonitorState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_MONITOR_ST, 0);
    }

    public void setMcuDelaySleep(int heartBeat, int value) {
        int[] data = {heartBeat, value};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MCU_DELAY_SLEEP, data);
    }

    public void sendMcuBleAccountDataFeedback(int feedback) {
        this.mPropertyService.setIntPropertyWithDefaultArea(VehicleProperty.MCU_BLE_ID_DATA_RSP, feedback);
    }

    public byte[] getMcuBleAccountData() {
        return this.mPropertyService.getByteVectorProperty(VehicleProperty.MCU_BLE_ID_DATA);
    }

    public int getMcuCidState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_CID_FB, 0);
    }

    public void setMcuRvcState(int state) {
        this.mPropertyService.setIntProperty(VehicleProperty.MCU_RVC_EN_CONTROL, 0, state);
    }

    public int getMcuBacklightTemperature() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_BL_TEMP, 0);
    }

    public int getMcuBacklightIcDriverState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_BL_IC_DRIVER_ST, 0);
    }

    public void setMcuTrunkPowerSw(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.MCU_REQ_TRKP_MODE, 0, sw);
    }

    public int getMcuTrunkPowerStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_REQ_TRKP_MODE, 0);
    }

    public void setMcuTrunkPowerOffDelay(int delay) {
        this.mPropertyService.setIntProperty(VehicleProperty.MCU_REQ_TRKP_TIMEON, 0, delay);
    }

    public int getMcuTrunkPowerOffDelay() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_REQ_TRKP_TIMEON, 0);
    }

    public void setMcuFactoryModeSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.MCU_FACTORY_MODE, 0, enable);
    }

    public int getMcuFactoryModeSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_FACTORY_MODE, 0);
    }

    public int getMcuTemporaryFactoryStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_TEMP_FACTORY, 0);
    }

    public int getMcuWifiHotspotRequest() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_WIFI_HOTSPOT_REQ, 0);
    }

    public void sendMcuOpenWifiHotspotResponse(int response) {
        this.mPropertyService.setIntProperty(VehicleProperty.MCU_WIFI_HOTSPOT_REQ, 0, response);
    }

    public int getMcuKeyStartStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_FAILED_POP_UP_ALARM, 0);
    }

    public int getMcuTrunkPowerOnRequest() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_TRUNK_POWERON_TO_APP, 0);
    }

    public void sendMcuMapVersion(String version) {
        byte[] data = new byte[64];
        byte[] versionBytes = version.getBytes();
        int length = Math.min(versionBytes.length, data.length);
        System.arraycopy(versionBytes, 0, data, 0, length);
        this.mPropertyService.setByteVectorProperty(VehicleProperty.MCU_MAP_INF_SYNC, data);
    }

    public void sendMcuTboxVBusControlCommand(int cmd) {
        this.mPropertyService.setIntProperty(VehicleProperty.MCU_TBOX_USB_VBUS_CTRL, 0, cmd);
    }

    public int getMcuRvcEnable() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_RVC_EN, 0);
    }

    public void setMcuRvcEnable(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.MCU_RVC_EN, 0, enable);
    }

    public void setMcuRvcVersion(int version) {
        this.mPropertyService.setIntProperty(VehicleProperty.MCU_RVC_VERSION, 0, version);
    }

    public int getMcuRemindWarningStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_REMIND_WARNING, 0);
    }

    public void setMcuSocRespDTCInfo(int module, int errCode, int errCodeSt) {
        int[] data = {module, errCode, errCodeSt};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MCU_SOC_RESP_DTC_INFO, data);
    }

    public void sendMcuGeofenceStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.ICM_GEOFENCE, 0, status);
    }

    public void sendMcuOtaUpdateFile(String None) {
        this.mPropertyService.setStringProperty(VehicleProperty.OTA_SEND_UPDATE_FILE, None);
    }

    public int isBcmRearFogLampOn() {
        return this.mPropertyService.getIntProperty(VehicleProperty.FOG_LIGHTS_SWITCH_XP, 0);
    }

    public void setBcmRearFogLampOn(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.FOG_LIGHTS_SWITCH_XP, 0, enable);
    }

    public int getBcmFrontLampMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_FRONT_LIGHT_GROUP_MODE, 0);
    }

    public void setBcmFrontLampMode(int groupid) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_FRONT_LIGHT_GROUP_MODE, 0, groupid);
    }

    public int getBcmFarLampState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HIGH_BEAM_LIGHTS_STATE, 0);
    }

    public int getBcmNearLampState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_LOW_BEAM, 0);
    }

    public int isBcmOutlineMarkerLampsOn() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_PARKING_LAMP, 0);
    }

    public void setBcmRearViewMirrorPos(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_MIRROR_SW, 0, type);
    }

    public void setBcmInternalLightOn(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_INTERNAL_LIGHT_ON, 0, enable);
    }

    public int isBcmInternalLightOn() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_INTERNAL_LIGHT_ON, 0);
    }

    public void setBcmEmergencyBrakeWarning(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_EMERGENCY_BRAKE, 0, enable);
    }

    public int isBcmEmergencyBrakeWarningEnabled() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_EMERGENCY_BRAKE, 0);
    }

    public void setBcmWindowMoveCmd(int window, int cmd) {
        if (window == 0) {
            this.mPropertyService.setIntProperty(VehicleProperty.BCM_FLSWST, 0, cmd);
        } else if (window == 1) {
            this.mPropertyService.setIntProperty(VehicleProperty.BCM_FRSWST, 0, cmd);
        } else if (window == 2) {
            this.mPropertyService.setIntProperty(VehicleProperty.BCM_RLSWST, 0, cmd);
        } else if (window == 3) {
            this.mPropertyService.setIntProperty(VehicleProperty.BCM_RRSWST, 0, cmd);
        } else if (window == 4) {
            byte[] data = {(byte) (cmd & 255), (byte) (cmd & 255), (byte) (cmd & 255), (byte) (cmd & 255)};
            Slog.i(TAG, "setWindowMoveCmd all cmd=" + ((int) data[0]));
            this.mPropertyService.setByteVectorProperty(VehicleProperty.BCM_ALLSWST, data);
        }
    }

    public void setBcmWindowMovePosition(int window, float position) {
        if (position > 100.0f || position < 0.0f) {
            return;
        }
        if (window == 0) {
            this.mPropertyService.setFloatProperty(VehicleProperty.CDU_IBCM_FLPOS, 0, position);
        } else if (window == 1) {
            this.mPropertyService.setFloatProperty(VehicleProperty.CDU_IBCM_FRPOS, 0, position);
        } else if (window == 2) {
            this.mPropertyService.setFloatProperty(VehicleProperty.CDU_IBCM_RLPOS, 0, position);
        } else if (window == 3) {
            this.mPropertyService.setFloatProperty(VehicleProperty.CDU_IBCM_RRPOS, 0, position);
        } else if (window == 4) {
            float[] data = {position, position, position, position};
            Slog.i(TAG, "setWindowMovePosition all pos=" + data[0]);
            this.mPropertyService.setFloatVectorProperty(VehicleProperty.BCM_ALLPOS, data);
        }
    }

    public float getBcmWindowMovePosition(int window) {
        if (window == 0) {
            float retVal = this.mPropertyService.getFloatProperty(VehicleProperty.CDU_IBCM_FLPOS, 0);
            return retVal;
        } else if (window == 1) {
            float retVal2 = this.mPropertyService.getFloatProperty(VehicleProperty.CDU_IBCM_FRPOS, 0);
            return retVal2;
        } else if (window == 2) {
            float retVal3 = this.mPropertyService.getFloatProperty(VehicleProperty.CDU_IBCM_RLPOS, 0);
            return retVal3;
        } else if (window != 3) {
            return 0.0f;
        } else {
            float retVal4 = this.mPropertyService.getFloatProperty(VehicleProperty.CDU_IBCM_RRPOS, 0);
            return retVal4;
        }
    }

    public int getBcmAtwsState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_ATWS, 0);
    }

    public float[] getBcmAllWindowsPos() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.BCM_WIN_POS);
    }

    public int getBcmLightMeHomeMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_FOLLOW_ME_HOME, 0);
    }

    public void setBcmLightMeHomeMode(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_FOLLOW_ME_HOME, 0, type);
    }

    public void setBcmDrvAutoLockEnabled(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_AUTO_LOCK, 0, enable);
    }

    public int isBcmDrvAutoLockEnabled() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_AUTO_LOCK, 0);
    }

    public void setBcmParkingAutoUnlockEnabled(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_AUTO_UNLOCK, 0, enable);
    }

    public int isBcmParkingAutoUnlockEnabled() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_AUTO_UNLOCK, 0);
    }

    public void setBcmHazardLampsFlash(int on) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_HAZARDLIGHT_SW, 0, on);
    }

    public void setBcmDoorLock(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_CENTRAL_LOCK_SW, 0, enable);
    }

    public int getBcmDoorLockState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_CENTRAL_LOCK_SW, 0);
    }

    public void setBcmTrunkOpen(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_TRUNKAJAR, 0, enable);
    }

    public int getBcmTrunkStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_TRUNKAJAR, 0);
    }

    public int getBcmWiperInterval() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CDU_IBCM_WIPERINTSW, 0);
    }

    public void setBcmWiperInterval(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.CDU_IBCM_WIPERINTSW, 0, level);
    }

    public void setChairSlowlyAhead(int type) {
    }

    public void setChairSlowlyBack(int type) {
    }

    public void setChairSlowlyEnd(int type) {
    }

    public int[] getChairDirection() {
        int[] ret = {0, 0, 0};
        return ret;
    }

    public int[] getChairLocationValue() {
        int[] ret = {0, 0, 0};
        return ret;
    }

    public void setChairPositionStart(int level, int height, int angle) {
    }

    public void setChairPositionEnd() {
    }

    public int getBcmChairWelcomeMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMD_DRIVER_SEAT_XP, 0);
    }

    public void setBcmChairWelcomeMode(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMD_DRIVER_SEAT_XP, 0, enable);
    }

    public void setBcmElectricSeatBeltEnabled(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_MSB_ACTIVE, 0, enable);
    }

    public int isBcmElectricSeatBeltEnabled() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_MSB_ACTIVE, 0);
    }

    public void setBcmRearSeatBeltWarningEnabled(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_2ND_BELT_WARNING, 0, enable);
    }

    public int isBcmRearSeatBeltWarningEnabled() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_2ND_BELT_WARNING, 0);
    }

    public int getBcmUnlockResponseMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_PREARMED, 0);
    }

    public void setBcmUnlockResponseMode(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_PREARMED, 0, type);
    }

    public int[] getBcmDoorsState() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.BCM_DOOR);
    }

    public void setBcmBackDefrostMode_() {
    }

    public int getBcmBackDefrostMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_REAR_DEFROST_ON, 0);
    }

    public void setBcmBackDefrostMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_REAR_DEFROST_ON, 0, mode);
    }

    public void setBcmBackMirrorHeatMode_() {
    }

    public int getBcmBackMirrorHeatMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_MIRRORHEAT, 0);
    }

    public void setBcmBackMirrorHeatMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_MIRRORHEAT, 0, mode);
    }

    public void setBcmSeatHeatLevel_() {
        Slog.e(TAG, "not impl");
    }

    public int getBcmSeatHeatLevel() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SEAT_HEAT_LEVEL, 0);
    }

    public void setBcmSeatHeatLevel(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_SEAT_HEAT_LEVEL, 0, level);
    }

    public void setBcmSeatBlowLevel_() {
        Slog.e(TAG, "not impl");
    }

    public int getBcmSeatBlowLevel() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_SEAT_VENTILATION, 0);
    }

    public void setBcmSeatBlowLevel(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_SEAT_VENTILATION, 0, level);
    }

    public int getBcmIgStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_POWERMODE, 0);
    }

    public int getBcmPowerOffSource() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_POWEROFF_SOURCE, 0);
    }

    public float getBcmFrontLeftWinPos() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.CDU_IBCM_FLPOS, 0);
    }

    public float getBcmFrontRightWinPos() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.CDU_IBCM_FRPOS, 0);
    }

    public float getBcmRearLeftWinPos() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.CDU_IBCM_RLPOS, 0);
    }

    public float getBcmRearRightWinPos() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.CDU_IBCM_RRPOS, 0);
    }

    public int getDriverDoorState() {
        int[] ret = this.mPropertyService.getIntVectorProperty(VehicleProperty.BCM_DOOR);
        if (ret == null) {
            return 0;
        }
        return ret[0];
    }

    public void setFactoryOledData(byte[] data) {
    }

    public void setFactoryOledDisplayMode(int mode) {
    }

    public void setBcmChargePortUnlock(int port, int unlock) {
        if (port == 1 || port == 0) {
            this.mPropertyService.setIntProperty(VehicleProperty.BCM_L_CHARGER_PORT, 0, unlock);
        } else if (port == 2) {
            this.mPropertyService.setIntProperty(VehicleProperty.BCM_R_CHARGER_PORT, 0, unlock);
        } else {
            Slog.e(CarLog.TAG_XPVEHICLE, "setChargePortUnlock wrong port:" + port);
        }
    }

    public int getSeatErrorState() {
        int[] src = this.mPropertyService.getIntVectorProperty(VehicleProperty.BCM_SEAT_ERR);
        if (src != null) {
            for (int i : src) {
                if (i == 1) {
                    return 1;
                }
            }
        }
        return 0;
    }

    public void setVentilate() {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_WIN_CMD, 0, 4);
    }

    public int getWelcomeModeBackStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_WELCOME_ST, 0);
    }

    public void openBcmBonnet() {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_BONNET, 0, 1);
    }

    public int isBcmBonnetOpened() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_BONNET, 0);
    }

    public int getBcmBonnetStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_BONNET, 0);
    }

    public int getBcmFollowMeTime() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_FOLLOW_ME_HOME_TIME, 0);
    }

    public void setBcmFollowMeTime(int timeType) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_FOLLOW_ME_HOME_TIME, 0, timeType);
    }

    public int getBcmDayLightMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_DRL_CFG, 0);
    }

    public void setBcmDayLightMode(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_DRL_CFG, 0, enable);
    }

    public int getBcmDomeLightCfg() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_DEMOLIGHT_SW, 0);
    }

    public void setBcmDomeLightCfg(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_DEMOLIGHT_SW, 0, type);
    }

    public void setBcmNfcCardEnabled(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_STOPNFCCAR_SW, 0, enable);
    }

    public int getBcmNfcCardSwitchState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_STOPNFCCAR_SW, 0);
    }

    public void setBcmAutoWindowCmd(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_COMFORTWND_CMD, 0, type);
    }

    public void setBcmWindowRemoteCtrlCfg(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_REMOTECTRL_CFG, 0, type);
    }

    public int getWindowRemoteCtrlCfg() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_REMOTECTRL_CFG, 0);
    }

    public int getBcmChildLockCfg() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_CHILDLOCK_SW, 0);
    }

    public void setBcmChildLockCfg(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_CHILDLOCK_SW, 0, type);
    }

    public void setBcmLeftMirrorCtrlCmd(int type) {
    }

    public void setBcmRightMirrorCtrlCmd(int type) {
    }

    public void setBcmLeftMirrorMove(int control, int direction) {
        int[] data = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.BCM_LMIRROR_CTRL, data);
    }

    public void setBcmRightMirrorMove(int control, int direction) {
        int[] data = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.BCM_RMIRROR_CTRL, data);
    }

    public int getBcmLeftMirrorHorizPos() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_RMALLR_LOCATION, 0);
    }

    public void setBcmLeftMirrorHorizPos(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_RMALLR_LOCATION, 0, pos);
    }

    public int getBcmRightMirrorHorizPos() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_RMARLR_LOCATION, 0);
    }

    public void setBcmRightMirrorHorizPos(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_RMARLR_LOCATION, 0, pos);
    }

    public int getBcmLeftMirrorVerticalPos() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_RMALUD_LOCATION, 0);
    }

    public void setBcmLeftMirrorVerticalPos(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_RMALUD_LOCATION, 0, pos);
    }

    public int getBcmRightMirrorVerticalPos() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_RMARUD_LOCATION, 0);
    }

    public void setBcmRightMirrorVerticalPos(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_RMARUD_LOCATION, 0, pos);
    }

    public int getBcmReverseMirrorCfgCmd() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_REVERSECAR_MIRROR_CFG, 0);
    }

    public void setBcmReverseMirrorCfgCmd(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_REVERSECAR_MIRROR_CFG, 0, type);
    }

    public void setBcmShcReq(int flag) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_SHC_REQ, 0, flag);
    }

    public int getBcmWiperServiceMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_WIPERSERVICE_SW, 0);
    }

    public void setBcmWiperServiceMode(int on) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_WIPERSERVICE_SW, 0, on);
    }

    public int getBcmManualFrontLeftWinStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_FLWIN_SWST, 0);
    }

    public int getBcmManualFrontRightWinStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_FRWIN_SWST, 0);
    }

    public int getBcmManualRearLeftWinStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_RLWIN_SWST, 0);
    }

    public int getBcmManualRearRightWinStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_RRWIN_SWST, 0);
    }

    public int[] getLeftFrontDoorOpened() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.BCM_DOOR);
    }

    public int isBcmDriverOnSeat() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_DRIVERSEAT_OCCUPIED, 0);
    }

    public void setBcmHighBeamMode(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_HIGHBEAM_SW, 0, type);
    }

    public int getBcmLeftTurnLampStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_LTURNLAMP_ST, 0);
    }

    public int getBcmRightTurnLampStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_RTURNLAMP_ST, 0);
    }

    public int getBcmChargePortStatus(int port) {
        if (port == 1) {
            return this.mPropertyService.getIntProperty(VehicleProperty.BCM_L_CHARGER_PORT, 0);
        }
        if (port == 2) {
            return this.mPropertyService.getIntProperty(VehicleProperty.BCM_R_CHARGER_PORT, 0);
        }
        return 0;
    }

    public int getBcmPowerMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_POWERMODE, 0);
    }

    public int getBcmPsnSeatHeatLevel() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_PSNSEAT_HEAT_LEVEL, 0);
    }

    public void setBcmPsnSeatHeatLevel(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_PSNSEAT_HEAT_LEVEL, 0, level);
    }

    public int getCwcChargeSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_CWCCHRG_ST, 0);
    }

    public int getCwcChargeErrorSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_CWCCHRG_ABNORMAL_WARNING, 0);
    }

    public int getFRCwcChargeSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_FR_CWCCHRG_ST, 0);
    }

    public int getFRCwcChargeErrorSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_FR_CWCCHRG_ABNORMAL_WARNING, 0);
    }

    public int getAutoWindowLockSw() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_LOCKWIN_CFG, 0);
    }

    public void setAutoWindowLockSw(int active) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_LOCKWIN_CFG, 0, active);
    }

    public int getLeavePollingLockSw() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_POLLING_LOCK_CFG, 0);
    }

    public void setLeavePollingLockSw(int active) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_POLLING_LOCK_CFG, 0, active);
    }

    public void setNearPollingUnLockSw(int active) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_POLLING_UNLOCK_CFG, 0, active);
    }

    public int getNearePollingUnLockSw() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_POLLING_UNLOCK_CFG, 0);
    }

    public void setStealthMode(int active) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_STEALTH_MODE, 0, active);
    }

    public void getStealthMode() {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_STEALTH_GET_MODE, 0, 0);
    }

    public int getBcmPollingOpenCfg() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_POLLING_OPEN_CFG, 0);
    }

    public void setBcmPollingOpenCfg(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_POLLING_OPEN_CFG, 0, status);
    }

    public int getBcmDriverBeltWarning() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_DRIVER_SBLT_WARNING, 0);
    }

    public int getBcmRearViewAutoDownCfg() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_RAUTODOWN_CFG, 0);
    }

    public void setBcmRearViewAutoDownCfg(int cfg) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_RAUTODOWN_CFG, 0, cfg);
    }

    public int getBcmChargeGunLockSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_CHARG_GUNL_ST, 0);
    }

    public void setBcmAllExteriorMirrorsPositions(int lMirrorHorizonPos, int lMirrorVerticalPos, int rMirrorHorizonPos, int rMirrorVerticalPos) {
        byte[] data = {(byte) (lMirrorHorizonPos & 255), (byte) (lMirrorVerticalPos & 255), (byte) (rMirrorHorizonPos & 255), (byte) (rMirrorVerticalPos & 255)};
        this.mPropertyService.setByteVectorProperty(VehicleProperty.BCM_RMAALL_CTRL, data);
    }

    @Deprecated
    public void setCmsAllExteriorMirrorsPositions(int lMirrorHorizonPos, int lMirrorVerticalPos, int rMirrorHorizonPos, int rMirrorVerticalPos) {
        int[] data = {lMirrorHorizonPos, lMirrorVerticalPos, rMirrorHorizonPos, rMirrorVerticalPos};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.CMS_VISION_POINT_CTRL, data);
    }

    public void setLRCMSAllExteriorMirrorsPositions(float lMirrorHorizonPos, float lMirrorVerticalPos, float rMirrorHorizonPos, float rMirrorVerticalPos) {
        float[] data = {lMirrorHorizonPos, lMirrorVerticalPos, rMirrorHorizonPos, rMirrorVerticalPos};
        this.mPropertyService.setFloatVectorProperty(VehicleProperty.CMS_VISION_POINT_CTRL_FLOAT, data);
    }

    public float[] getLRCMSAllExteriorMirrorsPositions() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.CMS_VISION_POINT_CTRL_FLOAT);
    }

    public void setBcmWindowsMovePositions(float flPosition, float frPosition, float rlPosition, float rrPosition) {
        float[] data = {flPosition, frPosition, rlPosition, rrPosition};
        this.mPropertyService.setFloatVectorProperty(VehicleProperty.BCM_ALLPOS, data);
    }

    public void setBcmSdcMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.SDC_MODE_SW, 0, mode);
    }

    public void setBcmTwcMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.TWC_MODE_SW, 0, mode);
    }

    public void setBcmTwcUpdownSwitch(int state) {
        this.mPropertyService.setIntProperty(VehicleProperty.TWC_UPDOWN_SW, 0, state);
    }

    public void setBcmLeftSdcSwitch(int state) {
        this.mPropertyService.setIntProperty(VehicleProperty.SDCL_SW, 0, state);
    }

    public void setBcmRightSdcSwitch(int state) {
        this.mPropertyService.setIntProperty(VehicleProperty.SDCR_SW, 0, state);
    }

    public int getBcmLeftChargePortLockState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_L_CHARGER_LOCK_PORT, 0);
    }

    public int getBcmRightChargePortLockState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_R_CHARGER_LOCK_PORT, 0);
    }

    public int getAlsInitializationStudyState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ALS_INISTUDY_ST, 0);
    }

    public int[] getAlsInitializationStudyAndErrorState() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.ALS_INISTUDY_AND_ERROR_ST);
    }

    public void setBcmWiperRainDetectSensitivity(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_RAIN_DETEC_SENCFG, 0, level);
    }

    public void setWiperRainDetectSensitivityAndInactive(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_RAIN_SEN_AND_INACTIVE, 0, level);
    }

    public int getBcmWiperRainDetectSensitivity() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_RAIN_DETEC_SENCFG, 0);
    }

    public void setBcmWindowLockState(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_WIN_LOCK_ST, 0, onOff);
    }

    public int getBcmWindowLockState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_WIN_LOCK_ST, 0);
    }

    public int[] getBcmLeftAndRightTurnLampStatus() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.BCM_LRTURNLAMP_ST);
    }

    public void setBcmParkLightRelatedFMBLightConfig(int cfg) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_PAKLIGHT_FMB_CFG, 0, cfg);
    }

    public int getBcmParkLightRelatedFMBLightConfigState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_PAKLIGHT_FMB_CFG, 0);
    }

    public int[] getBcmParkingLampsStates() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.BCM_PACKING_LAMP_OUTPUT);
    }

    public int getBcmDoorUnlockRequestSource() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_DOOR_UNLOCK_REQRES, 0);
    }

    public int getBcmLeftSdcPsdMotorState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SDCL_PSDMOTO_ST, 0);
    }

    public int getBcmRightSdcPsdMotorState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SDCR_PSDMOTO_ST, 0);
    }

    public int getBcmKeyAuthState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TBOX_IBCM_KEYAUTHST, 0);
    }

    public int getBcmWiperSpeedSwitchState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_WIPERSPDSW_ST, 0);
    }

    public void sendBcmSeatBeltRequest(int req) {
        this.mPropertyService.setIntPropertyWithDefaultArea(VehicleProperty.BCM_CDU_SEATBELT_REQ, req);
    }

    public byte[] getBcmNfcCardIdInfo() {
        return this.mPropertyService.getByteVectorProperty(VehicleProperty.BCM_NFC_CARDIDINFO);
    }

    public int getBcmAutoLightState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_AUTO_LIGHT, 0);
    }

    public void setAutoLightSwitch(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_AUTO_LIGHT, 0, onOff);
    }

    public int getWasherFluidWarning() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_WASH_FLUID_WARN, 0);
    }

    public int getBcmSeatHeatErrStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SEAT_HEAT_ERR, 0);
    }

    public int[] getBcmPassengerSeatBeltSbrWarningStatus() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.BCM_PSNG_SBSBR_WARNING);
    }

    public void setBcmRearLeftSeatHeatSw(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_RL_SEAT_HEAT, 0, sw);
    }

    public int getBcmRearLeftSeatHeatState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_RL_SEAT_HEAT, 0);
    }

    public void setBcmRearRightSeatHeatSw(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_RR_SEAT_HEAT, 0, sw);
    }

    public int getBcmRearRightSeatHeatState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_RR_SEAT_HEAT, 0);
    }

    public int getBcmRearLeftHeaterErrorState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_RL_SEAT_HEAT_ERR, 0);
    }

    public int getBcmRearRightHeaterErrorState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_RR_SEAT_HEAT_ERR, 0);
    }

    public void setBcmSdcKeyOpenCtrlCfg(int cfg) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_SDC_KEY_OPENMODE_CTRL, 0, cfg);
    }

    public int getBcmSdcKeyOpenCtrlCfg() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SDC_KEY_OPENMODE_CTRL, 0);
    }

    public void setBcmSdcKeyCloseCtrlCfg(int cfg) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_SDC_KEY_CLOSEMODE_CTRL, 0, cfg);
    }

    public int getBcmSdcKeyCloseCtrlCfg() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SDC_KEY_CLOSEMODE_CTRL, 0);
    }

    public void setBcmSdcMaxAutoDoorOpeningAngle(int maxAngle) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_SDC_OPENHALL_CFG, 0, maxAngle);
    }

    public int getBcmSdcMaxAutoDoorOpeningAngle() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SDC_OPENHALL_CFG, 0);
    }

    public int getBcmLeftSdcHazzardRequest() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SDCL_HAZZARD_REQ, 0);
    }

    public int getBcmRightSdcHazzardRequest() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SDCR_HAZZARD_REQ, 0);
    }

    public int getBcmLeftSdcSystemErrorState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SDCL_SYSERR, 0);
    }

    public int getBcmRightSdcSystemErrorState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SDCR_SYSERR, 0);
    }

    public int getBcmLeftSdcDenormalizeState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SDCL_DENORMALIZE_ST, 0);
    }

    public int getBcmRightSdcDenormalizeState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SDCR_DENORMALIZE_ST, 0);
    }

    public void setBcmLeftSdcWindowsAutoDownSwitch(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_SDC_WINS_AUTODOWN_L, 0, sw);
    }

    public int getBcmLeftSdcWindowsAutoDownSwitchState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SDC_WINS_AUTODOWN_L, 0);
    }

    public void setBcmRightSdcWindowsAutoDownSwitch(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_SDC_WINS_AUTODOWN_R, 0, sw);
    }

    public int getBcmRightSdcWindowsAutoDownSwitchState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SDC_WINS_AUTODOWN_R, 0);
    }

    public void setBcmLeftSdcAutoOrManualControl(int cmd, int sw) {
        int[] data = {cmd, sw};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.SDCL_SW_AUTO_AND_MANUAL, data);
    }

    public void setBcmRightSdcAutoOrManualControl(int cmd, int sw) {
        int[] data = {cmd, sw};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.SDCR_SW_AUTO_AND_MANUAL, data);
    }

    public int getBcmReadyEnableState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_READY_ENABLE, 0);
    }

    public int getBcmBreakPedalStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_BRKPEDAL_ST, 0);
    }

    public void setBcmLeftSdcDoorPosition(int position) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_SDCL_DOORPOS, 0, position);
    }

    public void setBcmRightSdcDoorPosition(int position) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_SDCR_DOORPOS, 0, position);
    }

    public int getBcmNfcCardAuthStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_NFC_CARD_AUTH_ST, 0);
    }

    public int[] getLeftAndRightTurnLampsActiveStatus() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.BCM_LRTURNLAMP_ACTIVE_ST);
    }

    public void setBcmAutoWindowsControl(int cmd, int type) {
        int[] data = {cmd, type};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.BCM_ROSE_WIN_CMD_MODE, data);
    }

    public int[] getBcmDaytimeRunningLightsOutputStatus() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.BCM_LR_DRL_OUTPUT_ST);
    }

    public int getBcmEnvironmentMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_ENVR_MODE, 0);
    }

    public int getBcmTrunkDoorHeight() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_RDM_DOOR_HEIGHT, 0);
    }

    public void setBcmFollowMeHomeCfg(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_FOLLOW_ME_HOME_TIME_ST, 0, type);
    }

    public int getBcmFollowMeHomeCfg() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_FOLLOW_ME_HOME_TIME_ST, 0);
    }

    public void setBcmCwcSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_CWC_SW, 0, enable);
    }

    public int getBcmCwcSwitchState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_CWC_SW, 0);
    }

    public void setBcmFRCwcSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_FR_CWC_SW, 0, enable);
    }

    public int getBcmFRCwcSwitchState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_FR_CWC_SW, 0);
    }

    public int getBcmLeftSdcMoveCommand() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SDCL_MOVECMD, 0);
    }

    public int getBcmRightSdcMoveCommand() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SDCR_MOVECMD, 0);
    }

    public void setBcmShadeControllerComfortCommand(int command) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_SC_COMFORT_CMD, 0, command);
    }

    public void setBcmShadeControllerPosition(int position) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_SC_POS, 0, position);
    }

    public int getBcmShadeControllerPosition() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SC_POS, 0);
    }

    public void setBcmShadeControllerInitialization(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_SC_INITIALIZED, 0, sw);
    }

    public int getBcmShadeControllerInitializationSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SC_INITIALIZED, 0);
    }

    public int[] getBcmWiperSpeedSwitchesStatus() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.BCM_GROUP_WIPERSPDSW_ST);
    }

    public int getBcmLeftSdcSystemRunningState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SDCL_SYS_RUNING_ST, 0);
    }

    public int getBcmRightSdcSystemRunningState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SDCR_SYS_RUNING_ST, 0);
    }

    public int getBcmLeftSdcDoorPosition() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SDCL_DOORPOS, 0);
    }

    public int getBcmRightSdcDoorPosition() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SDCR_DOORPOS, 0);
    }

    public int[] getBcmRearViewMirrorsAdjustStates() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.BCM_RMAALL_STATE);
    }

    public int getBcmShadeControllerMotorStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SC_MOTOR_STATUS, 0);
    }

    public void setBcmSaberLightSw(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_LIGHT_SABER_REQ, 0, sw);
    }

    public int getBcmSaberLightSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_LIGHT_SABER_REQ, 0);
    }

    public int getBcmMaintainModeSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_MAINTAINMODE, 0);
    }

    public void setBcmMaintainModeSw(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_MAINTAINMODE, 0, sw);
    }

    public int getBcmScSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SC_SWITCH_STATUS, 0);
    }

    public int getBcmScEcuStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SC_ECU_STATUS, 0);
    }

    public int getBcmScThermalProtectSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SC_THERMAL_PROTECT_ST, 0);
    }

    public int getBcmScAntiPinchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SC_ANTI_PINCH_STATUS, 0);
    }

    public int getBcmScIceBreakMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SC_ICE_BREAKMODE, 0);
    }

    public int getBcmScLinStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SC_LIN_STATUS, 0);
    }

    @Deprecated
    public int getBcmHeightLvlConfigValue() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AS_HEIGHT_LVL, 0);
    }

    public int getBcmTargetAsHeightLvlConfigValue() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AS_TARGET_HEIGHT_LVL, 0);
    }

    public int getBcmActualAsHeightLvlConfigValue() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AS_ACTUAL_HEIGHT_LVL, 0);
    }

    @Deprecated
    public void setBcmHeightLvlConfigValue(int config) {
        this.mPropertyService.setIntProperty(VehicleProperty.AS_HEIGHT_LVL, 0, config);
    }

    public void setBcmTargetAsHeightLvlConfigValue(int config) {
        this.mPropertyService.setIntProperty(VehicleProperty.AS_TARGET_HEIGHT_LVL, 0, config);
    }

    public int getBcmSoftLvlConfigValue() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AS_SOFT_LVL, 0);
    }

    public void setBcmSoftLvlConfigValue(int config) {
        this.mPropertyService.setIntProperty(VehicleProperty.AS_SOFT_LVL, 0, config);
    }

    public int getBcmHandleAutoState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_HANDLE_AUTO, 0);
    }

    public void setBcmHandleAutoSwitch(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_HANDLE_AUTO, 0, onOff);
    }

    public int getBcmEasyLoadingState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AS_EASY_LOADING, 0);
    }

    public void setBcmEasyLoadingSwitch(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.AS_EASY_LOADING, 0, onOff);
    }

    public int getBcmSuspenWelcomeSwitchState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_AS_WELCOME_SW, 0);
    }

    public void setBcmSuspenWelcomeSwitch(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_AS_WELCOME_SW, 0, onOff);
    }

    public int[] getBcmLRMirrorHeatSwitchStatus() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.BCM_MIRRORHEAT_FB);
    }

    public int[] getBcmLeftRightRearMirrorFoldOutputStatus() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.BCM_LRMIRROR_OUTPUT_ST);
    }

    public void setBcmFrontMirrorHeatSwitchStatus(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_FRONT_MIRRORHEAT, 0, onOff);
    }

    public int getBcmFrontMirrorHeatSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_FRONT_MIRRORHEAT, 0);
    }

    public void setBcmRearWiperServiceSwitchStatus(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_RWIPERSERVICE_SW, 0, onOff);
    }

    public int getBcmRearWiperServiceSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_RWIPERSERVICE_SW, 0);
    }

    public void setBcmSteeringWheelHeatingStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_SWS_HEAT, 0, status);
    }

    public int getBcmSteeringWheelHeatingStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SWS_HEAT, 0);
    }

    public void setBcmLeftChildLockSwitchStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_LCHILDLOCK, 0, status);
    }

    public int getBcmLeftChildLockSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_LCHILDLOCK, 0);
    }

    public void setBcmRightChildLockSwitchStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_RCHILDLOCK, 0, status);
    }

    public int getBcmRightChildLockSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_RCHILDLOCK, 0);
    }

    public void setBcmLockHazardLightSwitchStatus(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_LOCK_HAZARD_LIGHT, 0, onOff);
    }

    public int getBcmLockHazardLightSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_LOCK_HAZARD_LIGHT, 0);
    }

    public void setBcmLockHornSwitchStatus(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_LOCK_HORN, 0, onOff);
    }

    public int getBcmLockHornSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_LOCK_HORN, 0);
    }

    public void setBcmLockAvasSwitchStatus(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_LOCK_AVAS, 0, onOff);
    }

    public int getBcmLockAvasSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_LOCK_AVAS, 0);
    }

    public void setBcmDomeLightModeStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_DOMELIGHT_MODE, 0, status);
    }

    public int getBcmDomeLightModeStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_DOMELIGHT_MODE, 0);
    }

    public void setBcmDomeLightBrightLevel(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_DOMELIGHT_BRIGHT, 0, level);
    }

    public int getBcmDomeLightBrightLevel() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_DOMELIGHT_BRIGHT, 0);
    }

    public void setBcmFrontLeftDomeLightSwitchStatus(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_FLDOMELIGHT_SW, 0, onOff);
    }

    public int getBcmFrontLeftDomeLightSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_FLDOMELIGHT_SW, 0);
    }

    public void setBcmFrontRightDomeLightSwitchStatus(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_FRDOMELIGHT_SW, 0, onOff);
    }

    public int getBcmFrontRightDomeLightSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_FRDOMELIGHT_SW, 0);
    }

    public void setBcmRearLeftDomeLightSwitchStatus(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_RLDOMELIGHT_SW, 0, onOff);
    }

    public int getBcmRearLeftDomeLightSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_RLDOMELIGHT_SW, 0);
    }

    public void setBcmRearRightDomeLightSwitchStatus(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_RRDOMELIGHT_SW, 0, onOff);
    }

    public int getBcmRearRightDomeLightSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_RRDOMELIGHT_SW, 0);
    }

    public void setTrdLeftDomeLightSwitchStatus(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_TLDOMELIGHT_SW, 0, onOff);
    }

    public int getTrdLeftDomeLightSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_TLDOMELIGHT_SW, 0);
    }

    public void setTrdRightDomeLightSwitchStatus(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_TRDOMELIGHT_SW, 0, onOff);
    }

    public int getTrdRightDomeLightSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_TRDOMELIGHT_SW, 0);
    }

    public void setBcmFootKickSwitchStatus(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_FKS_CFG, 0, onOff);
    }

    public int getBcmFootKickSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_FKS_CFG, 0);
    }

    public void setBcmWashCarModeSwitchStatus(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_WASH_CAR_MODE, 0, onOff);
    }

    public void setBcmHeadLampLevelingReqValue(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_HEAD_LAMP_LVL_REQ, 0, level);
    }

    public int getBcmHeadLampLevelingReqValue() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_HEAD_LAMP_LVL_REQ, 0);
    }

    public int getBcmHeadLampLevelingCtrlMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_HEAD_LAMP_LVL_MODE, 0);
    }

    public int getBcmHeadLampCtrlLevel() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_HEAD_LAMP_LVL_ST, 0);
    }

    public void setBcmTrunkOpenRequestPosition(int position) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_TRUNKPOS_OPEN_REQ, 0, position);
    }

    public void setBcmTrunkSetPositionRequest(int position) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_TRUNK_SETPOS_REQ, 0, position);
    }

    public int getBcmTrunkSetPositionResponcePosition() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_TRUNK_SETPOS_REQ, 0);
    }

    public int getBcmTemporaryStopLockActivateStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_TEMPORARY_LOCK_ST, 0);
    }

    public void setBcmTrailerHitchSwitchStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.TTM_SW, 0, status);
    }

    public int getBcmTrailerHitchSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TTM_SW, 0);
    }

    public void setBcmPassengerSeatBlowLevel(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_PSNSEAT_VENTILATION, 0, level);
    }

    public int getBcmPassengerSeatBlowLevel() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_PSNSEAT_VENTILATION, 0);
    }

    public void setBcmEngineeringModeStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_ENGINEERINGMODE, 0, status);
    }

    public int getBcmEngineeringModeStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_ENGINEERINGMODE, 0);
    }

    public void setBcmTransportModeSwitchStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_TRANSPORT_MODE_SW, 0, status);
    }

    public void setBcmAsTrailerModeSwitchStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.AS_TRAILER_MODE_SW, 0, status);
    }

    public void setBcmColumnVerticalMove(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.BCM_COLUMN_VERTICAL_CMD, values);
    }

    public void setBcmColumnHorizonalMove(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.BCM_COLUMN_HORIZONAL_CMD, values);
    }

    @Deprecated
    public void setBcmColumnVerticalPosition(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_COLUMN_VERTICAL_POS, 0, pos);
    }

    public int getBcmColumnVerticalPosition() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_COLUMN_VERTICAL_POS, 0);
    }

    @Deprecated
    public void setBcmColumnHorizonalPosition(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_COLUMN_HORIZONAL_POS, 0, pos);
    }

    public int getBcmColumnHorizonalPosition() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_COLUMN_HORIZONAL_POS, 0);
    }

    public int[] getBcmWindowsInitSignalLostRequestStatus() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.BCM_WIN_INI_LOST_ST);
    }

    public int getBcmTrunkOpennerStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_TRUNK_OPENNER_ST, 0);
    }

    public void setBcmColumnPositionMove(int vertpos, int horpos) {
        int[] values = {vertpos, horpos};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.BCM_COLUMN_POS_CMD, values);
    }

    public void setBcmSwsControlSceneStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.SWS_CTRL_SCENE_ST, 0, status);
    }

    public int getBcmRearWiperMotorStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_WIPER_MOTOR_ST, 0);
    }

    public void setBcmCustomerModeFlagSwitchStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.AS_CUSTOMERMODE_SW, 0, status);
    }

    public int getBcmAsAutoLevelingResult() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AS_AUTO_LEVELING_ST, 0);
    }

    public int getBcmTtmDenormalizeStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TTM_DENORMALIZE_ST, 0);
    }

    public int getBcmTtmSystemErrorStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TTM_SYS_ERR, 0);
    }

    public void setBcmAsCampingModeSwitchStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.AS_CAMPING_MODE_SW, 0, status);
    }

    public int getBcmAsCampingModeSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AS_CAMPING_MODE_SW, 0);
    }

    public int getBcmHoistModeSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AS_HOIST_MODE, 0);
    }

    public int getBcmAsYellowLampRequest() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AS_YELLOW_LAMP_REQ, 0);
    }

    public int getBcmAsRedLampRequest() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AS_RED_LAMP_REQ, 0);
    }

    public void setBcmColumnPositionSaveToMcu(int vertpos, int horpos) {
        int[] values = {vertpos, horpos};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.BCM_COLUMN_POS_SAVE, values);
    }

    @Deprecated
    public void setBcmTrailerModeSwitchStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_TRAILER_MODE_ST, 0, status);
    }

    public void setBcmTrailerModeStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_TRAILER_MODE_ST, 0, status);
    }

    public int getBcmAsLockModeStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AS_LOCK_MODE_ST, 0);
    }

    public void setBcmAsDrivingMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.AS_DRIVE_MODE, 0, mode);
    }

    public int getBcmAsDrivingMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AS_DRIVE_MODE, 0);
    }

    public void setBcmAsSpecialDrivingMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.AS_SPEC_DRIVE_MODE, 0, mode);
    }

    public int getBcmAsWelcomeModeStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AS_WELCOME_ST, 0);
    }

    public int getBcmAsEspPataRequestStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AS_ESP_PATA_REQ, 0);
    }

    @Deprecated
    public int getBcmAsTargetHeight() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AS_TAR_LVL, 0);
    }

    public int getBcmAsHeightChangingStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AS_LVL_CHG, 0);
    }

    public int getBcmAsModeAllowedCampingStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AS_MODALLWD_CAMPING, 0);
    }

    public int getBcmFWiperMotorErr() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_FWIPER_MOTOR_ERR, 0);
    }

    public void setBcmSdcBrakeCloseDoorCfg(int cfg) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_SDC_BRAKE_CLOSE_DOOR, 0, cfg);
    }

    public int getBcmSdcBrakeCloseDoorCfg() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SDC_BRAKE_CLOSE_DOOR, 0);
    }

    public int getBcmCoverPlateStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_CAP_ST, 0);
    }

    public void setBcmGroupLedControlStatus(int upStatus, int downStatus, int leftStatus, int rightStatus) {
        int[] values = {upStatus, downStatus, leftStatus, rightStatus};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.BCM_LED_GROUP_CTRL, values);
    }

    public int[] getBcmGroupLedControlStatus() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.BCM_LED_GROUP_CTRL);
    }

    public void setBcmGroupLedColor(int upColor, int downColor, int leftColor, int rightColor) {
        int[] values = {upColor, downColor, leftColor, rightColor};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.BCM_GROUP_COLOR, values);
    }

    public int[] getBcmGroupLedColor() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.BCM_GROUP_COLOR);
    }

    public void setBcmGroupLedFadeTime(int upTime, int downTime, int leftTime, int rightTime) {
        int[] values = {upTime, downTime, leftTime, rightTime};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.BCM_GROUP_FADETIME, values);
    }

    public void setBcmGroupLedTemperature(int upTemp, int downTemp, int leftTemp, int rightTemp) {
        int[] values = {upTemp, downTemp, leftTemp, rightTemp};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.BCM_GROUP_TEMP, values);
    }

    public int[] getBcmGroupLedTemperature() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.BCM_GROUP_TEMP);
    }

    public void setBcmGroupLedBrigntness(int upLux, int downLux, int leftLux, int rightLux) {
        int[] values = {upLux, downLux, leftLux, rightLux};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.BCM_GROUP_LUX, values);
    }

    public int[] getBcmGroupLedBrigntness() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.BCM_GROUP_LUX);
    }

    public int[] getBcmXPortAsSystemAllStatus() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.AS_SYS_ST1);
    }

    public int[] getBcmAsWheelPositionHeightAll() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.AS_WHEEL_POS_FILT_MM_ALL);
    }

    public float[] getBcmAsAcceleratedSpeed() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.AS_ACCEL_METER_ALL);
    }

    public void setBcmAsLeopardModeSwitchStatus(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.AS_LEOPARD_MOD_SW, 0, sw);
    }

    public int getBcmAsLeopardModeSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AS_LEOPARD_MOD_SW, 0);
    }

    @Deprecated
    public void setBcmAsVehicleMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.AS_VEHICLE_MODE, 0, mode);
    }

    public int getBcmTrunkActualPosition() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_TRUNK_POS_FEEDBACK, 0);
    }

    public void setBcmSecRowSeatEasyEntrySwitchStatus(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.SECROW_EASY_ENTRY_SW, 0, sw);
    }

    public void setBcmMirrorAutoFoldSwitchStatus(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_MIRROR_AUTOFOLD_SW, 0, sw);
    }

    public int getBcmMirrorAutoFoldSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_MIRROR_AUTOFOLD_SW, 0);
    }

    public int getBcmAsAutoLevelingResultValue() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AS_AUTOLEVEL_RESULT, 0);
    }

    public void setBcmXsleepModeStatus(int active) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_XSLEEP_MODE_ST, 0, active);
    }

    public void setBcmXmovieModeStatus(int active) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_XMOVIE_MODE_ST, 0, active);
    }

    public void setBcmX5dCinemaModeStatus(int active) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_X5_DCINEMA_MODE_ST, 0, active);
    }

    public void setBcmXmeditationModeStatus(int active) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_XMEDITATION_MODE_ST, 0, active);
    }

    public int getBcmFrontWiperActiveStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_FWIPER_ACTIVE_ST, 0);
    }

    public int[] getBcmAllWindowsActionFeedbackStatus() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.BCM_WIN_ACTION_FB_ALL);
    }

    public void setLeftSlideDoorMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_LSLIDEDOOR_MOODE, 0, mode);
    }

    public int getLeftSlideDoorMoode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_LSLIDEDOOR_MOODE, 0);
    }

    public int getLeftSlideDoorStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_LSLIDEDOOR_CTRL, 0);
    }

    public void setLeftSlideDoorCtrl(int Ctrl) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_LSLIDEDOOR_CTRL, 0, Ctrl);
    }

    public int getLeftSlideDoorLockSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_LSLIDEDOOR_LOCK, 0);
    }

    public void setRightSlideDoorMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_RSLIDEDOOR_MOODE, 0, mode);
    }

    public int getRightSlideDoorMoode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_RSLIDEDOOR_MOODE, 0);
    }

    public int getRightSlideDoorStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_RSLIDEDOOR_CTRL, 0);
    }

    public void setRightSlideDoorCtrl(int Ctrl) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_RSLIDEDOOR_CTRL, 0, Ctrl);
    }

    public int getRightSlideDoorLockSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_RSLIDEDOOR_LOCK, 0);
    }

    public int getRearLogLight() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_REARLOGOLIGHT_SW, 0);
    }

    public void setRearLogLight(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_REARLOGOLIGHT_SW, 0, onOff);
    }

    public int getBcmTrunkWorkModeStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_WORK_MODE_ST, 0);
    }

    public void setCFPowerSwitch(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.CF_POWER_ON, 0, onOff);
    }

    public int getCFPowerState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CF_POWER_ON, 0);
    }

    public void setCarFridgeDoorCtrl(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.CF_DOOR_CTRL, 0, onOff);
    }

    public int getCarFridgeDoorState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CF_DOOR_CTRL, 0);
    }

    public void setCFTempInc() {
        this.mPropertyService.setIntProperty(VehicleProperty.CF_TEMP_UP, 0, 1);
    }

    public void setFCTempDec() {
        this.mPropertyService.setIntProperty(VehicleProperty.CF_TEMP_DOWN, 0, 1);
    }

    public void setCFTempValue(int value) {
        this.mPropertyService.setIntProperty(VehicleProperty.CF_TEMPERATURE_SET, 0, value);
    }

    public int getCFTempValue() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CF_TEMPERATURE_SET, 0);
    }

    public void setCFWorkMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.CF_WORK_MODE, 0, mode);
    }

    public int getCFWorkMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CF_WORK_MODE, 0);
    }

    public void setCFChildLock(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.CF_CHILD_LOCK, 0, onOff);
    }

    public int getCFChildLockState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CF_CHILD_LOCK, 0);
    }

    public void setCFKeepTempSwitch(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.CF_KEEPTEMP_REQ, 0, onOff);
    }

    public int getCFKeepTempState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CF_KEEPTEMP_REQ, 0);
    }

    public void setCFKeepTempTime(int value) {
        this.mPropertyService.setIntProperty(VehicleProperty.CF_KEEPTEMP_APPOINT, 0, value);
    }

    public int getCFKeepTempTime() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CF_KEEPTEMP_APPOINT, 0);
    }

    public int getCFKeepTempRemainTime() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CF_KEEPTEMP_REMAIN_TIME, 0);
    }

    public void setCFKeepTempTimeMemoryRequest(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.CF_KEEPTEMP_MEMORY_REQ, 0, onOff);
    }

    public int getCFKeepTempWorkState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CF_KEEPTEMP_WORK_ST, 0);
    }

    public void setCarpetLightWelcomeSw(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_CARPETLIGHT_WELCOM, 0, onOff);
    }

    public int getCarpetLightWelcomeState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_CARPETLIGHT_WELCOM, 0);
    }

    public void setPollingWelcomeSW(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_POLLING_WELCOME, 0, onOff);
    }

    public int getPollingWelcomeState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_POLLING_WELCOME, 0);
    }

    public void setSfmCtrl(int Ctrl) {
        this.mPropertyService.setIntProperty(VehicleProperty.SFM_CTRL, 0, Ctrl);
    }

    public int getSfmCtrlState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SFM_CTRL, 0);
    }

    public void setSfmAnglePos(int angel) {
        this.mPropertyService.setIntProperty(VehicleProperty.SFM_ANGLE_POS, 0, angel);
    }

    public int getSfmAnglePos() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SFM_ANGLE_POS, 0);
    }

    public void setSecRowLeftBlowLevel(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_SECROW_LTSEAT_VENT_LEVEL, 0, level);
    }

    public int getSecRowLeftBlowLevel() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SECROW_LTSEAT_VENT_LEVEL, 0);
    }

    public void setSecRowRightBlowLevel(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_SECROW_RTSEAT_VENT_LEVEL, 0, level);
    }

    public int getSecRowRightBlowLevel() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SECROW_RTSEAT_VENT_LEVEL, 0);
    }

    public void setImsModeReq(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.IMS_MODE, 0, mode);
    }

    public int getImsModeState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.IMS_MODE, 0);
    }

    public void setImsAutoVisionSw(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.IMS_AUTO_VISION_SW, 0, onOff);
    }

    public int getImsAutoVisionSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.IMS_AUTO_VISION_SW, 0);
    }

    public void setImsBrightLevel(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.IMS_BRIGHT_LEVEL, 0, level);
    }

    public int getImsBrightLevel() {
        return this.mPropertyService.getIntProperty(VehicleProperty.IMS_BRIGHT_LEVEL, 0);
    }

    public void setImsVisionCtrl(int control, int direction) {
        int[] data = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.IMS_VISION_CTRL, data);
    }

    public int getImsVisionVerticalLevel() {
        return this.mPropertyService.getIntProperty(VehicleProperty.IMS_VISION_VERTICAL_LEVEL, 0);
    }

    public int getImsVisionAngleLevl() {
        return this.mPropertyService.getIntProperty(VehicleProperty.IMS_VISION_ANGLE_LEVEL, 0);
    }

    public int getImsSystemSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.IMS_SYSTEM_ST, 0);
    }

    public void setBcmRLCwcSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_RL_CWC_SW, 0, enable);
    }

    public int getBcmRLCwcSwitchState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_RL_CWC_SW, 0);
    }

    public int getRLCwcChargeSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_RL_CWCCHRG_ST, 0);
    }

    public int getRLCwcChargeErrorSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_RL_CWCCHRG_ABNORMAL_WARNING, 0);
    }

    public void setBcmRRCwcSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_RR_CWC_SW, 0, enable);
    }

    public int getBcmRRCwcSwitchState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_RR_CWC_SW, 0);
    }

    public int getRRCwcChargeSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_RR_CWCCHRG_ST, 0);
    }

    public int getRRCwcChargeErrorSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_RR_CWCCHRG_ABNORMAL_WARNING, 0);
    }

    public void setLCMSAutoBrightSw(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.LCMS_AUTO_BRIGHT_SW, 0, sw);
    }

    public int getLCMSAutoBrightSwSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.LCMS_AUTO_BRIGHT_SW, 0);
    }

    @Deprecated
    public void setLCMSBright(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.LCMS_BRIGHT_SET, 0, level);
    }

    @Deprecated
    public int getLCMSBright() {
        return this.mPropertyService.getIntProperty(VehicleProperty.LCMS_BRIGHT_SET, 0);
    }

    @Deprecated
    public int getLRCMSStoreBrightSource() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CMS_STORE_BRIGHT_SOURCE, 0);
    }

    public void setLCMSBrightWithStoreflag(int[] data) {
        this.mPropertyService.setIntVectorProperty(VehicleProperty.CMS_BRIGHT_SET_WITH_FLAG, data);
    }

    public int[] getLCMSBrightWithSource() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.CMS_BRIGHT_SET_WITH_FLAG);
    }

    public void setLCMSHighSpeedViewSw(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.LCMS_HIGHSPEED_ZOOM_SW, 0, sw);
    }

    public int getLCMSHighSpeedViewSwSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.LCMS_HIGHSPEED_ZOOM_SW, 0);
    }

    public void setLCMSLowSpeedViewSw(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.LCMS_LOWSPEED_ZOOM_SW, 0, sw);
    }

    public int getLCMSLowSpeedViewSwSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.LCMS_LOWSPEED_ZOOM_SW, 0);
    }

    public void setLCMSDanObjectRecSw(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.LCMS_OBJECT_RECOGNIZE_SW, 0, sw);
    }

    public int getLCMSDanObjectRecSwSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.LCMS_OBJECT_RECOGNIZE_SW, 0);
    }

    public void setLCMSReverseAssitSw(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.LCMS_REVERSE_ASSIST_SW, 0, sw);
    }

    public int getLCMSReverseAssitSwSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.LCMS_REVERSE_ASSIST_SW, 0);
    }

    public void setLCMSTurnExtSw(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.LCMS_TURN_EXTN_SW, 0, sw);
    }

    public int getLCMSTurnExtSwSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.LCMS_TURN_EXTN_SW, 0);
    }

    public void setLCMSViewRecovery(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.LCMS_VIEW_RECOVERY, 0, enable);
    }

    public int getLCMSViewRecoverySt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.LCMS_VIEW_RECOVERY, 0);
    }

    public void sendLCMSLogCtrlReq(int req) {
        this.mPropertyService.setIntProperty(VehicleProperty.LCMS_LOG_CTRL, 0, req);
    }

    public void sendRCMSLogCtrlReq(int req) {
        this.mPropertyService.setIntProperty(VehicleProperty.RCMS_LOG_CTRL, 0, req);
    }

    public void setLRCMSViewAngle(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.CMS_VISION_ANGLE_CTRL, 0, type);
    }

    public int getLRCMSViewAngle() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CMS_VISION_ANGLE_CTRL, 0);
    }

    public void setArsWorkingMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.ARS_MODE, 0, mode);
    }

    public int getArsWorkingMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ARS_MODE, 0);
    }

    public void setArsFoldOrUnfold(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.ARS_CTRL, 0, type);
    }

    public int getArsWorkingState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ARS_STATE, 0);
    }

    public int getArsPosition() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ARS_POSITION, 0);
    }

    public int getArsInitState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ARS_INIT_ST, 0);
    }

    public void setArsInitState(int st) {
        this.mPropertyService.setIntProperty(VehicleProperty.ARS_INIT_ST, 0, st);
    }

    public int getArsFaultState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ARS_FAULT_ST, 0);
    }

    public int getBcmTtmLampConnectStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TTM_LAMP_CONNECT_ST, 0);
    }

    public int getBcmTtmLampFaultStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TTM_LAMP_FAULT_ST, 0);
    }

    public int getBcmTtmHookMotorStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TTM_HOOK_MOTOR_ST, 0);
    }

    public int getBcmLowBeamOffConfirmSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_LOW_BEAM_OFF_CONFIRM, 0);
    }

    public void setBcmLowBeamOffConfirmSt(int st) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_LOW_BEAM_OFF_CONFIRM, 0, st);
    }

    public void startCharge() {
        this.mPropertyService.setIntProperty(VehicleProperty.TBOX_CHARGER_REQ, 0, 1);
    }

    public void stopCharge() {
        this.mPropertyService.setIntProperty(VehicleProperty.TBOX_CHARGER_REQ, 0, 0);
    }

    public void startVcuCharge(int chargeSoc) {
    }

    public void stopVcuAcCharge(int chargeSoc) {
    }

    public void stopVcuDcCharge() {
    }

    public void setVcuBestCharge() {
    }

    public void setVcuFullyCharge() {
    }

    public void setVcuChargeLimit(int limit) {
    }

    public int getVcuChargingPlugStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_CHARGE_GUN_STATUS, 0);
    }

    public int getVcuChargeStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_CHARGE_STATUS, 0);
    }

    public int getVcuElectricQuantityPercent() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_BMS_SOCDISP, 0);
    }

    public int getVcuBatteryCoolingState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_BATTCOOLING_DSP, 0);
    }

    public float getVcuAcPowerConsume() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_HVAC_CONSUME, 0);
    }

    public int getVcuBatteryWarmingStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_BATWARM_STDISP, 0);
    }

    public int getVcuChargeRemainingTime() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_CHARGE_COMPLETE_TIME, 0);
    }

    public float getVcuAvgVehiclePowerConsume() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_VEHELC_CONSP_AVG, 0);
    }

    public int getVcuChargeMode() {
        return 0;
    }

    public void setVcuChargeMode(int mode) {
    }

    public float getVcuBatteryWastageStatus() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_SOH, 0);
    }

    public float getVcuBatteryMinTemperature() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_BATTERY_MIN_TEMPERATURE, 0);
    }

    public int getVcuDrivingMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_DRIVE_MODE, 0);
    }

    public int getVcuEnergyRecycleLevel() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_ENERGY_RECOVERY, 0);
    }

    public void setVcuEnergyRecycleLevel(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_ENERGY_RECOVERY, 0, level);
    }

    public int getVcuGearState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_CURRENT_GEARLEV, 0);
    }

    public int getVcuRealGearLevel() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_GEARLEV, 0);
    }

    public int getVcuAvalibleDrivingDistance() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_DSTBAT_DISP, 0);
    }

    public float getVcuNedcAvalibleDrivingDistanceFloat() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_DSTBAT_DISP_NEDC_FLOAT, 0);
    }

    public int getVcuResHeatManaTime() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_RESHEAT_MANATIME, 0);
    }

    public float getVcuAcChargeVolt() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_OBC_DCVOLT, 0);
    }

    public float getVcuAcChargeCur() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_OBC_DCCUR, 0);
    }

    public float getVcuDcChargeCur() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_DCCCS_CURR, 0);
    }

    public float getVcuDcChargeVolt() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_DCCCS_SUMU, 0);
    }

    public void setVcuDisChargeLimit(int percent) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_V2L_POWERLIMIT, 0, percent);
    }

    public int getVcuDisCargeLimit() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_V2L_POWERLIMIT, 0);
    }

    public void setVcuDisChargeEnabled(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_BAT_CHRG, 0, status);
    }

    public int getVcuDischargeQuantity() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_DISCHARGE_BATTCAP, 0);
    }

    public int getVcuChargeSocQuantity() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_CHARGE_BATTCAP, 0);
    }

    public int getVcuEvsysReadyState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_EVSYS_READYST, 0);
    }

    public int getVcuBatteryLevelPercent() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_BATTSOC_SATE, 0);
    }

    public float getVcuBatteryVolt() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_EBSBATT_VOLT, 0);
    }

    public float getVcuBatteryCur() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_EBSBATT_CURR, 0);
    }

    public int getVcuStopChargeReason() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_DCC_CHRGSTOP_REA, 0);
    }

    public void setVcuBrakeLightOn(int on) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_BRAKE_SIG, 0, on);
    }

    public int getVcuBrakeLightOnOffStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_BRAKE_SIG, 0);
    }

    public int getVcuBreakPedalStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_BRKPEDAL_ST, 0);
    }

    public int getVcuAccPedalStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_ACCPEDAL_SIG, 0);
    }

    public int isVcuParkingGearValid() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_PGEAR_VD, 0);
    }

    public float getVcuVehLast100mConsume() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_VEHELC_CONSP_100M, 0);
    }

    public int isVcuBatteryCold() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_BATCOLD_DISP, 0);
    }

    public int isVcuChargeSpeedSlow() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_CHGSPEED_SLOW_DSP, 0);
    }

    public int getVcuSnowMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_SNOW_MODE_SW, 0);
    }

    public void setVcuSnowMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_SNOW_MODE_SW, 0, mode);
    }

    public float getVcuRawCarSpeed() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_RAW_CAR_SPEED, 0);
    }

    public int getVcuPureDriveModeFeedback() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_DRIMODE_FB, 0);
    }

    public int getVcuSupDebugInfo() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_DBG_SUP_STSUP, 0);
    }

    public int getVcuErhDebugInfo() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_DBG_ERH_STSYSERRLVL, 0);
    }

    public int getVcuEbsBatterySoc() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_EBS_BATT_SOC, 0);
    }

    public int getVcuChargeError() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_CHARGE_ERROR, 0);
    }

    public int getVcuAcInputStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_AC_INPUT_STATUS, 0);
    }

    public int getVcuEvErrLampDsp() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_EVERR_LAMP_DSP, 0);
    }

    public int getVcuEvErrMsgDsp() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_EVERR_MSG_DSP, 0);
    }

    public void setVcuExtremeFastChargingMode(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_INTELHEAT_MANA, 0, enable);
    }

    public int getVcuExtremeFastChargingSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_INTELHEAT_MANA, 0);
    }

    public void setVcuNGearWarningSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_NGEAR_WARNINGSW, 0, enable);
    }

    public int getVcuNGearWarningSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_NGEAR_WARNINGSW, 0);
    }

    public float getVcuAcChargeCurAfterVoltBoosted() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_ACCCS_CUR, 0);
    }

    public float getVcuAcChargeVoltAfterVoltBoosted() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_ACCCS_VOLT, 0);
    }

    public int getVcuCruiseControlStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_CRUISECONTROL_STDISP, 0);
    }

    public int getBmsScoIsLowStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_BMS_SOC_SLOW, 0);
    }

    public int getBmsIsErrorStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_BMS_ERR, 0);
    }

    public int getIsHvCutOffStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_HV_CUTOFF, 0);
    }

    public void setBatteryKeepTempSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_BAT_KEEP_TEMP_REQ, 0, enable);
    }

    public int getBatteryKeepTempMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_BAT_KEEP_TEMP_MODE, 0);
    }

    public void setSpeedUpChargeSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_SPD_UP_CHG_REQ, 0, enable);
    }

    public int getSpeedUpChargeMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_SPD_UP_CHG_MODE, 0);
    }

    public void setVcuDriveMileIncreaseSwitch(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_DRIVE_MILE_INCREASE, 0, sw);
    }

    public int getVcuDriveMileIncreaseStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_DRIVE_MILE_INCREASE, 0);
    }

    public int getVcuEnduranceMileageMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_RANDIS_MODE, 0);
    }

    public void setVcuEnduranceMileageMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_RANDIS_MODE, 0, mode);
    }

    public int getVcuWltpAvailableDrivingDistance() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_DSTBAT_DISP_NEW, 0);
    }

    public float getVcuWltpAvailableDrivingDistanceFloat() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_DSTBAT_DISP_WLTP_FLOAT, 0);
    }

    public int getVcuCltcAvailableDrivingDistance() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_DSTBAT_DISP_CLTC, 0);
    }

    public float getVcuCltcAvailableDrivingDistanceFloat() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_DSTBAT_DISP_CLTC_FLOAT, 0);
    }

    public float getVcuDynamicAvailableDrivingDistance() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_DSTBAT_DISP_DYNAMIC, 0);
    }

    public float getVcuObcAcVoltage() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_OBC_SIDE_ACVOLT, 0);
    }

    public float getVcuObcAcCurrent() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_OBC_SIDE_ACCUR, 0);
    }

    public int getVcuObcAcVoltageStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_OBC_SIDE_ACVOLTST, 0);
    }

    public int getVcuCarStationaryStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_CAR_STATIONARY_ST, 0);
    }

    public void setVcuSpecialDrivingMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_SPEC_DRIVE_MODE, 0, mode);
    }

    public int getVcuSpecialDrivingMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_SPEC_DRIVE_MODE, 0);
    }

    public void setVcuPowerResponseMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_POWER_RESPONCE, 0, mode);
    }

    public int getVcuPowerResponseMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_POWER_RESPONCE, 0);
    }

    public void setVcuMotorPowerMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_MOTOR_POWERMODE, 0, mode);
    }

    public int getVcuMotorPowerMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_MOTOR_POWERMODE, 0);
    }

    public void setVcuXpedalModeSwitchStatus(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_XPEDAL_MODE_SW, 0, enable);
    }

    public int getVcuXpedalModeSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_XPEDAL_MODE_SW, 0);
    }

    public int getVcuExhibModeSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_EXHIB_MODE_SW, 0);
    }

    public int getVcuObcFaultPhaseLossStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.OBC_FAULT_PHASE_LOSS, 0);
    }

    public void setVcuTrailerModeSwitchStatus(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_TRAILER_MODE_SW, 0, enable);
    }

    public int getVcuTrailerModeSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_TRAILER_MODE_SW, 0);
    }

    public void setVcuSpecialCarbinModeSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_SPECIAL_CARBIN_MODE, 0, enable);
    }

    public void setVcuSecondaryPowerOffRequest(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_2ND_OFF_RES, 0, enable);
    }

    public int getVcuSecondaryPowerOffResponce() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_2ND_OFF_RES, 0);
    }

    public void setVcuCdcuChargeGunCommand(int command) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_CHRG_GUN_CMD, 0, command);
    }

    public int getVcuCdcuChargeGunStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_CHRG_GUN_CMD, 0);
    }

    public void setVcuDcPreWarmSwitchStatus(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_DC_PRE_WARM_SW, 0, enable);
    }

    public int getVcuDcPreWarmSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_DC_PRE_WARM_SW, 0);
    }

    @Deprecated
    public void setVcuDcPreWarmRequestStatus(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_DC_PRE_WARM_REQ, 0, enable);
    }

    @Deprecated
    public int getVcuDcPreWarmRequestStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_DC_PRE_WARM_REQ, 0);
    }

    public int getVcuDcPreWarmInStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_DC_PRE_WARM_ST, 0);
    }

    public float getVcuDischargeQuantityFloat() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_DISCHARGE_BATTCAP_FLOAT, 0);
    }

    public float getVcuChargeSocQuantityFloat() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_CHARGE_BATTCAP_FLOAT, 0);
    }

    public int getVcuVirtualAccPedalStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_VIRTUAL_ACCPEDAL_SIG, 0);
    }

    @Deprecated
    public int getVcuAsDriveModeStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AS_DRV_MODE_ST, 0);
    }

    public int getVcuDepolarizeStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_DEPOLARIZE_ST, 0);
    }

    public void setVcuBatBumpRecrdStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_BAT_BUMP_RECRD, 0, status);
    }

    public int getVcuBatBumpRecrdRequest() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_BAT_BUMP_RECRD, 0);
    }

    public void setVcuSsaSwitchStatus(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_SSA_SW, 0, sw);
    }

    public int getVcuSsaSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_SSA_SW, 0);
    }

    public void setVcuPGearLimOffSwitchStatus(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_PGEAR_LIM_OFF_SW, 0, sw);
    }

    public int getVcuSuperChargeFlag() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_SUPER_CHRG_FLG, 0);
    }

    public void setVcuXpedalCtrlMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_XPEDAL_CTRL_MODE, 0, mode);
    }

    public int getVcuXpedalCtrlMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_XPEDAL_CTRL_MODE, 0);
    }

    public void setVcuXPortIntellCalcCfg(float[] cfg) {
        if (cfg == null) {
            Slog.e(TAG, "setVcuXportIntellCalcCfg cfg is null");
            throw new IllegalArgumentException("cfg cannot be null");
        } else {
            this.mPropertyService.setFloatVectorProperty(VehicleProperty.VCU_XPORT_INTELL_CALC_CFG, cfg);
        }
    }

    public float[] getVcuXPortIntellCalcCfg() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.VCU_XPORT_INTELL_CALC_FB);
    }

    @Deprecated
    public float[] getVcuXPortIntellCalcCfg20Hz() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.VCU_XPORT_INTELL_CALC_20HZ);
    }

    public float[] getVcuXPortIntellCalcCfg50Hz() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.VCU_XPORT_INTELL_CALC_50HZ);
    }

    public int getVcuKeyBatteryStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_KEY_BATT_ST, 0);
    }

    public float getVcuBmsBatteryCurrent() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_BMS_BATT_CURR, 0);
    }

    public int getVcuBmsBatteryVoltage() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_BMS_BATT_VOLT, 0);
    }

    public int getVcuBmsBatteryAverageTemperature() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_BMS_BATT_TEMP_AVG, 0);
    }

    @Deprecated
    public int getVcuBmsChargeStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_BMS_CHRG_ST, 0);
    }

    public void setVcuXsportMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_XSPORT_MODE, 0, mode);
    }

    public int getVcuXsportMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_XSPORT_MODE, 0);
    }

    public float getVcuBmsActualSocValue() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_BMS_ACT_SOC, 0);
    }

    public float getVcuBmsMaximumAvailChargePower() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_BMS_MAX_AVAIL_CHRG_PWR, 0);
    }

    public float getVcuBmsMaximumAvailDischargePower() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_BMS_MAX_AVAIL_DISCHRG_PWR, 0);
    }

    public float getVcuBmsCurrentBatterySocDisp() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_BMS_BATT_SOC_DISP, 0);
    }

    public float getVcuChargeDischargePower() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_CHRG_PWR, 0);
    }

    public float getVcuChargeHighVoltageLoadPower() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_CHRG_HV_LOAD_PWR, 0);
    }

    public int getVcuSuperChargeDiffIncreaseRange() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_CHRG_DIFF, 0);
    }

    public int getVcuChargerLowVolSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_DCCH_VOL_LOW, 0);
    }

    public void setVcuChargerLowVolDiag(int ack) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_DCCH_VOL_LOW, 0, ack);
    }

    public float getVcuLastTwoPointFiveKmAverageVehConsume() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_VEHELC_CONSP_AVG_2_5, 0);
    }

    public float getVcuLastTwentyKmAverageVehConsume() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_VEHELC_CONSP_AVG_20, 0);
    }

    public float getVcuLastHundredKmAverageVehConsume() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_VEHELC_CONSP_AVG_100, 0);
    }

    public void setVcuAutoEasyLoadingSwitchStatus(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_AUTO_EASY_LOADING_SW, 0, sw);
    }

    public int getVcuAutoEasyLoadingSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_AUTO_EASY_LOADING_SW, 0);
    }

    public void setVCUAWDModeSw(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_XSPORT_AWD_MODE_SW, 0, mode);
    }

    public int getVCUAWDModeSw() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_XSPORT_AWD_MODE_SW, 0);
    }

    public void setVcuAcChargCurrentMaxLimitedValue(int value) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_AC_CUR_MAX_CHG_CON, 0, value);
    }

    public int getVcuAcChargCurrentMaxLimitedValue() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_AC_CUR_MAX_CHG_CON, 0);
    }

    public void setVMCRwsSwitch(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_VMC_RWS_SW, 0, sw);
    }

    public int getVMCRwsSwitchState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_VMC_RWS_SW, 0);
    }

    public void setVMCZWalkModeSwitch(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_VMC_ZWALK_SW, 0, sw);
    }

    public int getVMCZWalkModeState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_VMC_ZWALK_SW, 0);
    }

    public int getVMCSystemState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_VMC_SYSTEM_ST, 0);
    }

    public int getV2LDischargeErrorReason() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_DISCHARGE_ERROR_REASON, 0);
    }

    public void setVcuNaviDestInfo(String info) {
        this.mPropertyService.setStringProperty(VehicleProperty.VCU_NAVI_DEST_INFO, info);
    }

    public void setVcuNaviDestType(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_NAVI_DEST_TYPE, 0, type);
    }

    public void setVcuNaviRemainDistance(int distance) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_NAVI_REMAIN_DISTANCE, 0, distance);
    }

    public void setVcuNaviRemainTime(int minutes) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_NAVI_REMAIN_TIME, 0, minutes);
    }

    public void setVcuNaviPathId(int id) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_NAVI_PATH_ID, 0, id);
    }

    public void setVcuNaviType(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_NAVI_TYPE, 0, type);
    }

    public void setVcuNaviKValue(float k) {
        this.mPropertyService.setFloatProperty(VehicleProperty.VCU_NAVI_K_VALUE, 0, k);
    }

    public float getVcuAvalibleDrivingDistanceFloat() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.VCU_DSTBAT_DISP_FLOAT, 0);
    }

    public void setHvacPowerEnabled(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_POWER_ON_XP, 0, enable);
    }

    public int getHvacPowerState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_POWER_ON_XP, 0);
    }

    public void setHvacAcEnabled(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_AC_ON_XP, 0, enable);
    }

    public int getHvacAcState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_AC_ON_XP, 0);
    }

    public void setHvacDrvSeatTempUp(float value) {
        setHvacDrvSeatTempValue(getHvacDrvSeatTempValue() + value);
    }

    public void setHvacDrvSeatTempDown(float value) {
        setHvacDrvSeatTempValue(getHvacDrvSeatTempValue() - value);
    }

    public float getHvacDrvSeatTempValue() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.HVAC_TEMPERATURE_SET, 0);
    }

    public void setHvacDrvSeatTempValue(float level) {
        this.mPropertyService.setFloatProperty(VehicleProperty.HVAC_TEMPERATURE_SET, 0, level);
    }

    public void setHvacPsnSeatTempUp(float value) {
        setHvacPsnSeatTempValue(getHvacPsnSeatTempValue() + value);
    }

    public void setHvacPsnSeatTempDown(float value) {
        setHvacPsnSeatTempValue(getHvacPsnSeatTempValue() - value);
    }

    public float getHvacPsnSeatTempValue() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.HVAC_TEMPERATURE_PSN_SET, 0);
    }

    public void setHvacPsnSeatTempValue(float level) {
        this.mPropertyService.setFloatProperty(VehicleProperty.HVAC_TEMPERATURE_PSN_SET, 0, level);
    }

    public void setHvacAutoModeEnabled(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_AUTO_ON_XP, 0, enable);
    }

    public int getHvacAutoModeState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_AUTO_ON_XP, 0);
    }

    public int getHvacAutoModePreference() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_BLW_COMFORT_CFG, 0);
    }

    public void setHvacAutoModePreference(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_BLW_COMFORT_CFG, 0, level);
    }

    public int getHvacAirCycleMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_RECIRC_ON, 0);
    }

    public void setHvacAirCycleMode(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_RECIRC_ON, 0, type);
    }

    public int getHvacDefrostMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_DEFROSTER_XP, 0);
    }

    public void setHvacDefrostMode(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_DEFROSTER_XP, 0, enable);
    }

    public int getHvacFanPosition() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_FAN_DIRECTION, 0);
    }

    public void setHvacFanPosition(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_FAN_DIRECTION, 0, mode);
    }

    public void setHvacFanSpeedUp(int value) {
        setHvacFanSpeedLevel(getHvacFanSpeedLevel() + value);
    }

    public void setHvacFanSpeedDown(int value) {
        setHvacFanSpeedLevel(getHvacFanSpeedLevel() - value);
    }

    public int getHvacFanSpeedLevel() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_FAN_SPEED, 0);
    }

    public void setHvacFanSpeedLevel(int level) {
        Slog.i(TAG, "setHvacFanSpeedLevel: " + level);
        if (level < 1 || level > this.mHvacMaxFanSpeedLevel) {
            Slog.w(TAG, "Invalid level");
        } else {
            this.mPropertyService.setIntProperty(VehicleProperty.HVAC_FAN_SPEED, 0, level);
        }
    }

    public void setHvacAirPurgeEnabed(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_PM25_AIR_PURGE, 0, enable);
    }

    public int getHvacAirPurgeSwitchState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_PM25_AIR_PURGE, 0);
    }

    public int getHvacOutsideAirQualityStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_AIR_QUALITY_STATUS, 0);
    }

    public int getHvacOutsideAirQualityLevel() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_AIR_QUALITY_LEVEL, 0);
    }

    public float getHvacInnerTemp() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.HVAC_INNER_TEMPERATURE, 0);
    }

    public void setHvacFanSpeedInc() {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_FAN_SPEED_UP, 0, 1);
    }

    public void setHvacFanSpeedDec() {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_FAN_SPEED_DOWN, 0, 1);
    }

    public void setHvacDrvSeatTempInc() {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_DRIVERTEMP_UP, 0, 1);
    }

    public void setHvacDrvSeatTempDec() {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_DRIVERTEMP_DOWN, 0, 1);
    }

    public void setHvacPsnSeatTempInc() {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_TEMPERATURE_PSN_UP, 0, 1);
    }

    public void setHvacPsnSeatTempDec() {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_TEMPERATURE_PSN_DOWN, 0, 1);
    }

    public void setHvacTempLeftSyncEnabled(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_LEFT_SYNC, 0, enable);
    }

    public int getHvacTempLeftSyncState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_LEFT_SYNC, 0);
    }

    public void setHvacTempRightSyncEnabled(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_RIGHT_SYNC, 0, enable);
    }

    public int getHvacTempRightSyncState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_RIGHT_SYNC, 0);
    }

    public int getHvacDrvLeftFanHorPos() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_DRV_LEFTHORZ, 0);
    }

    public void setHvacDrvLeftFanHorPos(int position) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_DRV_LEFTHORZ, 0, position);
    }

    public int getHvacDrvLeftFanVerPos() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_DRV_LEFTVERT, 0);
    }

    public void setHvacDrvLeftFanVerPos(int position) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_DRV_LEFTVERT, 0, position);
    }

    public int getHvacDrvRightFanHorPos() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_DRV_RIGHTHORZ, 0);
    }

    public void setHvacDrvRightFanHorPos(int position) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_DRV_RIGHTHORZ, 0, position);
    }

    public int getHvacDrvRightFanVerPos() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_DRV_RIGHTVERT, 0);
    }

    public void setHvacDrvRightFanVerPos(int position) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_DRV_RIGHTVERT, 0, position);
    }

    public int getHvacPsnLeftFanHorPos() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_PSN_LEFTHORZ, 0);
    }

    public void setHvacPsnLeftFanHorPos(int position) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_PSN_LEFTHORZ, 0, position);
    }

    public int getHvacPsnLeftFanVerPos() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_PSN_LEFTVERT, 0);
    }

    public void setHvacPsnLeftFanVerPos(int position) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_PSN_LEFTVERT, 0, position);
    }

    public int getHvacPsnRightFanHorPos() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_PSN_RIGHTHORZ, 0);
    }

    public void setHvacPsnRightFanHorPos(int position) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_PSN_RIGHTHORZ, 0, position);
    }

    public int getHvacPsnRightFanVerPos() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_PSN_RIGHTVERT, 0);
    }

    public void setHvacPsnRightFanVerPos(int position) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_PSN_RIGHTVERT, 0, position);
    }

    public void setHvacAqsEnabled(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_AQS, 0, enable);
    }

    public int getHvacAqsSwitchState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_AQS, 0);
    }

    public int getHvacAqsSensitivity() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_AQSSENSITIVITY, 0);
    }

    public void setHvacAqsSensitivity(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_AQSSENSITIVITY, 0, level);
    }

    public int getHvacSweepWindStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_EAVSWEEP_WIND, 0);
    }

    public void setHvacSweepWindStatus(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_EAVSWEEP_WIND, 0, enable);
    }

    public void setHvacEconEnabled(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_ECON, 0, enable);
    }

    public int getHvacEconState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_ECON, 0);
    }

    public int getHvacEavDrvWindMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_DRV_WINDMODE, 0);
    }

    public void setHvacEavDrvWindMode(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_DRV_WINDMODE, 0, type);
    }

    public int getHvacEavPsnWindMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_PSN_WINDMODE, 0);
    }

    public void setHvacTempPtcStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_PTC_ST, 0, status);
    }

    public void setHvacEavPsnWindMode(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_PSN_WINDMODE, 0, type);
    }

    public int getHvacAirCirculationPeriod() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_CIRCULATION_PERIOD_SET, 0);
    }

    public void setHvacAirCirculationPeriod(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_CIRCULATION_PERIOD_SET, 0, type);
    }

    public int getHvacTempColor() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_WINDMODE_COLOUR, 0);
    }

    public void setHvacAirDistributionMode(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_BLOWER_MODE, 0, type);
    }

    public float getHvacExternalTemp() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.ENV_OUTSIDE_TEMPERATURE, 0);
    }

    public int getHvacPm25Value() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_PM25, 0);
    }

    public void setSocCoolingRequestTemp(float temp) {
        this.mPropertyService.setFloatProperty(VehicleProperty.HVAC_820A_COOLING_REQTEMP, 0, temp);
    }

    public void setAmpCoolingRequestTemp(float temp) {
        this.mPropertyService.setFloatProperty(VehicleProperty.HVAC_AMP_COOLING_REQTEMP, 0, temp);
    }

    public void setAmpTempRiseSpeedState(int temp) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_AMP_TEMPRISE_SPEED, 0, temp);
    }

    public int getHvacLonizerState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_IONIZER, 0);
    }

    public int getHvacTempSyncMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_SETTEMP_SYNCST, 0);
    }

    public void setHvacSelfDrySwStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_AFTER_BLOW, 0, status);
    }

    public int getHvacSelfDrySwStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_AFTER_BLOW, 0);
    }

    public int getHvacMinWindSpeedLevel() {
        return 1;
    }

    public int getHvacMaxWindSpeedLevel() {
        return this.mHvacMaxFanSpeedLevel;
    }

    public int getMinHavcTemperature() {
        return 18;
    }

    public int getMaxHavcTemperature() {
        return 32;
    }

    public int getHvacErrorStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_ERROR, 0);
    }

    public int getHvacAirInTakeAutoControlStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_AIRINTAKE_AUTO_ST, 0);
    }

    public int getHvacWindSpeedAutoControlStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_FAN_AUTO_CONCTRL_ST, 0);
    }

    public int getHvacAirDistributionAutoControlStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_AIRDISTRIBUTION_AUTO_ST, 0);
    }

    public int getHvacAcCtrlType() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_AC_CTRL_TYPE, 0);
    }

    public int getHvacBlowerCtrlType() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_BLOWER_CTRL_TYPE, 0);
    }

    public int getHvacAirCirculationType() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_AIR_CIRCULATION_TYPE, 0);
    }

    public void setHvacSfsSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_SFS_SW_ST, 0, enable);
    }

    public int getHvacSfsSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_SFS_SW_ST, 0);
    }

    public int[] getHvacSfsTypeInChannels() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.HVAC_SFS_CH_ALL);
    }

    public void setHavacSfsChannel(int channel) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_SFS_TASTE_SET, 0, channel);
    }

    public int getHvacSfsChannel() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_SFS_TASTE_SET, 0);
    }

    public void setHvacDeodorizeSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_DEODORIZE, 0, enable);
    }

    public int getHvacDeodorizeSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_DEODORIZE, 0);
    }

    public void setHvacWarpSpeedWarmingSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_MAX_WARMING, 0, enable);
    }

    public int getHvacWarpSpeedWarmingSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_MAX_WARMING, 0);
    }

    public void setHvacWarpSpeedCoolingSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_MAX_COOLING, 0, enable);
    }

    public int getHvacWarpSpeedCoolingSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_MAX_COOLING, 0);
    }

    public void setHvacAutoDefogSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_AUTO_DEFOG_SET, 0, enable);
    }

    public int getHvacAutoDefogSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_AUTO_DEFOG_SET, 0);
    }

    public int getHvacAutoDefogWorkSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_AUTO_DEFOG_WORK_ST, 0);
    }

    public void setHvacSfsConcentration(int value) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_SFS_CON_ST, 0, value);
    }

    public int getHvacSfsConcentrationStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_SFS_CON_ST, 0);
    }

    public int getHvacCoConcentrationStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_CO_CONST, 0);
    }

    public int getHvacDisinfSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_DISINF_SW, 0);
    }

    public int getHvacFrogingRiskStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_FROGING_RISK, 0);
    }

    @Deprecated
    public void setHvacSfsTypeMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_SFS_MODE, 0, mode);
    }

    @Deprecated
    public void setHvacSfsChannelResetRequest(int channel, int request) {
        if (channel == 0) {
            this.mPropertyService.setIntProperty(VehicleProperty.HVAC_SFS_CH1_RESET_REQ, 0, request);
        } else if (channel == 1) {
            this.mPropertyService.setIntProperty(VehicleProperty.HVAC_SFS_CH2_RESET_REQ, 0, request);
        } else if (channel == 2) {
            this.mPropertyService.setIntProperty(VehicleProperty.HVAC_SFS_CH3_RESET_REQ, 0, request);
        } else {
            Slog.e(CarLog.TAG_XPVEHICLE, "setHvacSfsChannelResetRequest wrong channel number:" + channel);
        }
    }

    public int getHavcCompressorConsumePower() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HAVC_COMPRESSOR_COMSUME_PWR, 0);
    }

    public int getHavcHvhConsumePower() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HAVC_HVH_COMSUME_PWR, 0);
    }

    public void setRearHvacAirDistributionMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_REAR_BLOWER_MODE, 0, mode);
    }

    public void setRearHvacFanPosition(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_REAR_FAN_DIRECTION, 0, mode);
    }

    public int getRearHvacFanPosition() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_REAR_FAN_DIRECTION, 0);
    }

    public void setRearHvacPowerEnabled(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_REAR_POWER_ON_XP, 0, enable);
    }

    public int getRearHvacPowerState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_REAR_POWER_ON_XP, 0);
    }

    public void setHvacSecRowLeftTempInc() {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_SECROWL_TEMP_UP, 0, 1);
    }

    public void setHvacSecRowLeftTempDec() {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_SECROWL_TEMP_DOWN, 0, 1);
    }

    public void setHvacSecRowRightTempInc() {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_SECROWR_TEMP_UP, 0, 1);
    }

    public void setHvacSecRowRightTempDec() {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_SECROWR_TEMP_DOWN, 0, 1);
    }

    public void setHvacTempSecRowLeftValue(float level) {
        this.mPropertyService.setFloatProperty(VehicleProperty.HVAC_SECROWL_TEMPE_SET, 0, level);
    }

    public float getHvacTempSecRowLeftValue() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.HVAC_SECROWL_TEMPE_SET, 0);
    }

    public void setHvacTempSecRowRightValue(float level) {
        this.mPropertyService.setFloatProperty(VehicleProperty.HVAC_SECROWR_TEMPE_SET, 0, level);
    }

    public float getHvacTempSecRowRightValue() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.HVAC_SECROWR_TEMPE_SET, 0);
    }

    public void setHvacRearAutoModeEnabled(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_REAR_AUTO_ON_XP, 0, enable);
    }

    public int getHvacRearAutoModeState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_REAR_AUTO_ON_XP, 0);
    }

    public int getHvacRearWindSpeedAutoControlStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_REAR_FAN_AUTO_CONCTRL_ST, 0);
    }

    public int getHvacRearAirDistributionAutoControlStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_REAR_AIRDISTRIBUTION_AUTO_ST, 0);
    }

    public void setHvacRearFanSpeedInc() {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_REAR_FAN_SPEED_UP, 0, 1);
    }

    public void setHvacRearFanSpeedDec() {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_REAR_FAN_SPEED_DOWN, 0, 1);
    }

    public void setHvacRearFanSpeedLevel(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_REAR_FAN_SPEED, 0, level);
    }

    public int getHvacRearFanSpeedLevel() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_REAR_FAN_SPEED, 0);
    }

    public void setHvacThirdRowTempInc() {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_THIROW_TEMP_UP, 0, 1);
    }

    public void setHvacThirdRowTempDec() {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_THIROW_TEMP_DOWN, 0, 1);
    }

    public void setHvacTempThirdRowtValue(float level) {
        this.mPropertyService.setFloatProperty(VehicleProperty.HVAC_THIROW_TEMPE_SET, 0, level);
    }

    public float getHvacTempThirdRowValue() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.HVAC_THIROW_TEMPE_SET, 0);
    }

    public void setHvacThirdRowWindBlowMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_THIROW_BLOWER_MODE, 0, mode);
    }

    public int getHvacThirdRowWindBlowMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_THIROW_BLOWER_MODE, 0);
    }

    public void setHvacNewFreshSwitchStatus(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_NEW_FRESH_SW, 0, onOff);
    }

    public int getHvacNewFreshSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_NEW_FRESH_SW, 0);
    }

    public void setHvacRearWindLessSwitch(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_REAR_FAN_INSENVENT, 0, onOff);
    }

    public int getHvacRearWindLessSwitch() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_REAR_FAN_INSENVENT, 0);
    }

    public void setHvacMachineStateSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.HVAC_MACHINE_STATE_SWITCH, 0, enable);
    }

    public int getHvacMachineStateSwitch() {
        return this.mPropertyService.getIntProperty(VehicleProperty.HVAC_MACHINE_STATE_SWITCH, 0);
    }

    public void calibrateTpmsTirePressure() {
        this.mPropertyService.setIntProperty(VehicleProperty.TPMS_SENSOR_RESET, 0, 1);
    }

    public int getTpmsTirePressureStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TPMS_SENSOR_RESET, 0);
    }

    public float getTpmsTirePressureValue(int position) {
        if (position == 1) {
            float retVal = this.mPropertyService.getFloatProperty(VehicleProperty.TPMS_PRFL, 0);
            return retVal;
        } else if (position == 2) {
            float retVal2 = this.mPropertyService.getFloatProperty(VehicleProperty.TPMS_PRFR, 0);
            return retVal2;
        } else if (position == 3) {
            float retVal3 = this.mPropertyService.getFloatProperty(VehicleProperty.TPMS_PRRL, 0);
            return retVal3;
        } else if (position != 4) {
            return 0.0f;
        } else {
            float retVal4 = this.mPropertyService.getFloatProperty(VehicleProperty.TPMS_PRRR, 0);
            return retVal4;
        }
    }

    public int getTpmsSystemFaultWarnLampStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TPMS_SYSFAULTWARN, 0);
    }

    public int getTpmsAbnormalTirePressureWarnLampStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TPMS_ABNORMALPRWARN, 0);
    }

    public int getTpmsTirePressureWarningInfo(int position) {
        if (position == 1) {
            int retVal = this.mPropertyService.getIntProperty(VehicleProperty.TPMS_PRWARNFL, 0);
            return retVal;
        } else if (position == 2) {
            int retVal2 = this.mPropertyService.getIntProperty(VehicleProperty.TPMS_PRWARNFR, 0);
            return retVal2;
        } else if (position == 3) {
            int retVal3 = this.mPropertyService.getIntProperty(VehicleProperty.TPMS_PRWARNRL, 0);
            return retVal3;
        } else if (position == 4) {
            int retVal4 = this.mPropertyService.getIntProperty(VehicleProperty.TPMS_PRWARNRR, 0);
            return retVal4;
        } else {
            Slog.e(TAG, "Unsupported position: " + position);
            throw new IllegalArgumentException("Unsupported position");
        }
    }

    public int[] getTpmsAllTirePressureWarnings() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.TPMS_PRWARN_ALL);
    }

    public int[] getTpmsAllTireTemperatureWarnings() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.TPMS_TEMPWARN_ALL);
    }

    public int[] getTpmsllTirePerssureSensorStatus() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.TPMS_SENSORWARN_ALL);
    }

    public int[] getTpmsAllTireTemperature() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.TPMS_TEMP_ALL);
    }

    public int[] getTpmsAllSensorStatus() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.TPMS_ALL_SENSOR_ST);
    }

    public final int getFrontCollisionSecurity() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_FCWAEB_SW, 0);
    }

    public final void setFrontCollisionSecurity(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_FCWAEB_SW, 0, enable);
    }

    public final int getIntelligentSpeedLimit() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_ISLC_SW, 0);
    }

    public final void setIntelligentSpeedLimit(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_ISLC_SW, 0, mode);
    }

    public final int getLaneChangeAssist() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_LCS_SW, 0);
    }

    public final void setLaneChangeAssist(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_LCS_SW, 0, enable);
    }

    public final int getSideReversingWarning() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SIDE_REVERSION_WARNING, 0);
    }

    public final void setSideReversingWarning(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_SIDE_REVERSION_WARNING, 0, enable);
    }

    public final int getLaneDepartureWarning() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_LDW_SW, 0);
    }

    public final void setLaneDepartureWarning(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_LDW_SW, 0, enable);
    }

    public final int getBlindAreaDetectionWarning() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_BSD_SW, 0);
    }

    public final void setBlindAreaDetectionWarning(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_BSD_SW, 0, enable);
    }

    public final int getParkingStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_PARKING_STATUS, 0);
    }

    public final void setLocationInfo(float latitude, float longitude, float altitude, float bearing, float accuracy, long gpsTime) {
        float lLatitude = (float) (latitude * 1.0E7d);
        float lLongitude = (float) (longitude * 1.0E7d);
        float gps_Time = (float) (gpsTime / 1000.0d);
        float[] locationArray = {lLatitude, lLongitude, altitude, accuracy, bearing, gps_Time};
        this.mPropertyService.setFloatVectorProperty(VehicleProperty.SCU_GPS_LOCATION, locationArray);
    }

    public final void setAutoPilotLocationInfo(float latitude, float longitude, float altitude, float bearing, float accuracy, float gpsSpeed, long gpsTime) {
        float lLatitude = (float) (latitude * 1.0E7d);
        float lLongitude = (float) (longitude * 1.0E7d);
        float gps_Time = (float) (gpsTime / 1000.0d);
        float[] locationArray = {lLatitude, lLongitude, altitude, accuracy, bearing, gpsSpeed, gps_Time};
        this.mPropertyService.setFloatVectorProperty(VehicleProperty.SCU_GPS_MESSAGE, locationArray);
    }

    public final void setScuTest(int cmd) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_TEST, 0, cmd);
    }

    public final int[] getScu322LogData() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.SCU_LOG_DATA_322);
    }

    public final int[] getScu3FDLogData() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.SCU_LOG_DATA_3FD);
    }

    public final int[] getScu3FELogData() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.SCU_LOG_DATA_3FE);
    }

    public final int getScuOperationTips() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_OPERATION_TIPS, 0);
    }

    public final void setParkLotChoseIndex2Scu(int index) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_CHOSE_ID, 0, index);
    }

    public final void setParkLotRecvIndex2Scu(int index) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_RECEIVE_ID, 0, index);
    }

    public final void setSuperParkMode(int active) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_SUPER_PK_ACT, 0, active);
    }

    public final void setAutoParkInState(int state) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_AUTO_PARK_CMD, 0, state);
    }

    public final void setAutoParkOutState(int state) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_AUTO_PARK_CMD, 0, state);
    }

    public int getNearestEnableRadar() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_RADAR_DISTANCE_EN, 0);
    }

    private int bytesToInt(byte[] src, int offset) {
        int value = (src[offset] & UByte.MAX_VALUE) | ((src[offset + 1] & UByte.MAX_VALUE) << 8) | ((src[offset + 2] & UByte.MAX_VALUE) << 16) | ((src[offset + 3] & UByte.MAX_VALUE) << 24);
        return value;
    }

    private int bytesToShort(byte[] src, int offset) {
        int value = (src[offset] & UByte.MAX_VALUE) | ((src[offset + 1] & UByte.MAX_VALUE) << 8);
        return value;
    }

    public int[] getFrontRadarLevel() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.SCU_FRONT_RADAR_LVL);
    }

    public int[] getTailRadarLevel() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.SCU_TAIL_RADAR_LVL);
    }

    public int[] getFrontRadarFaultSt() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.SCU_FRONT_RADAR_FAULT_ST);
    }

    public int[] getTailRadarFaultSt() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.SCU_TAIL_RADAR_FAULT_ST);
    }

    public float[] getMileageExtraParams() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.SCU_LOCAT_DATA_2);
    }

    public int getRadarWarningVoiceStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_URADAR_ALARM_SW, 0);
    }

    public final void setRadarWarningVoiceStatus(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_URADAR_ALARM_SW, 0, type);
    }

    public float getGpsSpeed() {
        return 0.0f;
    }

    public final void setPhoneAPButton(int action) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_PHONE_AP_BTN, 0, action);
    }

    public int getAutoParkErrorCode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_PARK_ERR, 0);
    }

    public int getFarLampAutoSwitch() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_IHB_SW, 0);
    }

    public void setFarLampAutoSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_IHB_SW, 0, enable);
    }

    public int getCutLinePreventSw() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_CIP_SW, 0);
    }

    public void setCutLinePreventSw(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_CIP_SW, 0, enable);
    }

    public int getRearCrossEmergencyWarning() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_RCTA_SW, 0);
    }

    public void setRearCrossEmergencyWarning(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_RCTA_SW, 0, enable);
    }

    public int getRearCollisionSecurity() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_RCW_SW, 0);
    }

    public void setRearCollisionSecurity(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_RCW_SW, 0, enable);
    }

    public int getDoorOpenWarning() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_DOW_SW, 0);
    }

    public void setDoorOpenWarning(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_DOW_SW, 0, enable);
    }

    public int getFatigueDetectionSw() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_DSM_SW, 0);
    }

    public void setFatigueDetectionSw(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_DSM_SW, 0, enable);
    }

    public int getTrafficSignRecognition() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_TSR_SW, 0);
    }

    public void setTrafficSignRecognition(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_TSR_SW, 0, enable);
    }

    public int getSpdLimitWarnType() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SLA_WARNING_TYPE, 0);
    }

    public void setSpdLimitWarnType(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_SLA_WARNING_TYPE, 0, type);
    }

    public int getLaneAlignmentAssist() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_LCC_SW, 0);
    }

    public void setLaneAlignmentAssist(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_LCC_SW, 0, enable);
    }

    public int getHighSpeedNavigation() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_HIC_SW, 0);
    }

    public void setHighSpeedNavigation(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_HIC_SW, 0, enable);
    }

    public void setAutoParkReq(int cmd) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_AUTO_PARK_CMD, 0, cmd);
    }

    public void setKeyRemoteParkType(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_REMOTE_CTRL_BTN, 0, type);
    }

    public int getKeyRemoteType() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_REMOTE_CTRL_BTN, 0);
    }

    public int getIntelligentCallButton() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_REMOTE_DRIVE_BTN, 0);
    }

    public void setIntelligentCallButton(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_REMOTE_DRIVE_BTN, 0, enable);
    }

    public int getPhoneSMButton() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_PHONE_PK_BTN, 0);
    }

    public void setPhoneSMButton(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_PHONE_PK_BTN, 0, enable);
    }

    public int getScuPhoneSmMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_PHONE_SM_BTN, 0);
    }

    public void setScuPhoneSmMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_PHONE_SM_BTN, 0, mode);
    }

    public int getAutoParkSwitch() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_PK_BTN, 0);
    }

    public void setAutoParkSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_PK_BTN, 0, enable);
    }

    public int getPhoneParkType() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_PHONE_CTRL_BTN, 0);
    }

    public void setPhoneParkType(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_PHONE_CTRL_BTN, 0, type);
    }

    public int getKeyRemoteSMButton() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_REMOTE_PK_BTN, 0);
    }

    public void setKeyRemoteSMButton(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_REMOTE_PK_BTN, 0, enable);
    }

    public void setAdasMapInfo(byte[] info) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.SCU_ADAS_MAPINFO, info);
    }

    public float[] getParkSlotInfo() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.SCU_PARK_SLOT_INFO);
    }

    public float[] getEnvCharacterInfo() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.SCU_ENV_CHARACTER_INFO);
    }

    public float[] getCarPositionInfo() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.SCU_POSITION_INFO);
    }

    public float[] getRadarDataInfo() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.SCU_URADAR_INFO);
    }

    public int getBlindAreaLeftWarning() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_BSD_LEFT_WARNING, 0);
    }

    public int getBlindAreaRightWarning() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_BSD_RIGHT_WARNING, 0);
    }

    public int getRearCrossLeftWarning() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_RCTA_LEFT_WARNING, 0);
    }

    public int getRearCrossRightWarning() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_RCTA_RIGHT_WARNING, 0);
    }

    public int getXpuLongCtrlRemind() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_LONGCTRL_REMIND, 0);
    }

    public int getXpilotStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_XPILOT_ST, 0);
    }

    public int getXpuLatCtrlRemind() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_LATCTRL_REMIND, 0);
    }

    public int getAccStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_ACC_ST, 0);
    }

    public int getScuKeyPark() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_KEYPARK_SWST, 0);
    }

    public void setScuKeyPark(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_KEYPARK_SWST, 0, enable);
    }

    public float[] getScuAltimeter() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.SCU_PARKING_VER);
    }

    public int[] getScuSlotTheta() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.SCU_SLOT_THETA);
    }

    public int[] getScuTargetParkingPosition() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.SCU_PARKING_TSK_TIME);
    }

    public int getScuFrontMinDistance() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_URADAR_FDIST, 0);
    }

    public int getScuRearMinDistance() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_URADAR_RDIST, 0);
    }

    public int getScuModeIndex() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_MODE_INDEX, 0);
    }

    public void setScuLocalWeather(int network, int temperature, int humidity, int weather) {
        byte[] local_temp = {(byte) temperature};
        this.mPropertyService.setByteVectorProperty(VehicleProperty.SCU_CUR_TEMP, local_temp);
        byte[] local_humidity = {(byte) humidity};
        this.mPropertyService.setByteVectorProperty(VehicleProperty.SCU_CUR_HUMI, local_humidity);
        byte[] local_weather = {(byte) weather, (byte) network};
        this.mPropertyService.setByteVectorProperty(VehicleProperty.SCU_LOCAT_WEATH, local_weather);
    }

    public void setScuRoadAttr(int attr) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_ROAD_ATTR, 0, attr);
    }

    public void setScuAssLineChanged(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_ASS_LCHG_SWST, 0, type);
    }

    public int getScuAssLineChanged() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_ASS_LCHG_SWST, 0);
    }

    public void setScuDmsMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_DMS_MODE_ST, 0, mode);
    }

    public void setScuSeatBeltReq(int req) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_DMS_SEBLRQ, 0, req);
    }

    public int getScuRadarDisplayActive() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_RADAR_DIS_ACTIVE, 0);
    }

    public int getScuErrorTips() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_ERR_TIPS, 0);
    }

    public int getScuSuperParkMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SLOT_VOICE, 0);
    }

    public float[] getScuLocatData() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.SCU_LOCAT_DATA);
    }

    public float[] getScuParkingProgress() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.SCU_PAK_PERCENT);
    }

    public float[] getScuSensorData() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.SCU_FEATURE_DATA);
    }

    public float[] getScuLAvmData() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.SCU_LAVMSLOT);
    }

    public float[] getScuRAvmData() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.SCU_RAVMSLOT);
    }

    public float[] getScuSlotForPark() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.SCU_SLOTFOR_PARK);
    }

    public int getScuRadarVoiceActive() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_URADAR_VOICE_ACTIVE, 0);
    }

    public int getScuRadarVoiceTone() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_URADAR_VOICE_TONE, 0);
    }

    public float[] getScuSlotData() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.SCU_SLOT_INFO);
    }

    public int getScuSlotsNumber() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_CDU_SLOTNUM, 0);
    }

    public void setScuRoadAttributes(int parking, int road) {
        if (this.mIcmUseSomeIp) {
            this.mPropertyService.setIntProperty(VehicleProperty.SCU_ROAD_ATTR, 0, parking);
            return;
        }
        int[] data = {parking, road};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.SCU_ROAD_ATTR_PK_RD, data);
    }

    public void setScuDetailRoadClass(int roadClass) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_RDATTR_DETAIL_RD, 0, roadClass);
    }

    public int[] getScuMrrAssistSystemStates() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.SCU_MRR_ASSIST_SYSST);
    }

    public int getScuRearCollisionWarning() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_RCW_WARNING, 0);
    }

    public void setScuCommonHomeSlotID(int id) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_PHONE_AP_BTN, 0, id);
    }

    public void setScuFreeParking1Data(float rx, float ry, float rtheta, int state, int attr, float ds, float r) {
        float[] data = {r, ds, attr, state, rtheta, ry, rx};
        this.mPropertyService.setFloatVectorProperty(VehicleProperty.SCU_BOX_DATA_3D7, data);
    }

    public void setScuFreeParking2Data(float rx, float ry, float rtheta, int state, int attr, float ds, float r) {
        float[] data = {r, ds, attr, state, rtheta, ry, rx};
        this.mPropertyService.setFloatVectorProperty(VehicleProperty.SCU_BOX_DATA_3D8, data);
    }

    public int getScuLeftDoorOpenWarning() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_DOW_LWARNING, 0);
    }

    public int getScuRightDoorOpenWarning() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_DOW_RWARNING, 0);
    }

    public int getScuRearMirrorControlState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_RMIRROR_CTRL, 0);
    }

    public int getScuExtraLatCtrlRemindInfo() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_LATCTRL_REMIND_2, 0);
    }

    public int getScuAlarmFaultStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_ALARM_FAULT_ST, 0);
    }

    public int[] getScu322LogDataD20() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.SCU_LOG_DATA_322_D20);
    }

    public int getScuSlaStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SLA_ST, 0);
    }

    public int getScuLdwStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_LDW_ST, 0);
    }

    public int getScuBsdStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_BSD_ST, 0);
    }

    public int getScuRctaStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_RCTA_ST, 0);
    }

    public void setScuDistractionSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_DISTRACTION_SW_ST, 0, enable);
    }

    public int getScuCutInPreventionWarning() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_CIP_WARNING_ST, 0);
    }

    public int getScuLkaState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_LKA_STATE, 0);
    }

    public int getScuAccLkaWarning() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_ACC_LKA_WARNING, 0);
    }

    public int getScuRoadVoiceTips() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_ACC_RD_VOICE_TIPS, 0);
    }

    public void setScuSlaAlarmSwitch(int state) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_SLA_ALARM_SWST, 0, state);
    }

    public int getScuSlaAlarmSwitchState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SLA_ALARM_SWST, 0);
    }

    public float[] getScuLocatDataWithZ() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.SCU_LOCAT_DATA_11);
    }

    public void setScuFsdSwitch(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_FSD_BUTTON, 0, onOff);
    }

    public int getScuFsdSwitchState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_FSD_BUTTON, 0);
    }

    public void notifyScuRearViewMirrorAdjustmentPageState(int state) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_RVIEWMIRROR_SW_ST, 0, state);
    }

    public int getScuMrrFailureSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_MRR_FAILURE_ST, 0);
    }

    public int getScuFishEyeCamFailureSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_FISHEYECAM_FAILURE_ST, 0);
    }

    public int getScuMainCamFailureSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_MAINCAM_FAILURE_ST, 0);
    }

    public int getScuNarrowCamFailureSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_NARROWCAM_FAILURE_ST, 0);
    }

    public int[] getScuSideCamsFailureSt() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.SCU_SIDECAM_FAILURE_ST);
    }

    public int getScuDisplayCruiseSpeed() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_CRUISESPD_SET_DISP, 0);
    }

    public int getQuitNgpOddSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_QUIT_NGP_ODD, 0);
    }

    public void setScuNgpOperationButton(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_NGP_OPEBUTTON_ST, 0, sw);
    }

    public int getScuNgpOperationButtonSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_NGP_OPEBUTTON_ST, 0);
    }

    public int getScuNgpLcTips1() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_NGP_LC_TIPS1, 0);
    }

    public int getScuNgpInfoTips1() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_NGP_INFO_TIPS1, 0);
    }

    public void setScuNgpTipsWindowsSw(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_NGP_TIPS_WINDOWS_ST, 0, sw);
    }

    public int[] getScuDoorsObstacleDetectionSt() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.SCU_SDC_DOOR_DETECTION);
    }

    public int[] getScuDoorsRadarDistance() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.SCU_RADAR_DIST);
    }

    public int[] getScuDoorsRadarDisplayLevel() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.SCU_RADAR_DIS_LEVEL);
    }

    public int getScuXpilot3Status() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_NGP_XPILOTST, 0);
    }

    public void setScuNgpPreferFastLaneSw(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_NGP_QUICKLANE_SW, 0, sw);
    }

    public void setScuNgpAvoidTruckSw(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_NGP_TRUCKOFFSET_SW, 0, sw);
    }

    public void setScuNgpDriverConfirmLaneChangeSw(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_NGP_DRIVERCONFIRM_LC_SW, 0, sw);
    }

    @Deprecated
    public void setScuNgpLaneChangeMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_NGP_LCMODE_SW, 0, mode);
    }

    public void setScuNgpRemindMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_NGP_REMIND_MODE_SW, 0, mode);
    }

    public int getScuNgpTipsWindowsSwSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_NGP_TIPS_WINDOWS_ST, 0);
    }

    public int getScuNgpPreferFastLaneSwSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_NGP_QUICKLANE_SW, 0);
    }

    public int getScuNgpAvoidTruckSwSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_NGP_TRUCKOFFSET_SW, 0);
    }

    public int getScuNgpDriverConfirmLaneChangeSwSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_NGP_DRIVERCONFIRM_LC_SW, 0);
    }

    @Deprecated
    public int getScuNgpLaneChangeMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_NGP_LCMODE_SW, 0);
    }

    public int getScuNgpRemindMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_NGP_REMIND_MODE_SW, 0);
    }

    public void setScuSsLeftSystemStatus(byte[] data) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.SCU_SS_LSYS_ST, data);
    }

    public void setScuSsRightSystemStatus(byte[] data) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.SCU_SS_RSYS_ST, data);
    }

    public int getScuSdcUltrasonicRadarVoiceTone() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_CTRL_URADARVOICE_TONE, 0);
    }

    public void setScuOtaTagStatus(int tag) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_OTA_TAG, 0, tag);
    }

    public int getScuSdcTips() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_CTRL_CDU_TIPS, 0);
    }

    public int getScuSdcTts() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_CTRL_CDU_TTS, 0);
    }

    public void setScuCurrentRoadSpeedLimit(int speedLimit) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_ADAS_SEG_SPEED_LIMIT, 0, speedLimit);
    }

    public int getScuLccExitReason() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_LCC_EXIT_REASON, 0);
    }

    public int getScuAccExitReason() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_ACC_EXIT_REASON, 0);
    }

    public void setScuElkSwitch(int off) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_ELK_STATE, 0, off);
    }

    public int getScuElkSwitchState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_ELK_STATE, 0);
    }

    public int getScuRightSdcRadarStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_RRADAR_STATUS, 0);
    }

    public int getScuLeftSdcRadarStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_LRADAR_STATUS, 0);
    }

    public int getScuLeftSdcBlindStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_LBLIND_STATUS, 0);
    }

    public int getScuLeftSdcSceneStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_LSCENE_STATUS, 0);
    }

    public int getScuRightSdcBlindStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_RBLIND_STATUS, 0);
    }

    public int getScuRightSdcSceneStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_RSCENE_STATUS, 0);
    }

    public int getScuSdcAutoModeStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_AUTOMODE_STATUS, 0);
    }

    public int getScuSdcCtrlIndex1() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_CTRL_INDEX1, 0);
    }

    public int getScuSdcCtrlIndex2() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_CTRL_INDEX2, 0);
    }

    public int getScuLeftSdcURadarDistance() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_URADAR_LDOOR_DIST, 0);
    }

    public int getScuRightSdcURadarDistance() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_URADAR_RDOOR_DIST, 0);
    }

    public void setScuParkingGroundState(int state) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_PARKINGGROUND_STATE, 0, state);
    }

    public int getScuRightSdcRadarDistance() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_URADAR_R1_DIST, 0);
    }

    public int getScuRightSdcRadarTof() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_URADAR_R1_TOF, 0);
    }

    public int getScuRightSdcRadarPeakLevel() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_URADAR_R1_PEAKLEVEL, 0);
    }

    public int getScuRightSdcRadarStatusCcp() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_URADAR_R1_ST, 0);
    }

    public int getScuRightSdcRadarErrorStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_URADAR_R1_ERROR_ST, 0);
    }

    public int getScuLeftSdcRadarDistance() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_URADAR_L1_DIST, 0);
    }

    public int getScuLeftSdcRadarTof() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_URADAR_L1_TOF, 0);
    }

    public int getScuLeftSdcRadarPeakLevel() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_URADAR_L1_PEAKLEVEL, 0);
    }

    public int getScuLeftSdcRadarStatusCcp() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_URADAR_L1_ST, 0);
    }

    public int getScuLeftSdcRadarErrorStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_URADAR_L1_ERROR_ST, 0);
    }

    public int getScuRightSdcRadarRt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_URADAR_R1_RT1, 0);
    }

    public int getScuRightSdcRadarWaveWidth() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_URADAR_R1_WAVEWIDTH, 0);
    }

    public int getScuLeftSdcRadarRt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_URADAR_L1_RT1, 0);
    }

    public int getScuLeftSdcRadarWaveWidth() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_URADAR_L1_WAVEWIDTH, 0);
    }

    public int getRightRightSdcIndexN() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_R1_INDEX_N, 0);
    }

    public int getLeftSdcIndexN() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_L1_INDEX_N, 0);
    }

    public int[] getScuSdcRadarFusion() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.SCU_SDC_URADAR_FUSION);
    }

    public int getScuLeftSdcRadarHallCounter() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_URADAR_LHALL_COUNTER, 0);
    }

    public int getScuRightSdcRadarHallCounter() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SDC_URADAR_RHALL_COUNTER, 0);
    }

    public int getScuAebAlarmSwitchState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_AEB_ALARM_SWST, 0);
    }

    public int getScuSteeringWheelEps() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_STEERING_WHEEL_EPS, 0);
    }

    public void setScuVoiceLaneChangeCommand(int cmd) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_VOICE_LANE_CHANGE, 0, cmd);
    }

    public int getScuNgpModeStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_NGP_MODE_STATUS, 0);
    }

    public int getScuParkByMemorySwSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_PARK_AP_SW, 0);
    }

    public void setScuParkByMemorySw(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_PARK_AP_SW, 0, sw);
    }

    public int getScuDsmPrompt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_DSM_REMMIND, 0);
    }

    public int getScuLdwLkaSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_LDW_LKA_SW, 0);
    }

    public void setScuLdwLkaSwitchStatus(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_LDW_LKA_SW, 0, enable);
    }

    public int getScuLkaSwitchState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_LKA_ST, 0);
    }

    public void setScuCurrentElectronicEyeSpeedLimitAndDistance(int speedLimit, int distance) {
        int[] data = {speedLimit, distance};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.SCU_ADAS_PROFSHORT_PROFTYPE, data);
    }

    public float[] getScuURadarDataInfo() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.SCU_URADAR_DATA_INFO);
    }

    public int getScuAlcCtrlRemindInfo() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_ALCCTRL_REMIND, 0);
    }

    public int getScuRemoteFlag() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_REMOTE_FLAG, 0);
    }

    public void setScuRoadAttribType(int road) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_ROAD_ATTR_RD, 0, road);
    }

    public void setScuSpeedLimitDriverConfirmSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_SL_DRV_SET, 0, enable);
    }

    public int getScuSpeedLimitDriverConfirmStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SL_DRV_SET, 0);
    }

    public void setScuSpeedLimitRange(int range) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_SL_SPD_RANGE, 0, range);
    }

    public int getScuSpeedLimitRange() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SL_SPD_RANGE, 0);
    }

    public int getScuSpeedLimitSwitchState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SPD_LIMIT_ST, 0);
    }

    public void setScuSpeedLimitSwitchState(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_SPD_LIMIT_ST, 0, enable);
    }

    public void setScuIntelligentSpeedLimitValue(int value) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_ISL_SPD, 0, value);
    }

    public int getScuSpeedLimitControlSystemState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SLIF_ST, 0);
    }

    public int getScuMemoryParkingState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_ICM_CDU_AP_ST, 0);
    }

    public int getScuSpeedLimitRemindVoice() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_SPDLIT_VOCREMIND, 0);
    }

    public void setScuDsmStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_DSM_SWITCH, 0, status);
    }

    public int getScuDsmStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_DSM_SWITCH, 0);
    }

    public void sendScuNaviLoadLinkType(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.CDU_NAVI_ROAD_LINK_TYPE, 0, type);
    }

    public int[] getScuLeftRightBlindSpotDetectionSwitchStatus() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.SCU_LR_BSD_SW);
    }

    public int[] getScuLeftRightRearCollisionSwitchStatus() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.SCU_LR_RCW_SW);
    }

    public int[] getScuLeftRightDoorOpenWarningSwitchStatus() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.SCU_LR_DOW_SW);
    }

    public int[] getScuLeftRightRearCrossTrafficAlertStatus() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.SCU_LR_RCTA_SW);
    }

    public void setScuNaviRoadConnectAttrib(int attrib) {
        this.mPropertyService.setIntProperty(VehicleProperty.CDU_SCU_FORMWAY_RD, 0, attrib);
    }

    public void setScuNaviDangerAreaRDInfo(int dangerAreaLoc, int dangerLane, int dangerLaneNum, int dangerType, int dangerLevel, int dangerPro, int dangerAct) {
        int[] properties = {dangerAreaLoc, dangerLane, dangerLaneNum, dangerType, dangerLevel, dangerPro, dangerAct};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.SCU_NAVI_DANGER_AREA_RD_INFO, properties);
    }

    public int getScuDoorsLRadarDisplayLevel() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_LRADAR_DIS_LEVEL, 0);
    }

    public int getScuDoorsRRadarDisplayLevel() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_RRADAR_DIS_LEVEL, 0);
    }

    public int getScuDoorsLRadarDistance() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_LRADAR_DIST, 0);
    }

    public int getScuDoorsRRadarDistance() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_RRADAR_DIST, 0);
    }

    public void setScuEventInfoRD(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_EVENT_INFO_RD, 0, type);
    }

    public int getScuHmiDopRemind() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_HMI_DOP_REMIND, 0);
    }

    public int getScuMrrRadarEmissStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_MRR_RADAR_EMISS_ST, 0);
    }

    public int[] getScuAllSrrRadarEmissStatus() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.SCU_SRR_RADAR_EMISS_ST);
    }

    public int getLaneSupportSystemStateAndWarning() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_LSS_SWST, 0);
    }

    public void setLaneSupportSystemStateAndWarning(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_LSS_SWST, 0, enable);
    }

    public void SetFcwAebSensitivitySwitchStatus(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.SCU_FCWAEB_SEN_SW, 0, level);
    }

    public int getFcwAebSensitivitySwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_FCWAEB_SEN_SW, 0);
    }

    public void setAnalogSoundEffect(int type) {
    }

    public void setAnalogSoundEnable(int enable) {
    }

    public void setAvasWaitForWakeUpSoundSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.AVAS_WAKEUP_WAITSETCMD, 0, enable);
    }

    public void setAvasFullChargeWaitForWakeUpSoundSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.AVAS_FULLCHRGWAKEUPSETCMD, 0, enable);
    }

    public int getAvasWaitForWakeUpSoundState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVAS_WAKEUP_WAITSETCMD, 0);
    }

    public int getAvasFullChargeWaitForWakeUpSoundState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVAS_FULLCHRGWAKEUPSETCMD, 0);
    }

    public void setAvasSleepSoundSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.AVAS_CARSLEEP_SETCMD, 0, enable);
    }

    public int getAvasSleepSoundState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVAS_CARSLEEP_SETCMD, 0);
    }

    public void setAvasAcChargingSoundSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.AVAS_ACCHRGIN_SETCMD, 0, enable);
    }

    public int getAvasAcChargingSoundState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVAS_ACCHRGIN_SETCMD, 0);
    }

    public void setAvasDcChargingSoundSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.AVAS_DCCHRGIN_SETCMD, 0, enable);
    }

    public int getAvasDcChargingSoundState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVAS_DCCHRGIN_SETCMD, 0);
    }

    public void setAvasDisconnectChargingSoundSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.AVAS_DISCONNECTCHRG_SETCMD, 0, enable);
    }

    public int getAvasDisconnectChargingSoundState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVAS_DISCONNECTCHRG_SETCMD, 0);
    }

    public int getAvasLowSpeedSoundSwitch() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVAS_SWSTCMD, 0);
    }

    public void setAvasLowSpeedSoundSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.AVAS_SWSTCMD, 0, enable);
    }

    public int getAvasLowSpeedSoundEffect() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVAS_LOWSPEEDSOUND_CFG, 0);
    }

    public void setAvasLowSpeedSoundEffect(int sound) {
        this.mPropertyService.setIntProperty(VehicleProperty.AVAS_LOWSPEEDSOUND_CFG, 0, sound);
    }

    public void setAvasExternalSoundCmd(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.AVAS_EXTERNALSOUNDFILE_CMD, 0, type);
    }

    public int getAvasFriendlySayHiSound() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVAS_FRIENDLY_SAYHICFG, 0);
    }

    public void setAvasFriendlySayHiSound(int sound) {
        this.mPropertyService.setIntProperty(VehicleProperty.AVAS_FRIENDLY_SAYHICFG, 0, sound);
    }

    public int getAvasExternalVolume() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVAS_EXTVOLADJUST_CMD, 0);
    }

    public void setAvasExternalVolume(int vol) {
        this.mPropertyService.setIntProperty(VehicleProperty.AVAS_EXTVOLADJUST_CMD, 0, vol);
    }

    public int getAvasLowSpeedVolume() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVAS_LOWSPDVOLADJUST_CFG, 0);
    }

    public void setAvasLowSpeedVolume(int vol) {
        this.mPropertyService.setIntProperty(VehicleProperty.AVAS_LOWSPDVOLADJUST_CFG, 0, vol);
    }

    public void setAvasExternalSoundModeCmd(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.AVAS_EXTERNALSOUNDMODE_CMD, 0, mode);
    }

    public void setAvasPhotoSoundSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.AVAS_PHOTOSOUND_SW, 0, enable);
    }

    public int getAvasPhotoSoundSwitch() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVAS_PHOTOSOUND_SW, 0);
    }

    public int getAvasFaultStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVAS_FAULT, 0);
    }

    public void setAvasLockUnlockSoundSwitchStatus(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.AVAS_LOCK_UNLOCK_SOUND_SW, 0, enable);
    }

    public int getAvasLockUnlockSoundSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVAS_LOCK_UNLOCK_SOUND_SW, 0);
    }

    public void setAvasChargeSoundSwitchStatus(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.AVAS_CHARGE_SOUND_SW, 0, enable);
    }

    public int getAvasChargeSoundSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVAS_CHARGE_SOUND_SW, 0);
    }

    public void setAvasSocSoundSwitchStatus(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.AVAS_SOC_SOUND_SW, 0, enable);
    }

    public int getAvasSocSoundSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVAS_SOC_SOUND_SW, 0);
    }

    public void setAvasUnlockSoundEffect(int sound) {
        this.mPropertyService.setIntProperty(VehicleProperty.AVAS_UNLOCKSOUND_CFG, 0, sound);
    }

    public int getAvasUnlockSoundEffect() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVAS_UNLOCKSOUND_CFG, 0);
    }

    public void setAvasLockSoundEffect(int sound) {
        this.mPropertyService.setIntProperty(VehicleProperty.AVAS_LOCKSOUND_CFG, 0, sound);
    }

    public int getAvasLockSoundEffect() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVAS_LOCKSOUND_CFG, 0);
    }

    public int getAvasMcuAvasRunnningStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVAS_ACIVE_ST, 0);
    }

    @Deprecated
    public int getAvasUnlockSoundSpeedVolume() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVAS_UNLOCKSOUND_VOL, 0);
    }

    @Deprecated
    public int getAvasChargeSoundSpeedVolume() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVAS_CHARGE_SOUND_VOL, 0);
    }

    @Deprecated
    public int getAvasSocSoundSpeedVolume() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVAS_SOC_SOUND_VOL, 0);
    }

    public void setAvasSocSoundVolumeToMcu(int vol) {
        this.mPropertyService.setIntProperty(VehicleProperty.AVAS_SOC_SOUND_VOL_CMD, 0, vol);
    }

    public int getAvmCameraAngle() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVM_CAMERA_ANGLE, 0);
    }

    public void setAvmCameraAngle(int targetAngle) {
        this.mPropertyService.setIntProperty(VehicleProperty.AVM_CAMERA_ANGLE, 0, targetAngle);
    }

    public void setAvmRoofCameraRaise(int up) {
        this.mPropertyService.setIntProperty(VehicleProperty.AVM_CAMERA_HEIGHT, 0, up);
    }

    public int getAvmRoofCameraHeightStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVM_CAMERA_HEIGHT, 0);
    }

    public int getAvmCameraDisplayMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVM_DISPLAY_MODE, 0);
    }

    public void setAvmCameraDisplayMode(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.AVM_DISPLAY_MODE, 0, type);
    }

    public final int hasRoofCamera() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVM_CAMERA_ROOF_CAMERA, 0);
    }

    private float byte2float(byte[] byteArray, int index) {
        int num = byteArray[index + 0];
        return Float.intBitsToFloat((int) ((((int) ((((int) ((num & 255) | (byteArray[index + 1] << 8))) & SupportMenu.USER_MASK) | (byteArray[index + 2] << 16))) & ViewCompat.MEASURED_SIZE_MASK) | (byteArray[index + 3] << 24)));
    }

    public float[] getFrontRadarData() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.SCU_FRONT_RADAR);
    }

    public float[] getTailRadarData() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.SCU_TAIL_RADAR);
    }

    public float getSteerWheelRotationAngle() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.EPS_STEERING_ANGLE, 0);
    }

    public int getAvmRoofCameraState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVM_CAMERA_CAM_STATE, 0);
    }

    public int getAvmRoofCameraPosition() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVM_CAMERA_CAM_POS, 0);
    }

    public int getAvmCameraInitState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVM_CAM_MOVE_STATE, 0);
    }

    public int getAvmCalibrationMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVM_CALIBRATION, 0);
    }

    public void setAvmCalibrationMode(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.AVM_CALIBRATION, 0, type);
    }

    public int getAvmOverlayWorkSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVM_OVERLAY_WORK, 0);
    }

    public void setAvmOverlayWorkSt(int on) {
        this.mPropertyService.setIntProperty(VehicleProperty.AVM_OVERLAY_WORK, 0, on);
    }

    public int getAvmTransparentChassisState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVM_TRANSPARENT_CHASISST, 0);
    }

    public void setAvmTransparentChassisState(int on) {
        this.mPropertyService.setIntProperty(VehicleProperty.AVM_TRANSPARENT_CHASISST, 0, on);
    }

    public int getAvmFineTuneMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVM_FINETUNE_MODE, 0);
    }

    public void setAvmFineTuneMode(int on) {
        this.mPropertyService.setIntProperty(VehicleProperty.AVM_FINETUNE_MODE, 0, on);
    }

    public void setAvmMultipleDisplayProperties(int displayMode, int calibration, int overlayWorkSt, int transparentChasisWorkSt, int fineTuneMode) {
        int[] properties = {displayMode, calibration, overlayWorkSt, transparentChasisWorkSt, fineTuneMode};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.AVM_DISPLAY_MODE_MULSIGNAL, properties);
    }

    public int getAvmWorkState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVM_WORK_ST, 0);
    }

    public int[] getAvmCamerasFaultStates() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.AVM_CAMERA_FAULT_ST);
    }

    public void setAvm3603dAngle(int angle) {
        this.mPropertyService.setIntProperty(VehicleProperty.AVM_360_3D_ANGLE, 0, angle);
    }

    public int getAvm3603dAngle() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVM_360_3D_ANGLE, 0);
    }

    public void setAvmTransBodySwitchStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.AVM_TRANS_BODY_SW, 0, status);
    }

    public int getAvmTransBodySwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AVM_TRANS_BODY_SW, 0);
    }

    public int getBmsBatteryCapacity() {
        return 0;
    }

    public int getBmsBatteryType() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BMS_BAT_TYPE, 0);
    }

    public float getBmsChargeCompleteTime() {
        return 0.0f;
    }

    public int getBmsBatteryChipSwVersion() {
        return 0;
    }

    public int getBmsFailureLvl() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BMS_FAILURE_LVL, 0);
    }

    public int getBmsVoltMaxNum() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BMS_CELL_VOLT_MAX_NUM, 0);
    }

    public int getBmsChargeMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BMS_CHARGE_MODE, 0);
    }

    public int getBmsVoltMinNum() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BMS_CELL_VOLT_MIN_NUM, 0);
    }

    public int getBmsHottestCellNum() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BMS_BATT_TEMP_MAX_NUM, 0);
    }

    public int getBmsColdestCellNum() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BMS_BATT_TEMP_MIN_NUM, 0);
    }

    public int getBmsMaxTemp() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BMS_BATT_TEMP_MAX, 0);
    }

    public float getBmsVoltMax() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.BMS_CELL_VOLT_MAX, 0);
    }

    public float getBmsVoltMin() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.BMS_CELL_VOLT_MIN, 0);
    }

    public int getBmsInsulationResistance() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BMS_BATT_RES, 0);
    }

    public float getBmsBatteryCurrent() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.BMS_CURRENT, 0);
    }

    public float getBmsAcMaxCurrent() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.BMS_AC_MAX_CURRENT, 0);
    }

    public int getBmsDtcErrorStopCurrent() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BMS_DTC_ERR_STOP_CURR, 0);
    }

    public int getBmsDtcChargeCurrentOver() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BMS_DC_CHG_CURT_OVER, 0);
    }

    public int getBmsDcChargeStopReason() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BMS_DC_CHG_STOP_REASON, 0);
    }

    public float getBmsBatteryTotalVolt() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.BMS_BATT_VOLT, 0);
    }

    public float getBmsDcCurrent() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.BMS_CCS_OUT_CURRENT, 0);
    }

    public float getBmsDcVolt() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.BMS_CCS_OUT_VOLT, 0);
    }

    public float getBmsCellTempMaxNum() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.BMS_CELL_TEMP_MAX_NUM, 0);
    }

    public float getBmsCellTempMinNum() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.BMS_CELL_TEMP_MIN_NUM, 0);
    }

    public int getBmsBatteryChargeStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BMS_BAT_CHRG_ST, 0);
    }

    public int getBmsAcChargeStopReason() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BMS_AC_CHG_STOP_REASON, 0);
    }

    public int getInsulativeResistanceValue() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BMS_INSULATIVE_RESIST_VAL, 0);
    }

    public int getDischargeHighVoltageLockState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BMS_DISCHARGE_HIGHVOL_LOCKST, 0);
    }

    public int getChargeHighVoltageLockState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BMS_CHARGE_HIGHVOL_LOCKST, 0);
    }

    public float getBattOutWaterTempature() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.BMS_BAT_WATERTEMP, 0);
    }

    public void setAdasMeta(byte[] metaValues) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.MCU_ADAS_META, metaValues);
    }

    public void setAdasPosition(byte[] positionValues) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.MCU_ADAS_POSITION, positionValues);
    }

    public void setAdasProfLong(byte[] profLongValues) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.MCU_ADAS_PROFLONG, profLongValues);
    }

    public void setAdasProfShort(byte[] profShortValues) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.MCU_ADAS_PROFSHORT, profShortValues);
    }

    public void setAdasSegment(byte[] segmentValues) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.MCU_ADAS_SEGMENT, segmentValues);
    }

    public void setAdasStub(byte[] stubValues) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.MCU_ADAS_STUB, stubValues);
    }

    public int getDcdcFailStInfo() {
        return this.mPropertyService.getIntProperty(VehicleProperty.DCDC_FAIL_ST, 0);
    }

    public int getDcdcStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.DCDC_OPERATING_MODE, 0);
    }

    public int getDcdcInputVoltage() {
        return this.mPropertyService.getIntProperty(VehicleProperty.DCDC_INPUT_VOLTAGE, 0);
    }

    public float getDcdcInputCurrent() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.DCDC_INPUT_CURRENT, 0);
    }

    public int getCcsFaultInfo() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CCS_FAULT_INFO, 0);
    }

    public int getEpsWorkMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.STEERING_WHEEL_EPS, 0);
    }

    public void setEpsWorkMode(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.STEERING_WHEEL_EPS, 0, type);
    }

    public float getEpsSteeringAngle() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.EPS_STEERING_ANGLE, 0);
    }

    public float getEpsSteeringAngleSpeed() {
        return this.mPropertyService.getIntProperty(VehicleProperty.EPS_STEERING_ANGLE_SPD, 0);
    }

    public float getEpsTorsionBarTorque() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.EPS_TORSION_BAR_TORQ, 0);
    }

    public int getEpsTorqControlStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.EPS_TORQ_CTRL_ST, 0);
    }

    public void setEspHdcEnabled(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.ESP_HDC, 0, enable);
    }

    public int isEspHdcEnabled() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ESP_HDC, 0);
    }

    public int getEspWorkMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ESC_ESP, 0);
    }

    public void setEspWorkMode(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.ESC_ESP, 0, type);
    }

    public void setEspAvhEnabled(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.ESC_AVH, 0, enable);
    }

    public int isEspAvhEnabled() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ESC_AVH, 0);
    }

    public int getEspIbsBrakeMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ESC_IBS_BRAKE_MODE, 0);
    }

    public void setEspIbsBrakeMode(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.ESC_IBS_BRAKE_MODE, 0, type);
    }

    public float getEspCarSpeed() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.ESP_VEHSPD, 0);
    }

    public int hasEspFault() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ESP_ESP_FAULT, 0);
    }

    public int hasEspHdcFault() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ESP_HDC_FAULT, 0);
    }

    public int hasEspAvhFault() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ESP_AVH_FAULT, 0);
    }

    public int getEspEpbWarningLampStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ESP_SYS_WARNIND_REQ, 0);
    }

    public int getEspEpsWarninglampStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ESP_WARN_LAMP, 0);
    }

    public int getEspApbSystemDisplayMessage() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ESP_APB_SYSDISP_MSGREQ, 0);
    }

    public void setEspEpbSystemSwitch(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.ESP_APBSYS_ST, 0, status);
    }

    public int getEspApbSystemStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ESP_APBSYS_ST, 0);
    }

    public int getEspHbcRequestStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.IBT_HBC_REQ, 0);
    }

    public void setEspOffRoadSwitch(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.ESP_OFFROAD, 0, onOff);
    }

    public int getEspOffRoadSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ESP_OFFROAD, 0);
    }

    public int getEspInterventionStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ESP_CDP_INTERVENTION, 0);
    }

    public void setEspTsmSwitchStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.ESP_TSM_SW, 0, status);
    }

    public int getEspTsmSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ESP_TSM_SW, 0);
    }

    public int getEspTsmFaultStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ESP_TSM_FAULT_ST, 0);
    }

    public int getEspDtcFaultStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ESP_DTC_FAULT, 0);
    }

    public int getEspIbtFailureLampRequest() {
        return this.mPropertyService.getIntProperty(VehicleProperty.IBT_FAILURE_LAMP, 0);
    }

    public float[] getEspAllWheelSpeed() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.ESP_WHEEL_SPD_ALL);
    }

    public int getEspAbsWorkStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ESP_ABS_ACT_ST, 0);
    }

    public int getEspTcsWorkStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ESP_TCS_ACT_ST, 0);
    }

    public int getEspVdcWorkStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ESP_VDC_ACT_ST, 0);
    }

    public int getEspIpuFrontActualRotateSpeed() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ESP_IPUF_ACT_ROT_SPD, 0);
    }

    public int getEspIpuRearActualRotateSpeed() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ESP_IPUR_ACT_ROT_SPD, 0);
    }

    public float getEspIpuFrontActualTorque() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.ESP_IPUF_ACT_TORQ, 0);
    }

    public int getEspIpuFrontMotorActualTemperature() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ESP_IPUF_MOTOR_ACT_TEMP, 0);
    }

    public float getEspIpuRearActualTorque() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.ESP_IPUR_ACT_TORQ, 0);
    }

    public int getEspIpuRearMotorActualTemperature() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ESP_IPUR_MOTOR_ACT_TEMP, 0);
    }

    public float getEspIbtBrakeTravelDistance() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.IBT_BRAKE_TRAVEL, 0);
    }

    public float getEspMasterCylinderPressure() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.ESP_MASTER_CYLINDER_PRESS, 0);
    }

    public void setEspCstStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.ESP_CST, 0, status);
    }

    public void setEspBpfStatus(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.ESP_BPF, 0, mode);
    }

    public int getEspEpbDriverOffWarningMsg() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ESP_EPB_DRIVER_OFF_WARNING, 0);
    }

    public void resetIcmMeterMileageA() {
        int prop = this.mIcmUseSomeIp ? VehicleProperty.ICM_RESET_TRIPA_SP : VehicleProperty.ICM_RESET_TRIPA;
        this.mPropertyService.setIntProperty(prop, 0, 1);
    }

    public void resetIcmMeterMileageB() {
        int prop = this.mIcmUseSomeIp ? VehicleProperty.ICM_RESET_TRIPB_SP : VehicleProperty.ICM_RESET_TRIPB;
        this.mPropertyService.setIntProperty(prop, 0, 1);
    }

    public int getIcmTemperature() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ICM_TEMPERATURE, 0);
    }

    public void setIcmTemperature(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.ICM_TEMPERATURE, 0, enable);
    }

    public int getIcmWindPower() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ICM_WIND_POWER, 0);
    }

    public void setIcmWindPower(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.ICM_WIND_POWER, 0, enable);
    }

    public int getIcmWindMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ICM_WIND_MODE, 0);
    }

    public void setIcmWindMode(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.ICM_WIND_MODE, 0, enable);
    }

    public int getIcmMediaSource() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ICM_MEDIA_SOURCE, 0);
    }

    public void setIcmMediaSource(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.ICM_MEDIA_SOURCE, 0, enable);
    }

    public int getIcmScreenLight() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ICM_SCREEN_LIGHT, 0);
    }

    public void setIcmScreenLight(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.ICM_SCREEN_LIGHT, 0, enable);
    }

    public int getIcmNavigation() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ICM_NAVIGATION, 0);
    }

    public void setIcmNavigation(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.ICM_NAVIGATION, 0, enable);
    }

    public int getIcmDayNightSwitch() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ICM_DAYNIGHT_SWITCH, 0);
    }

    public void setIcmDayNightSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.ICM_DAYNIGHT_SWITCH, 0, enable);
    }

    public int getIcmWindBlowMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ICM_WIND_BLOW_MODE, 0);
    }

    public void setIcmWindBlowMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.ICM_WIND_BLOW_MODE, 0, mode);
    }

    public int getIcmWindLevel() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ICM_WIND_BLOW_LEVEL, 0);
    }

    public void setIcmWindLevel(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.ICM_WIND_BLOW_LEVEL, 0, level);
    }

    public float getIcmDriverTempValue() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.ICM_DRIVER_TEMPERATURE, 0);
    }

    public void setIcmDriverTempValue(float value) {
        this.mPropertyService.setFloatProperty(VehicleProperty.ICM_DRIVER_TEMPERATURE, 0, value);
    }

    public void setMeterSoundState(int type, int volume, int mute) {
        int[] mSoundState = {type, volume, mute};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.ICM_METER_SOUND_STATE, mSoundState);
    }

    public void sendContacts(byte[] json) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.ICM_SEND_BT_MSG, json);
    }

    public void setWeatherInfo(byte[] json) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.ICM_SEND_WEATHER_MSG, json);
    }

    public void setNavigationInfo(byte[] json) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.ICM_SEND_NAV_MSG, json);
    }

    public void setMusicInfo(byte[] json, byte[] image) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.ICM_SEND_MUSIC_MSG, byteMerge(json, image));
    }

    public void setNetRadioInfo(byte[] json, byte[] image) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.ICM_SEND_NET_RADIO_MSG, byteMerge(json, image));
    }

    public void sendIcmUpdateRequest(String req) {
        this.mPropertyService.setStringProperty(VehicleProperty.ICM_UPDATE_REQ, req);
    }

    public String getIcmUpdateResponse() {
        return this.mPropertyService.getStringProperty(VehicleProperty.ICM_UPDATE_REQ);
    }

    public void setIcmUpdateFileTransferStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.ICM_UPDATE_TRANS, 0, status);
    }

    public int getIcmUpdateResult() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ICM_UPDATE_RESULT, 0);
    }

    public int getIcmUpdateProgress() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ICM_UPDATE_PROGRESS, 0);
    }

    public String getIcmCrashInfo() {
        return this.mPropertyService.getStringProperty(VehicleProperty.ICM_CRASH_INFO);
    }

    public String getIcmDiagnosisInfo() {
        return this.mPropertyService.getStringProperty(VehicleProperty.ICM_DIAGNOSIS_INFO);
    }

    public void setRadioInfo(byte[] json) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.ICM_SEND_RADIO_MSG, json);
    }

    public void setBtMusicState(byte[] json) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.ICM_SEND_BT_MSG, json);
    }

    public void setIcmSystemTimeValue(int hour, int minutes) {
        int[] time = {hour, minutes};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.ICM_SET_TIME, time);
    }

    private byte[] intToByteArray(int intData) {
        byte[] byteArray = {(byte) (intData & 255), (byte) ((intData >> 8) & 255), (byte) ((intData >> 16) & 255), (byte) ((intData >> 24) & 255)};
        return byteArray;
    }

    private byte[] byteMerge(byte[] byte_1, byte[] byte_2) {
        if (byte_1 == null && byte_2 == null) {
            return null;
        }
        if (byte_1 == null) {
            byte[] byte1_length = intToByteArray(0);
            byte[] byteArray = new byte[byte_2.length + byte1_length.length];
            System.arraycopy(byte1_length, 0, byteArray, 0, byte1_length.length);
            System.arraycopy(byte_2, 0, byteArray, byte1_length.length, byte_2.length);
            return byteArray;
        } else if (byte_2 == null) {
            byte[] byte1_length2 = intToByteArray(byte_1.length);
            byte[] byteArray2 = new byte[byte_1.length + byte1_length2.length];
            System.arraycopy(byte1_length2, 0, byteArray2, 0, byte1_length2.length);
            System.arraycopy(byte_1, 0, byteArray2, byte1_length2.length, byte_1.length);
            return byteArray2;
        } else {
            byte[] byte1_length3 = intToByteArray(byte_1.length);
            byte[] byteArray3 = new byte[byte_1.length + byte_2.length + byte1_length3.length];
            System.arraycopy(byte1_length3, 0, byteArray3, 0, byte1_length3.length);
            System.arraycopy(byte_1, 0, byteArray3, byte1_length3.length, byte_1.length);
            System.arraycopy(byte_2, 0, byteArray3, byte1_length3.length + byte_1.length, byte_2.length);
            return byteArray3;
        }
    }

    public float getMeterMileageA() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.ICM_TRIPA, 0);
    }

    public float getMeterMileageB() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.ICM_TRIPB, 0);
    }

    public float getDriveTotalMileage() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.ICM_TOTAL_ODOMETER, 0);
    }

    public float getLastChargeMileage() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.ICM_TRIP_SINC_CHRG, 0);
    }

    public float getLastStartUpMileage() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.ICM_TRIP_SINC_IGON, 0);
    }

    public void setMeterBackLightLevel(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.ICM_LIGHT_LEVER_ADJ, 0, level);
    }

    public void setSpeechStateInfo(byte[] info) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.ICM_SEND_NOTIFY_MSG, info);
    }

    public int getIcmConnectionState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ICM_CONNECTED, 0);
    }

    public void sendRomBinMsg(int rpcNum, byte[] bJson, byte[] bin) {
        byte[] rpcNumByte = intToByteArray(rpcNum);
        byte[] jsonBinByte = byteMerge(bJson, bin);
        if (jsonBinByte == null) {
            return;
        }
        byte[] byteArray = new byte[rpcNumByte.length + jsonBinByte.length];
        System.arraycopy(rpcNumByte, 0, byteArray, 0, rpcNumByte.length);
        System.arraycopy(jsonBinByte, 0, byteArray, rpcNumByte.length, jsonBinByte.length);
        this.mPropertyService.setByteVectorProperty(VehicleProperty.ICM_SEND_ROM_MSG, byteArray);
    }

    public void setNotifyMessage(byte[] info) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.ICM_SEND_NOTIFY_MSG, info);
    }

    public void setIcmSyncSignal(String data) {
        this.mPropertyService.setStringProperty(VehicleProperty.ICM_SYNC_SIGNAL, data);
    }

    public void setIcmOsdShow(String data) {
        this.mPropertyService.setStringProperty(VehicleProperty.ICM_OSDSHOW, data);
    }

    public void setIcmInfoCardAdd(String data) {
        this.mPropertyService.setStringProperty(VehicleProperty.ICM_INFOCARD_ADD, data);
    }

    public void setIcmInfoCardUpdate(String data) {
        this.mPropertyService.setStringProperty(VehicleProperty.ICM_INFOCARD_UPDATE, data);
    }

    public void setIcmInfoCardRemove(String data) {
        this.mPropertyService.setStringProperty(VehicleProperty.ICM_INFOCARD_REMOVE, data);
    }

    public void setIcmAllCardsRefresh(String data) {
        this.mPropertyService.setStringProperty(VehicleProperty.ICM_ALLCARD_REFRSH, data);
    }

    public void setIcmInfoFlowMsg(String data) {
        this.mPropertyService.setStringProperty(VehicleProperty.ICM_INFOFLOW_MSG, data);
    }

    public void setIcmCarSetting(String data) {
        this.mPropertyService.setStringProperty(VehicleProperty.ICM_CARSETTING, data);
    }

    public void setIcmWeather(String data) {
        this.mPropertyService.setStringProperty(VehicleProperty.ICM_WEATHER, data);
    }

    public void setIcmWheelkey(int key) {
        Icm_Wheelkey_22 mWheelkey = new Icm_Wheelkey_22();
        mWheelkey.wheelKey = key;
        String data = String.format("{\"wheelKey\":%d}", Integer.valueOf(key));
        this.mPropertyService.setStringProperty(VehicleProperty.ICM_WHEEL_KEY, data);
    }

    public void setIcmAccount(String data) {
        this.mPropertyService.setStringProperty(VehicleProperty.ICM_ACCOUNT, data);
    }

    public void setIcmSyncTime(String data) {
        this.mPropertyService.setStringProperty(VehicleProperty.ICM_SYNC_TIME, data);
    }

    public void setIcmNavigationBmp(int totalsize, int pagesize, int pageIndex, int totalPage, byte[] data) {
    }

    public void setIcmNavigationInfo(String data) {
        if (data == null) {
            throw new IllegalArgumentException("data cannot be null");
        }
        this.mPropertyService.setByteVectorProperty(VehicleProperty.ICM_SEND_NAV_MSG, data.getBytes());
    }

    public void setBtPhoneCall(String data) {
        this.mPropertyService.setStringProperty(VehicleProperty.ICM_BLUETOOTH_PHONE, data);
    }

    public int getIcmAlarmVolume() {
        int prop = this.mIcmUseSomeIp ? VehicleProperty.ICM_ALARM_VOLUME_SP : VehicleProperty.ICM_ALARM_VOLUME;
        return this.mPropertyService.getIntProperty(prop, 0);
    }

    public void setIcmAlarmVolume(int volumeType) {
        int prop = this.mIcmUseSomeIp ? VehicleProperty.ICM_ALARM_VOLUME_SP : VehicleProperty.ICM_ALARM_VOLUME;
        this.mPropertyService.setIntProperty(prop, 0, volumeType);
    }

    public int getIcmTimeFormat() {
        int prop = this.mIcmUseSomeIp ? VehicleProperty.ICM_TIME_FORMAT_SP : VehicleProperty.ICM_TIME_FORMAT;
        return this.mPropertyService.getIntProperty(prop, 0);
    }

    public void setIcmTimeFormat(int index) {
        int prop = this.mIcmUseSomeIp ? VehicleProperty.ICM_TIME_FORMAT_SP : VehicleProperty.ICM_TIME_FORMAT;
        this.mPropertyService.setIntProperty(prop, 0, index);
    }

    public int getIcmBrightness() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ICM_BRIGHTNESS_SP, 0);
    }

    public void setIcmBrightness(int level) {
        int prop = this.mIcmUseSomeIp ? VehicleProperty.ICM_BRIGHTNESS_SP : VehicleProperty.ICM_LIGHT_LEVER_ADJ;
        this.mPropertyService.setIntProperty(prop, 0, level);
    }

    public int getSpeedLimitWarningValue() {
        int prop = this.mIcmUseSomeIp ? VehicleProperty.ICM_SPEED_LIMIT_WARNING_VALUE_SP : VehicleProperty.ICM_SPEED_LIMIT_WARNING_VALUE;
        return this.mPropertyService.getIntProperty(prop, 0);
    }

    public void setSpeedLimitWarningValue(int level) {
        int prop = this.mIcmUseSomeIp ? VehicleProperty.ICM_SPEED_LIMIT_WARNING_VALUE_SP : VehicleProperty.ICM_SPEED_LIMIT_WARNING_VALUE;
        this.mPropertyService.setIntProperty(prop, 0, level);
    }

    public int getSpeedLimitWarningSwitch() {
        int prop = this.mIcmUseSomeIp ? VehicleProperty.ICM_SPEED_LIMIT_WARNING_SWITCH_SP : VehicleProperty.ICM_SPEED_LIMIT_WARNING_SWITCH;
        return this.mPropertyService.getIntProperty(prop, 0);
    }

    public void setSpeedLimitWarningSwitch(int level) {
        int prop = this.mIcmUseSomeIp ? VehicleProperty.ICM_SPEED_LIMIT_WARNING_SWITCH_SP : VehicleProperty.ICM_SPEED_LIMIT_WARNING_SWITCH;
        this.mPropertyService.setIntProperty(prop, 0, level);
    }

    public void setIcmDmsMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.ICM_DMS_MODE_ST, 0, mode);
    }

    public void setIcmFatigueLevel(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.ICM_FATG_LVL, 0, level);
    }

    public void setIcmDistractionLevel(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.ICM_DIST_LVL, 0, level);
    }

    public void setIcmMusicInfo(String musicInfo) {
        this.mPropertyService.setStringProperty(VehicleProperty.ICM_MEDIA_INFO, musicInfo);
    }

    public void setIcmMusicPlaybackTimeInfo(String timeInfo) {
        this.mPropertyService.setStringProperty(VehicleProperty.ICM_MEDIA_CURTIME, timeInfo);
    }

    public String getNaviBmpInfoRequiredByIcm() {
        return this.mPropertyService.getStringProperty(VehicleProperty.ICM_NEEDNAVI);
    }

    public void setIcmRadioType(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.ICM_RADIO_TYPE, 0, type);
    }

    public void setIcmFaceInfo(String faceInfo) {
        this.mPropertyService.setStringProperty(VehicleProperty.ICM_FACE_INFO, faceInfo);
    }

    public int getIcmBrakeFluidLevelWarningMessage() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ICM_BFLWARNING, 0);
    }

    public int getIcmCabinAiFeedback() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ICM_FEEDBACK, 0);
    }

    public void setIcmDayNightMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.ICM_SYNC_DAY_NIGHT_MODE, 0, mode);
    }

    public String getIcmUpdatingPartitionAndProgress() {
        return this.mPropertyService.getStringProperty(VehicleProperty.ICM_UPDATE_PARTITION_PROGRESS);
    }

    public String getIcmEcuUpdateResult() {
        return this.mPropertyService.getStringProperty(VehicleProperty.ICM_UPDATE_PARTITION_RESULT);
    }

    public void requestIcmDashboardLightsStatus() {
        this.mPropertyService.setIntProperty(VehicleProperty.ICM_ALL_LAMP_STATE_REQ, 0, 0);
    }

    public String getIcmDashboardLightsStatus() {
        return this.mPropertyService.getStringProperty(VehicleProperty.ICM_ALL_LAMP_STATE_INFO);
    }

    public void setIcmLeftCard(int index) {
        this.mPropertyService.setIntProperty(VehicleProperty.ICM_LEFT_MENU_CONTROL, 0, index);
    }

    public void setIcmRightCard(int index) {
        this.mPropertyService.setIntProperty(VehicleProperty.ICM_RIGHT_MENU_CONTROL, 0, index);
    }

    public void setIcmModeInfoArray(int name, int status) {
        int[] values = {name, status};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.ICM_SEND_MODE, values);
    }

    public void setIcmUserScenarioInfo(int[] info) {
        if (info == null || info.length != 3) {
            return;
        }
        this.mPropertyService.setIntVectorProperty(VehicleProperty.ICM_USER_SCENARIO_INFO, info);
    }

    public void setIcmWiperRainDetectSensitivity(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.ICM_RAIN_DETEC_SENCFG, 0, level);
    }

    public void sendIcmLogCompressRequest(String req) {
        this.mPropertyService.setStringProperty(VehicleProperty.ICM_LOG_COMPRESS, req);
    }

    public String getIcmLogCompressInformation() {
        return this.mPropertyService.getStringProperty(VehicleProperty.ICM_LOG_COMPRESS);
    }

    public void setIcmSoundThemeType(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.ICM_SOUND_THEME_TYPE, 0, type);
    }

    @Deprecated
    public void setIcmUserScenarioExitDialog(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.ICM_USER_SCENARIO_EXIT_DIALOG, 0, status);
    }

    public void sendIcmRandisDisplayType(String info) {
        this.mPropertyService.setStringProperty(VehicleProperty.ICM_RANDIS_DISPLAY_TYPE, info);
    }

    public int getIpuFailStInfo() {
        return this.mPropertyService.getIntProperty(VehicleProperty.IPUR_FAILST, 0);
    }

    public int getCtrlVolt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.IPU_ACTHV_VOLT, 0);
    }

    public int getCtrlCurr() {
        return this.mPropertyService.getIntProperty(VehicleProperty.IPU_ACTHV_CUR, 0);
    }

    public int getCtrlTemp() {
        return this.mPropertyService.getIntProperty(VehicleProperty.IPU_INVERTER_ACT_TEMP, 0);
    }

    public int getMotorTemp() {
        return this.mPropertyService.getIntProperty(VehicleProperty.IPU_MOTOR_ACT_TEMP, 0);
    }

    public float getTorque() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.IPU_ACT_TORQ, 0);
    }

    public int getRollSpeed() {
        return this.mPropertyService.getIntProperty(VehicleProperty.IPU_ACT_ROT_SPD, 0);
    }

    public int getMotorStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.IPU_ST_MODE, 0);
    }

    public void setPowerOnTunner() {
    }

    public void setPowerOffTunner() {
    }

    public void setRadioSearchStationUp() {
    }

    public void setRadioSearchStationDown() {
    }

    public void setStartFullBandScan() {
    }

    public void setStopFullBandScan() {
    }

    public void setRadioBand(int band) {
    }

    public void setRadioVolumePercent(int channel, int vol) {
    }

    public int getRadioVolumeAutoFocus() {
        return 0;
    }

    public void setRadioVolumeAutoFocus(int percent) {
    }

    public void setFmVolume(int channel, int volume) {
    }

    public void setCarExhibitionModeVol(int percent) {
    }

    public void setRadioFrequency(int band, int frequency) {
    }

    public int[] getRadioFrequency() {
        int[] ret = {0, 0};
        return ret;
    }

    public void setAudioMode(int item, int value) {
    }

    public int[] getAudioMode() {
        int[] ret = {0, 0};
        return ret;
    }

    public String getRadioStatus() {
        return "";
    }

    public String getAudioDspStatus() {
        return "";
    }

    public void setAudioGEQParams(int band, int frequence, int liftCurve, int gain) {
    }

    public void setAudioBalanceFader(int value1, int value2) {
    }

    public void setAudioParameters() {
    }

    public long getTboxRtcTimeStamp() {
        return this.mPropertyService.getLongProperty(VehicleProperty.TBOX_RTC, 0);
    }

    public void setTboxWifiStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.CDU_WIFI_STATE, 0, status);
    }

    public void setTboxWifiGatewayInfo(String info) {
        this.mPropertyService.setStringProperty(VehicleProperty.CDU_WIFI_GW, info);
    }

    public void sendTboxRemoteDiagInfo(String diagInfo) {
        this.mPropertyService.setStringProperty(VehicleProperty.CDU_REMOTE_DIAG_PARK_LOC, diagInfo);
    }

    public int getRemoteDiagCaptureRequest() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TBOX_REMOTE_DIAG_CAPTURE_REQ, 0);
    }

    public void setRemoteDiagCaptureResponse(String response) {
        this.mPropertyService.setStringProperty(VehicleProperty.CDU_REMOTE_DIAG_CAPTURE_RESP, response);
    }

    public void getTboxVersionInfoAsync() {
    }

    public void setTboxVersionInfoRequest() {
        this.mPropertyService.setStringProperty(VehicleProperty.TBOX_VERSION, "NULL");
    }

    public String getTboxVersionInfoResponse() {
        return this.mPropertyService.getStringProperty(VehicleProperty.TBOX_VERSION);
    }

    public void beginTboxOTA() {
    }

    public void startTboxOTA(String file) {
        this.mPropertyService.setStringProperty(VehicleProperty.TBOX_OTA_UPDATE, file);
    }

    public String getStartTboxOTAResponse() {
        return this.mPropertyService.getStringProperty(VehicleProperty.TBOX_OTA_UPDATE);
    }

    public void stopTboxOTA() {
        this.mPropertyService.setStringProperty(VehicleProperty.TBOX_OTA_COMMIT, "NULL");
    }

    public String getStopTboxOTAResponse() {
        return this.mPropertyService.getStringProperty(VehicleProperty.TBOX_OTA_COMMIT);
    }

    public int getOTAProgress() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TBOX_OTA_PROGRESS, 0);
    }

    public String getTBoxModemInfo() {
        return this.mPropertyService.getStringProperty(VehicleProperty.TBOX_MODEM_INFO);
    }

    public int getTBoxConnectionStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TBOX_CONNECT_STATE, 0);
    }

    public int getTBoxChargeLimitValue() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TBOX_CHARGE_STOP_SOC, 0);
    }

    public void setTBoxChargeLimitValue(int value) {
        this.mPropertyService.setIntProperty(VehicleProperty.TBOX_CHARGE_STOP_SOC, 0, value);
    }

    public void requestTBoxModemStatus() {
        this.mPropertyService.setIntProperty(VehicleProperty.CDU_TBOX_MODEM_STATUS_REQ, 0, 1);
    }

    public String getTBoxLastApnMsg() {
        return this.mPropertyService.getStringProperty(VehicleProperty.TBOX_CDU_APN);
    }

    public String getTBoxLastModemMsg() {
        return this.mPropertyService.getStringProperty(VehicleProperty.TBOX_CDU_MODEM_STATUS_RESP);
    }

    public void requestTBoxBandModemStatus() {
        this.mPropertyService.setIntProperty(VehicleProperty.CDU_TBOX_BAND_MODEM_REQ, 0, 1);
    }

    public int getTBoxLastBandModemMsg() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TBOX_CDU_SET_BAND_MODEM_RESP, 0);
    }

    public String getTBoxBandModem() {
        return this.mPropertyService.getStringProperty(VehicleProperty.TBOX_BAND_MODEM);
    }

    public void setTBoxBandModem(String value) {
        this.mPropertyService.setStringProperty(VehicleProperty.TBOX_BAND_MODEM, value);
    }

    public void setChargeGunUnlock() {
        this.mPropertyService.setIntProperty(VehicleProperty.TBOX_CHARGEGUN_UNLOCK_CMD, 0, 1);
    }

    public int[] getChargeAppointTime() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.TBOX_APPOINT_CHG_SET);
    }

    public void setChargeAppointTime(int[] data) {
        this.mPropertyService.setIntVectorProperty(VehicleProperty.TBOX_APPOINT_CHG_SET, data);
    }

    public int getNetWorkType() {
        return 2;
    }

    public void setNetWorkType(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.CDU_NET_ST, 0, type);
    }

    public void getSimStatusAsync() {
        this.mPropertyService.setIntProperty(VehicleProperty.TBOX_SIM_ST, 0, 0);
    }

    public void setGpsReset(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.TBOX_GPS_RESET, 0, type);
    }

    public int getGpsResetResp() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TBOX_GPS_RESET, 0);
    }

    public String getTboxPsuMsg() {
        return this.mPropertyService.getStringProperty(VehicleProperty.TBOX_PSU_MSG);
    }

    public void setTboxPsuMsg(String msg) {
        this.mPropertyService.setStringProperty(VehicleProperty.TBOX_PSU_MSG, msg);
    }

    public void setTboxCanControlMsg(String msg) {
        this.mPropertyService.setStringProperty(VehicleProperty.TBOX_CAN_CONTROLLER, msg);
    }

    public String getTboxCanControlMsg() {
        return this.mPropertyService.getStringProperty(VehicleProperty.TBOX_CAN_CONTROLLER);
    }

    public int getTboxAvpStartStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TBOX_AVP_START, 0);
    }

    public void startTboxCertInstall() {
        this.mPropertyService.setIntProperty(VehicleProperty.TBOX_CERT_INSTALL, 0, 0);
    }

    public void startTboxCertVerify() {
        this.mPropertyService.setIntProperty(VehicleProperty.TBOX_CERT_VERIFY, 0, 0);
    }

    public String getTboxDvBattMsg() {
        return this.mPropertyService.getStringProperty(VehicleProperty.TBOX_DV_BATT);
    }

    public void setTboxChargeGunLock() {
        this.mPropertyService.setIntProperty(VehicleProperty.TBOX_CHRG_GUN_LOCK_REQ, 0, 1);
    }

    public void setTboxDvTestReq(int req) {
        this.mPropertyService.setIntProperty(VehicleProperty.TBOX_DV_ON_OFF, 0, req);
    }

    public void setTboxDvTempSamplingPeriod(int second) {
        this.mPropertyService.setIntProperty(VehicleProperty.TBOX_DV_TEMP_REQ, 0, second);
    }

    public String getTboxDvTempMsg() {
        return this.mPropertyService.getStringProperty(VehicleProperty.TBOX_DV_TEMP);
    }

    public void startTboxUpgradingTmcu() {
        this.mPropertyService.setIntProperty(VehicleProperty.TBOX_UPGRADE_TMCU, 0, 101);
    }

    public int getTboxTmcuUpgradingProgress() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TBOX_UPGRADE_TMCU, 0);
    }

    public void startTboxUpgrading4G() {
        this.mPropertyService.setIntProperty(VehicleProperty.TBOX_UPGRADE_4G, 0, 100);
    }

    public int getTbox4GUpgradingProgress() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TBOX_UPGRADE_4G, 0);
    }

    public void setTboxCameraRemoteControlFeedback(String msg) {
        this.mPropertyService.setStringProperty(VehicleProperty.TBOX_CAMERA_REMOTE_CTRL, msg);
    }

    public void sendUpgradingTboxByUdiskReq(String msg) {
        this.mPropertyService.setStringProperty(VehicleProperty.TBOX_OTA_UDISK, msg);
    }

    public String getUpgradingTboxByUdiskResponse() {
        return this.mPropertyService.getStringProperty(VehicleProperty.TBOX_OTA_UDISK);
    }

    public void startTboxSlowCharge() {
        this.mPropertyService.setIntProperty(VehicleProperty.TBOX_SLOW_CHARGER_REQ, 0, 0);
    }

    public void stopTboxSlowCharge() {
        this.mPropertyService.setIntProperty(VehicleProperty.TBOX_SLOW_CHARGER_REQ, 0, 1);
    }

    public void setTboxAutoPowerOffConfig(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.TBOX_SET_AUTO_POWEOFF_SW, 0, enable);
    }

    public int getTboxAutoPowerOffSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TBOX_SET_AUTO_POWEOFF_SW, 0);
    }

    public void setTboxCancelPowerOffConfig(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.TBOX_UNSET_AUTO_POWEOFF_SW, 0, enable);
    }

    public int getTboxCancelPowerOffSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TBOX_UNSET_AUTO_POWEOFF_SW, 0);
    }

    public int[] getTboxPowerOffCountdown() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.TBOX_COUNTDOWN);
    }

    public void setTboxSoldierSw(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.TBOX_SOLDIERSW, 0, status);
    }

    public int getTboxSoldierSwState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TBOX_SOLDIERSW, 0);
    }

    public int getTboxSoldierWorkState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TBOX_SOLDIERWORKST, 0);
    }

    public int[] getTboxSoldierGsensorData() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.TBOX_GUARD);
    }

    public void sendTboxSoldierTick() {
        this.mPropertyService.setIntProperty(VehicleProperty.TBOX_GUARD_TICK, 0, 0);
    }

    public void sendTboxGpsAntPowerControlReq(int req) {
        int[] values = {req};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.TBOX_GPS_ANT_POWER, values);
    }

    public int[] getGpsAntPowerControlResponse() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.TBOX_GPS_ANT_POWER);
    }

    public void sendTboxGpsHwResetRequest() {
        this.mPropertyService.setIntProperty(VehicleProperty.TBOX_GPS_HW_RESET, 0, 0);
    }

    public void setGpsPollingType(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.TBOX_GPS_POLLING_CTRL, 0, type);
    }

    public void setTboxGpsDebugSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.TBOX_GPS_DEBUG, 0, enable);
    }

    public void sendTboxGpsMgaRequest() {
        this.mPropertyService.setIntProperty(VehicleProperty.TBOX_GPS_MGA, 0, 0);
    }

    public int getTboxGpsMgaResponse() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TBOX_GPS_MGA, 0);
    }

    public void resetTbox() {
        this.mPropertyService.setIntProperty(VehicleProperty.MCU_RESET_TBOX, 0, 1);
    }

    public void setTboxRepairMode(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.TBOX_REPAIR_MODE, 0, status);
    }

    public int getTboxRepairModeState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TBOX_REPAIR_MODE, 0);
    }

    public void setTboxSoliderCameraSwitch(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.TBOX_SOLDIER_CAMERASW, 0, onOff);
    }

    public int getTboxSoliderCameraState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TBOX_SOLDIER_CAMERASW, 0);
    }

    public void setTboxThresholdSwitch(int highLevel, int middleLevel, int lowLevel) {
        int[] tboxThresholdArray = {highLevel, middleLevel, lowLevel};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.TBOX_THRESHOLD, tboxThresholdArray);
    }

    public int getTboxSoliderEnableState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TBOX_SOLDIER_ENABLE, 0);
    }

    public void sendTboxRenewalPartsRequest(String req) {
        this.mPropertyService.setStringProperty(VehicleProperty.TBOX_REPLACE_REQ, req);
    }

    public String getTboxRenewalPartsResponse() {
        return this.mPropertyService.getStringProperty(VehicleProperty.TBOX_REPLACE_REQ);
    }

    public int getTboxRemoteLluMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TBOX_CDU_SELF_MODE, 0);
    }

    public void sendTboxBleAccountLoginFeedback(int feedback) {
        this.mPropertyService.setIntPropertyWithDefaultArea(VehicleProperty.TBOX_APP_ACCOUNT_FB, feedback);
    }

    public void sendTboxLocationInfo(String data) {
        this.mPropertyService.setStringProperty(VehicleProperty.TBOX_ANDROID_GPS_LOCATION, data);
    }

    public void setTboxBatteryKeepTempSwitch(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.VCU_BAT_KEEP_TEMP_REQ, 0, status);
    }

    public int getTboxRemoteBatteryKeepTempReq() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TBOX_BATTERY_KEEPTEMP_REQ, 0);
    }

    public void setTboxBatteryKeepTempAppointTime(int appointFlag, int appointHour, int appointMin) {
        int[] data = {appointFlag, appointHour, appointMin};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.TBOX_APPOINT_KEEP_TEMP, data);
    }

    public int[] getTboxBatteryKeepTempAppointTime() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.TBOX_APPOINT_KEEP_TEMP);
    }

    public void setTboxGpsLogSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.TBOX_UBLOX_LOG, 0, enable);
    }

    public int getTboxToggleGpsLogSwitchResult() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TBOX_UBLOX_LOG, 0);
    }

    public void sendTboxEmergencyWifiBleMessage(String msg) {
        this.mPropertyService.setStringProperty(VehicleProperty.TBOX_CDU_WIFI_BLE_EMER, msg);
    }

    public void sendTboxMultiBleRenewalRequest(String req) {
        this.mPropertyService.setStringProperty(VehicleProperty.TBOX_MUL_BLE_REPLACE, req);
    }

    public String getTboxMultiBleRenewalResponse() {
        return this.mPropertyService.getStringProperty(VehicleProperty.TBOX_MUL_BLE_REPLACE);
    }

    public void sendTboxFactoryPreCert(String cert) {
        this.mPropertyService.setStringProperty(VehicleProperty.TBOX_FACTORY_PRE_CERT, cert);
    }

    public String getTboxFactoryPreCert() {
        return this.mPropertyService.getStringProperty(VehicleProperty.TBOX_FACTORY_PRE_CERT);
    }

    public String getTboxPigeonNotification() {
        return this.mPropertyService.getStringProperty(VehicleProperty.MQTT_BACKEND_AND_PIGEONAPP);
    }

    public int getTboxEcallMuteRequest() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TBOX_ECALL_MUTE_REQ, 0);
    }

    public int getTboxEcallState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TBOX_ECALL_STATE, 0);
    }

    public void sendRoutingForTboxRequest(int cmd) {
        this.mPropertyService.setIntProperty(VehicleProperty.TBOX_WIFI_DEBUG_CTRL, 0, cmd);
    }

    public int getRoutingForTboxResponse() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TBOX_WIFI_DEBUG_CTRL, 0);
    }

    public void sendTboxModemCaptureRequest(int cmd) {
        this.mPropertyService.setIntProperty(VehicleProperty.TBOX_MODEM_CAPTURE_CTRL, 0, cmd);
    }

    public int getTboxModemCaptureResponse() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TBOX_MODEM_CAPTURE_CTRL, 0);
    }

    public void sendStartCopyTboxLogRequest(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.TBOX_LOG_DOWNLOAD_START, 0, type);
    }

    public int getStartCopyTboxLogResponse() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TBOX_LOG_DOWNLOAD_START, 0);
    }

    public void sendFinishCopyTboxLogRequest() {
        this.mPropertyService.setIntProperty(VehicleProperty.TBOX_LOG_DOWNLOAD_FINISH, 0, 0);
    }

    public int getFinishCopyTboxLogResponse() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TBOX_LOG_DOWNLOAD_FINISH, 0);
    }

    public int getTboxIOTBusinessType() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TBOX_IOT_BUS_TYPE, 0);
    }

    public int getTboxACChargeUnlockST() {
        return this.mPropertyService.getIntProperty(VehicleProperty.TBOX_AC_CHARGE_UNLOCK_ST, 0);
    }

    public void sendTboxOtaWorkingStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.CDU_TBOX_OTA_WOKING_ST, 0, status);
    }

    public void sendTboxWakeOrderRTC(String order) {
        this.mPropertyService.setStringProperty(VehicleProperty.TBOX_WAKE_ORDER_RTC, order);
    }

    public void sendTboxApnTrafficInfo(long apn0_traffic, long apn0_block, long apn1_traffic, long apn1_block) {
        long[] data = {apn0_traffic, apn0_block, apn1_traffic, apn1_block};
        this.mPropertyService.setLongVectorProperty(VehicleProperty.CDU_TBOX_APN_TRAFFIC, 0, data);
    }

    public String getTboxNetmConfInfo() {
        return this.mPropertyService.getStringProperty(VehicleProperty.TBOX_APN_NETM_CONFIG);
    }

    public int getAtlOpen() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ATL_OPEN, 0);
    }

    public void setAtlOpen(int on) {
        this.mPropertyService.setIntProperty(VehicleProperty.ATL_OPEN, 0, on);
    }

    public void setAtlPowerRequestSwitchStatus(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.ATL_POWER_REQ, 0, onOff);
    }

    public int[] getAtlLrPowerRequestSwitchStatus() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.ATL_LR_POWER_ST);
    }

    public void setAtlSwitchStatus(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.ATLS_SW_CTRL, 0, onOff);
    }

    public int getAtlSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ATLS_SW_CTRL, 0);
    }

    public void setAtlLin2Data(byte[] data) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.ATLS_LIN2_DATA, data);
    }

    public void setAtlLin3Data(byte[] data) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.ATLS_LIN3_DATA, data);
    }

    public void setAtlLin1Data(byte[] data) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.ATLS_LIN1_DATA, data);
    }

    public int getAtlReady() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ATL_WLCOMEFINISH, 0);
    }

    public void setTboxUpgradePrepareRequest(String req) {
        this.mPropertyService.setStringProperty(VehicleProperty.TBOX_UPGRADE_PREPARE, req);
    }

    public String getTboxUpgradePrepareResponse() {
        return this.mPropertyService.getStringProperty(VehicleProperty.TBOX_UPGRADE_PREPARE);
    }

    public int getAtlDowOpen() {
        return 0;
    }

    public void setAtlDowOpen(int on) {
    }

    public int getDoubleThemeColor() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ATL_THEMECOLORSWCFG, 0);
    }

    public void setDoubleThemeColor(int doubleColor) {
        this.mPropertyService.setIntProperty(VehicleProperty.ATL_THEMECOLORSWCFG, 0, doubleColor);
    }

    public int getThemeFirstColor() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ATL_THEMEFIRSTCOLORCFG, 0);
    }

    public void setThemeFirstColor(int color) {
        this.mPropertyService.setIntProperty(VehicleProperty.ATL_THEMEFIRSTCOLORCFG, 0, color);
    }

    public int getThemeSecondColor() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ATL_THEMESECONDCOLORCFG, 0);
    }

    public void setThemeSecondColor(int color) {
        this.mPropertyService.setIntProperty(VehicleProperty.ATL_THEMESECONDCOLORCFG, 0, color);
    }

    public int getAutoBrightness() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ATL_AUTOBRIGHTNESSSW, 0);
    }

    public void setAutoBrightness(int on) {
        this.mPropertyService.setIntProperty(VehicleProperty.ATL_AUTOBRIGHTNESSSW, 0, on);
    }

    public int getBrightnessLevel() {
        return this.mPropertyService.getIntProperty(VehicleProperty.ATL_BRIGHTNESSCFG, 0);
    }

    public void setBrightnessLevel(int brightness) {
        this.mPropertyService.setIntProperty(VehicleProperty.ATL_BRIGHTNESSCFG, 0, brightness);
    }

    public void setTwoLightData(byte protocol, byte[] lightPosition, boolean hold, byte[] color, byte[] bright, byte[] time) {
        byte[] lightdata = new byte[8];
        lightdata[0] = (byte) (protocol & 3);
        if (!hold) {
            lightdata[0] = (byte) (lightdata[0] | 4);
        }
        lightdata[0] = (byte) (lightdata[0] | ((byte) ((lightPosition[0] & 31) << 3)));
        lightdata[1] = time[0];
        lightdata[2] = (byte) ((color[0] & 31) << 3);
        lightdata[2] = (byte) (lightdata[2] | ((byte) ((bright[0] & 112) >> 4)));
        lightdata[3] = (byte) ((bright[0] & 15) << 4);
        lightdata[3] = (byte) (lightdata[3] | ((byte) ((protocol & 3) << 2)));
        if (!hold) {
            lightdata[3] = (byte) (lightdata[3] | 2);
        }
        lightdata[3] = (byte) (lightdata[3] | ((byte) ((lightPosition[1] & 16) >> 4)));
        lightdata[4] = (byte) ((lightPosition[1] & 15) << 4);
        lightdata[4] = (byte) (lightdata[4] | ((byte) ((color[1] & 30) >> 1)));
        lightdata[5] = (byte) ((color[1] & 1) << 7);
        lightdata[5] = (byte) (lightdata[5] | ((byte) (bright[1] & ByteCompanionObject.MAX_VALUE)));
        lightdata[6] = time[1];
        lightdata[7] = 0;
        this.mPropertyService.setByteVectorProperty(VehicleProperty.ATL_ATLS_SINGLECTRL, lightdata);
    }

    public void setGroutLightData(byte groupNum, byte solution, int lightlist, boolean hold, byte color, byte bright, byte time) {
        byte[] groupdata = new byte[8];
        if (color != -1) {
            groupdata[0] = 1;
        } else {
            groupdata[0] = 0;
        }
        groupdata[0] = (byte) (groupdata[0] | ((byte) ((solution & 1) << 1)));
        if (!hold) {
            groupdata[0] = (byte) (groupdata[0] | 4);
        }
        groupdata[0] = (byte) (groupdata[0] | ((byte) ((groupNum & 31) << 3)));
        groupdata[1] = time;
        groupdata[2] = (byte) ((color & 31) << 3);
        groupdata[2] = (byte) (groupdata[2] | ((byte) ((bright & 112) >> 4)));
        groupdata[3] = (byte) ((bright & 15) << 4);
        groupdata[3] = (byte) (((byte) (((lightlist & 4) >> 1) | ((lightlist & 1) << 3) | ((lightlist & 2) << 1) | ((lightlist & 8) >> 3))) | groupdata[3]);
        groupdata[4] = (byte) ((lightlist & 4080) >> 4);
        groupdata[5] = (byte) ((1044480 & lightlist) >> 12);
        groupdata[6] = (byte) ((3145728 & lightlist) >> 20);
        groupdata[7] = 0;
        if (demoFlag) {
            Message message = Message.obtain();
            Bundle data = new Bundle();
            data.putByteArray("lightData", groupdata);
            message.setData(data);
            OutPutHandler outPutHandler = this.outHandler;
            if (outPutHandler != null) {
                outPutHandler.sendMessage(message);
                return;
            }
            return;
        }
        this.mPropertyService.setByteVectorProperty(VehicleProperty.ATL_ATLS_MULTICTRL, groupdata);
    }

    public void setAllLightData(boolean hold, byte[] color, byte[] bright, byte[] fade) {
        byte[] alldata = new byte[64];
        alldata[0] = fade[0];
        alldata[1] = fade[1];
        alldata[2] = fade[2];
        alldata[3] = (byte) (bright[0] & ByteCompanionObject.MAX_VALUE);
        if (color[0] != Byte.MAX_VALUE && color[0] != -1) {
            alldata[3] = (byte) (alldata[3] | ByteCompanionObject.MIN_VALUE);
        }
        alldata[4] = (byte) (bright[1] & ByteCompanionObject.MAX_VALUE);
        if (!hold) {
            alldata[4] = (byte) (alldata[4] | ByteCompanionObject.MIN_VALUE);
        }
        alldata[5] = (byte) (bright[2] & ByteCompanionObject.MAX_VALUE);
        alldata[6] = (byte) ((color[0] & 31) << 3);
        alldata[6] = (byte) (alldata[6] | ((byte) ((color[1] & 28) >> 2)));
        alldata[7] = (byte) ((color[1] & 3) << 6);
        alldata[7] = (byte) (alldata[7] | ((byte) ((color[2] & 31) << 1)));
        alldata[8] = fade[3];
        alldata[9] = fade[4];
        alldata[10] = fade[5];
        alldata[11] = (byte) (bright[3] & ByteCompanionObject.MAX_VALUE);
        if (color[0] != Byte.MAX_VALUE && color[0] != -1) {
            alldata[11] = (byte) (alldata[11] | ByteCompanionObject.MIN_VALUE);
        }
        alldata[12] = (byte) (bright[4] & ByteCompanionObject.MAX_VALUE);
        if (!hold) {
            alldata[12] = (byte) (alldata[12] | ByteCompanionObject.MIN_VALUE);
        }
        alldata[13] = (byte) (bright[5] & ByteCompanionObject.MAX_VALUE);
        alldata[14] = (byte) ((color[3] & 31) << 3);
        alldata[14] = (byte) (((byte) ((color[4] & 28) >> 2)) | alldata[14]);
        alldata[15] = (byte) ((color[4] & 3) << 6);
        alldata[15] = (byte) (alldata[15] | ((byte) ((color[5] & 31) << 1)));
        alldata[16] = fade[6];
        alldata[17] = fade[7];
        alldata[18] = fade[8];
        alldata[19] = (byte) (bright[6] & ByteCompanionObject.MAX_VALUE);
        if (color[0] != Byte.MAX_VALUE && color[0] != -1) {
            alldata[19] = (byte) (alldata[19] | ByteCompanionObject.MIN_VALUE);
        }
        alldata[20] = (byte) (bright[7] & ByteCompanionObject.MAX_VALUE);
        if (!hold) {
            alldata[20] = (byte) (alldata[20] | ByteCompanionObject.MIN_VALUE);
        }
        alldata[21] = (byte) (bright[8] & ByteCompanionObject.MAX_VALUE);
        alldata[22] = (byte) ((color[6] & 31) << 3);
        alldata[22] = (byte) (alldata[22] | ((byte) ((color[7] & 28) >> 2)));
        alldata[23] = (byte) ((color[7] & 3) << 6);
        alldata[23] = (byte) (alldata[23] | ((byte) ((color[8] & 31) << 1)));
        alldata[24] = fade[9];
        alldata[25] = fade[10];
        alldata[26] = fade[11];
        alldata[27] = (byte) (bright[9] & ByteCompanionObject.MAX_VALUE);
        if (color[0] != Byte.MAX_VALUE && color[0] != -1) {
            alldata[27] = (byte) (alldata[27] | ByteCompanionObject.MIN_VALUE);
        }
        alldata[28] = (byte) (bright[10] & ByteCompanionObject.MAX_VALUE);
        if (!hold) {
            alldata[28] = (byte) (alldata[28] | ByteCompanionObject.MIN_VALUE);
        }
        alldata[29] = (byte) (bright[11] & ByteCompanionObject.MAX_VALUE);
        alldata[30] = (byte) ((color[9] & 31) << 3);
        alldata[30] = (byte) (alldata[30] | ((byte) ((color[10] & 28) >> 2)));
        alldata[31] = (byte) ((color[10] & 3) << 6);
        alldata[31] = (byte) (alldata[31] | ((byte) ((color[11] & 31) << 1)));
        alldata[32] = fade[12];
        alldata[33] = fade[13];
        alldata[34] = fade[14];
        alldata[35] = (byte) (bright[12] & ByteCompanionObject.MAX_VALUE);
        if (color[0] != Byte.MAX_VALUE && color[0] != -1) {
            alldata[35] = (byte) (alldata[35] | ByteCompanionObject.MIN_VALUE);
        }
        alldata[36] = (byte) (bright[13] & ByteCompanionObject.MAX_VALUE);
        if (!hold) {
            alldata[36] = (byte) (alldata[36] | ByteCompanionObject.MIN_VALUE);
        }
        alldata[37] = (byte) (bright[14] & ByteCompanionObject.MAX_VALUE);
        alldata[38] = (byte) ((color[12] & 31) << 3);
        alldata[38] = (byte) (alldata[38] | ((byte) ((color[13] & 28) >> 2)));
        alldata[39] = (byte) ((color[13] & 3) << 6);
        alldata[39] = (byte) (alldata[39] | ((byte) ((color[14] & 31) << 1)));
        alldata[40] = fade[15];
        alldata[41] = fade[16];
        alldata[42] = fade[17];
        alldata[43] = (byte) (bright[15] & ByteCompanionObject.MAX_VALUE);
        if (color[0] != Byte.MAX_VALUE && color[0] != -1) {
            alldata[43] = (byte) (alldata[43] | ByteCompanionObject.MIN_VALUE);
        }
        alldata[44] = (byte) (bright[16] & ByteCompanionObject.MAX_VALUE);
        if (!hold) {
            alldata[44] = (byte) (alldata[44] | ByteCompanionObject.MIN_VALUE);
        }
        alldata[45] = (byte) (bright[17] & ByteCompanionObject.MAX_VALUE);
        alldata[46] = (byte) ((color[15] & 31) << 3);
        alldata[46] = (byte) (alldata[46] | ((byte) ((color[16] & 28) >> 2)));
        alldata[47] = (byte) ((color[16] & 3) << 6);
        alldata[47] = (byte) (alldata[47] | ((byte) ((color[17] & 31) << 1)));
        alldata[48] = fade[18];
        alldata[49] = fade[19];
        alldata[50] = fade[20];
        alldata[51] = (byte) (bright[18] & ByteCompanionObject.MAX_VALUE);
        if (color[0] != Byte.MAX_VALUE && color[0] != -1) {
            alldata[51] = (byte) (alldata[51] | ByteCompanionObject.MIN_VALUE);
        }
        alldata[52] = (byte) (bright[19] & ByteCompanionObject.MAX_VALUE);
        if (!hold) {
            alldata[52] = (byte) (alldata[52] | ByteCompanionObject.MIN_VALUE);
        }
        alldata[53] = (byte) (bright[20] & ByteCompanionObject.MAX_VALUE);
        alldata[54] = (byte) ((color[18] & 31) << 3);
        alldata[54] = (byte) (((byte) ((28 & color[19]) >> 2)) | alldata[54]);
        alldata[55] = (byte) ((color[19] & 3) << 6);
        alldata[55] = (byte) (((byte) ((color[20] & 31) << 1)) | alldata[55]);
        alldata[56] = fade[21];
        alldata[59] = (byte) (bright[21] & ByteCompanionObject.MAX_VALUE);
        if (color[0] != Byte.MAX_VALUE && color[0] != -1) {
            alldata[59] = (byte) (alldata[59] | ByteCompanionObject.MIN_VALUE);
        }
        alldata[60] = 0;
        if (!hold) {
            alldata[60] = (byte) (alldata[60] | ByteCompanionObject.MIN_VALUE);
        }
        alldata[62] = (byte) ((color[21] & 31) << 3);
        this.mPropertyService.setByteVectorProperty(VehicleProperty.ATL_ATLS_ALLCTRL, alldata);
    }

    public void setAtlConfiguration(byte[] data) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.ATLS_COLOR_FADING_CTRL, data);
    }

    public int getLluEnableStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.LLU_SW, 0);
    }

    public void setLluEnableStatus(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.LLU_SW, 0, enable);
    }

    public int getLluWakeWaitSwitch() {
        return this.mPropertyService.getIntProperty(VehicleProperty.LLU_WAKEWAIT_SW, 0);
    }

    public void setLluWakeWaitSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.LLU_WAKEWAIT_SW, 0, enable);
    }

    public int getLluShowOffSwitch() {
        return this.mPropertyService.getIntProperty(VehicleProperty.LLU_SHOWOFF_SW, 0);
    }

    public void setLluShowOffSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.LLU_SHOWOFF_SW, 0, enable);
    }

    public int getLluSleepSwitch() {
        return this.mPropertyService.getIntProperty(VehicleProperty.LLU_SLEEP_SW, 0);
    }

    public void setLluSleepSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.LLU_SLEEP_SW, 0, enable);
    }

    public int getLluChargingSwitch() {
        return this.mPropertyService.getIntProperty(VehicleProperty.LLU_CHARGING_SW, 0);
    }

    public void setLluChargingSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.LLU_CHARGING_SW, 0, enable);
    }

    public int getLluPhotoSwitch() {
        return this.mPropertyService.getIntProperty(VehicleProperty.LLU_PHOTO_SW, 0);
    }

    public void setLluPhotoSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.LLU_PHOTO_SW, 0, enable);
    }

    public void setLluPrivateCtrl(boolean start, int fType, int fTick, int rType, int rTick, int personAngle, int personWalkDirection) {
        byte selfActive;
        byte[] lluData = new byte[8];
        if (start) {
            selfActive = 1;
        } else {
            selfActive = 0;
        }
        lluData[0] = (byte) (selfActive | 32);
        lluData[1] = (byte) (rType & 255);
        lluData[3] = (byte) (rTick & 255);
        lluData[2] = (byte) ((rTick & MotionEventCompat.ACTION_POINTER_INDEX_MASK) >> 8);
        lluData[4] = (byte) (fType & 255);
        lluData[6] = (byte) (fTick & 255);
        lluData[5] = (byte) ((65280 & fTick) >> 8);
        lluData[7] = 0;
        if (demoFlag) {
            Message message = Message.obtain();
            Bundle data = new Bundle();
            data.putByteArray("lightData", lluData);
            message.setData(data);
            OutPutHandler outPutHandler = this.outHandler;
            if (outPutHandler != null) {
                outPutHandler.sendMessage(message);
                return;
            }
            return;
        }
        this.mPropertyService.setByteVectorProperty(VehicleProperty.CDU_LLU_CTRL, lluData);
    }

    public int getLluBreathMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.LLU_BREATHMODESW, 0);
    }

    public void setLluBreathMode(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.LLU_BREATHMODESW, 0, type);
    }

    public int getLluCurrentFunction() {
        return this.mPropertyService.getIntProperty(VehicleProperty.LLU_FUNCTIONST, 0);
    }

    public void setLluCurrentFunction(int function) {
        this.mPropertyService.setIntProperty(VehicleProperty.LLU_FUNCTIONST, 0, function);
    }

    public int getLluWakeWaitMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.LLU_WAKEWAIT_CFG, 0);
    }

    public void setLluWakeWaitMode(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.LLU_WAKEWAIT_CFG, 0, type);
    }

    public int getLluShowOffMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.LLU_SHOWOFF_CFG, 0);
    }

    public void setLluShowOffMode(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.LLU_SHOWOFF_CFG, 0, type);
    }

    public int getLluSleepMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.LLU_SLEEP_CFG, 0);
    }

    public void setLluSleepMode(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.LLU_SLEEP_CFG, 0, type);
    }

    public int getLluAcChargeMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.LLU_ACCHARGING_CFG, 0);
    }

    public void setLluAcChargeMode(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.LLU_ACCHARGING_CFG, 0, type);
    }

    public int getLluDcChargeMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.LLU_DCCHARGING_CFG, 0);
    }

    public void setLluDcChargeMode(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.LLU_DCCHARGING_CFG, 0, type);
    }

    public int getLluPhotoMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.LLU_PHOTO_CFG, 0);
    }

    public void setLluPhotoMode(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.LLU_PHOTO_CFG, 0, type);
    }

    public void setLluSelfActive(int active) {
        this.mPropertyService.setIntProperty(VehicleProperty.LLU_SELF_ACTIVE, 0, active);
    }

    public void setLluLockSocDspSwitch(int active) {
        this.mPropertyService.setIntProperty(VehicleProperty.LLU_LOCK_SW, 0, active);
    }

    public int getLluLockSocDspSwitch() {
        return this.mPropertyService.getIntProperty(VehicleProperty.LLU_LOCK_SW, 0);
    }

    public void setLluUnLockSocDspSwitch(int active) {
        this.mPropertyService.setIntProperty(VehicleProperty.LLU_UNLOCK_SW, 0, active);
    }

    public int getLluUnLockSocDspSwitch() {
        return this.mPropertyService.getIntProperty(VehicleProperty.LLU_UNLOCK_SW, 0);
    }

    public void setLluScriptStRequest(int request) {
        this.mPropertyService.setIntProperty(VehicleProperty.LLU_SCRIPT_ST, 0, request);
    }

    public int getLluScriptStResponse() {
        return this.mPropertyService.getIntProperty(VehicleProperty.LLU_SCRIPT_ST, 0);
    }

    public void setLluScriptParameter(int[] parameter) {
        if (parameter == null) {
            Slog.w(TAG, "setLluScriptParameter parameter is null");
            throw new IllegalArgumentException("parameter cannot be null");
        } else {
            this.mPropertyService.setIntVectorProperty(VehicleProperty.LLU_SCRIPT_PARAM, parameter);
        }
    }

    public void setLluScriptData(int index, int pos, int length, int[] data) {
        if (data == null) {
            Slog.w(TAG, "setLluScriptData data is null");
            throw new IllegalArgumentException("data cannot be null");
        }
        int[] sentData = new int[63];
        sentData[0] = index;
        sentData[1] = pos;
        sentData[2] = length;
        int len = Math.min(data.length, 60);
        System.arraycopy(data, 0, sentData, 3, len);
        this.mPropertyService.setIntVectorProperty(VehicleProperty.LLU_SCRIPT_DATA, sentData);
    }

    public void setLluFindCarSwitch(int active) {
        this.mPropertyService.setIntProperty(VehicleProperty.MCU_LLU_FINDCAR, 0, active);
    }

    public int getLluFindCarSwitchState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_LLU_FINDCAR, 0);
    }

    public void setLluLockUnlockSocDspSwitch(int active) {
        this.mPropertyService.setIntProperty(VehicleProperty.MCU_LLU_LOCK_SOCDSP, 0, active);
    }

    public int getLluLockUnlockSocDspSwitchState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_LLU_LOCK_SOCDSP, 0);
    }

    public int getLluAcChargingCfg() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_LLU_ACCHARGINGCFG, 0);
    }

    public int getLluDcChargingCfg() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_LLU_DCCHARGINGCFG, 0);
    }

    public void setLluSpeedLimitCfg(int speed) {
        this.mPropertyService.setIntProperty(VehicleProperty.MCU_LLU_SPEDLIMIT_CFG, 0, speed);
    }

    public int getLluSpeedLimitCfg() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_LLU_SPEDLIMIT_CFG, 0);
    }

    public void setLluPersonAngle(int angle) {
        this.mPropertyService.setIntProperty(VehicleProperty.MCU_LLU_PERSONANGLE, 0, angle);
    }

    public void setLluPersonWalkDirection(int direction) {
        this.mPropertyService.setIntProperty(VehicleProperty.MCU_LLU_PERSONWALK_DIRECTION, 0, direction);
    }

    public int getMcuLluEnableStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_LLU_SW, 0);
    }

    public void setMcuLluEnableStatus(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.MCU_LLU_SW, 0, enable);
    }

    public int getMcuLluWakeWaitSwitch() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_LLU_WAKEWAIT_SW, 0);
    }

    public void setMcuLluWakeWaitSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.MCU_LLU_WAKEWAIT_SW, 0, enable);
    }

    public int getMcuLluShowOffSwitch() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_LLU_SHOWOFF_SW, 0);
    }

    public void setMcuLluShowOffSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.MCU_LLU_SHOWOFF_SW, 0, enable);
    }

    public int getMcuLluSleepSwitch() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_LLU_SLEEP_SW, 0);
    }

    public void setMcuLluSleepSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.MCU_LLU_SLEEP_SW, 0, enable);
    }

    public int getMcuLluChargingSwitch() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_LLU_CHARGING_SW, 0);
    }

    public void setMcuLluChargingSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.MCU_LLU_CHARGING_SW, 0, enable);
    }

    public int getMcuLluPhotoSwitch() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_LLU_PHOTO_SW, 0);
    }

    public void setMcuLluPhotoSwitch(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.MCU_LLU_PHOTO_SW, 0, enable);
    }

    public void activateAndroidLluControl() {
        this.mPropertyService.setIntProperty(VehicleProperty.MCU_LLU_ACTIVE, 0, 1);
    }

    public void deactivateAndroidLluControl() {
        this.mPropertyService.setIntProperty(VehicleProperty.MCU_LLU_ACTIVE, 0, 2);
    }

    public int getMcuLluWorkStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MCU_FB_LLU_ACTIVE, 0);
    }

    public void setMcuLluModeCtrl(int mhlActiveMode, int lhlActiveMode, int rhlActiveMode, int mrlActiveMode, int lrlActiveMode, int rrlActiveMode) {
        int[] data = {mhlActiveMode, lhlActiveMode, rhlActiveMode, mrlActiveMode, lrlActiveMode, rrlActiveMode};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MCU_LLU_MODECTRL, data);
    }

    public void setMcuLLuSelfControlData(byte[] data) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.MCU_SELF_CONTROL_DATA, data);
    }

    public void setLluAndroidLlSt(int st) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_ANDROID_LL_ST, 0, st);
    }

    public void setLluPowerRequestSwitchStatus(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.LLU_POWER_REQ, 0, enable);
    }

    public int getMcuLluPowerRequestSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.LLU_POWER_REQ, 0);
    }

    public void setMsmDrvSeatHorizMove(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSMD_SEATHORZCMD, values);
    }

    public void setMsmDrvSeatBackMove(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSMD_SEATTILTINGCMD, values);
    }

    public void setMsmDrvSeatVertiMove(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSMD_SEATVERTICALCMD, values);
    }

    public void setMsmDrvLegVertiMove(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSMD_SEATLEGHEIGHTCMD, values);
    }

    public void setMsmDrvLumbHorzMove(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSMD_SEATLUMBHORZCMD, values);
    }

    public void setMsmDrvLumbVertiMove(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSMD_SEATLUMBVERTICALCMD, values);
    }

    public int getMsmDrvSeatHorizPosition() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMD_SEATHORZPOS, 0);
    }

    public void setMsmDrvSeatHorizPosition(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMD_SEATHORZPOS, 0, pos);
    }

    public int getMsmDrvSeatVertiPosition() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMD_SEATVERTICALPOS, 0);
    }

    public void setMsmDrvSeatVertiPosition(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMD_SEATVERTICALPOS, 0, pos);
    }

    public int getMsmDrvSeatBackPosition() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMD_SEATILTINGPOS, 0);
    }

    public void setMsmDrvSeatBackPosition(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMD_SEATILTINGPOS, 0, pos);
    }

    public int getMsmDrvSeatLegPosition() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMD_SEATLEGHEIGHTPOS, 0);
    }

    public void setMsmDrvSeatLegPosition(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMD_SEATLEGHEIGHTPOS, 0, pos);
    }

    public void setMsmPsnSeatHorizMove(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSMP_SEATHORZCMD, values);
    }

    public void setMsmPsnSeatBackMove(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSMP_SEATTILTINGCMD, values);
    }

    public void setMsmPsnSeatVertiMove(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSMP_SEATVERTICALCMD, values);
    }

    public void setMsmDriverAllPositions(int seatHorizonPos, int seatVerticalPos, int seatTiltingPos, int legHeightPos) {
        byte[] allData = {(byte) (seatHorizonPos & 255), (byte) (seatVerticalPos & 255), (byte) (seatTiltingPos & 255), (byte) (legHeightPos & 255)};
        this.mPropertyService.setByteVectorProperty(VehicleProperty.MSMD_ALL_POS_SET, allData);
    }

    public void setMsmdAllPositions(int seatHorizonPos, int seatVerticalPos, int seatTiltingPos, int legHeightPos, int legHorzPos) {
        byte[] allData = {(byte) (seatHorizonPos & 255), (byte) (seatVerticalPos & 255), (byte) (seatTiltingPos & 255), (byte) (legHeightPos & 255), (byte) (legHorzPos & 255)};
        this.mPropertyService.setByteVectorProperty(VehicleProperty.MSMD_ALL_POS_SET, allData);
    }

    public void setMsmDriverAllPositionsToLDCU(int memoryReq, int seatHorizonPos, int seatVerticalPos, int seatTiltingPos, int legHeightPos, int legHorzPos) {
        byte[] allData = {(byte) (memoryReq & 255), (byte) (seatHorizonPos & 255), (byte) (seatVerticalPos & 255), (byte) (seatTiltingPos & 255), (byte) (legHeightPos & 255), (byte) (legHorzPos & 255)};
        this.mPropertyService.setByteVectorProperty(VehicleProperty.MSMD_ALL_POS_SET, allData);
    }

    public void saveMsmDriverAllPositionsToMcu(int seatHorizonPos, int seatVerticalPos, int seatTiltingPos, int legHeightPos) {
        byte[] allData = {(byte) (seatHorizonPos & 255), (byte) (seatVerticalPos & 255), (byte) (seatTiltingPos & 255), (byte) (legHeightPos & 255)};
        this.mPropertyService.setByteVectorProperty(VehicleProperty.MSMD_POS_SAVE, allData);
    }

    public void saveMsmPassengerAllPositionsToMcu(int seatHorizonPos, int seatVerticalPos, int seatTiltingPos, int legHeightPos) {
        int[] allData = {seatHorizonPos, seatVerticalPos, seatTiltingPos, legHeightPos};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSMP_POS_SAVE, allData);
    }

    public void saveMsmDAllPositionsToMcu(int seatHorizonPos, int seatVerticalPos, int seatTiltingPos, int legHeightPos, int legHorzPos) {
        byte[] allData = {(byte) (seatHorizonPos & 255), (byte) (seatVerticalPos & 255), (byte) (seatTiltingPos & 255), (byte) (legHeightPos & 255), (byte) (legHorzPos & 255)};
        this.mPropertyService.setByteVectorProperty(VehicleProperty.MSMD_POS_SAVE, allData);
    }

    public int getMsmPassengerSeatHorizontalPosition() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMP_SEATHORZPOS, 0);
    }

    public void setMsmPassengerSeatHorizontalPosition(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMP_SEATHORZPOS, 0, pos);
    }

    public int getMsmPassengerSeatVerticalPosition() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMP_SEATVERTICALPOS, 0);
    }

    public void setMsmPassengerSeatVerticalPosition(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMP_SEATVERTICALPOS, 0, pos);
    }

    public int getMsmPassengerSeatBackPosition() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMP_SEATILTINGPOS, 0);
    }

    public void setMsmPassengerSeatBackPosition(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMP_SEATILTINGPOS, 0, pos);
    }

    public int getMsmDriverHeadrestStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMD_HEADREST_ST, 0);
    }

    public int getMsmPassengerHeadrestStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMP_HEADREST_ST, 0);
    }

    public void setMsmPassengerAllPositions(int seatHorizonPos, int seatVerticalPos, int seatTiltingPos, int legHeightPos) {
        int[] allData = {seatHorizonPos, seatVerticalPos, seatTiltingPos, legHeightPos};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSMP_ALL_POS_SET, allData);
    }

    public void setMsmPassengerAllPositionsToLDCU(int memoryReq, int seatHorizonPos, int seatVerticalPos, int seatTiltingPos, int legHeightPos) {
        int[] allData = {memoryReq, seatHorizonPos, seatVerticalPos, seatTiltingPos, legHeightPos};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSMP_ALL_POS_SET, allData);
    }

    public void stopMsmDriverSeatMoving(int cmd) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMD_SEAT_STOPMOVING, 0, cmd);
    }

    public void stopMsmPassengerSeatMoving(int cmd) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMP_SEAT_STOPMOVING, 0, cmd);
    }

    public void setMsmDriverSeatTiltLevelOff(int cmd) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMD_SEAT_TILT_LEVEL_OFF, 0, cmd);
    }

    public void setMsmDriverSeatCushTiltPos(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSMD_SEAT_CUSH_TILT, values);
    }

    public void setMsmDriverSeatCushTiltPosition(int pos) {
    }

    public int getMsmDriverSeatCushTiltPosition() {
        return 0;
    }

    public void setMsmPassengerSeatCushExt(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSMP_SEAT_CUSH_EXT, values);
    }

    @Deprecated
    public void setMsmPassengerSeatCushExtPosition(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMP_SEATCUSHEXTPOS, 0, pos);
    }

    public void setMsmDriverSeatLegHorzPosition(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMD_SEAT_LEG_HORZ_POS, 0, pos);
    }

    public void setMsmPassengerSeatLegHorzPosition(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMP_SEAT_LEG_HORZ_POS, 0, pos);
    }

    @Deprecated
    public int getMsmPassengerSeatCushExtPosition() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMP_SEATCUSHEXTPOS, 0);
    }

    public int getMsmDriverSeatLegHorzPosition() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMD_SEAT_LEG_HORZ_POS, 0);
    }

    public int getMsmPassengerSeatLegHorzPosition() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMP_SEAT_LEG_HORZ_POS, 0);
    }

    public void setMsmPassengerSeatTitlLevelOff(int cmd) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMP_SEAT_TILT_LEVEL_OFF, 0, cmd);
    }

    public void setMsmSecrowLtSeatTiltReq(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.BCM_SECROW_LTSEAT_TILT, values);
    }

    public void setMsmSecrowLtSeatTiltPosition(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_SECROW_LTSEAT_TILTPOS, 0, pos);
    }

    public int getMsmSecrowLtSeatTiltPosition() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SECROW_LTSEAT_TILTPOS, 0);
    }

    @Deprecated
    public void setMsmSecrowLtSeatUnfoldReq(int cmd) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_SECROW_LTSEAT_UNFOLD_REQ, 0, cmd);
    }

    @Deprecated
    public void setMsmSecrowLtSeatSTopMoveReq(int cmd) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_SECROW_LTSEAT_STOP_MOVE_REQ, 0, cmd);
    }

    public void setMsmSecrowRtSeatTiltReq(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.BCM_SECROW_RTSEAT_TILT, values);
    }

    public void setMsmSecrowRtSeatTiltPosition(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_SECROW_RTSEAT_TILTPOS, 0, pos);
    }

    public int getMsmSecrowRtSeatTiltPosition() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SECROW_RTSEAT_TILTPOS, 0);
    }

    @Deprecated
    public void setMsmSecrowRtSeatSTopMoveReq(int cmd) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_SECROW_RTSEAT_STOP_MOVE_REQ, 0, cmd);
    }

    @Deprecated
    public void setMsmSecrowRTSeatUnfoldReq(int cmd) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_SECROW_RTSEAT_UNFOLD_REQ, 0, cmd);
    }

    public void setMsmPassengerWelcomeSwitch(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMP_MCU_WELCOME, 0, onOff);
    }

    public int getMsmPassengerWelcomeSwitch() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMP_MCU_WELCOME, 0);
    }

    public void setMsmDriverWelcomeActive(int active) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMD_MCU_WELCOME_ACTIVE, 0, active);
    }

    public void setMsmPassengerWelcomeActive(int active) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMP_MCU_WELCOME_ACTIVE, 0, active);
    }

    public void setMsmPassengerSeatLumbVerticalPos(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSMP_SEATLUMBVERTICALCMD, values);
    }

    public void setMsmPassengerSeatLumbHorzPos(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSMP_SEATLUMBHORZCMD, values);
    }

    @Deprecated
    public void setMsmSecRowLeftSeatCushExtReq(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.SECROW_LTSEAT_CUSHEXT_REQ, values);
    }

    public void setMsmSecRowLeftSeatLegHorzPosReq(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.SECROW_LTSEAT_LEG_HORZ_REQ, values);
    }

    @Deprecated
    public void setMsmSecRowRightSeatCushExtReq(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.SECROW_RTSEAT_CUSHEXT_REQ, values);
    }

    public void setMsmSecRowRightSeatLegHorzPosReq(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.SECROW_RTSEAT_LEG_HORZ_REQ, values);
    }

    @Deprecated
    public void setMsmSecRowLeftSeatCushExtPosition(int legHorzPos) {
        this.mPropertyService.setIntProperty(VehicleProperty.SECROW_LTSEAT_CUSHEXTPOS, 0, legHorzPos);
    }

    public void setMsmSecRowLeftSeatLegHorzPosition(int legHorzPos) {
        this.mPropertyService.setIntProperty(VehicleProperty.SECROW_LTSEAT_LEG_HORZ_POS, 0, legHorzPos);
    }

    @Deprecated
    public int getMsmSecRowLeftSeatCushExtPosition() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SECROW_LTSEAT_CUSHEXTPOS, 0);
    }

    public int getMsmSecRowLeftSeatLegHorzPosition() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SECROW_LTSEAT_LEG_HORZ_POS, 0);
    }

    @Deprecated
    public void setMsmSecRowRightSeatCushExtPosition(int legHorzPos) {
        this.mPropertyService.setIntProperty(VehicleProperty.SECROW_RTSEAT_CUSHEXTPOS, 0, legHorzPos);
    }

    public void setMsmSecRowRightSeatLegHorzPosition(int legHorzPos) {
        this.mPropertyService.setIntProperty(VehicleProperty.SECROW_RTSEAT_LEG_HORZ_POS, 0, legHorzPos);
    }

    @Deprecated
    public int getMsmSecRowRightSeatCushExtPosition() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SECROW_RTSEAT_CUSHEXTPOS, 0);
    }

    public int getMsmSecRowRightSeatLegHorzPosition() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SECROW_RTSEAT_LEG_HORZ_POS, 0);
    }

    public void setMsmSecrowRightSeatUnlockReq(int cmd) {
        this.mPropertyService.setIntProperty(VehicleProperty.SECROW_RTSEAT_UNLOCK, 0, cmd);
    }

    public void setMsmSecrowLeftSeatUnlockReq(int cmd) {
        this.mPropertyService.setIntProperty(VehicleProperty.SECROW_LTSEAT_UNLOCK, 0, cmd);
    }

    public void setMsmDriverSeatMassgProgMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMD_MASSG_PROG, 0, mode);
    }

    public int getMsmDriverSeatMassgProgMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMD_MASSG_PROG, 0);
    }

    public void setMsmDriverSeatMassgIntensity(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMD_MASSG_LVL, 0, level);
    }

    public int getMsmDriverSeatMassgIntensity() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMD_MASSG_LVL, 0);
    }

    public void setMsmPassengerSeatMassgProgMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMP_MASSG_PROG, 0, mode);
    }

    public int getMsmPassengerSeatMassgProgMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMP_MASSG_PROG, 0);
    }

    public void setMsmPassengerSeatMassgIntensity(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMP_MASSG_LVL, 0, level);
    }

    public int getMsmPassengerSeatMassgIntensity() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMP_MASSG_LVL, 0);
    }

    public void setMsmSecRowLeftSeatMassgProgMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.SECROW_LT_MASSG_PROG, 0, mode);
    }

    public int getMsmSecRowLeftSeatMassgProgMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SECROW_LT_MASSG_PROG, 0);
    }

    public void setMsmSecRowLeftSeatMassgIntensity(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.SECROW_LT_MASSG_LVL, 0, level);
    }

    public int getMsmSecRowLeftSeatMassgIntensity() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SECROW_LT_MASSG_LVL, 0);
    }

    public void setMsmSecRowRightSeatMassgProgMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.SECROW_RT_MASSG_PROG, 0, mode);
    }

    public int getMsmSecRowRightSeatMassgProgMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SECROW_RT_MASSG_PROG, 0);
    }

    public void setMsmSecRowRightSeatMassgIntensity(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.SECROW_RT_MASSG_LVL, 0, level);
    }

    public int getMsmSecRowRightSeatMassgIntensity() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SECROW_RT_MASSG_LVL, 0);
    }

    public void setMsmDriverSeatMassgElem(int[] cmd) {
        if (cmd.length != 20) {
            return;
        }
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSMD_MASSG_ELEM_CMD, cmd);
    }

    public int[] getMsmDriverSeatMassgElem() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.MSMD_MASSG_ELEM_CMD);
    }

    public void setMsmPassengerSeatMassgElem(int[] cmd) {
        if (cmd.length != 20) {
            return;
        }
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSMP_MASSG_ELEM_CMD, cmd);
    }

    public int[] getMsmPassengerSeatMassgElem() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.MSMP_MASSG_ELEM_CMD);
    }

    public void setMsmSecRowLeftSeatMassgElem(int[] cmd) {
        if (cmd.length != 20) {
            return;
        }
        this.mPropertyService.setIntVectorProperty(VehicleProperty.SECROW_LT_MASSG_ELEM_CMD, cmd);
    }

    public int[] getMsmSecRowLeftSeatMassgElem() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.SECROW_LT_MASSG_ELEM_CMD);
    }

    public void setMsmSecRowRightSeatMassgElem(int[] cmd) {
        if (cmd.length != 20) {
            return;
        }
        this.mPropertyService.setIntVectorProperty(VehicleProperty.SECROW_RT_MASSG_ELEM_CMD, cmd);
    }

    public int[] getMsmSecRowRightSeatMassgElem() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.SECROW_RT_MASSG_ELEM_CMD);
    }

    public void setMsmSecRowLeftSeatAllPos(int seatTiltingPos, int legHorzPos) {
        int[] values = {seatTiltingPos, legHorzPos};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.SECROW_LT_ALL_POS_SET, values);
    }

    public void setMsmSecRowRightSeatAllPos(int seatTiltingPos, int legHorzPos) {
        int[] values = {seatTiltingPos, legHorzPos};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.SECROW_RT_ALL_POS_SET, values);
    }

    public int getMsmDriverSeatLumberSwitchMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMD_LUM_SW_MODE, 0);
    }

    public int getMsmDriverSeatLumberSwitchCenterPressStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMD_LUM_SW_CENTER, 0);
    }

    public int getMsmPassengerSeatLumberSwitchMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMP_LUM_SW_MODE, 0);
    }

    public int getMsmPassengerSeatLumberSwitchCenterPressStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMP_LUM_SW_CENTER, 0);
    }

    public void setMsmDriverSeatPositionMemoryRequest(int cmd) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMD_MEMORY_REQ, 0, cmd);
    }

    public int getMsmDriverSeatMassgErrorStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMD_MASSG_ERR_ST, 0);
    }

    public int getMsmPassengerSeatMassgErrorStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMP_MASSG_ERR_ST, 0);
    }

    public int getMsmSecRowLeftSeatMassgErrorStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SECROW_LT_MASSG_ERR_ST, 0);
    }

    public int getMsmSecRowRightSeatMassgErrorStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SECROW_RT_MASSG_ERR_ST, 0);
    }

    public void setMsmDriverSeatLumbControlSwitchEnable(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMD_LUMB_CTRL_SW, 0, enable);
    }

    public void setMsmPassengerSeatLumbControlSwitchEnable(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMP_LUMB_CTRL_SW, 0, enable);
    }

    public void setSecRowLeftSeatPos(int memoryReq, int seatHorizonPos, int seatAngle, int seatTiltingPos, int legHeightPos, int legHorzPos, int HeadHeightPos, int HeadHorzPos) {
        int[] values = {memoryReq, seatHorizonPos, seatAngle, seatTiltingPos, legHeightPos, legHorzPos, HeadHeightPos, HeadHorzPos};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSM_SECROW_LTSEAT_ALLPOS_SET, values);
    }

    public int[] getSecRowLeftSeatPos() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.MSM_SECROW_LTSEAT_ALLPOS_SET);
    }

    public void setSecRowRightSeatPos(int memoryReq, int seatHorizonPos, int seatAngle, int seatTiltingPos, int legHeightPos, int legHorzPos, int HeadHeightPos, int HeadHorzPos) {
        int[] values = {memoryReq, seatHorizonPos, seatAngle, seatTiltingPos, legHeightPos, legHorzPos, HeadHeightPos, HeadHorzPos};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSM_SECROW_RTSEAT_ALLPOS_SET, values);
    }

    public int[] getSecRowRightSeatPos() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.MSM_SECROW_RTSEAT_ALLPOS_SET);
    }

    public void setTrdRowSeatAllPos(int ltSeatTiltingPos, int ltHeadHeightPos, int rtSeatTiltingPos, int rtHeadHeightPos, int midHeadHeightPos) {
        int[] values = {ltSeatTiltingPos, ltHeadHeightPos, rtSeatTiltingPos, rtHeadHeightPos, midHeadHeightPos};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSMT_ALLPOS_SET, values);
    }

    public int[] getTrdRowSeatAllPos() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.MSMT_ALLPOS_SET);
    }

    public void setSecRowLeftHorizMove(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSM_SECROW_LTSEAT_SEATHORZCMD, values);
    }

    public void setSecRowLeftAngleMove(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSM_SECROW_LTSEAT_SEATANGLECMD, values);
    }

    public void setSecRowLeftLegVertiMove(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSM_SECROW_LTSEAT_LEGVERTICALCMD, values);
    }

    public void setSecRowLeftHeadVertiMove(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSM_SECROW_LTSEAT_HEADVERTICALCMD, values);
    }

    public void setSecRowLeftHeadHorizMove(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSM_SECROW_LTSEAT_HEADHORZCMD, values);
    }

    public void setSecRowRightHorizMove(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSM_SECROW_RTSEAT_SEATHORZCMD, values);
    }

    public void setSecRowRighttAngleMove(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSM_SECROW_RTSEAT_SEATANGLECMD, values);
    }

    public void setSecRowRightLegVertiMove(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSM_SECROW_RTSEAT_LEGVERTICALCMD, values);
    }

    public void setSecRowRightHeadVertiMove(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSM_SECROW_RTSEAT_HEADVERTICALCMD, values);
    }

    public void setSecRowRightHeadHorizMove(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSM_SECROW_RTSEAT_HEADHORZCMD, values);
    }

    public void setTrdRowLeftSeatTiltMove(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSMT_LTSEAT_TILT, values);
    }

    public void setTrdRowLeftHeadVertiMove(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSMT_LTSEAT_HEADVERTICALCMD, values);
    }

    public void setTrdRowRightSeatTiltMove(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSMT_RTSEAT_TILT, values);
    }

    public void setTrdRowRightHeadVertiMove(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSMT_RTSEAT_HEADVERTICALCMD, values);
    }

    public void setTrdRowMiddleSeatTiltMove(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSMT_MIDSEAT_TILT, values);
    }

    public void setSecRowLeftSeatLumbVertiMove(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSM_SECROW_LTSEAT_LUMBVERTICALCMD, values);
    }

    public void setSecRowLeftSeatLumbHorzMove(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSM_SECROW_LTSEAT_LUMBHORZCMD, values);
    }

    public void setSecRowRightSeatLumbVertiMove(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSM_SECROW_RTSEAT_LUMBVERTICALCMD, values);
    }

    public void setSecRowRightSeatLumbHorzMove(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSM_SECROW_RTSEAT_LUMBHORZCMD, values);
    }

    public void setSecRowLeftSeatZeroGravReq(int ctrl) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSM_SECROW_LTSEAT_ZEROGRAV, 0, ctrl);
    }

    public void setSecRowRightSeatZeroGravReq(int ctrl) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSM_SECROW_RTSEAT_ZEROGRAV, 0, ctrl);
    }

    public void setSecRowLeftSeatEasyEntryReq(int ctrl) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSM_SECROW_LTSEAT_EASYENTRY, 0, ctrl);
    }

    public void setSecRowRightSeatEasyEntryReq(int ctrl) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSM_SECROW_RTSEAT_EASYENTRY, 0, ctrl);
    }

    public int getSecRowLeftSeatFuncSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSM_SECROW_LTSEAT_FUNCST, 0);
    }

    public int getSecRowRightSeatFuncSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSM_SECROW_RTSEAT_FUNCST, 0);
    }

    public void setMsmtLeftSeatFoldReq(int ctrl) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMT_LTSEAT_TILT_FOLD, 0, ctrl);
    }

    public int getMsmtLeftSeatFoldFunSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMT_LTSEAT_TILT_FOLD, 0);
    }

    public void setMsmtRightSeatFoldReq(int ctrl) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMT_RTSEAT_TILT_FOLD, 0, ctrl);
    }

    public int getMsmtRightSeatFoldFunSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMT_RTSEAT_TILT_FOLD, 0);
    }

    public void setMsmtSeatStowReq(int ctrl) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMT_SEAT_STOW, 0, ctrl);
    }

    public int getMsmtSeatStowFunSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMT_SEAT_STOW, 0);
    }

    public void stopSecRowLeftSeatMoving(int cmd) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSM_SECROW_LTSEAT_STOPMOVING, 0, cmd);
    }

    public void stopSecRowRightSeatMoving(int cmd) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSM_SECROW_RTSEAT_STOPMOVING, 0, cmd);
    }

    public void setSecRowLtSeatHorzPos(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSM_SECROW_LTSEAT_HORZPOS, 0, pos);
    }

    public int getSecRowLtSeatHorzPos() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSM_SECROW_LTSEAT_HORZPOS, 0);
    }

    public void setSecRowLtSeatAnglePos(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSM_SECROW_LTSEAT_ANGLEPOS, 0, pos);
    }

    public int getSecRowLtSeatAnglePos() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSM_SECROW_LTSEAT_ANGLEPOS, 0);
    }

    public void setSecRowLtSeatLegVerticalPos(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSM_SECROW_LTSEAT_LEGVERTICALPOS, 0, pos);
    }

    public int getSecRowLtSeatLegVerticalPos() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSM_SECROW_LTSEAT_LEGVERTICALPOS, 0);
    }

    public void setSecRowLtSeatHeadVerticalPos(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSM_SECROW_LTSEAT_HEADVERTICALPOS, 0, pos);
    }

    public int getSecRowLtSeatHeadVerticalPos() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSM_SECROW_LTSEAT_HEADVERTICALPOS, 0);
    }

    public void setSecRowLtSeatHeadHorzPos(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSM_SECROW_LTSEAT_HEADHORZPOS, 0, pos);
    }

    public int getSecRowLtSeatHeadHorzPos() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSM_SECROW_LTSEAT_HEADHORZPOS, 0);
    }

    public void setSecRowRtSeatHorzPos(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSM_SECROW_RTSEAT_HORZPOS, 0, pos);
    }

    public int getSecRowRtSeatHorzPos() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSM_SECROW_RTSEAT_HORZPOS, 0);
    }

    public void setSecRowRtSeatAnglePos(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSM_SECROW_RTSEAT_ANGLEPOS, 0, pos);
    }

    public int getSecRowRtSeatAnglePos() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSM_SECROW_RTSEAT_ANGLEPOS, 0);
    }

    public void setSecRowRtSeatLegVerticalPos(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSM_SECROW_RTSEAT_LEGVERTICALPOS, 0, pos);
    }

    public int getSecRowRtSeatLegVerticalPos() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSM_SECROW_RTSEAT_LEGVERTICALPOS, 0);
    }

    public void setSecRowRtSeatHeadVerticalPos(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSM_SECROW_RTSEAT_HEADVERTICALPOS, 0, pos);
    }

    public int getSecRowRtSeatHeadVerticalPos() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSM_SECROW_RTSEAT_HEADVERTICALPOS, 0);
    }

    public void setSecRowRtSeatHeadHorzPos(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSM_SECROW_RTSEAT_HEADHORZPOS, 0, pos);
    }

    public int getSecRowRtSeatHeadHorzPos() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSM_SECROW_RTSEAT_HEADHORZPOS, 0);
    }

    public void setTrdRowLtSeatTiltPos(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMT_LTSEAT_TILTPOS, 0, pos);
    }

    public int getTrdRowLtSeatTiltPos() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMT_LTSEAT_TILTPOS, 0);
    }

    public void setTrdRowLtSeatHeadVerticalPos(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMT_LTSEAT_HEADVERTICALPOS, 0, pos);
    }

    public int getTrdRowLtSeatHeadVerticalPos() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMT_LTSEAT_HEADVERTICALPOS, 0);
    }

    public void setTrdRowRtSeatTiltPos(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMT_RTSEAT_TILTPOS, 0, pos);
    }

    public int getTrdRowRtSeatTiltPos() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMT_RTSEAT_TILTPOS, 0);
    }

    public void setTrdRowRtSeatHeadVerticalPos(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMT_RTSEAT_HEADVERTICALPOS, 0, pos);
    }

    public int getTrdRowRtSeatHeadVerticalPos() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMT_RTSEAT_HEADVERTICALPOS, 0);
    }

    public void setTrdRowMidSeatHeadVerticalPos(int pos) {
        this.mPropertyService.setIntProperty(VehicleProperty.MSMT_MIDSEAT_HEADVERTICALPOS, 0, pos);
    }

    public int getTrdRowMidSeatHeadVerticalPos() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMT_MIDSEAT_HEADVERTICALPOS, 0);
    }

    public void setTrdRowMidHeadVertiMove(int control, int direction) {
        int[] values = {control, direction};
        this.mPropertyService.setIntVectorProperty(VehicleProperty.MSMT_MIDSEAT_HEADVERTICALCMD, values);
    }

    public int getTrdRowLeftSeatTiltState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMT_LTSEAT_TILT_ST, 0);
    }

    public int getTrdRowRightSeatTiltState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMT_RTSEAT_TILT_ST, 0);
    }

    public int getTrdRowSeatStowState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMT_SEAT_STOW_ST, 0);
    }

    public int getSecRowLtSeatState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSM_SECROW_LTSEA_ST, 0);
    }

    public int getSecRowRtSeatState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSM_SECROW_RTSEA_ST, 0);
    }

    public int getTrdRowLtSeatHeadMoveState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMT_LTSEAT_HEADMOVEST, 0);
    }

    public int getTrdRowMidSeatHeadMoveState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMT_MIDSEAT_HEADMOVEST, 0);
    }

    public int getTrdRowRtSeatHeadMoveState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.MSMT_RTSEAT_HEADMOVEST, 0);
    }

    public int getAmpMusicStyle() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AMP_STYLE, 0);
    }

    public void setAmpMusicStyle(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.AMP_STYLE, 0, type);
    }

    public int getAmpMusicScene() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AMP_SCENE, 0);
    }

    public void setAmpMusicScene(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.AMP_SCENE, 0, type);
    }

    public void setAmpStandByEnabled(int on) {
    }

    public int isAmpStandByEnabled() {
        return 0;
    }

    public int getAmpSoundFieldMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AMP_SOUNDFIELD, 0);
    }

    public void setAmpSoundFieldMode(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.AMP_SOUNDFIELD, 0, type);
    }

    public int getApmAudioEffect() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AMP_AUDIOAFFETSW, 0);
    }

    public void setApmAudioEffect(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.AMP_AUDIOAFFETSW, 0, type);
    }

    public void setAmpMute(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.AMP_MUTESW, 0, enable);
    }

    public int isAmpMute() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AMP_MUTESW, 0);
    }

    public int getAmpVolume() {
        return 0;
    }

    public void setAmpVolume(int level) {
    }

    public int getAmpSoundTrackMode() {
        return 0;
    }

    public void setAmpSoundTrackMode(int track) {
    }

    public void setAmpChannelVolAndSource(int channelbit, int volume, int soundSource, int activeBit) {
        int vol = volume / 3;
        if (vol > 31) {
            vol = 31;
        }
        byte[] data = new byte[8];
        data[0] = (byte) (vol & 255);
        data[1] = (byte) ((soundSource & 3) << 6);
        if ((activeBit & 1) != 1) {
            data[1] = (byte) (data[1] | 16);
        } else if ((channelbit & 1) == 1) {
            data[1] = (byte) (data[1] | 32);
        }
        if ((activeBit & 2) != 2) {
            data[1] = (byte) (data[1] | 4);
        } else if ((channelbit & 2) == 2) {
            data[1] = (byte) (data[1] | 8);
        }
        if ((activeBit & 4) != 4) {
            data[1] = (byte) (data[1] | 1);
        } else if ((channelbit & 4) == 4) {
            data[1] = (byte) (data[1] | 2);
        }
        if ((activeBit & 8) == 8) {
            if ((channelbit & 8) == 8) {
                data[2] = (byte) (data[2] | ByteCompanionObject.MIN_VALUE);
            }
        } else {
            data[2] = (byte) (data[2] | 64);
        }
        if ((activeBit & 16) != 16) {
            data[2] = (byte) (data[2] | 16);
        } else if ((channelbit & 16) == 16) {
            data[2] = (byte) (data[2] | 32);
        }
        if ((activeBit & 32) != 32) {
            data[2] = (byte) (data[2] | 4);
        } else if ((channelbit & 32) == 32) {
            data[2] = (byte) (data[2] | 8);
        }
        if ((activeBit & 64) != 64) {
            data[2] = (byte) (data[2] | 1);
        } else if ((channelbit & 64) == 64) {
            data[2] = (byte) (data[2] | 2);
        }
        if ((activeBit & 128) != 128) {
            data[3] = (byte) (data[3] | 64);
        } else if ((channelbit & 128) == 128) {
            data[3] = (byte) (data[3] | ByteCompanionObject.MIN_VALUE);
        }
        if ((activeBit & 256) != 256) {
            data[3] = (byte) (data[3] | 16);
        } else if ((channelbit & 256) == 256) {
            data[3] = (byte) (data[3] | 32);
        }
        if ((activeBit & 512) != 512) {
            data[3] = (byte) (data[3] | 4);
        } else if ((channelbit & 512) == 512) {
            data[3] = (byte) (8 | data[3]);
        }
        if ((activeBit & 1024) != 1024) {
            data[3] = (byte) (data[3] | 1);
        } else if ((channelbit & 1024) == 1024) {
            data[3] = (byte) (data[3] | 2);
        }
        if ((activeBit & 2048) != 2048) {
            data[4] = (byte) (data[4] | 64);
        } else if ((channelbit & 2048) == 2048) {
            data[4] = (byte) (data[4] | ByteCompanionObject.MIN_VALUE);
        }
        this.mPropertyService.setByteVectorProperty(VehicleProperty.AMP_BODY_CTRL, data);
    }

    public int[] getApmAllChannelVolume() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.AMP_ALLCHANNEL_VOLUME);
    }

    public int[] getApmAllChannelSoundSource() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.AMP_ALLCHANNEL_STATUS);
    }

    public void sendCduVolumeToAmp(int volume) {
        this.mPropertyService.setIntPropertyWithDefaultArea(VehicleProperty.AMP_VOULME_VALUE, volume);
    }

    public void setAmpPowerRequestSwitchStatus(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_AMP_POWER_REQ, 0, onOff);
    }

    public int getAmpPowerRequestSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_AMP_POWER_REQ, 0);
    }

    public void setAmpChannelSwitchControlStatus(byte[] sw) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.AMP_SW_CTRL, sw);
    }

    public void setAmpChannelVolumeControlValue(byte[] vol) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.AMP_VOL_CTRL, vol);
    }

    @Deprecated
    public void setAmpGroupSwitchControlStatus(byte[] sw) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.AMP_GROUP_SW_CTRL, sw);
    }

    @Deprecated
    public void setAmpGroupVolumeControlValue(byte[] vol) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.AMP_GROUP_VOL_CTRL, vol);
    }

    @Deprecated
    public void setAmpDolbyAtomsSwitchStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.AMP_DOLBY_ATOMS_SW, 0, status);
    }

    @Deprecated
    public void setAmpEffectStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.AMP_EFFECT_ST, 0, status);
    }

    @Deprecated
    public int getAmpEffectStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AMP_EFFECT_ST, 0);
    }

    @Deprecated
    public void setAmpDynSdvcLevel(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.AMP_DYN_SDVC_LEV, 0, level);
    }

    @Deprecated
    public int getAmpDynSdvcLevel() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AMP_DYN_SDVC_LEV, 0);
    }

    public void setAmpDyn3DEffectLevel(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.AMP_DYN_3D_EFFECT, 0, level);
    }

    public int getAmpDyn3DEffectLevel() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AMP_DYN_3D_EFFECT, 0);
    }

    public void setAmpSdsscLevel(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.AMP_SDSSC_LEV_SW, 0, level);
    }

    public int getAmpSdsscLevel() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AMP_SDSSC_LEV_SW, 0);
    }

    public void setAmpSoundSourceDolbyFormat(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.AMP_SOUND_SOURCE_DOLBY_ST, 0, type);
    }

    public int getAmpSoundSourceDolbyFormat() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AMP_SOUND_SOURCE_DOLBY_ST, 0);
    }

    public void setAmpSoundStyle(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.AMP_SOUND_STYLE_SW, 0, type);
    }

    public int getAmpSoundStyle() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AMP_SOUND_STYLE_SW, 0);
    }

    public void setAmpFreqGainGroupControlValue(int[] eqValue) {
        if (eqValue.length != 10) {
            return;
        }
        this.mPropertyService.setIntVectorProperty(VehicleProperty.AMP_FREQ_GAIN_GROUP_CTRL, eqValue);
    }

    public int[] getAmpFreqGainGroupControlValue() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.AMP_FREQ_GAIN_GROUP_CTRL);
    }

    public int getAmpA2BLinkStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.AMP_A2B_LINK_STATUS, 0);
    }

    public int getCdcFunctionMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CDC_FUNC_STYLE, 0);
    }

    public void setCdcFunctionMode(int style) {
        this.mPropertyService.setIntProperty(VehicleProperty.CDC_FUNC_STYLE, 0, style);
    }

    public void setMsbEnabled(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_MSB_ACTIVE, 0, enable);
    }

    public int isMsbEnabled() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_MSB_ACTIVE, 0);
    }

    public void setDhcDoorknobAutoOpenEnabled(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.DHC_ACTIVE_SW, 0, enable);
    }

    public int isDhcDoorknobAutoOpenEnabled() {
        return this.mPropertyService.getIntProperty(VehicleProperty.DHC_ACTIVE_SW, 0);
    }

    public void setSrsBackBeltWarningEnabled(int on) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_2ND_BELT_WARNING, 0, on);
    }

    public int isSrsBackBeltWarningEnabled() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_2ND_BELT_WARNING, 0);
    }

    public int isSrsPsnOnSeat() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SRS_PSNGR_OCCUPANCY_ST, 0);
    }

    public int getSrsDrvBeltFastenStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_DRVSEAT_BELTSBR_WARNING, 0);
    }

    public int getSrsPsnBeltFastenStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_PSNGRSEAT_BELTSBR_WARNING, 0);
    }

    public int getSrsBackLeftBeltFastenStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_2NDLEFTSEAT_BELTSBR_WARNING, 0);
    }

    public int getSrsBackMiddleBeltFastenStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_2NDMIDSEAT_BELTSBR_WARNING, 0);
    }

    public int getSrsBackRightBeltFastenStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_2NDRIGHTSEAT_BELTSBR_WARNING, 0);
    }

    public int getSrsCrashOutputStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_CRASH_OUTPUT_ST, 0);
    }

    public int[] getAllSrsCrashOutputStatus() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.BCM_ALL_CRASH_OUTPUT_ST);
    }

    public int getSrsAirbagFaultStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_AIRBAG_FAULT_ST, 0);
    }

    public int getSrsSelfCheckStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SELF_CHECK, 0);
    }

    public int getSrsRearLeftSeatOccupancyStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SRS_RLSEAT_OCCUPANCY_ST, 0);
    }

    public int getSrsRearMiddleSeatOccupancyStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SRS_RMSEAT_OCCUPANCY_ST, 0);
    }

    public int getSrsRearRightSeatOccupancyStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SRS_RRSEAT_OCCUPANCY_ST, 0);
    }

    public int getSrsPassengerCrashOccurSwSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.BCM_SRS_PSNGR_CRASH_OCCUR, 0);
    }

    public void setSrsPassengerCrashOccurSw(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.BCM_SRS_PSNGR_CRASH_OCCUR, 0, sw);
    }

    public int getVpmLdwLeftWarningStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VPM_LDW_LEFT_WARNING_ST, 0);
    }

    public int getVpmLdwRightWarningStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VPM_LDW_RIGHT_WARNING_ST, 0);
    }

    public int getVpmRdpLeftWarningStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VPM_RDP_LEFT_WARNING_ST, 0);
    }

    public int getVpmRdpRightWarningStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VPM_RDP_RIGHT_WARNING_ST, 0);
    }

    public int getCiuDmsStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CIU_DMS_SW, 0);
    }

    public void setCiuDmsStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.CIU_DMS_SW, 0, status);
    }

    public void setCiuFaceIdMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.CIU_FACE_ID_MODE, 0, mode);
    }

    public int getCiuUid() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CIU_UID, 0);
    }

    public void setCiuUid(int uid) {
        this.mPropertyService.setIntProperty(VehicleProperty.CIU_UID, 0, uid);
    }

    public int getCiuFaceIdStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CIU_FACE_ID_STATUS, 0);
    }

    public int getCiuFaceIdPrimalStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CIU_FACE_ID_PRIMAL_STATUS, 0);
    }

    public int getCiuFaceShieldStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CIU_FACE_SHIELD, 0);
    }

    public int getCiuErrorType() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CIU_ERROR_TYPE, 0);
    }

    public int getCiuLightIntensity() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CIU_LIGHT_INTENSITY, 0);
    }

    public int getCiuFaceIDSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CIU_FACE_ID_SW, 0);
    }

    public void setCiuFaceIdSwitch(int value) {
        this.mPropertyService.setIntProperty(VehicleProperty.CIU_FACE_ID_SW, 0, value);
    }

    public void setCiuDeleteFaceId(int deleteFaceId) {
        this.mPropertyService.setIntProperty(VehicleProperty.CIU_DELETE_FACE_ID, 0, deleteFaceId);
    }

    public int getCiuDeleteFaceIdResult() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CIU_DELETE_FACE_ID, 0);
    }

    public void setCiuRegHint(int hint) {
        this.mPropertyService.setIntProperty(VehicleProperty.CIU_REG_HINT, 0, hint);
    }

    public void setCiuStartRegFlow(int flow) {
        this.mPropertyService.setIntProperty(VehicleProperty.CIU_START_REG_FLOW, 0, flow);
    }

    public void setCiuStartRegFlag(int flag) {
        this.mPropertyService.setIntProperty(VehicleProperty.CIU_START_REG_FLAG, 0, flag);
    }

    public int getCiuFaceAction() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CIU_FACEACTION, 0);
    }

    public void setCiuFaceActionRequest(int action) {
        this.mPropertyService.setIntProperty(VehicleProperty.CIU_FACEACTION, 0, action);
    }

    public void setCiuFirmFaceCancel(int value) {
        this.mPropertyService.setIntProperty(VehicleProperty.CIU_PRIM_FACE_CANCEL, 0, value);
    }

    public void setCiuRegisterRequestMulti(int uid, int faceActionRequest, int faceIdMode) {
        LinkedList<CarPropertyValue> multiPropertyList = new LinkedList<>();
        CarPropertyValue<Integer> uidValue = new CarPropertyValue<>((int) VehicleProperty.CIU_UID, Integer.valueOf(uid));
        CarPropertyValue<Integer> faceActionRequestValue = new CarPropertyValue<>((int) VehicleProperty.CIU_FACEACTION, Integer.valueOf(faceActionRequest));
        CarPropertyValue<Integer> faceIdModeValue = new CarPropertyValue<>((int) VehicleProperty.CIU_FACE_ID_MODE, Integer.valueOf(faceIdMode));
        multiPropertyList.add(uidValue);
        multiPropertyList.add(faceActionRequestValue);
        multiPropertyList.add(faceIdModeValue);
        this.mPropertyService.setMultiProperties(multiPropertyList);
    }

    public void setCiuDeleteMulti(int uid, int deleteFaceId) {
        LinkedList<CarPropertyValue> multiPropertyList = new LinkedList<>();
        CarPropertyValue<Integer> uidValue = new CarPropertyValue<>((int) VehicleProperty.CIU_UID, Integer.valueOf(uid));
        CarPropertyValue<Integer> deleteFaceIdValue = new CarPropertyValue<>((int) VehicleProperty.CIU_DELETE_FACE_ID, Integer.valueOf(deleteFaceId));
        multiPropertyList.add(uidValue);
        multiPropertyList.add(deleteFaceIdValue);
        this.mPropertyService.setMultiProperties(multiPropertyList);
    }

    public int getCiuValid() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CIU_VALID, 0);
    }

    public int getCiuAutoLockSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CIU_AUTOLK_ST, 0);
    }

    public int getCiuDvrMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CIU_DVR_MODE, 0);
    }

    public void setCiuDvrMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.CIU_DVR_MODE, 0, mode);
    }

    public void setCiuPhotoProcess() {
        this.mPropertyService.setIntProperty(VehicleProperty.CIU_PHOTO_PROCESS, 0, 1);
    }

    public void setCiuDvrLockMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.CIU_DVR_LOCK, 0, mode);
    }

    public void setCiuVideoOutputMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.CIU_VIDEO_OUTPUT, 0, mode);
    }

    public int getCiuSdStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CIU_SD_ST, 0);
    }

    public int getCiuDvrStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CIU_DVR_STATUS, 0);
    }

    public void setCiuFormatMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.CIU_FORMAT_SD, 0, mode);
    }

    public int getCiuDvrFormatStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CIU_FORMAT_SD, 0);
    }

    public int getCiuDvrLockFb() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CIU_DVR_LOCK_FB, 0);
    }

    public void setCiuRainSw(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.CIU_RAIN_SW, 0, level);
    }

    public int getCiuRainSw() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CIU_RAIN_SW, 0);
    }

    public int getCiuCarWash() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CIU_CAR_WASH, 0);
    }

    public void setCiuCarWash(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.CIU_CAR_WASH, 0, status);
    }

    public int getCiuDistractionStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CIU_DISTRACTION_ST, 0);
    }

    public void setCiuDistractionStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.CIU_DISTRACTION_ST, 0, status);
    }

    public int getCiuFatigueStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CIU_FATIG_ST, 0);
    }

    public void setCiuFatigueStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.CIU_FATIG_ST, 0, status);
    }

    public void setCiuDmsMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.CIU_DMS_MODE_ST, 0, mode);
    }

    public int getCiuFatigueLevel() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CIU_FATG_LVL, 0);
    }

    public int getCiuDistractionLevel() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CIU_DIST_LVL, 0);
    }

    @Deprecated
    public void registerScuListener(IScuEventListener listener) {
        this.mPropertyService.registerScuListener(listener);
    }

    @Deprecated
    public void unregisterScuListener(IScuEventListener listener) {
        this.mPropertyService.unregisterScuListener(listener);
    }

    @Deprecated
    public void registerVcuListener(IVcuEventListener listener) {
        this.mPropertyService.registerVcuListener(listener);
    }

    @Deprecated
    public void unregisterVcuListener(IVcuEventListener listener) {
        this.mPropertyService.unregisterVcuListener(listener);
    }

    @Deprecated
    public void registerEpsListener(IEpsEventListener listener) {
        this.mPropertyService.registerEpsListener(listener);
    }

    @Deprecated
    public void unregisterEpsListener(IEpsEventListener listener) {
        this.mPropertyService.unregisterEpsListener(listener);
    }

    public void setMultipleDmsStatus(int dmsStatus, int faceIdStatus, int fatigueStatus, int distractionStatus) {
        LinkedList<CarPropertyValue> multiPropertyList = new LinkedList<>();
        CarPropertyValue<Integer> dmsStatusValue = new CarPropertyValue<>((int) VehicleProperty.CIU_DMS_SW, Integer.valueOf(dmsStatus));
        CarPropertyValue<Integer> faceIdStatusValue = new CarPropertyValue<>((int) VehicleProperty.CIU_FACE_ID_SW, Integer.valueOf(faceIdStatus));
        CarPropertyValue<Integer> fatigueStatusValue = new CarPropertyValue<>((int) VehicleProperty.CIU_FATIG_ST, Integer.valueOf(fatigueStatus));
        CarPropertyValue<Integer> distractionStatusValue = new CarPropertyValue<>((int) VehicleProperty.CIU_DISTRACTION_ST, Integer.valueOf(distractionStatus));
        multiPropertyList.add(dmsStatusValue);
        multiPropertyList.add(faceIdStatusValue);
        multiPropertyList.add(fatigueStatusValue);
        multiPropertyList.add(distractionStatusValue);
        this.mPropertyService.setMultiProperties(multiPropertyList);
    }

    public void setDvrEnable(int enable) {
        this.mPropertyService.setIntProperty(VehicleProperty.CIU_DVR_SWITCH, 0, enable);
    }

    public int getDvrEnableState() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CIU_DVR_SWITCH, 0);
    }

    public void setNotifyCiuAutoLightStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.CIU_LIGHT_CHANGE, 0, status);
    }

    public void setCiuConfigurationActive(int version) {
        this.mPropertyService.setIntProperty(VehicleProperty.CIU_CONFIG_ACTIVATE, 0, version);
    }

    public int getCiuConfigurationActive() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CIU_CONFIG_ACTIVATE, 0);
    }

    public void setCiuDelayOff(int value) {
        this.mPropertyService.setIntProperty(VehicleProperty.CIU_DELAY_OFF, 0, value);
    }

    public int getCiuDelayOff() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CIU_DELAY_OFF, 0);
    }

    public void setCiuDeliveryUploadMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.CIU_DELIVERY_CONFIG, 0, mode);
    }

    public int getCiuDeliveryUploadMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CIU_DELIVERY_CONFIG, 0);
    }

    public int getCiuStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.CIU_STATUS, 0);
    }

    public float[] getImuSystemState() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.IMU_SYSST);
    }

    public float[] getImuQuatData() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.IMU_QUAT);
    }

    public float[] getImuUbxPvtData1() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.IMU_UBXPVT1);
    }

    public float[] getImuUbxPvtData2() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.IMU_UBXPVT2);
    }

    public float[] getImuUbxRawXData() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.IMU_UBXRAWX);
    }

    public int[] getImuTboxPackGgaData() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.IMU_TBOX_PACKGGA);
    }

    public long[] getImuAddData() {
        return this.mPropertyService.getLongVectorProperty(VehicleProperty.IMU_ADDDATA);
    }

    public int[] getImuDiagMessage() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.IMU_DIAGMSG);
    }

    public float[] getImuNavigationData() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.IMU_NAVDATA);
    }

    public float[] getImuUbxSfrbxData() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.IMU_UBXSFRBX);
    }

    public int getImuSatellitesNumber() {
        return this.mPropertyService.getIntProperty(VehicleProperty.IMU_UBXPVT1_NUM_SV, 0);
    }

    public int getImuNavigationSatellitesRssi() {
        return this.mPropertyService.getIntProperty(VehicleProperty.IMU_UBX_PVT2_RSSI, 0);
    }

    public float[] getImuSystemStateAndSpeed() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.IMU_SCULOCAT_CARSPEED);
    }

    public float[] getImuSystemStateFromCan() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.IMU_SYSST_CAN);
    }

    public float[] getImuNavigationDataFromCan() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.IMU_NAVDATA_CAN);
    }

    public float[] getImuSystemStateAndSpeedFromCan() {
        return this.mPropertyService.getFloatVectorProperty(VehicleProperty.IMU_SCULOCAT_CARSPEED_CAN);
    }

    public void sendXpuUpdateRequest(String req) {
        this.mPropertyService.setStringProperty(VehicleProperty.XPU_UPDATE_REQ, req);
    }

    public String getXpuUpdateResponse() {
        return this.mPropertyService.getStringProperty(VehicleProperty.XPU_UPDATE_REQ);
    }

    public void setXpuUpdateFileTransferStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.XPU_UPDATE_TRANS, 0, status);
    }

    public int getXpuUpdateResult() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_UPDATE_RESULT, 0);
    }

    public int getXpuUpdateProgress() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_UPDATE_PROGRESS, 0);
    }

    public int getXpuConnectionStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_CONNECTED, 0);
    }

    public void setXpuNedcSwitch(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.XPU_NEDC_STATUS, 0, onOff);
    }

    public void setXpuLightChange(int onOff) {
        this.mPropertyService.setIntProperty(VehicleProperty.XPU_LIGHTCHANGE, 0, onOff);
    }

    public void setXpuCduBrightness(int brightness) {
        this.mPropertyService.setIntPropertyWithDefaultArea(VehicleProperty.CDU_BRIGHTNESS_ST, brightness);
    }

    public int getXpuBrightness() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_CDU_BRIGHTNESS_ST, 0);
    }

    public void setXpuScpSwitchStatus(int sw) {
        this.mPropertyService.setIntPropertyWithDefaultArea(VehicleProperty.XPU_SCP_SW, sw);
    }

    public int getXpuScpSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_SCP_SW, 0);
    }

    public void setXpuRaebSwitchStatus(int sw) {
        this.mPropertyService.setIntPropertyWithDefaultArea(VehicleProperty.SCU_RAEB_SW, sw);
    }

    public int getXpuRaebSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SCU_RAEB_SW, 0);
    }

    public int getXpuNedcSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_NEDC_STATUS, 0);
    }

    public void setXpuApRemoteSw(int sw) {
        this.mPropertyService.setIntPropertyWithDefaultArea(VehicleProperty.XPU_AP_REMOTE_SW, sw);
    }

    public int getXpuApRemoteSw() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_AP_REMOTE_SW, 0);
    }

    public void setXpuNaviTypeStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.CDU_XPU_NAVI_TYPE, 0, status);
    }

    public int getXpuRaebActiveStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_RAEB_ACTIVE_ST, 0);
    }

    public void sendXpuTransferVpaCmd(String cmd) {
        this.mPropertyService.setStringProperty(VehicleProperty.XPU_TRANSFER_VPA_CMD, cmd);
    }

    public void sendXpuTransferVpaAbInfo(String info) {
        this.mPropertyService.setStringProperty(VehicleProperty.XPU_TRANSFER_VPA_AB_INFO, info);
    }

    public void setXpuNraSwitchStatus(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.XPU_NRA_BTN, 0, sw);
    }

    public int getXpuNraSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_NRA_BTN, 0);
    }

    public int getXpuNraControlStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_NRA_CTRL, 0);
    }

    public void setXpuCityNgpSwitchStatus(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.XPU_CNGP_SW, 0, sw);
    }

    public int getXpuCityNgpSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_CNGP_SW, 0);
    }

    public void sendXpuGeoFencingConfig(String config) {
        this.mPropertyService.setStringProperty(VehicleProperty.XPU_GEO_FENCING_CONFIG, config);
    }

    public void sendXpuCountryCodeInfo(String info) {
        this.mPropertyService.setStringProperty(VehicleProperty.XPU_COUNTRY_CODE_INFO, info);
    }

    public void setXpuSlifSoundStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.XPU_SLIF_SOUND_SW, 0, status);
    }

    public int getXpuSlifSoundStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_SLIF_SOUND_SW, 0);
    }

    public void setXpuSlwfVoiceStatus(int status) {
        this.mPropertyService.setIntProperty(VehicleProperty.XPU_SLWF_VOICE_SW, 0, status);
    }

    public int getXpuSlwfVoiceStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_SLWF_VOICE_SW, 0);
    }

    public int getXpuNgpSwitchTransitionStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_NGP_SWITCH_TRANSITION, 0);
    }

    public int[] getXpuHeadPoseData() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.XPU_HEADPOSE_DAT);
    }

    public int getXpuHmiDopRemind() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_HMI_DOP_REMIND, 0);
    }

    public void setXpuNgpULCSwMode(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.XPU_NGP_ULC_SW, 0, mode);
    }

    public int getXpuNgpULCSwMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_NGP_ULC_SW, 0);
    }

    public void setXpuNgpOptimalLaneSw(int lane) {
        this.mPropertyService.setIntProperty(VehicleProperty.XPU_NGP_OPTIMAL_LANE_SW, 0, lane);
    }

    public int getXpuNgpOptimalLaneSw() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_NGP_OPTIMAL_LANE_SW, 0);
    }

    public void setXpuISLCDriverSet(int mode) {
        this.mPropertyService.setIntProperty(VehicleProperty.XPU_ISLC_DRIVER_SET, 0, mode);
    }

    public int getXpuISLCDriverSet() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_ISLC_DRIVER_SET, 0);
    }

    public int getXpuNgpModeIndexMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_NGP_MODE_INDX, 0);
    }

    public int getXpuModeIndexDefine() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_MODE_INDX, 0);
    }

    public int getXpuIntelligentChargePortTipsType() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_SCP_TIPS, 0);
    }

    public int getXpuIntelligentChargePortTtsBroadcastType() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_SCP_TTS, 0);
    }

    public int getXpuIntelligentChargePortSystemToneType() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_SCP_TONE, 0);
    }

    public int getXpuAutoParkingTipsType() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_AP_TIPS, 0);
    }

    public int getXpuAsLockScenario() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_AS_LOCK_SCENARIO, 0);
    }

    public int getXpuIntelligentChargePortSystemStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_SCP_ST, 0);
    }

    public int getXpuSlaSpeedWarningStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_SLA_WARNING_ST, 0);
    }

    public int getXpuScpChargePortCommandStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_SCP_CHRG_PORT_CMD, 0);
    }

    @Deprecated
    public int getXpuXmartPigeonMode() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XMART_PIGEON_MODE, 0);
    }

    public void setXpuElectricEyeSpeedLimit(int speedlimit) {
        this.mPropertyService.setIntProperty(VehicleProperty.XPU_ELEEYE_RANGE_SPD_LMT, 0, speedlimit);
    }

    public void setXpuElectricEyeSpeedDistance(int distance) {
        this.mPropertyService.setIntProperty(VehicleProperty.XPU_ELEEYE_SPD_DIS, 0, distance);
    }

    public void setXpuMetaCountryCode(int code) {
        this.mPropertyService.setIntProperty(VehicleProperty.XPU_META_COUNTRYCODE, 0, code);
    }

    public void setXpuMetaSpeedUnits(int unit) {
        this.mPropertyService.setIntProperty(VehicleProperty.XPU_META_SPD_UNITS, 0, unit);
    }

    public void setXpuEffectiveSpeedLimitType(int type) {
        this.mPropertyService.setIntProperty(VehicleProperty.XPU_EFF_SPD_LMTTYPE, 0, type);
    }

    public int getXpuAsTargetMinimumHeightRequest() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_AS_TAR_LVL_MIN_REQ, 0);
    }

    public int getXpuAsTargetMaximumHeightRequest() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_AS_TAR_LVL_MAX_REQ, 0);
    }

    public void sendPigeonAndXPURemoteAPHeartBeat(String beat) {
        this.mPropertyService.setStringProperty(VehicleProperty.XPU_PHONE_REMOTE_AP_HEART_BEAT, beat);
    }

    public void sendPhoneRemoteAPEvent(String event) {
        this.mPropertyService.setStringProperty(VehicleProperty.XPU_PHONE_REMOTE_AP_EVENT, event);
    }

    public byte[] getPhoneRemoteAPInformation() {
        return this.mPropertyService.getByteVectorProperty(VehicleProperty.XPU_PHONE_REMOTE_AP_INFO);
    }

    public byte[] getXpuLongLatPeriodData() {
        return this.mPropertyService.getByteVectorProperty(VehicleProperty.XPU_LONG_LAT_PERIOD_DATA);
    }

    public void setXpuAdasTopSpeedLimitedValue(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.XPU_ADAS_TOP_SPEED, 0, sw);
    }

    public void setXpuLssSensitivitySwitchStatus(int level) {
        this.mPropertyService.setIntProperty(VehicleProperty.XPU_LSS_SEN_SW, 0, level);
    }

    public int getXpuLssSensitivitySwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_LSS_SEN_SW, 0);
    }

    public void sendXpuZgEventMessage(byte[] message) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.XPU_ZG_EVENT_MSG, message);
    }

    public void sendXpuZgPeriodMessage(byte[] message) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.XPU_ZG_PERIOD_MSG, message);
    }

    public void setXpuRadarEmissionSwitchStatus(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.XPU_RADAR_EMISSION_SW, 0, sw);
    }

    public void sendXpuScpGeoInfo(String info) {
        this.mPropertyService.setStringProperty(VehicleProperty.XPU_SCP_GEO_INFO, info);
    }

    public int getXpuDriverHeadFaceArea() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_HEADPOSE_FACE_AREA, 0);
    }

    public void setXpuLLCCDetourSw(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.XPU_LCC_L_DETOUR_SW, 0, sw);
    }

    public int getXpuLLCCDetourSw() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_LCC_L_DETOUR_SW, 0);
    }

    public void sendCNGPCityMapCtrlReq(byte[] req) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.XPU_CNGP_CITY_MAP_CTRL, req);
    }

    public byte[] getCNGPCityMapCtrlResp() {
        return this.mPropertyService.getByteVectorProperty(VehicleProperty.XPU_CNGP_CITY_MAP_CTRL);
    }

    public byte[] getXpuVehLocationProto() {
        return this.mPropertyService.getByteVectorProperty(VehicleProperty.XPU_VEH_LOCATION_PROTO);
    }

    public void setXpuLccLStraightSw(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.XPU_LCC_L_STRAIGHT_SW, 0, sw);
    }

    public int getXpuLccLStraightSw() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_LCC_L_STRAIGHT_SW, 0);
    }

    public void setXpuNaviRemainingDistance(int distance) {
        this.mPropertyService.setIntProperty(VehicleProperty.XPU_NAVI_REM_DIST, 0, distance);
    }

    public byte[] getXpuNaviConfirmProto() {
        return this.mPropertyService.getByteVectorProperty(VehicleProperty.XPU_NAVI_CONFIRM_PROTO);
    }

    public void sendSRHeartBeatInfoRequest(String beat) {
        this.mPropertyService.setStringProperty(VehicleProperty.HOST_XPU_SR_HEART_BEAT, beat);
    }

    public void sendSRLagLogHeartBeatFeedBackRequest(String info) {
        this.mPropertyService.setStringProperty(VehicleProperty.XPU_SR_HEART_BEAT, info);
    }

    public String getSRHeartBeatInfo() {
        return this.mPropertyService.getStringProperty(VehicleProperty.HOST_XPU_SR_HEART_BEAT);
    }

    public void setXpuNgpCustomSpeedSwitchStatus(int sw) {
        this.mPropertyService.setIntProperty(VehicleProperty.XPU_NGP_CUSTOM_SPD_SW, 0, sw);
    }

    public int getXpuNgpCustomSpeedSwitchStatus() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_NGP_CUSTOM_SPD_SW, 0);
    }

    public void setXpuNgpCustomSpeedCountLever(int lever) {
        this.mPropertyService.setIntProperty(VehicleProperty.XPU_NGP_CUSTOM_SPD_COUNT1, 0, lever);
    }

    public int getXpuNgpCustomSpeedCountLever() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_NGP_CUSTOM_SPD_COUNT1, 0);
    }

    public void setXpuNgpCustomSpeedCountPercent(int percent) {
        this.mPropertyService.setIntProperty(VehicleProperty.XPU_NGP_CUSTOM_SPD_COUNT2, 0, percent);
    }

    public int getXpuNgpCustomSpeedCountPercent() {
        return this.mPropertyService.getIntProperty(VehicleProperty.XPU_NGP_CUSTOM_SPD_COUNT2, 0);
    }

    public int[] getSwsButtonsRawData() {
        return this.mPropertyService.getIntVectorProperty(VehicleProperty.SWS_BUTTON_RAWDATA);
    }

    public int getSpcSolarWorkSt() {
        return this.mPropertyService.getIntProperty(VehicleProperty.SPC_SOLAR_WORK_ST, 0);
    }

    public float getSpcGrossEnergyGeneration() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.SPC_GROSS_GENERATION, 0);
    }

    public float getSpcRecentEnergyGeneration() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.SPC_RECENT_GENERATED_ENERGY, 0);
    }

    public float getSpcSolarPower() {
        return this.mPropertyService.getFloatProperty(VehicleProperty.SPC_SOLAR_POWER, 0);
    }

    /* loaded from: classes3.dex */
    private class OutMessage {
        public byte[] Data = new byte[XpVehicleService.mDataLen];

        public OutMessage() {
        }

        public void setData(byte[] data) {
            this.Data = data;
        }

        public byte[] getData() {
            return this.Data;
        }
    }

    /* loaded from: classes3.dex */
    public class OutPutHandler extends Handler {
        public OutPutHandler(Looper looper) {
            super(looper);
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            Bundle bundleData = msg.getData();
            byte[] lightdata = bundleData.getByteArray("lightData");
            Slog.i(XpVehicleService.TAG, "sendMessage [0]:" + ((int) lightdata[0]) + " [1]:" + ((int) lightdata[1]) + " [2]:" + ((int) lightdata[2]) + " [3]:" + ((int) lightdata[3]));
            byte[] sendData = new byte[XpVehicleService.mDataLen];
            sendData[0] = (byte) ((XpVehicleService.mCanID & MotionEventCompat.ACTION_POINTER_INDEX_MASK) >> 8);
            sendData[1] = (byte) (XpVehicleService.mCanID & 255);
            System.arraycopy(lightdata, 0, sendData, 2, XpVehicleService.mDataLen - 2);
            OutMessage oMsg = new OutMessage();
            oMsg.setData(sendData);
            synchronized (XpVehicleService.this.lock) {
                if (XpVehicleService.this.messageArray.size() >= 6) {
                    XpVehicleService.this.messageArray.remove(0);
                }
                XpVehicleService.this.messageArray.add(oMsg);
                Slog.i(XpVehicleService.TAG, "ArraySize:" + XpVehicleService.this.messageArray.size() + "  messageArray:");
            }
        }
    }

    void init_SocketServer() {
        openSocketThread();
    }

    void destroy_SocketServer() {
        closeSocketThread();
    }

    private void openSocketThread() {
        Slog.i(TAG, "try to openSocketThread()");
        if (this.serverThread != null) {
            Slog.i(TAG, "SocketThread already created");
            return;
        }
        this.serverThread = new ServerThread();
        this.serverThread.start();
        this.handlethread = new HandlerThread("handler thread");
        this.handlethread.start();
        this.outHandler = new OutPutHandler(this.handlethread.getLooper());
        this.serverThread.setIsLoop(true);
    }

    private void closeSocketThread() {
        Slog.i(TAG, "closeSocketThread()");
        ServerThread serverThread = this.serverThread;
        if (serverThread != null) {
            serverThread.setIsLoop(false);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void closeAll() {
        try {
            if (this.socket != null) {
                this.socket.close();
            }
            if (this.serverSocket != null) {
                this.serverSocket.close();
            }
            this.serverSocket = null;
            this.socket = null;
            this.inputStream = null;
            this.outputStream = null;
            this.outHandler = null;
            this.serverThread = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes3.dex */
    public class ServerThread extends Thread {
        boolean isLoop = true;
        boolean isWaiting = true;

        ServerThread() {
        }

        public void setIsLoop(boolean isLoop) {
            this.isLoop = isLoop;
            this.isWaiting = isLoop;
        }

        public void setPipBroken() {
            this.isWaiting = false;
        }

        public void setFirstConnection(DataOutputStream oStream) {
            Slog.i(XpVehicleService.TAG, "setFirstConnection !! ");
            byte[] msgData = {0, (byte) (XpVehicleService.mBotRate & 255)};
            if (oStream != null) {
                try {
                    XpVehicleService.this.outputStream.write(msgData);
                    Slog.i(XpVehicleService.TAG, "setFirstConnection  outputStream [0]:" + ((int) msgData[0]) + " [1]:" + ((int) msgData[1]));
                } catch (Exception e) {
                    Slog.i(XpVehicleService.TAG, "outputStream.write error:" + e);
                    if (XpVehicleService.this.serverThread != null) {
                        XpVehicleService.this.serverThread.setPipBroken();
                    }
                }
            }
        }

        public void setStartAtl(DataOutputStream oStream) {
            for (int i = 0; i < 5; i++) {
                SystemClock.sleep(100L);
                Slog.i(XpVehicleService.TAG, "setStartAtl !! ");
                byte[] msgData = new byte[10];
                msgData[0] = 1;
                msgData[1] = 23;
                msgData[2] = ByteCompanionObject.MIN_VALUE;
                if (oStream != null) {
                    try {
                        XpVehicleService.this.outputStream.write(msgData);
                        Slog.i(XpVehicleService.TAG, "setStartAtl  outputStream [0]:" + ((int) msgData[0]) + " [1]:" + ((int) msgData[1]) + " [2]:" + ((int) msgData[2]));
                    } catch (Exception e) {
                        Slog.i(XpVehicleService.TAG, "outputStream.write error:" + e);
                        if (XpVehicleService.this.serverThread != null) {
                            XpVehicleService.this.serverThread.setPipBroken();
                        }
                    }
                }
                SystemClock.sleep(300L);
                msgData[0] = 2;
                msgData[1] = 36;
                if (XpVehicleService.mCanID == 307) {
                    msgData[2] = 64;
                } else {
                    msgData[2] = ByteCompanionObject.MIN_VALUE;
                }
                if (oStream != null) {
                    try {
                        XpVehicleService.this.outputStream.write(msgData);
                        Slog.i(XpVehicleService.TAG, "setStartAtl  outputStream [0]:" + ((int) msgData[0]) + " [1]:" + ((int) msgData[1]) + " [2]:" + ((int) msgData[2]));
                    } catch (Exception e2) {
                        Slog.i(XpVehicleService.TAG, "outputStream.write error:" + e2);
                        if (XpVehicleService.this.serverThread != null) {
                            XpVehicleService.this.serverThread.setPipBroken();
                        }
                    }
                }
            }
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            try {
                try {
                    XpVehicleService.this.serverSocket = new ServerSocket(10010);
                    Slog.i(XpVehicleService.TAG, "ServerSocket  create  mCanID=" + XpVehicleService.mCanID);
                    while (this.isLoop) {
                        XpVehicleService.this.socket = XpVehicleService.this.serverSocket.accept();
                        Slog.i(XpVehicleService.TAG, "socket  =" + XpVehicleService.this.socket);
                        XpVehicleService.this.inputStream = new DataInputStream(XpVehicleService.this.socket.getInputStream());
                        XpVehicleService.this.outputStream = new DataOutputStream(XpVehicleService.this.socket.getOutputStream());
                        this.isWaiting = true;
                        byte[] msgData = new byte[XpVehicleService.mDataLen];
                        boolean getData = false;
                        setFirstConnection(XpVehicleService.this.outputStream);
                        setStartAtl(XpVehicleService.this.outputStream);
                        Slog.i(XpVehicleService.TAG, "waiting message");
                        while (this.isWaiting) {
                            SystemClock.sleep(10L);
                            synchronized (XpVehicleService.this.lock) {
                                if (XpVehicleService.this.messageArray.size() > 0) {
                                    OutMessage oMsg = (OutMessage) XpVehicleService.this.messageArray.get(0);
                                    XpVehicleService.this.messageArray.remove(0);
                                    getData = true;
                                    msgData = oMsg.getData();
                                }
                            }
                            if (getData) {
                                try {
                                    if (XpVehicleService.this.outputStream != null) {
                                        XpVehicleService.this.outputStream.write(msgData);
                                        Slog.i(XpVehicleService.TAG, "outputStream [0]:" + ((int) msgData[0]) + " [1]:" + ((int) msgData[1]) + " [2]:" + ((int) msgData[2]) + " [3]:" + ((int) msgData[3]) + " [4]:" + ((int) msgData[4]) + " [5]:" + ((int) msgData[5]) + " [6]:" + ((int) msgData[6]) + " [7]:" + ((int) msgData[7]) + " [8]:" + ((int) msgData[8]) + " [9]:" + ((int) msgData[9]));
                                    } else {
                                        Slog.i(XpVehicleService.TAG, "no outputStream");
                                    }
                                } catch (Exception e) {
                                    Slog.i(XpVehicleService.TAG, "outputStream.write error:" + e);
                                    if (XpVehicleService.this.serverThread != null) {
                                        XpVehicleService.this.serverThread.setPipBroken();
                                    }
                                }
                                getData = false;
                            }
                        }
                    }
                } catch (Exception e2) {
                    e2.printStackTrace();
                }
            } finally {
                Slog.i(XpVehicleService.TAG, "destory");
                XpVehicleService.this.closeAll();
            }
        }
    }
}
