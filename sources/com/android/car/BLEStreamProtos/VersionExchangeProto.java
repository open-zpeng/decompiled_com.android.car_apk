package com.android.car.BLEStreamProtos;

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
public final class VersionExchangeProto {

    /* loaded from: classes3.dex */
    public interface BLEVersionExchangeOrBuilder extends MessageLiteOrBuilder {
        int getMaxSupportedMessagingVersion();

        int getMaxSupportedSecurityVersion();

        int getMinSupportedMessagingVersion();

        int getMinSupportedSecurityVersion();
    }

    private VersionExchangeProto() {
    }

    public static void registerAllExtensions(ExtensionRegistryLite registry) {
    }

    /* loaded from: classes3.dex */
    public static final class BLEVersionExchange extends GeneratedMessageLite<BLEVersionExchange, Builder> implements BLEVersionExchangeOrBuilder {
        private static final BLEVersionExchange DEFAULT_INSTANCE = new BLEVersionExchange();
        public static final int MAXSUPPORTEDMESSAGINGVERSION_FIELD_NUMBER = 2;
        public static final int MAXSUPPORTEDSECURITYVERSION_FIELD_NUMBER = 4;
        public static final int MINSUPPORTEDMESSAGINGVERSION_FIELD_NUMBER = 1;
        public static final int MINSUPPORTEDSECURITYVERSION_FIELD_NUMBER = 3;
        private static volatile Parser<BLEVersionExchange> PARSER;
        private int minSupportedMessagingVersion_ = 0;
        private int maxSupportedMessagingVersion_ = 0;
        private int minSupportedSecurityVersion_ = 0;
        private int maxSupportedSecurityVersion_ = 0;

        private BLEVersionExchange() {
        }

        @Override // com.android.car.BLEStreamProtos.VersionExchangeProto.BLEVersionExchangeOrBuilder
        public int getMinSupportedMessagingVersion() {
            return this.minSupportedMessagingVersion_;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void setMinSupportedMessagingVersion(int value) {
            this.minSupportedMessagingVersion_ = value;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void clearMinSupportedMessagingVersion() {
            this.minSupportedMessagingVersion_ = 0;
        }

        @Override // com.android.car.BLEStreamProtos.VersionExchangeProto.BLEVersionExchangeOrBuilder
        public int getMaxSupportedMessagingVersion() {
            return this.maxSupportedMessagingVersion_;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void setMaxSupportedMessagingVersion(int value) {
            this.maxSupportedMessagingVersion_ = value;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void clearMaxSupportedMessagingVersion() {
            this.maxSupportedMessagingVersion_ = 0;
        }

        @Override // com.android.car.BLEStreamProtos.VersionExchangeProto.BLEVersionExchangeOrBuilder
        public int getMinSupportedSecurityVersion() {
            return this.minSupportedSecurityVersion_;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void setMinSupportedSecurityVersion(int value) {
            this.minSupportedSecurityVersion_ = value;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void clearMinSupportedSecurityVersion() {
            this.minSupportedSecurityVersion_ = 0;
        }

        @Override // com.android.car.BLEStreamProtos.VersionExchangeProto.BLEVersionExchangeOrBuilder
        public int getMaxSupportedSecurityVersion() {
            return this.maxSupportedSecurityVersion_;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void setMaxSupportedSecurityVersion(int value) {
            this.maxSupportedSecurityVersion_ = value;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void clearMaxSupportedSecurityVersion() {
            this.maxSupportedSecurityVersion_ = 0;
        }

        @Override // com.android.car.protobuf.MessageLite
        public void writeTo(CodedOutputStream output) throws IOException {
            int i = this.minSupportedMessagingVersion_;
            if (i != 0) {
                output.writeInt32(1, i);
            }
            int i2 = this.maxSupportedMessagingVersion_;
            if (i2 != 0) {
                output.writeInt32(2, i2);
            }
            int i3 = this.minSupportedSecurityVersion_;
            if (i3 != 0) {
                output.writeInt32(3, i3);
            }
            int i4 = this.maxSupportedSecurityVersion_;
            if (i4 != 0) {
                output.writeInt32(4, i4);
            }
        }

        @Override // com.android.car.protobuf.MessageLite
        public int getSerializedSize() {
            int size = this.memoizedSerializedSize;
            if (size != -1) {
                return size;
            }
            int i = this.minSupportedMessagingVersion_;
            int size2 = i != 0 ? 0 + CodedOutputStream.computeInt32Size(1, i) : 0;
            int i2 = this.maxSupportedMessagingVersion_;
            if (i2 != 0) {
                size2 += CodedOutputStream.computeInt32Size(2, i2);
            }
            int i3 = this.minSupportedSecurityVersion_;
            if (i3 != 0) {
                size2 += CodedOutputStream.computeInt32Size(3, i3);
            }
            int i4 = this.maxSupportedSecurityVersion_;
            if (i4 != 0) {
                size2 += CodedOutputStream.computeInt32Size(4, i4);
            }
            this.memoizedSerializedSize = size2;
            return size2;
        }

        public static BLEVersionExchange parseFrom(ByteString data) throws InvalidProtocolBufferException {
            return (BLEVersionExchange) GeneratedMessageLite.parseFrom(DEFAULT_INSTANCE, data);
        }

        public static BLEVersionExchange parseFrom(ByteString data, ExtensionRegistryLite extensionRegistry) throws InvalidProtocolBufferException {
            return (BLEVersionExchange) GeneratedMessageLite.parseFrom(DEFAULT_INSTANCE, data, extensionRegistry);
        }

        public static BLEVersionExchange parseFrom(byte[] data) throws InvalidProtocolBufferException {
            return (BLEVersionExchange) GeneratedMessageLite.parseFrom(DEFAULT_INSTANCE, data);
        }

        public static BLEVersionExchange parseFrom(byte[] data, ExtensionRegistryLite extensionRegistry) throws InvalidProtocolBufferException {
            return (BLEVersionExchange) GeneratedMessageLite.parseFrom(DEFAULT_INSTANCE, data, extensionRegistry);
        }

        public static BLEVersionExchange parseFrom(InputStream input) throws IOException {
            return (BLEVersionExchange) GeneratedMessageLite.parseFrom(DEFAULT_INSTANCE, input);
        }

        public static BLEVersionExchange parseFrom(InputStream input, ExtensionRegistryLite extensionRegistry) throws IOException {
            return (BLEVersionExchange) GeneratedMessageLite.parseFrom(DEFAULT_INSTANCE, input, extensionRegistry);
        }

        public static BLEVersionExchange parseDelimitedFrom(InputStream input) throws IOException {
            return (BLEVersionExchange) parseDelimitedFrom(DEFAULT_INSTANCE, input);
        }

        public static BLEVersionExchange parseDelimitedFrom(InputStream input, ExtensionRegistryLite extensionRegistry) throws IOException {
            return (BLEVersionExchange) parseDelimitedFrom(DEFAULT_INSTANCE, input, extensionRegistry);
        }

        public static BLEVersionExchange parseFrom(CodedInputStream input) throws IOException {
            return (BLEVersionExchange) GeneratedMessageLite.parseFrom(DEFAULT_INSTANCE, input);
        }

        public static BLEVersionExchange parseFrom(CodedInputStream input, ExtensionRegistryLite extensionRegistry) throws IOException {
            return (BLEVersionExchange) GeneratedMessageLite.parseFrom(DEFAULT_INSTANCE, input, extensionRegistry);
        }

        public static Builder newBuilder() {
            return DEFAULT_INSTANCE.toBuilder();
        }

        public static Builder newBuilder(BLEVersionExchange prototype) {
            return DEFAULT_INSTANCE.toBuilder().mergeFrom((Builder) prototype);
        }

        /* loaded from: classes3.dex */
        public static final class Builder extends GeneratedMessageLite.Builder<BLEVersionExchange, Builder> implements BLEVersionExchangeOrBuilder {
            private Builder() {
                super(BLEVersionExchange.DEFAULT_INSTANCE);
            }

            @Override // com.android.car.BLEStreamProtos.VersionExchangeProto.BLEVersionExchangeOrBuilder
            public int getMinSupportedMessagingVersion() {
                return ((BLEVersionExchange) this.instance).getMinSupportedMessagingVersion();
            }

            public Builder setMinSupportedMessagingVersion(int value) {
                copyOnWrite();
                ((BLEVersionExchange) this.instance).setMinSupportedMessagingVersion(value);
                return this;
            }

            public Builder clearMinSupportedMessagingVersion() {
                copyOnWrite();
                ((BLEVersionExchange) this.instance).clearMinSupportedMessagingVersion();
                return this;
            }

            @Override // com.android.car.BLEStreamProtos.VersionExchangeProto.BLEVersionExchangeOrBuilder
            public int getMaxSupportedMessagingVersion() {
                return ((BLEVersionExchange) this.instance).getMaxSupportedMessagingVersion();
            }

            public Builder setMaxSupportedMessagingVersion(int value) {
                copyOnWrite();
                ((BLEVersionExchange) this.instance).setMaxSupportedMessagingVersion(value);
                return this;
            }

            public Builder clearMaxSupportedMessagingVersion() {
                copyOnWrite();
                ((BLEVersionExchange) this.instance).clearMaxSupportedMessagingVersion();
                return this;
            }

            @Override // com.android.car.BLEStreamProtos.VersionExchangeProto.BLEVersionExchangeOrBuilder
            public int getMinSupportedSecurityVersion() {
                return ((BLEVersionExchange) this.instance).getMinSupportedSecurityVersion();
            }

            public Builder setMinSupportedSecurityVersion(int value) {
                copyOnWrite();
                ((BLEVersionExchange) this.instance).setMinSupportedSecurityVersion(value);
                return this;
            }

            public Builder clearMinSupportedSecurityVersion() {
                copyOnWrite();
                ((BLEVersionExchange) this.instance).clearMinSupportedSecurityVersion();
                return this;
            }

            @Override // com.android.car.BLEStreamProtos.VersionExchangeProto.BLEVersionExchangeOrBuilder
            public int getMaxSupportedSecurityVersion() {
                return ((BLEVersionExchange) this.instance).getMaxSupportedSecurityVersion();
            }

            public Builder setMaxSupportedSecurityVersion(int value) {
                copyOnWrite();
                ((BLEVersionExchange) this.instance).setMaxSupportedSecurityVersion(value);
                return this;
            }

            public Builder clearMaxSupportedSecurityVersion() {
                copyOnWrite();
                ((BLEVersionExchange) this.instance).clearMaxSupportedSecurityVersion();
                return this;
            }
        }

        /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
        @Override // com.android.car.protobuf.GeneratedMessageLite
        protected final Object dynamicMethod(GeneratedMessageLite.MethodToInvoke method, Object arg0, Object arg1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE:
                    return new BLEVersionExchange();
                case IS_INITIALIZED:
                    return DEFAULT_INSTANCE;
                case MAKE_IMMUTABLE:
                    return null;
                case NEW_BUILDER:
                    return new Builder();
                case VISIT:
                    GeneratedMessageLite.Visitor visitor = (GeneratedMessageLite.Visitor) arg0;
                    BLEVersionExchange other = (BLEVersionExchange) arg1;
                    this.minSupportedMessagingVersion_ = visitor.visitInt(this.minSupportedMessagingVersion_ != 0, this.minSupportedMessagingVersion_, other.minSupportedMessagingVersion_ != 0, other.minSupportedMessagingVersion_);
                    this.maxSupportedMessagingVersion_ = visitor.visitInt(this.maxSupportedMessagingVersion_ != 0, this.maxSupportedMessagingVersion_, other.maxSupportedMessagingVersion_ != 0, other.maxSupportedMessagingVersion_);
                    this.minSupportedSecurityVersion_ = visitor.visitInt(this.minSupportedSecurityVersion_ != 0, this.minSupportedSecurityVersion_, other.minSupportedSecurityVersion_ != 0, other.minSupportedSecurityVersion_);
                    this.maxSupportedSecurityVersion_ = visitor.visitInt(this.maxSupportedSecurityVersion_ != 0, this.maxSupportedSecurityVersion_, other.maxSupportedSecurityVersion_ != 0, other.maxSupportedSecurityVersion_);
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
                                    this.minSupportedMessagingVersion_ = input.readInt32();
                                } else if (tag == 16) {
                                    this.maxSupportedMessagingVersion_ = input.readInt32();
                                } else if (tag == 24) {
                                    this.minSupportedSecurityVersion_ = input.readInt32();
                                } else if (tag != 32) {
                                    if (!input.skipField(tag)) {
                                        done = true;
                                    }
                                } else {
                                    this.maxSupportedSecurityVersion_ = input.readInt32();
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(new InvalidProtocolBufferException(e.getMessage()).setUnfinishedMessage(this));
                            }
                        } catch (InvalidProtocolBufferException e2) {
                            throw new RuntimeException(e2.setUnfinishedMessage(this));
                        }
                    }
                    break;
                case GET_DEFAULT_INSTANCE:
                    break;
                case GET_PARSER:
                    if (PARSER == null) {
                        synchronized (BLEVersionExchange.class) {
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

        public static BLEVersionExchange getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        public static Parser<BLEVersionExchange> parser() {
            return DEFAULT_INSTANCE.getParserForType();
        }
    }
}
