package com.android.car.stats;

import android.car.vms.VmsLayer;
import com.android.internal.annotations.GuardedBy;
/* JADX INFO: Access modifiers changed from: package-private */
/* loaded from: classes3.dex */
public class VmsClientStats {
    @GuardedBy({"mLock"})
    private long mDroppedBytes;
    @GuardedBy({"mLock"})
    private long mDroppedPackets;
    private final int mLayerChannel;
    private final int mLayerType;
    private final int mLayerVersion;
    private final Object mLock = new Object();
    @GuardedBy({"mLock"})
    private long mRxBytes;
    @GuardedBy({"mLock"})
    private long mRxPackets;
    @GuardedBy({"mLock"})
    private long mTxBytes;
    @GuardedBy({"mLock"})
    private long mTxPackets;
    private final int mUid;

    /* JADX INFO: Access modifiers changed from: package-private */
    public VmsClientStats(int uid, VmsLayer layer) {
        this.mUid = uid;
        this.mLayerType = layer.getType();
        this.mLayerChannel = layer.getSubtype();
        this.mLayerVersion = layer.getVersion();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public VmsClientStats(VmsClientStats other) {
        synchronized (other.mLock) {
            this.mUid = other.mUid;
            this.mLayerType = other.mLayerType;
            this.mLayerChannel = other.mLayerChannel;
            this.mLayerVersion = other.mLayerVersion;
            this.mTxBytes = other.mTxBytes;
            this.mTxPackets = other.mTxPackets;
            this.mRxBytes = other.mRxBytes;
            this.mRxPackets = other.mRxPackets;
            this.mDroppedBytes = other.mDroppedBytes;
            this.mDroppedPackets = other.mDroppedPackets;
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void packetSent(long size) {
        synchronized (this.mLock) {
            this.mTxBytes += size;
            this.mTxPackets++;
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void packetReceived(long size) {
        synchronized (this.mLock) {
            this.mRxBytes += size;
            this.mRxPackets++;
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void packetDropped(long size) {
        synchronized (this.mLock) {
            this.mDroppedBytes += size;
            this.mDroppedPackets++;
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public int getUid() {
        return this.mUid;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public int getLayerType() {
        return this.mLayerType;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public int getLayerChannel() {
        return this.mLayerChannel;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public int getLayerVersion() {
        return this.mLayerVersion;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public long getTxBytes() {
        long j;
        synchronized (this.mLock) {
            j = this.mTxBytes;
        }
        return j;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public long getTxPackets() {
        long j;
        synchronized (this.mLock) {
            j = this.mTxPackets;
        }
        return j;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public long getRxBytes() {
        long j;
        synchronized (this.mLock) {
            j = this.mRxBytes;
        }
        return j;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public long getRxPackets() {
        long j;
        synchronized (this.mLock) {
            j = this.mRxPackets;
        }
        return j;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public long getDroppedBytes() {
        long j;
        synchronized (this.mLock) {
            j = this.mDroppedBytes;
        }
        return j;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public long getDroppedPackets() {
        long j;
        synchronized (this.mLock) {
            j = this.mDroppedPackets;
        }
        return j;
    }
}
