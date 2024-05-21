package com.android.car.BLEStreamProtos;

import com.android.car.protobuf.ExtensionRegistryLite;
import com.android.car.protobuf.Internal;
/* loaded from: classes3.dex */
public final class BLEOperationProto {
    private BLEOperationProto() {
    }

    public static void registerAllExtensions(ExtensionRegistryLite registry) {
    }

    /* loaded from: classes3.dex */
    public enum OperationType implements Internal.EnumLite {
        OPERATION_TYPE_UNKNOWN(0),
        ENCRYPTION_HANDSHAKE(2),
        ACK(3),
        CLIENT_MESSAGE(4),
        UNRECOGNIZED(-1);
        
        public static final int ACK_VALUE = 3;
        public static final int CLIENT_MESSAGE_VALUE = 4;
        public static final int ENCRYPTION_HANDSHAKE_VALUE = 2;
        public static final int OPERATION_TYPE_UNKNOWN_VALUE = 0;
        private static final Internal.EnumLiteMap<OperationType> internalValueMap = new Internal.EnumLiteMap<OperationType>() { // from class: com.android.car.BLEStreamProtos.BLEOperationProto.OperationType.1
            /* JADX WARN: Can't rename method to resolve collision */
            @Override // com.android.car.protobuf.Internal.EnumLiteMap
            public OperationType findValueByNumber(int number) {
                return OperationType.forNumber(number);
            }
        };
        private final int value;

        @Override // com.android.car.protobuf.Internal.EnumLite
        public final int getNumber() {
            return this.value;
        }

        @Deprecated
        public static OperationType valueOf(int value) {
            return forNumber(value);
        }

        public static OperationType forNumber(int value) {
            if (value != 0) {
                if (value != 2) {
                    if (value != 3) {
                        if (value == 4) {
                            return CLIENT_MESSAGE;
                        }
                        return null;
                    }
                    return ACK;
                }
                return ENCRYPTION_HANDSHAKE;
            }
            return OPERATION_TYPE_UNKNOWN;
        }

        public static Internal.EnumLiteMap<OperationType> internalGetValueMap() {
            return internalValueMap;
        }

        OperationType(int value) {
            this.value = value;
        }
    }
}
