package com.android.settingslib.applications;

import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import androidx.annotation.VisibleForTesting;
import java.io.IOException;
/* loaded from: classes3.dex */
public class StorageStatsSource {
    private StorageStatsManager mStorageStatsManager;

    /* loaded from: classes3.dex */
    public interface AppStorageStats {
        long getCacheBytes();

        long getCodeBytes();

        long getDataBytes();

        long getTotalBytes();
    }

    public StorageStatsSource(Context context) {
        this.mStorageStatsManager = (StorageStatsManager) context.getSystemService(StorageStatsManager.class);
    }

    public ExternalStorageStats getExternalStorageStats(String volumeUuid, UserHandle user) throws IOException {
        return new ExternalStorageStats(this.mStorageStatsManager.queryExternalStatsForUser(volumeUuid, user));
    }

    public AppStorageStats getStatsForUid(String volumeUuid, int uid) throws IOException {
        return new AppStorageStatsImpl(this.mStorageStatsManager.queryStatsForUid(volumeUuid, uid));
    }

    public AppStorageStats getStatsForPackage(String volumeUuid, String packageName, UserHandle user) throws PackageManager.NameNotFoundException, IOException {
        return new AppStorageStatsImpl(this.mStorageStatsManager.queryStatsForPackage(volumeUuid, packageName, user));
    }

    public long getCacheQuotaBytes(String volumeUuid, int uid) {
        return this.mStorageStatsManager.getCacheQuotaBytes(volumeUuid, uid);
    }

    /* loaded from: classes3.dex */
    public static class ExternalStorageStats {
        public long appBytes;
        public long audioBytes;
        public long imageBytes;
        public long totalBytes;
        public long videoBytes;

        @VisibleForTesting
        public ExternalStorageStats(long totalBytes, long audioBytes, long videoBytes, long imageBytes, long appBytes) {
            this.totalBytes = totalBytes;
            this.audioBytes = audioBytes;
            this.videoBytes = videoBytes;
            this.imageBytes = imageBytes;
            this.appBytes = appBytes;
        }

        public ExternalStorageStats(android.app.usage.ExternalStorageStats stats) {
            this.totalBytes = stats.getTotalBytes();
            this.audioBytes = stats.getAudioBytes();
            this.videoBytes = stats.getVideoBytes();
            this.imageBytes = stats.getImageBytes();
            this.appBytes = stats.getAppBytes();
        }
    }

    /* loaded from: classes3.dex */
    public static class AppStorageStatsImpl implements AppStorageStats {
        private StorageStats mStats;

        public AppStorageStatsImpl(StorageStats stats) {
            this.mStats = stats;
        }

        @Override // com.android.settingslib.applications.StorageStatsSource.AppStorageStats
        public long getCodeBytes() {
            return this.mStats.getCodeBytes();
        }

        @Override // com.android.settingslib.applications.StorageStatsSource.AppStorageStats
        public long getDataBytes() {
            return this.mStats.getDataBytes();
        }

        @Override // com.android.settingslib.applications.StorageStatsSource.AppStorageStats
        public long getCacheBytes() {
            return this.mStats.getCacheBytes();
        }

        @Override // com.android.settingslib.applications.StorageStatsSource.AppStorageStats
        public long getTotalBytes() {
            return this.mStats.getAppBytes() + this.mStats.getDataBytes();
        }
    }
}
