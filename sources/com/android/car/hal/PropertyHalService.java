package com.android.car.hal;

import android.car.ValueUnavailableException;
import android.car.XpDebugLog;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.ICarPropertyEventListener;
import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.util.Slog;
import android.util.SparseArray;
import com.android.car.CarLog;
import com.android.car.CarServiceUtils;
import com.android.internal.annotations.GuardedBy;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
/* loaded from: classes3.dex */
public class PropertyHalService extends HalServiceBase {
    private static final boolean ENABLE_DUMP = false;
    private static final String TAG = "PropertyHalService";
    @GuardedBy({"mLock"})
    private PropertyHalListener mListener;
    private final VehicleHal mVehicleHal;
    private final boolean mDbg = true;
    private final LinkedList<CarPropertyEvent> mEventsToDispatch = new LinkedList<>();
    private final Map<Integer, CarPropertyConfig<?>> mProps = new ConcurrentHashMap();
    private final SparseArray<Float> mRates = new SparseArray<>();
    private final Object mLock = new Object();
    private final PropertyHalServiceIds mPropIds = new PropertyHalServiceIds();
    private final Set<Integer> mSubscribedPropIds = new HashSet();

    /* loaded from: classes3.dex */
    public interface PropertyHalListener {
        void onPropertyChange(List<CarPropertyEvent> list);

        void onPropertySetError(int i, int i2);
    }

    private int managerToHalPropId(int propId) {
        if (this.mProps.containsKey(Integer.valueOf(propId))) {
            return propId;
        }
        return -1;
    }

    private int halToManagerPropId(int halPropId) {
        if (this.mProps.containsKey(Integer.valueOf(halPropId))) {
            return halPropId;
        }
        return -1;
    }

    public int registerToHal(int pid, PropertyHalService halService, ICarPropertyEventListener listener) {
        return this.mVehicleHal.registerClientInfo(pid, halService, listener);
    }

    public void unregisterToHal(int pid, PropertyHalService halService, ICarPropertyEventListener listener) {
        this.mVehicleHal.unregisterClientInfo(pid, halService, listener);
    }

    public PropertyHalService(VehicleHal vehicleHal) {
        this.mVehicleHal = vehicleHal;
        Slog.i(TAG, "started PropertyHalService");
    }

    public void setListener(PropertyHalListener listener) {
        synchronized (this.mLock) {
            this.mListener = listener;
        }
    }

    public List<Integer> getMultiplePropertyIds() {
        return this.mPropIds.getMultiplePropertyIds();
    }

    public Map<Integer, CarPropertyConfig<?>> getPropertyList() {
        return this.mProps;
    }

    public CarPropertyValue getProperty(int mgrPropId, int areaId) {
        return getProperty(mgrPropId, areaId, true);
    }

    public CarPropertyValue getProperty(int mgrPropId, int areaId, boolean showNoDataLog) {
        int halPropId = managerToHalPropId(mgrPropId);
        if (halPropId == -1) {
            throw new IllegalArgumentException("Invalid property Id : 0x" + Integer.toHexString(mgrPropId));
        }
        try {
            VehiclePropValue value = this.mVehicleHal.get(halPropId, areaId, showNoDataLog);
            if (value == null) {
                return null;
            }
            return CarPropertyUtils.toCarPropertyValue(value, mgrPropId);
        } catch (PropertyTimeoutException e) {
            if (CarServiceUtils.isCduConnectedToCar()) {
                Slog.e(CarLog.TAG_PROPERTY, "get property failed due to timeout for 0x" + Integer.toHexString(halPropId) + e);
            } else {
                Slog.e(CarLog.TAG_PROPERTY, "get property failed due to timeout for 0x" + Integer.toHexString(halPropId));
            }
            throw new ValueUnavailableException(2);
        }
    }

    public float getSampleRate(int propId) {
        return this.mVehicleHal.getSampleRate(propId);
    }

    public String getReadPermission(int propId) {
        return this.mPropIds.getReadPermission(propId);
    }

    public String getWritePermission(int propId) {
        return this.mPropIds.getWritePermission(propId);
    }

    public boolean isDisplayUnitsProperty(int propId) {
        return this.mPropIds.isPropertyToChangeUnits(propId);
    }

    public void setProperty(CarPropertyValue prop) {
        int halPropId = managerToHalPropId(prop.getPropertyId());
        if (halPropId == -1) {
            throw new IllegalArgumentException("Invalid property Id : 0x" + Integer.toHexString(prop.getPropertyId()));
        }
        VehiclePropValue halProp = CarPropertyUtils.toVehiclePropValue(prop, halPropId);
        try {
            this.mVehicleHal.set(halProp);
        } catch (PropertyTimeoutException e) {
            if (CarServiceUtils.isCduConnectedToCar()) {
                Slog.e(CarLog.TAG_PROPERTY, "set property failed due to timeout for 0x" + Integer.toHexString(halPropId) + e);
            } else {
                Slog.e(CarLog.TAG_PROPERTY, "set property failed due to timeout for 0x" + Integer.toHexString(halPropId));
            }
            throw new ValueUnavailableException(2);
        }
    }

    public void setMultiProperties(List<CarPropertyValue> props) {
        ArrayList<VehiclePropValue> values = new ArrayList<>();
        for (CarPropertyValue prop : props) {
            int halPropId = managerToHalPropId(prop.getPropertyId());
            if (halPropId == -1) {
                throw new IllegalArgumentException("Invalid property Id : 0x" + Integer.toHexString(prop.getPropertyId()));
            } else if (halPropId == 356516106) {
                Slog.w(CarLog.TAG_HAL, "this interface may not Implemented.", new Throwable());
            } else {
                VehiclePropValue halProp = CarPropertyUtils.toVehiclePropValue(prop, halPropId);
                values.add(halProp);
            }
        }
        try {
            this.mVehicleHal.setMultiProperties(values);
        } catch (PropertyTimeoutException e) {
            if (CarServiceUtils.isCduConnectedToCar()) {
                Slog.e(CarLog.TAG_PROPERTY, "set property failed due to timeout" + e);
            } else {
                Slog.e(CarLog.TAG_PROPERTY, "set property failed due to timeout");
            }
            throw new ValueUnavailableException(2);
        }
    }

    public void subscribeProperty(int propId, float rate) {
        float rate2;
        if (CarLog.isCallbackLogEnable(propId)) {
            Slog.i(TAG, "subscribeProperty " + XpDebugLog.getPropertyDescription(propId) + ", rate=" + rate);
        }
        int halPropId = managerToHalPropId(propId);
        if (halPropId == -1) {
            throw new IllegalArgumentException("Invalid property Id : 0x" + Integer.toHexString(propId));
        }
        CarPropertyConfig cfg = this.mProps.get(Integer.valueOf(propId));
        if (rate > cfg.getMaxSampleRate()) {
            rate2 = cfg.getMaxSampleRate();
        } else if (rate >= cfg.getMinSampleRate()) {
            rate2 = rate;
        } else {
            rate2 = cfg.getMinSampleRate();
        }
        synchronized (this.mSubscribedPropIds) {
            this.mSubscribedPropIds.add(Integer.valueOf(halPropId));
        }
        this.mVehicleHal.subscribeProperty(this, halPropId, rate2);
    }

    public void unsubscribeProperty(int propId) {
        if (CarLog.isCallbackLogEnable(propId)) {
            Slog.i(TAG, "unsubscribeProperty " + XpDebugLog.getPropertyDescription(propId));
        }
        int halPropId = managerToHalPropId(propId);
        if (halPropId == -1) {
            throw new IllegalArgumentException("Invalid property Id : 0x" + Integer.toHexString(propId));
        }
        synchronized (this.mSubscribedPropIds) {
            if (this.mSubscribedPropIds.contains(Integer.valueOf(halPropId))) {
                this.mSubscribedPropIds.remove(Integer.valueOf(halPropId));
                this.mVehicleHal.unsubscribeProperty(this, halPropId);
            }
        }
    }

    @Override // com.android.car.hal.HalServiceBase
    public void init() {
        Slog.i(TAG, "init()");
    }

    @Override // com.android.car.hal.HalServiceBase
    public void release() {
        Slog.i(TAG, "release()");
        synchronized (this.mSubscribedPropIds) {
            for (Integer prop : this.mSubscribedPropIds) {
                this.mVehicleHal.unsubscribeProperty(this, prop.intValue());
            }
            this.mSubscribedPropIds.clear();
        }
        this.mProps.clear();
        synchronized (this.mLock) {
            this.mListener = null;
        }
    }

    @Override // com.android.car.hal.HalServiceBase
    public Collection<VehiclePropConfig> takeSupportedProperties(Collection<VehiclePropConfig> allProperties) {
        List<VehiclePropConfig> taken = new LinkedList<>();
        for (VehiclePropConfig p : allProperties) {
            int prop = p.prop;
            if (this.mPropIds.isSupportedProperty(prop)) {
                CarPropertyConfig config = CarPropertyUtils.toCarPropertyConfig(p, prop);
                taken.add(p);
                this.mProps.put(Integer.valueOf(prop), config);
                if (CarLog.isCallbackLogEnable(prop)) {
                    Slog.i(TAG, "takeSupportedProperties: " + XpDebugLog.getPropertyDescription(prop));
                }
            } else {
                Slog.i(TAG, XpDebugLog.getPropertyDescription(prop) + " not added to list");
            }
        }
        Slog.i(TAG, "takeSupportedProperties() took " + taken.size() + " properties");
        return taken;
    }

    @Override // com.android.car.hal.HalServiceBase
    public void handleHalEvents(List<VehiclePropValue> values) {
        PropertyHalListener listener;
        CarPropertyEvent event;
        this.mEventsToDispatch.clear();
        synchronized (this.mLock) {
            listener = this.mListener;
        }
        if (listener != null) {
            for (VehiclePropValue v : values) {
                if (v != null) {
                    int mgrPropId = halToManagerPropId(v.prop);
                    if (mgrPropId == -1) {
                        Slog.e(TAG, "Property is not supported: 0x" + Integer.toHexString(v.prop));
                    } else {
                        if (v.status == 0) {
                            CarPropertyValue<?> propVal = CarPropertyUtils.toCarPropertyValue(v, mgrPropId);
                            event = new CarPropertyEvent(0, propVal);
                        } else {
                            event = CarPropertyEvent.createErrorEventWithTimestamp(mgrPropId, v.timestamp, v.status);
                        }
                        this.mEventsToDispatch.add(event);
                    }
                }
            }
            listener.onPropertyChange(this.mEventsToDispatch);
            this.mEventsToDispatch.clear();
        }
    }

    @Override // com.android.car.hal.HalServiceBase
    public void handlePropertySetError(int property, int errorCode) {
        PropertyHalListener listener;
        synchronized (this.mLock) {
            listener = this.mListener;
        }
        if (listener != null) {
            listener.onPropertySetError(property, errorCode);
        }
    }

    @Override // com.android.car.hal.HalServiceBase
    public void dump(PrintWriter writer) {
    }

    public CarPropertyEvent convertHalEventToCarEvent(VehiclePropValue value) {
        int mgrPropId = halToManagerPropId(value.prop);
        if (mgrPropId == -1) {
            return null;
        }
        CarPropertyValue<?> propVal = CarPropertyUtils.toCarPropertyValue(value, mgrPropId);
        return new CarPropertyEvent(0, propVal);
    }
}
