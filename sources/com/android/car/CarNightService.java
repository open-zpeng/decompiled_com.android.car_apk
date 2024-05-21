package com.android.car;

import android.app.UiModeManager;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.ICarPropertyEventListener;
import android.content.Context;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.os.RemoteException;
import android.util.Slog;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
/* loaded from: classes3.dex */
public class CarNightService implements CarServiceBase {
    public static final boolean DBG = false;
    public static final int FORCED_DAY_MODE = 1;
    public static final int FORCED_NIGHT_MODE = 2;
    public static final int FORCED_SENSOR_MODE = 0;
    private CarPropertyService mCarPropertyService;
    private final Context mContext;
    private final UiModeManager mUiModeManager;
    private int mNightSetting = 2;
    private int mForcedMode = 0;
    private final ICarPropertyEventListener mICarPropertyEventListener = new ICarPropertyEventListener.Stub() { // from class: com.android.car.CarNightService.1
        public void onEvent(List<CarPropertyEvent> events) throws RemoteException {
            for (CarPropertyEvent event : events) {
                CarNightService.this.handlePropertyEvent(event);
            }
        }
    };

    @Retention(RetentionPolicy.SOURCE)
    /* loaded from: classes3.dex */
    public @interface DayNightSensorMode {
    }

    public synchronized void handlePropertyEvent(CarPropertyEvent event) {
        if (event == null) {
            return;
        }
        if (event.getEventType() == 0) {
            CarPropertyValue value = event.getCarPropertyValue();
            if (value.getPropertyId() == 287310855) {
                boolean nightMode = ((Boolean) value.getValue()).booleanValue();
                setNightMode(nightMode);
            }
        }
    }

    private synchronized void setNightMode(boolean nightMode) {
        if (nightMode) {
            this.mNightSetting = 2;
        } else {
            this.mNightSetting = 1;
        }
        if (this.mUiModeManager != null && this.mForcedMode == 0) {
            this.mUiModeManager.setNightMode(this.mNightSetting);
        }
    }

    public synchronized int forceDayNightMode(int mode) {
        int resultMode;
        if (this.mUiModeManager == null) {
            return -1;
        }
        if (mode == 0) {
            resultMode = this.mNightSetting;
            this.mForcedMode = 0;
        } else if (mode == 1) {
            resultMode = 1;
            this.mForcedMode = 1;
        } else if (mode == 2) {
            resultMode = 2;
            this.mForcedMode = 2;
        } else {
            Slog.e(CarLog.TAG_SENSOR, "Unknown forced day/night mode " + mode);
            return -1;
        }
        this.mUiModeManager.setNightMode(resultMode);
        return this.mUiModeManager.getNightMode();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public CarNightService(Context context, CarPropertyService propertyService) {
        this.mContext = context;
        this.mCarPropertyService = propertyService;
        this.mUiModeManager = (UiModeManager) this.mContext.getSystemService("uimode");
        if (this.mUiModeManager == null) {
            Slog.w(CarLog.TAG_SENSOR, "Failed to get UI_MODE_SERVICE");
        }
    }

    @Override // com.android.car.CarServiceBase
    public synchronized void init() {
        this.mCarPropertyService.registerListener(VehicleProperty.NIGHT_MODE, 0.0f, this.mICarPropertyEventListener);
        CarPropertyValue propertyValue = this.mCarPropertyService.getProperty(VehicleProperty.NIGHT_MODE, 0);
        if (propertyValue != null && propertyValue.getTimestamp() != 0) {
            setNightMode(((Boolean) propertyValue.getValue()).booleanValue());
        } else {
            Slog.w(CarLog.TAG_SENSOR, "Failed to get value of NIGHT_MODE");
            setNightMode(true);
        }
    }

    @Override // com.android.car.CarServiceBase
    public synchronized void release() {
    }

    @Override // com.android.car.CarServiceBase
    public synchronized void dump(PrintWriter writer) {
        String str;
        writer.println("*DAY NIGHT POLICY*");
        StringBuilder sb = new StringBuilder();
        sb.append("Mode:");
        sb.append(this.mNightSetting == 2 ? "night" : "day");
        writer.println(sb.toString());
        StringBuilder sb2 = new StringBuilder();
        sb2.append("Forced Mode? ");
        if (this.mForcedMode == 0) {
            str = "false";
        } else {
            str = this.mForcedMode == 1 ? "day" : "night";
        }
        sb2.append(str);
        writer.println(sb2.toString());
    }
}
