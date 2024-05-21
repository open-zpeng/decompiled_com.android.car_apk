package com.android.car.trust;

import android.app.ActivityManager;
import android.bluetooth.BluetoothDevice;
import android.car.encryptionrunner.EncryptionRunner;
import android.car.encryptionrunner.EncryptionRunnerFactory;
import android.car.encryptionrunner.HandshakeException;
import android.car.encryptionrunner.HandshakeMessage;
import android.car.encryptionrunner.Key;
import android.car.trust.ICarTrustAgentBleCallback;
import android.car.trust.ICarTrustAgentEnrollment;
import android.car.trust.ICarTrustAgentEnrollmentCallback;
import android.car.trust.TrustedDeviceInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.util.Slog;
import com.android.car.BLEStreamProtos.BLEOperationProto;
import com.android.car.ICarImpl;
import com.android.car.R;
import com.android.car.Utils;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
/* loaded from: classes3.dex */
public class CarTrustAgentEnrollmentService extends ICarTrustAgentEnrollment.Stub {
    @VisibleForTesting
    static final byte[] CONFIRMATION_SIGNAL = "True".getBytes();
    private static final char DEVICE_INFO_DELIMITER = '#';
    static final int ENROLLMENT_STATE_ENCRYPTION_COMPLETED = 2;
    static final int ENROLLMENT_STATE_HANDLE = 3;
    static final int ENROLLMENT_STATE_NONE = 0;
    static final int ENROLLMENT_STATE_UNIQUE_ID = 1;
    private static final int MAX_LOG_SIZE = 20;
    private static final String TAG = "CarTrustAgentEnroll";
    private static final String TRUSTED_DEVICE_ENROLLMENT_ENABLED_KEY = "trusted_device_enrollment_enabled";
    private final CarTrustAgentBleManager mCarTrustAgentBleManager;
    private String mClientDeviceId;
    private String mClientDeviceName;
    private final Context mContext;
    private Key mEncryptionKey;
    private CarTrustAgentEnrollmentRequestDelegate mEnrollmentDelegate;
    @VisibleForTesting
    int mEnrollmentState;
    private long mHandle;
    private HandshakeMessage mHandshakeMessage;
    @GuardedBy({"mRemoteDeviceLock"})
    private BluetoothDevice mRemoteEnrollmentDevice;
    private final CarTrustedDeviceService mTrustedDeviceService;
    private final List<EnrollmentStateClient> mEnrollmentStateClients = new ArrayList();
    private final List<BleStateChangeClient> mBleStateChangeClients = new ArrayList();
    private final Queue<String> mLogQueue = new LinkedList();
    private Object mRemoteDeviceLock = new Object();
    private final Map<Long, Boolean> mTokenActiveStateMap = new HashMap();
    private EncryptionRunner mEncryptionRunner = EncryptionRunnerFactory.newRunner();
    @VisibleForTesting
    int mEncryptionState = 0;

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes3.dex */
    public interface CarTrustAgentEnrollmentRequestDelegate {
        void addEscrowToken(byte[] bArr, int i);

        void isEscrowTokenActive(long j, int i);

        void removeEscrowToken(long j, int i);
    }

    @VisibleForTesting
    @Retention(RetentionPolicy.SOURCE)
    /* loaded from: classes3.dex */
    @interface EnrollmentState {
    }

    public CarTrustAgentEnrollmentService(Context context, CarTrustedDeviceService service, CarTrustAgentBleManager bleService) {
        this.mContext = context;
        this.mTrustedDeviceService = service;
        this.mCarTrustAgentBleManager = bleService;
    }

    public synchronized void init() {
        this.mCarTrustAgentBleManager.setupEnrollmentBleServer();
    }

    @VisibleForTesting
    void setEncryptionRunner(EncryptionRunner dummyEncryptionRunner) {
        this.mEncryptionRunner = dummyEncryptionRunner;
    }

    public synchronized void release() {
        for (EnrollmentStateClient client : this.mEnrollmentStateClients) {
            client.mListenerBinder.unlinkToDeath(client, 0);
        }
        for (BleStateChangeClient client2 : this.mBleStateChangeClients) {
            client2.mListenerBinder.unlinkToDeath(client2, 0);
        }
        this.mEnrollmentStateClients.clear();
    }

    public void startEnrollmentAdvertising() {
        ICarImpl.assertTrustAgentEnrollmentPermission(this.mContext);
        if (!this.mTrustedDeviceService.getSharedPrefs().getBoolean(TRUSTED_DEVICE_ENROLLMENT_ENABLED_KEY, true)) {
            Slog.e(TAG, "Trusted Device Enrollment disabled");
            dispatchEnrollmentFailure(2);
            return;
        }
        this.mTrustedDeviceService.getCarTrustAgentUnlockService().stopUnlockAdvertising();
        stopEnrollmentAdvertising();
        EventLog.logEnrollmentEvent("START_ENROLLMENT_ADVERTISING");
        addEnrollmentServiceLog("startEnrollmentAdvertising");
        this.mCarTrustAgentBleManager.startEnrollmentAdvertising();
        this.mEnrollmentState = 0;
    }

    public void stopEnrollmentAdvertising() {
        ICarImpl.assertTrustAgentEnrollmentPermission(this.mContext);
        EventLog.logEnrollmentEvent("STOP_ENROLLMENT_ADVERTISING");
        addEnrollmentServiceLog("stopEnrollmentAdvertising");
        this.mCarTrustAgentBleManager.stopEnrollmentAdvertising();
    }

    public void enrollmentHandshakeAccepted(BluetoothDevice device) {
        ICarImpl.assertTrustAgentEnrollmentPermission(this.mContext);
        EventLog.logEnrollmentEvent("ENROLLMENT_HANDSHAKE_ACCEPTED");
        addEnrollmentServiceLog("enrollmentHandshakeAccepted");
        if (device == null || !device.equals(this.mRemoteEnrollmentDevice)) {
            Slog.e(TAG, "Enrollment Failure: device is different from cached remote bluetooth device, disconnect from the device. current device is:" + device);
            this.mCarTrustAgentBleManager.disconnectRemoteDevice();
            return;
        }
        this.mCarTrustAgentBleManager.sendEnrollmentMessage(this.mRemoteEnrollmentDevice, CONFIRMATION_SIGNAL, BLEOperationProto.OperationType.ENCRYPTION_HANDSHAKE, false);
        setEnrollmentHandshakeAccepted();
    }

    public void terminateEnrollmentHandshake() {
        ICarImpl.assertTrustAgentEnrollmentPermission(this.mContext);
        addEnrollmentServiceLog("terminateEnrollmentHandshake");
        this.mCarTrustAgentBleManager.disconnectRemoteDevice();
        Iterator<Map.Entry<Long, Boolean>> it = this.mTokenActiveStateMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Boolean> pair = it.next();
            boolean isHandleActive = pair.getValue().booleanValue();
            if (!isHandleActive) {
                long handle = pair.getKey().longValue();
                int uid = this.mTrustedDeviceService.getSharedPrefs().getInt(String.valueOf(handle), -1);
                removeEscrowToken(handle, uid);
                it.remove();
            }
        }
    }

    public boolean isEscrowTokenActive(long handle, int uid) {
        ICarImpl.assertTrustAgentEnrollmentPermission(this.mContext);
        if (this.mTokenActiveStateMap.get(Long.valueOf(handle)) != null) {
            return this.mTokenActiveStateMap.get(Long.valueOf(handle)).booleanValue();
        }
        return false;
    }

    public void removeEscrowToken(long handle, int uid) {
        ICarImpl.assertTrustAgentEnrollmentPermission(this.mContext);
        this.mEnrollmentDelegate.removeEscrowToken(handle, uid);
        addEnrollmentServiceLog("removeEscrowToken (handle:" + handle + " uid:" + uid + ")");
    }

    public void removeAllTrustedDevices(int uid) {
        ICarImpl.assertTrustAgentEnrollmentPermission(this.mContext);
        for (TrustedDeviceInfo device : getEnrolledDeviceInfosForUser(uid)) {
            removeEscrowToken(device.getHandle(), uid);
        }
    }

    public void setTrustedDeviceEnrollmentEnabled(boolean isEnabled) {
        ICarImpl.assertTrustAgentEnrollmentPermission(this.mContext);
        SharedPreferences.Editor editor = this.mTrustedDeviceService.getSharedPrefs().edit();
        editor.putBoolean(TRUSTED_DEVICE_ENROLLMENT_ENABLED_KEY, isEnabled);
        if (!editor.commit()) {
            Slog.e(TAG, "Enrollment Failure: Commit to SharedPreferences failed. Enable? " + isEnabled);
        }
    }

    public void setTrustedDeviceUnlockEnabled(boolean isEnabled) {
        ICarImpl.assertTrustAgentEnrollmentPermission(this.mContext);
        this.mTrustedDeviceService.getCarTrustAgentUnlockService().setTrustedDeviceUnlockEnabled(isEnabled);
    }

    public List<TrustedDeviceInfo> getEnrolledDeviceInfosForUser(int uid) {
        ICarImpl.assertTrustAgentEnrollmentPermission(this.mContext);
        Set<String> enrolledDeviceInfos = this.mTrustedDeviceService.getSharedPrefs().getStringSet(String.valueOf(uid), new HashSet());
        List<TrustedDeviceInfo> trustedDeviceInfos = new ArrayList<>(enrolledDeviceInfos.size());
        for (String deviceInfoWithId : enrolledDeviceInfos) {
            TrustedDeviceInfo deviceInfo = extractDeviceInfo(deviceInfoWithId);
            if (deviceInfo != null) {
                trustedDeviceInfos.add(deviceInfo);
            }
        }
        return trustedDeviceInfos;
    }

    public synchronized void registerEnrollmentCallback(ICarTrustAgentEnrollmentCallback listener) {
        ICarImpl.assertTrustAgentEnrollmentPermission(this.mContext);
        if (listener == null) {
            throw new IllegalArgumentException("Listener is null");
        }
        if (findEnrollmentStateClientLocked(listener) == null) {
            EnrollmentStateClient client = new EnrollmentStateClient(listener);
            try {
                listener.asBinder().linkToDeath(client, 0);
                this.mEnrollmentStateClients.add(client);
            } catch (RemoteException e) {
                Slog.e(TAG, "Cannot link death recipient to binder ", e);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void onEscrowTokenAdded(byte[] token, long handle, int uid) {
        if (Log.isLoggable(TAG, 3)) {
            Slog.d(TAG, "onEscrowTokenAdded handle:" + handle + " uid:" + uid);
        }
        if (this.mRemoteEnrollmentDevice == null) {
            Slog.e(TAG, "onEscrowTokenAdded() but no remote device connected!");
            removeEscrowToken(handle, uid);
            dispatchEnrollmentFailure(1);
            return;
        }
        this.mTokenActiveStateMap.put(Long.valueOf(handle), false);
        for (EnrollmentStateClient client : this.mEnrollmentStateClients) {
            try {
                client.mListener.onEscrowTokenAdded(handle);
            } catch (RemoteException e) {
                Slog.e(TAG, "onEscrowTokenAdded dispatch failed", e);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void onEscrowTokenRemoved(long handle, int uid) {
        if (Log.isLoggable(TAG, 3)) {
            Slog.d(TAG, "onEscrowTokenRemoved handle:" + handle + " uid:" + uid);
        }
        for (EnrollmentStateClient client : this.mEnrollmentStateClients) {
            try {
                client.mListener.onEscrowTokenRemoved(handle);
            } catch (RemoteException e) {
                Slog.e(TAG, "onEscrowTokenRemoved dispatch failed", e);
            }
        }
        SharedPreferences sharedPrefs = this.mTrustedDeviceService.getSharedPrefs();
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.remove(String.valueOf(handle));
        Set<String> deviceInfos = sharedPrefs.getStringSet(String.valueOf(uid), new HashSet());
        Iterator<String> iterator = deviceInfos.iterator();
        while (true) {
            if (!iterator.hasNext()) {
                break;
            }
            String deviceIdAndInfo = iterator.next();
            TrustedDeviceInfo info = extractDeviceInfo(deviceIdAndInfo);
            if (info != null && info.getHandle() == handle) {
                if (Log.isLoggable(TAG, 3)) {
                    Slog.d(TAG, "Removing trusted device: " + info);
                }
                String clientDeviceId = extractDeviceId(deviceIdAndInfo);
                if (clientDeviceId != null && sharedPrefs.getLong(clientDeviceId, -1L) == handle) {
                    editor.remove(clientDeviceId);
                }
                iterator.remove();
            }
        }
        editor.putStringSet(String.valueOf(uid), deviceInfos);
        if (!editor.commit()) {
            Slog.e(TAG, "EscrowToken removed, but shared prefs update failed");
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void onEscrowTokenActiveStateChanged(long handle, boolean isTokenActive, int uid) {
        String clientDeviceName;
        if (Log.isLoggable(TAG, 3)) {
            Slog.d(TAG, "onEscrowTokenActiveStateChanged: " + Long.toHexString(handle));
        }
        if (this.mRemoteEnrollmentDevice == null || !isTokenActive) {
            if (this.mRemoteEnrollmentDevice == null) {
                Slog.e(TAG, "Device disconnected before sending back handle.  Enrollment incomplete");
            }
            if (!isTokenActive) {
                Slog.e(TAG, "Unexpected: Escrow Token activation failed");
            }
            removeEscrowToken(handle, uid);
            dispatchEnrollmentFailure(1);
            return;
        }
        SharedPreferences sharedPrefs = this.mTrustedDeviceService.getSharedPrefs();
        if (sharedPrefs.contains(this.mClientDeviceId)) {
            removeEscrowToken(sharedPrefs.getLong(this.mClientDeviceId, -1L), uid);
        }
        this.mTokenActiveStateMap.put(Long.valueOf(handle), Boolean.valueOf(isTokenActive));
        Set<String> deviceInfo = sharedPrefs.getStringSet(String.valueOf(uid), new HashSet());
        if (this.mRemoteEnrollmentDevice.getName() != null) {
            clientDeviceName = this.mRemoteEnrollmentDevice.getName();
        } else {
            String clientDeviceName2 = this.mClientDeviceName;
            if (clientDeviceName2 != null) {
                clientDeviceName = this.mClientDeviceName;
            } else {
                clientDeviceName = this.mContext.getString(R.string.trust_device_default_name);
            }
        }
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append("trustedDeviceAdded (id:");
        stringBuffer.append(this.mClientDeviceId);
        stringBuffer.append(", handle:");
        stringBuffer.append(handle);
        stringBuffer.append(", uid:");
        stringBuffer.append(uid);
        stringBuffer.append(", addr:");
        stringBuffer.append(this.mRemoteEnrollmentDevice.getAddress());
        stringBuffer.append(", name:");
        stringBuffer.append(clientDeviceName);
        StringBuffer log = stringBuffer.append(")");
        addEnrollmentServiceLog(log.toString());
        deviceInfo.add(serializeDeviceInfoWithId(new TrustedDeviceInfo(handle, this.mRemoteEnrollmentDevice.getAddress(), clientDeviceName), this.mClientDeviceId));
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putStringSet(String.valueOf(uid), deviceInfo);
        if (!editor.commit()) {
            Slog.e(TAG, "Writing DeviceInfo to shared prefs Failed");
            removeEscrowToken(handle, uid);
            dispatchEnrollmentFailure(1);
            return;
        }
        editor.putInt(String.valueOf(handle), uid);
        if (!editor.commit()) {
            Slog.e(TAG, "Writing (handle, uid) to shared prefs Failed");
            removeEscrowToken(handle, uid);
            dispatchEnrollmentFailure(1);
            return;
        }
        editor.putLong(this.mClientDeviceId, handle);
        if (!editor.commit()) {
            Slog.e(TAG, "Writing (identifier, handle) to shared prefs Failed");
            removeEscrowToken(handle, uid);
            dispatchEnrollmentFailure(1);
            return;
        }
        if (Log.isLoggable(TAG, 3)) {
            Slog.d(TAG, "Sending handle: " + handle);
        }
        this.mHandle = handle;
        this.mCarTrustAgentBleManager.sendEnrollmentMessage(this.mRemoteEnrollmentDevice, this.mEncryptionKey.encryptData(Utils.longToBytes(handle)), BLEOperationProto.OperationType.CLIENT_MESSAGE, true);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void onEnrollmentAdvertiseStartSuccess() {
        for (BleStateChangeClient client : this.mBleStateChangeClients) {
            try {
                client.mListener.onEnrollmentAdvertisingStarted();
            } catch (RemoteException e) {
                Slog.e(TAG, "onAdvertiseSuccess dispatch failed", e);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void onEnrollmentAdvertiseStartFailure() {
        for (BleStateChangeClient client : this.mBleStateChangeClients) {
            try {
                client.mListener.onEnrollmentAdvertisingFailed();
            } catch (RemoteException e) {
                Slog.e(TAG, "onAdvertiseSuccess dispatch failed", e);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void onRemoteDeviceConnected(BluetoothDevice device) {
        EventLog.logEnrollmentEvent("REMOTE_DEVICE_CONNECTED");
        addEnrollmentServiceLog("onRemoteDeviceConnected (addr:" + device.getAddress() + ")");
        resetEncryptionState();
        this.mHandle = 0L;
        synchronized (this.mRemoteDeviceLock) {
            this.mRemoteEnrollmentDevice = device;
        }
        for (BleStateChangeClient client : this.mBleStateChangeClients) {
            try {
                client.mListener.onBleEnrollmentDeviceConnected(device);
            } catch (RemoteException e) {
                Slog.e(TAG, "onRemoteDeviceConnected dispatch failed", e);
            }
        }
        this.mCarTrustAgentBleManager.stopEnrollmentAdvertising();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void onRemoteDeviceDisconnected(BluetoothDevice device) {
        if (Log.isLoggable(TAG, 3)) {
            Slog.d(TAG, "Device Disconnected: " + device.getAddress() + " Enrollment State: " + this.mEnrollmentState + " Encryption State: " + this.mEncryptionState);
        }
        addEnrollmentServiceLog("onRemoteDeviceDisconnected (addr:" + device.getAddress() + ")");
        addEnrollmentServiceLog("Enrollment State: " + this.mEnrollmentState + " EncryptionState: " + this.mEncryptionState);
        resetEncryptionState();
        this.mHandle = 0L;
        synchronized (this.mRemoteDeviceLock) {
            this.mRemoteEnrollmentDevice = null;
        }
        for (BleStateChangeClient client : this.mBleStateChangeClients) {
            try {
                client.mListener.onBleEnrollmentDeviceDisconnected(device);
            } catch (RemoteException e) {
                Slog.e(TAG, "onRemoteDeviceDisconnected dispatch failed", e);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void onEnrollmentDataReceived(byte[] value) {
        if (this.mEnrollmentDelegate == null) {
            if (Log.isLoggable(TAG, 3)) {
                Slog.d(TAG, "Enrollment Delegate not set");
                return;
            }
            return;
        }
        int i = this.mEnrollmentState;
        if (i == 0) {
            if (!CarTrustAgentValidator.isValidEnrollmentDeviceId(value)) {
                Slog.e(TAG, "Device id rejected by validator.");
                return;
            }
            notifyDeviceIdReceived(value);
            EventLog.logEnrollmentEvent("RECEIVED_DEVICE_ID");
        } else if (i == 1) {
            try {
                processInitEncryptionMessage(value);
            } catch (HandshakeException e) {
                Slog.e(TAG, "HandshakeException during set up of encryption: ", e);
            }
        } else if (i == 2) {
            notifyEscrowTokenReceived(value);
        } else if (i == 3) {
            dispatchEscrowTokenActiveStateChanged(this.mHandle, true);
            this.mCarTrustAgentBleManager.disconnectRemoteDevice();
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void onDeviceNameRetrieved(String deviceName) {
        this.mClientDeviceName = deviceName;
    }

    private void notifyDeviceIdReceived(byte[] id) {
        UUID deviceId = Utils.bytesToUUID(id);
        if (deviceId == null) {
            Slog.e(TAG, "Invalid device id sent");
            return;
        }
        this.mClientDeviceId = deviceId.toString();
        if (Log.isLoggable(TAG, 3)) {
            Slog.d(TAG, "Received device id: " + this.mClientDeviceId);
        }
        UUID uniqueId = this.mTrustedDeviceService.getUniqueId();
        if (uniqueId == null) {
            Slog.e(TAG, "Cannot get Unique ID for the IHU");
            resetEnrollmentStateOnFailure();
            dispatchEnrollmentFailure(1);
            return;
        }
        if (Log.isLoggable(TAG, 3)) {
            Slog.d(TAG, "Sending device id: " + uniqueId.toString());
        }
        this.mCarTrustAgentBleManager.sendEnrollmentMessage(this.mRemoteEnrollmentDevice, Utils.uuidToBytes(uniqueId), BLEOperationProto.OperationType.CLIENT_MESSAGE, false);
        this.mEnrollmentState++;
    }

    private void notifyEscrowTokenReceived(byte[] token) {
        try {
            this.mEnrollmentDelegate.addEscrowToken(this.mEncryptionKey.decryptData(token), ActivityManager.getCurrentUser());
            this.mEnrollmentState++;
            EventLog.logEnrollmentEvent("ESCROW_TOKEN_ADDED");
        } catch (SignatureException e) {
            Slog.e(TAG, "Could not decrypt escrow token", e);
        }
    }

    private void processInitEncryptionMessage(byte[] message) throws HandshakeException {
        if (Log.isLoggable(TAG, 3)) {
            Slog.d(TAG, "Processing init encryption message.");
        }
        int i = this.mEncryptionState;
        if (i == 0) {
            if (Log.isLoggable(TAG, 3)) {
                Slog.d(TAG, "Responding to handshake init request.");
            }
            this.mHandshakeMessage = this.mEncryptionRunner.respondToInitRequest(message);
            this.mEncryptionState = this.mHandshakeMessage.getHandshakeState();
            this.mCarTrustAgentBleManager.sendEnrollmentMessage(this.mRemoteEnrollmentDevice, this.mHandshakeMessage.getNextMessage(), BLEOperationProto.OperationType.ENCRYPTION_HANDSHAKE, false);
            EventLog.logEnrollmentEvent("ENROLLMENT_ENCRYPTION_STATE", this.mEncryptionState);
        } else if (i != 1) {
            if (i != 2) {
                if (i == 3) {
                    notifyEscrowTokenReceived(message);
                    return;
                }
                Slog.w(TAG, "Encountered invalid handshake state: " + this.mEncryptionState);
                return;
            }
            Slog.w(TAG, "Encountered VERIFICATION_NEEDED state when it should have been transitioned to after IN_PROGRESS.");
            showVerificationCode();
        } else {
            if (Log.isLoggable(TAG, 3)) {
                Slog.d(TAG, "Continuing handshake.");
            }
            this.mHandshakeMessage = this.mEncryptionRunner.continueHandshake(message);
            this.mEncryptionState = this.mHandshakeMessage.getHandshakeState();
            if (Log.isLoggable(TAG, 3)) {
                Slog.d(TAG, "Updated encryption state: " + this.mEncryptionState);
            }
            if (this.mEncryptionState == 2) {
                showVerificationCode();
            } else {
                this.mCarTrustAgentBleManager.sendEnrollmentMessage(this.mRemoteEnrollmentDevice, this.mHandshakeMessage.getNextMessage(), BLEOperationProto.OperationType.ENCRYPTION_HANDSHAKE, false);
            }
        }
    }

    private void showVerificationCode() {
        if (Log.isLoggable(TAG, 3)) {
            Slog.d(TAG, "showVerificationCode(): " + this.mHandshakeMessage.getVerificationCode());
        }
        for (EnrollmentStateClient client : this.mEnrollmentStateClients) {
            try {
                client.mListener.onAuthStringAvailable(this.mRemoteEnrollmentDevice, this.mHandshakeMessage.getVerificationCode());
            } catch (RemoteException e) {
                Slog.e(TAG, "Broadcast verification code failed", e);
            }
        }
        EventLog.logEnrollmentEvent("SHOW_VERIFICATION_CODE");
    }

    private void resetEnrollmentStateOnFailure() {
        terminateEnrollmentHandshake();
        resetEncryptionState();
    }

    private void resetEncryptionState() {
        this.mEncryptionRunner = EncryptionRunnerFactory.newRunner();
        this.mHandshakeMessage = null;
        this.mEncryptionKey = null;
        this.mEncryptionState = 0;
        this.mEnrollmentState = 0;
    }

    private synchronized void setEnrollmentHandshakeAccepted() {
        if (this.mEncryptionRunner == null) {
            Slog.e(TAG, "Received notification that enrollment handshake was accepted, but encryption was never set up.");
            return;
        }
        try {
            HandshakeMessage message = this.mEncryptionRunner.verifyPin();
            if (message.getHandshakeState() != 3) {
                Slog.e(TAG, "Handshake not finished after calling verify PIN. Instead got state: " + message.getHandshakeState());
                return;
            }
            this.mEncryptionState = 3;
            this.mEncryptionKey = message.getKey();
            if (!this.mTrustedDeviceService.saveEncryptionKey(this.mClientDeviceId, this.mEncryptionKey.asBytes())) {
                resetEnrollmentStateOnFailure();
                dispatchEnrollmentFailure(1);
                return;
            }
            EventLog.logEnrollmentEvent("ENCRYPTION_KEY_SAVED");
            this.mEnrollmentState++;
        } catch (HandshakeException e) {
            Slog.e(TAG, "Error during PIN verification", e);
            resetEnrollmentStateOnFailure();
            dispatchEnrollmentFailure(1);
        }
    }

    private EnrollmentStateClient findEnrollmentStateClientLocked(ICarTrustAgentEnrollmentCallback listener) {
        IBinder binder = listener.asBinder();
        for (EnrollmentStateClient client : this.mEnrollmentStateClients) {
            if (client.isHoldingBinder(binder)) {
                return client;
            }
        }
        return null;
    }

    public synchronized void unregisterEnrollmentCallback(ICarTrustAgentEnrollmentCallback listener) {
        ICarImpl.assertTrustAgentEnrollmentPermission(this.mContext);
        if (listener == null) {
            throw new IllegalArgumentException("Listener is null");
        }
        EnrollmentStateClient client = findEnrollmentStateClientLocked(listener);
        if (client == null) {
            Slog.e(TAG, "unregisterEnrollmentCallback(): listener was not previously registered");
            return;
        }
        listener.asBinder().unlinkToDeath(client, 0);
        this.mEnrollmentStateClients.remove(client);
    }

    public synchronized void registerBleCallback(ICarTrustAgentBleCallback listener) {
        ICarImpl.assertTrustAgentEnrollmentPermission(this.mContext);
        if (listener == null) {
            throw new IllegalArgumentException("Listener is null");
        }
        if (findBleStateClientLocked(listener) == null) {
            BleStateChangeClient client = new BleStateChangeClient(listener);
            try {
                listener.asBinder().linkToDeath(client, 0);
                this.mBleStateChangeClients.add(client);
            } catch (RemoteException e) {
                Slog.e(TAG, "Cannot link death recipient to binder " + e);
            }
        }
    }

    private BleStateChangeClient findBleStateClientLocked(ICarTrustAgentBleCallback listener) {
        IBinder binder = listener.asBinder();
        for (BleStateChangeClient client : this.mBleStateChangeClients) {
            if (client.isHoldingBinder(binder)) {
                return client;
            }
        }
        return null;
    }

    public synchronized void unregisterBleCallback(ICarTrustAgentBleCallback listener) {
        ICarImpl.assertTrustAgentEnrollmentPermission(this.mContext);
        if (listener == null) {
            throw new IllegalArgumentException("Listener is null");
        }
        BleStateChangeClient client = findBleStateClientLocked(listener);
        if (client == null) {
            Slog.e(TAG, "unregisterBleCallback(): listener was not previously registered");
            return;
        }
        listener.asBinder().unlinkToDeath(client, 0);
        this.mBleStateChangeClients.remove(client);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void setEnrollmentRequestDelegate(CarTrustAgentEnrollmentRequestDelegate delegate) {
        this.mEnrollmentDelegate = delegate;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void dump(PrintWriter writer) {
        writer.println("*CarTrustAgentEnrollmentService*");
        writer.println("Enrollment Service Logs:");
        for (String log : this.mLogQueue) {
            writer.println("\t" + log);
        }
    }

    private void addEnrollmentServiceLog(String message) {
        if (this.mLogQueue.size() >= 20) {
            this.mLogQueue.remove();
        }
        Queue<String> queue = this.mLogQueue;
        queue.add(System.currentTimeMillis() + " : " + message);
    }

    private void dispatchEscrowTokenActiveStateChanged(long handle, boolean active) {
        for (EnrollmentStateClient client : this.mEnrollmentStateClients) {
            try {
                client.mListener.onEscrowTokenActiveStateChanged(handle, active);
            } catch (RemoteException e) {
                Slog.e(TAG, "Cannot notify client of a Token Activation change: " + active);
            }
        }
    }

    private void dispatchEnrollmentFailure(int error) {
        for (EnrollmentStateClient client : this.mEnrollmentStateClients) {
            try {
                client.mListener.onEnrollmentHandshakeFailure((BluetoothDevice) null, error);
            } catch (RemoteException e) {
                Slog.e(TAG, "onEnrollmentHandshakeFailure dispatch failed", e);
            }
        }
    }

    private static TrustedDeviceInfo extractDeviceInfo(String deviceInfoWithId) {
        int delimiterIndex = deviceInfoWithId.indexOf(35);
        if (delimiterIndex < 0) {
            return null;
        }
        return TrustedDeviceInfo.deserialize(deviceInfoWithId.substring(delimiterIndex + 1));
    }

    private static String extractDeviceId(String deviceInfoWithId) {
        int delimiterIndex = deviceInfoWithId.indexOf(35);
        if (delimiterIndex < 0) {
            return null;
        }
        return deviceInfoWithId.substring(0, delimiterIndex);
    }

    private static String serializeDeviceInfoWithId(TrustedDeviceInfo info, String id) {
        return id + DEVICE_INFO_DELIMITER + info.serialize();
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class EnrollmentStateClient implements IBinder.DeathRecipient {
        private final ICarTrustAgentEnrollmentCallback mListener;
        private final IBinder mListenerBinder;

        EnrollmentStateClient(ICarTrustAgentEnrollmentCallback listener) {
            this.mListener = listener;
            this.mListenerBinder = listener.asBinder();
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            if (Log.isLoggable(CarTrustAgentEnrollmentService.TAG, 3)) {
                Slog.d(CarTrustAgentEnrollmentService.TAG, "Binder died " + this.mListenerBinder);
            }
            this.mListenerBinder.unlinkToDeath(this, 0);
            synchronized (CarTrustAgentEnrollmentService.this) {
                CarTrustAgentEnrollmentService.this.mEnrollmentStateClients.remove(this);
            }
        }

        public boolean isHoldingBinder(IBinder binder) {
            return this.mListenerBinder == binder;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public class BleStateChangeClient implements IBinder.DeathRecipient {
        private final ICarTrustAgentBleCallback mListener;
        private final IBinder mListenerBinder;

        BleStateChangeClient(ICarTrustAgentBleCallback listener) {
            this.mListener = listener;
            this.mListenerBinder = listener.asBinder();
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            if (Log.isLoggable(CarTrustAgentEnrollmentService.TAG, 3)) {
                Slog.d(CarTrustAgentEnrollmentService.TAG, "Binder died " + this.mListenerBinder);
            }
            this.mListenerBinder.unlinkToDeath(this, 0);
            synchronized (CarTrustAgentEnrollmentService.this) {
                CarTrustAgentEnrollmentService.this.mBleStateChangeClients.remove(this);
            }
        }

        public boolean isHoldingBinder(IBinder binder) {
            return this.mListenerBinder == binder;
        }

        public void onEnrollmentAdvertisementStarted() {
            try {
                this.mListener.onEnrollmentAdvertisingStarted();
            } catch (RemoteException e) {
                Slog.e(CarTrustAgentEnrollmentService.TAG, "onEnrollmentAdvertisementStarted() failed", e);
            }
        }
    }
}
