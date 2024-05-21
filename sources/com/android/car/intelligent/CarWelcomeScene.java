package com.android.car.intelligent;

import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.ICarPropertyEventListener;
import android.car.intelligent.CarIntelligentEngineManager;
import android.car.intelligent.CarSceneEvent;
import android.content.Context;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.os.SystemProperties;
import android.util.Slog;
import com.android.car.CarPropertyService;
import java.util.List;
import java.util.Locale;
/* loaded from: classes3.dex */
class CarWelcomeScene {
    private static final int INVALID = -1;
    private static final int[] REQUIRED_PROPERTIES = {VehicleProperty.BCM_DRVSEAT_BELTSBR_WARNING, VehicleProperty.BCM_PSNGRSEAT_BELTSBR_WARNING, VehicleProperty.BCM_2NDLEFTSEAT_BELTSBR_WARNING, VehicleProperty.BCM_2NDRIGHTSEAT_BELTSBR_WARNING, VehicleProperty.BCM_DRIVERSEAT_OCCUPIED, VehicleProperty.SRS_PSNGR_OCCUPANCY_ST, VehicleProperty.SRS_PSNGR_OCCUPANCY_ST, VehicleProperty.SRS_RLSEAT_OCCUPANCY_ST, VehicleProperty.SRS_RRSEAT_OCCUPANCY_ST, VehicleProperty.BCM_DOOR, VehicleProperty.VCU_CURRENT_GEARLEV, VehicleProperty.MCU_IG_DATA, VehicleProperty.VCU_CAR_STATIONARY_ST, VehicleProperty.MCU_FACTORY_MODE};
    private static final String TAG = "CarWelcomeScene";
    private Context mContext;
    private int mFactoryMode;
    private CarIntelligentEngineManager.CarSceneListener mListener;
    private CarPropertyService mPropertyService;
    private int mCacheFrontLBelt = -1;
    private int mCacheFrontRBelt = -1;
    private int mCacheSecondLBelt = -1;
    private int mCacheSecondRBelt = -1;
    private int mCacheFrontLOccupied = -1;
    private int mCacheFrontROccupied = -1;
    private int mCacheSecondLOccupied = -1;
    private int mCacheSecondROccupied = -1;
    private final int[] mCacheDoorVal = new int[4];
    private int mGearLevel = -1;
    private int mIgStatus = -1;
    private int mStationaryStatus = -1;
    private final ICarPropertyEventListener mICarPropertyEventListener = new ICarPropertyEventListener.Stub() { // from class: com.android.car.intelligent.CarWelcomeScene.1
        public void onEvent(List<CarPropertyEvent> events) {
            for (CarPropertyEvent event : events) {
                CarWelcomeScene.this.handlePropertyEvent(event);
            }
        }
    };

    public CarWelcomeScene(Context context, CarPropertyService propertyService) {
        this.mContext = context;
        this.mPropertyService = propertyService;
    }

    public void init(CarIntelligentEngineManager.CarSceneListener listener) {
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

    /* JADX INFO: Access modifiers changed from: private */
    public synchronized void handlePropertyEvent(CarPropertyEvent event) {
        try {
            CarPropertyValue value = event.getCarPropertyValue();
            int propId = value.getPropertyId();
            long timestamp = value.getTimestamp();
            switch (propId) {
                case VehicleProperty.VCU_CURRENT_GEARLEV /* 557847045 */:
                    this.mGearLevel = ((Integer) value.getValue()).intValue();
                    break;
                case VehicleProperty.VCU_CAR_STATIONARY_ST /* 557847132 */:
                    this.mStationaryStatus = ((Integer) value.getValue()).intValue();
                    break;
                case VehicleProperty.MCU_IG_DATA /* 557847561 */:
                    this.mIgStatus = ((Integer) value.getValue()).intValue();
                    break;
                case VehicleProperty.MCU_FACTORY_MODE /* 557847658 */:
                    this.mFactoryMode = ((Integer) value.getValue()).intValue();
                    SystemProperties.set("persist.sys.xiaopeng.factory_mode", String.valueOf(this.mFactoryMode));
                    Slog.i(TAG, "handlePropertyEvent: mFactoryMode:" + this.mFactoryMode);
                    break;
                case VehicleProperty.BCM_DRIVERSEAT_OCCUPIED /* 557849607 */:
                    this.mCacheFrontLOccupied = ((Integer) value.getValue()).intValue();
                    break;
                case VehicleProperty.BCM_DRVSEAT_BELTSBR_WARNING /* 557849612 */:
                    checkWelcomeWhenBeltChanged(1, ((Integer) value.getValue()).intValue(), timestamp);
                    this.mCacheFrontLBelt = ((Integer) value.getValue()).intValue();
                    break;
                case VehicleProperty.BCM_PSNGRSEAT_BELTSBR_WARNING /* 557849613 */:
                    checkWelcomeWhenBeltChanged(2, ((Integer) value.getValue()).intValue(), timestamp);
                    this.mCacheFrontRBelt = ((Integer) value.getValue()).intValue();
                    break;
                case VehicleProperty.BCM_2NDLEFTSEAT_BELTSBR_WARNING /* 557849614 */:
                    checkWelcomeWhenBeltChanged(3, ((Integer) value.getValue()).intValue(), timestamp);
                    this.mCacheSecondLBelt = ((Integer) value.getValue()).intValue();
                    break;
                case VehicleProperty.BCM_2NDRIGHTSEAT_BELTSBR_WARNING /* 557849616 */:
                    checkWelcomeWhenBeltChanged(4, ((Integer) value.getValue()).intValue(), timestamp);
                    this.mCacheSecondRBelt = ((Integer) value.getValue()).intValue();
                    break;
                case VehicleProperty.SRS_PSNGR_OCCUPANCY_ST /* 557849679 */:
                    this.mCacheFrontROccupied = ((Integer) value.getValue()).intValue();
                    break;
                case VehicleProperty.SRS_RLSEAT_OCCUPANCY_ST /* 557849800 */:
                    this.mCacheSecondLOccupied = ((Integer) value.getValue()).intValue();
                    break;
                case VehicleProperty.SRS_RRSEAT_OCCUPANCY_ST /* 557849802 */:
                    this.mCacheSecondROccupied = ((Integer) value.getValue()).intValue();
                    break;
                case VehicleProperty.BCM_DOOR /* 557915161 */:
                    Integer[] data = (Integer[]) value.getValue();
                    if (data != null && data.length == 4) {
                        checkWelcomeWhenDoorChanged(data, timestamp);
                        this.mCacheDoorVal[0] = data[0].intValue();
                        this.mCacheDoorVal[1] = data[1].intValue();
                        this.mCacheDoorVal[2] = data[2].intValue();
                        this.mCacheDoorVal[3] = data[3].intValue();
                        break;
                    }
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int getCacheOccupiedStatusByPosition(int pos) {
        if (pos != 1) {
            if (pos != 2) {
                if (pos != 3) {
                    if (pos == 4) {
                        return this.mCacheSecondROccupied;
                    }
                    return -1;
                }
                return this.mCacheSecondLOccupied;
            }
            return this.mCacheFrontROccupied;
        }
        return this.mCacheFrontLOccupied;
    }

    private int getCacheBeltStatusByPosition(int pos) {
        if (pos != 1) {
            if (pos != 2) {
                if (pos != 3) {
                    if (pos == 4) {
                        return this.mCacheSecondRBelt;
                    }
                    return -1;
                }
                return this.mCacheSecondLBelt;
            }
            return this.mCacheFrontRBelt;
        }
        return this.mCacheFrontLBelt;
    }

    private int getCacheDoorStatusByPosition(int pos) {
        if (pos != 1) {
            if (pos != 2) {
                if (pos != 3) {
                    if (pos == 4) {
                        return this.mCacheDoorVal[3];
                    }
                    return -1;
                }
                return this.mCacheDoorVal[2];
            }
            return this.mCacheDoorVal[1];
        }
        return this.mCacheDoorVal[0];
    }

    private void checkWelcomeWhenBeltChanged(int pos, int belt, long timestamp) {
        int door = getCacheDoorStatusByPosition(pos);
        int occupied = getCacheOccupiedStatusByPosition(pos);
        int oldBelt = getCacheBeltStatusByPosition(pos);
        if (belt == oldBelt || belt == -1 || this.mListener == null) {
            return;
        }
        Slog.i(TAG, String.format(Locale.getDefault(), "checkWelcomeWhenBeltChanged: pos=%d ig=%d gear=%d station=%d occupied=%d belt=%d door=%d", Integer.valueOf(pos), Integer.valueOf(this.mIgStatus), Integer.valueOf(this.mGearLevel), Integer.valueOf(this.mStationaryStatus), Integer.valueOf(occupied), Integer.valueOf(belt), Integer.valueOf(door)));
        if (this.mIgStatus == 1 && this.mGearLevel == 4 && this.mStationaryStatus == 1 && occupied == 1 && door == 1 && belt == 1) {
            CarSceneEvent sceneEvent = new CarSceneEvent();
            sceneEvent.setSceneAction(1);
            sceneEvent.setScenePosition(pos);
            sceneEvent.setTimeStamp(timestamp);
            Slog.i(TAG, String.format("checkWelcomeWhenBeltChanged: onWelcomeSceneChanged sceneEvent=%s", sceneEvent));
            this.mListener.onWelcomeSceneChanged(sceneEvent);
        }
    }

    private void checkWelcomeWhenDoorChanged(Integer[] doors, long timestamp) {
        if (this.mListener == null) {
            return;
        }
        for (int i = 0; i < this.mCacheDoorVal.length; i++) {
            int pos = i + 1;
            int oldDoor = getCacheDoorStatusByPosition(pos);
            if (oldDoor != -1 && oldDoor != doors[i].intValue()) {
                int occupied = getCacheOccupiedStatusByPosition(pos);
                int belt = getCacheBeltStatusByPosition(pos);
                Slog.i(TAG, String.format(Locale.getDefault(), "checkWelcomeWhenDoorChanged: pos=%d ig=%d gear=%d station=%d occupied=%d belt=%d door=%d", Integer.valueOf(pos), Integer.valueOf(this.mIgStatus), Integer.valueOf(this.mGearLevel), Integer.valueOf(this.mStationaryStatus), Integer.valueOf(occupied), Integer.valueOf(belt), doors[i]));
                if (this.mIgStatus == 1 && this.mGearLevel == 4 && this.mStationaryStatus == 1 && occupied == 1 && belt == 1) {
                    if (doors[i].intValue() == 1) {
                        CarSceneEvent sceneEvent = new CarSceneEvent();
                        sceneEvent.setSceneAction(1);
                        sceneEvent.setScenePosition(pos);
                        sceneEvent.setTimeStamp(timestamp);
                        Slog.i(TAG, String.format("checkWelcomeWhenDoorChanged: onWelcomeSceneChanged sceneEvent=%s", sceneEvent));
                        this.mListener.onWelcomeSceneChanged(sceneEvent);
                    } else if (doors[i].intValue() == 0) {
                        CarSceneEvent sceneEvent2 = new CarSceneEvent();
                        sceneEvent2.setSceneAction(0);
                        sceneEvent2.setScenePosition(pos);
                        sceneEvent2.setTimeStamp(timestamp);
                        Slog.i(TAG, String.format("checkWelcomeWhenDoorChanged: onWelcomeSceneChanged sceneEvent=%s", sceneEvent2));
                        this.mListener.onWelcomeSceneChanged(sceneEvent2);
                    }
                }
            }
        }
    }
}
