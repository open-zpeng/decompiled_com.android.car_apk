package com.android.car.hal;

import android.car.ValueUnavailableException;
import android.car.XpDebugLog;
import android.content.Context;
import android.hardware.automotive.vehicle.V2_0.IVehicle;
import android.hardware.automotive.vehicle.V2_0.IVehicleCallback;
import android.hardware.automotive.vehicle.V2_0.SubscribeOptions;
import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Pair;
import android.util.Slog;
import com.android.car.CallbackStatistics;
import com.android.car.CarLog;
import com.android.car.ProcessUtils;
import com.android.car.hal.HalClient;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.LinkedTransferQueue;
import java.util.function.Consumer;
/* JADX INFO: Access modifiers changed from: package-private */
/* loaded from: classes3.dex */
public class HalClient {
    private static final int COMMON_MULTI_PROPERTY = -2;
    private static final int HAL_DEAD = -1;
    private static final long MAX_HANDLER_DELIVER_MESSAGE_INTERVAL_MS;
    private static final long MAX_HANDLER_DISPATCH_MESSAGE_INTERVAL_MS;
    private static final long MAX_RECEIVE_HAL_PROPS_FROM_HANDLER_INTERVAL_MS;
    private static final long MAX_RECEIVE_HAL_PROPS_INTERVAL_MS;
    private static final long MAX_SEND_MESSAGE_INTERVAL_MS;
    private static final int SLEEP_BETWEEN_RETRIABLE_INVOKES_MS = 50;
    private static final boolean USE_QUEUE = true;
    private static final int WAIT_CAP_FOR_RETRIABLE_RESULT_MS = 2000;
    private final IVehicleCallback mCallback;
    private volatile Context mContext;
    private final IVehicle mVehicle;
    private final LinkedTransferQueue<HalCallBackValue> mQueue = new LinkedTransferQueue<>();
    private final Thread mHandlerThread = new Thread(new MessageLoop(), "VhalCallbackThread");
    private final IVehicleCallback mInternalCallback = new XpVehicleCallback();
    private final CallbackStatistics mSetPropStatistics = new CallbackStatistics("SetProp", true);
    private final CallbackStatistics mGetPropStatistics = new CallbackStatistics("GetProp", true);

    /* loaded from: classes3.dex */
    private interface HalCallBackValue {
        void run();
    }

    /* loaded from: classes3.dex */
    interface RetriableCallback {
        int action();
    }

    static {
        MAX_RECEIVE_HAL_PROPS_INTERVAL_MS = (Build.IS_USER ? Duration.ofMillis(750L) : Duration.ofMillis(350L)).toMillis();
        MAX_SEND_MESSAGE_INTERVAL_MS = Duration.ofMillis(100L).toMillis();
        MAX_HANDLER_DELIVER_MESSAGE_INTERVAL_MS = (Build.IS_USER ? Duration.ofMillis(750L) : Duration.ofMillis(350L)).toMillis();
        MAX_RECEIVE_HAL_PROPS_FROM_HANDLER_INTERVAL_MS = Duration.ofMillis(Build.IS_USER ? 1500L : 700L).toMillis();
        MAX_HANDLER_DISPATCH_MESSAGE_INTERVAL_MS = Duration.ofMillis(Build.IS_USER ? 400L : 200L).toMillis();
    }

    public void setContext(Context context) {
        this.mContext = context;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public HalClient(IVehicle vehicle, Looper looper, IVehicleCallback callback) {
        this.mVehicle = vehicle;
        this.mCallback = callback;
    }

    public void start() {
        this.mHandlerThread.start();
    }

    public void stop() {
        this.mHandlerThread.interrupt();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public ArrayList<VehiclePropConfig> getAllPropConfigs() throws RemoteException {
        return this.mVehicle.getAllPropConfigs();
    }

    public void subscribe(SubscribeOptions... options) throws RemoteException {
        if (CarLog.isCallbackLogEnable(0)) {
            Slog.d(CarLog.TAG_HAL, "subscribe Pid=" + Binder.getCallingPid() + " SubscribeOptions:" + Arrays.toString(options));
        }
        this.mVehicle.subscribe(this.mInternalCallback, new ArrayList<>(Arrays.asList(options)));
    }

    public void unsubscribe(int prop) throws RemoteException {
        if (CarLog.isCallbackLogEnable(0)) {
            Slog.i(CarLog.TAG_HAL, "unsubscribe Pid=" + Binder.getCallingPid() + " for " + XpDebugLog.getPropertyDescription(prop));
        }
        this.mVehicle.unsubscribe(this.mInternalCallback, prop);
    }

    public void setValue(VehiclePropValue propValue) throws PropertyTimeoutException {
        int status;
        int property = propValue.prop;
        if (CarLog.isSetLogEnable()) {
            Slog.i(CarLog.TAG_HAL, "Pid=" + Binder.getCallingPid() + " setValue for " + XpDebugLog.getPropertyDescription(property) + " value=" + propValue.value.toString());
        }
        if (property == 356516106) {
            Slog.w(CarLog.TAG_HAL, "this interface may not Implemented.", new Throwable());
            return;
        }
        if (!Build.IS_USER && this.mContext != null) {
            int callingPid = Binder.getCallingPid();
            int callingUid = Binder.getCallingUid();
            this.mSetPropStatistics.addPropMethodCallCount(property, ProcessUtils.getProcessName(this.mContext, callingPid, callingUid), callingPid, null);
        }
        try {
            status = this.mVehicle.set(propValue);
        } catch (RemoteException e) {
            Slog.e(CarLog.TAG_HAL, "Pid = " + Binder.getCallingPid() + " Failed to set " + XpDebugLog.getPropertyDescription(property) + " value = " + propValue.value.toString() + " " + e.getMessage());
            status = -1;
        }
        if (2 == status) {
            throw new IllegalArgumentException(String.format("Pid:%d Failed to set value for: 0x%x, areaId: 0x%x", Integer.valueOf(Binder.getCallingPid()), Integer.valueOf(property), Integer.valueOf(propValue.areaId)));
        }
        if (-1 == status) {
            throw new PropertyTimeoutException(property);
        }
        if (status != 0) {
            throw new IllegalStateException(String.format("Pid:%d Failed to set property: 0x%x, areaId: 0x%x, code: %d", Integer.valueOf(Binder.getCallingPid()), Integer.valueOf(property), Integer.valueOf(propValue.areaId), Integer.valueOf(status)));
        }
    }

    public void setMultiValues(ArrayList<VehiclePropValue> propValues) throws PropertyTimeoutException {
        int status;
        if (CarLog.isSetLogEnable()) {
            Slog.i(CarLog.TAG_HAL, "Pid=" + Binder.getCallingPid() + " call setMultiValues");
            Iterator<VehiclePropValue> it = propValues.iterator();
            while (it.hasNext()) {
                VehiclePropValue halProp = it.next();
                Slog.i(CarLog.TAG_HAL, " set PropValue:" + halProp);
            }
        }
        if (!Build.IS_USER && this.mContext != null) {
            final int callingPid = Binder.getCallingPid();
            final int callingUid = Binder.getCallingUid();
            if (propValues != null && propValues.size() > 0) {
                propValues.forEach(new Consumer() { // from class: com.android.car.hal.-$$Lambda$HalClient$_AeC6TbWqeLtrACU7mKyzuWEAbI
                    @Override // java.util.function.Consumer
                    public final void accept(Object obj) {
                        HalClient.this.lambda$setMultiValues$0$HalClient(callingPid, callingUid, (VehiclePropValue) obj);
                    }
                });
            }
        }
        try {
            status = this.mVehicle.setMulti(propValues);
        } catch (RemoteException e) {
            Slog.e(CarLog.TAG_HAL, "Pid=" + Binder.getCallingPid() + " Failed to set multiple values: " + e.getMessage());
            status = -1;
        }
        if (2 == status) {
            throw new IllegalArgumentException(String.format("Pid:%d Failed to set multiple values", Integer.valueOf(Binder.getCallingPid())));
        }
        if (-1 == status) {
            throw new PropertyTimeoutException(-2);
        }
        if (status != 0) {
            throw new IllegalStateException(String.format("Pid:%d Failed to set multi values, code: %d", Integer.valueOf(Binder.getCallingPid()), Integer.valueOf(status)));
        }
    }

    public /* synthetic */ void lambda$setMultiValues$0$HalClient(int callingPid, int callingUid, VehiclePropValue v) {
        this.mSetPropStatistics.addPropMethodCallCount(v.prop, ProcessUtils.getProcessName(this.mContext, callingPid, callingUid), callingPid, null);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public VehiclePropValue getValue(VehiclePropValue requestedPropValue) throws PropertyTimeoutException {
        return getValue(requestedPropValue, true);
    }

    public void dump(PrintWriter writer) {
        writer.println("**dump process call the prop set method count**");
        this.mSetPropStatistics.dump(writer);
        writer.println("**dump process call the prop get method count**");
        this.mGetPropStatistics.dump(writer);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* JADX WARN: Type inference failed for: r9v0, types: [T, android.hardware.automotive.vehicle.V2_0.VehiclePropValue] */
    public VehiclePropValue getValue(VehiclePropValue requestedPropValue, boolean showNoDataLog) throws PropertyTimeoutException {
        int property = requestedPropValue.prop;
        boolean getLogEnable = CarLog.isGetLogEnable();
        int callingPid = Binder.getCallingPid();
        if (getLogEnable) {
            Slog.i(CarLog.TAG_HAL, "Pid=" + callingPid + " try to getValue of " + XpDebugLog.getPropertyDescription(property));
        }
        if (property == 356516106) {
            Slog.w(CarLog.TAG_HAL, "this interface may not Implemented.", new Throwable());
            return requestedPropValue;
        }
        if (!Build.IS_USER && this.mContext != null && showNoDataLog) {
            int callingUid = Binder.getCallingUid();
            this.mGetPropStatistics.addPropMethodCallCount(property, ProcessUtils.getProcessName(this.mContext, callingPid, callingUid), callingPid, null);
        }
        ObjectWrapper<VehiclePropValue> valueWrapper = new ObjectWrapper<>();
        ValueResult res = internalGet(requestedPropValue);
        valueWrapper.object = res.propValue;
        int status = res.status;
        int areaId = requestedPropValue.areaId;
        if (2 == status) {
            throw new IllegalArgumentException(String.format("Pid:%d Failed to get value for: 0x%x, areaId: 0x%x", Integer.valueOf(callingPid), Integer.valueOf(property), Integer.valueOf(areaId)));
        }
        if (-1 == status) {
            throw new PropertyTimeoutException(property);
        }
        if (1 == status) {
            throw new ValueUnavailableException(0);
        }
        if (6 == status) {
            ValueUnavailableException e = new ValueUnavailableException(1);
            Slog.w(CarLog.TAG_HAL, "Pid=" + callingPid + " " + e.getMessage() + " for " + XpDebugLog.getPropertyDescription(property));
            throw e;
        }
        VehiclePropValue propValue = (VehiclePropValue) valueWrapper.object;
        if (status != 0 || propValue == null) {
            throw new IllegalStateException(String.format("Pid: %d Failed to get property: 0x%x, areaId: 0x%x, code: %d", Integer.valueOf(callingPid), Integer.valueOf(property), Integer.valueOf(areaId), Integer.valueOf(status)));
        }
        if (getLogEnable) {
            Slog.i(CarLog.TAG_HAL, "Pid=" + callingPid + " get value=" + propValue + " for " + XpDebugLog.getPropertyDescription(property));
        }
        return propValue;
    }

    private ValueResult internalGet(VehiclePropValue requestedPropValue) {
        final ValueResult result = new ValueResult();
        try {
            this.mVehicle.get(requestedPropValue, new IVehicle.getCallback() { // from class: com.android.car.hal.-$$Lambda$HalClient$lgKjYXTxFf-Kf2lEPGMj7lCe6AA
                @Override // android.hardware.automotive.vehicle.V2_0.IVehicle.getCallback
                public final void onValues(int i, VehiclePropValue vehiclePropValue) {
                    HalClient.lambda$internalGet$1(HalClient.ValueResult.this, i, vehiclePropValue);
                }
            });
        } catch (RemoteException e) {
            Slog.e(CarLog.TAG_HAL, "Failed to get value from vehicle HAL: " + e.getMessage());
            result.status = -1;
        }
        return result;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ void lambda$internalGet$1(ValueResult result, int status, VehiclePropValue propValue) {
        result.status = status;
        result.propValue = propValue;
    }

    private static int invokeRetriable(RetriableCallback callback, long timeoutMs, long sleepMs) {
        int status = callback.action();
        long startTime = SystemClock.elapsedRealtime();
        while (-1 == status && SystemClock.elapsedRealtime() - startTime < timeoutMs) {
            try {
                Thread.sleep(sleepMs);
                status = callback.action();
            } catch (InterruptedException e) {
                Slog.e(CarLog.TAG_HAL, "Thread was interrupted while waiting for vehicle HAL.", e);
            }
        }
        return status;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static class ObjectWrapper<T> {
        T object;

        private ObjectWrapper() {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static class ValueResult {
        VehiclePropValue propValue;
        int status;

        private ValueResult() {
        }
    }

    /* loaded from: classes3.dex */
    private static class PropertySetError {
        final int areaId;
        final int errorCode;
        final int propId;

        PropertySetError(int errorCode, int propId, int areaId) {
            this.errorCode = errorCode;
            this.propId = propId;
            this.areaId = areaId;
        }
    }

    /* loaded from: classes3.dex */
    private static class CallbackHandler extends Handler {
        private static final int MSG_ON_PROPERTY_EVENT = 2;
        private static final int MSG_ON_PROPERTY_SET = 1;
        private static final int MSG_ON_SET_ERROR = 3;
        private final IVehicleCallback mCallback;

        CallbackHandler(Looper looper, IVehicleCallback callback) {
            super(looper);
            this.mCallback = callback;
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            try {
                int i = msg.what;
                if (i == 1) {
                    this.mCallback.onPropertySet((VehiclePropValue) msg.obj);
                } else if (i != 2) {
                    if (i == 3) {
                        PropertySetError obj = (PropertySetError) msg.obj;
                        this.mCallback.onPropertySetError(obj.errorCode, obj.propId, obj.areaId);
                        return;
                    }
                    Slog.e(CarLog.TAG_HAL, "Unexpected message: " + msg.what);
                } else {
                    Pair<Long, ArrayList<VehiclePropValue>> pair = (Pair) msg.obj;
                    long timeCost = SystemClock.uptimeMillis() - ((Long) pair.first).longValue();
                    ArrayList<VehiclePropValue> vehiclePropValueList = (ArrayList) pair.second;
                    if (timeCost > HalClient.MAX_HANDLER_DELIVER_MESSAGE_INTERVAL_MS && vehiclePropValueList != null && vehiclePropValueList.size() > 0) {
                        int propertyId = vehiclePropValueList.get(0).prop;
                        long sendTimeStamp = vehiclePropValueList.get(0).timestamp;
                        Slog.w(CarLog.TAG_HAL, "deliver: " + XpDebugLog.getPropertyDescription(propertyId) + ", timestamp:" + sendTimeStamp + " callback cost too much time:" + timeCost + " ms");
                    }
                    this.mCallback.onPropertyEvent(vehiclePropValueList);
                }
            } catch (RemoteException e) {
                Slog.e(CarLog.TAG_HAL, "Message failed: " + msg.what);
            }
        }
    }

    /* loaded from: classes3.dex */
    private static class VehicleCallback extends IVehicleCallback.Stub {
        private final Handler mHandler;

        VehicleCallback(Handler handler) {
            this.mHandler = handler;
        }

        @Override // android.hardware.automotive.vehicle.V2_0.IVehicleCallback
        public void onPropertyEvent(ArrayList<VehiclePropValue> propValues) {
            if (propValues != null && propValues.size() > 0) {
                long currentTimestamp = SystemClock.uptimeMillis();
                VehiclePropValue value = propValues.get(0);
                long sendTimestamp = value.timestamp;
                long timeCost = currentTimestamp - (sendTimestamp / 1000000);
                if (timeCost > HalClient.MAX_RECEIVE_HAL_PROPS_INTERVAL_MS) {
                    Slog.w(CarLog.TAG_HAL, "receive: " + XpDebugLog.getPropertyDescription(value.prop) + ", timestamp:" + sendTimestamp + " callback cost too much time:" + timeCost + " ms");
                }
                Handler handler = this.mHandler;
                handler.sendMessage(Message.obtain(handler, 2, new Pair(Long.valueOf(currentTimestamp), propValues)));
                long timeCost2 = SystemClock.uptimeMillis() - currentTimestamp;
                if (timeCost2 > HalClient.MAX_SEND_MESSAGE_INTERVAL_MS) {
                    Slog.w(CarLog.TAG_HAL, "send message: " + XpDebugLog.getPropertyDescription(value.prop) + ", timestamp:" + sendTimestamp + " cost too much time:" + timeCost2 + " ms");
                }
            }
        }

        @Override // android.hardware.automotive.vehicle.V2_0.IVehicleCallback
        public void onPropertySet(VehiclePropValue propValue) {
            Handler handler = this.mHandler;
            handler.sendMessage(Message.obtain(handler, 1, propValue));
        }

        @Override // android.hardware.automotive.vehicle.V2_0.IVehicleCallback
        public void onPropertySetError(int errorCode, int propId, int areaId) {
            Handler handler = this.mHandler;
            handler.sendMessage(Message.obtain(handler, 3, new PropertySetError(errorCode, propId, areaId)));
        }
    }

    /* loaded from: classes3.dex */
    private class OnPropertyEventValue implements HalCallBackValue {
        private final long timestamp;
        private final ArrayList<VehiclePropValue> vehiclePropValueList;

        public OnPropertyEventValue(long timestamp, ArrayList<VehiclePropValue> vehiclePropValueList) {
            this.timestamp = timestamp;
            this.vehiclePropValueList = vehiclePropValueList;
        }

        @Override // com.android.car.hal.HalClient.HalCallBackValue
        public void run() {
            ArrayList<VehiclePropValue> arrayList = this.vehiclePropValueList;
            if (arrayList == null || arrayList.isEmpty()) {
                return;
            }
            VehiclePropValue firstValue = this.vehiclePropValueList.get(0);
            int firstPropertyId = firstValue.prop;
            long firstSendTimestamp = firstValue.timestamp;
            int arraySize = this.vehiclePropValueList.size();
            long receiveTimestamp = SystemClock.uptimeMillis();
            long timeCost = receiveTimestamp - (firstSendTimestamp / 1000000);
            if (timeCost > HalClient.MAX_RECEIVE_HAL_PROPS_FROM_HANDLER_INTERVAL_MS) {
                String propertyDescription = XpDebugLog.getPropertyDescription(firstPropertyId);
                Slog.w(CarLog.TAG_HAL, "receive props[" + arraySize + "]: " + propertyDescription + ", timestamp:" + firstSendTimestamp + " from hal and handler cost too much time:" + timeCost + " ms");
                long deliverTimeCost = receiveTimestamp - this.timestamp;
                if (deliverTimeCost > HalClient.MAX_HANDLER_DELIVER_MESSAGE_INTERVAL_MS) {
                    Slog.w(CarLog.TAG_HAL, "deliver props[" + arraySize + "]: " + propertyDescription + ", timestamp:" + firstSendTimestamp + " callback cost too much time:" + deliverTimeCost + " ms");
                }
            }
            try {
                HalClient.this.mCallback.onPropertyEvent(this.vehiclePropValueList);
            } catch (Exception e) {
                Slog.e(CarLog.TAG_HAL, "call onPropertyEvent " + this.vehiclePropValueList + " failed", e);
            }
            long timeCost2 = SystemClock.uptimeMillis() - receiveTimestamp;
            if (timeCost2 > HalClient.MAX_HANDLER_DISPATCH_MESSAGE_INTERVAL_MS) {
                Slog.w(CarLog.TAG_HAL, "dispatch props[" + arraySize + "]: " + XpDebugLog.getPropertyDescription(firstPropertyId) + ", timestamp:" + firstSendTimestamp + " cost too much time:" + timeCost2 + " ms");
            }
        }
    }

    /* loaded from: classes3.dex */
    private class OnPropertySetValue implements HalCallBackValue {
        private final VehiclePropValue vehiclePropValue;

        public OnPropertySetValue(VehiclePropValue vehiclePropValue) {
            this.vehiclePropValue = vehiclePropValue;
        }

        @Override // com.android.car.hal.HalClient.HalCallBackValue
        public void run() {
            try {
                HalClient.this.mCallback.onPropertySet(this.vehiclePropValue);
            } catch (Exception e) {
                Slog.e(CarLog.TAG_HAL, "call onPropertySet failed: " + e.getMessage());
            }
        }
    }

    /* loaded from: classes3.dex */
    private class OnPropertySetErrorValue implements HalCallBackValue {
        private final PropertySetError propertySetErrorValue;

        public OnPropertySetErrorValue(PropertySetError propertySetErrorValue) {
            this.propertySetErrorValue = propertySetErrorValue;
        }

        @Override // com.android.car.hal.HalClient.HalCallBackValue
        public void run() {
            try {
                HalClient.this.mCallback.onPropertySetError(this.propertySetErrorValue.errorCode, this.propertySetErrorValue.propId, this.propertySetErrorValue.areaId);
            } catch (Exception e) {
                Slog.e(CarLog.TAG_HAL, "call onPropertySet failed: " + e.getMessage());
            }
        }
    }

    /* loaded from: classes3.dex */
    private class XpVehicleCallback extends IVehicleCallback.Stub {
        private XpVehicleCallback() {
        }

        @Override // android.hardware.automotive.vehicle.V2_0.IVehicleCallback
        public void onPropertyEvent(ArrayList<VehiclePropValue> propValues) {
            try {
                HalClient.this.mQueue.put(new OnPropertyEventValue(SystemClock.uptimeMillis(), propValues));
            } catch (Exception e) {
                Slog.e(CarLog.TAG_HAL, "send onPropertyEvent " + propValues + " failed: " + e.getMessage());
            }
        }

        @Override // android.hardware.automotive.vehicle.V2_0.IVehicleCallback
        public void onPropertySet(VehiclePropValue propValue) {
            try {
                HalClient.this.mQueue.put(new OnPropertySetValue(propValue));
            } catch (Exception e) {
                Slog.e(CarLog.TAG_HAL, "send onPropertySet" + propValue + " failed: " + e.getMessage());
            }
        }

        @Override // android.hardware.automotive.vehicle.V2_0.IVehicleCallback
        public void onPropertySetError(int errorCode, int propId, int areaId) {
            try {
                HalClient.this.mQueue.put(new OnPropertySetErrorValue(new PropertySetError(errorCode, propId, areaId)));
            } catch (Exception e) {
                Slog.e(CarLog.TAG_HAL, "send onPropertySetError propId = " + propId + " errorCode=" + errorCode + " failed: " + e.getMessage());
            }
        }
    }

    /* loaded from: classes3.dex */
    private final class MessageLoop implements Runnable {
        private MessageLoop() {
        }

        @Override // java.lang.Runnable
        public void run() {
            Process.setThreadPriority(-8);
            while (true) {
                try {
                    HalCallBackValue item = (HalCallBackValue) HalClient.this.mQueue.take();
                    item.run();
                } catch (InterruptedException e) {
                    Slog.w(CarLog.TAG_HAL, "MessageLoop : Shutting down (interrupted)");
                    return;
                }
            }
        }
    }
}
