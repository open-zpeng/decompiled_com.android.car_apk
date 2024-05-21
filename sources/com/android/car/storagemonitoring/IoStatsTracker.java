package com.android.car.storagemonitoring;

import android.car.storagemonitoring.IoStatsEntry;
import android.car.storagemonitoring.UidIoRecord;
import android.util.SparseArray;
import com.android.car.SparseArrayStream;
import com.android.car.procfsinspector.ProcessInfo;
import com.android.car.systeminterface.SystemStateInterface;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
/* loaded from: classes3.dex */
public class IoStatsTracker {
    private SparseArray<IoStatsEntry> mCurrentSample;
    private final long mSampleWindowMs;
    private final SystemStateInterface mSystemStateInterface;
    private SparseArray<IoStatsEntry> mTotal;

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public abstract class Lazy<T> {
        protected Optional<T> mLazy;

        protected abstract T supply();

        private Lazy() {
            this.mLazy = Optional.empty();
        }

        public synchronized T get() {
            if (!this.mLazy.isPresent()) {
                this.mLazy = Optional.of(supply());
            }
            return this.mLazy.get();
        }
    }

    public IoStatsTracker(List<IoStatsEntry> initialValue, long sampleWindowMs, SystemStateInterface systemStateInterface) {
        this.mTotal = new SparseArray<>(initialValue.size());
        initialValue.forEach(new Consumer() { // from class: com.android.car.storagemonitoring.-$$Lambda$IoStatsTracker$dM-lQcPLyMC4Tz_tgo9QUrwd-Yg
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                IoStatsTracker.this.lambda$new$0$IoStatsTracker((IoStatsEntry) obj);
            }
        });
        this.mCurrentSample = this.mTotal.clone();
        this.mSampleWindowMs = sampleWindowMs;
        this.mSystemStateInterface = systemStateInterface;
    }

    public /* synthetic */ void lambda$new$0$IoStatsTracker(IoStatsEntry uidIoStats) {
        this.mTotal.append(uidIoStats.uid, uidIoStats);
    }

    public synchronized void update(SparseArray<UidIoRecord> newMetrics) {
        final Lazy<List<ProcessInfo>> processTable = new Lazy<List<ProcessInfo>>() { // from class: com.android.car.storagemonitoring.IoStatsTracker.1
            /* JADX INFO: Access modifiers changed from: protected */
            @Override // com.android.car.storagemonitoring.IoStatsTracker.Lazy
            public List<ProcessInfo> supply() {
                return IoStatsTracker.this.mSystemStateInterface.getRunningProcesses();
            }
        };
        final SparseArray<IoStatsEntry> newSample = new SparseArray<>();
        final SparseArray<IoStatsEntry> newTotal = new SparseArray<>();
        SparseArrayStream.valueStream(newMetrics).forEach(new Consumer() { // from class: com.android.car.storagemonitoring.-$$Lambda$IoStatsTracker$6SyXoNzwUCFonyT2dvBIzkw5i1k
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                IoStatsTracker.this.lambda$update$2$IoStatsTracker(processTable, newSample, newTotal, (UidIoRecord) obj);
            }
        });
        this.mCurrentSample = newSample;
        this.mTotal = newTotal;
    }

    public /* synthetic */ void lambda$update$2$IoStatsTracker(Lazy processTable, SparseArray newSample, SparseArray newTotal, UidIoRecord newRecord) {
        final int uid = newRecord.uid;
        IoStatsEntry oldRecord = this.mTotal.get(uid);
        IoStatsEntry newStats = null;
        if (oldRecord == null) {
            newStats = new IoStatsEntry(newRecord, this.mSampleWindowMs);
        } else if (oldRecord.representsSameMetrics(newRecord)) {
            if (((List) processTable.get()).stream().anyMatch(new Predicate() { // from class: com.android.car.storagemonitoring.-$$Lambda$IoStatsTracker$ffetSYj-ja44vra_OEt-ULDMGQE
                @Override // java.util.function.Predicate
                public final boolean test(Object obj) {
                    return IoStatsTracker.lambda$update$1(uid, (ProcessInfo) obj);
                }
            })) {
                newStats = new IoStatsEntry(newRecord.delta(oldRecord), oldRecord.runtimeMillis + this.mSampleWindowMs);
            }
        } else {
            newStats = new IoStatsEntry(newRecord.delta(oldRecord), oldRecord.runtimeMillis + this.mSampleWindowMs);
        }
        if (newStats != null) {
            newSample.put(uid, newStats);
            newTotal.append(uid, new IoStatsEntry(newRecord, newStats.runtimeMillis));
            return;
        }
        newTotal.append(uid, oldRecord);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ boolean lambda$update$1(int uid, ProcessInfo pi) {
        return pi.uid == uid;
    }

    public synchronized SparseArray<IoStatsEntry> getTotal() {
        return this.mTotal;
    }

    public synchronized SparseArray<IoStatsEntry> getCurrentSample() {
        return this.mCurrentSample;
    }
}
