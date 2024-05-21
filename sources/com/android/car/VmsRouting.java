package com.android.car;

import android.car.vms.IVmsSubscriberClient;
import android.car.vms.VmsAssociatedLayer;
import android.car.vms.VmsLayer;
import android.car.vms.VmsOperationRecorder;
import android.car.vms.VmsSubscriptionState;
import android.os.IBinder;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Pair;
import com.android.internal.annotations.GuardedBy;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
/* loaded from: classes3.dex */
public class VmsRouting {
    private final Object mLock = new Object();
    @GuardedBy({"mLock"})
    private Map<IBinder, IVmsSubscriberClient> mSubscribers = new ArrayMap();
    @GuardedBy({"mLock"})
    private Set<IBinder> mPassiveSubscribers = new ArraySet();
    @GuardedBy({"mLock"})
    private Map<VmsLayer, Set<IBinder>> mLayerSubscriptions = new ArrayMap();
    @GuardedBy({"mLock"})
    private Map<VmsLayer, Map<Integer, Set<IBinder>>> mLayerSubscriptionsToPublishers = new ArrayMap();
    @GuardedBy({"mLock"})
    private int mSequenceNumber = 0;

    public void addSubscription(IVmsSubscriberClient subscriber) {
        synchronized (this.mLock) {
            if (this.mPassiveSubscribers.add(addSubscriber(subscriber))) {
                int sequenceNumber = this.mSequenceNumber;
                VmsOperationRecorder.get().addPromiscuousSubscription(sequenceNumber);
            }
        }
    }

    public void removeSubscription(IVmsSubscriberClient subscriber) {
        synchronized (this.mLock) {
            if (this.mPassiveSubscribers.remove(subscriber.asBinder())) {
                int sequenceNumber = this.mSequenceNumber;
                VmsOperationRecorder.get().removePromiscuousSubscription(sequenceNumber);
            }
        }
    }

    public void addSubscription(IVmsSubscriberClient subscriber, VmsLayer layer) {
        synchronized (this.mLock) {
            Set<IBinder> subscribers = this.mLayerSubscriptions.computeIfAbsent(layer, new Function() { // from class: com.android.car.-$$Lambda$VmsRouting$tGMbM3CQFJnnnRz8NVUB3lDoh20
                @Override // java.util.function.Function
                public final Object apply(Object obj) {
                    return VmsRouting.lambda$addSubscription$0((VmsLayer) obj);
                }
            });
            if (subscribers.add(addSubscriber(subscriber))) {
                int sequenceNumber = this.mSequenceNumber + 1;
                this.mSequenceNumber = sequenceNumber;
                VmsOperationRecorder.get().addSubscription(sequenceNumber, layer);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ Set lambda$addSubscription$0(VmsLayer k) {
        return new ArraySet();
    }

    /* renamed from: removeSubscription */
    public void lambda$removeDeadSubscriber$4$VmsRouting(IVmsSubscriberClient subscriber, VmsLayer layer) {
        synchronized (this.mLock) {
            Set<IBinder> subscribers = this.mLayerSubscriptions.getOrDefault(layer, Collections.emptySet());
            if (subscribers.remove(subscriber.asBinder())) {
                int sequenceNumber = this.mSequenceNumber + 1;
                this.mSequenceNumber = sequenceNumber;
                if (subscribers.isEmpty()) {
                    this.mLayerSubscriptions.remove(layer);
                }
                VmsOperationRecorder.get().removeSubscription(sequenceNumber, layer);
            }
        }
    }

    public void addSubscription(IVmsSubscriberClient subscriber, VmsLayer layer, int publisherId) {
        synchronized (this.mLock) {
            Set<IBinder> subscribers = this.mLayerSubscriptionsToPublishers.computeIfAbsent(layer, new Function() { // from class: com.android.car.-$$Lambda$VmsRouting$J3LVu7IEDHVo1HV-drUSwEcKoXU
                @Override // java.util.function.Function
                public final Object apply(Object obj) {
                    return VmsRouting.lambda$addSubscription$1((VmsLayer) obj);
                }
            }).computeIfAbsent(Integer.valueOf(publisherId), new Function() { // from class: com.android.car.-$$Lambda$VmsRouting$xBchmasXgfK4z6RsODbUQdpO9hs
                @Override // java.util.function.Function
                public final Object apply(Object obj) {
                    return VmsRouting.lambda$addSubscription$2((Integer) obj);
                }
            });
            if (subscribers.add(addSubscriber(subscriber))) {
                int sequenceNumber = this.mSequenceNumber + 1;
                this.mSequenceNumber = sequenceNumber;
                VmsOperationRecorder.get().addSubscription(sequenceNumber, layer);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ Map lambda$addSubscription$1(VmsLayer k) {
        return new ArrayMap();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ Set lambda$addSubscription$2(Integer k) {
        return new ArraySet();
    }

    public void removeSubscription(IVmsSubscriberClient subscriber, VmsLayer layer, int publisherId) {
        synchronized (this.mLock) {
            Map<Integer, Set<IBinder>> subscribersToPublishers = this.mLayerSubscriptionsToPublishers.getOrDefault(layer, Collections.emptyMap());
            Set<IBinder> subscribers = subscribersToPublishers.getOrDefault(Integer.valueOf(publisherId), Collections.emptySet());
            if (subscribers.remove(subscriber.asBinder())) {
                int sequenceNumber = this.mSequenceNumber + 1;
                this.mSequenceNumber = sequenceNumber;
                if (subscribers.isEmpty()) {
                    subscribersToPublishers.remove(Integer.valueOf(publisherId));
                }
                if (subscribersToPublishers.isEmpty()) {
                    this.mLayerSubscriptionsToPublishers.remove(layer);
                }
                VmsOperationRecorder.get().removeSubscription(sequenceNumber, layer);
            }
        }
    }

    public boolean removeDeadSubscriber(final IVmsSubscriberClient subscriber) {
        boolean z;
        final IBinder subscriberBinder = subscriber.asBinder();
        synchronized (this.mLock) {
            int startSequenceNumber = this.mSequenceNumber;
            removeSubscription(subscriber);
            ((Set) this.mLayerSubscriptions.entrySet().stream().filter(new Predicate() { // from class: com.android.car.-$$Lambda$VmsRouting$iPb9kKsMMZoHWOmUbqWJjbAFIzc
                @Override // java.util.function.Predicate
                public final boolean test(Object obj) {
                    boolean contains;
                    contains = ((Set) ((Map.Entry) obj).getValue()).contains(subscriberBinder);
                    return contains;
                }
            }).map(new Function() { // from class: com.android.car.-$$Lambda$Nb0Md9TjmiJit5qkuy3Ytehw0y8
                @Override // java.util.function.Function
                public final Object apply(Object obj) {
                    return (VmsLayer) ((Map.Entry) obj).getKey();
                }
            }).collect(Collectors.toSet())).forEach(new Consumer() { // from class: com.android.car.-$$Lambda$VmsRouting$9rwgIJpVT2BPNIP5VrLYe-bjdTk
                @Override // java.util.function.Consumer
                public final void accept(Object obj) {
                    VmsRouting.this.lambda$removeDeadSubscriber$4$VmsRouting(subscriber, (VmsLayer) obj);
                }
            });
            ((Set) this.mLayerSubscriptionsToPublishers.entrySet().stream().flatMap(new Function() { // from class: com.android.car.-$$Lambda$VmsRouting$pDpxYftgZhqMpGUboHEx0-MnuKg
                @Override // java.util.function.Function
                public final Object apply(Object obj) {
                    Stream map;
                    map = ((Map) r2.getValue()).entrySet().stream().filter(new Predicate() { // from class: com.android.car.-$$Lambda$VmsRouting$Sf0Mf-MjsAvYT4ponMWGUs3bNEY
                        @Override // java.util.function.Predicate
                        public final boolean test(Object obj2) {
                            boolean contains;
                            contains = ((Set) ((Map.Entry) obj2).getValue()).contains(r1);
                            return contains;
                        }
                    }).map(new Function() { // from class: com.android.car.-$$Lambda$VmsRouting$rbORpligw5njaPtOOqyFrlyFdS0
                        @Override // java.util.function.Function
                        public final Object apply(Object obj2) {
                            Pair create;
                            create = Pair.create((VmsLayer) r1.getKey(), (Integer) ((Map.Entry) obj2).getKey());
                            return create;
                        }
                    });
                    return map;
                }
            }).collect(Collectors.toSet())).forEach(new Consumer() { // from class: com.android.car.-$$Lambda$VmsRouting$OjB8KGAbXPoURq29ab4ql-t6Yb8
                @Override // java.util.function.Consumer
                public final void accept(Object obj) {
                    VmsRouting.this.lambda$removeDeadSubscriber$8$VmsRouting(subscriber, (Pair) obj);
                }
            });
            this.mSubscribers.remove(subscriberBinder);
            z = startSequenceNumber != this.mSequenceNumber;
        }
        return z;
    }

    public /* synthetic */ void lambda$removeDeadSubscriber$8$VmsRouting(IVmsSubscriberClient subscriber, Pair layerAndPublisher) {
        removeSubscription(subscriber, (VmsLayer) layerAndPublisher.first, ((Integer) layerAndPublisher.second).intValue());
    }

    public Set<IVmsSubscriberClient> getSubscribersForLayerFromPublisher(VmsLayer layer, int publisherId) {
        Set<IBinder> subscribers = new HashSet<>();
        synchronized (this.mLock) {
            subscribers.addAll(this.mPassiveSubscribers);
            subscribers.addAll(this.mLayerSubscriptions.getOrDefault(layer, Collections.emptySet()));
            subscribers.addAll(this.mLayerSubscriptionsToPublishers.getOrDefault(layer, Collections.emptyMap()).getOrDefault(Integer.valueOf(publisherId), Collections.emptySet()));
        }
        return (Set) subscribers.stream().map(new Function() { // from class: com.android.car.-$$Lambda$VmsRouting$2QhXcCdaq24mdf6ln1mzgwDhmE4
            @Override // java.util.function.Function
            public final Object apply(Object obj) {
                return VmsRouting.this.lambda$getSubscribersForLayerFromPublisher$9$VmsRouting((IBinder) obj);
            }
        }).filter(new Predicate() { // from class: com.android.car.-$$Lambda$yq0RKY_jp-5-R9a9yftuQj8ngMs
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return Objects.nonNull((IVmsSubscriberClient) obj);
            }
        }).collect(Collectors.toSet());
    }

    public /* synthetic */ IVmsSubscriberClient lambda$getSubscribersForLayerFromPublisher$9$VmsRouting(IBinder binder) {
        return this.mSubscribers.get(binder);
    }

    public boolean hasLayerSubscriptions(VmsLayer layer) {
        boolean containsKey;
        synchronized (this.mLock) {
            containsKey = this.mLayerSubscriptions.containsKey(layer);
        }
        return containsKey;
    }

    public boolean hasLayerFromPublisherSubscriptions(VmsLayer layer, int publisherId) {
        boolean z;
        synchronized (this.mLock) {
            z = this.mLayerSubscriptionsToPublishers.containsKey(layer) && this.mLayerSubscriptionsToPublishers.getOrDefault(layer, Collections.emptyMap()).containsKey(Integer.valueOf(publisherId));
        }
        return z;
    }

    public VmsSubscriptionState getSubscriptionState() {
        VmsSubscriptionState vmsSubscriptionState;
        synchronized (this.mLock) {
            vmsSubscriptionState = new VmsSubscriptionState(this.mSequenceNumber, new ArraySet(this.mLayerSubscriptions.keySet()), (Set) this.mLayerSubscriptionsToPublishers.entrySet().stream().map(new Function() { // from class: com.android.car.-$$Lambda$VmsRouting$bZNaiDtYuu8PeKeRRaiqueV2MHU
                @Override // java.util.function.Function
                public final Object apply(Object obj) {
                    return VmsRouting.lambda$getSubscriptionState$10((Map.Entry) obj);
                }
            }).collect(Collectors.toSet()));
        }
        return vmsSubscriptionState;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ VmsAssociatedLayer lambda$getSubscriptionState$10(Map.Entry e) {
        return new VmsAssociatedLayer((VmsLayer) e.getKey(), ((Map) e.getValue()).keySet());
    }

    private IBinder addSubscriber(IVmsSubscriberClient subscriber) {
        IBinder subscriberBinder = subscriber.asBinder();
        synchronized (this.mLock) {
            this.mSubscribers.putIfAbsent(subscriberBinder, subscriber);
        }
        return subscriberBinder;
    }
}
