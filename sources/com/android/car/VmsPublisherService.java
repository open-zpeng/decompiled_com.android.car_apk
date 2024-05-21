package com.android.car;

import android.car.vms.IVmsPublisherClient;
import android.car.vms.IVmsPublisherService;
import android.car.vms.IVmsSubscriberClient;
import android.car.vms.VmsLayer;
import android.car.vms.VmsLayersOffering;
import android.car.vms.VmsSubscriptionState;
import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Slog;
import com.android.car.VmsPublisherService;
import com.android.car.stats.CarStatsService;
import com.android.car.vms.VmsBrokerService;
import com.android.car.vms.VmsClientManager;
import com.android.internal.annotations.VisibleForTesting;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
/* loaded from: classes3.dex */
public class VmsPublisherService implements CarServiceBase {
    private static final boolean DBG = false;
    private static final String TAG = "VmsPublisherService";
    private final VmsBrokerService mBrokerService;
    private final VmsClientManager mClientManager;
    private final Context mContext;
    private final IntSupplier mGetCallingUid;
    private final Map<String, PublisherProxy> mPublisherProxies;
    private final CarStatsService mStatsService;

    /* JADX INFO: Access modifiers changed from: package-private */
    public VmsPublisherService(Context context, CarStatsService statsService, VmsBrokerService brokerService, VmsClientManager clientManager) {
        this(context, statsService, brokerService, clientManager, new IntSupplier() { // from class: com.android.car.-$$Lambda$OLUSIA110KxM3wbFP4L-5xrTvHw
            @Override // java.util.function.IntSupplier
            public final int getAsInt() {
                return Binder.getCallingUid();
            }
        });
    }

    @VisibleForTesting
    VmsPublisherService(Context context, CarStatsService statsService, VmsBrokerService brokerService, VmsClientManager clientManager, IntSupplier getCallingUid) {
        this.mPublisherProxies = Collections.synchronizedMap(new ArrayMap());
        this.mContext = context;
        this.mStatsService = statsService;
        this.mBrokerService = brokerService;
        this.mClientManager = clientManager;
        this.mGetCallingUid = getCallingUid;
        this.mClientManager.setPublisherService(this);
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
    }

    @Override // com.android.car.CarServiceBase
    public void release() {
        this.mPublisherProxies.values().forEach(new Consumer() { // from class: com.android.car.-$$Lambda$5ZZJlJ3IGzQ4D_9yX9FKZz1sXu0
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                ((VmsPublisherService.PublisherProxy) obj).unregister();
            }
        });
        this.mPublisherProxies.clear();
    }

    @Override // com.android.car.CarServiceBase
    public void dump(PrintWriter writer) {
        writer.println("*" + getClass().getSimpleName() + "*");
        StringBuilder sb = new StringBuilder();
        sb.append("mPublisherProxies: ");
        sb.append(this.mPublisherProxies.size());
        writer.println(sb.toString());
    }

    public void onClientConnected(String publisherName, IVmsPublisherClient publisherClient) {
        IBinder publisherToken = new Binder();
        PublisherProxy publisherProxy = new PublisherProxy(publisherName, publisherToken, publisherClient);
        publisherProxy.register();
        try {
            publisherClient.setVmsPublisherService(publisherToken, publisherProxy);
            PublisherProxy existingProxy = this.mPublisherProxies.put(publisherName, publisherProxy);
            if (existingProxy != null) {
                existingProxy.unregister();
            }
        } catch (Throwable e) {
            Slog.e(TAG, "unable to configure publisher: " + publisherName, e);
        }
    }

    public void onClientDisconnected(String publisherName) {
        PublisherProxy proxy = this.mPublisherProxies.remove(publisherName);
        if (proxy != null) {
            proxy.unregister();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class PublisherProxy extends IVmsPublisherService.Stub implements VmsBrokerService.PublisherListener {
        private boolean mConnected;
        private final String mName;
        private final IVmsPublisherClient mPublisherClient;
        private final IBinder mToken;

        PublisherProxy(String name, IBinder token, IVmsPublisherClient publisherClient) {
            this.mName = name;
            this.mToken = token;
            this.mPublisherClient = publisherClient;
        }

        void register() {
            this.mConnected = true;
            VmsPublisherService.this.mBrokerService.addPublisherListener(this);
        }

        /* JADX INFO: Access modifiers changed from: package-private */
        public void unregister() {
            this.mConnected = false;
            VmsPublisherService.this.mBrokerService.removePublisherListener(this);
            VmsPublisherService.this.mBrokerService.removeDeadPublisher(this.mToken);
        }

        public void setLayersOffering(IBinder token, VmsLayersOffering offering) {
            assertPermission(token);
            VmsPublisherService.this.mBrokerService.setPublisherLayersOffering(token, offering);
        }

        public void publish(IBinder token, VmsLayer layer, int publisherId, byte[] payload) {
            assertPermission(token);
            if (layer == null) {
                return;
            }
            int payloadLength = payload != null ? payload.length : 0;
            VmsPublisherService.this.mStatsService.getVmsClientLogger(VmsPublisherService.this.mGetCallingUid.getAsInt()).logPacketSent(layer, payloadLength);
            Set<IVmsSubscriberClient> listeners = VmsPublisherService.this.mBrokerService.getSubscribersForLayerFromPublisher(layer, publisherId);
            if (listeners.size() == 0) {
                VmsPublisherService.this.mStatsService.getVmsClientLogger(-1).logPacketDropped(layer, payloadLength);
            }
            for (IVmsSubscriberClient listener : listeners) {
                int subscriberUid = VmsPublisherService.this.mClientManager.getSubscriberUid(listener);
                try {
                    listener.onVmsMessageReceived(layer, payload);
                    VmsPublisherService.this.mStatsService.getVmsClientLogger(subscriberUid).logPacketReceived(layer, payloadLength);
                } catch (RemoteException e) {
                    VmsPublisherService.this.mStatsService.getVmsClientLogger(subscriberUid).logPacketDropped(layer, payloadLength);
                    String subscriberName = VmsPublisherService.this.mClientManager.getPackageName(listener);
                    Slog.e(VmsPublisherService.TAG, String.format("Unable to publish to listener: %s", subscriberName));
                }
            }
        }

        public VmsSubscriptionState getSubscriptions() {
            assertPermission();
            return VmsPublisherService.this.mBrokerService.getSubscriptionState();
        }

        public int getPublisherId(byte[] publisherInfo) {
            assertPermission();
            return VmsPublisherService.this.mBrokerService.getPublisherId(publisherInfo);
        }

        @Override // com.android.car.vms.VmsBrokerService.PublisherListener
        public void onSubscriptionChange(VmsSubscriptionState subscriptionState) {
            try {
                this.mPublisherClient.onVmsSubscriptionChange(subscriptionState);
            } catch (Throwable e) {
                Slog.e(VmsPublisherService.TAG, String.format("Unable to send subscription state to: %s", this.mName), e);
            }
        }

        private void assertPermission(IBinder publisherToken) {
            if (this.mToken != publisherToken) {
                throw new SecurityException("Invalid publisher token");
            }
            assertPermission();
        }

        private void assertPermission() {
            if (this.mConnected) {
                ICarImpl.assertVmsPublisherPermission(VmsPublisherService.this.mContext);
                return;
            }
            throw new SecurityException("Publisher has been disconnected");
        }
    }
}
