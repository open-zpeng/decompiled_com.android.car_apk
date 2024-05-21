package com.android.car;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.car.ICar;
import android.car.cluster.renderer.IInstrumentClusterNavigation;
import android.car.userlib.CarUserManagerHelper;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.hardware.automotive.vehicle.V2_0.IVehicle;
import android.hardware.automotive.vehicle.V2_0.VehicleArea;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Slog;
import android.util.TimeUtils;
import android.util.TimingsTraceLog;
import com.android.car.Manifest;
import com.android.car.am.FixedActivityService;
import com.android.car.audio.CarAudioService;
import com.android.car.cluster.InstrumentClusterService;
import com.android.car.garagemode.GarageModeService;
import com.android.car.hal.VehicleHal;
import com.android.car.intelligent.CarIntelligentEngineService;
import com.android.car.permission.CarPermissionManager;
import com.android.car.permission.CarPermissionManagerService;
import com.android.car.pm.CarPackageManagerService;
import com.android.car.stats.CarStatsService;
import com.android.car.systeminterface.SystemInterface;
import com.android.car.trust.CarTrustedDeviceService;
import com.android.car.user.CarUserNoticeService;
import com.android.car.user.CarUserService;
import com.android.car.vms.VmsBrokerService;
import com.android.car.vms.VmsClientManager;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.car.ICarServiceHelper;
import com.android.settingslib.accessibility.AccessibilityUtils;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import kotlin.text.Typography;
/* loaded from: classes3.dex */
public class ICarImpl extends ICar.Stub {
    private static final String AOSP_DIAGNOSTIC_SERVICE = "diagnostic";
    private static final boolean ENABLE_BLUETOOTH_SERVICE = false;
    public static final String INTERNAL_INPUT_SERVICE = "internal_input";
    public static final String INTERNAL_SYSTEM_ACTIVITY_MONITORING_SERVICE = "system_activity_monitoring";
    public static final String INTERNAL_VMS_MANAGER = "vms_manager";
    private static final String TAG = "ICarImpl";
    private static final String VHAL_TIMING_TAG = "VehicleHalTiming";
    @GuardedBy({"mPermmissionCached"})
    private static final Map<Integer, List<String>> mPermmissionCached = new ConcurrentHashMap();
    private final AirplaneModeService mAirplaneModeService;
    private final CarServiceBase[] mAllServices;
    private final AppFocusService mAppFocusService;
    private TimingsTraceLog mBootTiming;
    private final CarAudioService mCarAudioService;
    private final CarBluetoothService mCarBluetoothService;
    private final CarBugreportManagerService mCarBugreportManagerService;
    private final CarConditionService mCarConditionService;
    private final CarConfigurationService mCarConfigurationService;
    private final CarDiagnosticService mCarDiagnosticService;
    private final CarDrivingStateService mCarDrivingStateService;
    private final CarInputService mCarInputService;
    private final CarIntelligentEngineService mCarIntelligentEngineService;
    private final CarLocationService mCarLocationService;
    private final CarMediaService mCarMediaService;
    private final CarNightService mCarNightService;
    private final CarPackageManagerService mCarPackageManagerService;
    private final CarPermissionManagerService mCarPermissionManagerService;
    private final CarPowerManagementService mCarPowerManagementService;
    private final CarProjectionService mCarProjectionService;
    private final CarPropertyService mCarPropertyService;
    private final CarStatsService mCarStatsService;
    private final CarStorageMonitoringService mCarStorageMonitoringService;
    @GuardedBy({"this"})
    private CarTestService mCarTestService;
    private final CarTrustedDeviceService mCarTrustedDeviceService;
    private final CarUxRestrictionsManagerService mCarUXRestrictionsService;
    private final CarUserNoticeService mCarUserNoticeService;
    private final CarUserService mCarUserService;
    private final Context mContext;
    private final CarServiceBase[] mDumpServices;
    private final FixedActivityService mFixedActivityService;
    private final GarageModeService mGarageModeService;
    private final VehicleHal mHal;
    @GuardedBy({"this"})
    private ICarServiceHelper mICarServiceHelper;
    private final InstrumentClusterService mInstrumentClusterService;
    private final PerUserCarServiceHelper mPerUserCarServiceHelper;
    private final long mStartTime = SystemClock.elapsedRealtime();
    private final SystemActivityMonitoringService mSystemActivityMonitoringService;
    private final SystemInterface mSystemInterface;
    private final SystemStateControllerService mSystemStateControllerService;
    private final CarUserManagerHelper mUserManagerHelper;
    private final String mVehicleInterfaceName;
    private final VmsBrokerService mVmsBrokerService;
    private final VmsClientManager mVmsClientManager;
    private final VmsPublisherService mVmsPublisherService;
    private final VmsSubscriberService mVmsSubscriberService;
    private final XpDiagnosticService mXpDiagnosticService;
    private final XpSharedMemoryService mXpSharedMemoryService;
    private final XpUpdateTimeService mXpUpdateTimeService;
    private final XpVehicleService mXpVehicleService;

    public ICarImpl(Context serviceContext, IVehicle vehicle, SystemInterface systemInterface, CanBusErrorNotifier errorNotifier, String vehicleInterfaceName) {
        this.mContext = serviceContext;
        this.mSystemInterface = systemInterface;
        this.mHal = new VehicleHal(serviceContext, vehicle);
        this.mHal.setContext(serviceContext);
        this.mVehicleInterfaceName = vehicleInterfaceName;
        this.mUserManagerHelper = new CarUserManagerHelper(serviceContext);
        Resources res = this.mContext.getResources();
        int maxRunningUsers = res.getInteger(17694846);
        this.mCarUserService = new CarUserService(serviceContext, this.mUserManagerHelper, ActivityManager.getService(), maxRunningUsers);
        this.mSystemActivityMonitoringService = new SystemActivityMonitoringService(serviceContext);
        this.mCarPowerManagementService = new CarPowerManagementService(this.mContext, this.mHal.getPowerHal(), systemInterface, this.mUserManagerHelper);
        this.mCarUserNoticeService = new CarUserNoticeService(serviceContext);
        this.mCarPropertyService = new CarPropertyService(serviceContext, this.mHal.getPropertyHal());
        this.mCarDrivingStateService = new CarDrivingStateService(serviceContext, this.mCarPropertyService);
        this.mCarUXRestrictionsService = new CarUxRestrictionsManagerService(serviceContext, this.mCarDrivingStateService, this.mCarPropertyService);
        this.mCarPackageManagerService = new CarPackageManagerService(serviceContext, this.mCarUXRestrictionsService, this.mSystemActivityMonitoringService, this.mUserManagerHelper);
        this.mPerUserCarServiceHelper = new PerUserCarServiceHelper(serviceContext);
        this.mCarBluetoothService = new CarBluetoothService(serviceContext, this.mPerUserCarServiceHelper);
        this.mCarInputService = new CarInputService(serviceContext, this.mHal.getInputHal());
        this.mCarProjectionService = new CarProjectionService(serviceContext, null, this.mCarInputService, this.mCarBluetoothService);
        this.mGarageModeService = new GarageModeService(this.mContext);
        this.mAppFocusService = new AppFocusService(serviceContext, this.mSystemActivityMonitoringService);
        this.mCarAudioService = new CarAudioService(serviceContext);
        this.mCarNightService = new CarNightService(serviceContext, this.mCarPropertyService);
        this.mFixedActivityService = new FixedActivityService(serviceContext);
        this.mInstrumentClusterService = new InstrumentClusterService(serviceContext, this.mAppFocusService, this.mCarInputService);
        this.mSystemStateControllerService = new SystemStateControllerService(serviceContext, this.mCarAudioService, this);
        this.mCarStatsService = new CarStatsService(serviceContext);
        this.mVmsBrokerService = new VmsBrokerService();
        this.mVmsClientManager = new VmsClientManager(serviceContext, this.mCarStatsService, this.mCarUserService, this.mVmsBrokerService, this.mHal.getVmsHal());
        this.mVmsSubscriberService = new VmsSubscriberService(serviceContext, this.mVmsBrokerService, this.mVmsClientManager, this.mHal.getVmsHal());
        this.mVmsPublisherService = new VmsPublisherService(serviceContext, this.mCarStatsService, this.mVmsBrokerService, this.mVmsClientManager);
        this.mCarDiagnosticService = new CarDiagnosticService(serviceContext, this.mHal.getDiagnosticHal());
        this.mCarStorageMonitoringService = new CarStorageMonitoringService(serviceContext, systemInterface);
        this.mCarConfigurationService = new CarConfigurationService(serviceContext, new JsonReaderImpl());
        this.mCarLocationService = new CarLocationService(this.mContext, this.mUserManagerHelper);
        this.mCarTrustedDeviceService = new CarTrustedDeviceService(serviceContext);
        this.mCarMediaService = new CarMediaService(serviceContext);
        this.mCarBugreportManagerService = new CarBugreportManagerService(serviceContext);
        this.mAirplaneModeService = new AirplaneModeService(serviceContext);
        this.mXpVehicleService = new XpVehicleService(this.mContext, this.mCarPropertyService, this.mHal);
        this.mXpSharedMemoryService = new XpSharedMemoryService(this.mContext, this.mHal);
        this.mXpDiagnosticService = new XpDiagnosticService(this.mCarPropertyService, this.mContext, this.mSystemActivityMonitoringService, this.mHal);
        this.mXpUpdateTimeService = new XpUpdateTimeService(this.mCarPropertyService, this.mContext);
        this.mCarConditionService = new CarConditionService(this.mContext, this.mCarPropertyService, this.mHal.getPropertyHal());
        this.mCarPermissionManagerService = new CarPermissionManagerService(this.mContext, this.mCarPropertyService);
        this.mCarIntelligentEngineService = new CarIntelligentEngineService(this.mContext, this.mCarPropertyService);
        CarLocalServices.addService(CarPowerManagementService.class, this.mCarPowerManagementService);
        CarLocalServices.addService(CarUserService.class, this.mCarUserService);
        CarLocalServices.addService(CarTrustedDeviceService.class, this.mCarTrustedDeviceService);
        CarLocalServices.addService(CarUserNoticeService.class, this.mCarUserNoticeService);
        CarLocalServices.addService(SystemInterface.class, this.mSystemInterface);
        CarLocalServices.addService(CarDrivingStateService.class, this.mCarDrivingStateService);
        CarLocalServices.addService(PerUserCarServiceHelper.class, this.mPerUserCarServiceHelper);
        CarLocalServices.addService(FixedActivityService.class, this.mFixedActivityService);
        CarLocalServices.addService(CarPermissionManagerService.class, this.mCarPermissionManagerService);
        CarLocalServices.addService(CarIntelligentEngineService.class, this.mCarIntelligentEngineService);
        List<CarServiceBase> allServices = new ArrayList<>();
        allServices.add(this.mCarUserService);
        allServices.add(this.mSystemActivityMonitoringService);
        allServices.add(this.mCarPowerManagementService);
        allServices.add(this.mCarPropertyService);
        allServices.add(this.mCarDrivingStateService);
        allServices.add(this.mCarUXRestrictionsService);
        allServices.add(this.mCarPackageManagerService);
        allServices.add(this.mCarInputService);
        allServices.add(this.mGarageModeService);
        allServices.add(this.mCarUserNoticeService);
        allServices.add(this.mAppFocusService);
        allServices.add(this.mCarAudioService);
        allServices.add(this.mCarNightService);
        allServices.add(this.mFixedActivityService);
        allServices.add(this.mInstrumentClusterService);
        allServices.add(this.mSystemStateControllerService);
        allServices.add(this.mPerUserCarServiceHelper);
        allServices.add(this.mCarProjectionService);
        allServices.add(this.mCarDiagnosticService);
        allServices.add(this.mCarStorageMonitoringService);
        allServices.add(this.mCarConfigurationService);
        allServices.add(this.mVmsClientManager);
        allServices.add(this.mVmsSubscriberService);
        allServices.add(this.mVmsPublisherService);
        allServices.add(this.mCarTrustedDeviceService);
        allServices.add(this.mCarMediaService);
        allServices.add(this.mCarLocationService);
        allServices.add(this.mCarBugreportManagerService);
        allServices.add(this.mAirplaneModeService);
        allServices.add(this.mXpVehicleService);
        allServices.add(this.mXpDiagnosticService);
        allServices.add(this.mXpSharedMemoryService);
        allServices.add(this.mXpUpdateTimeService);
        allServices.add(this.mCarConditionService);
        allServices.add(this.mCarPermissionManagerService);
        allServices.add(this.mCarIntelligentEngineService);
        this.mAllServices = (CarServiceBase[]) allServices.toArray(new CarServiceBase[0]);
        this.mDumpServices = new CarServiceBase[]{this.mXpVehicleService, this.mXpUpdateTimeService, this.mCarPropertyService, this.mXpSharedMemoryService, this.mXpDiagnosticService, this.mCarAudioService, this.mCarPowerManagementService, this.mAirplaneModeService, this.mCarStorageMonitoringService, this.mCarConditionService, this.mCarPermissionManagerService};
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void init() {
        CarServiceBase[] carServiceBaseArr;
        this.mBootTiming = new TimingsTraceLog(VHAL_TIMING_TAG, (long) PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH);
        traceBegin("VehicleHal.init");
        this.mHal.init();
        traceEnd();
        traceBegin("CarService.initAllServices");
        for (CarServiceBase service : this.mAllServices) {
            try {
                service.init();
            } catch (Exception e) {
                Slog.e(TAG, "Got unexpected exception", e);
            }
        }
        traceEnd();
        this.mSystemInterface.reconfigureSecondaryDisplays();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void release() {
        for (int i = this.mAllServices.length - 1; i >= 0; i--) {
            this.mAllServices[i].release();
        }
        this.mHal.release();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void vehicleHalReconnected(IVehicle vehicle) {
        CarServiceBase[] carServiceBaseArr;
        this.mHal.vehicleHalReconnected(vehicle);
        for (CarServiceBase service : this.mAllServices) {
            service.vehicleHalReconnected();
        }
    }

    public void setCarServiceHelper(IBinder helper) {
        assertCallingFromSystemProcess();
        synchronized (this) {
            this.mICarServiceHelper = ICarServiceHelper.Stub.asInterface(helper);
            this.mSystemInterface.setCarServiceHelper(this.mICarServiceHelper);
        }
    }

    public void setUserLockStatus(int userHandle, int unlocked) {
        assertCallingFromSystemProcess();
        this.mCarUserService.setUserLockStatus(userHandle, unlocked == 1);
        this.mCarMediaService.setUserLockStatus(userHandle, unlocked == 1);
    }

    public void onSwitchUser(int userHandle) {
        assertCallingFromSystemProcess();
        Slog.i(TAG, "Foreground user switched to " + userHandle);
        this.mCarUserService.onSwitchUser(userHandle);
    }

    static void assertCallingFromSystemProcess() {
        int uid = Binder.getCallingUid();
        if (uid != 1000) {
            throw new SecurityException("Only allowed from system");
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static void assertCallingFromSystemProcessOrSelf() {
        int uid = Binder.getCallingUid();
        int pid = Binder.getCallingPid();
        if (uid != 1000 && pid != Process.myPid()) {
            throw new SecurityException("Only allowed from system or self");
        }
    }

    public static boolean checkCarPermission(Context context, int propId, int access) {
        return CarPermissionManager.get().checkCarPermission(context, propId, access);
    }

    /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
    public IBinder getCarService(String serviceName) {
        char c;
        CarTestService carTestService;
        switch (serviceName.hashCode()) {
            case -1969960369:
                if (serviceName.equals("projection")) {
                    c = ',';
                    break;
                }
                c = 65535;
                break;
            case -1871502322:
                if (serviceName.equals("xp_avas")) {
                    c = 11;
                    break;
                }
                c = 65535;
                break;
            case -1871431131:
                if (serviceName.equals("xp_dcdc")) {
                    c = 17;
                    break;
                }
                c = 65535;
                break;
            case -1870955074:
                if (serviceName.equals("xp_tbox")) {
                    c = '#';
                    break;
                }
                c = 65535;
                break;
            case -1870948428:
                if (serviceName.equals("xp_time")) {
                    c = ';';
                    break;
                }
                c = 65535;
                break;
            case -1870941687:
                if (serviceName.equals("xp_tpms")) {
                    c = Typography.quote;
                    break;
                }
                c = 65535;
                break;
            case -1855028221:
                if (serviceName.equals("car_bluetooth")) {
                    c = '/';
                    break;
                }
                c = 65535;
                break;
            case -1853877803:
                if (serviceName.equals("car_navigation_service")) {
                    c = '*';
                    break;
                }
                c = 65535;
                break;
            case -1547904089:
                if (serviceName.equals(AOSP_DIAGNOSTIC_SERVICE)) {
                    c = 3;
                    break;
                }
                c = 65535;
                break;
            case -993141291:
                if (serviceName.equals("property")) {
                    c = '\b';
                    break;
                }
                c = 65535;
                break;
            case -905948230:
                if (serviceName.equals("sensor")) {
                    c = '\t';
                    break;
                }
                c = 65535;
                break;
            case -902328044:
                if (serviceName.equals("xp_shared_memory")) {
                    c = '9';
                    break;
                }
                c = 65535;
                break;
            case -874200568:
                if (serviceName.equals("vendor_extension")) {
                    c = '\n';
                    break;
                }
                c = 65535;
                break;
            case -807062458:
                if (serviceName.equals("package")) {
                    c = 2;
                    break;
                }
                c = 65535;
                break;
            case -753107971:
                if (serviceName.equals("xp_amp")) {
                    c = 24;
                    break;
                }
                c = 65535;
                break;
            case -753107758:
                if (serviceName.equals("xp_atl")) {
                    c = 23;
                    break;
                }
                c = 65535;
                break;
            case -753107695:
                if (serviceName.equals("xp_avm")) {
                    c = '\f';
                    break;
                }
                c = 65535;
                break;
            case -753107323:
                if (serviceName.equals("xp_bcm")) {
                    c = '\r';
                    break;
                }
                c = 65535;
                break;
            case -753107007:
                if (serviceName.equals("xp_bms")) {
                    c = 14;
                    break;
                }
                c = 65535;
                break;
            case -753106423:
                if (serviceName.equals("xp_can")) {
                    c = 15;
                    break;
                }
                c = 65535;
                break;
            case -753106356:
                if (serviceName.equals("xp_ccs")) {
                    c = 16;
                    break;
                }
                c = 65535;
                break;
            case -753106341:
                if (serviceName.equals("xp_cdc")) {
                    c = 31;
                    break;
                }
                c = 65535;
                break;
            case -753106168:
                if (serviceName.equals("xp_ciu")) {
                    c = Typography.amp;
                    break;
                }
                c = 65535;
                break;
            case -753105256:
                if (serviceName.equals("xp_dhc")) {
                    c = ' ';
                    break;
                }
                c = 65535;
                break;
            case -753104031:
                if (serviceName.equals("xp_eps")) {
                    c = 18;
                    break;
                }
                c = 65535;
                break;
            case -753103941:
                if (serviceName.equals("xp_esp")) {
                    c = 19;
                    break;
                }
                c = 65535;
                break;
            case -753100596:
                if (serviceName.equals("xp_icm")) {
                    c = 20;
                    break;
                }
                c = 65535;
                break;
            case -753100278:
                if (serviceName.equals("xp_imu")) {
                    c = '\'';
                    break;
                }
                c = 65535;
                break;
            case -753100185:
                if (serviceName.equals("xp_ipu")) {
                    c = 22;
                    break;
                }
                c = 65535;
                break;
            case -753097426:
                if (serviceName.equals("xp_llu")) {
                    c = 25;
                    break;
                }
                c = 65535;
                break;
            case -753096744:
                if (serviceName.equals("xp_mcu")) {
                    c = 26;
                    break;
                }
                c = 65535;
                break;
            case -753096267:
                if (serviceName.equals("xp_msb")) {
                    c = 30;
                    break;
                }
                c = 65535;
                break;
            case -753096256:
                if (serviceName.equals("xp_msm")) {
                    c = 29;
                    break;
                }
                c = 65535;
                break;
            case -753090978:
                if (serviceName.equals("xp_scu")) {
                    c = '!';
                    break;
                }
                c = 65535;
                break;
            case -753090593:
                if (serviceName.equals("xp_spc")) {
                    c = ')';
                    break;
                }
                c = 65535;
                break;
            case -753090515:
                if (serviceName.equals("xp_srs")) {
                    c = 27;
                    break;
                }
                c = 65535;
                break;
            case -753088095:
                if (serviceName.equals("xp_vcu")) {
                    c = Typography.dollar;
                    break;
                }
                c = 65535;
                break;
            case -753087700:
                if (serviceName.equals("xp_vpm")) {
                    c = 28;
                    break;
                }
                c = 65535;
                break;
            case -753085770:
                if (serviceName.equals("xp_xpu")) {
                    c = '(';
                    break;
                }
                c = 65535;
                break;
            case -603093501:
                if (serviceName.equals("car-service-test")) {
                    c = '.';
                    break;
                }
                c = 65535;
                break;
            case -444756694:
                if (serviceName.equals("drivingstate")) {
                    c = '1';
                    break;
                }
                c = 65535;
                break;
            case -375708743:
                if (serviceName.equals("car_media")) {
                    c = '5';
                    break;
                }
                c = 65535;
                break;
            case -259003252:
                if (serviceName.equals("storage_monitoring")) {
                    c = '0';
                    break;
                }
                c = 65535;
                break;
            case 3214768:
                if (serviceName.equals("hvac")) {
                    c = 6;
                    break;
                }
                c = 65535;
                break;
            case 3237038:
                if (serviceName.equals("info")) {
                    c = 7;
                    break;
                }
                c = 65535;
                break;
            case 22072549:
                if (serviceName.equals("xp_vehicle")) {
                    c = '7';
                    break;
                }
                c = 65535;
                break;
            case 93166550:
                if (serviceName.equals("audio")) {
                    c = 0;
                    break;
                }
                c = 65535;
                break;
            case 94415849:
                if (serviceName.equals("cabin")) {
                    c = 5;
                    break;
                }
                c = 65535;
                break;
            case 106858757:
                if (serviceName.equals("power")) {
                    c = 4;
                    break;
                }
                c = 65535;
                break;
            case 264148814:
                if (serviceName.equals("xp_diagnostic")) {
                    c = '8';
                    break;
                }
                c = 65535;
                break;
            case 486923284:
                if (serviceName.equals("vehicle_map_subscriber_service")) {
                    c = '-';
                    break;
                }
                c = 65535;
                break;
            case 859709588:
                if (serviceName.equals("xp_condition")) {
                    c = AccessibilityUtils.ENABLED_ACCESSIBILITY_SERVICES_SEPARATOR;
                    break;
                }
                c = 65535;
                break;
            case 1075548489:
                if (serviceName.equals("uxrestriction")) {
                    c = '2';
                    break;
                }
                c = 65535;
                break;
            case 1134120567:
                if (serviceName.equals("intelligent")) {
                    c = Typography.less;
                    break;
                }
                c = 65535;
                break;
            case 1644291440:
                if (serviceName.equals("cluster_service")) {
                    c = '+';
                    break;
                }
                c = 65535;
                break;
            case 1763569149:
                if (serviceName.equals("car_bugreport")) {
                    c = '6';
                    break;
                }
                c = 65535;
                break;
            case 1830376762:
                if (serviceName.equals("app_focus")) {
                    c = 1;
                    break;
                }
                c = 65535;
                break;
            case 1891269741:
                if (serviceName.equals("trust_enroll")) {
                    c = '4';
                    break;
                }
                c = 65535;
                break;
            case 1932752118:
                if (serviceName.equals("configuration")) {
                    c = '3';
                    break;
                }
                c = 65535;
                break;
            case 2120134595:
                if (serviceName.equals("xp_input")) {
                    c = 21;
                    break;
                }
                c = 65535;
                break;
            case 2128047092:
                if (serviceName.equals("xp_radio")) {
                    c = '%';
                    break;
                }
                c = 65535;
                break;
            default:
                c = 65535;
                break;
        }
        switch (c) {
            case 0:
                return this.mCarAudioService;
            case 1:
                return this.mAppFocusService;
            case 2:
                return this.mCarPackageManagerService;
            case 3:
                assertAnyDiagnosticPermission(this.mContext);
                return this.mCarDiagnosticService;
            case 4:
                assertPowerPermission(this.mContext);
                return this.mCarPowerManagementService;
            case 5:
            case 6:
            case 7:
            case '\b':
            case '\t':
            case '\n':
            case 11:
            case '\f':
            case '\r':
            case 14:
            case 15:
            case 16:
            case 17:
            case 18:
            case 19:
            case 20:
            case 21:
            case 22:
            case 23:
            case 24:
            case 25:
            case 26:
            case 27:
            case 28:
            case 29:
            case 30:
            case 31:
            case ' ':
            case '!':
            case '\"':
            case '#':
            case '$':
            case '%':
            case '&':
            case '\'':
            case '(':
            case ')':
                return this.mCarPropertyService;
            case '*':
                assertNavigationManagerPermission(this.mContext);
                IInstrumentClusterNavigation navService = this.mInstrumentClusterService.getNavigationService();
                if (navService == null) {
                    return null;
                }
                return navService.asBinder();
            case '+':
                assertClusterManagerPermission(this.mContext);
                return this.mInstrumentClusterService.getManagerService();
            case ',':
                return this.mCarProjectionService;
            case '-':
                assertVmsSubscriberPermission(this.mContext);
                return this.mVmsSubscriberService;
            case '.':
                assertPermission(this.mContext, Manifest.permission.CAR_TEST_SERVICE);
                synchronized (this) {
                    if (this.mCarTestService == null) {
                        this.mCarTestService = new CarTestService(this.mContext, this);
                    }
                    carTestService = this.mCarTestService;
                }
                return carTestService;
            case '/':
                return this.mCarBluetoothService;
            case '0':
                assertPermission(this.mContext, Manifest.permission.STORAGE_MONITORING);
                return this.mCarStorageMonitoringService;
            case '1':
                assertDrivingStatePermission(this.mContext);
                return this.mCarDrivingStateService;
            case '2':
                return this.mCarUXRestrictionsService;
            case '3':
                return this.mCarConfigurationService;
            case '4':
                assertTrustAgentEnrollmentPermission(this.mContext);
                return this.mCarTrustedDeviceService.getCarTrustAgentEnrollmentService();
            case '5':
                return this.mCarMediaService;
            case '6':
                return this.mCarBugreportManagerService;
            case '7':
                return this.mXpVehicleService;
            case '8':
                return this.mXpDiagnosticService;
            case '9':
                return this.mXpSharedMemoryService;
            case ':':
                return this.mCarConditionService;
            case ';':
                return this.mXpUpdateTimeService;
            case '<':
                return this.mCarIntelligentEngineService;
            default:
                Slog.w(CarLog.TAG_SERVICE, "getCarService for unknown service:" + serviceName);
                return null;
        }
    }

    public int getCarConnectionType() {
        return 5;
    }

    public void onAutoWakeupResult(boolean success) {
        assertCallingFromSystemProcess();
        Slog.i(TAG, "onAutoWakeupResult " + success);
        this.mCarPowerManagementService.onAutoWakeupResult(success);
    }

    public CarServiceBase getCarInternalService(String serviceName) {
        char c;
        int hashCode = serviceName.hashCode();
        if (hashCode == -37346550) {
            if (serviceName.equals(INTERNAL_VMS_MANAGER)) {
                c = 2;
            }
            c = 65535;
        } else if (hashCode != 846184) {
            if (hashCode == 782548936 && serviceName.equals(INTERNAL_INPUT_SERVICE)) {
                c = 0;
            }
            c = 65535;
        } else {
            if (serviceName.equals(INTERNAL_SYSTEM_ACTIVITY_MONITORING_SERVICE)) {
                c = 1;
            }
            c = 65535;
        }
        if (c != 0) {
            if (c != 1) {
                if (c == 2) {
                    return this.mVmsClientManager;
                }
                Slog.w(CarLog.TAG_SERVICE, "getCarInternalService for unknown service:" + serviceName);
                return null;
            }
            return this.mSystemActivityMonitoringService;
        }
        return this.mCarInputService;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public CarStatsService getStatsService() {
        return this.mCarStatsService;
    }

    public static void assertVehicleHalMockPermission(Context context) {
        assertPermission(context, Manifest.permission.CAR_MOCK_VEHICLE_HAL);
    }

    public static void assertNavigationManagerPermission(Context context) {
        assertPermission(context, Manifest.permission.CAR_NAVIGATION_MANAGER);
    }

    public static void assertClusterManagerPermission(Context context) {
        assertPermission(context, Manifest.permission.CAR_INSTRUMENT_CLUSTER_CONTROL);
    }

    public static void assertPowerPermission(Context context) {
        assertPermission(context, Manifest.permission.CAR_POWER);
    }

    public static void assertProjectionPermission(Context context) {
        assertPermission(context, Manifest.permission.CAR_PROJECTION);
    }

    public static void assertProjectionStatusPermission(Context context) {
        assertPermission(context, Manifest.permission.ACCESS_CAR_PROJECTION_STATUS);
    }

    public static void assertAnyDiagnosticPermission(Context context) {
        assertAnyPermission(context, Manifest.permission.CAR_DIAGNOSTICS, Manifest.permission.CLEAR_CAR_DIAGNOSTICS);
    }

    public static void assertDrivingStatePermission(Context context) {
        assertPermission(context, Manifest.permission.CAR_DRIVING_STATE);
    }

    public static void assertVmsPublisherPermission(Context context) {
        assertPermission(context, Manifest.permission.VMS_PUBLISHER);
    }

    public static void assertVmsSubscriberPermission(Context context) {
        assertPermission(context, Manifest.permission.VMS_SUBSCRIBER);
    }

    public static void assertTrustAgentEnrollmentPermission(Context context) {
        assertPermission(context, Manifest.permission.CAR_ENROLL_TRUST);
    }

    public static void assertPermission(Context context, String permission) {
        if (permission != null && !Build.IS_DEBUGGABLE && context.checkCallingOrSelfPermission(permission) != 0) {
            throw new SecurityException("requires " + permission);
        }
    }

    public static boolean hasPermission(Context context, String permission) {
        boolean z = true;
        if (permission == null) {
            return true;
        }
        synchronized (mPermmissionCached) {
            List<String> history = mPermmissionCached.get(Integer.valueOf(Binder.getCallingUid()));
            if (history != null && history.contains(permission)) {
                return true;
            }
            if (!Build.IS_DEBUGGABLE && context.checkCallingOrSelfPermission(permission) != 0) {
                z = false;
            }
            boolean isGranted = z;
            if (isGranted) {
                if (history == null) {
                    history = new CopyOnWriteArrayList();
                    mPermmissionCached.put(Integer.valueOf(Binder.getCallingUid()), history);
                }
                history.add(permission);
            }
            return isGranted;
        }
    }

    public static void assertAnyPermission(Context context, String... permissions) {
        for (String permission : permissions) {
            if (context.checkCallingOrSelfPermission(permission) == 0) {
                return;
            }
        }
        throw new SecurityException("requires any of " + Arrays.toString(permissions));
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        if (this.mContext.checkCallingOrSelfPermission("android.permission.DUMP") != 0) {
            writer.println("Permission Denial: can't dump CarService from from pid=" + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid() + " without permission android.permission.DUMP");
        } else if (args == null || args.length == 0 || (args.length > 0 && "-a".equals(args[0]))) {
            writer.println("*Dump car service*");
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                LocalDateTime now = LocalDateTime.now();
                writer.println("    current date time: " + now.format(formatter));
                writer.println("    run time: " + TimeUtils.formatDuration(SystemClock.elapsedRealtime() - this.mStartTime));
                writer.println("*FutureConfig, DEFAULT:true");
                writer.println("*Dump all services*");
                dumpAllowedServices(writer);
                writer.println("*Dump Vehicle HAL*");
                writer.println("Vehicle HAL Interface: " + this.mVehicleInterfaceName);
                this.mHal.dump(writer);
            } catch (Exception e) {
                writer.println("Failed dumping: " + this.mHal.getClass().getName());
                e.printStackTrace(writer);
            }
        } else if ("--metrics".equals(args[0])) {
            this.mCarStatsService.dump(fd, writer, (String[]) Arrays.copyOfRange(args, 1, args.length));
        } else if ("--vms-hal".equals(args[0])) {
            this.mHal.getVmsHal().dumpMetrics(fd);
        } else if (Build.IS_USERDEBUG || Build.IS_ENG) {
            execShellCmd(args, writer);
        } else {
            writer.println("Commands not supported in " + Build.TYPE);
        }
    }

    private void dumpAllServices(PrintWriter writer) {
        CarServiceBase[] carServiceBaseArr;
        for (CarServiceBase service : this.mAllServices) {
            dumpService(service, writer);
        }
        CarTestService carTestService = this.mCarTestService;
        if (carTestService != null) {
            dumpService(carTestService, writer);
        }
    }

    private void dumpAllowedServices(PrintWriter writer) {
        CarServiceBase[] carServiceBaseArr;
        for (CarServiceBase service : this.mDumpServices) {
            dumpService(service, writer);
            writer.println();
        }
        CarTestService carTestService = this.mCarTestService;
        if (carTestService != null) {
            dumpService(carTestService, writer);
            writer.println();
        }
    }

    private void dumpService(CarServiceBase service, PrintWriter writer) {
        try {
            service.dump(writer);
        } catch (Exception e) {
            writer.println("Failed dumping: " + service.getClass().getName());
            e.printStackTrace(writer);
        }
    }

    void execShellCmd(String[] args, PrintWriter writer) {
        new CarShellCommand().exec(args, writer);
    }

    private void traceBegin(String name) {
        Slog.i(TAG, name);
        this.mBootTiming.traceBegin(name);
    }

    private void traceEnd() {
        this.mBootTiming.traceEnd();
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class CarShellCommand {
        private static final String COMMAND_DAY_NIGHT_MODE = "day-night-mode";
        private static final String COMMAND_DEBUG_CAR_FEATURE_INJECT = "car-feature";
        private static final String COMMAND_DEBUG_CAR_SCENE_CALIBRATION_INJECT = "car-scene-calibration-inject";
        private static final String COMMAND_DEBUG_CAR_SCENE_INJECT = "car-scene-inject";
        private static final String COMMAND_DEBUG_UPDATE_PERMISSION_COST = "car-permission-cost";
        private static final String COMMAND_DEBUG_UPDATE_PERMISSION_DENIED = "car-permission-denied";
        private static final String COMMAND_DEBUG_UPDATE_PERMISSION_REFRESH = "car-permission-update";
        private static final String COMMAND_DISPLAY_OFF = "display-off";
        private static final String COMMAND_DISPLAY_ON = "display-on";
        private static final String COMMAND_ENABLE_TRUSTED_DEVICE = "enable-trusted-device";
        private static final String COMMAND_ENABLE_UXR = "enable-uxr";
        private static final String COMMAND_GARAGE_MODE = "garage-mode";
        private static final String COMMAND_GET_CARPROPERTYCONFIG = "get-carpropertyconfig";
        private static final String COMMAND_GET_DO_ACTIVITIES = "get-do-activities";
        private static final String COMMAND_GET_PROPERTY_VALUE = "get-property-value";
        private static final String COMMAND_HELP = "-h";
        private static final String COMMAND_INJECT_DEBUG = "inject-debug";
        private static final String COMMAND_INJECT_ERROR_EVENT = "inject-error-event";
        private static final String COMMAND_INJECT_KEY = "inject-key";
        private static final String COMMAND_INJECT_VHAL_EVENT = "inject-vhal-event";
        private static final String COMMAND_PROJECTION_AP_TETHERING = "projection-tethering";
        private static final String COMMAND_PROJECTION_UI_MODE = "projection-ui-mode";
        private static final String COMMAND_REMOVE_TRUSTED_DEVICES = "remove-trusted-devices";
        private static final String COMMAND_RESUME = "resume";
        private static final String COMMAND_SHOW_VERBOSE_LOGS = "show-verb-logs";
        private static final String COMMAND_START_FIXED_ACTIVITY_MODE = "start-fixed-activity-mode";
        private static final String COMMAND_STOP_FIXED_ACTIVITY_MODE = "stop-fixed-activity-mode";
        private static final String COMMAND_SUSPEND = "suspend";
        private static final int DISPLAY_ICM = 1;
        private static final int DISPLAY_IVI = 0;
        private static final int DISPLAY_PSN_OTA = 2;
        private static final String PARAM_DAY_MODE = "day";
        private static final String PARAM_DEBUG_DISPLAY = "display";
        private static final String PARAM_NIGHT_MODE = "night";
        private static final String PARAM_OFF_MODE = "off";
        private static final String PARAM_ON_MODE = "on";
        private static final String PARAM_QUERY_MODE = "query";
        private static final String PARAM_REBOOT = "reboot";
        private static final String PARAM_SENSOR_MODE = "sensor";
        private static final String PARAM_VEHICLE_PROPERTY_AREA_GLOBAL = "0";

        private CarShellCommand() {
        }

        private void dumpHelp(PrintWriter pw) {
            pw.println("Car service commands:");
            pw.println("\t-h");
            pw.println("\t  Print this help text.");
            pw.println("\tday-night-mode [day|night|sensor]");
            pw.println("\t  Force into day/night mode or restore to auto.");
            pw.println("\tinject-vhal-event property [zone] data(can be comma separated list)");
            pw.println("\t  Inject a vehicle property for testing.");
            pw.println("\tinject-error-event property zone errorCode");
            pw.println("\t  Inject an error event from VHAL for testing.");
            pw.println("\tenable-uxr true|false");
            pw.println("\t  Enable/Disable UX restrictions and App blocking.");
            pw.println("\tgarage-mode [on|off|query|reboot]");
            pw.println("\t  Force into or out of garage mode, or check status.");
            pw.println("\t  With 'reboot', enter garage mode, then reboot when it completes.");
            pw.println("\tget-do-activities pkgname");
            pw.println("\t  Get Distraction Optimized activities in given package.");
            pw.println("\tget-carpropertyconfig [propertyId]");
            pw.println("\t  Get a CarPropertyConfig by Id in Hex or list all CarPropertyConfigs");
            pw.println("\tget-property-value [propertyId] [areaId]");
            pw.println("\t  Get a vehicle property value by property id in Hex and areaId");
            pw.println("\t  or list all property values for all areaId");
            pw.println("\tsuspend");
            pw.println("\t  Suspend the system to Deep Sleep.");
            pw.println("\tresume");
            pw.println("\t  Wake the system up after a 'suspend.'");
            pw.println("\tenable-trusted-device true|false");
            pw.println("\t  Enable/Disable Trusted device feature.");
            pw.println("\tremove-trusted-devices");
            pw.println("\t  Remove all trusted devices for the current foreground user.");
            pw.println("\tprojection-tethering [true|false]");
            pw.println("\t  Whether tethering should be used when creating access point for wireless projection");
            pw.println("\t--metrics");
            pw.println("\t  When used with dumpsys, only metrics will be in the dumpsys output.");
            pw.println("\tstart-fixed-activity displayId packageName activityName");
            pw.println("\t  Start an Activity the specified display as fixed mode");
            pw.println("\tstop-fixed-mode displayId");
            pw.println("\t  Stop fixed Activity mode for the given display. The Activity will not be restarted upon crash.");
            pw.println("\tinject-key [-d display] [-t down_delay_ms] key_code");
            pw.println("\t  inject key down / up event to car service");
            pw.println("\t  display: 0 for main, 1 for cluster. If not specified, it will be 0.");
            pw.println("\t  down_delay_ms: delay from down to up key event. If not specified,");
            pw.println("\t                 it will be 0");
            pw.println("\t  key_code: int key code defined in android KeyEvent");
            pw.println("\t  Inject a vehicle property for testing");
            pw.println("\tshow-verb-logs 0|1|2|3|4|5|6|7|8|9|10|11|12|13|14|15");
            pw.println("\t  enable set log      = 0x01");
            pw.println("\t  enable get log      = 0x02");
            pw.println("\t  enable callback log = 0x04");
            pw.println("\t  enable perf log     = 0x08");
            pw.println("\t  Show verbose logs");
            pw.println("\tdisplay-on|display-off 0|1|2");
            pw.println("\t  display-on 0|1|2 ---> ivi display on|icm display on|ota psn display on");
            pw.println("\t  display-off 0|1|2 ----> ivi display off | icm display off|ota psn display off");
            pw.println("\tinject-debug display 1  ---> debug screen on");
            pw.println("\tinject-debug display 0  ---> debug screen off");
            pw.println("\tcar-permission-update  ---> update car permission signature data");
            pw.println("\tcar-permission-denied  ---> collect denied propName list");
            pw.println("\tcar-permission-cost  ---> collect cost time propName list");
            pw.println("\tcar-scene-inject {type} {pos} {action} ---> inject car scene");
            pw.println("\tcar-feature {featureName} {value} ---> car-feature debug");
        }

        public void exec(String[] args, PrintWriter writer) {
            String data;
            try {
                String arg = args[0];
                char c = 65535;
                switch (arg.hashCode()) {
                    case -2051851360:
                        if (arg.equals(COMMAND_DEBUG_UPDATE_PERMISSION_DENIED)) {
                            c = 23;
                            break;
                        }
                        break;
                    case -1947496588:
                        if (arg.equals(COMMAND_GET_CARPROPERTYCONFIG)) {
                            c = 7;
                            break;
                        }
                        break;
                    case -1852006340:
                        if (arg.equals(COMMAND_SUSPEND)) {
                            c = '\f';
                            break;
                        }
                        break;
                    case -1803231086:
                        if (arg.equals(COMMAND_PROJECTION_AP_TETHERING)) {
                            c = '\n';
                            break;
                        }
                        break;
                    case -1661913154:
                        if (arg.equals(COMMAND_PROJECTION_UI_MODE)) {
                            c = '\t';
                            break;
                        }
                        break;
                    case -1594986098:
                        if (arg.equals(COMMAND_DEBUG_CAR_SCENE_CALIBRATION_INJECT)) {
                            c = 26;
                            break;
                        }
                        break;
                    case -1555302194:
                        if (arg.equals(COMMAND_DEBUG_UPDATE_PERMISSION_REFRESH)) {
                            c = 22;
                            break;
                        }
                        break;
                    case -934426579:
                        if (arg.equals(COMMAND_RESUME)) {
                            c = 11;
                            break;
                        }
                        break;
                    case -914567478:
                        if (arg.equals(COMMAND_DISPLAY_ON)) {
                            c = 20;
                            break;
                        }
                        break;
                    case -766779255:
                        if (arg.equals(COMMAND_GARAGE_MODE)) {
                            c = 2;
                            break;
                        }
                        break;
                    case -413651266:
                        if (arg.equals(COMMAND_REMOVE_TRUSTED_DEVICES)) {
                            c = 14;
                            break;
                        }
                        break;
                    case -357334197:
                        if (arg.equals(COMMAND_DEBUG_CAR_SCENE_INJECT)) {
                            c = 25;
                            break;
                        }
                        break;
                    case -258824931:
                        if (arg.equals(COMMAND_START_FIXED_ACTIVITY_MODE)) {
                            c = 15;
                            break;
                        }
                        break;
                    case 1499:
                        if (arg.equals(COMMAND_HELP)) {
                            c = 0;
                            break;
                        }
                        break;
                    case 97605688:
                        if (arg.equals(COMMAND_GET_DO_ACTIVITIES)) {
                            c = 6;
                            break;
                        }
                        break;
                    case 247238886:
                        if (arg.equals(COMMAND_INJECT_VHAL_EVENT)) {
                            c = 3;
                            break;
                        }
                        break;
                    case 854968829:
                        if (arg.equals(COMMAND_DEBUG_CAR_FEATURE_INJECT)) {
                            c = 27;
                            break;
                        }
                        break;
                    case 855944466:
                        if (arg.equals(COMMAND_DEBUG_UPDATE_PERMISSION_COST)) {
                            c = 24;
                            break;
                        }
                        break;
                    case 905425270:
                        if (arg.equals(COMMAND_ENABLE_TRUSTED_DEVICE)) {
                            c = '\r';
                            break;
                        }
                        break;
                    case 983226064:
                        if (arg.equals(COMMAND_GET_PROPERTY_VALUE)) {
                            c = '\b';
                            break;
                        }
                        break;
                    case 1047274263:
                        if (arg.equals(COMMAND_INJECT_DEBUG)) {
                            c = 19;
                            break;
                        }
                        break;
                    case 1121162505:
                        if (arg.equals(COMMAND_DAY_NIGHT_MODE)) {
                            c = 1;
                            break;
                        }
                        break;
                    case 1318574269:
                        if (arg.equals(COMMAND_STOP_FIXED_ACTIVITY_MODE)) {
                            c = 16;
                            break;
                        }
                        break;
                    case 1648991321:
                        if (arg.equals(COMMAND_INJECT_ERROR_EVENT)) {
                            c = 4;
                            break;
                        }
                        break;
                    case 1693121229:
                        if (arg.equals(COMMAND_SHOW_VERBOSE_LOGS)) {
                            c = 18;
                            break;
                        }
                        break;
                    case 1713179108:
                        if (arg.equals(COMMAND_DISPLAY_OFF)) {
                            c = 21;
                            break;
                        }
                        break;
                    case 1892083429:
                        if (arg.equals(COMMAND_ENABLE_UXR)) {
                            c = 5;
                            break;
                        }
                        break;
                    case 2030144547:
                        if (arg.equals(COMMAND_INJECT_KEY)) {
                            c = 17;
                            break;
                        }
                        break;
                }
                String str = "";
                switch (c) {
                    case 0:
                        dumpHelp(writer);
                        return;
                    case 1:
                        if (args.length >= 2) {
                            str = args[1];
                        }
                        String value = str;
                        forceDayNightMode(value, writer);
                        return;
                    case 2:
                        if (args.length >= 2) {
                            str = args[1];
                        }
                        String value2 = str;
                        forceGarageMode(value2, writer);
                        return;
                    case 3:
                        String zone = PARAM_VEHICLE_PROPERTY_AREA_GLOBAL;
                        if (args.length != 3 && args.length != 4) {
                            writer.println("Incorrect number of arguments.");
                            dumpHelp(writer);
                            return;
                        }
                        if (args.length == 4) {
                            zone = args[2];
                            data = args[3];
                        } else {
                            String data2 = args[2];
                            data = data2;
                        }
                        injectVhalEvent(args[1], zone, data, false, writer);
                        return;
                    case 4:
                        if (args.length != 4) {
                            writer.println("Incorrect number of arguments");
                            dumpHelp(writer);
                            return;
                        }
                        String errorAreaId = args[2];
                        String errorCode = args[3];
                        injectVhalEvent(args[1], errorAreaId, errorCode, true, writer);
                        return;
                    case 5:
                        if (args.length != 2) {
                            writer.println("Incorrect number of arguments");
                            dumpHelp(writer);
                            return;
                        }
                        boolean enableBlocking = Boolean.valueOf(args[1]).booleanValue();
                        if (ICarImpl.this.mCarPackageManagerService != null) {
                            ICarImpl.this.mCarPackageManagerService.setEnableActivityBlocking(enableBlocking);
                            return;
                        }
                        return;
                    case 6:
                        if (args.length != 2) {
                            writer.println("Incorrect number of arguments");
                            dumpHelp(writer);
                            return;
                        }
                        String pkgName = args[1].toLowerCase();
                        if (ICarImpl.this.mCarPackageManagerService != null) {
                            String[] doActivities = ICarImpl.this.mCarPackageManagerService.getDistractionOptimizedActivities(pkgName);
                            if (doActivities != null) {
                                writer.println("DO Activities for " + pkgName);
                                for (String a : doActivities) {
                                    writer.println(a);
                                }
                            } else {
                                writer.println("No DO Activities for " + pkgName);
                            }
                            return;
                        }
                        return;
                    case 7:
                        if (args.length >= 2) {
                            str = args[1];
                        }
                        String propertyId = str;
                        ICarImpl.this.mHal.dumpPropertyConfigs(writer, propertyId);
                        return;
                    case '\b':
                        String propId = args.length < 2 ? "" : args[1];
                        if (args.length >= 3) {
                            str = args[2];
                        }
                        String areaId = str;
                        ICarImpl.this.mHal.dumpPropertyValueByCommend(writer, propId, areaId);
                        return;
                    case '\t':
                        if (args.length == 2) {
                            ICarImpl.this.mCarProjectionService.setUiMode(Integer.valueOf(args[1]));
                            return;
                        }
                        writer.println("Incorrect number of arguments");
                        dumpHelp(writer);
                        return;
                    case '\n':
                        if (args.length == 2) {
                            ICarImpl.this.mCarProjectionService.setAccessPointTethering(Boolean.valueOf(args[1]).booleanValue());
                            return;
                        }
                        writer.println("Incorrect number of arguments");
                        dumpHelp(writer);
                        return;
                    case 11:
                        ICarImpl.this.mCarPowerManagementService.forceSimulatedResume();
                        writer.println("Resume: Simulating resuming from Deep Sleep");
                        return;
                    case '\f':
                        ICarImpl.this.mCarPowerManagementService.forceSuspendAndMaybeReboot(false);
                        writer.println("Resume: Simulating powering down to Deep Sleep");
                        return;
                    case '\r':
                        if (args.length != 2) {
                            writer.println("Incorrect number of arguments");
                            dumpHelp(writer);
                            return;
                        }
                        ICarImpl.this.mCarTrustedDeviceService.getCarTrustAgentEnrollmentService().setTrustedDeviceEnrollmentEnabled(Boolean.valueOf(args[1]).booleanValue());
                        ICarImpl.this.mCarTrustedDeviceService.getCarTrustAgentUnlockService().setTrustedDeviceUnlockEnabled(Boolean.valueOf(args[1]).booleanValue());
                        return;
                    case 14:
                        ICarImpl.this.mCarTrustedDeviceService.getCarTrustAgentEnrollmentService().removeAllTrustedDevices(ICarImpl.this.mUserManagerHelper.getCurrentForegroundUserId());
                        return;
                    case 15:
                        handleStartFixedActivity(args, writer);
                        return;
                    case 16:
                        handleStopFixedMode(args, writer);
                        return;
                    case 17:
                        if (args.length < 2) {
                            writer.println("Incorrect number of arguments");
                            dumpHelp(writer);
                            return;
                        }
                        handleInjectKey(args, writer);
                        return;
                    case 18:
                        if (args.length < 2) {
                            writer.println("Incorrect number of arguments.");
                            dumpHelp(writer);
                            return;
                        }
                        CarLog.CAR_DEBUG_FLAG = Integer.parseInt(args[1]);
                        return;
                    case 19:
                        if (args.length < 3) {
                            writer.println("Incorrect number of arguments.");
                            dumpHelp(writer);
                            return;
                        }
                        handleInjectDebug(args);
                        return;
                    case 20:
                    case 21:
                        if (args.length < 2) {
                            writer.println("Incorrect number of arguments.");
                            dumpHelp(writer);
                            return;
                        }
                        int display = Integer.parseInt(args[1]);
                        if (display == 0) {
                            ICarImpl.this.mHal.getPowerHal().simulateIviScreenEnable(COMMAND_DISPLAY_ON.equals(arg));
                        } else if (display == 1) {
                            ICarImpl.this.mCarPowerManagementService.simulateIcmScreenEnable(COMMAND_DISPLAY_ON.equals(arg));
                        } else if (display == 2) {
                            ICarImpl.this.mHal.getPowerHal().simulatePsnScreenEnable(COMMAND_DISPLAY_ON.equals(arg));
                        }
                        return;
                    case 22:
                        ICarImpl.this.mCarPermissionManagerService.initCarPermissionData();
                        return;
                    case 23:
                        writer.println("*Dump car permisison service statistics*");
                        writer.println("car-permission-denied command: \"" + arg + "\"");
                        StringBuilder sb = new StringBuilder();
                        sb.append("denied = ");
                        sb.append(ICarImpl.this.mCarPermissionManagerService.dumpDeniedPropInfo());
                        writer.println(sb.toString());
                        return;
                    case 24:
                        writer.println("*Dump car permisison service statistics*");
                        writer.println("car-permission-cost command: \"" + arg + "\"");
                        StringBuilder sb2 = new StringBuilder();
                        sb2.append("cost = ");
                        sb2.append(ICarImpl.this.mCarPermissionManagerService.dumpCostPropInfo());
                        writer.println(sb2.toString());
                        return;
                    case 25:
                        if (args.length < 3) {
                            writer.println("Incorrect number of arguments need to set [type, pos, action].");
                            dumpHelp(writer);
                            return;
                        }
                        int type = Integer.parseInt(args[1]);
                        int action = Integer.parseInt(args[2]);
                        int pos = Integer.parseInt(args[3]);
                        ICarImpl.this.mCarIntelligentEngineService.injectCarSceneAction(type, action, pos);
                        return;
                    case 26:
                        if (args.length == 13) {
                            float[] front = {Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]), Integer.parseInt(args[5]), Integer.parseInt(args[6])};
                            float[] rear = {Integer.parseInt(args[7]), Integer.parseInt(args[8]), Integer.parseInt(args[9]), Integer.parseInt(args[10]), Integer.parseInt(args[11]), Integer.parseInt(args[12])};
                            ICarImpl.this.mCarIntelligentEngineService.injectCarSceneCalibrationData(front, rear);
                            return;
                        }
                        writer.println("Incorrect number of arguments need to set [type, pos, action].");
                        dumpHelp(writer);
                        return;
                    case 27:
                        if (Build.IS_USER) {
                            writer.println("Permission denied, please use this tool in userdebug version!");
                            return;
                        } else if (args.length == 2) {
                            writer.println("Incorrect number of arguments, need to set feature name and value");
                            return;
                        } else if (args.length == 3) {
                            String featureName = args[1];
                            String value3 = args[2];
                            injectCarFeature(featureName, value3);
                            return;
                        } else {
                            return;
                        }
                    default:
                        writer.println("Unknown command: \"" + arg + "\"");
                        dumpHelp(writer);
                        return;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void handleStartFixedActivity(String[] args, PrintWriter writer) {
            if (args.length != 4) {
                writer.println("Incorrect number of arguments");
                dumpHelp(writer);
                return;
            }
            try {
                int displayId = Integer.parseInt(args[1]);
                String packageName = args[2];
                String activityName = args[3];
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(packageName, activityName));
                intent.addFlags(268468224);
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setLaunchDisplayId(displayId);
                if (!ICarImpl.this.mFixedActivityService.startFixedActivityModeForDisplayAndUser(intent, options, displayId, ActivityManager.getCurrentUser())) {
                    writer.println("Failed to start");
                } else {
                    writer.println("Succeeded");
                }
            } catch (NumberFormatException e) {
                writer.println("Wrong display id:" + args[1]);
            }
        }

        private void handleStopFixedMode(String[] args, PrintWriter writer) {
            if (args.length != 2) {
                writer.println("Incorrect number of arguments");
                dumpHelp(writer);
                return;
            }
            try {
                int displayId = Integer.parseInt(args[1]);
                ICarImpl.this.mFixedActivityService.stopFixedActivityMode(displayId);
            } catch (NumberFormatException e) {
                writer.println("Wrong display id:" + args[1]);
            }
        }

        /* JADX WARN: Code restructure failed: missing block: B:22:0x0051, code lost:
            throw new java.lang.IllegalArgumentException("key_code already set:" + r3);
         */
        /* JADX WARN: Removed duplicated region for block: B:18:0x002f  */
        /* JADX WARN: Removed duplicated region for block: B:24:0x005c A[Catch: Exception -> 0x00d0, TRY_LEAVE, TryCatch #0 {Exception -> 0x00d0, blocks: (B:3:0x0004, B:5:0x0009, B:20:0x0033, B:21:0x003b, B:22:0x0051, B:23:0x0052, B:24:0x005c, B:10:0x0019, B:13:0x0023), top: B:43:0x0004 }] */
        /*
            Code decompiled incorrectly, please refer to instructions dump.
            To view partially-correct add '--show-bad-code' argument
        */
        private void handleInjectKey(java.lang.String[] r11, java.io.PrintWriter r12) {
            /*
                Method dump skipped, instructions count: 233
                To view this dump add '--comments-level debug' option
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.car.ICarImpl.CarShellCommand.handleInjectKey(java.lang.String[], java.io.PrintWriter):void");
        }

        private void handleInjectDebug(String[] args) {
            String str = args[1];
            if (((str.hashCode() == 1671764162 && str.equals(PARAM_DEBUG_DISPLAY)) ? (char) 0 : (char) 65535) == 0) {
                ICarImpl.this.mHal.getPowerHal().setScreenDebug(Integer.parseInt(args[2]) > 0);
            }
        }

        private void forceDayNightMode(String arg, PrintWriter writer) {
            char c;
            int mode;
            int hashCode = arg.hashCode();
            if (hashCode == -905948230) {
                if (arg.equals(PARAM_SENSOR_MODE)) {
                    c = 2;
                }
                c = 65535;
            } else if (hashCode != 99228) {
                if (hashCode == 104817688 && arg.equals(PARAM_NIGHT_MODE)) {
                    c = 1;
                }
                c = 65535;
            } else {
                if (arg.equals(PARAM_DAY_MODE)) {
                    c = 0;
                }
                c = 65535;
            }
            if (c == 0) {
                mode = 1;
            } else if (c == 1) {
                mode = 2;
            } else if (c == 2) {
                mode = 0;
            } else {
                writer.println("Unknown value. Valid argument: day|night|sensor");
                return;
            }
            int current = ICarImpl.this.mCarNightService.forceDayNightMode(mode);
            String currentMode = null;
            if (current == 0) {
                currentMode = PARAM_SENSOR_MODE;
            } else if (current == 1) {
                currentMode = PARAM_DAY_MODE;
            } else if (current == 2) {
                currentMode = PARAM_NIGHT_MODE;
            }
            writer.println("DayNightMode changed to: " + currentMode);
        }

        private void forceGarageMode(String arg, PrintWriter writer) {
            char c;
            int hashCode = arg.hashCode();
            if (hashCode == -934938715) {
                if (arg.equals(PARAM_REBOOT)) {
                    c = 3;
                }
                c = 65535;
            } else if (hashCode == 3551) {
                if (arg.equals(PARAM_ON_MODE)) {
                    c = 0;
                }
                c = 65535;
            } else if (hashCode != 109935) {
                if (hashCode == 107944136 && arg.equals(PARAM_QUERY_MODE)) {
                    c = 2;
                }
                c = 65535;
            } else {
                if (arg.equals(PARAM_OFF_MODE)) {
                    c = 1;
                }
                c = 65535;
            }
            if (c == 0) {
                ICarImpl.this.mGarageModeService.forceStartGarageMode();
                writer.println("Garage mode: " + ICarImpl.this.mGarageModeService.isGarageModeActive());
            } else if (c == 1) {
                ICarImpl.this.mGarageModeService.stopAndResetGarageMode();
                writer.println("Garage mode: " + ICarImpl.this.mGarageModeService.isGarageModeActive());
            } else if (c == 2) {
                ICarImpl.this.mGarageModeService.dump(writer);
            } else if (c == 3) {
                ICarImpl.this.mCarPowerManagementService.forceSuspendAndMaybeReboot(true);
                writer.println("Entering Garage Mode. Will reboot when it completes.");
            } else {
                writer.println("Unknown value. Valid argument: on|off|query|reboot");
            }
        }

        private void injectVhalEvent(String property, String zone, String value, boolean isErrorEvent, PrintWriter writer) {
            if (zone != null && zone.equalsIgnoreCase(PARAM_VEHICLE_PROPERTY_AREA_GLOBAL) && !isPropertyAreaTypeGlobal(property)) {
                writer.println("Property area type inconsistent with given zone");
                return;
            }
            try {
                if (isErrorEvent) {
                    ICarImpl.this.mHal.injectOnPropertySetError(property, zone, value);
                } else {
                    ICarImpl.this.mHal.injectVhalEvent(property, zone, value);
                }
            } catch (NumberFormatException e) {
                writer.println("Invalid property Id zone Id or value" + e);
                dumpHelp(writer);
            }
        }

        private boolean isPropertyAreaTypeGlobal(String property) {
            return property != null && (Integer.decode(property).intValue() & VehicleArea.MASK) == 16777216;
        }

        private void injectCarFeature(String featureModel, String value) {
            try {
                StringBuffer sbf = new StringBuffer();
                sbf.append("persist.sys.xiaopeng.");
                sbf.append(featureModel);
                sbf.append(".debug");
                Slog.i(ICarImpl.TAG, "injectCarFeature:\t" + sbf.toString() + "\t" + value);
                SystemProperties.set(sbf.toString(), value);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
