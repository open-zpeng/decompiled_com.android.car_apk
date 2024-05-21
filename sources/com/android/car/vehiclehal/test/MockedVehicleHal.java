package com.android.car.vehiclehal.test;

import android.hardware.automotive.vehicle.V2_0.IVehicle;
import android.hardware.automotive.vehicle.V2_0.IVehicleCallback;
import android.hardware.automotive.vehicle.V2_0.SubscribeOptions;
import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.os.RemoteException;
import android.os.SystemClock;
import com.google.android.collect.Lists;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import junit.framework.Assert;
/* loaded from: classes3.dex */
public class MockedVehicleHal extends IVehicle.Stub {
    private final Map<Integer, VehicleHalPropertyHandler> mPropertyHandlerMap = new HashMap();
    private final Map<Integer, VehiclePropConfig> mConfigs = new HashMap();
    private final Map<Integer, List<IVehicleCallback>> mSubscribers = new HashMap();

    /* loaded from: classes3.dex */
    public interface VehicleHalPropertyHandler {
        public static final VehicleHalPropertyHandler NOP = new VehicleHalPropertyHandler() { // from class: com.android.car.vehiclehal.test.MockedVehicleHal.VehicleHalPropertyHandler.1
        };

        default void onPropertySet(VehiclePropValue value) {
        }

        default VehiclePropValue onPropertyGet(VehiclePropValue value) {
            return null;
        }

        default void onPropertySubscribe(int property, float sampleRate) {
        }

        default void onPropertyUnsubscribe(int property) {
        }
    }

    public synchronized void addProperties(VehiclePropConfig... configs) {
        for (VehiclePropConfig config : configs) {
            addProperty(config, new DefaultPropertyHandler(config, null));
        }
    }

    public synchronized void addProperty(VehiclePropConfig config, VehicleHalPropertyHandler handler) {
        this.mPropertyHandlerMap.put(Integer.valueOf(config.prop), handler);
        this.mConfigs.put(Integer.valueOf(config.prop), config);
    }

    public synchronized void addStaticProperty(VehiclePropConfig config, VehiclePropValue value) {
        addProperty(config, new StaticPropertyHandler(value));
    }

    public boolean waitForSubscriber(int propId, long timeoutMillis) {
        boolean z;
        long startTime = SystemClock.elapsedRealtime();
        try {
            synchronized (this) {
                while (this.mSubscribers.get(Integer.valueOf(propId)) == null) {
                    long waitMillis = (startTime - SystemClock.elapsedRealtime()) + timeoutMillis;
                    if (waitMillis < 0) {
                        break;
                    }
                    wait(waitMillis);
                }
                z = this.mSubscribers.get(Integer.valueOf(propId)) != null;
            }
            return z;
        } catch (InterruptedException e) {
            return false;
        }
    }

    public synchronized void injectEvent(VehiclePropValue value, boolean setProperty) {
        VehicleHalPropertyHandler handler;
        List<IVehicleCallback> callbacks = this.mSubscribers.get(Integer.valueOf(value.prop));
        Assert.assertNotNull("Injecting event failed for property: " + value.prop + ". No listeners found", callbacks);
        if (setProperty && (handler = this.mPropertyHandlerMap.get(Integer.valueOf(value.prop))) != null) {
            handler.onPropertySet(value);
        }
        for (IVehicleCallback callback : callbacks) {
            try {
                callback.onPropertyEvent(Lists.newArrayList(new VehiclePropValue[]{value}));
            } catch (RemoteException e) {
                e.printStackTrace();
                Assert.fail("Remote exception while injecting events.");
            }
        }
    }

    public synchronized void injectEvent(VehiclePropValue value) {
        injectEvent(value, false);
    }

    public synchronized void injectError(int errorCode, int propertyId, int areaId) {
        List<IVehicleCallback> callbacks = this.mSubscribers.get(Integer.valueOf(propertyId));
        Assert.assertNotNull("Injecting error failed for property: " + propertyId + ". No listeners found", callbacks);
        for (IVehicleCallback callback : callbacks) {
            try {
                callback.onPropertySetError(errorCode, propertyId, areaId);
            } catch (RemoteException e) {
                e.printStackTrace();
                Assert.fail("Remote exception while injecting errors.");
            }
        }
    }

    @Override // android.hardware.automotive.vehicle.V2_0.IVehicle
    public synchronized ArrayList<VehiclePropConfig> getAllPropConfigs() {
        return new ArrayList<>(this.mConfigs.values());
    }

    @Override // android.hardware.automotive.vehicle.V2_0.IVehicle
    public synchronized ArrayList<VehiclePropConfig> getRefreshAllPropConfigs() {
        return new ArrayList<>(this.mConfigs.values());
    }

    @Override // android.hardware.automotive.vehicle.V2_0.IVehicle
    public synchronized void getPropConfigs(ArrayList<Integer> props, IVehicle.getPropConfigsCallback cb) {
        ArrayList<VehiclePropConfig> res = new ArrayList<>();
        Iterator<Integer> it = props.iterator();
        while (it.hasNext()) {
            Integer prop = it.next();
            VehiclePropConfig config = this.mConfigs.get(prop);
            if (config == null) {
                cb.onValues(2, new ArrayList<>());
                return;
            }
            res.add(config);
        }
        cb.onValues(0, res);
    }

    @Override // android.hardware.automotive.vehicle.V2_0.IVehicle
    public synchronized void get(VehiclePropValue requestedPropValue, IVehicle.getCallback cb) {
        VehicleHalPropertyHandler handler = this.mPropertyHandlerMap.get(Integer.valueOf(requestedPropValue.prop));
        if (handler == null) {
            cb.onValues(2, null);
        } else {
            cb.onValues(0, handler.onPropertyGet(requestedPropValue));
        }
    }

    @Override // android.hardware.automotive.vehicle.V2_0.IVehicle
    public synchronized int set(VehiclePropValue propValue) {
        VehicleHalPropertyHandler handler = this.mPropertyHandlerMap.get(Integer.valueOf(propValue.prop));
        if (handler == null) {
            return 2;
        }
        handler.onPropertySet(propValue);
        return 0;
    }

    @Override // android.hardware.automotive.vehicle.V2_0.IVehicle
    public synchronized int setMulti(ArrayList<VehiclePropValue> propValues) {
        return 0;
    }

    @Override // android.hardware.automotive.vehicle.V2_0.IVehicle
    public synchronized int subscribe(IVehicleCallback callback, ArrayList<SubscribeOptions> options) {
        Iterator<SubscribeOptions> it = options.iterator();
        while (it.hasNext()) {
            SubscribeOptions opt = it.next();
            VehicleHalPropertyHandler handler = this.mPropertyHandlerMap.get(Integer.valueOf(opt.propId));
            if (handler == null) {
                return 2;
            }
            handler.onPropertySubscribe(opt.propId, opt.sampleRate);
            List<IVehicleCallback> subscribers = this.mSubscribers.get(Integer.valueOf(opt.propId));
            if (subscribers == null) {
                subscribers = new ArrayList();
                this.mSubscribers.put(Integer.valueOf(opt.propId), subscribers);
                notifyAll();
            } else {
                Iterator<IVehicleCallback> it2 = subscribers.iterator();
                while (true) {
                    if (it2.hasNext()) {
                        IVehicleCallback s = it2.next();
                        if (callback.asBinder() == s.asBinder()) {
                            subscribers.remove(callback);
                            break;
                        }
                    }
                }
            }
            subscribers.add(callback);
        }
        return 0;
    }

    @Override // android.hardware.automotive.vehicle.V2_0.IVehicle
    public synchronized int unsubscribe(IVehicleCallback callback, int propId) {
        VehicleHalPropertyHandler handler = this.mPropertyHandlerMap.get(Integer.valueOf(propId));
        if (handler == null) {
            return 2;
        }
        handler.onPropertyUnsubscribe(propId);
        List<IVehicleCallback> subscribers = this.mSubscribers.get(Integer.valueOf(propId));
        if (subscribers != null) {
            subscribers.remove(callback);
            if (subscribers.size() == 0) {
                this.mSubscribers.remove(Integer.valueOf(propId));
            }
        }
        return 0;
    }

    @Override // android.hardware.automotive.vehicle.V2_0.IVehicle
    public String debugDump() {
        return null;
    }

    /* loaded from: classes3.dex */
    public static class FailingPropertyHandler implements VehicleHalPropertyHandler {
        @Override // com.android.car.vehiclehal.test.MockedVehicleHal.VehicleHalPropertyHandler
        public void onPropertySet(VehiclePropValue value) {
            Assert.fail("Unexpected onPropertySet call");
        }

        @Override // com.android.car.vehiclehal.test.MockedVehicleHal.VehicleHalPropertyHandler
        public VehiclePropValue onPropertyGet(VehiclePropValue value) {
            Assert.fail("Unexpected onPropertyGet call");
            return null;
        }

        @Override // com.android.car.vehiclehal.test.MockedVehicleHal.VehicleHalPropertyHandler
        public void onPropertySubscribe(int property, float sampleRate) {
            Assert.fail("Unexpected onPropertySubscribe call");
        }

        @Override // com.android.car.vehiclehal.test.MockedVehicleHal.VehicleHalPropertyHandler
        public void onPropertyUnsubscribe(int property) {
            Assert.fail("Unexpected onPropertyUnsubscribe call");
        }
    }

    /* loaded from: classes3.dex */
    public static class StaticPropertyHandler extends FailingPropertyHandler {
        private final VehiclePropValue mValue;

        public StaticPropertyHandler(VehiclePropValue value) {
            this.mValue = value;
        }

        @Override // com.android.car.vehiclehal.test.MockedVehicleHal.FailingPropertyHandler, com.android.car.vehiclehal.test.MockedVehicleHal.VehicleHalPropertyHandler
        public synchronized VehiclePropValue onPropertyGet(VehiclePropValue value) {
            return this.mValue;
        }
    }

    /* loaded from: classes3.dex */
    public static class DefaultPropertyHandler implements VehicleHalPropertyHandler {
        private final VehiclePropConfig mConfig;
        private boolean mSubscribed = false;
        private VehiclePropValue mValue;

        public DefaultPropertyHandler(VehiclePropConfig config, VehiclePropValue initialValue) {
            this.mConfig = config;
            this.mValue = initialValue;
        }

        @Override // com.android.car.vehiclehal.test.MockedVehicleHal.VehicleHalPropertyHandler
        public synchronized void onPropertySet(VehiclePropValue value) {
            Assert.assertEquals(this.mConfig.prop, value.prop);
            Assert.assertEquals(2, this.mConfig.access & 2);
            this.mValue = value;
        }

        @Override // com.android.car.vehiclehal.test.MockedVehicleHal.VehicleHalPropertyHandler
        public synchronized VehiclePropValue onPropertyGet(VehiclePropValue value) {
            Assert.assertEquals(this.mConfig.prop, value.prop);
            Assert.assertEquals(1, this.mConfig.access & 1);
            return this.mValue;
        }

        @Override // com.android.car.vehiclehal.test.MockedVehicleHal.VehicleHalPropertyHandler
        public synchronized void onPropertySubscribe(int property, float sampleRate) {
            Assert.assertEquals(this.mConfig.prop, property);
            this.mSubscribed = true;
        }

        @Override // com.android.car.vehiclehal.test.MockedVehicleHal.VehicleHalPropertyHandler
        public synchronized void onPropertyUnsubscribe(int property) {
            Assert.assertEquals(this.mConfig.prop, property);
            if (!this.mSubscribed) {
                throw new IllegalArgumentException("Property was not subscribed 0x" + Integer.toHexString(property));
            }
            this.mSubscribed = false;
        }
    }
}
