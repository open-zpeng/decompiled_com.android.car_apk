package com.android.car.trust;

import android.util.Log;
import android.util.Slog;
/* loaded from: classes3.dex */
final class EventLog {
    static final String BLUETOOTH_STATE_CHANGED = "BLUETOOTH_STATE_CHANGED";
    static final String CLIENT_AUTHENTICATED = "CLIENT_AUTHENTICATED";
    static final String ENCRYPTION_KEY_SAVED = "ENCRYPTION_KEY_SAVED";
    static final String ENROLLMENT_ENCRYPTION_STATE = "ENROLLMENT_ENCRYPTION_STATE";
    static final String ENROLLMENT_HANDSHAKE_ACCEPTED = "ENROLLMENT_HANDSHAKE_ACCEPTED";
    private static final String ENROLL_TAG = "CarTrustAgentEnrollmentEvent";
    static final String ESCROW_TOKEN_ADDED = "ESCROW_TOKEN_ADDED";
    static final String RECEIVED_DEVICE_ID = "RECEIVED_DEVICE_ID";
    static final String REMOTE_DEVICE_CONNECTED = "REMOTE_DEVICE_CONNECTED";
    static final String SHOW_VERIFICATION_CODE = "SHOW_VERIFICATION_CODE";
    static final String START_ENROLLMENT_ADVERTISING = "START_ENROLLMENT_ADVERTISING";
    static final String START_UNLOCK_ADVERTISING = "START_UNLOCK_ADVERTISING";
    static final String STOP_ENROLLMENT_ADVERTISING = "STOP_ENROLLMENT_ADVERTISING";
    static final String STOP_UNLOCK_ADVERTISING = "STOP_UNLOCK_ADVERTISING";
    static final String UNLOCK_CREDENTIALS_RECEIVED = "UNLOCK_CREDENTIALS_RECEIVED";
    static final String UNLOCK_ENCRYPTION_STATE = "UNLOCK_ENCRYPTION_STATE";
    static final String UNLOCK_SERVICE_INIT = "UNLOCK_SERVICE_INIT";
    private static final String UNLOCK_TAG = "CarTrustAgentUnlockEvent";
    static final String USER_UNLOCKED = "USER_UNLOCKED";
    static final String WAITING_FOR_CLIENT_AUTH = "WAITING_FOR_CLIENT_AUTH";

    private EventLog() {
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static void logUnlockEvent(String eventType) {
        if (Log.isLoggable(UNLOCK_TAG, 4)) {
            Slog.i(UNLOCK_TAG, String.format("timestamp: %d - %s", Long.valueOf(System.currentTimeMillis()), eventType));
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static void logUnlockEvent(String eventType, int value) {
        if (Log.isLoggable(UNLOCK_TAG, 4)) {
            Slog.i(UNLOCK_TAG, String.format("timestamp: %d - %s: %d", Long.valueOf(System.currentTimeMillis()), eventType, Integer.valueOf(value)));
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static void logEnrollmentEvent(String eventType) {
        if (Log.isLoggable(ENROLL_TAG, 4)) {
            Slog.i(ENROLL_TAG, String.format("timestamp: %d - %s", Long.valueOf(System.currentTimeMillis()), eventType));
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static void logEnrollmentEvent(String eventType, int value) {
        if (Log.isLoggable(ENROLL_TAG, 4)) {
            Slog.i(ENROLL_TAG, String.format("timestamp: %d - %s: %d", Long.valueOf(System.currentTimeMillis()), eventType, Integer.valueOf(value)));
        }
    }
}
