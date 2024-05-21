package com.android.car.storagemonitoring;

import android.car.storagemonitoring.UidIoRecord;
import android.util.Slog;
import android.util.SparseArray;
import com.android.car.CarLog;
import com.android.internal.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.StringTokenizer;
/* loaded from: classes3.dex */
public class ProcfsUidIoStatsProvider implements UidIoStatsProvider {
    private static Path DEFAULT_STATS_FILE = new File("/proc/uid_io/stats").toPath();
    private final Path mStatsFile;

    public ProcfsUidIoStatsProvider() {
        this(DEFAULT_STATS_FILE);
    }

    @VisibleForTesting
    ProcfsUidIoStatsProvider(Path statsFile) {
        this.mStatsFile = (Path) Objects.requireNonNull(statsFile);
    }

    @Override // com.android.car.storagemonitoring.UidIoStatsProvider
    public SparseArray<UidIoRecord> load() {
        SparseArray<UidIoRecord> result = new SparseArray<>();
        try {
            List<String> lines = Files.readAllLines(this.mStatsFile);
            for (String line : lines) {
                StringTokenizer tokenizer = new StringTokenizer(line);
                if (tokenizer.countTokens() != 11) {
                    Slog.w(CarLog.TAG_STORAGE, "malformed I/O stats entry: " + line);
                    return null;
                }
                try {
                    int uid = Integer.valueOf(tokenizer.nextToken()).intValue();
                    long foreground_rchar = Long.valueOf(tokenizer.nextToken()).longValue();
                    long foreground_wchar = Long.valueOf(tokenizer.nextToken()).longValue();
                    long foreground_read_bytes = Long.valueOf(tokenizer.nextToken()).longValue();
                    long foreground_write_bytes = Long.valueOf(tokenizer.nextToken()).longValue();
                    long background_rchar = Long.valueOf(tokenizer.nextToken()).longValue();
                    long background_wchar = Long.valueOf(tokenizer.nextToken()).longValue();
                    long background_read_bytes = Long.valueOf(tokenizer.nextToken()).longValue();
                    long background_write_bytes = Long.valueOf(tokenizer.nextToken()).longValue();
                    long foreground_fsync = Long.valueOf(tokenizer.nextToken()).longValue();
                    long background_fsync = Long.valueOf(tokenizer.nextToken()).longValue();
                    result.append(uid, new UidIoRecord(uid, foreground_rchar, foreground_wchar, foreground_read_bytes, foreground_write_bytes, foreground_fsync, background_rchar, background_wchar, background_read_bytes, background_write_bytes, background_fsync));
                } catch (NumberFormatException e) {
                    Slog.w(CarLog.TAG_STORAGE, "malformed I/O stats entry: " + line, e);
                    return null;
                }
            }
            return result;
        } catch (IOException e2) {
            Slog.w(CarLog.TAG_STORAGE, "can't read I/O stats from " + this.mStatsFile, e2);
            return null;
        }
    }
}
