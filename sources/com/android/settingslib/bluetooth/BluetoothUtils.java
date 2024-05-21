package com.android.settingslib.bluetooth;

import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import androidx.annotation.DrawableRes;
import com.android.settingslib.R;
import com.android.settingslib.widget.AdaptiveIcon;
import com.android.settingslib.widget.AdaptiveOutlineDrawable;
import java.io.IOException;
import java.util.List;
/* loaded from: classes3.dex */
public class BluetoothUtils {
    public static final boolean D = true;
    public static final int META_INT_ERROR = -1;
    private static final String TAG = "BluetoothUtils";
    public static final boolean V = false;
    private static ErrorListener sErrorListener;

    /* loaded from: classes3.dex */
    public interface ErrorListener {
        void onShowError(Context context, String str, int i);
    }

    public static int getConnectionStateSummary(int connectionState) {
        if (connectionState != 0) {
            if (connectionState != 1) {
                if (connectionState != 2) {
                    if (connectionState == 3) {
                        return R.string.bluetooth_disconnecting;
                    }
                    return 0;
                }
                return R.string.bluetooth_connected;
            }
            return R.string.bluetooth_connecting;
        }
        return R.string.bluetooth_disconnected;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static void showError(Context context, String name, int messageResId) {
        ErrorListener errorListener = sErrorListener;
        if (errorListener != null) {
            errorListener.onShowError(context, name, messageResId);
        }
    }

    public static void setErrorListener(ErrorListener listener) {
        sErrorListener = listener;
    }

    public static Pair<Drawable, String> getBtClassDrawableWithDescription(Context context, CachedBluetoothDevice cachedDevice) {
        BluetoothClass btClass = cachedDevice.getBtClass();
        if (btClass != null) {
            int majorDeviceClass = btClass.getMajorDeviceClass();
            if (majorDeviceClass == 256) {
                return new Pair<>(getBluetoothDrawable(context, 17302315), context.getString(R.string.bluetooth_talkback_computer));
            }
            if (majorDeviceClass == 512) {
                return new Pair<>(getBluetoothDrawable(context, 17302773), context.getString(R.string.bluetooth_talkback_phone));
            }
            if (majorDeviceClass == 1280) {
                return new Pair<>(getBluetoothDrawable(context, HidProfile.getHidClassDrawable(btClass)), context.getString(R.string.bluetooth_talkback_input_peripheral));
            }
            if (majorDeviceClass == 1536) {
                return new Pair<>(getBluetoothDrawable(context, 17302805), context.getString(R.string.bluetooth_talkback_imaging));
            }
        }
        List<LocalBluetoothProfile> profiles = cachedDevice.getProfiles();
        for (LocalBluetoothProfile profile : profiles) {
            int resId = profile.getDrawableResource(btClass);
            if (resId != 0) {
                return new Pair<>(getBluetoothDrawable(context, resId), null);
            }
        }
        if (btClass != null) {
            if (btClass.doesClassMatch(0)) {
                return new Pair<>(getBluetoothDrawable(context, 17302313), context.getString(R.string.bluetooth_talkback_headset));
            }
            if (btClass.doesClassMatch(1)) {
                return new Pair<>(getBluetoothDrawable(context, 17302312), context.getString(R.string.bluetooth_talkback_headphone));
            }
        }
        return new Pair<>(getBluetoothDrawable(context, 17302803), context.getString(R.string.bluetooth_talkback_bluetooth));
    }

    public static Drawable getBluetoothDrawable(Context context, @DrawableRes int resId) {
        return context.getDrawable(resId);
    }

    public static Pair<Drawable, String> getBtRainbowDrawableWithDescription(Context context, CachedBluetoothDevice cachedDevice) {
        Uri iconUri;
        Pair<Drawable, String> pair = getBtClassDrawableWithDescription(context, cachedDevice);
        BluetoothDevice bluetoothDevice = cachedDevice.getDevice();
        boolean untetheredHeadset = getBooleanMetaData(bluetoothDevice, 6);
        int iconSize = context.getResources().getDimensionPixelSize(R.dimen.bt_nearby_icon_size);
        Resources resources = context.getResources();
        if (untetheredHeadset && (iconUri = getUriMetaData(bluetoothDevice, 5)) != null) {
            try {
                context.getContentResolver().takePersistableUriPermission(iconUri, 1);
            } catch (SecurityException e) {
                Log.e(TAG, "Failed to take persistable permission for: " + iconUri);
            }
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(context.getContentResolver(), iconUri);
                if (bitmap != null) {
                    Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, iconSize, iconSize, false);
                    bitmap.recycle();
                    AdaptiveOutlineDrawable drawable = new AdaptiveOutlineDrawable(resources, resizedBitmap);
                    return new Pair<>(drawable, (String) pair.second);
                }
            } catch (IOException e2) {
                Log.e(TAG, "Failed to get drawable for: " + iconUri, e2);
            }
        }
        return new Pair<>(buildBtRainbowDrawable(context, (Drawable) pair.first, cachedDevice.getAddress().hashCode()), (String) pair.second);
    }

    public static Drawable buildBtRainbowDrawable(Context context, Drawable drawable, int hashCode) {
        Resources resources = context.getResources();
        int[] iconFgColors = resources.getIntArray(R.array.bt_icon_fg_colors);
        int[] iconBgColors = resources.getIntArray(R.array.bt_icon_bg_colors);
        int index = Math.abs(hashCode % iconBgColors.length);
        drawable.setTint(iconFgColors[index]);
        Drawable adaptiveIcon = new AdaptiveIcon(context, drawable);
        ((AdaptiveIcon) adaptiveIcon).setBackgroundColor(iconBgColors[index]);
        return adaptiveIcon;
    }

    public static boolean getBooleanMetaData(BluetoothDevice bluetoothDevice, int key) {
        byte[] data;
        if (bluetoothDevice == null || (data = bluetoothDevice.getMetadata(key)) == null) {
            return false;
        }
        return Boolean.parseBoolean(new String(data));
    }

    public static String getStringMetaData(BluetoothDevice bluetoothDevice, int key) {
        byte[] data;
        if (bluetoothDevice == null || (data = bluetoothDevice.getMetadata(key)) == null) {
            return null;
        }
        return new String(data);
    }

    public static int getIntMetaData(BluetoothDevice bluetoothDevice, int key) {
        byte[] data;
        if (bluetoothDevice == null || (data = bluetoothDevice.getMetadata(key)) == null) {
            return -1;
        }
        try {
            return Integer.parseInt(new String(data));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static Uri getUriMetaData(BluetoothDevice bluetoothDevice, int key) {
        String data = getStringMetaData(bluetoothDevice, key);
        if (data == null) {
            return null;
        }
        return Uri.parse(data);
    }
}
