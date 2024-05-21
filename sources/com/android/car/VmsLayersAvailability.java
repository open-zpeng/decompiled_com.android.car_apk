package com.android.car;

import android.car.vms.VmsAssociatedLayer;
import android.car.vms.VmsAvailableLayers;
import android.car.vms.VmsLayer;
import android.car.vms.VmsLayerDependency;
import android.car.vms.VmsLayersOffering;
import android.util.Slog;
import com.android.internal.annotations.GuardedBy;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
/* loaded from: classes3.dex */
public class VmsLayersAvailability {
    private static final boolean DBG = false;
    private static final String TAG = "VmsLayersAvailability";
    private final Object mLock = new Object();
    @GuardedBy({"mLock"})
    private final Map<VmsLayer, Set<Set<VmsLayer>>> mPotentialLayersAndDependencies = new HashMap();
    @GuardedBy({"mLock"})
    private Set<VmsAssociatedLayer> mAvailableAssociatedLayers = Collections.EMPTY_SET;
    @GuardedBy({"mLock"})
    private Set<VmsAssociatedLayer> mUnavailableAssociatedLayers = Collections.EMPTY_SET;
    @GuardedBy({"mLock"})
    private Map<VmsLayer, Set<Integer>> mPotentialLayersAndPublishers = new HashMap();
    @GuardedBy({"mLock"})
    private int mSeq = 0;

    public void setPublishersOffering(Collection<VmsLayersOffering> publishersLayersOfferings) {
        synchronized (this.mLock) {
            reset();
            for (VmsLayersOffering offering : publishersLayersOfferings) {
                for (VmsLayerDependency dependency : offering.getDependencies()) {
                    VmsLayer layer = dependency.getLayer();
                    Set<Integer> curPotentialLayerAndPublishers = this.mPotentialLayersAndPublishers.get(layer);
                    if (curPotentialLayerAndPublishers == null) {
                        curPotentialLayerAndPublishers = new HashSet();
                        this.mPotentialLayersAndPublishers.put(layer, curPotentialLayerAndPublishers);
                    }
                    curPotentialLayerAndPublishers.add(Integer.valueOf(offering.getPublisherId()));
                    Set<Set<VmsLayer>> curDependencies = this.mPotentialLayersAndDependencies.get(layer);
                    if (curDependencies == null) {
                        curDependencies = new HashSet();
                        this.mPotentialLayersAndDependencies.put(layer, curDependencies);
                    }
                    curDependencies.add(dependency.getDependencies());
                }
            }
            calculateLayers();
        }
    }

    public VmsAvailableLayers getAvailableLayers() {
        VmsAvailableLayers vmsAvailableLayers;
        synchronized (this.mLock) {
            vmsAvailableLayers = new VmsAvailableLayers(this.mAvailableAssociatedLayers, this.mSeq);
        }
        return vmsAvailableLayers;
    }

    private void reset() {
        synchronized (this.mLock) {
            this.mPotentialLayersAndDependencies.clear();
            this.mPotentialLayersAndPublishers.clear();
            this.mAvailableAssociatedLayers = Collections.EMPTY_SET;
            this.mUnavailableAssociatedLayers = Collections.EMPTY_SET;
            this.mSeq++;
        }
    }

    private void calculateLayers() {
        synchronized (this.mLock) {
            final Set<VmsLayer> availableLayersSet = new HashSet<>();
            Set<VmsLayer> cyclicAvoidanceAuxiliarySet = new HashSet<>();
            for (VmsLayer layer : this.mPotentialLayersAndDependencies.keySet()) {
                addLayerToAvailabilityCalculationLocked(layer, availableLayersSet, cyclicAvoidanceAuxiliarySet);
            }
            this.mAvailableAssociatedLayers = Collections.unmodifiableSet((Set) availableLayersSet.stream().map(new Function() { // from class: com.android.car.-$$Lambda$VmsLayersAvailability$kK9fVtlkV2qHr__vOmhp-DNWRzM
                @Override // java.util.function.Function
                public final Object apply(Object obj) {
                    return VmsLayersAvailability.this.lambda$calculateLayers$0$VmsLayersAvailability((VmsLayer) obj);
                }
            }).collect(Collectors.toSet()));
            this.mUnavailableAssociatedLayers = Collections.unmodifiableSet((Set) this.mPotentialLayersAndDependencies.keySet().stream().filter(new Predicate() { // from class: com.android.car.-$$Lambda$VmsLayersAvailability$IswgpWbrwp22Ep0ME7X_OzQZ1Yw
                @Override // java.util.function.Predicate
                public final boolean test(Object obj) {
                    return VmsLayersAvailability.lambda$calculateLayers$1(availableLayersSet, (VmsLayer) obj);
                }
            }).map(new Function() { // from class: com.android.car.-$$Lambda$VmsLayersAvailability$3eoR_oQql0pTwQF-l0KYinIaX6I
                @Override // java.util.function.Function
                public final Object apply(Object obj) {
                    return VmsLayersAvailability.this.lambda$calculateLayers$2$VmsLayersAvailability((VmsLayer) obj);
                }
            }).collect(Collectors.toSet()));
        }
    }

    public /* synthetic */ VmsAssociatedLayer lambda$calculateLayers$0$VmsLayersAvailability(VmsLayer l) {
        return new VmsAssociatedLayer(l, this.mPotentialLayersAndPublishers.get(l));
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ boolean lambda$calculateLayers$1(Set availableLayersSet, VmsLayer l) {
        return !availableLayersSet.contains(l);
    }

    public /* synthetic */ VmsAssociatedLayer lambda$calculateLayers$2$VmsLayersAvailability(VmsLayer l) {
        return new VmsAssociatedLayer(l, this.mPotentialLayersAndPublishers.get(l));
    }

    @GuardedBy({"mLock"})
    private void addLayerToAvailabilityCalculationLocked(VmsLayer layer, Set<VmsLayer> currentAvailableLayers, Set<VmsLayer> cyclicAvoidanceSet) {
        if (currentAvailableLayers.contains(layer) || !this.mPotentialLayersAndDependencies.containsKey(layer)) {
            return;
        }
        if (cyclicAvoidanceSet.contains(layer)) {
            Slog.e(TAG, "Detected a cyclic dependency: " + cyclicAvoidanceSet + " -> " + layer);
            return;
        }
        for (Set<VmsLayer> dependencies : this.mPotentialLayersAndDependencies.get(layer)) {
            if (dependencies == null || dependencies.isEmpty()) {
                currentAvailableLayers.add(layer);
                return;
            }
            cyclicAvoidanceSet.add(layer);
            boolean isSupported = true;
            Iterator<VmsLayer> it = dependencies.iterator();
            while (true) {
                if (!it.hasNext()) {
                    break;
                }
                VmsLayer dependency = it.next();
                addLayerToAvailabilityCalculationLocked(dependency, currentAvailableLayers, cyclicAvoidanceSet);
                if (!currentAvailableLayers.contains(dependency)) {
                    isSupported = false;
                    break;
                }
            }
            cyclicAvoidanceSet.remove(layer);
            if (isSupported) {
                currentAvailableLayers.add(layer);
                return;
            }
        }
    }
}
