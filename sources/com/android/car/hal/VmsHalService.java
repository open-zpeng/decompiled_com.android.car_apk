package com.android.car.hal;

import android.car.vms.IVmsPublisherClient;
import android.car.vms.IVmsPublisherService;
import android.car.vms.IVmsSubscriberClient;
import android.car.vms.IVmsSubscriberService;
import android.car.vms.VmsAssociatedLayer;
import android.car.vms.VmsAvailableLayers;
import android.car.vms.VmsLayer;
import android.car.vms.VmsLayerDependency;
import android.car.vms.VmsLayersOffering;
import android.car.vms.VmsOperationRecorder;
import android.car.vms.VmsSubscriptionState;
import android.content.Context;
import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VmsMessageType;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.ArraySet;
import android.util.Slog;
import androidx.annotation.VisibleForTesting;
import com.android.car.CarServiceUtils;
import com.android.car.R;
import com.android.car.vms.VmsClientManager;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
/* loaded from: classes3.dex */
public class VmsHalService extends HalServiceBase {
    private static final boolean DBG = false;
    private static final int HAL_PROPERTY_ID = 299895808;
    private static final int NUM_INTEGERS_IN_VMS_LAYER = 3;
    private static final String TAG = "VmsHalService";
    private static final int UNKNOWN_CLIENT_ID = -1;
    private int mAvailableLayersSequence;
    private VmsClientManager mClientManager;
    private final int mClientMetricsProperty;
    private final int mCoreId;
    private volatile boolean mIsSupported;
    private final MessageQueue mMessageQueue;
    private final boolean mPropagatePropertyException;
    private final IVmsPublisherClient.Stub mPublisherClient;
    private IVmsPublisherService mPublisherService;
    private IBinder mPublisherToken;
    private final IVmsSubscriberClient.Stub mSubscriberClient;
    private IVmsSubscriberService mSubscriberService;
    private int mSubscriptionStateSequence;
    private final VehicleHal mVehicleHal;

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class MessageQueue implements Handler.Callback {
        private Handler mHandler;
        private HandlerThread mHandlerThread;
        private final Set<Integer> mSupportedMessageTypes;

        private MessageQueue() {
            this.mSupportedMessageTypes = new ArraySet(Arrays.asList(12, 17, 9, 11));
        }

        synchronized void init() {
            this.mHandlerThread = new HandlerThread(VmsHalService.TAG);
            this.mHandlerThread.start();
            this.mHandler = new Handler(this.mHandlerThread.getLooper(), this);
        }

        synchronized void release() {
            if (this.mHandlerThread != null) {
                this.mHandlerThread.quitSafely();
            }
        }

        synchronized void enqueue(int messageType, Object message) {
            if (this.mSupportedMessageTypes.contains(Integer.valueOf(messageType))) {
                Message.obtain(this.mHandler, messageType, message).sendToTarget();
            } else {
                Slog.e(VmsHalService.TAG, "Unexpected message type: " + VmsMessageType.toString(messageType));
            }
        }

        synchronized void clear() {
            Set<Integer> set = this.mSupportedMessageTypes;
            final Handler handler = this.mHandler;
            Objects.requireNonNull(handler);
            set.forEach(new Consumer() { // from class: com.android.car.hal.-$$Lambda$7iOl2rDfZg5T5bjGIVqHE4Bn4x8
                @Override // java.util.function.Consumer
                public final void accept(Object obj) {
                    handler.removeMessages(((Integer) obj).intValue());
                }
            });
        }

        @Override // android.os.Handler.Callback
        public boolean handleMessage(Message msg) {
            int i = msg.what;
            VehiclePropValue vehicleProp = (VehiclePropValue) msg.obj;
            VmsHalService.this.setPropertyValue(vehicleProp);
            return true;
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public VmsHalService(Context context, VehicleHal vehicleHal) {
        this(context, vehicleHal, new Supplier() { // from class: com.android.car.hal.-$$Lambda$-xfp9icEnUYvJbxkgTT4aGLVw-8
            @Override // java.util.function.Supplier
            public final Object get() {
                return Long.valueOf(SystemClock.uptimeMillis());
            }
        }, Build.IS_ENG || Build.IS_USERDEBUG);
    }

    @VisibleForTesting
    VmsHalService(Context context, VehicleHal vehicleHal, Supplier<Long> getCoreId, boolean propagatePropertyException) {
        this.mIsSupported = false;
        this.mSubscriptionStateSequence = -1;
        this.mAvailableLayersSequence = -1;
        this.mPublisherClient = new IVmsPublisherClient.Stub() { // from class: com.android.car.hal.VmsHalService.1
            public void setVmsPublisherService(IBinder token, IVmsPublisherService service) {
                VmsHalService.this.mPublisherToken = token;
                VmsHalService.this.mPublisherService = service;
            }

            public void onVmsSubscriptionChange(VmsSubscriptionState subscriptionState) {
                if (subscriptionState.getSequenceNumber() > VmsHalService.this.mSubscriptionStateSequence) {
                    VmsHalService.this.mSubscriptionStateSequence = subscriptionState.getSequenceNumber();
                    VmsHalService.this.mMessageQueue.enqueue(11, VmsHalService.createSubscriptionStateMessage(11, subscriptionState));
                    return;
                }
                Slog.w(VmsHalService.TAG, String.format("Out of order subscription state received: %d (expecting %d)", Integer.valueOf(subscriptionState.getSequenceNumber()), Integer.valueOf(VmsHalService.this.mSubscriptionStateSequence + 1)));
            }
        };
        this.mSubscriberClient = new IVmsSubscriberClient.Stub() { // from class: com.android.car.hal.VmsHalService.2
            public void onVmsMessageReceived(VmsLayer layer, byte[] payload) {
                VmsHalService.this.mMessageQueue.enqueue(12, VmsHalService.createDataMessage(layer, payload));
            }

            public void onLayersAvailabilityChanged(VmsAvailableLayers availableLayers) {
                if (availableLayers.getSequence() > VmsHalService.this.mAvailableLayersSequence) {
                    VmsHalService.this.mAvailableLayersSequence = availableLayers.getSequence();
                    VmsHalService.this.mMessageQueue.enqueue(9, VmsHalService.createAvailableLayersMessage(9, availableLayers));
                    return;
                }
                Slog.w(VmsHalService.TAG, String.format("Out of order layer availability received: %d (expecting %d)", Integer.valueOf(availableLayers.getSequence()), Integer.valueOf(VmsHalService.this.mAvailableLayersSequence + 1)));
            }
        };
        this.mVehicleHal = vehicleHal;
        this.mCoreId = (int) (getCoreId.get().longValue() % 2147483647L);
        this.mMessageQueue = new MessageQueue();
        this.mClientMetricsProperty = getClientMetricsProperty(context);
        this.mPropagatePropertyException = propagatePropertyException;
    }

    private static int getClientMetricsProperty(Context context) {
        int propId = context.getResources().getInteger(R.integer.vmsHalClientMetricsProperty);
        if (propId == 0) {
            Slog.i(TAG, "Metrics collection disabled");
            return 0;
        } else if (((-268435456) & propId) != 536870912) {
            Slog.w(TAG, String.format("Metrics collection disabled, non-vendor property: 0x%x", Integer.valueOf(propId)));
            return 0;
        } else {
            Slog.i(TAG, String.format("Metrics collection property: 0x%x", Integer.valueOf(propId)));
            return propId;
        }
    }

    @VisibleForTesting
    Handler getHandler() {
        return this.mMessageQueue.mHandler;
    }

    public void setClientManager(VmsClientManager clientManager) {
        this.mClientManager = clientManager;
    }

    public void setVmsSubscriberService(IVmsSubscriberService service) {
        this.mSubscriberService = service;
    }

    @Override // com.android.car.hal.HalServiceBase
    public Collection<VehiclePropConfig> takeSupportedProperties(Collection<VehiclePropConfig> allProperties) {
        for (VehiclePropConfig p : allProperties) {
            if (p.prop == 299895808) {
                this.mIsSupported = true;
                return Collections.singleton(p);
            }
        }
        return Collections.emptySet();
    }

    @Override // com.android.car.hal.HalServiceBase
    public void init() {
        if (this.mIsSupported) {
            Slog.i(TAG, "Initializing VmsHalService VHAL property");
            this.mVehicleHal.subscribeProperty(this, 299895808);
            this.mMessageQueue.init();
            this.mMessageQueue.enqueue(17, createStartSessionMessage(this.mCoreId, -1));
            return;
        }
        Slog.i(TAG, "VmsHalService VHAL property not supported");
    }

    @Override // com.android.car.hal.HalServiceBase
    public void release() {
        this.mMessageQueue.release();
        this.mSubscriptionStateSequence = -1;
        this.mAvailableLayersSequence = -1;
        if (this.mIsSupported) {
            this.mVehicleHal.unsubscribeProperty(this, 299895808);
            IVmsSubscriberService iVmsSubscriberService = this.mSubscriberService;
            if (iVmsSubscriberService != null) {
                try {
                    iVmsSubscriberService.removeVmsSubscriberToNotifications(this.mSubscriberClient);
                } catch (RemoteException e) {
                    Slog.e(TAG, "While removing subscriber callback", e);
                }
            }
        }
    }

    @Override // com.android.car.hal.HalServiceBase
    public void dump(PrintWriter writer) {
        writer.println("*VMS HAL*");
        StringBuilder sb = new StringBuilder();
        sb.append("VmsProperty: ");
        sb.append(this.mIsSupported ? "supported" : "unsupported");
        writer.println(sb.toString());
        StringBuilder sb2 = new StringBuilder();
        sb2.append("VmsPublisherService: ");
        sb2.append(this.mPublisherService != null ? "registered " : "unregistered");
        writer.println(sb2.toString());
        writer.println("mSubscriptionStateSequence: " + this.mSubscriptionStateSequence);
        StringBuilder sb3 = new StringBuilder();
        sb3.append("VmsSubscriberService: ");
        sb3.append(this.mSubscriberService != null ? "registered" : "unregistered");
        writer.println(sb3.toString());
        writer.println("mAvailableLayersSequence: " + this.mAvailableLayersSequence);
    }

    /* JADX WARN: Unsupported multi-entry loop pattern (BACK_EDGE: B:24:0x0044 -> B:25:0x0048). Please submit an issue!!! */
    public void dumpMetrics(FileDescriptor fd) {
        int i = this.mClientMetricsProperty;
        if (i == 0) {
            Slog.w(TAG, "Metrics collection is disabled");
            return;
        }
        VehiclePropValue vehicleProp = null;
        try {
            vehicleProp = this.mVehicleHal.get(i);
        } catch (PropertyTimeoutException | RuntimeException e) {
            Slog.e(TAG, "While reading metrics from client", e);
        }
        if (vehicleProp == null) {
            return;
        }
        FileOutputStream fout = new FileOutputStream(fd);
        try {
            try {
                try {
                    fout.write(CarServiceUtils.toByteArray(vehicleProp.value.bytes));
                    fout.flush();
                    fout.close();
                } catch (IOException e2) {
                    Slog.e(TAG, "Error writing metrics to output stream");
                    fout.close();
                }
            } catch (IOException e3) {
                e3.printStackTrace();
            }
        } catch (Throwable th) {
            try {
                fout.close();
            } catch (IOException e4) {
                e4.printStackTrace();
            }
            throw th;
        }
    }

    @Override // com.android.car.hal.HalServiceBase
    public void handleHalEvents(List<VehiclePropValue> values) {
        for (VehiclePropValue v : values) {
            ArrayList<Integer> vec = v.value.int32Values;
            int messageType = vec.get(0).intValue();
            if (messageType == 12) {
                handleDataEvent(vec, CarServiceUtils.toByteArray(v.value.bytes));
            } else if (messageType == 13) {
                handlePublisherIdRequest(CarServiceUtils.toByteArray(v.value.bytes));
            } else {
                if (messageType == 15) {
                    handlePublisherInfoRequest(vec);
                } else if (messageType != 17) {
                    switch (messageType) {
                        case 1:
                            handleSubscribeEvent(vec);
                            break;
                        case 2:
                            handleSubscribeToPublisherEvent(vec);
                            break;
                        case 3:
                            handleUnsubscribeEvent(vec);
                            break;
                        case 4:
                            handleUnsubscribeFromPublisherEvent(vec);
                            break;
                        case 5:
                            break;
                        case 6:
                            handleAvailabilityRequestEvent();
                            break;
                        case 7:
                            handleSubscriptionsRequestEvent();
                            break;
                        default:
                            try {
                                Slog.e(TAG, "Unexpected message type: " + messageType);
                                break;
                            } catch (RemoteException | IndexOutOfBoundsException e) {
                                Slog.e(TAG, "While handling " + VmsMessageType.toString(messageType), e);
                                break;
                            }
                    }
                } else {
                    handleStartSessionEvent(vec);
                }
                handleOfferingEvent(vec);
            }
        }
    }

    private void handleStartSessionEvent(List<Integer> message) {
        int coreId = message.get(1).intValue();
        int clientId = message.get(2).intValue();
        Slog.i(TAG, "Starting new session with coreId: " + coreId + " client: " + clientId);
        if (coreId != this.mCoreId) {
            VmsClientManager vmsClientManager = this.mClientManager;
            if (vmsClientManager != null) {
                vmsClientManager.onHalDisconnected();
            } else {
                Slog.w(TAG, "Client manager not registered");
            }
            this.mMessageQueue.clear();
            this.mSubscriptionStateSequence = -1;
            this.mAvailableLayersSequence = -1;
            setPropertyValue(createStartSessionMessage(this.mCoreId, clientId));
        }
        VmsClientManager vmsClientManager2 = this.mClientManager;
        if (vmsClientManager2 != null) {
            vmsClientManager2.onHalConnected(this.mPublisherClient, this.mSubscriberClient);
        } else {
            Slog.w(TAG, "Client manager not registered");
        }
        IVmsSubscriberService iVmsSubscriberService = this.mSubscriberService;
        if (iVmsSubscriberService != null) {
            try {
                this.mSubscriberClient.onLayersAvailabilityChanged(iVmsSubscriberService.getAvailableLayers());
                return;
            } catch (RemoteException e) {
                Slog.e(TAG, "While publishing layer availability", e);
                return;
            }
        }
        Slog.w(TAG, "Subscriber connect callback not registered");
    }

    private void handleDataEvent(List<Integer> message, byte[] payload) throws RemoteException {
        VmsLayer vmsLayer = parseVmsLayerFromMessage(message);
        int publisherId = parsePublisherIdFromMessage(message);
        this.mPublisherService.publish(this.mPublisherToken, vmsLayer, publisherId, payload);
    }

    private void handleSubscribeEvent(List<Integer> message) throws RemoteException {
        VmsLayer vmsLayer = parseVmsLayerFromMessage(message);
        this.mSubscriberService.addVmsSubscriber(this.mSubscriberClient, vmsLayer);
    }

    private void handleSubscribeToPublisherEvent(List<Integer> message) throws RemoteException {
        VmsLayer vmsLayer = parseVmsLayerFromMessage(message);
        int publisherId = parsePublisherIdFromMessage(message);
        this.mSubscriberService.addVmsSubscriberToPublisher(this.mSubscriberClient, vmsLayer, publisherId);
    }

    private void handleUnsubscribeEvent(List<Integer> message) throws RemoteException {
        VmsLayer vmsLayer = parseVmsLayerFromMessage(message);
        this.mSubscriberService.removeVmsSubscriber(this.mSubscriberClient, vmsLayer);
    }

    private void handleUnsubscribeFromPublisherEvent(List<Integer> message) throws RemoteException {
        VmsLayer vmsLayer = parseVmsLayerFromMessage(message);
        int publisherId = parsePublisherIdFromMessage(message);
        this.mSubscriberService.removeVmsSubscriberToPublisher(this.mSubscriberClient, vmsLayer, publisherId);
    }

    private void handlePublisherIdRequest(byte[] payload) throws RemoteException {
        VehiclePropValue vehicleProp = createVmsMessage(14);
        vehicleProp.value.int32Values.add(Integer.valueOf(this.mPublisherService.getPublisherId(payload)));
        setPropertyValue(vehicleProp);
    }

    private void handlePublisherInfoRequest(List<Integer> message) throws RemoteException {
        int publisherId = message.get(1).intValue();
        VehiclePropValue vehicleProp = createVmsMessage(16);
        appendBytes(vehicleProp.value.bytes, this.mSubscriberService.getPublisherInfo(publisherId));
        setPropertyValue(vehicleProp);
    }

    private void handleOfferingEvent(List<Integer> message) throws RemoteException {
        int publisherId = message.get(1).intValue();
        int numLayerDependencies = message.get(2).intValue();
        Set<VmsLayerDependency> offeredLayers = new ArraySet<>(numLayerDependencies);
        int numDependenciesForLayer = 3;
        for (int i = 0; i < numLayerDependencies; i++) {
            VmsLayer offeredLayer = parseVmsLayerAtIndex(message, numDependenciesForLayer);
            int idx = numDependenciesForLayer + 3;
            int idx2 = idx + 1;
            int numDependenciesForLayer2 = message.get(idx).intValue();
            if (numDependenciesForLayer2 == 0) {
                offeredLayers.add(new VmsLayerDependency(offeredLayer));
            } else {
                Set<VmsLayer> dependencies = new HashSet<>();
                for (int j = 0; j < numDependenciesForLayer2; j++) {
                    VmsLayer dependantLayer = parseVmsLayerAtIndex(message, idx2);
                    idx2 += 3;
                    dependencies.add(dependantLayer);
                }
                offeredLayers.add(new VmsLayerDependency(offeredLayer, dependencies));
            }
            numDependenciesForLayer = idx2;
        }
        VmsLayersOffering offering = new VmsLayersOffering(offeredLayers, publisherId);
        VmsOperationRecorder.get().setHalPublisherLayersOffering(offering);
        this.mPublisherService.setLayersOffering(this.mPublisherToken, offering);
    }

    private void handleAvailabilityRequestEvent() throws RemoteException {
        setPropertyValue(createAvailableLayersMessage(8, this.mSubscriberService.getAvailableLayers()));
    }

    private void handleSubscriptionsRequestEvent() throws RemoteException {
        setPropertyValue(createSubscriptionStateMessage(10, this.mPublisherService.getSubscriptions()));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setPropertyValue(VehiclePropValue vehicleProp) {
        int messageType = vehicleProp.value.int32Values.get(0).intValue();
        if (!this.mIsSupported) {
            Slog.w(TAG, "HAL unsupported while attempting to send " + VmsMessageType.toString(messageType));
            return;
        }
        try {
            this.mVehicleHal.set(vehicleProp);
        } catch (PropertyTimeoutException | RuntimeException e) {
            Slog.e(TAG, "While sending " + VmsMessageType.toString(messageType), e.getCause());
            if (this.mPropagatePropertyException) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static VehiclePropValue createStartSessionMessage(int coreId, int clientId) {
        VehiclePropValue vehicleProp = createVmsMessage(17);
        List<Integer> message = vehicleProp.value.int32Values;
        message.add(Integer.valueOf(coreId));
        message.add(Integer.valueOf(clientId));
        return vehicleProp;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static VehiclePropValue createDataMessage(VmsLayer layer, byte[] payload) {
        VehiclePropValue vehicleProp = createVmsMessage(12);
        appendLayer(vehicleProp.value.int32Values, layer);
        List<Integer> message = vehicleProp.value.int32Values;
        message.add(0);
        appendBytes(vehicleProp.value.bytes, payload);
        return vehicleProp;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static VehiclePropValue createSubscriptionStateMessage(int messageType, VmsSubscriptionState subscriptionState) {
        VehiclePropValue vehicleProp = createVmsMessage(messageType);
        List<Integer> message = vehicleProp.value.int32Values;
        message.add(Integer.valueOf(subscriptionState.getSequenceNumber()));
        Set<VmsLayer> layers = subscriptionState.getLayers();
        Set<VmsAssociatedLayer> associatedLayers = subscriptionState.getAssociatedLayers();
        message.add(Integer.valueOf(layers.size()));
        message.add(Integer.valueOf(associatedLayers.size()));
        for (VmsLayer layer : layers) {
            appendLayer(message, layer);
        }
        for (VmsAssociatedLayer layer2 : associatedLayers) {
            appendAssociatedLayer(message, layer2);
        }
        return vehicleProp;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static VehiclePropValue createAvailableLayersMessage(int messageType, VmsAvailableLayers availableLayers) {
        VehiclePropValue vehicleProp = createVmsMessage(messageType);
        List<Integer> message = vehicleProp.value.int32Values;
        message.add(Integer.valueOf(availableLayers.getSequence()));
        message.add(Integer.valueOf(availableLayers.getAssociatedLayers().size()));
        for (VmsAssociatedLayer layer : availableLayers.getAssociatedLayers()) {
            appendAssociatedLayer(message, layer);
        }
        return vehicleProp;
    }

    private static VehiclePropValue createVmsMessage(int messageType) {
        VehiclePropValue vehicleProp = new VehiclePropValue();
        vehicleProp.prop = 299895808;
        vehicleProp.areaId = 0;
        vehicleProp.value.int32Values.add(Integer.valueOf(messageType));
        return vehicleProp;
    }

    private static void appendLayer(List<Integer> message, VmsLayer layer) {
        message.add(Integer.valueOf(layer.getType()));
        message.add(Integer.valueOf(layer.getSubtype()));
        message.add(Integer.valueOf(layer.getVersion()));
    }

    private static void appendAssociatedLayer(List<Integer> message, VmsAssociatedLayer layer) {
        message.add(Integer.valueOf(layer.getVmsLayer().getType()));
        message.add(Integer.valueOf(layer.getVmsLayer().getSubtype()));
        message.add(Integer.valueOf(layer.getVmsLayer().getVersion()));
        message.add(Integer.valueOf(layer.getPublisherIds().size()));
        message.addAll(layer.getPublisherIds());
    }

    private static void appendBytes(ArrayList<Byte> dst, byte[] src) {
        dst.ensureCapacity(src.length);
        for (byte b : src) {
            dst.add(Byte.valueOf(b));
        }
    }

    private static VmsLayer parseVmsLayerFromMessage(List<Integer> message) {
        return parseVmsLayerAtIndex(message, 1);
    }

    private static VmsLayer parseVmsLayerAtIndex(List<Integer> message, int index) {
        List<Integer> layerValues = message.subList(index, index + 3);
        return new VmsLayer(layerValues.get(0).intValue(), layerValues.get(1).intValue(), layerValues.get(2).intValue());
    }

    private static int parsePublisherIdFromMessage(List<Integer> message) {
        return message.get(4).intValue();
    }
}
