package com.android.settingslib.media;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.Toast;
import androidx.mediarouter.media.MediaRouter;
import com.android.settingslib.R;
import com.android.settingslib.bluetooth.BluetoothUtils;
/* loaded from: classes3.dex */
public class InfoMediaDevice extends MediaDevice {
    private static final String TAG = "InfoMediaDevice";
    private MediaRouter.RouteInfo mRouteInfo;

    /* JADX INFO: Access modifiers changed from: package-private */
    public InfoMediaDevice(Context context, MediaRouter.RouteInfo info) {
        super(context, 2);
        this.mRouteInfo = info;
        initDeviceRecord();
    }

    @Override // com.android.settingslib.media.MediaDevice
    public String getName() {
        return this.mRouteInfo.getName();
    }

    @Override // com.android.settingslib.media.MediaDevice
    public String getSummary() {
        return null;
    }

    @Override // com.android.settingslib.media.MediaDevice
    public Drawable getIcon() {
        return BluetoothUtils.buildBtRainbowDrawable(this.mContext, this.mContext.getDrawable(R.drawable.ic_media_device), getId().hashCode());
    }

    @Override // com.android.settingslib.media.MediaDevice
    public String getId() {
        return MediaDeviceUtils.getId(this.mRouteInfo);
    }

    @Override // com.android.settingslib.media.MediaDevice
    public boolean connect() {
        setConnectedRecord();
        Toast.makeText(this.mContext, "This is cast device !", 0).show();
        return false;
    }

    @Override // com.android.settingslib.media.MediaDevice
    public void disconnect() {
    }

    @Override // com.android.settingslib.media.MediaDevice
    public boolean isConnected() {
        return true;
    }
}
