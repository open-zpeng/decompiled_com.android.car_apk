package com.android.car.trust;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;
import android.util.Slog;
import androidx.annotation.Nullable;
import com.android.car.BLEStreamProtos.BLEMessageProto;
import com.android.car.BLEStreamProtos.BLEOperationProto;
import com.android.car.BLEStreamProtos.VersionExchangeProto;
import com.android.car.CarLocalServices;
import com.android.car.R;
import com.android.car.Utils;
import com.android.car.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
/* JADX INFO: Access modifiers changed from: package-private */
/* loaded from: classes3.dex */
public class CarTrustAgentBleManager extends BleManager {
    private static final int ATT_PAYLOAD_RESERVED_BYTES = 3;
    private static final int BLE_MESSAGE_RETRY_LIMIT = 20;
    private static final String TAG = "CarTrustBLEManager";
    private static final int TRUSTED_DEVICE_OPERATION_ENROLLMENT = 1;
    private static final int TRUSTED_DEVICE_OPERATION_NONE = 0;
    private static final int TRUSTED_DEVICE_OPERATION_UNLOCK = 2;
    private BLEMessagePayloadStream mBleMessagePayloadStream;
    private int mBleMessageRetryStartCount;
    private CarTrustAgentEnrollmentService mCarTrustAgentEnrollmentService;
    private CarTrustAgentUnlockService mCarTrustAgentUnlockService;
    private CarTrustedDeviceService mCarTrustedDeviceService;
    private int mCurrentTrustedDeviceOperation;
    private final AdvertiseCallback mEnrollmentAdvertisingCallback;
    private UUID mEnrollmentClientWriteUuid;
    private String mEnrollmentDeviceName;
    private BluetoothGattService mEnrollmentGattService;
    private UUID mEnrollmentServerWriteUuid;
    private UUID mEnrollmentServiceUuid;
    private Handler mHandler;
    private boolean mIsVersionExchanged;
    private int mMaxWriteSize;
    private Queue<BLEMessageProto.BLEMessage> mMessageQueue;
    private String mOriginalBluetoothName;
    private Runnable mSendRepeatedBleMessage;
    private byte[] mUniqueId;
    private final AdvertiseCallback mUnlockAdvertisingCallback;
    private UUID mUnlockClientWriteUuid;
    private BluetoothGattService mUnlockGattService;
    private UUID mUnlockServerWriteUuid;
    private UUID mUnlockServiceUuid;
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final long BLE_MESSAGE_RETRY_DELAY_MS = TimeUnit.SECONDS.toMillis(2);

    @Retention(RetentionPolicy.SOURCE)
    /* loaded from: classes3.dex */
    public @interface TrustedDeviceOperation {
    }

    static /* synthetic */ int access$008(CarTrustAgentBleManager x0) {
        int i = x0.mBleMessageRetryStartCount;
        x0.mBleMessageRetryStartCount = i + 1;
        return i;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public CarTrustAgentBleManager(Context context) {
        super(context);
        this.mCurrentTrustedDeviceOperation = 0;
        this.mMaxWriteSize = 20;
        this.mMessageQueue = new LinkedList();
        this.mBleMessagePayloadStream = new BLEMessagePayloadStream();
        this.mHandler = new Handler(Looper.getMainLooper());
        this.mEnrollmentAdvertisingCallback = new AdvertiseCallback() { // from class: com.android.car.trust.CarTrustAgentBleManager.2
            @Override // android.bluetooth.le.AdvertiseCallback
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                super.onStartSuccess(settingsInEffect);
                if (CarTrustAgentBleManager.this.getEnrollmentService() != null) {
                    CarTrustAgentBleManager.this.getEnrollmentService().onEnrollmentAdvertiseStartSuccess();
                }
                if (Log.isLoggable(CarTrustAgentBleManager.TAG, 3)) {
                    Slog.d(CarTrustAgentBleManager.TAG, "Successfully started advertising service");
                }
            }

            @Override // android.bluetooth.le.AdvertiseCallback
            public void onStartFailure(int errorCode) {
                Slog.e(CarTrustAgentBleManager.TAG, "Failed to advertise, errorCode: " + errorCode);
                super.onStartFailure(errorCode);
                if (CarTrustAgentBleManager.this.getEnrollmentService() != null) {
                    CarTrustAgentBleManager.this.getEnrollmentService().onEnrollmentAdvertiseStartFailure();
                }
            }
        };
        this.mUnlockAdvertisingCallback = new AdvertiseCallback() { // from class: com.android.car.trust.CarTrustAgentBleManager.3
            @Override // android.bluetooth.le.AdvertiseCallback
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                super.onStartSuccess(settingsInEffect);
                if (Log.isLoggable(CarTrustAgentBleManager.TAG, 3)) {
                    Slog.d(CarTrustAgentBleManager.TAG, "Unlock Advertising onStartSuccess");
                }
            }

            @Override // android.bluetooth.le.AdvertiseCallback
            public void onStartFailure(int errorCode) {
                Slog.e(CarTrustAgentBleManager.TAG, "Failed to advertise, errorCode: " + errorCode);
                super.onStartFailure(errorCode);
                if (errorCode == 3) {
                    return;
                }
                if (Log.isLoggable(CarTrustAgentBleManager.TAG, 3)) {
                    Slog.d(CarTrustAgentBleManager.TAG, "Start unlock advertising fail, retry to advertising..");
                }
                CarTrustAgentBleManager.this.setupUnlockBleServer();
                CarTrustAgentBleManager.this.startUnlockAdvertising();
            }
        };
    }

    @Override // com.android.car.trust.BleManager
    public void onRemoteDeviceConnected(BluetoothDevice device) {
        if (getTrustedDeviceService() == null) {
            return;
        }
        if (this.mCurrentTrustedDeviceOperation == 1 && device.getName() == null) {
            retrieveDeviceName(device);
        }
        this.mMessageQueue.clear();
        this.mIsVersionExchanged = false;
        getTrustedDeviceService().onRemoteDeviceConnected(device);
        Runnable runnable = this.mSendRepeatedBleMessage;
        if (runnable != null) {
            this.mHandler.removeCallbacks(runnable);
            this.mSendRepeatedBleMessage = null;
        }
    }

    @Override // com.android.car.trust.BleManager
    public void onRemoteDeviceDisconnected(BluetoothDevice device) {
        if (getTrustedDeviceService() != null) {
            getTrustedDeviceService().onRemoteDeviceDisconnected(device);
        }
        this.mMessageQueue.clear();
        this.mIsVersionExchanged = false;
        this.mBleMessagePayloadStream.reset();
        Runnable runnable = this.mSendRepeatedBleMessage;
        if (runnable != null) {
            this.mHandler.removeCallbacks(runnable);
        }
        this.mSendRepeatedBleMessage = null;
    }

    @Override // com.android.car.trust.BleManager
    protected void onDeviceNameRetrieved(@Nullable String deviceName) {
        if (getTrustedDeviceService() != null) {
            getTrustedDeviceService().onDeviceNameRetrieved(deviceName);
        }
    }

    @Override // com.android.car.trust.BleManager
    protected void onMtuSizeChanged(int size) {
        this.mMaxWriteSize = size - 3;
        if (Log.isLoggable(TAG, 3)) {
            Slog.d(TAG, "MTU size changed to: " + size + "; setting max payload size to: " + this.mMaxWriteSize);
        }
    }

    @Override // com.android.car.trust.BleManager
    public void onCharacteristicWrite(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
        UUID uuid = characteristic.getUuid();
        if (Log.isLoggable(TAG, 3)) {
            Slog.d(TAG, "onCharacteristicWrite received uuid: " + uuid);
        }
        if (!this.mIsVersionExchanged) {
            resolveBLEVersion(device, value, uuid);
            return;
        }
        try {
            BLEMessageProto.BLEMessage message = BLEMessageProto.BLEMessage.parseFrom(value);
            if (message.getOperation() == BLEOperationProto.OperationType.ACK) {
                handleClientAckMessage(device, uuid);
                return;
            }
            try {
                this.mBleMessagePayloadStream.write(message);
                if (!this.mBleMessagePayloadStream.isComplete()) {
                    sendAcknowledgmentMessage(device, uuid);
                    return;
                }
                if (uuid.equals(this.mEnrollmentClientWriteUuid)) {
                    if (getEnrollmentService() != null) {
                        getEnrollmentService().onEnrollmentDataReceived(this.mBleMessagePayloadStream.toByteArray());
                    }
                } else if (uuid.equals(this.mUnlockClientWriteUuid) && getUnlockService() != null) {
                    getUnlockService().onUnlockDataReceived(this.mBleMessagePayloadStream.toByteArray());
                }
                this.mBleMessagePayloadStream.reset();
            } catch (IOException e) {
                Slog.e(TAG, "Can write the BLE message's payload", e);
            }
        } catch (InvalidProtocolBufferException e2) {
            Slog.e(TAG, "Can not parse BLE message", e2);
        }
    }

    @Override // com.android.car.trust.BleManager
    public void onCharacteristicRead(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
    }

    @Nullable
    private CarTrustedDeviceService getTrustedDeviceService() {
        if (this.mCarTrustedDeviceService == null) {
            this.mCarTrustedDeviceService = (CarTrustedDeviceService) CarLocalServices.getService(CarTrustedDeviceService.class);
        }
        return this.mCarTrustedDeviceService;
    }

    /* JADX INFO: Access modifiers changed from: private */
    @Nullable
    public CarTrustAgentEnrollmentService getEnrollmentService() {
        CarTrustAgentEnrollmentService carTrustAgentEnrollmentService = this.mCarTrustAgentEnrollmentService;
        if (carTrustAgentEnrollmentService != null) {
            return carTrustAgentEnrollmentService;
        }
        if (getTrustedDeviceService() != null) {
            this.mCarTrustAgentEnrollmentService = getTrustedDeviceService().getCarTrustAgentEnrollmentService();
        }
        return this.mCarTrustAgentEnrollmentService;
    }

    @Nullable
    private CarTrustAgentUnlockService getUnlockService() {
        CarTrustAgentUnlockService carTrustAgentUnlockService = this.mCarTrustAgentUnlockService;
        if (carTrustAgentUnlockService != null) {
            return carTrustAgentUnlockService;
        }
        if (getTrustedDeviceService() != null) {
            this.mCarTrustAgentUnlockService = getTrustedDeviceService().getCarTrustAgentUnlockService();
        }
        return this.mCarTrustAgentUnlockService;
    }

    @Nullable
    private byte[] getUniqueId() {
        byte[] bArr = this.mUniqueId;
        if (bArr != null) {
            return bArr;
        }
        if (getTrustedDeviceService() != null && getTrustedDeviceService().getUniqueId() != null) {
            this.mUniqueId = Utils.uuidToBytes(getTrustedDeviceService().getUniqueId());
        }
        return this.mUniqueId;
    }

    @Nullable
    private String getEnrollmentDeviceName() {
        String str = this.mEnrollmentDeviceName;
        if (str != null) {
            return str;
        }
        if (getTrustedDeviceService() != null) {
            this.mEnrollmentDeviceName = getTrustedDeviceService().getEnrollmentDeviceName();
        }
        return this.mEnrollmentDeviceName;
    }

    private void resolveBLEVersion(BluetoothDevice device, byte[] value, UUID clientCharacteristicUUID) {
        BluetoothGattCharacteristic characteristic = getCharacteristicForWrite(clientCharacteristicUUID);
        if (characteristic == null) {
            Slog.e(TAG, "Invalid UUID (" + clientCharacteristicUUID + ") during version exchange; disconnecting from remote device.");
            disconnectRemoteDevice();
            return;
        }
        try {
            VersionExchangeProto.BLEVersionExchange deviceVersion = VersionExchangeProto.BLEVersionExchange.parseFrom(value);
            if (!BLEVersionExchangeResolver.hasSupportedVersion(deviceVersion)) {
                Slog.e(TAG, "No supported version found during version exchange.");
                disconnectRemoteDevice();
                return;
            }
            VersionExchangeProto.BLEVersionExchange headunitVersion = BLEVersionExchangeResolver.makeVersionExchange();
            setValueOnCharacteristicAndNotify(device, headunitVersion.toByteArray(), characteristic);
            if (Log.isLoggable(TAG, 3)) {
                Slog.d(TAG, "Sent supported version to the phone.");
            }
            this.mIsVersionExchanged = true;
        } catch (InvalidProtocolBufferException e) {
            disconnectRemoteDevice();
            Slog.e(TAG, "Could not parse version exchange message", e);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void setupEnrollmentBleServer() {
        this.mEnrollmentServiceUuid = UUID.fromString(getContext().getString(R.string.enrollment_service_uuid));
        this.mEnrollmentClientWriteUuid = UUID.fromString(getContext().getString(R.string.enrollment_client_write_uuid));
        this.mEnrollmentServerWriteUuid = UUID.fromString(getContext().getString(R.string.enrollment_server_write_uuid));
        this.mEnrollmentGattService = new BluetoothGattService(this.mEnrollmentServiceUuid, 0);
        BluetoothGattCharacteristic clientCharacteristic = new BluetoothGattCharacteristic(this.mEnrollmentClientWriteUuid, 12, 16);
        BluetoothGattCharacteristic serverCharacteristic = new BluetoothGattCharacteristic(this.mEnrollmentServerWriteUuid, 16, 1);
        addDescriptorToCharacteristic(serverCharacteristic);
        this.mEnrollmentGattService.addCharacteristic(clientCharacteristic);
        this.mEnrollmentGattService.addCharacteristic(serverCharacteristic);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void setupUnlockBleServer() {
        this.mUnlockServiceUuid = UUID.fromString(getContext().getString(R.string.unlock_service_uuid));
        this.mUnlockClientWriteUuid = UUID.fromString(getContext().getString(R.string.unlock_client_write_uuid));
        this.mUnlockServerWriteUuid = UUID.fromString(getContext().getString(R.string.unlock_server_write_uuid));
        this.mUnlockGattService = new BluetoothGattService(this.mUnlockServiceUuid, 0);
        BluetoothGattCharacteristic clientCharacteristic = new BluetoothGattCharacteristic(this.mUnlockClientWriteUuid, 12, 16);
        BluetoothGattCharacteristic serverCharacteristic = new BluetoothGattCharacteristic(this.mUnlockServerWriteUuid, 16, 1);
        addDescriptorToCharacteristic(serverCharacteristic);
        this.mUnlockGattService.addCharacteristic(clientCharacteristic);
        this.mUnlockGattService.addCharacteristic(serverCharacteristic);
    }

    private void addDescriptorToCharacteristic(BluetoothGattCharacteristic characteristic) {
        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(CLIENT_CHARACTERISTIC_CONFIG, 17);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        characteristic.addDescriptor(descriptor);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void startEnrollmentAdvertising() {
        this.mCurrentTrustedDeviceOperation = 1;
        String name = getEnrollmentDeviceName();
        if (name != null) {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (this.mOriginalBluetoothName == null) {
                this.mOriginalBluetoothName = adapter.getName();
            }
            adapter.setName(name);
            if (Log.isLoggable(TAG, 3)) {
                Slog.d(TAG, "Changing bluetooth adapter name from " + this.mOriginalBluetoothName + " to " + name);
            }
        }
        startAdvertising(this.mEnrollmentGattService, new AdvertiseData.Builder().setIncludeDeviceName(true).addServiceUuid(new ParcelUuid(this.mEnrollmentServiceUuid)).build(), this.mEnrollmentAdvertisingCallback);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void stopEnrollmentAdvertising() {
        if (this.mOriginalBluetoothName != null) {
            if (Log.isLoggable(TAG, 3)) {
                Slog.d(TAG, "Changing bluetooth adapter name back to " + this.mOriginalBluetoothName);
            }
            BluetoothAdapter.getDefaultAdapter().setName(this.mOriginalBluetoothName);
        }
        stopAdvertising(this.mEnrollmentAdvertisingCallback);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void startUnlockAdvertising() {
        this.mCurrentTrustedDeviceOperation = 2;
        startAdvertising(this.mUnlockGattService, new AdvertiseData.Builder().setIncludeDeviceName(false).addServiceData(new ParcelUuid(this.mUnlockServiceUuid), getUniqueId()).addServiceUuid(new ParcelUuid(this.mUnlockServiceUuid)).build(), this.mUnlockAdvertisingCallback);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void stopUnlockAdvertising() {
        this.mCurrentTrustedDeviceOperation = 0;
        stopAdvertising(this.mUnlockAdvertisingCallback);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void disconnectRemoteDevice() {
        stopGattServer();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void sendUnlockMessage(BluetoothDevice device, byte[] message, BLEOperationProto.OperationType operation, boolean isPayloadEncrypted) {
        BluetoothGattCharacteristic writeCharacteristic = this.mUnlockGattService.getCharacteristic(this.mUnlockServerWriteUuid);
        sendMessage(device, writeCharacteristic, message, operation, isPayloadEncrypted);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void sendEnrollmentMessage(BluetoothDevice device, byte[] message, BLEOperationProto.OperationType operation, boolean isPayloadEncrypted) {
        BluetoothGattCharacteristic writeCharacteristic = this.mEnrollmentGattService.getCharacteristic(this.mEnrollmentServerWriteUuid);
        sendMessage(device, writeCharacteristic, message, operation, isPayloadEncrypted);
    }

    private void handleClientAckMessage(BluetoothDevice device, UUID clientCharacteristicUUID) {
        if (Log.isLoggable(TAG, 3)) {
            Slog.d(TAG, "Received ACK from client. Attempting to write next message in queue. UUID: " + clientCharacteristicUUID);
        }
        BluetoothGattCharacteristic writeCharacteristic = getCharacteristicForWrite(clientCharacteristicUUID);
        if (writeCharacteristic == null) {
            Slog.e(TAG, "No corresponding write characteristic found for writing next message in queue. UUID: " + clientCharacteristicUUID);
            return;
        }
        Runnable runnable = this.mSendRepeatedBleMessage;
        if (runnable != null) {
            this.mHandler.removeCallbacks(runnable);
            this.mSendRepeatedBleMessage = null;
        }
        this.mMessageQueue.remove();
        writeNextMessageInQueue(device, writeCharacteristic);
    }

    private void sendMessage(BluetoothDevice device, BluetoothGattCharacteristic characteristic, byte[] message, BLEOperationProto.OperationType operation, boolean isPayloadEncrypted) {
        if (Log.isLoggable(TAG, 3)) {
            Slog.d(TAG, "sendMessage to: " + device.getAddress() + "; and characteristic UUID: " + characteristic.getUuid());
        }
        List<BLEMessageProto.BLEMessage> bleMessages = BLEMessageV1Factory.makeBLEMessages(message, operation, this.mMaxWriteSize, isPayloadEncrypted);
        if (Log.isLoggable(TAG, 3)) {
            Slog.d(TAG, "sending " + bleMessages.size() + " messages to device");
        }
        this.mMessageQueue.addAll(bleMessages);
        writeNextMessageInQueue(device, characteristic);
    }

    private void writeNextMessageInQueue(final BluetoothDevice device, final BluetoothGattCharacteristic characteristic) {
        if (this.mMessageQueue.isEmpty()) {
            Slog.e(TAG, "Call to write next message in queue, but the message queue is empty");
        } else if (this.mMessageQueue.size() == 1) {
            setValueOnCharacteristicAndNotify(device, this.mMessageQueue.remove().toByteArray(), characteristic);
        } else {
            this.mBleMessageRetryStartCount = 0;
            this.mSendRepeatedBleMessage = new Runnable() { // from class: com.android.car.trust.CarTrustAgentBleManager.1
                @Override // java.lang.Runnable
                public void run() {
                    if (Log.isLoggable(CarTrustAgentBleManager.TAG, 3)) {
                        Slog.d(CarTrustAgentBleManager.TAG, "BLE message sending... retry count: " + CarTrustAgentBleManager.this.mBleMessageRetryStartCount);
                    }
                    if (CarTrustAgentBleManager.this.mBleMessageRetryStartCount >= 20) {
                        Slog.e(CarTrustAgentBleManager.TAG, "Error during BLE message sending - exceeded retry limit.");
                        CarTrustAgentBleManager.this.mHandler.removeCallbacks(this);
                        CarTrustAgentBleManager.this.mCarTrustAgentEnrollmentService.terminateEnrollmentHandshake();
                        CarTrustAgentBleManager.this.mSendRepeatedBleMessage = null;
                        return;
                    }
                    CarTrustAgentBleManager carTrustAgentBleManager = CarTrustAgentBleManager.this;
                    carTrustAgentBleManager.setValueOnCharacteristicAndNotify(device, ((BLEMessageProto.BLEMessage) carTrustAgentBleManager.mMessageQueue.peek()).toByteArray(), characteristic);
                    CarTrustAgentBleManager.access$008(CarTrustAgentBleManager.this);
                    CarTrustAgentBleManager.this.mHandler.postDelayed(this, CarTrustAgentBleManager.BLE_MESSAGE_RETRY_DELAY_MS);
                }
            };
            this.mHandler.post(this.mSendRepeatedBleMessage);
        }
    }

    private void sendAcknowledgmentMessage(BluetoothDevice device, UUID clientCharacteristicUUID) {
        BluetoothGattCharacteristic writeCharacteristic = getCharacteristicForWrite(clientCharacteristicUUID);
        if (writeCharacteristic == null) {
            Slog.e(TAG, "No corresponding write characteristic found for sending ACK. UUID: " + clientCharacteristicUUID);
            return;
        }
        setValueOnCharacteristicAndNotify(device, BLEMessageV1Factory.makeAcknowledgementMessage().toByteArray(), writeCharacteristic);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setValueOnCharacteristicAndNotify(BluetoothDevice device, byte[] message, BluetoothGattCharacteristic characteristic) {
        characteristic.setValue(message);
        notifyCharacteristicChanged(device, characteristic, false);
    }

    @Nullable
    private BluetoothGattCharacteristic getCharacteristicForWrite(UUID uuid) {
        if (uuid.equals(this.mEnrollmentClientWriteUuid)) {
            return this.mEnrollmentGattService.getCharacteristic(this.mEnrollmentServerWriteUuid);
        }
        if (uuid.equals(this.mUnlockClientWriteUuid)) {
            return this.mUnlockGattService.getCharacteristic(this.mUnlockServerWriteUuid);
        }
        return null;
    }
}
