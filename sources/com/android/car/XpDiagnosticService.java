package com.android.car;

import android.annotation.SuppressLint;
import android.car.ValueUnavailableException;
import android.car.XpDebugLog;
import android.car.diagnostic.EcusFailureStates;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.XpVehicle.IXpDiagnostic;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.ICarPropertyEventListener;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Pair;
import android.util.Slog;
import android.util.SparseArray;
import com.android.car.SystemActivityMonitoringService;
import com.android.car.XpDiagnosticService;
import com.android.car.hal.VehicleHal;
import com.android.internal.annotations.GuardedBy;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
/* JADX INFO: Access modifiers changed from: package-private */
/* loaded from: classes3.dex */
public class XpDiagnosticService extends IXpDiagnostic.Stub implements CarServiceBase {
    private static final int CAR_SERVICE_DEBUG_ENABLE = 1;
    private static final Duration CHECK_ICM_TBOX_CONNECTED_INTERVAL = Duration.ofSeconds(20);
    private static final boolean DBG = true;
    private static final String TAG = "XpDiagnosticService";
    private static final int WAIT_ECUS_DIAGNOSTIC_DATA_TIMEOUT_MS = 6000;
    private final ArraySet<Integer> mAfterSalesIds;
    private final CallbackStatistics mCallbackStatistics;
    private Runnable mCheckConnectedStateRunnable;
    private final Context mContext;
    private final ArraySet<Integer> mDiagPropertyIds;
    private DiagnosisErrorReporter mDiagnosisErrorReporter;
    private final Object mEcusDataLock;
    private volatile boolean mEcusDiagDataValid;
    private final ArraySet<Integer> mEcusFailureStatePropertyIds;
    private EcusFailureStates mEcusFailureStates;
    @GuardedBy({"mEcusDataLock"})
    private SparseArray<IntConsumer> mEcusIntDataConsumerMap;
    @GuardedBy({"mEcusDataLock"})
    private SparseArray<Pair<Integer, Long>> mEcusIntDataMap;
    @GuardedBy({"mEcusDataLock"})
    private SparseArray<Consumer<int[]>> mEcusIntVectorDataConsumerMap;
    @GuardedBy({"mEcusDataLock"})
    private SparseArray<Pair<Integer[], Long>> mEcusIntVectorDataMap;
    private final VehicleHal mHal;
    @GuardedBy({"this"})
    private DiagHandler mHandler;
    @GuardedBy({"this"})
    private HandlerThread mHandlerThread;
    private final ICarPropertyEventListener mICarPropertyEventListener;
    private AtomicInteger mIcmConnectedState;
    private final Object mLock;
    private final ArraySet<Integer> mMcuDiagPropertyIds;
    private final int mMyPid;
    private CarPropertyService mPropertyService;
    private Runnable mReportEcusDiagRunnable;
    @GuardedBy({"mLock"})
    private Map<Integer, CarPropertyConfig> mSupportedAfterSalesPropConfigs;
    @GuardedBy({"mLock"})
    private Map<Integer, CarPropertyConfig> mSupportedCheckedPropConfigs;
    @GuardedBy({"mLock"})
    private Map<Integer, CarPropertyConfig> mSupportedPropConfigs;
    private final SystemActivityMonitoringService mSystemActivityMonitoringService;
    @GuardedBy({"this"})
    private int mSystemDiagContent;
    private AtomicInteger mTboxConnectedState;
    @GuardedBy({"this"})
    private final ArraySet<ICarPropertyEventListener> mCallbacks = new ArraySet<>();
    private final Map<IBinder, Client> mClientMap = new ConcurrentHashMap();
    private final Map<Integer, List<Client>> mPropIdClientMap = new ConcurrentHashMap();

    @Retention(RetentionPolicy.SOURCE)
    /* loaded from: classes3.dex */
    public @interface PropertyId {
    }

    public /* synthetic */ void lambda$new$0$XpDiagnosticService() {
        Slog.i(TAG, "time to Check ICM/TBOX connected state");
        if (this.mTboxConnectedState.get() == 0) {
            this.mDiagnosisErrorReporter.reportTboxNotConnected();
        }
        if (this.mIcmConnectedState.get() == 0) {
            this.mDiagnosisErrorReporter.reportIcmNotConnected();
        }
    }

    public /* synthetic */ void lambda$new$1$XpDiagnosticService() {
        Slog.i(TAG, "time to report ECUs diagnostic data");
        this.mEcusDiagDataValid = true;
        onPropertyChange(getCurrentEcusDiagEventList());
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public XpDiagnosticService(CarPropertyService service, Context context, SystemActivityMonitoringService systemActivityMonitoringService, VehicleHal hal) {
        Integer valueOf = Integer.valueOf((int) VehicleProperty.MCU_DIAG_REQUEST);
        Integer valueOf2 = Integer.valueOf((int) VehicleProperty.AND_DIAG_REQUEST);
        Integer valueOf3 = Integer.valueOf((int) VehicleProperty.MCU_RID_RESET_PHY);
        Integer valueOf4 = Integer.valueOf((int) VehicleProperty.MCU_RID_TEST_MODE);
        Integer valueOf5 = Integer.valueOf((int) VehicleProperty.MCU_RID_CABLE_DIAGNOSTICS);
        this.mMcuDiagPropertyIds = new ArraySet<>(Arrays.asList(valueOf, valueOf2, valueOf3, valueOf4, valueOf5, Integer.valueOf((int) VehicleProperty.MCU_DID_SQI_100BASE), Integer.valueOf((int) VehicleProperty.MCU_DID_SQI_1000BASE), Integer.valueOf((int) VehicleProperty.MCU_RID_MASTERSLAVE_CONTROL)));
        this.mAfterSalesIds = new ArraySet<>(Arrays.asList(Integer.valueOf((int) VehicleProperty.ICM_CONNECTED), Integer.valueOf((int) VehicleProperty.TBOX_CONNECT_STATE)));
        this.mEcusFailureStatePropertyIds = new ArraySet<>(Arrays.asList(Integer.valueOf((int) VehicleProperty.BCM_HIGHTBEAM_FAIL), Integer.valueOf((int) VehicleProperty.BCM_LOWBEAM_FAIL), Integer.valueOf((int) VehicleProperty.BCM_LTURNLAMP_FAIL), Integer.valueOf((int) VehicleProperty.BCM_RTURNLAMP_FAIL), Integer.valueOf((int) VehicleProperty.BCM_REARFOG_FAIL), Integer.valueOf((int) VehicleProperty.BCM_RDTROUTPUT_FAIL), Integer.valueOf((int) VehicleProperty.BCM_LDTROUTPUT_FAIL), Integer.valueOf((int) VehicleProperty.BCM_PARKINGLAMP_FAIL), Integer.valueOf((int) VehicleProperty.BCM_SYSERROR_WARM), Integer.valueOf((int) VehicleProperty.LLU_ERR_ST), Integer.valueOf((int) VehicleProperty.ATLS_ERR_ST), Integer.valueOf((int) VehicleProperty.MSMD_ECU_ERR), Integer.valueOf((int) VehicleProperty.MSMD_VENTILATIONMOTOR_ERR), Integer.valueOf((int) VehicleProperty.MSMD_HEATSYS_ERR), Integer.valueOf((int) VehicleProperty.MSMP_ECU_ERR), Integer.valueOf((int) VehicleProperty.MSMP_HEATSYS_ERR), Integer.valueOf((int) VehicleProperty.AVAS_FAULT), Integer.valueOf((int) VehicleProperty.TPMS_SYSFAULTWARN), Integer.valueOf((int) VehicleProperty.TPMS_ABNORMALPRWARN), Integer.valueOf((int) VehicleProperty.BCM_AIRBAG_FAULT_ST), Integer.valueOf((int) VehicleProperty.ALS_ERROR_ST), Integer.valueOf((int) VehicleProperty.DHC_ERR), Integer.valueOf((int) VehicleProperty.VCU_EVERRLAMP_DSP), Integer.valueOf((int) VehicleProperty.VCU_GEAR_WARNING), Integer.valueOf((int) VehicleProperty.VCU_EBS_ERR_DSP), Integer.valueOf((int) VehicleProperty.VCU_LIQUIDHIGHTEMP_ERR), Integer.valueOf((int) VehicleProperty.VCU_WATERSENSOR_ERR), Integer.valueOf((int) VehicleProperty.VCU_BCRUISE_ERR), Integer.valueOf((int) VehicleProperty.VCU_THERMORUNAWAY_ST), Integer.valueOf((int) VehicleProperty.VCU_POWERLIMITATION_DSP), Integer.valueOf((int) VehicleProperty.VCU_CHGPORTHOT_DSP), Integer.valueOf((int) VehicleProperty.VCU_OBCMSG_LOST), Integer.valueOf((int) VehicleProperty.VCU_DEADBATTERY_FLAG), Integer.valueOf((int) VehicleProperty.IPUR_FAILST), Integer.valueOf((int) VehicleProperty.IPUF_FAILST), Integer.valueOf((int) VehicleProperty.ESP_ESP_FAULT), Integer.valueOf((int) VehicleProperty.ESP_APBERR_ST), Integer.valueOf((int) VehicleProperty.ESP_ABS_FAULT), Integer.valueOf((int) VehicleProperty.ESP_AVH_FAULT), Integer.valueOf((int) VehicleProperty.IBT_FAILURE_LAMP), Integer.valueOf((int) VehicleProperty.ESP_WARN_LAMP), Integer.valueOf((int) VehicleProperty.CDC_DATAUPLOAD_ST), Integer.valueOf((int) VehicleProperty.XPU_XPU_FAIL_ST), Integer.valueOf((int) VehicleProperty.SCU_IHB_SW), Integer.valueOf((int) VehicleProperty.SCU_BSD_SW), Integer.valueOf((int) VehicleProperty.SCU_DOW_SW), Integer.valueOf((int) VehicleProperty.SCU_RCTA_SW), Integer.valueOf((int) VehicleProperty.SCU_RCW_SW), Integer.valueOf((int) VehicleProperty.SRR_FLFAIL_ST), Integer.valueOf((int) VehicleProperty.SRR_FRFAIL_ST), Integer.valueOf((int) VehicleProperty.SRR_RLFAIL_ST), Integer.valueOf((int) VehicleProperty.SRR_RRFAIL_ST), Integer.valueOf((int) VehicleProperty.SCU_LONGCTRL_REMIND), Integer.valueOf((int) VehicleProperty.SCU_ISLC_SW), Integer.valueOf((int) VehicleProperty.SCU_ALCCTRL_REMIND), Integer.valueOf((int) VehicleProperty.SCU_ICM_APAERR_TIPS), Integer.valueOf((int) VehicleProperty.ESP_SYS_WARNIND_REQ), Integer.valueOf((int) VehicleProperty.ESP_HDC_FAULT), Integer.valueOf((int) VehicleProperty.TPMS_TEMPWARN_ALL), Integer.valueOf((int) VehicleProperty.TPMS_ALL_SENSOR_ST), Integer.valueOf((int) VehicleProperty.VCU_BATHOT_DSP), Integer.valueOf((int) VehicleProperty.VCU_CCS_WORK_ST), Integer.valueOf((int) VehicleProperty.VCU_DCDC_ERROR), Integer.valueOf((int) VehicleProperty.VCU_BAT_VOLT_LOW), Integer.valueOf((int) VehicleProperty.VCU_EMOTOR_SYSTEM_HOT), Integer.valueOf((int) VehicleProperty.VCU_EVACUUM_PUMP_ERROR), Integer.valueOf((int) VehicleProperty.VCU_HVRLY_ADHESION_ST), Integer.valueOf((int) VehicleProperty.VCU_AGS_ERROR), Integer.valueOf((int) VehicleProperty.VCU_TCM_MOTOR), Integer.valueOf((int) VehicleProperty.BCM_MSM_ERROR_INFO), Integer.valueOf((int) VehicleProperty.CIU_DVR_STATUS), Integer.valueOf((int) VehicleProperty.CIU_SD_ST), Integer.valueOf((int) VehicleProperty.SCU_LDW_SW), Integer.valueOf((int) VehicleProperty.SCU_FCWAEB_SW), Integer.valueOf((int) VehicleProperty.SCU_SIDE_REVERSION_WARNING), Integer.valueOf((int) VehicleProperty.HVAC_PTC_ERROR), Integer.valueOf((int) VehicleProperty.HVAC_COMPRESSOR_ERROR_INFO), Integer.valueOf((int) VehicleProperty.VCU_BATTERY_PTC_ERROR_INFO)));
        this.mDiagPropertyIds = new ArraySet<>(Arrays.asList(valueOf, valueOf2, valueOf3, valueOf4, valueOf5, Integer.valueOf((int) VehicleProperty.MCU_DID_SQI_100BASE), Integer.valueOf((int) VehicleProperty.MCU_DID_SQI_1000BASE), Integer.valueOf((int) VehicleProperty.MCU_RID_MASTERSLAVE_CONTROL), Integer.valueOf((int) VehicleProperty.BCM_HIGHTBEAM_FAIL), Integer.valueOf((int) VehicleProperty.BCM_LOWBEAM_FAIL), Integer.valueOf((int) VehicleProperty.BCM_LTURNLAMP_FAIL), Integer.valueOf((int) VehicleProperty.BCM_RTURNLAMP_FAIL), Integer.valueOf((int) VehicleProperty.BCM_REARFOG_FAIL), Integer.valueOf((int) VehicleProperty.BCM_RDTROUTPUT_FAIL), Integer.valueOf((int) VehicleProperty.BCM_LDTROUTPUT_FAIL), Integer.valueOf((int) VehicleProperty.BCM_PARKINGLAMP_FAIL), Integer.valueOf((int) VehicleProperty.BCM_SYSERROR_WARM), Integer.valueOf((int) VehicleProperty.LLU_ERR_ST), Integer.valueOf((int) VehicleProperty.ATLS_ERR_ST), Integer.valueOf((int) VehicleProperty.MSMD_ECU_ERR), Integer.valueOf((int) VehicleProperty.MSMD_VENTILATIONMOTOR_ERR), Integer.valueOf((int) VehicleProperty.MSMD_HEATSYS_ERR), Integer.valueOf((int) VehicleProperty.MSMP_ECU_ERR), Integer.valueOf((int) VehicleProperty.MSMP_HEATSYS_ERR), Integer.valueOf((int) VehicleProperty.AVAS_FAULT), Integer.valueOf((int) VehicleProperty.TPMS_SYSFAULTWARN), Integer.valueOf((int) VehicleProperty.TPMS_ABNORMALPRWARN), Integer.valueOf((int) VehicleProperty.BCM_AIRBAG_FAULT_ST), Integer.valueOf((int) VehicleProperty.ALS_ERROR_ST), Integer.valueOf((int) VehicleProperty.DHC_ERR), Integer.valueOf((int) VehicleProperty.VCU_EVERRLAMP_DSP), Integer.valueOf((int) VehicleProperty.VCU_GEAR_WARNING), Integer.valueOf((int) VehicleProperty.VCU_EBS_ERR_DSP), Integer.valueOf((int) VehicleProperty.VCU_LIQUIDHIGHTEMP_ERR), Integer.valueOf((int) VehicleProperty.VCU_WATERSENSOR_ERR), Integer.valueOf((int) VehicleProperty.VCU_BCRUISE_ERR), Integer.valueOf((int) VehicleProperty.VCU_THERMORUNAWAY_ST), Integer.valueOf((int) VehicleProperty.VCU_POWERLIMITATION_DSP), Integer.valueOf((int) VehicleProperty.VCU_CHGPORTHOT_DSP), Integer.valueOf((int) VehicleProperty.VCU_OBCMSG_LOST), Integer.valueOf((int) VehicleProperty.VCU_DEADBATTERY_FLAG), Integer.valueOf((int) VehicleProperty.IPUR_FAILST), Integer.valueOf((int) VehicleProperty.IPUF_FAILST), Integer.valueOf((int) VehicleProperty.ESP_ESP_FAULT), Integer.valueOf((int) VehicleProperty.ESP_APBERR_ST), Integer.valueOf((int) VehicleProperty.ESP_ABS_FAULT), Integer.valueOf((int) VehicleProperty.ESP_AVH_FAULT), Integer.valueOf((int) VehicleProperty.IBT_FAILURE_LAMP), Integer.valueOf((int) VehicleProperty.ESP_WARN_LAMP), Integer.valueOf((int) VehicleProperty.CDC_DATAUPLOAD_ST), Integer.valueOf((int) VehicleProperty.XPU_XPU_FAIL_ST), Integer.valueOf((int) VehicleProperty.SCU_IHB_SW), Integer.valueOf((int) VehicleProperty.SCU_BSD_SW), Integer.valueOf((int) VehicleProperty.SCU_DOW_SW), Integer.valueOf((int) VehicleProperty.SCU_RCTA_SW), Integer.valueOf((int) VehicleProperty.SCU_RCW_SW), Integer.valueOf((int) VehicleProperty.SRR_FLFAIL_ST), Integer.valueOf((int) VehicleProperty.SRR_FRFAIL_ST), Integer.valueOf((int) VehicleProperty.SRR_RLFAIL_ST), Integer.valueOf((int) VehicleProperty.SRR_RRFAIL_ST), Integer.valueOf((int) VehicleProperty.SCU_LONGCTRL_REMIND), Integer.valueOf((int) VehicleProperty.SCU_ISLC_SW), Integer.valueOf((int) VehicleProperty.SCU_ALCCTRL_REMIND), Integer.valueOf((int) VehicleProperty.SCU_ICM_APAERR_TIPS), Integer.valueOf((int) VehicleProperty.VCU_EVERR_MSGDISP), Integer.valueOf((int) VehicleProperty.ESP_SYS_WARNIND_REQ), Integer.valueOf((int) VehicleProperty.ESP_HDC_FAULT), Integer.valueOf((int) VehicleProperty.TPMS_TEMPWARN_ALL), Integer.valueOf((int) VehicleProperty.TPMS_ALL_SENSOR_ST), Integer.valueOf((int) VehicleProperty.VCU_BATHOT_DSP), Integer.valueOf((int) VehicleProperty.VCU_CCS_WORK_ST), Integer.valueOf((int) VehicleProperty.VCU_DCDC_ERROR), Integer.valueOf((int) VehicleProperty.VCU_BAT_VOLT_LOW), Integer.valueOf((int) VehicleProperty.VCU_EMOTOR_SYSTEM_HOT), Integer.valueOf((int) VehicleProperty.VCU_EVACUUM_PUMP_ERROR), Integer.valueOf((int) VehicleProperty.VCU_HVRLY_ADHESION_ST), Integer.valueOf((int) VehicleProperty.VCU_AGS_ERROR), Integer.valueOf((int) VehicleProperty.VCU_TCM_MOTOR), Integer.valueOf((int) VehicleProperty.BCM_MSM_ERROR_INFO), Integer.valueOf((int) VehicleProperty.CIU_DVR_STATUS), Integer.valueOf((int) VehicleProperty.CIU_SD_ST), Integer.valueOf((int) VehicleProperty.SCU_LDW_SW), Integer.valueOf((int) VehicleProperty.SCU_FCWAEB_SW), Integer.valueOf((int) VehicleProperty.SCU_SIDE_REVERSION_WARNING), Integer.valueOf((int) VehicleProperty.HVAC_PTC_ERROR), Integer.valueOf((int) VehicleProperty.HVAC_COMPRESSOR_ERROR_INFO), Integer.valueOf((int) VehicleProperty.VCU_BATTERY_PTC_ERROR_INFO), Integer.valueOf((int) VehicleProperty.ICM_CONNECTED), Integer.valueOf((int) VehicleProperty.TBOX_CONNECT_STATE)));
        this.mLock = new Object();
        this.mEcusDataLock = new Object();
        this.mICarPropertyEventListener = new ICarPropertyEventListener.Stub() { // from class: com.android.car.XpDiagnosticService.1
            public void onEvent(List<CarPropertyEvent> events) throws RemoteException {
                XpDiagnosticService.this.mHandler.handleDiagEvents(events);
            }
        };
        this.mEcusFailureStates = new EcusFailureStates();
        this.mEcusDiagDataValid = false;
        this.mTboxConnectedState = new AtomicInteger(-1);
        this.mIcmConnectedState = new AtomicInteger(-1);
        this.mCheckConnectedStateRunnable = new Runnable() { // from class: com.android.car.-$$Lambda$XpDiagnosticService$iol7Rtqy14H0S5ebWfrLcEJGIe8
            @Override // java.lang.Runnable
            public final void run() {
                XpDiagnosticService.this.lambda$new$0$XpDiagnosticService();
            }
        };
        this.mEcusIntDataConsumerMap = new SparseArray<>(128);
        this.mEcusIntVectorDataConsumerMap = new SparseArray<>(64);
        this.mEcusIntDataMap = new SparseArray<>(128);
        this.mEcusIntVectorDataMap = new SparseArray<>(64);
        this.mReportEcusDiagRunnable = new Runnable() { // from class: com.android.car.-$$Lambda$XpDiagnosticService$hcWnQk0aC5PTcHLR34zMpVFnxek
            @Override // java.lang.Runnable
            public final void run() {
                XpDiagnosticService.this.lambda$new$1$XpDiagnosticService();
            }
        };
        this.mPropertyService = service;
        this.mContext = context;
        this.mSystemActivityMonitoringService = systemActivityMonitoringService;
        this.mHal = hal;
        this.mDiagnosisErrorReporter = DiagnosisErrorReporter.getInstance(context);
        this.mCallbackStatistics = new CallbackStatistics(TAG, true);
        this.mMyPid = this.mCallbackStatistics.getMyPid();
    }

    private List<CarPropertyEvent> getCurrentEcusDiagEventList() {
        List<CarPropertyEvent> list = new ArrayList<>();
        Set<Integer> props = getSupportedCheckedPropIds();
        if (props == null) {
            return list;
        }
        synchronized (this.mEcusDataLock) {
            for (Integer prop : props) {
                CarPropertyConfig config = getCarPropertyConfig(prop.intValue());
                if (config == null) {
                    Slog.w(TAG, "Unsupported propId=0x" + Integer.toHexString(prop.intValue()));
                } else {
                    Class<?> clazz = config.getPropertyType();
                    if (Integer.class == clazz) {
                        Pair<Integer, Long> value = this.mEcusIntDataMap.get(prop.intValue());
                        Integer propData = (Integer) value.first;
                        if (-1 != propData.intValue()) {
                            list.add(new CarPropertyEvent(0, new CarPropertyValue(prop.intValue(), 0, 0, ((Long) value.second).longValue(), propData)));
                        }
                    } else if (Integer[].class == clazz) {
                        Pair<Integer[], Long> value2 = this.mEcusIntVectorDataMap.get(prop.intValue());
                        Integer[] propData2 = (Integer[]) value2.first;
                        if (EcusFailureStates.INTEGER_VECTOR_DEFAULT_STATE != propData2) {
                            list.add(new CarPropertyEvent(0, new CarPropertyValue(prop.intValue(), 0, 0, ((Long) value2.second).longValue(), propData2)));
                        }
                    } else {
                        Slog.w(TAG, "Unsupported type: " + clazz + " for propId=0x" + Integer.toHexString(prop.intValue()));
                    }
                }
            }
        }
        return list;
    }

    private void initSupportedPropConfigs() {
        synchronized (this.mLock) {
            if (this.mSupportedPropConfigs == null) {
                this.mSupportedPropConfigs = new ArrayMap();
                List<CarPropertyConfig> configs = getPropertyList(this.mDiagPropertyIds);
                for (CarPropertyConfig c : configs) {
                    this.mSupportedPropConfigs.put(Integer.valueOf(c.getPropertyId()), c);
                }
            }
        }
    }

    private void initSupportedCheckedPropConfigs() {
        synchronized (this.mLock) {
            if (this.mSupportedCheckedPropConfigs == null) {
                this.mSupportedCheckedPropConfigs = new ArrayMap();
                List<CarPropertyConfig> configs = getPropertyList(this.mEcusFailureStatePropertyIds);
                for (CarPropertyConfig c : configs) {
                    this.mSupportedCheckedPropConfigs.put(Integer.valueOf(c.getPropertyId()), c);
                }
            }
        }
    }

    private void initSupportedAfterSalesPropConfigs() {
        synchronized (this.mLock) {
            if (this.mSupportedAfterSalesPropConfigs == null) {
                this.mSupportedAfterSalesPropConfigs = new ArrayMap();
                List<CarPropertyConfig> configs = getPropertyList(this.mAfterSalesIds);
                for (CarPropertyConfig c : configs) {
                    this.mSupportedAfterSalesPropConfigs.put(Integer.valueOf(c.getPropertyId()), c);
                }
            }
        }
    }

    private List<CarPropertyConfig> getPropertyList(ArraySet<Integer> propertyIds) {
        List<CarPropertyConfig> configs = new ArrayList<>();
        List<CarPropertyConfig> propertyList = this.mPropertyService.getPropertyList();
        for (CarPropertyConfig c : propertyList) {
            if (propertyIds.contains(Integer.valueOf(c.getPropertyId()))) {
                configs.add(c);
            }
        }
        return configs;
    }

    @SuppressLint({"NewApi"})
    private void initEcusDataMap() {
        synchronized (this.mEcusDataLock) {
            this.mEcusIntDataMap.put(VehicleProperty.BCM_HIGHTBEAM_FAIL, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.BCM_LOWBEAM_FAIL, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.BCM_LTURNLAMP_FAIL, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.BCM_RTURNLAMP_FAIL, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.BCM_REARFOG_FAIL, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.BCM_RDTROUTPUT_FAIL, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.BCM_LDTROUTPUT_FAIL, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.BCM_PARKINGLAMP_FAIL, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.BCM_SYSERROR_WARM, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.LLU_ERR_ST, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.ATLS_ERR_ST, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.MSMD_ECU_ERR, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.MSMD_VENTILATIONMOTOR_ERR, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.MSMD_HEATSYS_ERR, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.MSMP_ECU_ERR, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.MSMP_HEATSYS_ERR, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.AVAS_FAULT, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.TPMS_SYSFAULTWARN, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.TPMS_ABNORMALPRWARN, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.BCM_AIRBAG_FAULT_ST, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.ALS_ERROR_ST, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.DHC_ERR, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.VCU_EVERRLAMP_DSP, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.VCU_GEAR_WARNING, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.VCU_EBS_ERR_DSP, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.VCU_LIQUIDHIGHTEMP_ERR, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.VCU_WATERSENSOR_ERR, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.VCU_BCRUISE_ERR, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.VCU_THERMORUNAWAY_ST, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.VCU_POWERLIMITATION_DSP, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.VCU_CHGPORTHOT_DSP, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.VCU_OBCMSG_LOST, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.VCU_DEADBATTERY_FLAG, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.IPUR_FAILST, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.IPUF_FAILST, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.ESP_ESP_FAULT, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.ESP_APBERR_ST, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.ESP_ABS_FAULT, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.ESP_AVH_FAULT, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.IBT_FAILURE_LAMP, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.ESP_WARN_LAMP, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.CDC_DATAUPLOAD_ST, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.XPU_XPU_FAIL_ST, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.SCU_IHB_SW, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.SCU_BSD_SW, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.SCU_DOW_SW, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.SCU_RCTA_SW, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.SCU_RCW_SW, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.SRR_FLFAIL_ST, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.SRR_FRFAIL_ST, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.SRR_RLFAIL_ST, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.SRR_RRFAIL_ST, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.SCU_LONGCTRL_REMIND, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.SCU_ISLC_SW, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.SCU_ALCCTRL_REMIND, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.SCU_ICM_APAERR_TIPS, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.ESP_SYS_WARNIND_REQ, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.ESP_HDC_FAULT, Pair.create(-1, 0L));
            this.mEcusIntVectorDataMap.put(VehicleProperty.TPMS_TEMPWARN_ALL, Pair.create(EcusFailureStates.INTEGER_VECTOR_DEFAULT_STATE, 0L));
            this.mEcusIntVectorDataMap.put(VehicleProperty.TPMS_ALL_SENSOR_ST, Pair.create(EcusFailureStates.INTEGER_VECTOR_DEFAULT_STATE, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.VCU_BATHOT_DSP, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.VCU_CCS_WORK_ST, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.VCU_DCDC_ERROR, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.VCU_BAT_VOLT_LOW, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.VCU_EMOTOR_SYSTEM_HOT, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.VCU_EVACUUM_PUMP_ERROR, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.VCU_HVRLY_ADHESION_ST, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.VCU_AGS_ERROR, Pair.create(-1, 0L));
            this.mEcusIntVectorDataMap.put(VehicleProperty.VCU_TCM_MOTOR, Pair.create(EcusFailureStates.INTEGER_VECTOR_DEFAULT_STATE, 0L));
            this.mEcusIntVectorDataMap.put(VehicleProperty.BCM_MSM_ERROR_INFO, Pair.create(EcusFailureStates.INTEGER_VECTOR_DEFAULT_STATE, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.CIU_DVR_STATUS, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.CIU_SD_ST, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.SCU_LDW_SW, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.SCU_FCWAEB_SW, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.SCU_SIDE_REVERSION_WARNING, Pair.create(-1, 0L));
            this.mEcusIntDataMap.put(VehicleProperty.HVAC_PTC_ERROR, Pair.create(-1, 0L));
            this.mEcusIntVectorDataMap.put(VehicleProperty.HVAC_COMPRESSOR_ERROR_INFO, Pair.create(EcusFailureStates.INTEGER_VECTOR_DEFAULT_STATE, 0L));
            this.mEcusIntVectorDataMap.put(VehicleProperty.VCU_BATTERY_PTC_ERROR_INFO, Pair.create(EcusFailureStates.INTEGER_VECTOR_DEFAULT_STATE, 0L));
        }
    }

    private void initEcusDataConsumerMap() {
        synchronized (this.mEcusDataLock) {
            SparseArray<IntConsumer> sparseArray = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates);
            sparseArray.put(VehicleProperty.BCM_HIGHTBEAM_FAIL, new IntConsumer() { // from class: com.android.car.-$$Lambda$qBdF3dkRE_ALq7_pW1nGKMt18V8
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates.setHighBeamFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray2 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates2 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates2);
            sparseArray2.put(VehicleProperty.BCM_LOWBEAM_FAIL, new IntConsumer() { // from class: com.android.car.-$$Lambda$Q7Ch6IwgskbUm2K71VzJH1u33ig
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates2.setLowBeamFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray3 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates3 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates3);
            sparseArray3.put(VehicleProperty.BCM_LTURNLAMP_FAIL, new IntConsumer() { // from class: com.android.car.-$$Lambda$9Gvg1Zwnuju10R3suSdFHk3A0ME
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates3.setLeftTurnLampFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray4 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates4 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates4);
            sparseArray4.put(VehicleProperty.BCM_RTURNLAMP_FAIL, new IntConsumer() { // from class: com.android.car.-$$Lambda$Zibb3GyJRnXftVkM5m5-LeCIj4o
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates4.setRightTurnLampFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray5 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates5 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates5);
            sparseArray5.put(VehicleProperty.BCM_REARFOG_FAIL, new IntConsumer() { // from class: com.android.car.-$$Lambda$fbzLLGcFsCouS-Us6m4pvdvHTqU
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates5.setRearFogLampFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray6 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates6 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates6);
            sparseArray6.put(VehicleProperty.BCM_RDTROUTPUT_FAIL, new IntConsumer() { // from class: com.android.car.-$$Lambda$ZgI-O5z-nkDSmX6GuwdIyyAmUMc
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates6.setRightDaytimeRunningLightFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray7 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates7 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates7);
            sparseArray7.put(VehicleProperty.BCM_LDTROUTPUT_FAIL, new IntConsumer() { // from class: com.android.car.-$$Lambda$G9kZX4G4_o-O47WcJICYJ3sWkBM
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates7.setLeftDaytimeRunningLightFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray8 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates8 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates8);
            sparseArray8.put(VehicleProperty.BCM_PARKINGLAMP_FAIL, new IntConsumer() { // from class: com.android.car.-$$Lambda$b_FF52OM5oYTgokQPlOJXRPGDgw
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates8.setParkingLampFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray9 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates9 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates9);
            sparseArray9.put(VehicleProperty.BCM_SYSERROR_WARM, new IntConsumer() { // from class: com.android.car.-$$Lambda$6lbVPIylgvUt3CIif3x24QvVCo0
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates9.setBcmSystemFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray10 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates10 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates10);
            sparseArray10.put(VehicleProperty.LLU_ERR_ST, new IntConsumer() { // from class: com.android.car.-$$Lambda$knzOLLWyDG3LiZhReJUTX5xnBu4
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates10.setLluFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray11 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates11 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates11);
            sparseArray11.put(VehicleProperty.ATLS_ERR_ST, new IntConsumer() { // from class: com.android.car.-$$Lambda$-9qexQY76oKTUxBmhsi4lRBCHc4
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates11.setAtlsFailureSate(i);
                }
            });
            SparseArray<IntConsumer> sparseArray12 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates12 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates12);
            sparseArray12.put(VehicleProperty.MSMD_ECU_ERR, new IntConsumer() { // from class: com.android.car.-$$Lambda$JDbMdE4AWV9mDod6Uom5zXwjC2g
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates12.setDriverMsmFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray13 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates13 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates13);
            sparseArray13.put(VehicleProperty.MSMD_VENTILATIONMOTOR_ERR, new IntConsumer() { // from class: com.android.car.-$$Lambda$pW_XACrsat4z7flTXqW-SisTTmc
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates13.setDriverMsmVentilationMotorFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray14 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates14 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates14);
            sparseArray14.put(VehicleProperty.MSMD_HEATSYS_ERR, new IntConsumer() { // from class: com.android.car.-$$Lambda$THEni5FmRYpXyZcn7E-Au-uJgQ8
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates14.setDriverMsmHeatSysFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray15 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates15 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates15);
            sparseArray15.put(VehicleProperty.MSMP_ECU_ERR, new IntConsumer() { // from class: com.android.car.-$$Lambda$GP3rBq0qoUw3Qm4pkPGrR_T4Nb8
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates15.setPassengerMsmFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray16 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates16 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates16);
            sparseArray16.put(VehicleProperty.MSMP_HEATSYS_ERR, new IntConsumer() { // from class: com.android.car.-$$Lambda$eb99sNINGDVjQpAf2Th2lYUhgiE
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates16.setPassengerMsmHeatSysFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray17 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates17 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates17);
            sparseArray17.put(VehicleProperty.AVAS_FAULT, new IntConsumer() { // from class: com.android.car.-$$Lambda$PVzn-dN_w4F-1K29VjQY4yBeybs
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates17.setAvasFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray18 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates18 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates18);
            sparseArray18.put(VehicleProperty.TPMS_SYSFAULTWARN, new IntConsumer() { // from class: com.android.car.-$$Lambda$mj0NleCdR5f3CvIlCxNXPtJUbgk
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates18.setTpmsSysFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray19 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates19 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates19);
            sparseArray19.put(VehicleProperty.TPMS_ABNORMALPRWARN, new IntConsumer() { // from class: com.android.car.-$$Lambda$5EX90otr9siSZKJJi04HFspaYs0
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates19.setAbnormalTirePressureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray20 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates20 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates20);
            sparseArray20.put(VehicleProperty.BCM_AIRBAG_FAULT_ST, new IntConsumer() { // from class: com.android.car.-$$Lambda$M40v8ITW7nihNs2lf77MilQoMDw
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates20.setAirbagFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray21 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates21 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates21);
            sparseArray21.put(VehicleProperty.ALS_ERROR_ST, new IntConsumer() { // from class: com.android.car.-$$Lambda$hL37e3yTsIASku1DySyDQarrVXk
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates21.setAlsFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray22 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates22 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates22);
            sparseArray22.put(VehicleProperty.DHC_ERR, new IntConsumer() { // from class: com.android.car.-$$Lambda$xBF1AkcCDgoMxX9WF3WCklCJeAA
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates22.setDhcFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray23 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates23 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates23);
            sparseArray23.put(VehicleProperty.VCU_EVERRLAMP_DSP, new IntConsumer() { // from class: com.android.car.-$$Lambda$PcF9luIkJ8PKkKf93plb42YPgaY
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates23.setEvSysFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray24 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates24 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates24);
            sparseArray24.put(VehicleProperty.VCU_GEAR_WARNING, new IntConsumer() { // from class: com.android.car.-$$Lambda$KjKFzeCHHBPxHRMB9RorKnz5a24
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates24.setGearWarningInfo(i);
                }
            });
            SparseArray<IntConsumer> sparseArray25 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates25 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates25);
            sparseArray25.put(VehicleProperty.VCU_EBS_ERR_DSP, new IntConsumer() { // from class: com.android.car.-$$Lambda$hUgh1-GSx_zg_t2fYR-uGXGIdhI
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates25.setEbsFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray26 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates26 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates26);
            sparseArray26.put(VehicleProperty.VCU_LIQUIDHIGHTEMP_ERR, new IntConsumer() { // from class: com.android.car.-$$Lambda$cb52vZ5WBDokn7lAJKKugPl_kMU
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates26.setLiquidHighTempState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray27 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates27 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates27);
            sparseArray27.put(VehicleProperty.VCU_WATERSENSOR_ERR, new IntConsumer() { // from class: com.android.car.-$$Lambda$W-j19uyK0PG0KArQn7yjHJ22MSA
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates27.setWaterSensorFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray28 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates28 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates28);
            sparseArray28.put(VehicleProperty.VCU_BCRUISE_ERR, new IntConsumer() { // from class: com.android.car.-$$Lambda$irN-fQruqi0u_JvrCKVzN_wzUrM
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates28.setBCruiseFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray29 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates29 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates29);
            sparseArray29.put(VehicleProperty.VCU_THERMORUNAWAY_ST, new IntConsumer() { // from class: com.android.car.-$$Lambda$FFte1cavrFZX0SUPDXrarulByAY
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates29.setThermalRunawayState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray30 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates30 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates30);
            sparseArray30.put(VehicleProperty.VCU_POWERLIMITATION_DSP, new IntConsumer() { // from class: com.android.car.-$$Lambda$7EaeF8HnnGFMD8U0ySa4tPxBUi8
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates30.setPowerLimitationState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray31 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates31 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates31);
            sparseArray31.put(VehicleProperty.VCU_CHGPORTHOT_DSP, new IntConsumer() { // from class: com.android.car.-$$Lambda$p1indbJDbrEeu5oHoAeDV8ocumg
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates31.setChargePortHotState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray32 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates32 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates32);
            sparseArray32.put(VehicleProperty.VCU_OBCMSG_LOST, new IntConsumer() { // from class: com.android.car.-$$Lambda$cG9XLaflGJNPiKwryRSZb0OINLs
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates32.setObcMsgLostState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray33 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates33 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates33);
            sparseArray33.put(VehicleProperty.VCU_DEADBATTERY_FLAG, new IntConsumer() { // from class: com.android.car.-$$Lambda$EFg6OY25rDSzBPkB37PhOfoV2Co
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates33.setBatteryDeadState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray34 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates34 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates34);
            sparseArray34.put(VehicleProperty.IPUF_FAILST, new IntConsumer() { // from class: com.android.car.-$$Lambda$UXgzj6iiUCo1ZJAn6tUGLqVeUIU
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates34.setFrontIpuFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray35 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates35 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates35);
            sparseArray35.put(VehicleProperty.IPUR_FAILST, new IntConsumer() { // from class: com.android.car.-$$Lambda$nmbwCL27uc7ys9Prh7DC4FBIeis
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates35.setRearIpuFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray36 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates36 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates36);
            sparseArray36.put(VehicleProperty.ESP_ESP_FAULT, new IntConsumer() { // from class: com.android.car.-$$Lambda$UqPOlO9ftAgDmoMcVWCGL3zuZOo
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates36.setEspFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray37 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates37 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates37);
            sparseArray37.put(VehicleProperty.ESP_APBERR_ST, new IntConsumer() { // from class: com.android.car.-$$Lambda$12U1Vpp2Q18zGFhCIT2RFpSvQhs
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates37.setApbFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray38 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates38 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates38);
            sparseArray38.put(VehicleProperty.ESP_ABS_FAULT, new IntConsumer() { // from class: com.android.car.-$$Lambda$Bz-eCRXpe1-dPuMCrrUKm2Ppfr0
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates38.setAbsFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray39 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates39 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates39);
            sparseArray39.put(VehicleProperty.ESP_AVH_FAULT, new IntConsumer() { // from class: com.android.car.-$$Lambda$nuXE0mm0gmMx74j6i4Hb1hh7ObA
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates39.setAvhFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray40 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates40 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates40);
            sparseArray40.put(VehicleProperty.IBT_FAILURE_LAMP, new IntConsumer() { // from class: com.android.car.-$$Lambda$1YepSWdH0extUjeZgZSEN2PcSPI
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates40.setIbtFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray41 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates41 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates41);
            sparseArray41.put(VehicleProperty.ESP_WARN_LAMP, new IntConsumer() { // from class: com.android.car.-$$Lambda$b-RbqFOd2i8ILqw2tcDmpElArZI
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates41.setEpsFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray42 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates42 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates42);
            sparseArray42.put(VehicleProperty.CDC_DATAUPLOAD_ST, new IntConsumer() { // from class: com.android.car.-$$Lambda$eGqvC3_yC3WsXHhbOQz29YL_1N4
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates42.setCdcFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray43 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates43 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates43);
            sparseArray43.put(VehicleProperty.XPU_XPU_FAIL_ST, new IntConsumer() { // from class: com.android.car.-$$Lambda$4i8afgAOB8xs085a-Cmwfnc4tgA
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates43.setXpuFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray44 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates44 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates44);
            sparseArray44.put(VehicleProperty.SCU_IHB_SW, new IntConsumer() { // from class: com.android.car.-$$Lambda$IR9bBVaxj7ppMBX-5dk0339kGbM
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates44.setIhbState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray45 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates45 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates45);
            sparseArray45.put(VehicleProperty.SCU_BSD_SW, new IntConsumer() { // from class: com.android.car.-$$Lambda$BBHBx7Sr6WjEoVrAwz5hNyA7gGc
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates45.setBsdState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray46 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates46 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates46);
            sparseArray46.put(VehicleProperty.SCU_DOW_SW, new IntConsumer() { // from class: com.android.car.-$$Lambda$lLMq7U7J7BVzeJflAGwHxmUrOT0
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates46.setDowState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray47 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates47 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates47);
            sparseArray47.put(VehicleProperty.SCU_RCTA_SW, new IntConsumer() { // from class: com.android.car.-$$Lambda$SBIfDFrP9pULcylDpad3D9O5CIc
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates47.setRctaState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray48 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates48 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates48);
            sparseArray48.put(VehicleProperty.SCU_RCW_SW, new IntConsumer() { // from class: com.android.car.-$$Lambda$krHlU9hfN8x1ZQ7JZmc-bZXDu-8
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates48.setRcwState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray49 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates49 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates49);
            sparseArray49.put(VehicleProperty.SRR_FLFAIL_ST, new IntConsumer() { // from class: com.android.car.-$$Lambda$lP33gXCTKgBWVgstlN86NRLVBts
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates49.setFlSrrFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray50 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates50 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates50);
            sparseArray50.put(VehicleProperty.SRR_FRFAIL_ST, new IntConsumer() { // from class: com.android.car.-$$Lambda$eqIUvcZeH-_pm4G6VWOvG9x4oPQ
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates50.setFrSrrFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray51 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates51 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates51);
            sparseArray51.put(VehicleProperty.SRR_RLFAIL_ST, new IntConsumer() { // from class: com.android.car.-$$Lambda$2tOeZhQFoHHWJSXHQnPtBXUVieQ
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates51.setRlSrrFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray52 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates52 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates52);
            sparseArray52.put(VehicleProperty.SRR_RRFAIL_ST, new IntConsumer() { // from class: com.android.car.-$$Lambda$OxtWp33drShc5e6lFWNK_V_IeBo
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates52.setRrSrrFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray53 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates53 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates53);
            sparseArray53.put(VehicleProperty.SCU_LONGCTRL_REMIND, new IntConsumer() { // from class: com.android.car.-$$Lambda$2qByKjQ-AI4zBBK_o2OVYTNdeAs
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates53.setScuLongCtrlRemindInfo(i);
                }
            });
            SparseArray<IntConsumer> sparseArray54 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates54 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates54);
            sparseArray54.put(VehicleProperty.SCU_ISLC_SW, new IntConsumer() { // from class: com.android.car.-$$Lambda$MU-PHWtOC7MJ0MR3HI66FSW5Ieg
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates54.setIslcState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray55 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates55 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates55);
            sparseArray55.put(VehicleProperty.SCU_ALCCTRL_REMIND, new IntConsumer() { // from class: com.android.car.-$$Lambda$zgj2tLyUvQ8DwdagvJA-k0V6_-8
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates55.setAlcCtrlRemindInfo(i);
                }
            });
            SparseArray<IntConsumer> sparseArray56 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates56 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates56);
            sparseArray56.put(VehicleProperty.SCU_ICM_APAERR_TIPS, new IntConsumer() { // from class: com.android.car.-$$Lambda$sB0WVTvr5Pfv5HTMfXfpxrJQx2Y
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates56.setApaFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray57 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates57 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates57);
            sparseArray57.put(VehicleProperty.ESP_SYS_WARNIND_REQ, new IntConsumer() { // from class: com.android.car.-$$Lambda$diVvTRXamzHEkowyR9MNFz0TSkc
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates57.setEpbSysFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray58 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates58 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates58);
            sparseArray58.put(VehicleProperty.ESP_HDC_FAULT, new IntConsumer() { // from class: com.android.car.-$$Lambda$b6GmqOlR1w2eDD9308-uuMCT2vQ
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates58.setHdcFailureState(i);
                }
            });
            SparseArray<Consumer<int[]>> sparseArray59 = this.mEcusIntVectorDataConsumerMap;
            final EcusFailureStates ecusFailureStates59 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates59);
            sparseArray59.put(VehicleProperty.TPMS_TEMPWARN_ALL, new Consumer() { // from class: com.android.car.-$$Lambda$vefc2zVSTHZXDoL1F1OM0JxlJHM
                @Override // java.util.function.Consumer
                public final void accept(Object obj) {
                    ecusFailureStates59.setAllTireTemperatureWarnings((int[]) obj);
                }
            });
            SparseArray<Consumer<int[]>> sparseArray60 = this.mEcusIntVectorDataConsumerMap;
            final EcusFailureStates ecusFailureStates60 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates60);
            sparseArray60.put(VehicleProperty.TPMS_ALL_SENSOR_ST, new Consumer() { // from class: com.android.car.-$$Lambda$y6v1Qm8NEkKBDCO7feSd45Q50jU
                @Override // java.util.function.Consumer
                public final void accept(Object obj) {
                    ecusFailureStates60.setAllTirePerssureSensorsFailureStates((int[]) obj);
                }
            });
            SparseArray<IntConsumer> sparseArray61 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates61 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates61);
            sparseArray61.put(VehicleProperty.VCU_BATHOT_DSP, new IntConsumer() { // from class: com.android.car.-$$Lambda$IJWLGlMyzPVlGwcwVKG6-qnlx_4
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates61.setBatteryOverheatingState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray62 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates62 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates62);
            sparseArray62.put(VehicleProperty.VCU_CCS_WORK_ST, new IntConsumer() { // from class: com.android.car.-$$Lambda$jBPbxsoLptuKGduf-yccc9RfHCw
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates62.setCcsWorkState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray63 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates63 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates63);
            sparseArray63.put(VehicleProperty.VCU_DCDC_ERROR, new IntConsumer() { // from class: com.android.car.-$$Lambda$w8xkGt4EbAgrmO_4OThkslU8b7I
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates63.setDcdcFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray64 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates64 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates64);
            sparseArray64.put(VehicleProperty.VCU_BAT_VOLT_LOW, new IntConsumer() { // from class: com.android.car.-$$Lambda$kUoXGGU0cDIrGmAbdvH16DLK5Wc
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates64.setBatteryVoltageLowState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray65 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates65 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates65);
            sparseArray65.put(VehicleProperty.VCU_EMOTOR_SYSTEM_HOT, new IntConsumer() { // from class: com.android.car.-$$Lambda$-iTlA6h7q6ajuJJ5gFxW70PX9P4
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates65.setElectricMotorSystemHotState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray66 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates66 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates66);
            sparseArray66.put(VehicleProperty.VCU_EVACUUM_PUMP_ERROR, new IntConsumer() { // from class: com.android.car.-$$Lambda$eFsfSTq2H28ukw65neXoesZiBHQ
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates66.setElectricVacuumPumpFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray67 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates67 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates67);
            sparseArray67.put(VehicleProperty.VCU_HVRLY_ADHESION_ST, new IntConsumer() { // from class: com.android.car.-$$Lambda$l4a1huZOd563dL_7Vt61s_-xW9A
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates67.setHighVoltageRelayAdhesionState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray68 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates68 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates68);
            sparseArray68.put(VehicleProperty.VCU_AGS_ERROR, new IntConsumer() { // from class: com.android.car.-$$Lambda$569kP2qhAEZ6AP8zxizLHNQ2OSQ
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates68.setAgsFailureState(i);
                }
            });
            SparseArray<Consumer<int[]>> sparseArray69 = this.mEcusIntVectorDataConsumerMap;
            final EcusFailureStates ecusFailureStates69 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates69);
            sparseArray69.put(VehicleProperty.VCU_TCM_MOTOR, new Consumer() { // from class: com.android.car.-$$Lambda$zeZrG_hOS9QFOWP2DNciDrTveeU
                @Override // java.util.function.Consumer
                public final void accept(Object obj) {
                    ecusFailureStates69.setTcmMotorFailureStates((int[]) obj);
                }
            });
            SparseArray<Consumer<int[]>> sparseArray70 = this.mEcusIntVectorDataConsumerMap;
            final EcusFailureStates ecusFailureStates70 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates70);
            sparseArray70.put(VehicleProperty.BCM_MSM_ERROR_INFO, new Consumer() { // from class: com.android.car.-$$Lambda$okNwYaZ8BiNVc0FAHQMA70Ne8hQ
                @Override // java.util.function.Consumer
                public final void accept(Object obj) {
                    ecusFailureStates70.setAllMsmModulesFailureStates((int[]) obj);
                }
            });
            SparseArray<IntConsumer> sparseArray71 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates71 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates71);
            sparseArray71.put(VehicleProperty.CIU_DVR_STATUS, new IntConsumer() { // from class: com.android.car.-$$Lambda$rhMb2lcxEfnTFCezvSdEah5Ptgg
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates71.setDvrFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray72 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates72 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates72);
            sparseArray72.put(VehicleProperty.CIU_SD_ST, new IntConsumer() { // from class: com.android.car.-$$Lambda$QWzeNCAF4i7R29BnpSMY0w02Tzk
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates72.setCiuSdcardFailureState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray73 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates73 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates73);
            sparseArray73.put(VehicleProperty.SCU_LDW_SW, new IntConsumer() { // from class: com.android.car.-$$Lambda$5vwV130YKIbPHNOusOVCtsLMDKA
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates73.setLdwState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray74 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates74 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates74);
            sparseArray74.put(VehicleProperty.SCU_FCWAEB_SW, new IntConsumer() { // from class: com.android.car.-$$Lambda$Qsc_CP6k-Qs-NqdaoTeVHdnnBkc
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates74.setAebState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray75 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates75 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates75);
            sparseArray75.put(VehicleProperty.SCU_SIDE_REVERSION_WARNING, new IntConsumer() { // from class: com.android.car.-$$Lambda$ucKWuYyhH97zgtL3QteORmHJMlk
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates75.setSideReversingState(i);
                }
            });
            SparseArray<IntConsumer> sparseArray76 = this.mEcusIntDataConsumerMap;
            final EcusFailureStates ecusFailureStates76 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates76);
            sparseArray76.put(VehicleProperty.HVAC_PTC_ERROR, new IntConsumer() { // from class: com.android.car.-$$Lambda$OXXQ4v1P27EzA0UsOM2FBNw0cfQ
                @Override // java.util.function.IntConsumer
                public final void accept(int i) {
                    ecusFailureStates76.setHvacPtcFailureState(i);
                }
            });
            SparseArray<Consumer<int[]>> sparseArray77 = this.mEcusIntVectorDataConsumerMap;
            final EcusFailureStates ecusFailureStates77 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates77);
            sparseArray77.put(VehicleProperty.HVAC_COMPRESSOR_ERROR_INFO, new Consumer() { // from class: com.android.car.-$$Lambda$HtOc881CMxvsI0f8EqQofq62SFA
                @Override // java.util.function.Consumer
                public final void accept(Object obj) {
                    ecusFailureStates77.setHvacCompressorFailureStates((int[]) obj);
                }
            });
            SparseArray<Consumer<int[]>> sparseArray78 = this.mEcusIntVectorDataConsumerMap;
            final EcusFailureStates ecusFailureStates78 = this.mEcusFailureStates;
            Objects.requireNonNull(ecusFailureStates78);
            sparseArray78.put(VehicleProperty.VCU_BATTERY_PTC_ERROR_INFO, new Consumer() { // from class: com.android.car.-$$Lambda$_yESXX6Lg50cwLe3W1whYNU9CdY
                @Override // java.util.function.Consumer
                public final void accept(Object obj) {
                    ecusFailureStates78.setBatteryPtcFailureStates((int[]) obj);
                }
            });
        }
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
        synchronized (this) {
            this.mHandlerThread = new HandlerThread(CarLog.TAG_XPDIAG);
            this.mHandlerThread.start();
            this.mHandler = new DiagHandler(this.mHandlerThread.getLooper());
            this.mSystemDiagContent = 0;
        }
        initEcusDataMap();
        initEcusDataConsumerMap();
        initSupportedPropConfigs();
        initSupportedCheckedPropConfigs();
        initSupportedAfterSalesPropConfigs();
        this.mPropertyService.registerListener(VehicleProperty.MCU_IG_DATA, 0.0f, this.mICarPropertyEventListener);
        this.mPropertyService.registerListener(VehicleProperty.SCU_REMOTE_FLAG, 0.0f, this.mICarPropertyEventListener);
        Set<Integer> props = getSupportedPropIds();
        if (props != null) {
            for (Integer num : props) {
                int prop = num.intValue();
                this.mPropertyService.registerListener(prop, 0.0f, this.mICarPropertyEventListener);
            }
        }
        this.mHandler.postDelayed(this.mReportEcusDiagRunnable, 6000L);
    }

    private Set<Integer> getSupportedPropIds() {
        synchronized (this.mLock) {
            if (this.mSupportedPropConfigs != null) {
                return this.mSupportedPropConfigs.keySet();
            }
            return null;
        }
    }

    private Set<Integer> getSupportedCheckedPropIds() {
        synchronized (this.mLock) {
            if (this.mSupportedCheckedPropConfigs != null) {
                return this.mSupportedCheckedPropConfigs.keySet();
            }
            return null;
        }
    }

    @Override // com.android.car.CarServiceBase
    public void release() {
        HandlerThread handlerThread;
        this.mPropertyService.unregisterListener(VehicleProperty.MCU_IG_DATA, this.mICarPropertyEventListener);
        this.mPropertyService.unregisterListener(VehicleProperty.SCU_REMOTE_FLAG, this.mICarPropertyEventListener);
        Set<Integer> props = getSupportedPropIds();
        if (props != null) {
            for (Integer num : props) {
                int prop = num.intValue();
                this.mPropertyService.unregisterListener(prop, this.mICarPropertyEventListener);
            }
        }
        synchronized (this) {
            this.mHandler.cancelAll();
            handlerThread = this.mHandlerThread;
        }
        handlerThread.quitSafely();
        try {
            handlerThread.join(1000L);
        } catch (InterruptedException e) {
            Slog.e(TAG, "Timeout while joining for handler thread to join.");
        }
        synchronized (this.mLock) {
            this.mSupportedPropConfigs.clear();
            this.mSupportedPropConfigs = null;
            this.mSupportedCheckedPropConfigs.clear();
            this.mSupportedCheckedPropConfigs = null;
            for (Client c : this.mClientMap.values()) {
                c.release();
            }
            this.mClientMap.clear();
            this.mPropIdClientMap.clear();
            this.mCallbackStatistics.release();
        }
        synchronized (this.mEcusDataLock) {
            this.mEcusIntDataConsumerMap.clear();
            this.mEcusIntDataMap.clear();
            this.mEcusIntVectorDataConsumerMap.clear();
            this.mEcusIntVectorDataMap.clear();
        }
    }

    @Override // com.android.car.CarServiceBase
    public void dump(final PrintWriter writer) {
        writer.println("*XpDiagnosticService*");
        writer.println("    Client Info: " + this.mClientMap.size());
        this.mClientMap.values().stream().sorted(Comparator.comparingInt(new ToIntFunction() { // from class: com.android.car.-$$Lambda$XpDiagnosticService$zAu4RGEIGPn_BGtPuzvGCcN3lf8
            @Override // java.util.function.ToIntFunction
            public final int applyAsInt(Object obj) {
                int i;
                i = ((XpDiagnosticService.Client) obj).pid;
                return i;
            }
        })).filter(new Predicate() { // from class: com.android.car.-$$Lambda$XpDiagnosticService$1oizBHRh1wZpUBfAm24McJrHj5I
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return XpDiagnosticService.this.lambda$dump$3$XpDiagnosticService((XpDiagnosticService.Client) obj);
            }
        }).forEach(new Consumer() { // from class: com.android.car.-$$Lambda$XpDiagnosticService$m4jewEimtS3lbQvaaxdCpLjn9nY
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                ((XpDiagnosticService.Client) obj).dump(writer);
            }
        });
        writer.println("    mPropertyToClientsMap: " + this.mPropIdClientMap.size());
        this.mPropIdClientMap.entrySet().stream().sorted(Comparator.comparing(new Function() { // from class: com.android.car.-$$Lambda$XpDiagnosticService$6nptzz1lzQOc3KFeyqEogPKaRVs
            @Override // java.util.function.Function
            public final Object apply(Object obj) {
                Integer valueOf;
                valueOf = Integer.valueOf(((List) ((Map.Entry) obj).getValue()).size());
                return valueOf;
            }
        }, Comparator.reverseOrder())).forEach(new Consumer() { // from class: com.android.car.-$$Lambda$XpDiagnosticService$PzCC4iMY2jtExM0cAMi2wvMwzlg
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                XpDiagnosticService.this.lambda$dump$8$XpDiagnosticService(writer, (Map.Entry) obj);
            }
        });
        this.mCallbackStatistics.dump(writer);
        writer.println("Ecus Int Data: ");
        int size = this.mEcusIntDataMap.size();
        for (int i = 0; i < size; i++) {
            int tmp = this.mEcusIntDataMap.keyAt(i);
            writer.println("    " + XpDebugLog.getPropertyDescription(tmp) + " = " + this.mEcusIntDataMap.valueAt(i));
        }
        writer.println("Ecus Int Vector Data: ");
        int size2 = this.mEcusIntVectorDataMap.size();
        for (int i2 = 0; i2 < size2; i2++) {
            int tmp2 = this.mEcusIntVectorDataMap.keyAt(i2);
            writer.println("    " + XpDebugLog.getPropertyDescription(tmp2) + " = " + this.mEcusIntVectorDataMap.valueAt(i2));
        }
    }

    public /* synthetic */ boolean lambda$dump$3$XpDiagnosticService(Client v) {
        return v.pid != this.mMyPid;
    }

    public /* synthetic */ void lambda$dump$8$XpDiagnosticService(final PrintWriter writer, Map.Entry cs) {
        List<Client> clients = (List) cs.getValue();
        int prop = ((Integer) cs.getKey()).intValue();
        writer.println("        " + XpDebugLog.getPropertyDescription(prop) + " Listeners size: " + clients.size());
        clients.stream().filter(new Predicate() { // from class: com.android.car.-$$Lambda$XpDiagnosticService$He3YpbHjtOTvwgcB1MRVlvqQzxM
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return XpDiagnosticService.this.lambda$dump$6$XpDiagnosticService((XpDiagnosticService.Client) obj);
            }
        }).forEach(new Consumer() { // from class: com.android.car.-$$Lambda$XpDiagnosticService$dOciQydiknQNbNgNq11ehFwZMIA
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                XpDiagnosticService.Client client = (XpDiagnosticService.Client) obj;
                writer.println("            pid: " + client.pid + "(" + client.processName + "), mListenerBinder: " + client.mListenerBinder);
            }
        });
    }

    public /* synthetic */ boolean lambda$dump$6$XpDiagnosticService(Client v) {
        return v.pid != this.mMyPid;
    }

    public void registerListener(int propId, float rate, ICarPropertyEventListener listener) {
        Pair<Integer[], Long> value;
        Pair<Integer, Long> value2;
        Slog.i(TAG, "registerListener: " + XpDebugLog.getPropertyDescription(propId) + " rate=" + rate + " pid=" + Binder.getCallingPid() + " listener:" + listener.asBinder());
        Set<Integer> ids = getSupportedPropIds();
        if (ids == null) {
            Slog.e(TAG, "registerListener:  no supported properties");
        } else if (ids.contains(Integer.valueOf(propId))) {
            ICarImpl.assertPermission(this.mContext, this.mHal.getPropertyHal().getReadPermission(propId));
            IBinder listenerBinder = listener.asBinder();
            synchronized (this.mLock) {
                Client client = this.mClientMap.get(listenerBinder);
                if (client == null) {
                    client = new Client(listener);
                }
                client.addProperty(propId, rate);
                List<Client> clients = this.mPropIdClientMap.get(Integer.valueOf(propId));
                if (clients == null) {
                    clients = new CopyOnWriteArrayList();
                    this.mPropIdClientMap.put(Integer.valueOf(propId), clients);
                }
                if (!clients.contains(client)) {
                    clients.add(client);
                }
            }
            List<CarPropertyEvent> events = new LinkedList<>();
            if (this.mEcusFailureStatePropertyIds.contains(Integer.valueOf(propId))) {
                if (this.mEcusDiagDataValid) {
                    CarPropertyConfig config = getCarPropertyConfig(propId);
                    if (config == null) {
                        Slog.e(TAG, "registerListener:  no supported properties to notify");
                        return;
                    }
                    Class<?> clazz = config.getPropertyType();
                    if (Integer.class == clazz) {
                        synchronized (this.mEcusDataLock) {
                            try {
                                value2 = this.mEcusIntDataMap.get(propId);
                            } catch (Throwable th) {
                                th = th;
                                while (true) {
                                    try {
                                        break;
                                    } catch (Throwable th2) {
                                        th = th2;
                                    }
                                }
                                throw th;
                            }
                        }
                        Integer propData = (Integer) value2.first;
                        if (-1 != propData.intValue()) {
                            events.add(new CarPropertyEvent(0, new CarPropertyValue(propId, 0, 0, ((Long) value2.second).longValue(), propData)));
                        }
                    } else if (Integer[].class == clazz) {
                        synchronized (this.mEcusDataLock) {
                            value = this.mEcusIntVectorDataMap.get(propId);
                        }
                        Integer[] propData2 = (Integer[]) value.first;
                        if (EcusFailureStates.INTEGER_VECTOR_DEFAULT_STATE != propData2) {
                            events.add(new CarPropertyEvent(0, new CarPropertyValue(propId, 0, 0, ((Long) value.second).longValue(), propData2)));
                        }
                    } else {
                        Slog.w(TAG, "Unsupported type: " + clazz + " for propId=0x" + Integer.toHexString(propId));
                    }
                } else {
                    Slog.w(TAG, "Ignore sending current data for prop: " + propId);
                }
            } else {
                try {
                    CarPropertyValue value3 = this.mHal.getPropertyHal().getProperty(propId, 0);
                    if (value3 != null) {
                        CarPropertyEvent event = new CarPropertyEvent(0, value3);
                        events.add(event);
                    }
                } catch (Exception e) {
                    if (CarLog.isGetLogEnable()) {
                        Slog.e(TAG, "get prop data failed, won't callback");
                    }
                }
            }
            if (!events.isEmpty()) {
                try {
                    listener.onEvent(events);
                } catch (RemoteException ex) {
                    Slog.e(TAG, "onEvent calling failed: " + ex.getMessage() + " for " + XpDebugLog.getPropertyDescription(propId));
                }
            }
        } else {
            Slog.e(TAG, "registerListener: " + XpDebugLog.getPropertyDescription(propId) + " is not in config list:  " + Integer.toHexString(propId));
        }
    }

    private CarPropertyConfig getCarPropertyConfig(int propId) {
        CarPropertyConfig config;
        synchronized (this.mLock) {
            config = this.mSupportedPropConfigs != null ? this.mSupportedPropConfigs.get(Integer.valueOf(propId)) : null;
        }
        return config;
    }

    public void unregisterListener(int propId, ICarPropertyEventListener listener) {
        Slog.i(TAG, "unregisterListener prop=" + XpDebugLog.getPropertyDescription(propId) + " pid=" + Binder.getCallingPid() + " listener:" + listener.asBinder());
        ICarImpl.assertPermission(this.mContext, this.mHal.getPropertyHal().getReadPermission(propId));
        IBinder listenerBinder = listener.asBinder();
        synchronized (this.mLock) {
            unregisterListenerBinderLocked(propId, listenerBinder);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void unregisterListenerBinderLocked(int propId, IBinder listenerBinder) {
        Client client = this.mClientMap.get(listenerBinder);
        List<Client> propertyClients = this.mPropIdClientMap.get(Integer.valueOf(propId));
        if (!this.mDiagPropertyIds.contains(Integer.valueOf(propId))) {
            Slog.e(TAG, "unregisterListener: " + XpDebugLog.getPropertyDescription(propId) + " is not in the config list");
        } else if (client == null || propertyClients == null) {
            Slog.e(TAG, "unregisterListenerBinderLocked: Listener was not previously registered.");
        } else if (propertyClients.remove(client)) {
            client.removeProperty(propId);
        } else {
            Slog.e(TAG, "unregisterListenerBinderLocked: Listener was not registered for propId=0x" + Integer.toHexString(propId));
        }
    }

    private void startFactorTestApp() {
        List<SystemActivityMonitoringService.TopTaskInfoContainer> topTasks = this.mSystemActivityMonitoringService.getTopTasks();
        for (SystemActivityMonitoringService.TopTaskInfoContainer t : topTasks) {
            Slog.i(TAG, "task=" + t.toString());
            if (t.topActivity.getPackageName().equals("com.xiaopeng.factory") && t.topActivity.getClassName().equals("com.xiaopeng.factory.view.factorytest.AllTestActivity")) {
                Slog.i(TAG, "task=" + t.toString() + " is on top!");
                return;
            }
        }
        Intent intent = Intent.makeMainActivity(new ComponentName("com.xiaopeng.factory", "com.xiaopeng.factory.view.factorytest.AllTestActivity"));
        intent.setFlags(268435456);
        this.mContext.startActivity(intent);
    }

    private void handleOnMcuEvent(CarPropertyEvent value) {
        dispatchEvent(value);
    }

    private void handleOnANDEvent(CarPropertyEvent value) {
        dispatchEvent(value);
    }

    private void handleOnOtherEvent(CarPropertyEvent value) {
        dispatchEvent(value);
    }

    private void dispatchEvent(CarPropertyEvent event) {
        Collection<ICarPropertyEventListener> callbacks;
        List<CarPropertyEvent> events = new ArrayList<>();
        events.add(event);
        synchronized (this) {
            callbacks = new ArraySet<>(this.mCallbacks);
        }
        for (ICarPropertyEventListener l : callbacks) {
            try {
                l.onEvent(events);
            } catch (RemoteException ex) {
                Slog.e(TAG, "onEvent calling failed: " + ex.getMessage() + " for events: " + events);
            }
        }
    }

    /*  JADX ERROR: JadxRuntimeException in pass: BlockProcessor
        jadx.core.utils.exceptions.JadxRuntimeException: Unreachable block: B:52:0x013e
        	at jadx.core.dex.visitors.blocks.BlockProcessor.checkForUnreachableBlocks(BlockProcessor.java:81)
        	at jadx.core.dex.visitors.blocks.BlockProcessor.processBlocksTree(BlockProcessor.java:47)
        	at jadx.core.dex.visitors.blocks.BlockProcessor.visit(BlockProcessor.java:39)
        */
    public void onPropertyChange(java.util.List<android.car.hardware.property.CarPropertyEvent> r22) {
        /*
            Method dump skipped, instructions count: 579
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.car.XpDiagnosticService.onPropertyChange(java.util.List):void");
    }

    public void onPropertySetError(int property, int errorCode) {
        List<Client> clients;
        boolean callbackLogEnable = CarLog.isCallbackLogEnable(property);
        if (callbackLogEnable) {
            Slog.w(TAG, "onPropertySetError " + XpDebugLog.getPropertyDescription(property) + " errorCode:" + errorCode);
        }
        synchronized (this.mLock) {
            clients = this.mPropIdClientMap.get(Integer.valueOf(property));
        }
        if (clients != null) {
            List<CarPropertyEvent> eventList = new LinkedList<>();
            eventList.add(CarPropertyEvent.createErrorEvent(property, errorCode));
            for (Client c : clients) {
                try {
                    boolean perfLogEnable = CarLog.isPerfLogEnable();
                    if (perfLogEnable) {
                        Slog.i(TAG, "++onPropertySetError pid: " + c.pid + " binder:" + c.mListenerBinder + " eventList: " + eventList);
                    }
                    c.getListener().onEvent(eventList);
                    if (perfLogEnable) {
                        Slog.i(TAG, "--onPropertySetError pid: " + c.pid + " binder:" + c.mListenerBinder + " eventList: " + eventList);
                    }
                } catch (RemoteException ex) {
                    Slog.e(TAG, "onEvent calling failed: " + ex.getMessage() + " for " + XpDebugLog.getPropertyDescription(property));
                }
            }
        } else if (callbackLogEnable) {
            Slog.e(TAG, "onPropertySetError called with no listener registered for " + XpDebugLog.getPropertyDescription(property));
        }
    }

    private void doHandleDiagEvent(CarPropertyEvent event) {
        CarPropertyValue value = event.getCarPropertyValue();
        switch (value.getPropertyId()) {
            case VehicleProperty.MCU_DIAG_REQUEST /* 560993284 */:
                handleOnMcuEvent(event);
                return;
            case VehicleProperty.AND_DIAG_REQUEST /* 560993285 */:
                handleOnANDEvent(event);
                return;
            default:
                handleOnOtherEvent(event);
                return;
        }
    }

    public byte[] getMcuLastDiagCmd() {
        return this.mPropertyService.getByteVectorProperty(VehicleProperty.MCU_DIAG_REQUEST);
    }

    public void sendMcuDiagCmdAck(byte[] cmd) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.MCU_DIAG_REQUEST, cmd);
    }

    public byte[] getAndLastDiagCmd() {
        return this.mPropertyService.getByteVectorProperty(VehicleProperty.AND_DIAG_REQUEST);
    }

    public void sendAndDiagCmd(byte[] cmd) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.AND_DIAG_REQUEST, cmd);
    }

    public int getSystemDiagContent() {
        int i;
        synchronized (this) {
            i = this.mSystemDiagContent;
        }
        return i;
    }

    public void setCarServiceDebugEnabled(boolean on) {
        synchronized (this) {
            if (on) {
                this.mSystemDiagContent |= 1;
            } else {
                this.mSystemDiagContent &= -2;
            }
        }
        this.mHal.setDebugEnabled(on);
    }

    public void sendResetPhyToMcu(byte[] cmd) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.MCU_RID_RESET_PHY, cmd);
    }

    public void sendTestModeToMcu(byte[] cmd) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.MCU_RID_TEST_MODE, cmd);
    }

    public void sendCableDiagToMcu(byte[] cmd) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.MCU_RID_CABLE_DIAGNOSTICS, cmd);
    }

    public void sendLinkStatus(byte[] cmd) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.MCU_DID_LINK_STATUS, cmd);
    }

    public void sendSQIValue(byte[] cmd) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.MCU_DID_SQI_VALUE, cmd);
    }

    public void sendTransmittedPak(byte[] cmd) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.MCU_DID_TRANSMITTED_PAK, cmd);
    }

    public void sendReceivedPak(byte[] cmd) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.MCU_DID_RECEIVED_PAK, cmd);
    }

    public void sendUnexpectedLnkLoss(byte[] cmd) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.MCU_DTC_ETH_UNEXPECTED_LOSS, cmd);
    }

    public void sendInsufficientSQI(byte[] cmd) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.MCU_DTC_INSUFFICIENT_SQI, cmd);
    }

    public void sendIpErr(byte[] cmd) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.MCU_DTC_IP_ERROR, cmd);
    }

    public void sendSQIMax(byte[] cmd) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.MCU_DID_SQI_MAX, cmd);
    }

    public void sendSQI100Base(byte[] cmd) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.MCU_DID_SQI_100BASE, cmd);
    }

    public void sendSQI1000Base(byte[] cmd) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.MCU_DID_SQI_1000BASE, cmd);
    }

    public void sendMaterSlaveMode(byte[] cmd) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.MCU_DID_PHY_SETTING, cmd);
    }

    public byte[] getMaterSlaveSettings() {
        return this.mPropertyService.getByteVectorProperty(VehicleProperty.MCU_RID_MASTERSLAVE_CONTROL);
    }

    public void sendMaterSlaveSettingsResponse(byte[] cmd) {
        this.mPropertyService.setByteVectorProperty(VehicleProperty.MCU_RID_MASTERSLAVE_CONTROL, cmd);
    }

    public int getHighBeamFailState() {
        return getIntValueOrThrow(VehicleProperty.BCM_HIGHTBEAM_FAIL);
    }

    private int getIntValueOrThrow(int key) {
        int ret;
        synchronized (this.mEcusDataLock) {
            ret = ((Integer) this.mEcusIntDataMap.get(key).first).intValue();
        }
        if (-1 == ret) {
            throw new ValueUnavailableException();
        }
        return ret;
    }

    private int[] getIntVectorValueOrThrow(int key) {
        int[] ret;
        synchronized (this.mEcusDataLock) {
            ret = CarServiceUtils.toIntArray((Integer[]) this.mEcusIntVectorDataMap.get(key).first);
        }
        if (EcusFailureStates.INT_VECTOR_DEFAULT_STATE == ret) {
            throw new ValueUnavailableException();
        }
        return ret;
    }

    public int getLowBeamFailState() {
        return getIntValueOrThrow(VehicleProperty.BCM_LOWBEAM_FAIL);
    }

    public int getLTurnLampFailState() {
        return getIntValueOrThrow(VehicleProperty.BCM_LTURNLAMP_FAIL);
    }

    public int getRTurnLampFailState() {
        return getIntValueOrThrow(VehicleProperty.BCM_RTURNLAMP_FAIL);
    }

    public int getRearFogFailState() {
        return getIntValueOrThrow(VehicleProperty.BCM_REARFOG_FAIL);
    }

    public int getRDtrOutputFailState() {
        return getIntValueOrThrow(VehicleProperty.BCM_RDTROUTPUT_FAIL);
    }

    public int getLDtrOutputFailState() {
        return getIntValueOrThrow(VehicleProperty.BCM_LDTROUTPUT_FAIL);
    }

    public int getParkingLampFailState() {
        return getIntValueOrThrow(VehicleProperty.BCM_PARKINGLAMP_FAIL);
    }

    public int getSysErrorWarn() {
        return getIntValueOrThrow(VehicleProperty.BCM_SYSERROR_WARM);
    }

    public int getLluErrSt() {
        return getIntValueOrThrow(VehicleProperty.LLU_ERR_ST);
    }

    public int getAtlsErrSt() {
        return getIntValueOrThrow(VehicleProperty.ATLS_ERR_ST);
    }

    public int getMsmdEcuErr() {
        return getIntValueOrThrow(VehicleProperty.MSMD_ECU_ERR);
    }

    public int getMsmdVentilationMotorErr() {
        return getIntValueOrThrow(VehicleProperty.MSMD_VENTILATIONMOTOR_ERR);
    }

    public int getMsmdHeatSysErr() {
        return getIntValueOrThrow(VehicleProperty.MSMD_HEATSYS_ERR);
    }

    public int getMsmpEcuErr() {
        return getIntValueOrThrow(VehicleProperty.MSMP_ECU_ERR);
    }

    public int getMsmpHeatSysErr() {
        return getIntValueOrThrow(VehicleProperty.MSMP_HEATSYS_ERR);
    }

    public int getAvasFault() {
        return getIntValueOrThrow(VehicleProperty.AVAS_FAULT);
    }

    public int getTpmsSysFaultWarn() {
        return getIntValueOrThrow(VehicleProperty.TPMS_SYSFAULTWARN);
    }

    public int getTpmsAbnormalPrWarn() {
        return getIntValueOrThrow(VehicleProperty.TPMS_ABNORMALPRWARN);
    }

    public int getAirbagFaultSt() {
        return getIntValueOrThrow(VehicleProperty.BCM_AIRBAG_FAULT_ST);
    }

    public int getAlsErrorSt() {
        return getIntValueOrThrow(VehicleProperty.ALS_ERROR_ST);
    }

    public int getDhcErr() {
        return getIntValueOrThrow(VehicleProperty.DHC_ERR);
    }

    public int getVcuEvErrLampDsp() {
        return getIntValueOrThrow(VehicleProperty.VCU_EVERRLAMP_DSP);
    }

    public int getVcuGearWarning() {
        return getIntValueOrThrow(VehicleProperty.VCU_GEAR_WARNING);
    }

    public int getVcuEbsErrDsp() {
        return getIntValueOrThrow(VehicleProperty.VCU_EBS_ERR_DSP);
    }

    public int getVcuLiquidHighTempErr() {
        return getIntValueOrThrow(VehicleProperty.VCU_LIQUIDHIGHTEMP_ERR);
    }

    public int getVcuWaterSensorErr() {
        return getIntValueOrThrow(VehicleProperty.VCU_WATERSENSOR_ERR);
    }

    public int getVcuBCruiseErr() {
        return getIntValueOrThrow(VehicleProperty.VCU_BCRUISE_ERR);
    }

    public int getVcuThermoRunawaySt() {
        return getIntValueOrThrow(VehicleProperty.VCU_THERMORUNAWAY_ST);
    }

    public int getVcuPowerLimitationDsp() {
        return getIntValueOrThrow(VehicleProperty.VCU_POWERLIMITATION_DSP);
    }

    public int getVcuChgPortHotDsp() {
        return getIntValueOrThrow(VehicleProperty.VCU_CHGPORTHOT_DSP);
    }

    public int getVcuObcMsgLost() {
        return getIntValueOrThrow(VehicleProperty.VCU_OBCMSG_LOST);
    }

    public int getVcuDeadBatteryFlag() {
        return getIntValueOrThrow(VehicleProperty.VCU_DEADBATTERY_FLAG);
    }

    public int getEspEspFault() {
        return getIntValueOrThrow(VehicleProperty.ESP_ESP_FAULT);
    }

    public int getEspApbErrSt() {
        return getIntValueOrThrow(VehicleProperty.ESP_APBERR_ST);
    }

    public int getEspAbsFault() {
        return getIntValueOrThrow(VehicleProperty.ESP_ABS_FAULT);
    }

    public int getEspAvhFault() {
        return getIntValueOrThrow(VehicleProperty.ESP_AVH_FAULT);
    }

    public int getIbtFailureLamp() {
        return getIntValueOrThrow(VehicleProperty.IBT_FAILURE_LAMP);
    }

    public int getEpsWarnLamp() {
        return getIntValueOrThrow(VehicleProperty.ESP_WARN_LAMP);
    }

    public int getCdcDataUploadSt() {
        return getIntValueOrThrow(VehicleProperty.CDC_DATAUPLOAD_ST);
    }

    public int getXpuXpuFailState() {
        return getIntValueOrThrow(VehicleProperty.XPU_XPU_FAIL_ST);
    }

    public int getScuIhbSw() {
        return getIntValueOrThrow(VehicleProperty.SCU_IHB_SW);
    }

    public int getScuBsdSw() {
        return getIntValueOrThrow(VehicleProperty.SCU_BSD_SW);
    }

    public int getScuDowSw() {
        return getIntValueOrThrow(VehicleProperty.SCU_DOW_SW);
    }

    public int getScuRctaSw() {
        return getIntValueOrThrow(VehicleProperty.SCU_RCTA_SW);
    }

    public int getScuRcwSw() {
        return getIntValueOrThrow(VehicleProperty.SCU_RCW_SW);
    }

    public int getSrrFlFailState() {
        return getIntValueOrThrow(VehicleProperty.SRR_FLFAIL_ST);
    }

    public int getSrrFrFailState() {
        return getIntValueOrThrow(VehicleProperty.SRR_FRFAIL_ST);
    }

    public int getSrrRlFailState() {
        return getIntValueOrThrow(VehicleProperty.SRR_RLFAIL_ST);
    }

    public int getSrrRrFailState() {
        return getIntValueOrThrow(VehicleProperty.SRR_RRFAIL_ST);
    }

    public int getScuLongCtrlRemind() {
        return getIntValueOrThrow(VehicleProperty.SCU_LONGCTRL_REMIND);
    }

    public int getScuIslcSw() {
        return getIntValueOrThrow(VehicleProperty.SCU_ISLC_SW);
    }

    public int getScuAlcCtrlRemind() {
        return getIntValueOrThrow(VehicleProperty.SCU_ALCCTRL_REMIND);
    }

    public int getScuIcmApaErrTips() {
        return getIntValueOrThrow(VehicleProperty.SCU_ICM_APAERR_TIPS);
    }

    public int getFrontIpuFailureState() {
        return getIntValueOrThrow(VehicleProperty.IPUF_FAILST);
    }

    public int getRearIpuFailureState() {
        return getIntValueOrThrow(VehicleProperty.IPUR_FAILST);
    }

    public int getVcuEvErrorMessage() {
        return this.mPropertyService.getIntProperty(VehicleProperty.VCU_EVERR_MSGDISP, 0);
    }

    public int getEpbSysFailureState() {
        return getIntValueOrThrow(VehicleProperty.ESP_SYS_WARNIND_REQ);
    }

    public int getHdcFailureState() {
        return getIntValueOrThrow(VehicleProperty.ESP_HDC_FAULT);
    }

    public int[] getAllTireTemperatureWarnings() {
        return getIntVectorValueOrThrow(VehicleProperty.TPMS_TEMPWARN_ALL);
    }

    public int[] getAllTirePerssureSensorsFailureStates() {
        return getIntVectorValueOrThrow(VehicleProperty.TPMS_ALL_SENSOR_ST);
    }

    public int getBatteryOverheatingState() {
        return getIntValueOrThrow(VehicleProperty.VCU_BATHOT_DSP);
    }

    public int getCcsWorkState() {
        return getIntValueOrThrow(VehicleProperty.VCU_CCS_WORK_ST);
    }

    public int getDcdcFailureState() {
        return getIntValueOrThrow(VehicleProperty.VCU_DCDC_ERROR);
    }

    public int getBatteryVoltageLowState() {
        return getIntValueOrThrow(VehicleProperty.VCU_BAT_VOLT_LOW);
    }

    public int getElectricMotorSystemHotState() {
        return getIntValueOrThrow(VehicleProperty.VCU_EMOTOR_SYSTEM_HOT);
    }

    public int getElectricVacuumPumpFailureState() {
        return getIntValueOrThrow(VehicleProperty.VCU_EVACUUM_PUMP_ERROR);
    }

    public int getHighVoltageRelayAdhesionState() {
        return getIntValueOrThrow(VehicleProperty.VCU_HVRLY_ADHESION_ST);
    }

    public int getAgsFailureState() {
        return getIntValueOrThrow(VehicleProperty.VCU_AGS_ERROR);
    }

    public int[] getTcmMotorFailureStates() {
        return getIntVectorValueOrThrow(VehicleProperty.VCU_TCM_MOTOR);
    }

    public int[] getAllMsmModulesFailureStates() {
        return getIntVectorValueOrThrow(VehicleProperty.BCM_MSM_ERROR_INFO);
    }

    public int getDvrFailureState() {
        return getIntValueOrThrow(VehicleProperty.CIU_DVR_STATUS);
    }

    public int getCiuSdcardFailureState() {
        return getIntValueOrThrow(VehicleProperty.CIU_SD_ST);
    }

    public int getLdwState() {
        return getIntValueOrThrow(VehicleProperty.SCU_LDW_SW);
    }

    public int getAebState() {
        return getIntValueOrThrow(VehicleProperty.SCU_FCWAEB_SW);
    }

    public int getSideReversingState() {
        return getIntValueOrThrow(VehicleProperty.SCU_SIDE_REVERSION_WARNING);
    }

    public int getHvacPtcFailureState() {
        return getIntValueOrThrow(VehicleProperty.HVAC_PTC_ERROR);
    }

    public int[] getHvacCompressorFailureStates() {
        return getIntVectorValueOrThrow(VehicleProperty.HVAC_COMPRESSOR_ERROR_INFO);
    }

    public int[] getBatteryPtcFailureStates() {
        return getIntVectorValueOrThrow(VehicleProperty.VCU_BATTERY_PTC_ERROR_INFO);
    }

    public EcusFailureStates getAllEcusFailureStates() {
        return getCurrentEcusDiagData();
    }

    @SuppressLint({"NewApi"})
    private EcusFailureStates buildCurrentEcusDiagData() {
        EcusFailureStates data = new EcusFailureStates();
        synchronized (this.mEcusDataLock) {
            data.setHighBeamFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.BCM_HIGHTBEAM_FAIL).first).intValue());
            data.setLowBeamFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.BCM_LOWBEAM_FAIL).first).intValue());
            data.setLeftTurnLampFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.BCM_LTURNLAMP_FAIL).first).intValue());
            data.setRightTurnLampFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.BCM_RTURNLAMP_FAIL).first).intValue());
            data.setRearFogLampFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.BCM_REARFOG_FAIL).first).intValue());
            data.setRightDaytimeRunningLightFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.BCM_RDTROUTPUT_FAIL).first).intValue());
            data.setLeftDaytimeRunningLightFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.BCM_LDTROUTPUT_FAIL).first).intValue());
            data.setParkingLampFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.BCM_PARKINGLAMP_FAIL).first).intValue());
            data.setBcmSystemFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.BCM_SYSERROR_WARM).first).intValue());
            data.setLluFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.LLU_ERR_ST).first).intValue());
            data.setAtlsFailureSate(((Integer) this.mEcusIntDataMap.get(VehicleProperty.ATLS_ERR_ST).first).intValue());
            data.setDriverMsmFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.MSMD_ECU_ERR).first).intValue());
            data.setDriverMsmVentilationMotorFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.MSMD_VENTILATIONMOTOR_ERR).first).intValue());
            data.setDriverMsmHeatSysFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.MSMD_HEATSYS_ERR).first).intValue());
            data.setPassengerMsmFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.MSMP_ECU_ERR).first).intValue());
            data.setPassengerMsmHeatSysFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.MSMP_HEATSYS_ERR).first).intValue());
            data.setAvasFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.AVAS_FAULT).first).intValue());
            data.setTpmsSysFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.TPMS_SYSFAULTWARN).first).intValue());
            data.setAbnormalTirePressureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.TPMS_ABNORMALPRWARN).first).intValue());
            data.setAirbagFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.BCM_AIRBAG_FAULT_ST).first).intValue());
            data.setAlsFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.ALS_ERROR_ST).first).intValue());
            data.setDhcFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.DHC_ERR).first).intValue());
            data.setEvSysFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.VCU_EVERRLAMP_DSP).first).intValue());
            data.setGearWarningInfo(((Integer) this.mEcusIntDataMap.get(VehicleProperty.VCU_GEAR_WARNING).first).intValue());
            data.setEbsFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.VCU_EBS_ERR_DSP).first).intValue());
            data.setLiquidHighTempState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.VCU_LIQUIDHIGHTEMP_ERR).first).intValue());
            data.setWaterSensorFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.VCU_WATERSENSOR_ERR).first).intValue());
            data.setBCruiseFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.VCU_BCRUISE_ERR).first).intValue());
            data.setThermalRunawayState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.VCU_THERMORUNAWAY_ST).first).intValue());
            data.setPowerLimitationState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.VCU_POWERLIMITATION_DSP).first).intValue());
            data.setChargePortHotState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.VCU_CHGPORTHOT_DSP).first).intValue());
            data.setObcMsgLostState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.VCU_OBCMSG_LOST).first).intValue());
            data.setBatteryDeadState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.VCU_DEADBATTERY_FLAG).first).intValue());
            data.setEspFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.ESP_ESP_FAULT).first).intValue());
            data.setApbFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.ESP_APBERR_ST).first).intValue());
            data.setAbsFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.ESP_ABS_FAULT).first).intValue());
            data.setAvhFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.ESP_AVH_FAULT).first).intValue());
            data.setIbtFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.IBT_FAILURE_LAMP).first).intValue());
            data.setEpsFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.ESP_WARN_LAMP).first).intValue());
            data.setCdcFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.CDC_DATAUPLOAD_ST).first).intValue());
            data.setXpuFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.XPU_XPU_FAIL_ST).first).intValue());
            data.setIhbState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.SCU_IHB_SW).first).intValue());
            data.setBsdState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.SCU_BSD_SW).first).intValue());
            data.setDowState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.SCU_DOW_SW).first).intValue());
            data.setRctaState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.SCU_RCTA_SW).first).intValue());
            data.setRcwState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.SCU_RCW_SW).first).intValue());
            data.setFlSrrFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.SRR_FLFAIL_ST).first).intValue());
            data.setFrSrrFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.SRR_FRFAIL_ST).first).intValue());
            data.setRlSrrFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.SRR_RLFAIL_ST).first).intValue());
            data.setRrSrrFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.SRR_RRFAIL_ST).first).intValue());
            data.setScuLongCtrlRemindInfo(((Integer) this.mEcusIntDataMap.get(VehicleProperty.SCU_LONGCTRL_REMIND).first).intValue());
            data.setIslcState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.SCU_ISLC_SW).first).intValue());
            data.setAlcCtrlRemindInfo(((Integer) this.mEcusIntDataMap.get(VehicleProperty.SCU_ALCCTRL_REMIND).first).intValue());
            data.setApaFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.SCU_ICM_APAERR_TIPS).first).intValue());
            data.setFrontIpuFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.IPUF_FAILST).first).intValue());
            data.setRearIpuFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.IPUR_FAILST).first).intValue());
            data.setEpbSysFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.ESP_SYS_WARNIND_REQ).first).intValue());
            data.setHdcFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.ESP_HDC_FAULT).first).intValue());
            data.setAllTireTemperatureWarnings(CarServiceUtils.toIntArray((Integer[]) this.mEcusIntVectorDataMap.get(VehicleProperty.TPMS_TEMPWARN_ALL).first));
            data.setAllTirePerssureSensorsFailureStates(CarServiceUtils.toIntArray((Integer[]) this.mEcusIntVectorDataMap.get(VehicleProperty.TPMS_ALL_SENSOR_ST).first));
            data.setBatteryOverheatingState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.VCU_BATHOT_DSP).first).intValue());
            data.setCcsWorkState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.VCU_CCS_WORK_ST).first).intValue());
            data.setDcdcFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.VCU_DCDC_ERROR).first).intValue());
            data.setBatteryVoltageLowState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.VCU_BAT_VOLT_LOW).first).intValue());
            data.setElectricMotorSystemHotState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.VCU_EMOTOR_SYSTEM_HOT).first).intValue());
            data.setElectricVacuumPumpFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.VCU_EVACUUM_PUMP_ERROR).first).intValue());
            data.setHighVoltageRelayAdhesionState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.VCU_HVRLY_ADHESION_ST).first).intValue());
            data.setAgsFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.VCU_AGS_ERROR).first).intValue());
            data.setTcmMotorFailureStates(CarServiceUtils.toIntArray((Integer[]) this.mEcusIntVectorDataMap.get(VehicleProperty.VCU_TCM_MOTOR).first));
            data.setAllMsmModulesFailureStates(CarServiceUtils.toIntArray((Integer[]) this.mEcusIntVectorDataMap.get(VehicleProperty.BCM_MSM_ERROR_INFO).first));
            data.setDvrFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.CIU_DVR_STATUS).first).intValue());
            data.setCiuSdcardFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.CIU_SD_ST).first).intValue());
            data.setLdwState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.SCU_LDW_SW).first).intValue());
            data.setAebState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.SCU_FCWAEB_SW).first).intValue());
            data.setSideReversingState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.SCU_SIDE_REVERSION_WARNING).first).intValue());
            data.setHvacPtcFailureState(((Integer) this.mEcusIntDataMap.get(VehicleProperty.HVAC_PTC_ERROR).first).intValue());
            data.setHvacCompressorFailureStates(CarServiceUtils.toIntArray((Integer[]) this.mEcusIntVectorDataMap.get(VehicleProperty.HVAC_COMPRESSOR_ERROR_INFO).first));
            data.setBatteryPtcFailureStates(CarServiceUtils.toIntArray((Integer[]) this.mEcusIntVectorDataMap.get(VehicleProperty.VCU_BATTERY_PTC_ERROR_INFO).first));
        }
        return data;
    }

    @SuppressLint({"NewApi"})
    private EcusFailureStates getCurrentEcusDiagData() {
        EcusFailureStates ecusFailureStates;
        Set<Integer> props = getSupportedCheckedPropIds();
        if (props == null) {
            return this.mEcusFailureStates;
        }
        synchronized (this.mEcusDataLock) {
            for (Integer prop : props) {
                CarPropertyConfig config = getCarPropertyConfig(prop.intValue());
                if (config != null) {
                    Class<?> clazz = config.getPropertyType();
                    if (Integer.class == clazz) {
                        this.mEcusIntDataConsumerMap.get(prop.intValue()).accept(((Integer) this.mEcusIntDataMap.get(prop.intValue()).first).intValue());
                    } else if (Integer[].class == clazz) {
                        this.mEcusIntVectorDataConsumerMap.get(prop.intValue()).accept(CarServiceUtils.toIntArray((Integer[]) this.mEcusIntVectorDataMap.get(prop.intValue()).first));
                    } else {
                        Slog.w(TAG, "Unsupported class:" + clazz + " for propId=0x" + Integer.toHexString(prop.intValue()));
                    }
                }
            }
            ecusFailureStates = this.mEcusFailureStates;
        }
        return ecusFailureStates;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleTboxConnectStateChanged(CarPropertyValue<?> carPropertyValue) {
        this.mTboxConnectedState.set(((Integer) carPropertyValue.getValue()).intValue());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleIcmConnectStateChanged(CarPropertyValue<?> carPropertyValue) {
        this.mIcmConnectedState.set(((Integer) carPropertyValue.getValue()).intValue());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleIgStateChanged(CarPropertyValue<?> carPropertyValue) {
        int igState = ((Integer) carPropertyValue.getValue()).intValue();
        Slog.i(TAG, "ig state: " + igState);
        this.mHandler.removeCallbacks(this.mCheckConnectedStateRunnable);
        if (1 == igState) {
            this.mHandler.postDelayed(this.mCheckConnectedStateRunnable, CHECK_ICM_TBOX_CONNECTED_INTERVAL.toMillis());
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class Client implements IBinder.DeathRecipient {
        private final ICarPropertyEventListener mListener;
        private final IBinder mListenerBinder;
        final String processName;
        private final SparseArray<Float> mRateMap = new SparseArray<>();
        final int pid = Binder.getCallingPid();
        final int uid = Binder.getCallingUid();

        static /* synthetic */ IBinder access$400(Client x0) {
            return x0.mListenerBinder;
        }

        Client(ICarPropertyEventListener listener) {
            this.mListener = listener;
            this.mListenerBinder = listener.asBinder();
            this.processName = ProcessUtils.getProcessName(XpDiagnosticService.this.mContext, this.pid, this.uid);
            try {
                this.mListenerBinder.linkToDeath(this, 0);
                XpDiagnosticService.this.mClientMap.put(this.mListenerBinder, this);
            } catch (RemoteException e) {
                Slog.e(XpDiagnosticService.TAG, "Failed to link death for recipient. " + e);
                throw new IllegalStateException("CarNotConnected");
            }
        }

        void addProperty(int propId, float rate) {
            this.mRateMap.put(propId, Float.valueOf(rate));
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            Slog.i(XpDiagnosticService.TAG, "binderDied " + this.mListenerBinder);
            while (this.mRateMap.size() != 0) {
                int propId = this.mRateMap.keyAt(0);
                synchronized (XpDiagnosticService.this.mLock) {
                    XpDiagnosticService.this.unregisterListenerBinderLocked(propId, this.mListenerBinder);
                }
            }
            XpDiagnosticService.this.mCallbackStatistics.removeProcess(this.processName, this.pid, this.mListenerBinder);
        }

        ICarPropertyEventListener getListener() {
            return this.mListener;
        }

        IBinder getListenerBinder() {
            return this.mListenerBinder;
        }

        float getRate(int propId) {
            return this.mRateMap.get(propId, Float.valueOf(0.0f)).floatValue();
        }

        void release() {
            this.mListenerBinder.unlinkToDeath(this, 0);
            XpDiagnosticService.this.mClientMap.remove(this.mListenerBinder);
        }

        void removeProperty(int propId) {
            this.mRateMap.remove(propId);
            if (this.mRateMap.size() == 0) {
                release();
            }
        }

        public void dump(PrintWriter writer) {
            StringBuilder sb = new StringBuilder();
            sb.append("        ");
            sb.append(this.processName);
            sb.append("(Pid:");
            sb.append(this.pid);
            sb.append(") mListenerBinder:");
            sb.append(this.mListenerBinder);
            sb.append(" mRateMap:");
            StringBuilder builder = sb.append(rateMapToString());
            writer.println(builder.toString());
        }

        public String getDescriptionString() {
            return "Client(" + this.processName + ":" + this.pid + ", " + this.mListenerBinder + ")";
        }

        private String rateMapToString() {
            synchronized (XpDiagnosticService.this.mLock) {
                int size = this.mRateMap.size();
                if (size <= 0) {
                    return "{}";
                }
                StringBuilder buffer = new StringBuilder(size * 48);
                buffer.append('{');
                for (int i = 0; i < size; i++) {
                    if (i > 0) {
                        buffer.append(", ");
                    }
                    int key = this.mRateMap.keyAt(i);
                    buffer.append(XpDebugLog.getPropertyName(key));
                    buffer.append(" : ");
                    buffer.append(this.mRateMap.valueAt(i));
                }
                buffer.append('}');
                return buffer.toString();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class DiagHandler extends Handler {
        private final int MSG_DIAG_EVENT;

        private DiagHandler(Looper looper) {
            super(looper);
            this.MSG_DIAG_EVENT = 0;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void handleDiagEvents(List<CarPropertyEvent> events) {
            Message msg = obtainMessage(0, events);
            sendMessage(msg);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void cancelAll() {
            removeCallbacksAndMessages(null);
        }

        @Override // android.os.Handler
        @SuppressLint({"NewApi"})
        public void handleMessage(Message msg) {
            if (msg.what == 0) {
                try {
                    handleDiagMsg(msg);
                } catch (Exception e) {
                    Slog.e(XpDiagnosticService.TAG, "Handling diag msg got exception: " + e);
                }
            }
        }

        private void handleDiagMsg(Message msg) {
            List<CarPropertyEvent> events = (List) msg.obj;
            if (events != null && !events.isEmpty()) {
                List<CarPropertyEvent> onChangeList = (List) events.stream().filter(new Predicate() { // from class: com.android.car.-$$Lambda$XpDiagnosticService$DiagHandler$YNHYqwLaR2i6mhkYoes5_ENk_qM
                    @Override // java.util.function.Predicate
                    public final boolean test(Object obj) {
                        return XpDiagnosticService.DiagHandler.lambda$handleDiagMsg$0((CarPropertyEvent) obj);
                    }
                }).collect(Collectors.toList());
                if (!onChangeList.isEmpty()) {
                    onChangeList.forEach(new Consumer() { // from class: com.android.car.-$$Lambda$XpDiagnosticService$DiagHandler$Bgt5oGf4xyHViouNrhGR7UBKEWk
                        @Override // java.util.function.Consumer
                        public final void accept(Object obj) {
                            XpDiagnosticService.DiagHandler.this.lambda$handleDiagMsg$1$XpDiagnosticService$DiagHandler((CarPropertyEvent) obj);
                        }
                    });
                }
                XpDiagnosticService.this.onPropertyChange(events);
                return;
            }
            Slog.w(XpDiagnosticService.TAG, "events is null");
        }

        /* JADX INFO: Access modifiers changed from: package-private */
        public static /* synthetic */ boolean lambda$handleDiagMsg$0(CarPropertyEvent v) {
            return v.getEventType() == 0;
        }

        public /* synthetic */ void lambda$handleDiagMsg$1$XpDiagnosticService$DiagHandler(CarPropertyEvent v) {
            CarPropertyValue<?> carPropertyValue = v.getCarPropertyValue();
            int propertyId = carPropertyValue.getPropertyId();
            if (XpDiagnosticService.this.mEcusFailureStatePropertyIds.contains(Integer.valueOf(propertyId))) {
                XpDiagnosticService.this.handleEcuFailureStateChanged(carPropertyValue, propertyId);
            } else if (557848078 == propertyId) {
                XpDiagnosticService.this.handleIcmConnectStateChanged(carPropertyValue);
            } else if (557846543 == propertyId) {
                XpDiagnosticService.this.handleTboxConnectStateChanged(carPropertyValue);
            } else if (557847561 == propertyId) {
                XpDiagnosticService.this.handleIgStateChanged(carPropertyValue);
            } else if (557852377 == propertyId) {
                XpDiagnosticService.this.mHal.setXpuFlag(((Integer) carPropertyValue.getValue()).intValue());
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleEcuFailureStateChanged(CarPropertyValue<?> carPropertyValue, int propertyId) {
        CarPropertyConfig config = getCarPropertyConfig(propertyId);
        if (config != null) {
            Class<?> clazz = config.getPropertyType();
            if (Integer.class == clazz) {
                synchronized (this.mEcusDataLock) {
                    this.mEcusIntDataMap.put(propertyId, Pair.create((Integer) carPropertyValue.getValue(), Long.valueOf(carPropertyValue.getTimestamp())));
                }
            } else if (Integer[].class == clazz) {
                synchronized (this.mEcusDataLock) {
                    this.mEcusIntVectorDataMap.put(propertyId, Pair.create((Integer[]) carPropertyValue.getValue(), Long.valueOf(carPropertyValue.getTimestamp())));
                }
            } else {
                Slog.w(TAG, "Unsupported class:" + clazz + " for propId=0x" + Integer.toHexString(propertyId));
            }
        }
    }
}
