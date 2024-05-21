package com.android.car.trust;

import android.app.ActivityManager;
import android.bluetooth.BluetoothDevice;
import android.car.trust.TrustedDeviceInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.sysprop.CarProperties;
import android.util.Base64;
import android.util.Log;
import android.util.Slog;
import com.android.car.CarServiceBase;
import com.android.car.R;
import com.android.car.Utils;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.UUID;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
/* loaded from: classes3.dex */
public class CarTrustedDeviceService implements CarServiceBase {
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int DEVICE_NAME_LENGTH_LIMIT = 8;
    private static final int DEVICE_NAME_PREFIX_LIMIT = 4;
    private static final int GCM_AUTHENTICATION_TAG_LENGTH = 128;
    private static final String IV_SPEC_SEPARATOR = ";";
    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String KEY_ALIAS = "Ukey2Key";
    private static final String PREF_ENCRYPTION_KEY_PREFIX = "CTABM_encryption_key";
    private static final String TAG = CarTrustedDeviceService.class.getSimpleName();
    private static final String UNIQUE_ID_KEY = "CTABM_unique_id";
    private CarTrustAgentBleManager mCarTrustAgentBleManager;
    private CarTrustAgentEnrollmentService mCarTrustAgentEnrollmentService;
    private CarTrustAgentUnlockService mCarTrustAgentUnlockService;
    private final Context mContext;
    private String mEnrollmentDeviceName;
    private SharedPreferences mTrustAgentTokenPreferences;
    private UUID mUniqueId;

    public CarTrustedDeviceService(Context context) {
        this.mContext = context;
        this.mCarTrustAgentBleManager = new CarTrustAgentBleManager(context);
        this.mCarTrustAgentEnrollmentService = new CarTrustAgentEnrollmentService(this.mContext, this, this.mCarTrustAgentBleManager);
        this.mCarTrustAgentUnlockService = new CarTrustAgentUnlockService(this, this.mCarTrustAgentBleManager);
    }

    @Override // com.android.car.CarServiceBase
    public synchronized void init() {
        this.mCarTrustAgentEnrollmentService.init();
        this.mCarTrustAgentUnlockService.init();
    }

    @Override // com.android.car.CarServiceBase
    public synchronized void release() {
        this.mCarTrustAgentBleManager.cleanup();
        this.mCarTrustAgentEnrollmentService.release();
        this.mCarTrustAgentUnlockService.release();
    }

    public CarTrustAgentEnrollmentService getCarTrustAgentEnrollmentService() {
        return this.mCarTrustAgentEnrollmentService;
    }

    public CarTrustAgentUnlockService getCarTrustAgentUnlockService() {
        return this.mCarTrustAgentUnlockService;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public int getUserHandleByTokenHandle(long handle) {
        return getSharedPrefs().getInt(String.valueOf(handle), -1);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void onRemoteDeviceConnected(BluetoothDevice device) {
        this.mCarTrustAgentEnrollmentService.onRemoteDeviceConnected(device);
        this.mCarTrustAgentUnlockService.onRemoteDeviceConnected(device);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void onRemoteDeviceDisconnected(BluetoothDevice device) {
        this.mCarTrustAgentEnrollmentService.onRemoteDeviceDisconnected(device);
        this.mCarTrustAgentUnlockService.onRemoteDeviceDisconnected(device);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void onDeviceNameRetrieved(String deviceName) {
        this.mCarTrustAgentEnrollmentService.onDeviceNameRetrieved(deviceName);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void cleanupBleService() {
        if (Log.isLoggable(TAG, 3)) {
            Slog.d(TAG, "cleanupBleService");
        }
        this.mCarTrustAgentBleManager.stopGattServer();
        this.mCarTrustAgentBleManager.stopEnrollmentAdvertising();
        this.mCarTrustAgentBleManager.stopUnlockAdvertising();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public SharedPreferences getSharedPrefs() {
        SharedPreferences sharedPreferences = this.mTrustAgentTokenPreferences;
        if (sharedPreferences != null) {
            return sharedPreferences;
        }
        Context context = this.mContext;
        this.mTrustAgentTokenPreferences = context.getSharedPreferences(context.getString(R.string.token_handle_shared_preferences), 0);
        return this.mTrustAgentTokenPreferences;
    }

    @Override // com.android.car.CarServiceBase
    public void dump(PrintWriter writer) {
        writer.println("*CarTrustedDeviceService*");
        int uid = ActivityManager.getCurrentUser();
        writer.println("current user id: " + uid);
        List<TrustedDeviceInfo> deviceInfos = this.mCarTrustAgentEnrollmentService.getEnrolledDeviceInfosForUser(uid);
        writer.println(getDeviceInfoListString(uid, deviceInfos));
        this.mCarTrustAgentEnrollmentService.dump(writer);
        this.mCarTrustAgentUnlockService.dump(writer);
    }

    private static String getDeviceInfoListString(int uid, List<TrustedDeviceInfo> deviceInfos) {
        StringBuilder sb = new StringBuilder();
        sb.append("device list of (user : ");
        sb.append(uid);
        sb.append("):");
        if (deviceInfos != null && deviceInfos.size() > 0) {
            for (int i = 0; i < deviceInfos.size(); i++) {
                sb.append("\n\tdevice# ");
                sb.append(i + 1);
                sb.append(" : ");
                sb.append(deviceInfos.get(i).toString());
            }
        } else {
            sb.append("\n\tno device listed");
        }
        return sb.toString();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public UUID getUniqueId() {
        UUID uuid = this.mUniqueId;
        if (uuid != null) {
            return uuid;
        }
        SharedPreferences prefs = getSharedPrefs();
        if (prefs.contains(UNIQUE_ID_KEY)) {
            this.mUniqueId = UUID.fromString(prefs.getString(UNIQUE_ID_KEY, null));
            if (Log.isLoggable(TAG, 3)) {
                String str = TAG;
                Slog.d(str, "Found existing trusted unique id: " + prefs.getString(UNIQUE_ID_KEY, ""));
            }
        } else {
            this.mUniqueId = UUID.randomUUID();
            if (!prefs.edit().putString(UNIQUE_ID_KEY, this.mUniqueId.toString()).commit()) {
                this.mUniqueId = null;
            } else if (Log.isLoggable(TAG, 3)) {
                String str2 = TAG;
                Slog.d(str2, "Generated new trusted unique id: " + prefs.getString(UNIQUE_ID_KEY, ""));
            }
        }
        return this.mUniqueId;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public byte[] getEncryptionKey(String deviceId) {
        SharedPreferences prefs = getSharedPrefs();
        String key = PREF_ENCRYPTION_KEY_PREFIX + deviceId;
        if (prefs.contains(key)) {
            String[] values = prefs.getString(key, null).split(IV_SPEC_SEPARATOR);
            if (values.length != 2) {
                return null;
            }
            byte[] encryptedKey = Base64.decode(values[0], 0);
            byte[] ivSpec = Base64.decode(values[1], 0);
            return decryptWithKeyStore(KEY_ALIAS, encryptedKey, ivSpec);
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean saveEncryptionKey(String deviceId, byte[] encryptionKey) {
        String encryptedKey;
        if (encryptionKey == null || deviceId == null || (encryptedKey = encryptWithKeyStore(KEY_ALIAS, encryptionKey)) == null) {
            return false;
        }
        if (getSharedPrefs().contains(deviceId)) {
            clearEncryptionKey(deviceId);
        }
        SharedPreferences.Editor edit = getSharedPrefs().edit();
        return edit.putString(PREF_ENCRYPTION_KEY_PREFIX + deviceId, encryptedKey).commit();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void clearEncryptionKey(String deviceId) {
        if (deviceId == null) {
            return;
        }
        getSharedPrefs().edit().remove(deviceId);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public String getEnrollmentDeviceName() {
        if (this.mEnrollmentDeviceName == null) {
            String deviceNamePrefix = (String) CarProperties.trusted_device_device_name_prefix().orElse("");
            String deviceNamePrefix2 = deviceNamePrefix.substring(0, Math.min(deviceNamePrefix.length(), 4));
            int randomNameLength = 8 - deviceNamePrefix2.length();
            String randomName = Utils.generateRandomNumberString(randomNameLength);
            this.mEnrollmentDeviceName = deviceNamePrefix2 + randomName;
        }
        return this.mEnrollmentDeviceName;
    }

    String encryptWithKeyStore(String keyAlias, byte[] value) {
        if (value == null) {
            return null;
        }
        Key key = getKeyStoreKey(keyAlias);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(1, key);
            StringBuffer stringBuffer = new StringBuffer(Base64.encodeToString(cipher.doFinal(value), 0));
            stringBuffer.append(IV_SPEC_SEPARATOR);
            stringBuffer.append(Base64.encodeToString(cipher.getIV(), 0));
            return stringBuffer.toString();
        } catch (IllegalStateException | InvalidKeyException | NoSuchAlgorithmException | BadPaddingException | IllegalBlockSizeException | NoSuchPaddingException e) {
            String str = TAG;
            Slog.e(str, "Unable to encrypt value with key " + keyAlias, e);
            return null;
        }
    }

    byte[] decryptWithKeyStore(String keyAlias, byte[] value, byte[] ivSpec) {
        if (value == null) {
            return null;
        }
        try {
            Key key = getKeyStoreKey(keyAlias);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(2, key, new GCMParameterSpec(128, ivSpec));
            return cipher.doFinal(value);
        } catch (IllegalStateException | InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | BadPaddingException | IllegalBlockSizeException | NoSuchPaddingException e) {
            String str = TAG;
            Slog.e(str, "Unable to decrypt value with key " + keyAlias, e);
            return null;
        }
    }

    private Key getKeyStoreKey(String keyAlias) {
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
            keyStore.load(null);
            if (!keyStore.containsAlias(keyAlias)) {
                KeyGenerator keyGenerator = KeyGenerator.getInstance("AES", KEYSTORE_PROVIDER);
                keyGenerator.init(new KeyGenParameterSpec.Builder(keyAlias, 3).setBlockModes("GCM").setEncryptionPaddings("NoPadding").build());
                keyGenerator.generateKey();
            }
            return keyStore.getKey(keyAlias, null);
        } catch (IOException | InvalidAlgorithmParameterException | KeyStoreException | NoSuchAlgorithmException | NoSuchProviderException | UnrecoverableKeyException | CertificateException e) {
            String str = TAG;
            Slog.e(str, "Unable to retrieve key " + keyAlias + " from KeyStore.", e);
            throw new IllegalStateException(e);
        }
    }
}
