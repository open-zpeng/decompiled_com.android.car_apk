package com.android.car;

import android.car.vms.IVmsSubscriberClient;
import android.car.vms.IVmsSubscriberService;
import android.car.vms.VmsAvailableLayers;
import android.car.vms.VmsLayer;
import android.content.Context;
import android.os.RemoteException;
import android.util.Slog;
import com.android.car.hal.VmsHalService;
import com.android.car.vms.VmsBrokerService;
import com.android.car.vms.VmsClientManager;
import java.io.PrintWriter;
/* loaded from: classes3.dex */
public class VmsSubscriberService extends IVmsSubscriberService.Stub implements CarServiceBase, VmsBrokerService.SubscriberListener {
    private static final String TAG = "VmsSubscriberService";
    private final VmsBrokerService mBrokerService;
    private final VmsClientManager mClientManager;
    private final Context mContext;

    /* JADX INFO: Access modifiers changed from: package-private */
    public VmsSubscriberService(Context context, VmsBrokerService brokerService, VmsClientManager clientManager, VmsHalService hal) {
        this.mContext = context;
        this.mBrokerService = brokerService;
        this.mClientManager = clientManager;
        this.mBrokerService.addSubscriberListener(this);
        hal.setVmsSubscriberService(this);
    }

    @Override // com.android.car.CarServiceBase
    public void init() {
    }

    @Override // com.android.car.CarServiceBase
    public void release() {
    }

    @Override // com.android.car.CarServiceBase
    public void dump(PrintWriter writer) {
    }

    public void addVmsSubscriberToNotifications(IVmsSubscriberClient subscriber) {
        ICarImpl.assertVmsSubscriberPermission(this.mContext);
        this.mClientManager.addSubscriber(subscriber);
    }

    public void removeVmsSubscriberToNotifications(IVmsSubscriberClient subscriber) {
        ICarImpl.assertVmsSubscriberPermission(this.mContext);
        this.mClientManager.removeSubscriber(subscriber);
    }

    public void addVmsSubscriber(IVmsSubscriberClient subscriber, VmsLayer layer) {
        ICarImpl.assertVmsSubscriberPermission(this.mContext);
        this.mClientManager.addSubscriber(subscriber);
        this.mBrokerService.addSubscription(subscriber, layer);
    }

    public void removeVmsSubscriber(IVmsSubscriberClient subscriber, VmsLayer layer) {
        ICarImpl.assertVmsSubscriberPermission(this.mContext);
        this.mBrokerService.removeSubscription(subscriber, layer);
    }

    public void addVmsSubscriberToPublisher(IVmsSubscriberClient subscriber, VmsLayer layer, int publisherId) {
        ICarImpl.assertVmsSubscriberPermission(this.mContext);
        this.mClientManager.addSubscriber(subscriber);
        this.mBrokerService.addSubscription(subscriber, layer, publisherId);
    }

    public void removeVmsSubscriberToPublisher(IVmsSubscriberClient subscriber, VmsLayer layer, int publisherId) {
        ICarImpl.assertVmsSubscriberPermission(this.mContext);
        this.mBrokerService.removeSubscription(subscriber, layer, publisherId);
    }

    public void addVmsSubscriberPassive(IVmsSubscriberClient subscriber) {
        ICarImpl.assertVmsSubscriberPermission(this.mContext);
        this.mClientManager.addSubscriber(subscriber);
        this.mBrokerService.addSubscription(subscriber);
    }

    public void removeVmsSubscriberPassive(IVmsSubscriberClient subscriber) {
        ICarImpl.assertVmsSubscriberPermission(this.mContext);
        this.mBrokerService.removeSubscription(subscriber);
    }

    public byte[] getPublisherInfo(int publisherId) {
        ICarImpl.assertVmsSubscriberPermission(this.mContext);
        return this.mBrokerService.getPublisherInfo(publisherId);
    }

    public VmsAvailableLayers getAvailableLayers() {
        ICarImpl.assertVmsSubscriberPermission(this.mContext);
        return this.mBrokerService.getAvailableLayers();
    }

    @Override // com.android.car.vms.VmsBrokerService.SubscriberListener
    public void onLayersAvailabilityChange(VmsAvailableLayers availableLayers) {
        for (IVmsSubscriberClient subscriber : this.mClientManager.getAllSubscribers()) {
            try {
                subscriber.onLayersAvailabilityChanged(availableLayers);
            } catch (RemoteException e) {
                Slog.e(TAG, "onLayersAvailabilityChanged failed: " + this.mClientManager.getPackageName(subscriber), e);
            }
        }
    }
}
