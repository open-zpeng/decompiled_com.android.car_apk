package com.android.car.vms;

import android.car.vms.IVmsSubscriberClient;
import android.car.vms.VmsAvailableLayers;
import android.car.vms.VmsLayer;
import android.car.vms.VmsLayersOffering;
import android.car.vms.VmsOperationRecorder;
import android.car.vms.VmsSubscriptionState;
import android.os.IBinder;
import android.util.Slog;
import com.android.car.VmsLayersAvailability;
import com.android.car.VmsPublishersInfo;
import com.android.car.VmsRouting;
import com.android.internal.annotations.GuardedBy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
/* loaded from: classes3.dex */
public class VmsBrokerService {
    private static final boolean DBG = false;
    private static final String TAG = "VmsBrokerService";
    private CopyOnWriteArrayList<PublisherListener> mPublisherListeners = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<SubscriberListener> mSubscriberListeners = new CopyOnWriteArrayList<>();
    private final Object mLock = new Object();
    @GuardedBy({"mLock"})
    private final VmsRouting mRouting = new VmsRouting();
    @GuardedBy({"mLock"})
    private final Map<IBinder, Map<Integer, VmsLayersOffering>> mOfferings = new HashMap();
    @GuardedBy({"mLock"})
    private final VmsLayersAvailability mAvailableLayers = new VmsLayersAvailability();
    @GuardedBy({"mLock"})
    private final VmsPublishersInfo mPublishersInfo = new VmsPublishersInfo();

    /* loaded from: classes3.dex */
    public interface PublisherListener {
        void onSubscriptionChange(VmsSubscriptionState vmsSubscriptionState);
    }

    /* loaded from: classes3.dex */
    public interface SubscriberListener {
        void onLayersAvailabilityChange(VmsAvailableLayers vmsAvailableLayers);
    }

    public void addPublisherListener(PublisherListener listener) {
        this.mPublisherListeners.add(listener);
    }

    public void addSubscriberListener(SubscriberListener listener) {
        this.mSubscriberListeners.add(listener);
    }

    public void removePublisherListener(PublisherListener listener) {
        this.mPublisherListeners.remove(listener);
    }

    public void removeSubscriberListener(SubscriberListener listener) {
        this.mSubscriberListeners.remove(listener);
    }

    public void addSubscription(IVmsSubscriberClient subscriber) {
        synchronized (this.mLock) {
            this.mRouting.addSubscription(subscriber);
        }
    }

    public void removeSubscription(IVmsSubscriberClient subscriber) {
        synchronized (this.mLock) {
            this.mRouting.removeSubscription(subscriber);
        }
    }

    public void addSubscription(IVmsSubscriberClient subscriber, VmsLayer layer) {
        boolean firstSubscriptionForLayer;
        synchronized (this.mLock) {
            firstSubscriptionForLayer = !this.mRouting.hasLayerSubscriptions(layer);
            this.mRouting.addSubscription(subscriber, layer);
        }
        if (firstSubscriptionForLayer) {
            notifyOfSubscriptionChange();
        }
    }

    public void removeSubscription(IVmsSubscriberClient subscriber, VmsLayer layer) {
        synchronized (this.mLock) {
            if (this.mRouting.hasLayerSubscriptions(layer)) {
                this.mRouting.lambda$removeDeadSubscriber$4$VmsRouting(subscriber, layer);
                boolean layerHasSubscribers = this.mRouting.hasLayerSubscriptions(layer);
                if (!layerHasSubscribers) {
                    notifyOfSubscriptionChange();
                }
            }
        }
    }

    public void addSubscription(IVmsSubscriberClient subscriber, VmsLayer layer, int publisherId) {
        boolean firstSubscriptionForLayer;
        synchronized (this.mLock) {
            firstSubscriptionForLayer = (this.mRouting.hasLayerSubscriptions(layer) || this.mRouting.hasLayerFromPublisherSubscriptions(layer, publisherId)) ? false : true;
            this.mRouting.addSubscription(subscriber, layer, publisherId);
        }
        if (firstSubscriptionForLayer) {
            notifyOfSubscriptionChange();
        }
    }

    /* JADX WARN: Removed duplicated region for block: B:17:0x0029  */
    /* JADX WARN: Removed duplicated region for block: B:23:? A[RETURN, SYNTHETIC] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public void removeSubscription(android.car.vms.IVmsSubscriberClient r3, android.car.vms.VmsLayer r4, int r5) {
        /*
            r2 = this;
            java.lang.Object r0 = r2.mLock
            monitor-enter(r0)
            com.android.car.VmsRouting r1 = r2.mRouting     // Catch: java.lang.Throwable -> L2d
            boolean r1 = r1.hasLayerFromPublisherSubscriptions(r4, r5)     // Catch: java.lang.Throwable -> L2d
            if (r1 != 0) goto Ld
            monitor-exit(r0)     // Catch: java.lang.Throwable -> L2d
            return
        Ld:
            com.android.car.VmsRouting r1 = r2.mRouting     // Catch: java.lang.Throwable -> L2d
            r1.removeSubscription(r3, r4, r5)     // Catch: java.lang.Throwable -> L2d
            com.android.car.VmsRouting r1 = r2.mRouting     // Catch: java.lang.Throwable -> L2d
            boolean r1 = r1.hasLayerSubscriptions(r4)     // Catch: java.lang.Throwable -> L2d
            if (r1 != 0) goto L25
            com.android.car.VmsRouting r1 = r2.mRouting     // Catch: java.lang.Throwable -> L2d
            boolean r1 = r1.hasLayerFromPublisherSubscriptions(r4, r5)     // Catch: java.lang.Throwable -> L2d
            if (r1 == 0) goto L23
            goto L25
        L23:
            r1 = 0
            goto L26
        L25:
            r1 = 1
        L26:
            monitor-exit(r0)     // Catch: java.lang.Throwable -> L2d
            if (r1 != 0) goto L2c
            r2.notifyOfSubscriptionChange()
        L2c:
            return
        L2d:
            r1 = move-exception
            monitor-exit(r0)     // Catch: java.lang.Throwable -> L2d
            throw r1
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.car.vms.VmsBrokerService.removeSubscription(android.car.vms.IVmsSubscriberClient, android.car.vms.VmsLayer, int):void");
    }

    public void removeDeadSubscriber(IVmsSubscriberClient subscriber) {
        boolean subscriptionStateChanged;
        synchronized (this.mLock) {
            subscriptionStateChanged = this.mRouting.removeDeadSubscriber(subscriber);
        }
        if (subscriptionStateChanged) {
            notifyOfSubscriptionChange();
        }
    }

    public Set<IVmsSubscriberClient> getSubscribersForLayerFromPublisher(VmsLayer layer, int publisherId) {
        Set<IVmsSubscriberClient> subscribersForLayerFromPublisher;
        synchronized (this.mLock) {
            subscribersForLayerFromPublisher = this.mRouting.getSubscribersForLayerFromPublisher(layer, publisherId);
        }
        return subscribersForLayerFromPublisher;
    }

    public VmsSubscriptionState getSubscriptionState() {
        VmsSubscriptionState subscriptionState;
        synchronized (this.mLock) {
            subscriptionState = this.mRouting.getSubscriptionState();
        }
        return subscriptionState;
    }

    public int getPublisherId(byte[] publisherInfo) {
        int idForInfo;
        synchronized (this.mLock) {
            idForInfo = this.mPublishersInfo.getIdForInfo(publisherInfo);
        }
        return idForInfo;
    }

    public byte[] getPublisherInfo(int publisherId) {
        byte[] publisherInfo;
        synchronized (this.mLock) {
            publisherInfo = this.mPublishersInfo.getPublisherInfo(publisherId);
        }
        return publisherInfo;
    }

    public void setPublisherLayersOffering(IBinder publisherToken, VmsLayersOffering offering) {
        synchronized (this.mLock) {
            Map<Integer, VmsLayersOffering> publisherOfferings = this.mOfferings.computeIfAbsent(publisherToken, new Function() { // from class: com.android.car.vms.-$$Lambda$VmsBrokerService$Bhk3pslmP2FTUJzs8XOKQosYJv4
                @Override // java.util.function.Function
                public final Object apply(Object obj) {
                    return VmsBrokerService.lambda$setPublisherLayersOffering$0((IBinder) obj);
                }
            });
            publisherOfferings.put(Integer.valueOf(offering.getPublisherId()), offering);
            updateLayerAvailability();
        }
        VmsOperationRecorder.get().setPublisherLayersOffering(offering);
        notifyOfAvailabilityChange();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ Map lambda$setPublisherLayersOffering$0(IBinder k) {
        return new HashMap();
    }

    public void removeDeadPublisher(IBinder publisherToken) {
        synchronized (this.mLock) {
            this.mOfferings.remove(publisherToken);
            updateLayerAvailability();
        }
        notifyOfAvailabilityChange();
    }

    public VmsAvailableLayers getAvailableLayers() {
        VmsAvailableLayers availableLayers;
        synchronized (this.mLock) {
            availableLayers = this.mAvailableLayers.getAvailableLayers();
        }
        return availableLayers;
    }

    private void updateLayerAvailability() {
        Set<VmsLayersOffering> allPublisherOfferings = new HashSet<>();
        synchronized (this.mLock) {
            for (Map<Integer, VmsLayersOffering> offerings : this.mOfferings.values()) {
                allPublisherOfferings.addAll(offerings.values());
            }
            this.mAvailableLayers.setPublishersOffering(allPublisherOfferings);
        }
    }

    private void notifyOfSubscriptionChange() {
        VmsSubscriptionState subscriptionState = getSubscriptionState();
        Slog.i(TAG, "Notifying publishers of subscriptions: " + subscriptionState);
        Iterator<PublisherListener> it = this.mPublisherListeners.iterator();
        while (it.hasNext()) {
            PublisherListener listener = it.next();
            listener.onSubscriptionChange(subscriptionState);
        }
    }

    private void notifyOfAvailabilityChange() {
        VmsAvailableLayers availableLayers = getAvailableLayers();
        Slog.i(TAG, "Notifying subscribers of layers availability: " + availableLayers);
        Iterator<SubscriberListener> it = this.mSubscriberListeners.iterator();
        while (it.hasNext()) {
            SubscriberListener listener = it.next();
            listener.onLayersAvailabilityChange(availableLayers);
        }
    }
}
