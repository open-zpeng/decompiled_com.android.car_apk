package com.android.settingslib.deviceinfo;

import android.app.usage.ExternalStorageStats;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.VolumeInfo;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseLongArray;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
/* loaded from: classes3.dex */
public class StorageMeasurement {
    private static final String TAG = "StorageMeasurement";
    private final Context mContext;
    private WeakReference<MeasurementReceiver> mReceiver;
    private final VolumeInfo mSharedVolume;
    private final StorageStatsManager mStats;
    private final UserManager mUser;
    private final VolumeInfo mVolume;

    /* loaded from: classes3.dex */
    public interface MeasurementReceiver {
        void onDetailsChanged(MeasurementDetails measurementDetails);
    }

    /* loaded from: classes3.dex */
    public static class MeasurementDetails {
        public long availSize;
        public long cacheSize;
        public long totalSize;
        public SparseLongArray usersSize = new SparseLongArray();
        public SparseLongArray appsSize = new SparseLongArray();
        public SparseArray<HashMap<String, Long>> mediaSize = new SparseArray<>();
        public SparseLongArray miscSize = new SparseLongArray();

        public String toString() {
            return "MeasurementDetails: [totalSize: " + this.totalSize + " availSize: " + this.availSize + " cacheSize: " + this.cacheSize + " mediaSize: " + this.mediaSize + " miscSize: " + this.miscSize + "usersSize: " + this.usersSize + "]";
        }
    }

    public StorageMeasurement(Context context, VolumeInfo volume, VolumeInfo sharedVolume) {
        this.mContext = context.getApplicationContext();
        this.mUser = (UserManager) this.mContext.getSystemService(UserManager.class);
        this.mStats = (StorageStatsManager) this.mContext.getSystemService(StorageStatsManager.class);
        this.mVolume = volume;
        this.mSharedVolume = sharedVolume;
    }

    public void setReceiver(MeasurementReceiver receiver) {
        WeakReference<MeasurementReceiver> weakReference = this.mReceiver;
        if (weakReference == null || weakReference.get() == null) {
            this.mReceiver = new WeakReference<>(receiver);
        }
    }

    public void forceMeasure() {
        measure();
    }

    public void measure() {
        new MeasureTask().execute(new Void[0]);
    }

    public void onDestroy() {
        this.mReceiver = null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class MeasureTask extends AsyncTask<Void, Void, MeasurementDetails> {
        private MeasureTask() {
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.os.AsyncTask
        public MeasurementDetails doInBackground(Void... params) {
            return StorageMeasurement.this.measureExactStorage();
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.os.AsyncTask
        public void onPostExecute(MeasurementDetails result) {
            MeasurementReceiver receiver = StorageMeasurement.this.mReceiver != null ? (MeasurementReceiver) StorageMeasurement.this.mReceiver.get() : null;
            if (receiver != null) {
                receiver.onDetailsChanged(result);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public MeasurementDetails measureExactStorage() {
        long finishTotal;
        List<UserInfo> users = this.mUser.getUsers();
        long miscBytes = SystemClock.elapsedRealtime();
        MeasurementDetails details = new MeasurementDetails();
        VolumeInfo volumeInfo = this.mVolume;
        if (volumeInfo == null) {
            return details;
        }
        if (volumeInfo.getType() != 0 && this.mVolume.getType() != 5) {
            try {
                details.totalSize = this.mStats.getTotalBytes(this.mVolume.fsUuid);
                details.availSize = this.mStats.getFreeBytes(this.mVolume.fsUuid);
                long finishTotal2 = SystemClock.elapsedRealtime();
                Log.d(TAG, "Measured total storage in " + (finishTotal2 - miscBytes) + "ms");
                VolumeInfo volumeInfo2 = this.mSharedVolume;
                if (volumeInfo2 != null && volumeInfo2.isMountedReadable()) {
                    for (UserInfo user : users) {
                        HashMap<String, Long> mediaMap = new HashMap<>();
                        details.mediaSize.put(user.id, mediaMap);
                        try {
                            ExternalStorageStats stats = this.mStats.queryExternalStatsForUser(this.mSharedVolume.fsUuid, UserHandle.of(user.id));
                            SparseLongArray sparseLongArray = details.usersSize;
                            int i = user.id;
                            long start = miscBytes;
                            long start2 = stats.getTotalBytes();
                            addValue(sparseLongArray, i, start2);
                            mediaMap.put(Environment.DIRECTORY_MUSIC, Long.valueOf(stats.getAudioBytes()));
                            mediaMap.put(Environment.DIRECTORY_MOVIES, Long.valueOf(stats.getVideoBytes()));
                            mediaMap.put(Environment.DIRECTORY_PICTURES, Long.valueOf(stats.getImageBytes()));
                            addValue(details.miscSize, user.id, ((stats.getTotalBytes() - stats.getAudioBytes()) - stats.getVideoBytes()) - stats.getImageBytes());
                            miscBytes = start;
                        } catch (IOException e) {
                            long start3 = miscBytes;
                            Log.w(TAG, e);
                            miscBytes = start3;
                        }
                    }
                }
                long finishShared = SystemClock.elapsedRealtime();
                Log.d(TAG, "Measured shared storage in " + (finishShared - finishTotal2) + "ms");
                if (this.mVolume.getType() == 1 && this.mVolume.isMountedReadable()) {
                    for (UserInfo user2 : users) {
                        try {
                            StorageStats stats2 = this.mStats.queryStatsForUser(this.mVolume.fsUuid, UserHandle.of(user2.id));
                            if (user2.id != UserHandle.myUserId()) {
                                finishTotal = finishTotal2;
                            } else {
                                finishTotal = finishTotal2;
                                addValue(details.usersSize, user2.id, stats2.getCodeBytes());
                            }
                            addValue(details.usersSize, user2.id, stats2.getDataBytes());
                            addValue(details.appsSize, user2.id, stats2.getCodeBytes() + stats2.getDataBytes());
                            details.cacheSize += stats2.getCacheBytes();
                            finishTotal2 = finishTotal;
                        } catch (IOException e2) {
                            Log.w(TAG, e2);
                            finishTotal2 = finishTotal2;
                        }
                    }
                }
                long finishPrivate = SystemClock.elapsedRealtime();
                Log.d(TAG, "Measured private storage in " + (finishPrivate - finishShared) + "ms");
                return details;
            } catch (IOException e3) {
                Log.w(TAG, e3);
                return details;
            }
        }
        details.totalSize = this.mVolume.getPath().getTotalSpace();
        details.availSize = this.mVolume.getPath().getUsableSpace();
        return details;
    }

    private static void addValue(SparseLongArray array, int key, long value) {
        array.put(key, array.get(key) + value);
    }
}
