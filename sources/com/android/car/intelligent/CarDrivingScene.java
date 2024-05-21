package com.android.car.intelligent;

import android.car.Car;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.ICarPropertyEventListener;
import android.car.intelligent.CarIntelligentEngineManager;
import android.car.intelligent.CarSceneEvent;
import android.content.Context;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Slog;
import com.android.car.CarPropertyService;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;
/* loaded from: classes3.dex */
public class CarDrivingScene {
    private static final int SCENE_RADAR_COUNT = 6;
    private static final int SCENE_TRIGGER_RADAR_MIN_VALUE = 0;
    private static final int SCENE_TRIGGER_RADAR_THRESHOLD_VALUE = 20;
    private static final int SCENE_TRIGGER_SPEED_VALUE = 15;
    private static final String TAG = "CarDrivingScene";
    private String mCDUType;
    private Context mContext;
    private CarIntelligentEngineManager.CarDrivingSceneListener mListener;
    private CarPropertyService mPropertyService;
    private static final int[] REQUIRED_PROPERTIES = {VehicleProperty.SCU_FRONT_RADAR, VehicleProperty.SCU_TAIL_RADAR, VehicleProperty.VCU_RAW_CAR_SPEED, VehicleProperty.VCU_CURRENT_GEARLEV, VehicleProperty.ESC_AVH, VehicleProperty.MCU_IG_DATA, VehicleProperty.XPU_NEDC_STATUS, VehicleProperty.AVM_WORK_ST};
    private static final float[] sRadarCalibrationFront1 = {50.0f, 50.0f, 50.0f, 60.0f, 60.0f, 50.0f};
    private static final float[] sRadarCalibrationRear1 = {50.0f, 50.0f, 50.0f, 50.0f, 50.0f, 50.0f};
    private static final float[] sRadarCalibrationFront2 = {60.0f, 60.0f, 60.0f, 70.0f, 70.0f, 60.0f};
    private static final float[] sRadarCalibrationRear2 = {60.0f, 60.0f, 60.0f, 60.0f, 60.0f, 60.0f};
    private static final float[] sRadarCalibrationFront3 = {75.0f, 75.0f, 75.0f, 85.0f, 85.0f, 75.0f};
    private static final float[] sRadarCalibrationRear3 = {75.0f, 75.0f, 75.0f, 75.0f, 75.0f, 75.0f};
    private final float[] mCacheRadarFront = new float[6];
    private final float[] mCacheRadarRear = new float[6];
    private float[] mRadarCalibrationFront = sRadarCalibrationFront2;
    private float[] mRadarCalibrationRear = sRadarCalibrationRear2;
    private float mCarSpeed = -1.0f;
    private int mGearLevel = -1;
    private int mAVH = -1;
    private volatile STATUS mStatus = STATUS.EXIT;
    private volatile int mNotifyAction = -1;
    private int mLevel = -1;
    private int mIgData = -1;
    private int mNEDCSt = 0;
    private boolean mIsAVMSupport = false;
    private boolean mIsNEDCBlock = true;
    private int mAvmWorkSt = -1;
    private final ICarPropertyEventListener mICarPropertyEventListener = new ICarPropertyEventListener.Stub() { // from class: com.android.car.intelligent.CarDrivingScene.1
        public void onEvent(List<CarPropertyEvent> events) {
            for (CarPropertyEvent event : events) {
                CarDrivingScene.this.handlePropertyEvent(event);
            }
        }
    };
    private final MessageHandler mDispatchHandler = new MessageHandler(this, Looper.getMainLooper());

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public enum STATUS {
        ENTER,
        EXIT,
        EXIT_DELAY
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static class MessageHandler extends Handler {
        public static final int MSG_SCENE_CHANGED = 1;
        public static final int TIME_MSG_SCENE_CHANGED_DELAY = 3000;
        private final WeakReference<CarDrivingScene> mService;

        public MessageHandler(CarDrivingScene service, Looper looper) {
            super(looper);
            this.mService = new WeakReference<>(service);
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == 1 && this.mService.get() != null) {
                this.mService.get().dispatchEventChanged((CarSceneEvent) msg.obj);
            }
        }
    }

    public CarDrivingScene(Context context, CarPropertyService propertyService) {
        this.mContext = context;
        this.mPropertyService = propertyService;
    }

    public void init(CarIntelligentEngineManager.CarDrivingSceneListener listener) {
        this.mIsAVMSupport = SystemProperties.getInt("persist.sys.xiaopeng.AVM", 0) == 1;
        if (!this.mIsAVMSupport) {
            Slog.i(TAG, "init: cannot support AVM, cannot run CarDrivingScene!");
            return;
        }
        this.mCDUType = Car.getXpCduType();
        if ("Q1".equals(this.mCDUType) || "Q2".equals(this.mCDUType) || "Q3".equals(this.mCDUType) || "Q5".equals(this.mCDUType) || "Q6".equals(this.mCDUType) || "Q3A".equals(this.mCDUType)) {
            this.mIsNEDCBlock = false;
            Slog.i(TAG, "init: nedc cannot block CarDrivingScene!");
        }
        int level = Settings.Global.getInt(this.mContext.getContentResolver(), "android.car.VALUE_USER_SWITCH_CAR_DRIVING_SCENE_NRA_LEVEL", 2);
        setCarDrivingSceneNRALevel(level);
        subscribeToProperties();
        this.mListener = listener;
    }

    public void release() {
        int[] iArr;
        for (int property : REQUIRED_PROPERTIES) {
            this.mPropertyService.unregisterListener(property, this.mICarPropertyEventListener);
        }
        this.mListener = null;
    }

    private synchronized void subscribeToProperties() {
        int[] iArr;
        for (int propertyId : REQUIRED_PROPERTIES) {
            this.mPropertyService.registerListener(propertyId, 0.0f, this.mICarPropertyEventListener);
        }
    }

    public void setCarDrivingSceneNRALevel(int level) {
        this.mLevel = level;
        if (level != 0) {
            if (level == 1) {
                this.mRadarCalibrationFront = sRadarCalibrationFront1;
                this.mRadarCalibrationRear = sRadarCalibrationRear1;
            } else if (level == 2) {
                this.mRadarCalibrationFront = sRadarCalibrationFront2;
                this.mRadarCalibrationRear = sRadarCalibrationRear2;
            } else if (level == 3) {
                this.mRadarCalibrationFront = sRadarCalibrationFront3;
                this.mRadarCalibrationRear = sRadarCalibrationRear3;
            }
        }
        Slog.i(TAG, "setCarDrivingSceneNRALevel: level:" + level + " front:" + Arrays.toString(this.mRadarCalibrationFront) + " rear:" + Arrays.toString(this.mRadarCalibrationRear));
    }

    public void injectCarSceneCalibrationData(float[] front, float[] rear) {
        Slog.i(TAG, "injectCarSceneCalibrationData: front:" + Arrays.toString(front) + " rear:" + Arrays.toString(rear));
        if (front != null && front.length == 6 && rear != null && rear.length == 6) {
            for (int i = 0; i < 6; i++) {
                if (front[i] > 0.0f) {
                    this.mRadarCalibrationFront[i] = front[i];
                }
                if (rear[i] > 0.0f) {
                    this.mRadarCalibrationRear[i] = front[i];
                }
            }
        }
    }

    private void dispatchEventMessage(STATUS status, CarSceneEvent sceneEvent) {
        this.mStatus = status;
        this.mDispatchHandler.removeMessages(1);
        Message msg = new Message();
        msg.what = 1;
        msg.obj = sceneEvent;
        if (status == STATUS.EXIT_DELAY) {
            this.mDispatchHandler.sendMessageDelayed(msg, 3000L);
        } else {
            this.mDispatchHandler.sendMessage(msg);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void dispatchEventChanged(CarSceneEvent sceneEvent) {
        Slog.i(TAG, "dispatchEvent: mNotifyAction:" + this.mNotifyAction + " sceneEvent:" + sceneEvent);
        if (this.mListener != null && this.mNotifyAction != sceneEvent.getSceneAction()) {
            this.mNotifyAction = sceneEvent.getSceneAction();
            this.mListener.onCarDrivingSceneChanged(sceneEvent);
        }
    }

    private void checkRadarChanged(long timestamp) {
        if (this.mLevel <= 0 || this.mGearLevel <= 0 || this.mAvmWorkSt == -1) {
            return;
        }
        if (this.mIsNEDCBlock && this.mNEDCSt != 0) {
            return;
        }
        int i = this.mAvmWorkSt;
        if ((i != 1 && i != 3) || this.mIgData != 1 || this.mCarSpeed > 15.0f || this.mGearLevel != 1 || this.mAVH == 1) {
            return;
        }
        if (this.mStatus != STATUS.ENTER) {
            boolean enterState = false;
            int i2 = 0;
            while (true) {
                if (i2 >= 6) {
                    break;
                } else if (this.mCacheRadarFront[i2] > this.mRadarCalibrationFront[i2]) {
                    if (this.mCacheRadarRear[i2] > this.mRadarCalibrationRear[i2]) {
                        i2++;
                    } else {
                        enterState = true;
                        break;
                    }
                } else {
                    enterState = true;
                    break;
                }
            }
            if (enterState) {
                CarSceneEvent sceneEvent = new CarSceneEvent(timestamp, 2, 0, 0);
                Slog.i(TAG, String.format("checkRadarChanged: mCacheRadarFront:%s mCacheRadarRear:%s sceneEvent:%s", Arrays.toString(this.mCacheRadarFront), Arrays.toString(this.mCacheRadarRear), sceneEvent));
                dispatchEventMessage(STATUS.ENTER, sceneEvent);
                return;
            }
            return;
        }
        boolean enterState2 = false;
        int i3 = 0;
        while (true) {
            if (i3 >= 6) {
                break;
            } else if (this.mCacheRadarFront[i3] > this.mRadarCalibrationFront[i3] + 20.0f) {
                if (this.mCacheRadarRear[i3] > this.mRadarCalibrationRear[i3] + 20.0f) {
                    i3++;
                } else {
                    enterState2 = true;
                    break;
                }
            } else {
                enterState2 = true;
                break;
            }
        }
        if (!enterState2) {
            CarSceneEvent sceneEvent2 = new CarSceneEvent(timestamp, 2, 1, 0);
            sceneEvent2.setExitReason(2);
            Slog.i(TAG, String.format("checkRadarChanged: mCacheRadarFront:%s mCacheRadarRear:%s sceneEvent:%s", Arrays.toString(this.mCacheRadarFront), Arrays.toString(this.mCacheRadarRear), sceneEvent2));
            dispatchEventMessage(STATUS.EXIT, sceneEvent2);
        }
    }

    private void checkCarSpeedChanged(long timestamp) {
        if (this.mCarSpeed > 15.0f && this.mStatus == STATUS.ENTER) {
            CarSceneEvent sceneEvent = new CarSceneEvent(timestamp, 2, 1, 0);
            sceneEvent.setExitReason(3);
            Slog.i(TAG, String.format("checkCarSpeedChanged: mCarSpeed:%s sceneEvent:%s", Float.valueOf(this.mCarSpeed), sceneEvent));
            dispatchEventMessage(STATUS.EXIT, sceneEvent);
        }
    }

    private void checkGearLevelChanged(long timestamp) {
        int i = this.mGearLevel;
        if (i == 2 || (i == 4 && this.mStatus == STATUS.ENTER)) {
            CarSceneEvent sceneEvent = new CarSceneEvent(timestamp, 2, 1, 0);
            sceneEvent.setExitReason(1);
            Slog.i(TAG, String.format("checkGearLevelChanged: mGearLevel:%s sceneEvent:%s", Integer.valueOf(this.mGearLevel), sceneEvent));
            dispatchEventMessage(STATUS.EXIT, sceneEvent);
        } else if (this.mGearLevel == 3 && this.mStatus == STATUS.ENTER) {
            CarSceneEvent sceneEvent2 = new CarSceneEvent(timestamp, 2, 1, 0);
            Slog.i(TAG, String.format("checkGearLevelChanged: mGearLevel:%s sceneEvent:%s", Integer.valueOf(this.mGearLevel), sceneEvent2));
            dispatchEventMessage(STATUS.EXIT, sceneEvent2);
        }
    }

    private void checkAVHChanged(long timestamp) {
        if (this.mAVH == 1 && this.mStatus == STATUS.ENTER) {
            CarSceneEvent sceneEvent = new CarSceneEvent(timestamp, 2, 1, 0);
            sceneEvent.setExitReason(4);
            Slog.i(TAG, String.format("checkAVHChanged: mAVH:%s sceneEvent:%s", Integer.valueOf(this.mAVH), sceneEvent));
            dispatchEventMessage(STATUS.EXIT, sceneEvent);
        }
    }

    private void checkNEDCChanged(long timestamp) {
        if (this.mIsNEDCBlock && this.mNEDCSt != 0 && this.mStatus == STATUS.ENTER) {
            CarSceneEvent sceneEvent = new CarSceneEvent(timestamp, 2, 1, 0);
            Slog.i(TAG, String.format("checkNEDCChanged: mNEDCSt:%s sceneEvent=%s", Integer.valueOf(this.mNEDCSt), sceneEvent));
            dispatchEventMessage(STATUS.EXIT_DELAY, sceneEvent);
        }
    }

    private void checkAvmWorkStChanged(long timestamp) {
        int i = this.mAvmWorkSt;
        if (i != 1 && i != 3 && this.mStatus == STATUS.ENTER) {
            CarSceneEvent sceneEvent = new CarSceneEvent(timestamp, 2, 1, 0);
            Slog.i(TAG, String.format("checkAvmWorkStChanged: mAvmWorkSt:%s sceneEvent=%s", Integer.valueOf(this.mAvmWorkSt), sceneEvent));
            dispatchEventMessage(STATUS.EXIT_DELAY, sceneEvent);
        }
    }

    private void checkIgStateChanged(long timestamp) {
        if (this.mIgData != 1 && this.mStatus == STATUS.ENTER) {
            CarSceneEvent sceneEvent = new CarSceneEvent(timestamp, 2, 1, 0);
            Slog.i(TAG, String.format("checkIgStateChanged: mIgData:%s sceneEvent=%s", Integer.valueOf(this.mIgData), sceneEvent));
            dispatchEventMessage(STATUS.EXIT, sceneEvent);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public synchronized void handlePropertyEvent(CarPropertyEvent event) {
        try {
            CarPropertyValue value = event.getCarPropertyValue();
            int propId = value.getPropertyId();
            long timestamp = value.getTimestamp();
            switch (propId) {
                case VehicleProperty.VCU_CURRENT_GEARLEV /* 557847045 */:
                    this.mGearLevel = ((Integer) value.getValue()).intValue();
                    checkGearLevelChanged(timestamp);
                    break;
                case VehicleProperty.MCU_IG_DATA /* 557847561 */:
                    this.mIgData = ((Integer) value.getValue()).intValue();
                    checkIgStateChanged(this.mIgData);
                    break;
                case VehicleProperty.ESC_AVH /* 557851651 */:
                    this.mAVH = ((Integer) value.getValue()).intValue();
                    checkAVHChanged(timestamp);
                    break;
                case VehicleProperty.AVM_WORK_ST /* 557855760 */:
                    this.mAvmWorkSt = ((Integer) value.getValue()).intValue();
                    checkAvmWorkStChanged(timestamp);
                    break;
                case VehicleProperty.XPU_NEDC_STATUS /* 557856775 */:
                    this.mNEDCSt = ((Integer) value.getValue()).intValue();
                    checkNEDCChanged(timestamp);
                    break;
                case VehicleProperty.VCU_RAW_CAR_SPEED /* 559944229 */:
                    this.mCarSpeed = ((Float) value.getValue()).floatValue();
                    checkCarSpeedChanged(timestamp);
                    break;
                case VehicleProperty.SCU_FRONT_RADAR /* 560014876 */:
                    Float[] data = (Float[]) value.getValue();
                    if (data != null && data.length == 6) {
                        this.mCacheRadarFront[0] = data[0].floatValue();
                        this.mCacheRadarFront[1] = data[1].floatValue();
                        this.mCacheRadarFront[2] = data[2].floatValue();
                        this.mCacheRadarFront[3] = data[3].floatValue();
                        this.mCacheRadarFront[4] = data[4].floatValue();
                        this.mCacheRadarFront[5] = data[5].floatValue();
                        checkRadarChanged(timestamp);
                    }
                    break;
                case VehicleProperty.SCU_TAIL_RADAR /* 560014877 */:
                    Float[] data2 = (Float[]) value.getValue();
                    if (data2 != null && data2.length == 6) {
                        this.mCacheRadarRear[0] = data2[0].floatValue();
                        this.mCacheRadarRear[1] = data2[1].floatValue();
                        this.mCacheRadarRear[2] = data2[2].floatValue();
                        this.mCacheRadarRear[3] = data2[3].floatValue();
                        this.mCacheRadarRear[4] = data2[4].floatValue();
                        this.mCacheRadarRear[5] = data2[5].floatValue();
                        checkRadarChanged(timestamp);
                    }
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
