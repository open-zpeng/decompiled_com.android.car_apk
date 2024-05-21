package com.android.car;

import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Slog;
import com.android.car.systeminterface.SystemInterface;
import com.android.car.systeminterface.TimeInterface;
import com.android.internal.annotations.VisibleForTesting;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
/* loaded from: classes3.dex */
public class UptimeTracker {
    private static long DEFAULT_SNAPSHOT_INTERVAL_MS = 18000000;
    public static final long MINIMUM_SNAPSHOT_INTERVAL_MS = 3600000;
    private Optional<Long> mHistoricalUptime;
    private long mLastRealTimeSnapshot;
    private final Object mLock;
    private TimeInterface mTimeInterface;
    private File mUptimeFile;

    public UptimeTracker(File file) {
        this(file, DEFAULT_SNAPSHOT_INTERVAL_MS);
    }

    public UptimeTracker(File file, long snapshotInterval) {
        this(file, snapshotInterval, new TimeInterface.DefaultImpl());
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public UptimeTracker(File file, long snapshotInterval, SystemInterface systemInterface) {
        this(file, snapshotInterval, systemInterface.getTimeInterface());
    }

    @VisibleForTesting
    UptimeTracker(File file, long snapshotInterval, TimeInterface timeInterface) {
        this.mLock = new Object();
        long snapshotInterval2 = Math.max(snapshotInterval, (long) MINIMUM_SNAPSHOT_INTERVAL_MS);
        this.mUptimeFile = (File) Objects.requireNonNull(file);
        this.mTimeInterface = timeInterface;
        this.mLastRealTimeSnapshot = this.mTimeInterface.getUptime(false);
        this.mHistoricalUptime = Optional.empty();
        this.mTimeInterface.scheduleAction(new Runnable() { // from class: com.android.car.-$$Lambda$UptimeTracker$xu1OYBk8ZWIueCktLcjt2G8ZNhc
            @Override // java.lang.Runnable
            public final void run() {
                UptimeTracker.this.flushSnapshot();
            }
        }, snapshotInterval2);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void onDestroy() {
        synchronized (this.mLock) {
            if (this.mTimeInterface != null) {
                this.mTimeInterface.cancelAllActions();
            }
            flushSnapshot();
            this.mTimeInterface = null;
            this.mUptimeFile = null;
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public long getTotalUptime() {
        synchronized (this.mLock) {
            if (this.mTimeInterface == null) {
                return 0L;
            }
            return getHistoricalUptimeLocked() + (this.mTimeInterface.getUptime(false) - this.mLastRealTimeSnapshot);
        }
    }

    private long getHistoricalUptimeLocked() {
        File file;
        if (!this.mHistoricalUptime.isPresent() && (file = this.mUptimeFile) != null && file.exists()) {
            try {
                JsonReader reader = new JsonReader(new FileReader(this.mUptimeFile));
                reader.beginObject();
                if (!reader.nextName().equals("uptime")) {
                    throw new IllegalArgumentException(this.mUptimeFile + " is not in a valid format");
                }
                this.mHistoricalUptime = Optional.of(Long.valueOf(reader.nextLong()));
                reader.endObject();
                reader.close();
            } catch (IOException | IllegalArgumentException e) {
                Slog.w(CarLog.TAG_SERVICE, "unable to read historical uptime data", e);
                this.mHistoricalUptime = Optional.empty();
            }
        }
        return this.mHistoricalUptime.orElse(0L).longValue();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void flushSnapshot() {
        synchronized (this.mLock) {
            if (this.mUptimeFile == null) {
                return;
            }
            try {
                long newUptime = getTotalUptime();
                this.mHistoricalUptime = Optional.of(Long.valueOf(newUptime));
                this.mLastRealTimeSnapshot = this.mTimeInterface.getUptime(false);
                JsonWriter writer = new JsonWriter(new FileWriter(this.mUptimeFile));
                writer.beginObject();
                writer.name("uptime");
                writer.value(newUptime);
                writer.endObject();
                writer.close();
            } catch (IOException e) {
                Slog.w(CarLog.TAG_SERVICE, "unable to write historical uptime data", e);
            }
        }
    }
}
