package com.android.settingslib.media;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
/* loaded from: classes3.dex */
public abstract class MediaDevice implements Comparable<MediaDevice> {
    private static final String TAG = "MediaDevice";
    private int mConnectedRecord;
    protected Context mContext;
    protected int mType;

    @Retention(RetentionPolicy.SOURCE)
    /* loaded from: classes3.dex */
    public @interface MediaDeviceType {
        public static final int TYPE_BLUETOOTH_DEVICE = 3;
        public static final int TYPE_CAST_DEVICE = 2;
        public static final int TYPE_PHONE_DEVICE = 1;
    }

    public abstract boolean connect();

    public abstract void disconnect();

    public abstract Drawable getIcon();

    public abstract String getId();

    public abstract String getName();

    public abstract String getSummary();

    public abstract boolean isConnected();

    /* JADX INFO: Access modifiers changed from: package-private */
    public MediaDevice(Context context, int type) {
        this.mType = type;
        this.mContext = context;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void initDeviceRecord() {
        ConnectionRecordManager.getInstance().fetchLastSelectedDevice(this.mContext);
        this.mConnectedRecord = ConnectionRecordManager.getInstance().fetchConnectionRecord(this.mContext, getId());
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void setConnectedRecord() {
        this.mConnectedRecord++;
        ConnectionRecordManager.getInstance().setConnectionRecord(this.mContext, getId(), this.mConnectedRecord);
    }

    @Override // java.lang.Comparable
    public int compareTo(MediaDevice another) {
        if (isConnected() ^ another.isConnected()) {
            return isConnected() ? -1 : 1;
        } else if (this.mType == 1) {
            return -1;
        } else {
            if (another.mType == 1) {
                return 1;
            }
            if (isCarKitDevice()) {
                return -1;
            }
            if (another.isCarKitDevice()) {
                return 1;
            }
            String lastSelectedDevice = ConnectionRecordManager.getInstance().getLastSelectedDevice();
            if (TextUtils.equals(lastSelectedDevice, getId())) {
                return -1;
            }
            if (TextUtils.equals(lastSelectedDevice, another.getId())) {
                return 1;
            }
            int i = this.mConnectedRecord;
            int i2 = another.mConnectedRecord;
            if (i != i2 && (i2 > 0 || i > 0)) {
                return another.mConnectedRecord - this.mConnectedRecord;
            }
            int i3 = this.mType;
            int i4 = another.mType;
            if (i3 == i4) {
                String s1 = getName();
                String s2 = another.getName();
                return s1.compareToIgnoreCase(s2);
            }
            return i3 - i4;
        }
    }

    protected boolean isCarKitDevice() {
        return false;
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof MediaDevice)) {
            return false;
        }
        MediaDevice otherDevice = (MediaDevice) obj;
        return otherDevice.getId().equals(getId());
    }
}
