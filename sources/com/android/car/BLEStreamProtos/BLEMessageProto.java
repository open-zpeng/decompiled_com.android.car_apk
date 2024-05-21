package com.android.car.BLEStreamProtos;

import com.android.car.BLEStreamProtos.BLEOperationProto;
import com.android.car.protobuf.ByteString;
import com.android.car.protobuf.CodedInputStream;
import com.android.car.protobuf.CodedOutputStream;
import com.android.car.protobuf.ExtensionRegistryLite;
import com.android.car.protobuf.GeneratedMessageLite;
import com.android.car.protobuf.InvalidProtocolBufferException;
import com.android.car.protobuf.MessageLiteOrBuilder;
import com.android.car.protobuf.Parser;
import java.io.IOException;
import java.io.InputStream;
/* loaded from: classes3.dex */
public final class BLEMessageProto {

    /* loaded from: classes3.dex */
    public interface BLEMessageOrBuilder extends MessageLiteOrBuilder {
        boolean getIsPayloadEncrypted();

        BLEOperationProto.OperationType getOperation();

        int getOperationValue();

        int getPacketNumber();

        ByteString getPayload();

        int getTotalPackets();

        int getVersion();
    }

    private BLEMessageProto() {
    }

    public static void registerAllExtensions(ExtensionRegistryLite registry) {
    }

    /* loaded from: classes3.dex */
    public static final class BLEMessage extends GeneratedMessageLite<BLEMessage, Builder> implements BLEMessageOrBuilder {
        private static final BLEMessage DEFAULT_INSTANCE = new BLEMessage();
        public static final int IS_PAYLOAD_ENCRYPTED_FIELD_NUMBER = 5;
        public static final int OPERATION_FIELD_NUMBER = 2;
        public static final int PACKET_NUMBER_FIELD_NUMBER = 3;
        private static volatile Parser<BLEMessage> PARSER = null;
        public static final int PAYLOAD_FIELD_NUMBER = 6;
        public static final int TOTAL_PACKETS_FIELD_NUMBER = 4;
        public static final int VERSION_FIELD_NUMBER = 1;
        private int version_ = 0;
        private int operation_ = 0;
        private int packetNumber_ = 0;
        private int totalPackets_ = 0;
        private boolean isPayloadEncrypted_ = false;
        private ByteString payload_ = ByteString.EMPTY;

        private BLEMessage() {
        }

        @Override // com.android.car.BLEStreamProtos.BLEMessageProto.BLEMessageOrBuilder
        public int getVersion() {
            return this.version_;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void setVersion(int value) {
            this.version_ = value;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void clearVersion() {
            this.version_ = 0;
        }

        @Override // com.android.car.BLEStreamProtos.BLEMessageProto.BLEMessageOrBuilder
        public int getOperationValue() {
            return this.operation_;
        }

        @Override // com.android.car.BLEStreamProtos.BLEMessageProto.BLEMessageOrBuilder
        public BLEOperationProto.OperationType getOperation() {
            BLEOperationProto.OperationType result = BLEOperationProto.OperationType.forNumber(this.operation_);
            return result == null ? BLEOperationProto.OperationType.UNRECOGNIZED : result;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void setOperationValue(int value) {
            this.operation_ = value;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void setOperation(BLEOperationProto.OperationType value) {
            if (value == null) {
                throw new NullPointerException();
            }
            this.operation_ = value.getNumber();
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void clearOperation() {
            this.operation_ = 0;
        }

        @Override // com.android.car.BLEStreamProtos.BLEMessageProto.BLEMessageOrBuilder
        public int getPacketNumber() {
            return this.packetNumber_;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void setPacketNumber(int value) {
            this.packetNumber_ = value;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void clearPacketNumber() {
            this.packetNumber_ = 0;
        }

        @Override // com.android.car.BLEStreamProtos.BLEMessageProto.BLEMessageOrBuilder
        public int getTotalPackets() {
            return this.totalPackets_;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void setTotalPackets(int value) {
            this.totalPackets_ = value;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void clearTotalPackets() {
            this.totalPackets_ = 0;
        }

        @Override // com.android.car.BLEStreamProtos.BLEMessageProto.BLEMessageOrBuilder
        public boolean getIsPayloadEncrypted() {
            return this.isPayloadEncrypted_;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void setIsPayloadEncrypted(boolean value) {
            this.isPayloadEncrypted_ = value;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void clearIsPayloadEncrypted() {
            this.isPayloadEncrypted_ = false;
        }

        @Override // com.android.car.BLEStreamProtos.BLEMessageProto.BLEMessageOrBuilder
        public ByteString getPayload() {
            return this.payload_;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void setPayload(ByteString value) {
            if (value == null) {
                throw new NullPointerException();
            }
            this.payload_ = value;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void clearPayload() {
            this.payload_ = getDefaultInstance().getPayload();
        }

        @Override // com.android.car.protobuf.MessageLite
        public void writeTo(CodedOutputStream output) throws IOException {
            int i = this.version_;
            if (i != 0) {
                output.writeInt32(1, i);
            }
            if (this.operation_ != BLEOperationProto.OperationType.OPERATION_TYPE_UNKNOWN.getNumber()) {
                output.writeEnum(2, this.operation_);
            }
            int i2 = this.packetNumber_;
            if (i2 != 0) {
                output.writeFixed32(3, i2);
            }
            int i3 = this.totalPackets_;
            if (i3 != 0) {
                output.writeFixed32(4, i3);
            }
            boolean z = this.isPayloadEncrypted_;
            if (z) {
                output.writeBool(5, z);
            }
            if (!this.payload_.isEmpty()) {
                output.writeBytes(6, this.payload_);
            }
        }

        @Override // com.android.car.protobuf.MessageLite
        public int getSerializedSize() {
            int size = this.memoizedSerializedSize;
            if (size != -1) {
                return size;
            }
            int i = this.version_;
            int size2 = i != 0 ? 0 + CodedOutputStream.computeInt32Size(1, i) : 0;
            if (this.operation_ != BLEOperationProto.OperationType.OPERATION_TYPE_UNKNOWN.getNumber()) {
                size2 += CodedOutputStream.computeEnumSize(2, this.operation_);
            }
            int i2 = this.packetNumber_;
            if (i2 != 0) {
                size2 += CodedOutputStream.computeFixed32Size(3, i2);
            }
            int i3 = this.totalPackets_;
            if (i3 != 0) {
                size2 += CodedOutputStream.computeFixed32Size(4, i3);
            }
            boolean z = this.isPayloadEncrypted_;
            if (z) {
                size2 += CodedOutputStream.computeBoolSize(5, z);
            }
            if (!this.payload_.isEmpty()) {
                size2 += CodedOutputStream.computeBytesSize(6, this.payload_);
            }
            this.memoizedSerializedSize = size2;
            return size2;
        }

        public static BLEMessage parseFrom(ByteString data) throws InvalidProtocolBufferException {
            return (BLEMessage) GeneratedMessageLite.parseFrom(DEFAULT_INSTANCE, data);
        }

        public static BLEMessage parseFrom(ByteString data, ExtensionRegistryLite extensionRegistry) throws InvalidProtocolBufferException {
            return (BLEMessage) GeneratedMessageLite.parseFrom(DEFAULT_INSTANCE, data, extensionRegistry);
        }

        public static BLEMessage parseFrom(byte[] data) throws InvalidProtocolBufferException {
            return (BLEMessage) GeneratedMessageLite.parseFrom(DEFAULT_INSTANCE, data);
        }

        public static BLEMessage parseFrom(byte[] data, ExtensionRegistryLite extensionRegistry) throws InvalidProtocolBufferException {
            return (BLEMessage) GeneratedMessageLite.parseFrom(DEFAULT_INSTANCE, data, extensionRegistry);
        }

        public static BLEMessage parseFrom(InputStream input) throws IOException {
            return (BLEMessage) GeneratedMessageLite.parseFrom(DEFAULT_INSTANCE, input);
        }

        public static BLEMessage parseFrom(InputStream input, ExtensionRegistryLite extensionRegistry) throws IOException {
            return (BLEMessage) GeneratedMessageLite.parseFrom(DEFAULT_INSTANCE, input, extensionRegistry);
        }

        public static BLEMessage parseDelimitedFrom(InputStream input) throws IOException {
            return (BLEMessage) parseDelimitedFrom(DEFAULT_INSTANCE, input);
        }

        public static BLEMessage parseDelimitedFrom(InputStream input, ExtensionRegistryLite extensionRegistry) throws IOException {
            return (BLEMessage) parseDelimitedFrom(DEFAULT_INSTANCE, input, extensionRegistry);
        }

        public static BLEMessage parseFrom(CodedInputStream input) throws IOException {
            return (BLEMessage) GeneratedMessageLite.parseFrom(DEFAULT_INSTANCE, input);
        }

        public static BLEMessage parseFrom(CodedInputStream input, ExtensionRegistryLite extensionRegistry) throws IOException {
            return (BLEMessage) GeneratedMessageLite.parseFrom(DEFAULT_INSTANCE, input, extensionRegistry);
        }

        public static Builder newBuilder() {
            return DEFAULT_INSTANCE.toBuilder();
        }

        public static Builder newBuilder(BLEMessage prototype) {
            return DEFAULT_INSTANCE.toBuilder().mergeFrom((Builder) prototype);
        }

        /* loaded from: classes3.dex */
        public static final class Builder extends GeneratedMessageLite.Builder<BLEMessage, Builder> implements BLEMessageOrBuilder {
            private Builder() {
                super(BLEMessage.DEFAULT_INSTANCE);
            }

            @Override // com.android.car.BLEStreamProtos.BLEMessageProto.BLEMessageOrBuilder
            public int getVersion() {
                return ((BLEMessage) this.instance).getVersion();
            }

            public Builder setVersion(int value) {
                copyOnWrite();
                ((BLEMessage) this.instance).setVersion(value);
                return this;
            }

            public Builder clearVersion() {
                copyOnWrite();
                ((BLEMessage) this.instance).clearVersion();
                return this;
            }

            @Override // com.android.car.BLEStreamProtos.BLEMessageProto.BLEMessageOrBuilder
            public int getOperationValue() {
                return ((BLEMessage) this.instance).getOperationValue();
            }

            public Builder setOperationValue(int value) {
                copyOnWrite();
                ((BLEMessage) this.instance).setOperationValue(value);
                return this;
            }

            @Override // com.android.car.BLEStreamProtos.BLEMessageProto.BLEMessageOrBuilder
            public BLEOperationProto.OperationType getOperation() {
                return ((BLEMessage) this.instance).getOperation();
            }

            public Builder setOperation(BLEOperationProto.OperationType value) {
                copyOnWrite();
                ((BLEMessage) this.instance).setOperation(value);
                return this;
            }

            public Builder clearOperation() {
                copyOnWrite();
                ((BLEMessage) this.instance).clearOperation();
                return this;
            }

            @Override // com.android.car.BLEStreamProtos.BLEMessageProto.BLEMessageOrBuilder
            public int getPacketNumber() {
                return ((BLEMessage) this.instance).getPacketNumber();
            }

            public Builder setPacketNumber(int value) {
                copyOnWrite();
                ((BLEMessage) this.instance).setPacketNumber(value);
                return this;
            }

            public Builder clearPacketNumber() {
                copyOnWrite();
                ((BLEMessage) this.instance).clearPacketNumber();
                return this;
            }

            @Override // com.android.car.BLEStreamProtos.BLEMessageProto.BLEMessageOrBuilder
            public int getTotalPackets() {
                return ((BLEMessage) this.instance).getTotalPackets();
            }

            public Builder setTotalPackets(int value) {
                copyOnWrite();
                ((BLEMessage) this.instance).setTotalPackets(value);
                return this;
            }

            public Builder clearTotalPackets() {
                copyOnWrite();
                ((BLEMessage) this.instance).clearTotalPackets();
                return this;
            }

            @Override // com.android.car.BLEStreamProtos.BLEMessageProto.BLEMessageOrBuilder
            public boolean getIsPayloadEncrypted() {
                return ((BLEMessage) this.instance).getIsPayloadEncrypted();
            }

            public Builder setIsPayloadEncrypted(boolean value) {
                copyOnWrite();
                ((BLEMessage) this.instance).setIsPayloadEncrypted(value);
                return this;
            }

            public Builder clearIsPayloadEncrypted() {
                copyOnWrite();
                ((BLEMessage) this.instance).clearIsPayloadEncrypted();
                return this;
            }

            @Override // com.android.car.BLEStreamProtos.BLEMessageProto.BLEMessageOrBuilder
            public ByteString getPayload() {
                return ((BLEMessage) this.instance).getPayload();
            }

            public Builder setPayload(ByteString value) {
                copyOnWrite();
                ((BLEMessage) this.instance).setPayload(value);
                return this;
            }

            public Builder clearPayload() {
                copyOnWrite();
                ((BLEMessage) this.instance).clearPayload();
                return this;
            }
        }

        /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
        @Override // com.android.car.protobuf.GeneratedMessageLite
        protected final Object dynamicMethod(GeneratedMessageLite.MethodToInvoke method, Object arg0, Object arg1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new BLEMessage();
                case IS_INITIALIZED:
                    return DEFAULT_INSTANCE;
                case MAKE_IMMUTABLE:
                    return null;
                case NEW_BUILDER:
                    return new Builder();
                case VISIT:
                    GeneratedMessageLite.Visitor visitor = (GeneratedMessageLite.Visitor) arg0;
                    BLEMessage other = (BLEMessage) arg1;
                    this.version_ = visitor.visitInt(this.version_ != 0, this.version_, other.version_ != 0, other.version_);
                    this.operation_ = visitor.visitInt(this.operation_ != 0, this.operation_, other.operation_ != 0, other.operation_);
                    this.packetNumber_ = visitor.visitInt(this.packetNumber_ != 0, this.packetNumber_, other.packetNumber_ != 0, other.packetNumber_);
                    this.totalPackets_ = visitor.visitInt(this.totalPackets_ != 0, this.totalPackets_, other.totalPackets_ != 0, other.totalPackets_);
                    boolean z = this.isPayloadEncrypted_;
                    boolean z2 = other.isPayloadEncrypted_;
                    this.isPayloadEncrypted_ = visitor.visitBoolean(z, z, z2, z2);
                    this.payload_ = visitor.visitByteString(this.payload_ != ByteString.EMPTY, this.payload_, other.payload_ != ByteString.EMPTY, other.payload_);
                    GeneratedMessageLite.MergeFromVisitor mergeFromVisitor = GeneratedMessageLite.MergeFromVisitor.INSTANCE;
                    return this;
                case MERGE_FROM_STREAM:
                    CodedInputStream input = (CodedInputStream) arg0;
                    ExtensionRegistryLite extensionRegistryLite = (ExtensionRegistryLite) arg1;
                    boolean done = false;
                    while (!done) {
                        try {
                            try {
                                int tag = input.readTag();
                                if (tag == 0) {
                                    done = true;
                                } else if (tag == 8) {
                                    int rawValue = input.readInt32();
                                    this.version_ = rawValue;
                                } else if (tag == 16) {
                                    int rawValue2 = input.readEnum();
                                    this.operation_ = rawValue2;
                                } else if (tag == 29) {
                                    this.packetNumber_ = input.readFixed32();
                                } else if (tag == 37) {
                                    this.totalPackets_ = input.readFixed32();
                                } else if (tag == 40) {
                                    this.isPayloadEncrypted_ = input.readBool();
                                } else if (tag != 50) {
                                    if (!input.skipField(tag)) {
                                        done = true;
                                    }
                                } else {
                                    this.payload_ = input.readBytes();
                                }
                            } catch (InvalidProtocolBufferException e) {
                                throw new RuntimeException(e.setUnfinishedMessage(this));
                            }
                        } catch (IOException e2) {
                            throw new RuntimeException(new InvalidProtocolBufferException(e2.getMessage()).setUnfinishedMessage(this));
                        }
                    }
                    break;
                case GET_DEFAULT_INSTANCE:
                    break;
                case GET_PARSER:
                    if (PARSER == null) {
                        synchronized (BLEMessage.class) {
                            if (PARSER == null) {
                                PARSER = new GeneratedMessageLite.DefaultInstanceBasedParser(DEFAULT_INSTANCE);
                            }
                        }
                    }
                    return PARSER;
                default:
                    throw new UnsupportedOperationException();
            }
            return DEFAULT_INSTANCE;
        }

        static {
            DEFAULT_INSTANCE.makeImmutable();
        }

        public static BLEMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        public static Parser<BLEMessage> parser() {
            return DEFAULT_INSTANCE.getParserForType();
        }
    }
}
