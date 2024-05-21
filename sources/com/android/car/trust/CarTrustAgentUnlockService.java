package com.android.car.trust;

import android.bluetooth.BluetoothDevice;
import android.car.encryptionrunner.EncryptionRunner;
import android.car.encryptionrunner.EncryptionRunnerFactory;
import android.car.encryptionrunner.HandshakeException;
import android.car.encryptionrunner.HandshakeMessage;
import android.car.encryptionrunner.Key;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.Slog;
import com.android.car.BLEStreamProtos.BLEOperationProto;
import com.android.car.PhoneAuthProtos.PhoneAuthProto;
import com.android.car.Utils;
import com.android.car.protobuf.InvalidProtocolBufferException;
import com.android.internal.annotations.GuardedBy;
import com.google.security.cryptauth.lib.securegcm.D2DConnectionContext;
import com.google.security.cryptauth.lib.securemessage.CryptoOps;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
/* loaded from: classes3.dex */
public class CarTrustAgentUnlockService {
    private static final int MAX_LOG_SIZE = 20;
    private static final int RESUME_HMAC_LENGTH = 32;
    private static final String TAG = "CarTrustAgentUnlock";
    private static final String TRUSTED_DEVICE_UNLOCK_ENABLED_KEY = "trusted_device_unlock_enabled";
    private static final int UNLOCK_STATE_KEY_EXCHANGE_IN_PROGRESS = 1;
    private static final int UNLOCK_STATE_MUTUAL_AUTH_ESTABLISHED = 3;
    private static final int UNLOCK_STATE_PHONE_CREDENTIALS_RECEIVED = 4;
    private static final int UNLOCK_STATE_WAITING_FOR_CLIENT_AUTH = 2;
    private static final int UNLOCK_STATE_WAITING_FOR_UNIQUE_ID = 0;
    private final CarTrustAgentBleManager mCarTrustAgentBleManager;
    private String mClientDeviceId;
    private D2DConnectionContext mCurrentContext;
    private Key mEncryptionKey;
    private HandshakeMessage mHandshakeMessage;
    private D2DConnectionContext mPrevContext;
    @GuardedBy({"mDeviceLock"})
    private BluetoothDevice mRemoteUnlockDevice;
    private final CarTrustedDeviceService mTrustedDeviceService;
    private CarTrustAgentUnlockDelegate mUnlockDelegate;
    private static final byte[] RESUME = "RESUME".getBytes();
    private static final byte[] SERVER = "SERVER".getBytes();
    private static final byte[] CLIENT = "CLIENT".getBytes();
    private static final byte[] ACKNOWLEDGEMENT_MESSAGE = "ACK".getBytes();
    private int mCurrentUnlockState = 0;
    private final Queue<String> mLogQueue = new LinkedList();
    private final Object mDeviceLock = new Object();
    private EncryptionRunner mEncryptionRunner = EncryptionRunnerFactory.newRunner();
    private int mEncryptionState = 0;

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes3.dex */
    public interface CarTrustAgentUnlockDelegate {
        void onUnlockDataReceived(int i, byte[] bArr, long j);
    }

    @Retention(RetentionPolicy.SOURCE)
    /* loaded from: classes3.dex */
    @interface UnlockState {
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public CarTrustAgentUnlockService(CarTrustedDeviceService service, CarTrustAgentBleManager bleService) {
        this.mTrustedDeviceService = service;
        this.mCarTrustAgentBleManager = bleService;
    }

    public void setTrustedDeviceUnlockEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = this.mTrustedDeviceService.getSharedPrefs().edit();
        editor.putBoolean(TRUSTED_DEVICE_UNLOCK_ENABLED_KEY, isEnabled);
        if (!editor.commit()) {
            Slog.e(TAG, "Unlock Enable Failed. Enable? " + isEnabled);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void setUnlockRequestDelegate(CarTrustAgentUnlockDelegate delegate) {
        this.mUnlockDelegate = delegate;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void startUnlockAdvertising() {
        if (!this.mTrustedDeviceService.getSharedPrefs().getBoolean(TRUSTED_DEVICE_UNLOCK_ENABLED_KEY, true)) {
            Slog.e(TAG, "Trusted Device Unlock is disabled");
            return;
        }
        this.mTrustedDeviceService.getCarTrustAgentEnrollmentService().stopEnrollmentAdvertising();
        stopUnlockAdvertising();
        EventLog.logUnlockEvent("START_UNLOCK_ADVERTISING");
        queueMessageForLog("startUnlockAdvertising");
        this.mCarTrustAgentBleManager.startUnlockAdvertising();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void stopUnlockAdvertising() {
        EventLog.logUnlockEvent("STOP_UNLOCK_ADVERTISING");
        queueMessageForLog("stopUnlockAdvertising");
        this.mCarTrustAgentBleManager.stopUnlockAdvertising();
        if (this.mRemoteUnlockDevice != null) {
            this.mCarTrustAgentBleManager.disconnectRemoteDevice();
            this.mRemoteUnlockDevice = null;
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void init() {
        EventLog.logUnlockEvent("UNLOCK_SERVICE_INIT");
        this.mCarTrustAgentBleManager.setupUnlockBleServer();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void release() {
        synchronized (this.mDeviceLock) {
            this.mRemoteUnlockDevice = null;
        }
        this.mPrevContext = null;
        this.mCurrentContext = null;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void onRemoteDeviceConnected(BluetoothDevice device) {
        synchronized (this.mDeviceLock) {
            if (this.mRemoteUnlockDevice != null) {
                Slog.e(TAG, "Unexpected: Cannot connect to another device when already connected");
            }
            queueMessageForLog("onRemoteDeviceConnected (addr:" + device.getAddress() + ")");
            EventLog.logUnlockEvent("REMOTE_DEVICE_CONNECTED");
            this.mRemoteUnlockDevice = device;
        }
        resetEncryptionState();
        this.mCurrentUnlockState = 0;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void onRemoteDeviceDisconnected(BluetoothDevice device) {
        if (!device.equals(this.mRemoteUnlockDevice) && device.getAddress() != null) {
            Slog.e(TAG, "Disconnected from an unknown device:" + device.getAddress());
        }
        queueMessageForLog("onRemoteDeviceDisconnected (addr:" + device.getAddress() + ")");
        synchronized (this.mDeviceLock) {
            this.mRemoteUnlockDevice = null;
        }
        resetEncryptionState();
        this.mCurrentUnlockState = 0;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void onUnlockDataReceived(byte[] value) {
        int i = this.mCurrentUnlockState;
        if (i == 0) {
            if (!CarTrustAgentValidator.isValidUnlockDeviceId(value)) {
                Slog.e(TAG, "Device Id rejected by validator.");
                resetUnlockStateOnFailure();
                return;
            }
            this.mClientDeviceId = convertToDeviceId(value);
            if (this.mClientDeviceId == null) {
                if (Log.isLoggable(TAG, 3)) {
                    Slog.d(TAG, "Phone not enrolled as a trusted device");
                }
                resetUnlockStateOnFailure();
                return;
            }
            EventLog.logUnlockEvent("RECEIVED_DEVICE_ID");
            sendAckToClient(false);
            this.mCurrentUnlockState = 1;
        } else if (i == 1) {
            try {
                processKeyExchangeHandshakeMessage(value);
            } catch (HandshakeException e) {
                Slog.e(TAG, "Handshake failure", e);
                resetUnlockStateOnFailure();
            }
        } else if (i == 2) {
            if (!authenticateClient(value)) {
                if (Log.isLoggable(TAG, 3)) {
                    Slog.d(TAG, "HMAC from the phone is not correct. Cannot resume session. Need to re-enroll");
                }
                this.mTrustedDeviceService.clearEncryptionKey(this.mClientDeviceId);
                resetUnlockStateOnFailure();
                return;
            }
            EventLog.logUnlockEvent("CLIENT_AUTHENTICATED");
            sendServerAuthToClient();
            this.mCurrentUnlockState = 3;
        } else if (i != 3) {
            if (i == 4) {
                Slog.e(TAG, "Landed on unexpected state of credentials received.");
                return;
            }
            Slog.e(TAG, "Encountered unexpected unlock state: " + this.mCurrentUnlockState);
        } else {
            Key key = this.mEncryptionKey;
            if (key == null) {
                Slog.e(TAG, "Current session key null. Unexpected at this stage: " + this.mCurrentUnlockState);
                this.mTrustedDeviceService.clearEncryptionKey(this.mClientDeviceId);
                resetUnlockStateOnFailure();
                return;
            }
            this.mTrustedDeviceService.saveEncryptionKey(this.mClientDeviceId, key.asBytes());
            try {
                byte[] decryptedCredentials = this.mEncryptionKey.decryptData(value);
                processCredentials(decryptedCredentials);
                this.mCurrentUnlockState = 4;
                EventLog.logUnlockEvent("UNLOCK_CREDENTIALS_RECEIVED");
                sendAckToClient(true);
            } catch (SignatureException e2) {
                Slog.e(TAG, "Could not decrypt phone credentials.", e2);
                resetUnlockStateOnFailure();
            }
        }
    }

    private void sendAckToClient(boolean isEncrypted) {
        byte[] ack = isEncrypted ? this.mEncryptionKey.encryptData(ACKNOWLEDGEMENT_MESSAGE) : ACKNOWLEDGEMENT_MESSAGE;
        this.mCarTrustAgentBleManager.sendUnlockMessage(this.mRemoteUnlockDevice, ack, BLEOperationProto.OperationType.CLIENT_MESSAGE, isEncrypted);
    }

    private String convertToDeviceId(byte[] id) {
        UUID deviceId = Utils.bytesToUUID(id);
        if (deviceId == null || this.mTrustedDeviceService.getEncryptionKey(deviceId.toString()) == null) {
            if (deviceId != null) {
                Slog.e(TAG, "Unknown phone connected: " + deviceId.toString());
                return null;
            }
            return null;
        }
        return deviceId.toString();
    }

    private void processKeyExchangeHandshakeMessage(byte[] message) throws HandshakeException {
        int i = this.mEncryptionState;
        if (i == 0) {
            if (Log.isLoggable(TAG, 3)) {
                Slog.d(TAG, "Responding to handshake init request.");
            }
            this.mHandshakeMessage = this.mEncryptionRunner.respondToInitRequest(message);
            this.mEncryptionState = this.mHandshakeMessage.getHandshakeState();
            this.mCarTrustAgentBleManager.sendUnlockMessage(this.mRemoteUnlockDevice, this.mHandshakeMessage.getNextMessage(), BLEOperationProto.OperationType.ENCRYPTION_HANDSHAKE, false);
            EventLog.logUnlockEvent("UNLOCK_ENCRYPTION_STATE", this.mEncryptionState);
        } else if (i != 1) {
            if (i == 2 || i == 3) {
                showVerificationCode();
                return;
            }
            Slog.w(TAG, "Encountered invalid handshake state: " + this.mEncryptionState);
        } else {
            if (Log.isLoggable(TAG, 3)) {
                Slog.d(TAG, "Continuing handshake.");
            }
            this.mHandshakeMessage = this.mEncryptionRunner.continueHandshake(message);
            this.mEncryptionState = this.mHandshakeMessage.getHandshakeState();
            if (Log.isLoggable(TAG, 3)) {
                Slog.d(TAG, "Updated encryption state: " + this.mEncryptionState);
            }
            int i2 = this.mEncryptionState;
            if (i2 == 2) {
                EventLog.logUnlockEvent("UNLOCK_ENCRYPTION_STATE", i2);
                showVerificationCode();
                return;
            }
            this.mCarTrustAgentBleManager.sendUnlockMessage(this.mRemoteUnlockDevice, this.mHandshakeMessage.getNextMessage(), BLEOperationProto.OperationType.ENCRYPTION_HANDSHAKE, false);
        }
    }

    private void showVerificationCode() {
        try {
            HandshakeMessage handshakeMessage = this.mEncryptionRunner.verifyPin();
            if (handshakeMessage.getHandshakeState() != 3) {
                Slog.e(TAG, "Handshake not finished after calling verify PIN. Instead got state: " + handshakeMessage.getHandshakeState());
                resetUnlockStateOnFailure();
                return;
            }
            this.mEncryptionState = 3;
            this.mEncryptionKey = handshakeMessage.getKey();
            this.mCurrentContext = D2DConnectionContext.fromSavedSession(this.mEncryptionKey.asBytes());
            String str = this.mClientDeviceId;
            if (str == null) {
                resetUnlockStateOnFailure();
                return;
            }
            byte[] oldSessionKeyBytes = this.mTrustedDeviceService.getEncryptionKey(str);
            if (oldSessionKeyBytes == null) {
                Slog.e(TAG, "Could not retrieve previous session keys! Have to re-enroll trusted device");
                resetUnlockStateOnFailure();
                return;
            }
            this.mPrevContext = D2DConnectionContext.fromSavedSession(oldSessionKeyBytes);
            if (this.mPrevContext == null) {
                resetUnlockStateOnFailure();
                return;
            }
            this.mCurrentUnlockState = 2;
            EventLog.logUnlockEvent("WAITING_FOR_CLIENT_AUTH");
        } catch (HandshakeException e) {
            Slog.e(TAG, "Verify pin failed for new keys - Unexpected");
            resetUnlockStateOnFailure();
        }
    }

    private void sendServerAuthToClient() {
        byte[] resumeBytes = computeMAC(this.mPrevContext, this.mCurrentContext, SERVER);
        if (resumeBytes == null) {
            return;
        }
        this.mCarTrustAgentBleManager.sendUnlockMessage(this.mRemoteUnlockDevice, resumeBytes, BLEOperationProto.OperationType.CLIENT_MESSAGE, false);
    }

    private byte[] computeMAC(D2DConnectionContext previous, D2DConnectionContext next, byte[] info) {
        try {
            SecretKeySpec inputKeyMaterial = new SecretKeySpec(Utils.concatByteArrays(previous.getSessionUnique(), next.getSessionUnique()), "");
            return CryptoOps.hkdf(inputKeyMaterial, RESUME, info);
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            Slog.e(TAG, "Compute MAC failed");
            return null;
        }
    }

    private boolean authenticateClient(byte[] message) {
        if (message.length != 32) {
            Slog.e(TAG, "failing because message.length is " + message.length);
            return false;
        }
        return MessageDigest.isEqual(message, computeMAC(this.mPrevContext, this.mCurrentContext, CLIENT));
    }

    void processCredentials(byte[] credentials) {
        if (this.mUnlockDelegate == null) {
            if (Log.isLoggable(TAG, 3)) {
                Slog.d(TAG, "No Unlock delegate to notify of unlock credentials.");
                return;
            }
            return;
        }
        queueMessageForLog("processCredentials");
        try {
            PhoneAuthProto.PhoneCredentials phoneCredentials = PhoneAuthProto.PhoneCredentials.parseFrom(credentials);
            byte[] handle = phoneCredentials.getHandle().toByteArray();
            this.mUnlockDelegate.onUnlockDataReceived(this.mTrustedDeviceService.getUserHandleByTokenHandle(Utils.bytesToLong(handle)), phoneCredentials.getEscrowToken().toByteArray(), Utils.bytesToLong(handle));
        } catch (InvalidProtocolBufferException e) {
            Slog.e(TAG, "Error parsing credentials protobuf.", e);
        }
    }

    private void resetUnlockStateOnFailure() {
        this.mCarTrustAgentBleManager.disconnectRemoteDevice();
        resetEncryptionState();
    }

    private void resetEncryptionState() {
        this.mEncryptionRunner = EncryptionRunnerFactory.newRunner();
        this.mHandshakeMessage = null;
        this.mEncryptionKey = null;
        this.mEncryptionState = 0;
        this.mCurrentUnlockState = 0;
        if (this.mCurrentContext != null) {
            this.mCurrentContext = null;
        }
        if (this.mPrevContext != null) {
            this.mPrevContext = null;
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void dump(PrintWriter writer) {
        writer.println("*CarTrustAgentUnlockService*");
        writer.println("Unlock Service Logs:");
        for (String log : this.mLogQueue) {
            writer.println("\t" + log);
        }
    }

    private void queueMessageForLog(String message) {
        if (this.mLogQueue.size() >= 20) {
            this.mLogQueue.remove();
        }
        Queue<String> queue = this.mLogQueue;
        queue.add(System.currentTimeMillis() + " : " + message);
    }
}
