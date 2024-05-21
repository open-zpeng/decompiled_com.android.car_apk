package com.android.car.hal;

import android.car.diagnostic.CarDiagnosticEvent;
import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.util.Slog;
import android.util.SparseArray;
import com.android.car.CarLog;
import com.android.car.CarServiceUtils;
import com.android.car.vehiclehal.VehiclePropValueBuilder;
import com.android.internal.annotations.GuardedBy;
import java.io.PrintWriter;
import java.util.BitSet;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;
/* loaded from: classes3.dex */
public class DiagnosticHalService extends HalServiceBase {
    static final boolean DEBUG = true;
    static final int OBD2_SELECTIVE_FRAME_CLEAR = 1;
    @GuardedBy({"mLock"})
    private DiagnosticListener mDiagnosticListener;
    private final VehicleHal mVehicleHal;
    private final Object mLock = new Object();
    @GuardedBy({"mLock"})
    private boolean mIsReady = false;
    @GuardedBy({"mLock"})
    private final DiagnosticCapabilities mDiagnosticCapabilities = new DiagnosticCapabilities();
    @GuardedBy({"mLock"})
    protected final SparseArray<VehiclePropConfig> mVehiclePropertyToConfig = new SparseArray<>();
    @GuardedBy({"mLock"})
    protected final SparseArray<VehiclePropConfig> mSensorTypeToConfig = new SparseArray<>();
    private final LinkedList<CarDiagnosticEvent> mEventsToDispatch = new LinkedList<>();

    /* loaded from: classes3.dex */
    public interface DiagnosticListener {
        void onDiagnosticEvents(List<CarDiagnosticEvent> list);
    }

    /* loaded from: classes3.dex */
    public static class DiagnosticCapabilities {
        private final CopyOnWriteArraySet<Integer> mProperties = new CopyOnWriteArraySet<>();

        void setSupported(int propertyId) {
            this.mProperties.add(Integer.valueOf(propertyId));
        }

        boolean isSupported(int propertyId) {
            return this.mProperties.contains(Integer.valueOf(propertyId));
        }

        public boolean isLiveFrameSupported() {
            return isSupported(VehicleProperty.OBD2_LIVE_FRAME);
        }

        public boolean isFreezeFrameSupported() {
            return isSupported(VehicleProperty.OBD2_FREEZE_FRAME);
        }

        public boolean isFreezeFrameInfoSupported() {
            return isSupported(VehicleProperty.OBD2_FREEZE_FRAME_INFO);
        }

        public boolean isFreezeFrameClearSupported() {
            return isSupported(VehicleProperty.OBD2_FREEZE_FRAME_CLEAR);
        }

        public boolean isSelectiveClearFreezeFramesSupported() {
            return isSupported(1);
        }

        void clear() {
            this.mProperties.clear();
        }
    }

    public DiagnosticHalService(VehicleHal hal) {
        this.mVehicleHal = hal;
    }

    @Override // com.android.car.hal.HalServiceBase
    public Collection<VehiclePropConfig> takeSupportedProperties(Collection<VehiclePropConfig> allProperties) {
        Slog.i(CarLog.TAG_DIAGNOSTIC, "takeSupportedProperties");
        LinkedList<VehiclePropConfig> supportedProperties = new LinkedList<>();
        for (VehiclePropConfig vp : allProperties) {
            int sensorType = getTokenForProperty(vp);
            if (sensorType == -1) {
                Slog.i(CarLog.TAG_DIAGNOSTIC, "0x" + Integer.toHexString(vp.prop) + " ignored");
            } else {
                supportedProperties.add(vp);
                synchronized (this.mLock) {
                    this.mSensorTypeToConfig.append(sensorType, vp);
                }
            }
        }
        return supportedProperties;
    }

    protected int getTokenForProperty(VehiclePropConfig propConfig) {
        switch (propConfig.prop) {
            case VehicleProperty.OBD2_LIVE_FRAME /* 299896064 */:
                this.mDiagnosticCapabilities.setSupported(propConfig.prop);
                this.mVehiclePropertyToConfig.put(propConfig.prop, propConfig);
                Slog.i(CarLog.TAG_DIAGNOSTIC, String.format("configArray for OBD2_LIVE_FRAME is %s", propConfig.configArray));
                return 0;
            case VehicleProperty.OBD2_FREEZE_FRAME /* 299896065 */:
                this.mDiagnosticCapabilities.setSupported(propConfig.prop);
                this.mVehiclePropertyToConfig.put(propConfig.prop, propConfig);
                Slog.i(CarLog.TAG_DIAGNOSTIC, String.format("configArray for OBD2_FREEZE_FRAME is %s", propConfig.configArray));
                return 1;
            case VehicleProperty.OBD2_FREEZE_FRAME_INFO /* 299896066 */:
                this.mDiagnosticCapabilities.setSupported(propConfig.prop);
                return propConfig.prop;
            case VehicleProperty.OBD2_FREEZE_FRAME_CLEAR /* 299896067 */:
                this.mDiagnosticCapabilities.setSupported(propConfig.prop);
                Slog.i(CarLog.TAG_DIAGNOSTIC, String.format("configArray for OBD2_FREEZE_FRAME_CLEAR is %s", propConfig.configArray));
                if (propConfig.configArray.size() < 1) {
                    Slog.e(CarLog.TAG_DIAGNOSTIC, String.format("property 0x%x does not specify whether it supports selective clearing of freeze frames. assuming it does not.", Integer.valueOf(propConfig.prop)));
                } else if (propConfig.configArray.get(0).intValue() == 1) {
                    this.mDiagnosticCapabilities.setSupported(1);
                }
                return propConfig.prop;
            default:
                return -1;
        }
    }

    @Override // com.android.car.hal.HalServiceBase
    public void init() {
        Slog.i(CarLog.TAG_DIAGNOSTIC, "init()");
        synchronized (this.mLock) {
            this.mIsReady = true;
        }
    }

    @Override // com.android.car.hal.HalServiceBase
    public void release() {
        synchronized (this.mLock) {
            this.mDiagnosticCapabilities.clear();
            this.mIsReady = false;
        }
    }

    public boolean isReady() {
        return this.mIsReady;
    }

    public int[] getSupportedDiagnosticProperties() {
        int[] supportedDiagnosticProperties;
        synchronized (this.mLock) {
            supportedDiagnosticProperties = new int[this.mSensorTypeToConfig.size()];
            for (int i = 0; i < supportedDiagnosticProperties.length; i++) {
                supportedDiagnosticProperties[i] = this.mSensorTypeToConfig.keyAt(i);
            }
        }
        return supportedDiagnosticProperties;
    }

    public boolean requestDiagnosticStart(int sensorType, int rate) {
        VehiclePropConfig propConfig;
        synchronized (this.mLock) {
            propConfig = this.mSensorTypeToConfig.get(sensorType);
        }
        if (propConfig == null) {
            Slog.e(CarLog.TAG_DIAGNOSTIC, "VehiclePropConfig not found, propertyId: 0x" + Integer.toHexString(sensorType));
            return false;
        }
        Slog.i(CarLog.TAG_DIAGNOSTIC, "requestDiagnosticStart, propertyId: 0x" + Integer.toHexString(propConfig.prop) + ", rate: " + rate);
        this.mVehicleHal.subscribeProperty(this, propConfig.prop, fixSamplingRateForProperty(propConfig, rate));
        return true;
    }

    public void requestDiagnosticStop(int sensorType) {
        VehiclePropConfig propConfig;
        synchronized (this.mLock) {
            propConfig = this.mSensorTypeToConfig.get(sensorType);
        }
        if (propConfig == null) {
            Slog.e(CarLog.TAG_DIAGNOSTIC, "VehiclePropConfig not found, propertyId: 0x" + Integer.toHexString(sensorType));
            return;
        }
        Slog.i(CarLog.TAG_DIAGNOSTIC, "requestDiagnosticStop, propertyId: 0x" + Integer.toHexString(propConfig.prop));
        this.mVehicleHal.unsubscribeProperty(this, propConfig.prop);
    }

    public VehiclePropValue getCurrentDiagnosticValue(int sensorType) {
        VehiclePropConfig propConfig;
        synchronized (this.mLock) {
            propConfig = this.mSensorTypeToConfig.get(sensorType);
        }
        if (propConfig == null) {
            Slog.e(CarLog.TAG_DIAGNOSTIC, "property not available 0x" + Integer.toHexString(sensorType));
            return null;
        }
        try {
            return this.mVehicleHal.get(propConfig.prop);
        } catch (PropertyTimeoutException e) {
            Slog.e(CarLog.TAG_DIAGNOSTIC, "property not ready 0x" + Integer.toHexString(propConfig.prop), e);
            return null;
        }
    }

    private VehiclePropConfig getPropConfig(int halPropId) {
        VehiclePropConfig config;
        synchronized (this.mLock) {
            config = this.mVehiclePropertyToConfig.get(halPropId, null);
        }
        return config;
    }

    private List<Integer> getPropConfigArray(int halPropId) {
        VehiclePropConfig propConfig = getPropConfig(halPropId);
        return propConfig.configArray;
    }

    private int getNumIntegerSensors(int halPropId) {
        List<Integer> configArray = getPropConfigArray(halPropId);
        if (configArray.size() >= 2) {
            int count = 32 + configArray.get(0).intValue();
            return count;
        }
        Slog.e(CarLog.TAG_DIAGNOSTIC, String.format("property 0x%x does not specify the number of vendor-specific properties.assuming 0.", Integer.valueOf(halPropId)));
        return 32;
    }

    private int getNumFloatSensors(int halPropId) {
        List<Integer> configArray = getPropConfigArray(halPropId);
        if (configArray.size() >= 2) {
            int count = 71 + configArray.get(1).intValue();
            return count;
        }
        Slog.e(CarLog.TAG_DIAGNOSTIC, String.format("property 0x%x does not specify the number of vendor-specific properties.assuming 0.", Integer.valueOf(halPropId)));
        return 71;
    }

    private CarDiagnosticEvent createCarDiagnosticEvent(VehiclePropValue value) {
        CarDiagnosticEvent.Builder newLiveFrameBuilder;
        if (value == null) {
            return null;
        }
        boolean isFreezeFrame = value.prop == 299896065;
        if (isFreezeFrame) {
            newLiveFrameBuilder = CarDiagnosticEvent.Builder.newFreezeFrameBuilder();
        } else {
            newLiveFrameBuilder = CarDiagnosticEvent.Builder.newLiveFrameBuilder();
        }
        CarDiagnosticEvent.Builder builder = newLiveFrameBuilder.atTimestamp(value.timestamp);
        BitSet bitset = BitSet.valueOf(CarServiceUtils.toByteArray(value.value.bytes));
        int numIntegerProperties = getNumIntegerSensors(value.prop);
        int numFloatProperties = getNumFloatSensors(value.prop);
        for (int i = 0; i < numIntegerProperties; i++) {
            if (bitset.get(i)) {
                builder.withIntValue(i, value.value.int32Values.get(i).intValue());
            }
        }
        for (int i2 = 0; i2 < numFloatProperties; i2++) {
            if (bitset.get(numIntegerProperties + i2)) {
                builder.withFloatValue(i2, value.value.floatValues.get(i2).floatValue());
            }
        }
        builder.withDtc(value.value.stringValue);
        return builder.build();
    }

    @Override // com.android.car.hal.HalServiceBase
    public void handleHalEvents(List<VehiclePropValue> values) {
        DiagnosticListener listener;
        this.mEventsToDispatch.clear();
        for (VehiclePropValue value : values) {
            CarDiagnosticEvent event = createCarDiagnosticEvent(value);
            if (event != null) {
                this.mEventsToDispatch.add(event);
            }
        }
        synchronized (this.mLock) {
            listener = this.mDiagnosticListener;
        }
        if (listener != null) {
            listener.onDiagnosticEvents(this.mEventsToDispatch);
        }
        this.mEventsToDispatch.clear();
    }

    public void setDiagnosticListener(DiagnosticListener listener) {
        synchronized (this.mLock) {
            this.mDiagnosticListener = listener;
        }
    }

    public DiagnosticListener getDiagnosticListener() {
        return this.mDiagnosticListener;
    }

    @Override // com.android.car.hal.HalServiceBase
    public void dump(PrintWriter writer) {
        writer.println("*Diagnostic HAL*");
    }

    protected float fixSamplingRateForProperty(VehiclePropConfig prop, int carSensorManagerRate) {
        if (prop.changeMode == 1) {
            return 0.0f;
        }
        float rate = 1.0f;
        if (carSensorManagerRate == 5) {
            rate = 5.0f;
        } else if (carSensorManagerRate == 10 || carSensorManagerRate == 100) {
            rate = 10.0f;
        }
        if (rate > prop.maxSampleRate) {
            rate = prop.maxSampleRate;
        }
        if (rate < prop.minSampleRate) {
            float rate2 = prop.minSampleRate;
            return rate2;
        }
        return rate;
    }

    public DiagnosticCapabilities getDiagnosticCapabilities() {
        return this.mDiagnosticCapabilities;
    }

    public CarDiagnosticEvent getCurrentLiveFrame() {
        try {
            VehiclePropValue value = this.mVehicleHal.get(VehicleProperty.OBD2_LIVE_FRAME);
            return createCarDiagnosticEvent(value);
        } catch (PropertyTimeoutException e) {
            Slog.e(CarLog.TAG_DIAGNOSTIC, "timeout trying to read OBD2_LIVE_FRAME");
            return null;
        } catch (IllegalArgumentException e2) {
            Slog.e(CarLog.TAG_DIAGNOSTIC, "illegal argument trying to read OBD2_LIVE_FRAME", e2);
            return null;
        }
    }

    public long[] getFreezeFrameTimestamps() {
        try {
            VehiclePropValue value = this.mVehicleHal.get(VehicleProperty.OBD2_FREEZE_FRAME_INFO);
            long[] timestamps = new long[value.value.int64Values.size()];
            for (int i = 0; i < timestamps.length; i++) {
                timestamps[i] = value.value.int64Values.get(i).longValue();
            }
            return timestamps;
        } catch (PropertyTimeoutException e) {
            Slog.e(CarLog.TAG_DIAGNOSTIC, "timeout trying to read OBD2_FREEZE_FRAME_INFO");
            return null;
        } catch (IllegalArgumentException e2) {
            Slog.e(CarLog.TAG_DIAGNOSTIC, "illegal argument trying to read OBD2_FREEZE_FRAME_INFO", e2);
            return null;
        }
    }

    public CarDiagnosticEvent getFreezeFrame(long timestamp) {
        VehiclePropValueBuilder builder = VehiclePropValueBuilder.newBuilder((int) VehicleProperty.OBD2_FREEZE_FRAME);
        builder.setInt64Value(timestamp);
        try {
            VehiclePropValue value = this.mVehicleHal.get(builder.build());
            return createCarDiagnosticEvent(value);
        } catch (PropertyTimeoutException e) {
            Slog.e(CarLog.TAG_DIAGNOSTIC, "timeout trying to read OBD2_FREEZE_FRAME");
            return null;
        } catch (IllegalArgumentException e2) {
            Slog.e(CarLog.TAG_DIAGNOSTIC, "illegal argument trying to read OBD2_FREEZE_FRAME", e2);
            return null;
        }
    }

    public void clearFreezeFrames(long... timestamps) {
        VehiclePropValueBuilder builder = VehiclePropValueBuilder.newBuilder((int) VehicleProperty.OBD2_FREEZE_FRAME_CLEAR);
        builder.setInt64Value(timestamps);
        try {
            this.mVehicleHal.set(builder.build());
        } catch (PropertyTimeoutException e) {
            Slog.e(CarLog.TAG_DIAGNOSTIC, "timeout trying to write OBD2_FREEZE_FRAME_CLEAR");
        } catch (IllegalArgumentException e2) {
            Slog.e(CarLog.TAG_DIAGNOSTIC, "illegal argument trying to write OBD2_FREEZE_FRAME_CLEAR", e2);
        }
    }
}
