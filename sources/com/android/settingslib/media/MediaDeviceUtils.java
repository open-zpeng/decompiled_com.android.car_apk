package com.android.settingslib.media;

import android.bluetooth.BluetoothDevice;
import androidx.mediarouter.media.MediaRouter;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
/* loaded from: classes3.dex */
public class MediaDeviceUtils {
    public static String getId(CachedBluetoothDevice cachedDevice) {
        return cachedDevice.getAddress();
    }

    public static String getId(BluetoothDevice bluetoothDevice) {
        return bluetoothDevice.getAddress();
    }

    public static String getId(MediaRouter.RouteInfo route) {
        return route.getId();
    }
}
