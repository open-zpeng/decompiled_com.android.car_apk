package com.android.car.PhoneAuthProtos;

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
public final class PhoneAuthProto {

    /* loaded from: classes3.dex */
    public interface PhoneCredentialsOrBuilder extends MessageLiteOrBuilder {
        ByteString getEscrowToken();

        ByteString getHandle();
    }

    private PhoneAuthProto() {
    }

    public static void registerAllExtensions(ExtensionRegistryLite registry) {
    }

    /* loaded from: classes3.dex */
    public static final class PhoneCredentials extends GeneratedMessageLite<PhoneCredentials, Builder> implements PhoneCredentialsOrBuilder {
        private static final PhoneCredentials DEFAULT_INSTANCE = new PhoneCredentials();
        public static final int ESCROW_TOKEN_FIELD_NUMBER = 1;
        public static final int HANDLE_FIELD_NUMBER = 2;
        private static volatile Parser<PhoneCredentials> PARSER;
        private ByteString escrowToken_ = ByteString.EMPTY;
        private ByteString handle_ = ByteString.EMPTY;

        private PhoneCredentials() {
        }

        @Override // com.android.car.PhoneAuthProtos.PhoneAuthProto.PhoneCredentialsOrBuilder
        public ByteString getEscrowToken() {
            return this.escrowToken_;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void setEscrowToken(ByteString value) {
            if (value == null) {
                throw new NullPointerException();
            }
            this.escrowToken_ = value;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void clearEscrowToken() {
            this.escrowToken_ = getDefaultInstance().getEscrowToken();
        }

        @Override // com.android.car.PhoneAuthProtos.PhoneAuthProto.PhoneCredentialsOrBuilder
        public ByteString getHandle() {
            return this.handle_;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void setHandle(ByteString value) {
            if (value == null) {
                throw new NullPointerException();
            }
            this.handle_ = value;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void clearHandle() {
            this.handle_ = getDefaultInstance().getHandle();
        }

        @Override // com.android.car.protobuf.MessageLite
        public void writeTo(CodedOutputStream output) throws IOException {
            if (!this.escrowToken_.isEmpty()) {
                output.writeBytes(1, this.escrowToken_);
            }
            if (!this.handle_.isEmpty()) {
                output.writeBytes(2, this.handle_);
            }
        }

        @Override // com.android.car.protobuf.MessageLite
        public int getSerializedSize() {
            int size = this.memoizedSerializedSize;
            if (size != -1) {
                return size;
            }
            int size2 = this.escrowToken_.isEmpty() ? 0 : 0 + CodedOutputStream.computeBytesSize(1, this.escrowToken_);
            if (!this.handle_.isEmpty()) {
                size2 += CodedOutputStream.computeBytesSize(2, this.handle_);
            }
            this.memoizedSerializedSize = size2;
            return size2;
        }

        public static PhoneCredentials parseFrom(ByteString data) throws InvalidProtocolBufferException {
            return (PhoneCredentials) GeneratedMessageLite.parseFrom(DEFAULT_INSTANCE, data);
        }

        public static PhoneCredentials parseFrom(ByteString data, ExtensionRegistryLite extensionRegistry) throws InvalidProtocolBufferException {
            return (PhoneCredentials) GeneratedMessageLite.parseFrom(DEFAULT_INSTANCE, data, extensionRegistry);
        }

        public static PhoneCredentials parseFrom(byte[] data) throws InvalidProtocolBufferException {
            return (PhoneCredentials) GeneratedMessageLite.parseFrom(DEFAULT_INSTANCE, data);
        }

        public static PhoneCredentials parseFrom(byte[] data, ExtensionRegistryLite extensionRegistry) throws InvalidProtocolBufferException {
            return (PhoneCredentials) GeneratedMessageLite.parseFrom(DEFAULT_INSTANCE, data, extensionRegistry);
        }

        public static PhoneCredentials parseFrom(InputStream input) throws IOException {
            return (PhoneCredentials) GeneratedMessageLite.parseFrom(DEFAULT_INSTANCE, input);
        }

        public static PhoneCredentials parseFrom(InputStream input, ExtensionRegistryLite extensionRegistry) throws IOException {
            return (PhoneCredentials) GeneratedMessageLite.parseFrom(DEFAULT_INSTANCE, input, extensionRegistry);
        }

        public static PhoneCredentials parseDelimitedFrom(InputStream input) throws IOException {
            return (PhoneCredentials) parseDelimitedFrom(DEFAULT_INSTANCE, input);
        }

        public static PhoneCredentials parseDelimitedFrom(InputStream input, ExtensionRegistryLite extensionRegistry) throws IOException {
            return (PhoneCredentials) parseDelimitedFrom(DEFAULT_INSTANCE, input, extensionRegistry);
        }

        public static PhoneCredentials parseFrom(CodedInputStream input) throws IOException {
            return (PhoneCredentials) GeneratedMessageLite.parseFrom(DEFAULT_INSTANCE, input);
        }

        public static PhoneCredentials parseFrom(CodedInputStream input, ExtensionRegistryLite extensionRegistry) throws IOException {
            return (PhoneCredentials) GeneratedMessageLite.parseFrom(DEFAULT_INSTANCE, input, extensionRegistry);
        }

        public static Builder newBuilder() {
            return DEFAULT_INSTANCE.toBuilder();
        }

        public static Builder newBuilder(PhoneCredentials prototype) {
            return DEFAULT_INSTANCE.toBuilder().mergeFrom((Builder) prototype);
        }

        /* loaded from: classes3.dex */
        public static final class Builder extends GeneratedMessageLite.Builder<PhoneCredentials, Builder> implements PhoneCredentialsOrBuilder {
            private Builder() {
                super(PhoneCredentials.DEFAULT_INSTANCE);
            }

            @Override // com.android.car.PhoneAuthProtos.PhoneAuthProto.PhoneCredentialsOrBuilder
            public ByteString getEscrowToken() {
                return ((PhoneCredentials) this.instance).getEscrowToken();
            }

            public Builder setEscrowToken(ByteString value) {
                copyOnWrite();
                ((PhoneCredentials) this.instance).setEscrowToken(value);
                return this;
            }

            public Builder clearEscrowToken() {
                copyOnWrite();
                ((PhoneCredentials) this.instance).clearEscrowToken();
                return this;
            }

            @Override // com.android.car.PhoneAuthProtos.PhoneAuthProto.PhoneCredentialsOrBuilder
            public ByteString getHandle() {
                return ((PhoneCredentials) this.instance).getHandle();
            }

            public Builder setHandle(ByteString value) {
                copyOnWrite();
                ((PhoneCredentials) this.instance).setHandle(value);
                return this;
            }

            public Builder clearHandle() {
                copyOnWrite();
                ((PhoneCredentials) this.instance).clearHandle();
                return this;
            }
        }

        /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
        @Override // com.android.car.protobuf.GeneratedMessageLite
        protected final Object dynamicMethod(GeneratedMessageLite.MethodToInvoke method, Object arg0, Object arg1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new PhoneCredentials();
                case IS_INITIALIZED:
                    return DEFAULT_INSTANCE;
                case MAKE_IMMUTABLE:
                    return null;
                case NEW_BUILDER:
                    return new Builder();
                case VISIT:
                    GeneratedMessageLite.Visitor visitor = (GeneratedMessageLite.Visitor) arg0;
                    PhoneCredentials other = (PhoneCredentials) arg1;
                    this.escrowToken_ = visitor.visitByteString(this.escrowToken_ != ByteString.EMPTY, this.escrowToken_, other.escrowToken_ != ByteString.EMPTY, other.escrowToken_);
                    this.handle_ = visitor.visitByteString(this.handle_ != ByteString.EMPTY, this.handle_, other.handle_ != ByteString.EMPTY, other.handle_);
                    GeneratedMessageLite.MergeFromVisitor mergeFromVisitor = GeneratedMessageLite.MergeFromVisitor.INSTANCE;
                    return this;
                case MERGE_FROM_STREAM:
                    CodedInputStream input = (CodedInputStream) arg0;
                    ExtensionRegistryLite extensionRegistryLite = (ExtensionRegistryLite) arg1;
                    boolean done = false;
                    while (!done) {
                        try {
                            int tag = input.readTag();
                            if (tag == 0) {
                                done = true;
                            } else if (tag == 10) {
                                this.escrowToken_ = input.readBytes();
                            } else if (tag != 18) {
                                if (!input.skipField(tag)) {
                                    done = true;
                                }
                            } else {
                                this.handle_ = input.readBytes();
                            }
                        } catch (InvalidProtocolBufferException e) {
                            throw new RuntimeException(e.setUnfinishedMessage(this));
                        } catch (IOException e2) {
                            throw new RuntimeException(new InvalidProtocolBufferException(e2.getMessage()).setUnfinishedMessage(this));
                        }
                    }
                    break;
                case GET_DEFAULT_INSTANCE:
                    break;
                case GET_PARSER:
                    if (PARSER == null) {
                        synchronized (PhoneCredentials.class) {
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

        public static PhoneCredentials getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        public static Parser<PhoneCredentials> parser() {
            return DEFAULT_INSTANCE.getParserForType();
        }
    }
}
