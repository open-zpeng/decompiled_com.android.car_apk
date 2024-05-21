package com.android.car.stats;

import android.car.vms.VmsLayer;
import android.util.ArrayMap;
import android.util.StatsLog;
import com.android.internal.annotations.GuardedBy;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
/* loaded from: classes3.dex */
public class VmsClientLogger {
    private final String mPackageName;
    private final int mUid;
    private final Object mLock = new Object();
    @GuardedBy({"mLock"})
    private Map<Integer, AtomicLong> mConnectionStateCounters = new ArrayMap();
    @GuardedBy({"mLock"})
    private final Map<VmsLayer, VmsClientStats> mLayerStats = new ArrayMap();

    /* loaded from: classes3.dex */
    public static class ConnectionState {
        public static final int CONNECTED = 2;
        public static final int CONNECTING = 1;
        public static final int CONNECTION_ERROR = 5;
        public static final int DISCONNECTED = 3;
        public static final int TERMINATED = 4;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public VmsClientLogger(int clientUid, String clientPackage) {
        this.mUid = clientUid;
        this.mPackageName = clientPackage != null ? clientPackage : "";
    }

    public int getUid() {
        return this.mUid;
    }

    public String getPackageName() {
        return this.mPackageName;
    }

    public void logConnectionState(int connectionState) {
        AtomicLong counter;
        StatsLog.write(230, this.mUid, this.mPackageName, connectionState);
        synchronized (this.mLock) {
            counter = this.mConnectionStateCounters.computeIfAbsent(Integer.valueOf(connectionState), new Function() { // from class: com.android.car.stats.-$$Lambda$VmsClientLogger$ICC505kxiz154wELg0PHgXRuYKg
                @Override // java.util.function.Function
                public final Object apply(Object obj) {
                    return VmsClientLogger.lambda$logConnectionState$0((Integer) obj);
                }
            });
        }
        counter.incrementAndGet();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ AtomicLong lambda$logConnectionState$0(Integer ignored) {
        return new AtomicLong();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public long getConnectionStateCount(int connectionState) {
        AtomicLong counter;
        synchronized (this.mLock) {
            counter = this.mConnectionStateCounters.get(Integer.valueOf(connectionState));
        }
        if (counter == null) {
            return 0L;
        }
        return counter.get();
    }

    public void logPacketSent(VmsLayer layer, long size) {
        getLayerEntry(layer).packetSent(size);
    }

    public void logPacketReceived(VmsLayer layer, long size) {
        getLayerEntry(layer).packetReceived(size);
    }

    public void logPacketDropped(VmsLayer layer, long size) {
        getLayerEntry(layer).packetDropped(size);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public Collection<VmsClientStats> getLayerEntries() {
        Collection<VmsClientStats> collection;
        synchronized (this.mLock) {
            collection = (Collection) this.mLayerStats.values().stream().map(new Function() { // from class: com.android.car.stats.-$$Lambda$wWnXWH2SoxrmJII0VT8Cp7B8UpI
                @Override // java.util.function.Function
                public final Object apply(Object obj) {
                    return new VmsClientStats((VmsClientStats) obj);
                }
            }).collect(Collectors.toList());
        }
        return collection;
    }

    private VmsClientStats getLayerEntry(final VmsLayer layer) {
        VmsClientStats computeIfAbsent;
        synchronized (this.mLock) {
            computeIfAbsent = this.mLayerStats.computeIfAbsent(layer, new Function() { // from class: com.android.car.stats.-$$Lambda$VmsClientLogger$8XrWPYGRskOQh7jC_oC9e7jw-9o
                @Override // java.util.function.Function
                public final Object apply(Object obj) {
                    return VmsClientLogger.this.lambda$getLayerEntry$1$VmsClientLogger(layer, (VmsLayer) obj);
                }
            });
        }
        return computeIfAbsent;
    }

    public /* synthetic */ VmsClientStats lambda$getLayerEntry$1$VmsClientLogger(VmsLayer layer, VmsLayer k) {
        return new VmsClientStats(this.mUid, layer);
    }
}
