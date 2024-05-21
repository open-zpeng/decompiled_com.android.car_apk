package com.android.car;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.text.format.DateFormat;
import android.util.SparseArray;
import com.android.settingslib.bluetooth.PbapServerProfile;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
/* loaded from: classes3.dex */
public class Utils {
    private static final int UUID_LENGTH = 16;
    static final Boolean DBG = false;
    private static final SparseArray<String> sAdapterStates = new SparseArray<>();
    private static final SparseArray<String> sBondStates = new SparseArray<>();
    private static final SparseArray<String> sConnectionStates = new SparseArray<>();
    private static final SparseArray<String> sProfileNames = new SparseArray<>();

    static {
        sAdapterStates.put(12, "On");
        sAdapterStates.put(10, "Off");
        sAdapterStates.put(11, "Turning On");
        sAdapterStates.put(13, "Turning Off");
        sBondStates.put(12, "Bonded");
        sBondStates.put(11, "Bonding");
        sBondStates.put(10, "Unbonded");
        sConnectionStates.put(2, "Connected");
        sConnectionStates.put(0, "Disconnected");
        sConnectionStates.put(1, "Connecting");
        sConnectionStates.put(3, "Disconnecting");
        sProfileNames.put(1, "HFP Server");
        sProfileNames.put(2, "A2DP Source");
        sProfileNames.put(3, "HDP");
        sProfileNames.put(4, "HID Host");
        sProfileNames.put(5, "PAN");
        sProfileNames.put(6, PbapServerProfile.NAME);
        sProfileNames.put(7, "GATT Client");
        sProfileNames.put(8, "GATT Server");
        sProfileNames.put(9, "MAP Server");
        sProfileNames.put(10, "SAP");
        sProfileNames.put(11, "A2DP Sink");
        sProfileNames.put(12, "AVRCP Controller");
        sProfileNames.put(13, "AVRCP Target");
        sProfileNames.put(16, "HFP Client");
        sProfileNames.put(17, "PBAP Client");
        sProfileNames.put(18, "MAP Client");
        sProfileNames.put(19, "HID Device");
        sProfileNames.put(20, "OPP");
        sProfileNames.put(21, "Hearing Aid");
    }

    static String getDeviceDebugInfo(BluetoothDevice device) {
        if (device == null) {
            return "(null)";
        }
        return "(name = " + device.getName() + ", addr = " + device.getAddress() + ")";
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static String getProfileName(int profile) {
        String name = sProfileNames.get(profile, "Unknown");
        return "(" + profile + ") " + name;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static String getConnectionStateName(int state) {
        String name = sConnectionStates.get(state, "Unknown");
        return "(" + state + ") " + name;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static String getBondStateName(int state) {
        String name = sBondStates.get(state, "Unknown");
        return "(" + state + ") " + name;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static String getAdapterStateName(int state) {
        String name = sAdapterStates.get(state, "Unknown");
        return "(" + state + ") " + name;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static String getProfilePriorityName(int priority) {
        String name;
        if (priority >= 1000) {
            name = "PRIORITY_AUTO_CONNECT";
        } else if (priority >= 100) {
            name = "PRIORITY_ON";
        } else if (priority >= 0) {
            name = "PRIORITY_OFF";
        } else {
            name = "PRIORITY_UNDEFINED";
        }
        return "(" + priority + ") " + name;
    }

    /* loaded from: classes3.dex */
    public static class TransitionLog {
        private String mExtra;
        private Object mFromState;
        private String mServiceName;
        private long mTimestampMs;
        private Object mToState;

        public TransitionLog(String name, Object fromState, Object toState, long timestamp, String extra) {
            this(name, fromState, toState, timestamp);
            this.mExtra = extra;
        }

        public TransitionLog(String name, Object fromState, Object toState, long timeStamp) {
            this.mServiceName = name;
            this.mFromState = fromState;
            this.mToState = toState;
            this.mTimestampMs = timeStamp;
        }

        private CharSequence timeToLog(long timestamp) {
            return DateFormat.format("MM-dd HH:mm:ss", timestamp);
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append((Object) timeToLog(this.mTimestampMs));
            sb.append(" ");
            sb.append(this.mServiceName);
            sb.append(": ");
            String str = this.mExtra;
            if (str == null) {
                str = "";
            }
            sb.append(str);
            sb.append(" changed from ");
            sb.append(this.mFromState);
            sb.append(" to ");
            sb.append(this.mToState);
            return sb.toString();
        }
    }

    public static byte[] longToBytes(long primitive) {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(primitive);
        return buffer.array();
    }

    public static long bytesToLong(byte[] array) {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.put(array);
        buffer.flip();
        long value = buffer.getLong();
        return value;
    }

    public static String byteArrayToHexString(byte[] array) {
        StringBuilder sb = new StringBuilder(array.length * 2);
        for (byte b : array) {
            sb.append(String.format("%02x", Byte.valueOf(b)));
        }
        return sb.toString();
    }

    public static byte[] uuidToBytes(UUID uuid) {
        return ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits()).array();
    }

    public static UUID bytesToUUID(byte[] bytes) {
        if (bytes.length != 16) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    @SuppressLint({"DefaultLocale"})
    public static String generateRandomNumberString(int length) {
        return String.format("%0" + length + "d", Integer.valueOf(ThreadLocalRandom.current().nextInt((int) Math.pow(10.0d, length))));
    }

    public static byte[] concatByteArrays(byte[] a, byte[] b) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        if (a != null) {
            try {
                outputStream.write(a);
            } catch (IOException e) {
                return null;
            }
        }
        if (b != null) {
            outputStream.write(b);
        }
        return outputStream.toByteArray();
    }
}
