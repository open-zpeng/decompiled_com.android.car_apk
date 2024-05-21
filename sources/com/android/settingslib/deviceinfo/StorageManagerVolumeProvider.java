package com.android.settingslib.deviceinfo;

import android.app.usage.StorageStatsManager;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import java.io.IOException;
import java.util.List;
/* loaded from: classes3.dex */
public class StorageManagerVolumeProvider implements StorageVolumeProvider {
    private StorageManager mStorageManager;

    public StorageManagerVolumeProvider(StorageManager sm) {
        this.mStorageManager = sm;
    }

    @Override // com.android.settingslib.deviceinfo.StorageVolumeProvider
    public long getPrimaryStorageSize() {
        return this.mStorageManager.getPrimaryStorageSize();
    }

    @Override // com.android.settingslib.deviceinfo.StorageVolumeProvider
    public List<VolumeInfo> getVolumes() {
        return this.mStorageManager.getVolumes();
    }

    @Override // com.android.settingslib.deviceinfo.StorageVolumeProvider
    public VolumeInfo findEmulatedForPrivate(VolumeInfo privateVolume) {
        return this.mStorageManager.findEmulatedForPrivate(privateVolume);
    }

    @Override // com.android.settingslib.deviceinfo.StorageVolumeProvider
    public long getTotalBytes(StorageStatsManager stats, VolumeInfo volume) throws IOException {
        return stats.getTotalBytes(volume.getFsUuid());
    }

    @Override // com.android.settingslib.deviceinfo.StorageVolumeProvider
    public long getFreeBytes(StorageStatsManager stats, VolumeInfo volume) throws IOException {
        return stats.getFreeBytes(volume.getFsUuid());
    }
}
