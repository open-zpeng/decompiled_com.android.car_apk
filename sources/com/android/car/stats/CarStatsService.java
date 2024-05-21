package com.android.car.stats;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.StatsLogEventWrapper;
import android.os.SystemClock;
import android.util.ArrayMap;
import android.util.Slog;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.car.ICarStatsService;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
/* loaded from: classes3.dex */
public class CarStatsService extends ICarStatsService.Stub {
    private static final boolean DEBUG = false;
    private static final String TAG = "CarStatsService";
    private static final String VMS_CLIENT_STATS_DUMPSYS_HEADER = "uid,layerType,layerChannel,layerVersion,txBytes,txPackets,rxBytes,rxPackets,droppedBytes,droppedPackets";
    private static final String VMS_CONNECTION_STATS_DUMPSYS_HEADER = "uid,packageName,attempts,connected,disconnected,terminated,errors";
    private final Context mContext;
    private final PackageManager mPackageManager;
    @GuardedBy({"mVmsClientStats"})
    private final Map<Integer, VmsClientLogger> mVmsClientStats = new ArrayMap();
    private static final Function<VmsClientLogger, String> VMS_CONNECTION_STATS_DUMPSYS_FORMAT = new Function() { // from class: com.android.car.stats.-$$Lambda$CarStatsService$SQru8gltIw7fRY4QCKc1quKTXM4
        @Override // java.util.function.Function
        public final Object apply(Object obj) {
            String format;
            format = String.format(Locale.US, "%d,%s,%d,%d,%d,%d,%d", Integer.valueOf(r1.getUid()), r1.getPackageName(), Long.valueOf(r1.getConnectionStateCount(1)), Long.valueOf(r1.getConnectionStateCount(2)), Long.valueOf(r1.getConnectionStateCount(3)), Long.valueOf(r1.getConnectionStateCount(4)), Long.valueOf(((VmsClientLogger) obj).getConnectionStateCount(5)));
            return format;
        }
    };
    private static final Function<VmsClientStats, String> VMS_CLIENT_STATS_DUMPSYS_FORMAT = new Function() { // from class: com.android.car.stats.-$$Lambda$CarStatsService$OLzDBKTN8MWqmffWR8KiuWo1lQ8
        @Override // java.util.function.Function
        public final Object apply(Object obj) {
            String format;
            format = String.format("%d,%d,%d,%d,%d,%d,%d,%d,%d,%d", Integer.valueOf(r1.getUid()), Integer.valueOf(r1.getLayerType()), Integer.valueOf(r1.getLayerChannel()), Integer.valueOf(r1.getLayerVersion()), Long.valueOf(r1.getTxBytes()), Long.valueOf(r1.getTxPackets()), Long.valueOf(r1.getRxBytes()), Long.valueOf(r1.getRxPackets()), Long.valueOf(r1.getDroppedBytes()), Long.valueOf(((VmsClientStats) obj).getDroppedPackets()));
            return format;
        }
    };
    private static final Comparator<VmsClientStats> VMS_CLIENT_STATS_ORDER = Comparator.comparingInt(new ToIntFunction() { // from class: com.android.car.stats.-$$Lambda$NzQHmHcmCXpZGdMwzFRNdZAA_b8
        @Override // java.util.function.ToIntFunction
        public final int applyAsInt(Object obj) {
            return ((VmsClientStats) obj).getUid();
        }
    }).thenComparingInt(new ToIntFunction() { // from class: com.android.car.stats.-$$Lambda$DMw-CAZLiLmN9p9vsgV1s4qTACU
        @Override // java.util.function.ToIntFunction
        public final int applyAsInt(Object obj) {
            return ((VmsClientStats) obj).getLayerType();
        }
    }).thenComparingInt(new ToIntFunction() { // from class: com.android.car.stats.-$$Lambda$Ae3HZE7DkCQiACxOME57hDQoAhg
        @Override // java.util.function.ToIntFunction
        public final int applyAsInt(Object obj) {
            return ((VmsClientStats) obj).getLayerChannel();
        }
    }).thenComparingInt(new ToIntFunction() { // from class: com.android.car.stats.-$$Lambda$ZC55c5KE7QSb5PvNh2uMLCvlBSA
        @Override // java.util.function.ToIntFunction
        public final int applyAsInt(Object obj) {
            return ((VmsClientStats) obj).getLayerVersion();
        }
    });

    public CarStatsService(Context context) {
        this.mContext = context;
        this.mPackageManager = context.getPackageManager();
    }

    public VmsClientLogger getVmsClientLogger(int clientUid) {
        VmsClientLogger computeIfAbsent;
        synchronized (this.mVmsClientStats) {
            computeIfAbsent = this.mVmsClientStats.computeIfAbsent(Integer.valueOf(clientUid), new Function() { // from class: com.android.car.stats.-$$Lambda$CarStatsService$pbcYhCm0cjrNpZorpMC8RQXwEjk
                @Override // java.util.function.Function
                public final Object apply(Object obj) {
                    return CarStatsService.this.lambda$getVmsClientLogger$2$CarStatsService((Integer) obj);
                }
            });
        }
        return computeIfAbsent;
    }

    public /* synthetic */ VmsClientLogger lambda$getVmsClientLogger$2$CarStatsService(Integer uid) {
        String packageName = this.mPackageManager.getNameForUid(uid.intValue());
        return new VmsClientLogger(uid.intValue(), packageName);
    }

    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        List<String> flags = Arrays.asList(args);
        if (args.length == 0 || flags.contains("--vms-client")) {
            dumpVmsStats(writer);
        }
    }

    public StatsLogEventWrapper[] pullData(int tagId) {
        this.mContext.enforceCallingPermission("android.permission.DUMP", null);
        if (tagId != 10065) {
            Slog.w(TAG, "Unexpected tagId: " + tagId);
            return null;
        }
        List<StatsLogEventWrapper> ret = new ArrayList<>();
        long elapsedNanos = SystemClock.elapsedRealtimeNanos();
        long wallClockNanos = SystemClock.currentTimeMicro() * 1000;
        pullVmsClientStats(tagId, elapsedNanos, wallClockNanos, ret);
        return (StatsLogEventWrapper[]) ret.toArray(new StatsLogEventWrapper[0]);
    }

    private void dumpVmsStats(final PrintWriter writer) {
        synchronized (this.mVmsClientStats) {
            writer.println(VMS_CONNECTION_STATS_DUMPSYS_HEADER);
            this.mVmsClientStats.values().stream().filter(new Predicate() { // from class: com.android.car.stats.-$$Lambda$CarStatsService$HcRH26P2gQCK4M5mAVpFjb86U1w
                @Override // java.util.function.Predicate
                public final boolean test(Object obj) {
                    return CarStatsService.lambda$dumpVmsStats$3((VmsClientLogger) obj);
                }
            }).sorted(Comparator.comparingInt(new ToIntFunction() { // from class: com.android.car.stats.-$$Lambda$elBXxx0RKD-S_mR5Y3_7D4sSrh8
                @Override // java.util.function.ToIntFunction
                public final int applyAsInt(Object obj) {
                    return ((VmsClientLogger) obj).getUid();
                }
            })).forEachOrdered(new Consumer() { // from class: com.android.car.stats.-$$Lambda$CarStatsService$PBGL1ACekHVG6Ixa1dfVBNvbzcU
                @Override // java.util.function.Consumer
                public final void accept(Object obj) {
                    writer.println(CarStatsService.VMS_CONNECTION_STATS_DUMPSYS_FORMAT.apply((VmsClientLogger) obj));
                }
            });
            writer.println();
            writer.println(VMS_CLIENT_STATS_DUMPSYS_HEADER);
            dumpVmsClientStats(new Consumer() { // from class: com.android.car.stats.-$$Lambda$CarStatsService$dQJ8BVPYBELj6O5MAba39ykyMLc
                @Override // java.util.function.Consumer
                public final void accept(Object obj) {
                    writer.println(CarStatsService.VMS_CLIENT_STATS_DUMPSYS_FORMAT.apply((VmsClientStats) obj));
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ boolean lambda$dumpVmsStats$3(VmsClientLogger entry) {
        return entry.getUid() > 0;
    }

    private void pullVmsClientStats(final int tagId, final long elapsedNanos, final long wallClockNanos, final List<StatsLogEventWrapper> pulledData) {
        dumpVmsClientStats(new Consumer() { // from class: com.android.car.stats.-$$Lambda$CarStatsService$76wZtIWvEvlGY1kyX9UQ7cDFR-Q
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                CarStatsService.lambda$pullVmsClientStats$6(tagId, elapsedNanos, wallClockNanos, pulledData, (VmsClientStats) obj);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ void lambda$pullVmsClientStats$6(int tagId, long elapsedNanos, long wallClockNanos, List pulledData, VmsClientStats entry) {
        StatsLogEventWrapper e = new StatsLogEventWrapper(tagId, elapsedNanos, wallClockNanos);
        e.writeInt(entry.getUid());
        e.writeInt(entry.getLayerType());
        e.writeInt(entry.getLayerChannel());
        e.writeInt(entry.getLayerVersion());
        e.writeLong(entry.getTxBytes());
        e.writeLong(entry.getTxPackets());
        e.writeLong(entry.getRxBytes());
        e.writeLong(entry.getRxPackets());
        e.writeLong(entry.getDroppedBytes());
        e.writeLong(entry.getDroppedPackets());
        pulledData.add(e);
    }

    private void dumpVmsClientStats(Consumer<VmsClientStats> dumpFn) {
        synchronized (this.mVmsClientStats) {
            this.mVmsClientStats.values().stream().flatMap(new Function() { // from class: com.android.car.stats.-$$Lambda$CarStatsService$fLB6jBcczXzvlUyV0FX90eEToU8
                @Override // java.util.function.Function
                public final Object apply(Object obj) {
                    Stream stream;
                    stream = ((VmsClientLogger) obj).getLayerEntries().stream();
                    return stream;
                }
            }).sorted(VMS_CLIENT_STATS_ORDER).forEachOrdered(dumpFn);
        }
    }
}
