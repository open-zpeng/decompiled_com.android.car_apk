package com.android.car.protobuf;

import com.android.car.protobuf.AbstractMessageLite;
import com.android.car.protobuf.FieldSet;
import com.android.car.protobuf.GeneratedMessageLite;
import com.android.car.protobuf.GeneratedMessageLite.Builder;
import com.android.car.protobuf.Internal;
import com.android.car.protobuf.MessageLite;
import com.android.car.protobuf.WireFormat;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
/* loaded from: classes3.dex */
public abstract class GeneratedMessageLite<MessageType extends GeneratedMessageLite<MessageType, BuilderType>, BuilderType extends Builder<MessageType, BuilderType>> extends AbstractMessageLite<MessageType, BuilderType> {
    protected UnknownFieldSetLite unknownFields = UnknownFieldSetLite.getDefaultInstance();
    protected int memoizedSerializedSize = -1;

    /* loaded from: classes3.dex */
    public interface ExtendableMessageOrBuilder<MessageType extends ExtendableMessage<MessageType, BuilderType>, BuilderType extends ExtendableBuilder<MessageType, BuilderType>> extends MessageLiteOrBuilder {
        <Type> Type getExtension(ExtensionLite<MessageType, Type> extensionLite);

        <Type> Type getExtension(ExtensionLite<MessageType, List<Type>> extensionLite, int i);

        <Type> int getExtensionCount(ExtensionLite<MessageType, List<Type>> extensionLite);

        <Type> boolean hasExtension(ExtensionLite<MessageType, Type> extensionLite);
    }

    /* loaded from: classes3.dex */
    public enum MethodToInvoke {
        IS_INITIALIZED,
        VISIT,
        MERGE_FROM_STREAM,
        MAKE_IMMUTABLE,
        NEW_MUTABLE_INSTANCE,
        NEW_BUILDER,
        GET_DEFAULT_INSTANCE,
        GET_PARSER
    }

    /* JADX INFO: Access modifiers changed from: protected */
    /* loaded from: classes3.dex */
    public interface Visitor {
        boolean visitBoolean(boolean z, boolean z2, boolean z3, boolean z4);

        Internal.BooleanList visitBooleanList(Internal.BooleanList booleanList, Internal.BooleanList booleanList2);

        ByteString visitByteString(boolean z, ByteString byteString, boolean z2, ByteString byteString2);

        double visitDouble(boolean z, double d, boolean z2, double d2);

        Internal.DoubleList visitDoubleList(Internal.DoubleList doubleList, Internal.DoubleList doubleList2);

        FieldSet<ExtensionDescriptor> visitExtensions(FieldSet<ExtensionDescriptor> fieldSet, FieldSet<ExtensionDescriptor> fieldSet2);

        float visitFloat(boolean z, float f, boolean z2, float f2);

        Internal.FloatList visitFloatList(Internal.FloatList floatList, Internal.FloatList floatList2);

        int visitInt(boolean z, int i, boolean z2, int i2);

        Internal.IntList visitIntList(Internal.IntList intList, Internal.IntList intList2);

        LazyFieldLite visitLazyMessage(boolean z, LazyFieldLite lazyFieldLite, boolean z2, LazyFieldLite lazyFieldLite2);

        <T> Internal.ProtobufList<T> visitList(Internal.ProtobufList<T> protobufList, Internal.ProtobufList<T> protobufList2);

        long visitLong(boolean z, long j, boolean z2, long j2);

        Internal.LongList visitLongList(Internal.LongList longList, Internal.LongList longList2);

        <K, V> MapFieldLite<K, V> visitMap(MapFieldLite<K, V> mapFieldLite, MapFieldLite<K, V> mapFieldLite2);

        <T extends MessageLite> T visitMessage(T t, T t2);

        Object visitOneofBoolean(boolean z, Object obj, Object obj2);

        Object visitOneofByteString(boolean z, Object obj, Object obj2);

        Object visitOneofDouble(boolean z, Object obj, Object obj2);

        Object visitOneofFloat(boolean z, Object obj, Object obj2);

        Object visitOneofInt(boolean z, Object obj, Object obj2);

        Object visitOneofLazyMessage(boolean z, Object obj, Object obj2);

        Object visitOneofLong(boolean z, Object obj, Object obj2);

        Object visitOneofMessage(boolean z, Object obj, Object obj2);

        void visitOneofNotSet(boolean z);

        Object visitOneofString(boolean z, Object obj, Object obj2);

        String visitString(boolean z, String str, boolean z2, String str2);

        UnknownFieldSetLite visitUnknownFields(UnknownFieldSetLite unknownFieldSetLite, UnknownFieldSetLite unknownFieldSetLite2);
    }

    protected abstract Object dynamicMethod(MethodToInvoke methodToInvoke, Object obj, Object obj2);

    @Override // com.android.car.protobuf.MessageLite
    public final Parser<MessageType> getParserForType() {
        return (Parser) dynamicMethod(MethodToInvoke.GET_PARSER);
    }

    @Override // com.android.car.protobuf.MessageLiteOrBuilder
    public final MessageType getDefaultInstanceForType() {
        return (MessageType) dynamicMethod(MethodToInvoke.GET_DEFAULT_INSTANCE);
    }

    @Override // com.android.car.protobuf.MessageLite
    public final BuilderType newBuilderForType() {
        return (BuilderType) dynamicMethod(MethodToInvoke.NEW_BUILDER);
    }

    public String toString() {
        return MessageLiteToString.toString(this, super.toString());
    }

    public int hashCode() {
        if (this.memoizedHashCode == 0) {
            HashCodeVisitor visitor = new HashCodeVisitor(null);
            visit(visitor, this);
            this.memoizedHashCode = visitor.hashCode;
        }
        return this.memoizedHashCode;
    }

    int hashCode(HashCodeVisitor visitor) {
        if (this.memoizedHashCode == 0) {
            int inProgressHashCode = visitor.hashCode;
            visitor.hashCode = 0;
            visit(visitor, this);
            this.memoizedHashCode = visitor.hashCode;
            visitor.hashCode = inProgressHashCode;
        }
        int inProgressHashCode2 = this.memoizedHashCode;
        return inProgressHashCode2;
    }

    /* JADX WARN: Multi-variable type inference failed */
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (getDefaultInstanceForType().getClass().isInstance(other)) {
            try {
                visit(EqualsVisitor.INSTANCE, (GeneratedMessageLite) other);
                return true;
            } catch (EqualsVisitor.NotEqualsException e) {
                return false;
            }
        }
        return false;
    }

    /* JADX WARN: Multi-variable type inference failed */
    boolean equals(EqualsVisitor visitor, MessageLite other) {
        if (this == other) {
            return true;
        }
        if (!getDefaultInstanceForType().getClass().isInstance(other)) {
            return false;
        }
        visit(visitor, (GeneratedMessageLite) other);
        return true;
    }

    private final void ensureUnknownFieldsInitialized() {
        if (this.unknownFields == UnknownFieldSetLite.getDefaultInstance()) {
            this.unknownFields = UnknownFieldSetLite.newInstance();
        }
    }

    protected boolean parseUnknownField(int tag, CodedInputStream input) throws IOException {
        if (WireFormat.getTagWireType(tag) == 4) {
            return false;
        }
        ensureUnknownFieldsInitialized();
        return this.unknownFields.mergeFieldFrom(tag, input);
    }

    protected void mergeVarintField(int tag, int value) {
        ensureUnknownFieldsInitialized();
        this.unknownFields.mergeVarintField(tag, value);
    }

    protected void mergeLengthDelimitedField(int fieldNumber, ByteString value) {
        ensureUnknownFieldsInitialized();
        this.unknownFields.mergeLengthDelimitedField(fieldNumber, value);
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public void makeImmutable() {
        dynamicMethod(MethodToInvoke.MAKE_IMMUTABLE);
        this.unknownFields.makeImmutable();
    }

    @Override // com.android.car.protobuf.MessageLiteOrBuilder
    public final boolean isInitialized() {
        return dynamicMethod(MethodToInvoke.IS_INITIALIZED, Boolean.TRUE) != null;
    }

    @Override // com.android.car.protobuf.MessageLite
    public final BuilderType toBuilder() {
        BuilderType builder = (BuilderType) dynamicMethod(MethodToInvoke.NEW_BUILDER);
        builder.mergeFrom(this);
        return builder;
    }

    protected Object dynamicMethod(MethodToInvoke method, Object arg0) {
        return dynamicMethod(method, arg0, null);
    }

    protected Object dynamicMethod(MethodToInvoke method) {
        return dynamicMethod(method, null, null);
    }

    void visit(Visitor visitor, MessageType other) {
        dynamicMethod(MethodToInvoke.VISIT, visitor, other);
        this.unknownFields = visitor.visitUnknownFields(this.unknownFields, other.unknownFields);
    }

    protected final void mergeUnknownFields(UnknownFieldSetLite unknownFields) {
        this.unknownFields = UnknownFieldSetLite.mutableCopyOf(this.unknownFields, unknownFields);
    }

    /* loaded from: classes3.dex */
    public static abstract class Builder<MessageType extends GeneratedMessageLite<MessageType, BuilderType>, BuilderType extends Builder<MessageType, BuilderType>> extends AbstractMessageLite.Builder<MessageType, BuilderType> {
        private final MessageType defaultInstance;
        protected MessageType instance;
        protected boolean isBuilt = false;

        /* JADX WARN: Multi-variable type inference failed */
        @Override // com.android.car.protobuf.AbstractMessageLite.Builder
        protected /* bridge */ /* synthetic */ AbstractMessageLite.Builder internalMergeFrom(AbstractMessageLite abstractMessageLite) {
            return internalMergeFrom((Builder<MessageType, BuilderType>) ((GeneratedMessageLite) abstractMessageLite));
        }

        /* JADX INFO: Access modifiers changed from: protected */
        public Builder(MessageType defaultInstance) {
            this.defaultInstance = defaultInstance;
            this.instance = (MessageType) defaultInstance.dynamicMethod(MethodToInvoke.NEW_MUTABLE_INSTANCE);
        }

        /* JADX INFO: Access modifiers changed from: protected */
        public void copyOnWrite() {
            if (this.isBuilt) {
                MessageType newInstance = (MessageType) this.instance.dynamicMethod(MethodToInvoke.NEW_MUTABLE_INSTANCE);
                newInstance.visit(MergeFromVisitor.INSTANCE, this.instance);
                this.instance = newInstance;
                this.isBuilt = false;
            }
        }

        @Override // com.android.car.protobuf.MessageLiteOrBuilder
        public final boolean isInitialized() {
            return GeneratedMessageLite.isInitialized(this.instance, false);
        }

        @Override // com.android.car.protobuf.MessageLite.Builder
        public final BuilderType clear() {
            this.instance = (MessageType) this.instance.dynamicMethod(MethodToInvoke.NEW_MUTABLE_INSTANCE);
            return this;
        }

        @Override // com.android.car.protobuf.AbstractMessageLite.Builder
        /* renamed from: clone */
        public BuilderType mo8clone() {
            BuilderType builder = (BuilderType) getDefaultInstanceForType().newBuilderForType();
            builder.mergeFrom(buildPartial());
            return builder;
        }

        @Override // com.android.car.protobuf.MessageLite.Builder
        public MessageType buildPartial() {
            if (this.isBuilt) {
                return this.instance;
            }
            this.instance.makeImmutable();
            this.isBuilt = true;
            return this.instance;
        }

        @Override // com.android.car.protobuf.MessageLite.Builder
        public final MessageType build() {
            MessageType result = buildPartial();
            if (!result.isInitialized()) {
                throw newUninitializedMessageException(result);
            }
            return result;
        }

        protected BuilderType internalMergeFrom(MessageType message) {
            return mergeFrom((Builder<MessageType, BuilderType>) message);
        }

        public BuilderType mergeFrom(MessageType message) {
            copyOnWrite();
            this.instance.visit(MergeFromVisitor.INSTANCE, message);
            return this;
        }

        @Override // com.android.car.protobuf.MessageLiteOrBuilder
        public MessageType getDefaultInstanceForType() {
            return this.defaultInstance;
        }

        @Override // com.android.car.protobuf.AbstractMessageLite.Builder, com.android.car.protobuf.MessageLite.Builder
        public BuilderType mergeFrom(CodedInputStream input, ExtensionRegistryLite extensionRegistry) throws IOException {
            copyOnWrite();
            try {
                this.instance.dynamicMethod(MethodToInvoke.MERGE_FROM_STREAM, input, extensionRegistry);
                return this;
            } catch (RuntimeException e) {
                if (e.getCause() instanceof IOException) {
                    throw ((IOException) e.getCause());
                }
                throw e;
            }
        }
    }

    /* loaded from: classes3.dex */
    public static abstract class ExtendableMessage<MessageType extends ExtendableMessage<MessageType, BuilderType>, BuilderType extends ExtendableBuilder<MessageType, BuilderType>> extends GeneratedMessageLite<MessageType, BuilderType> implements ExtendableMessageOrBuilder<MessageType, BuilderType> {
        protected FieldSet<ExtensionDescriptor> extensions = FieldSet.newFieldSet();

        /* JADX WARN: Multi-variable type inference failed */
        @Override // com.android.car.protobuf.GeneratedMessageLite
        /* bridge */ /* synthetic */ void visit(Visitor visitor, GeneratedMessageLite generatedMessageLite) {
            visit(visitor, (Visitor) ((ExtendableMessage) generatedMessageLite));
        }

        protected final void mergeExtensionFields(MessageType other) {
            if (this.extensions.isImmutable()) {
                this.extensions = this.extensions.m9clone();
            }
            this.extensions.mergeFrom(other.extensions);
        }

        final void visit(Visitor visitor, MessageType other) {
            super.visit(visitor, (Visitor) other);
            this.extensions = visitor.visitExtensions(this.extensions, other.extensions);
        }

        protected <MessageType extends MessageLite> boolean parseUnknownField(MessageType defaultInstance, CodedInputStream input, ExtensionRegistryLite extensionRegistry, int tag) throws IOException {
            Object value;
            MessageLite existingValue;
            int wireType = WireFormat.getTagWireType(tag);
            int fieldNumber = WireFormat.getTagFieldNumber(tag);
            GeneratedExtension<MessageType, ?> extension = extensionRegistry.findLiteExtensionByNumber(defaultInstance, fieldNumber);
            boolean unknown = false;
            boolean packed = false;
            boolean z = true;
            if (extension == null) {
                unknown = true;
            } else if (wireType == FieldSet.getWireFormatForFieldType(extension.descriptor.getLiteType(), false)) {
                packed = false;
            } else if (extension.descriptor.isRepeated && extension.descriptor.type.isPackable() && wireType == FieldSet.getWireFormatForFieldType(extension.descriptor.getLiteType(), true)) {
                packed = true;
            } else {
                unknown = true;
            }
            if (unknown) {
                return parseUnknownField(tag, input);
            }
            if (packed) {
                int length = input.readRawVarint32();
                int limit = input.pushLimit(length);
                if (extension.descriptor.getLiteType() == WireFormat.FieldType.ENUM) {
                    while (input.getBytesUntilLimit() > 0) {
                        Object value2 = extension.descriptor.getEnumType().findValueByNumber(input.readEnum());
                        if (value2 == null) {
                            return z;
                        }
                        this.extensions.addRepeatedField(extension.descriptor, extension.singularToFieldSetType(value2));
                        wireType = wireType;
                        z = true;
                    }
                } else {
                    while (input.getBytesUntilLimit() > 0) {
                        Object value3 = FieldSet.readPrimitiveField(input, extension.descriptor.getLiteType(), false);
                        this.extensions.addRepeatedField(extension.descriptor, value3);
                    }
                }
                input.popLimit(limit);
                return true;
            }
            int i = AnonymousClass1.$SwitchMap$com$google$protobuf$WireFormat$JavaType[extension.descriptor.getLiteJavaType().ordinal()];
            if (i == 1) {
                MessageLite.Builder subBuilder = null;
                if (!extension.descriptor.isRepeated() && (existingValue = (MessageLite) this.extensions.getField(extension.descriptor)) != null) {
                    subBuilder = existingValue.toBuilder();
                }
                if (subBuilder == null) {
                    subBuilder = extension.getMessageDefaultInstance().newBuilderForType();
                }
                if (extension.descriptor.getLiteType() == WireFormat.FieldType.GROUP) {
                    input.readGroup(extension.getNumber(), subBuilder, extensionRegistry);
                } else {
                    input.readMessage(subBuilder, extensionRegistry);
                }
                value = subBuilder.build();
            } else if (i == 2) {
                int rawValue = input.readEnum();
                value = extension.descriptor.getEnumType().findValueByNumber(rawValue);
                if (value == null) {
                    mergeVarintField(fieldNumber, rawValue);
                    return true;
                }
            } else {
                value = FieldSet.readPrimitiveField(input, extension.descriptor.getLiteType(), false);
            }
            if (extension.descriptor.isRepeated()) {
                this.extensions.addRepeatedField(extension.descriptor, extension.singularToFieldSetType(value));
                return true;
            }
            this.extensions.setField(extension.descriptor, extension.singularToFieldSetType(value));
            return true;
        }

        private void verifyExtensionContainingType(GeneratedExtension<MessageType, ?> extension) {
            if (extension.getContainingTypeDefaultInstance() != getDefaultInstanceForType()) {
                throw new IllegalArgumentException("This extension is for a different message type.  Please make sure that you are not suppressing any generics type warnings.");
            }
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.ExtendableMessageOrBuilder
        public final <Type> boolean hasExtension(ExtensionLite<MessageType, Type> extension) {
            GeneratedExtension<MessageType, ?> checkIsLite = GeneratedMessageLite.checkIsLite(extension);
            verifyExtensionContainingType(checkIsLite);
            return this.extensions.hasField(checkIsLite.descriptor);
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.ExtendableMessageOrBuilder
        public final <Type> int getExtensionCount(ExtensionLite<MessageType, List<Type>> extension) {
            GeneratedExtension<MessageType, List<Type>> extensionLite = GeneratedMessageLite.checkIsLite(extension);
            verifyExtensionContainingType(extensionLite);
            return this.extensions.getRepeatedFieldCount(extensionLite.descriptor);
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.ExtendableMessageOrBuilder
        public final <Type> Type getExtension(ExtensionLite<MessageType, Type> extension) {
            GeneratedExtension<MessageType, ?> checkIsLite = GeneratedMessageLite.checkIsLite(extension);
            verifyExtensionContainingType(checkIsLite);
            Object value = this.extensions.getField(checkIsLite.descriptor);
            if (value == null) {
                return checkIsLite.defaultValue;
            }
            return (Type) checkIsLite.fromFieldSetType(value);
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.ExtendableMessageOrBuilder
        public final <Type> Type getExtension(ExtensionLite<MessageType, List<Type>> extension, int index) {
            GeneratedExtension<MessageType, List<Type>> extensionLite = GeneratedMessageLite.checkIsLite(extension);
            verifyExtensionContainingType(extensionLite);
            return (Type) extensionLite.singularFromFieldSetType(this.extensions.getRepeatedField(extensionLite.descriptor, index));
        }

        protected boolean extensionsAreInitialized() {
            return this.extensions.isInitialized();
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite
        protected final void makeImmutable() {
            super.makeImmutable();
            this.extensions.makeImmutable();
        }

        /* loaded from: classes3.dex */
        protected class ExtensionWriter {
            private final Iterator<Map.Entry<ExtensionDescriptor, Object>> iter;
            private final boolean messageSetWireFormat;
            private Map.Entry<ExtensionDescriptor, Object> next;

            /* synthetic */ ExtensionWriter(ExtendableMessage x0, boolean x1, AnonymousClass1 x2) {
                this(x1);
            }

            private ExtensionWriter(boolean messageSetWireFormat) {
                this.iter = ExtendableMessage.this.extensions.iterator();
                if (this.iter.hasNext()) {
                    this.next = this.iter.next();
                }
                this.messageSetWireFormat = messageSetWireFormat;
            }

            public void writeUntil(int end, CodedOutputStream output) throws IOException {
                while (true) {
                    Map.Entry<ExtensionDescriptor, Object> entry = this.next;
                    if (entry != null && entry.getKey().getNumber() < end) {
                        ExtensionDescriptor extension = this.next.getKey();
                        if (this.messageSetWireFormat && extension.getLiteJavaType() == WireFormat.JavaType.MESSAGE && !extension.isRepeated()) {
                            output.writeMessageSetExtension(extension.getNumber(), (MessageLite) this.next.getValue());
                        } else {
                            FieldSet.writeField(extension, this.next.getValue(), output);
                        }
                        if (this.iter.hasNext()) {
                            this.next = this.iter.next();
                        } else {
                            this.next = null;
                        }
                    } else {
                        return;
                    }
                }
            }
        }

        protected ExtendableMessage<MessageType, BuilderType>.ExtensionWriter newExtensionWriter() {
            return new ExtensionWriter(this, false, null);
        }

        protected ExtendableMessage<MessageType, BuilderType>.ExtensionWriter newMessageSetExtensionWriter() {
            return new ExtensionWriter(this, true, null);
        }

        protected int extensionsSerializedSize() {
            return this.extensions.getSerializedSize();
        }

        protected int extensionsSerializedSizeAsMessageSet() {
            return this.extensions.getMessageSetSerializedSize();
        }
    }

    /* renamed from: com.android.car.protobuf.GeneratedMessageLite$1  reason: invalid class name */
    /* loaded from: classes3.dex */
    static /* synthetic */ class AnonymousClass1 {
        static final /* synthetic */ int[] $SwitchMap$com$google$protobuf$WireFormat$JavaType = new int[WireFormat.JavaType.values().length];

        static {
            try {
                $SwitchMap$com$google$protobuf$WireFormat$JavaType[WireFormat.JavaType.MESSAGE.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$google$protobuf$WireFormat$JavaType[WireFormat.JavaType.ENUM.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
        }
    }

    /* loaded from: classes3.dex */
    public static abstract class ExtendableBuilder<MessageType extends ExtendableMessage<MessageType, BuilderType>, BuilderType extends ExtendableBuilder<MessageType, BuilderType>> extends Builder<MessageType, BuilderType> implements ExtendableMessageOrBuilder<MessageType, BuilderType> {
        protected ExtendableBuilder(MessageType defaultInstance) {
            super(defaultInstance);
            ((ExtendableMessage) this.instance).extensions = ((ExtendableMessage) this.instance).extensions.m9clone();
        }

        void internalSetExtensionSet(FieldSet<ExtensionDescriptor> extensions) {
            copyOnWrite();
            ((ExtendableMessage) this.instance).extensions = extensions;
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // com.android.car.protobuf.GeneratedMessageLite.Builder
        public void copyOnWrite() {
            if (!this.isBuilt) {
                return;
            }
            super.copyOnWrite();
            ((ExtendableMessage) this.instance).extensions = ((ExtendableMessage) this.instance).extensions.m9clone();
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Builder, com.android.car.protobuf.MessageLite.Builder
        public final MessageType buildPartial() {
            if (this.isBuilt) {
                return (MessageType) this.instance;
            }
            ((ExtendableMessage) this.instance).extensions.makeImmutable();
            return (MessageType) super.buildPartial();
        }

        private void verifyExtensionContainingType(GeneratedExtension<MessageType, ?> extension) {
            if (extension.getContainingTypeDefaultInstance() != getDefaultInstanceForType()) {
                throw new IllegalArgumentException("This extension is for a different message type.  Please make sure that you are not suppressing any generics type warnings.");
            }
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.ExtendableMessageOrBuilder
        public final <Type> boolean hasExtension(ExtensionLite<MessageType, Type> extension) {
            return ((ExtendableMessage) this.instance).hasExtension(extension);
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.ExtendableMessageOrBuilder
        public final <Type> int getExtensionCount(ExtensionLite<MessageType, List<Type>> extension) {
            return ((ExtendableMessage) this.instance).getExtensionCount(extension);
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.ExtendableMessageOrBuilder
        public final <Type> Type getExtension(ExtensionLite<MessageType, Type> extension) {
            return (Type) ((ExtendableMessage) this.instance).getExtension(extension);
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.ExtendableMessageOrBuilder
        public final <Type> Type getExtension(ExtensionLite<MessageType, List<Type>> extension, int index) {
            return (Type) ((ExtendableMessage) this.instance).getExtension(extension, index);
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Builder, com.android.car.protobuf.AbstractMessageLite.Builder
        /* renamed from: clone */
        public BuilderType mo8clone() {
            return (BuilderType) super.mo8clone();
        }

        public final <Type> BuilderType setExtension(ExtensionLite<MessageType, Type> extension, Type value) {
            GeneratedExtension<MessageType, ?> checkIsLite = GeneratedMessageLite.checkIsLite(extension);
            verifyExtensionContainingType(checkIsLite);
            copyOnWrite();
            ((ExtendableMessage) this.instance).extensions.setField(checkIsLite.descriptor, checkIsLite.toFieldSetType(value));
            return this;
        }

        public final <Type> BuilderType setExtension(ExtensionLite<MessageType, List<Type>> extension, int index, Type value) {
            GeneratedExtension<MessageType, List<Type>> extensionLite = GeneratedMessageLite.checkIsLite(extension);
            verifyExtensionContainingType(extensionLite);
            copyOnWrite();
            ((ExtendableMessage) this.instance).extensions.setRepeatedField(extensionLite.descriptor, index, extensionLite.singularToFieldSetType(value));
            return this;
        }

        public final <Type> BuilderType addExtension(ExtensionLite<MessageType, List<Type>> extension, Type value) {
            GeneratedExtension<MessageType, List<Type>> extensionLite = GeneratedMessageLite.checkIsLite(extension);
            verifyExtensionContainingType(extensionLite);
            copyOnWrite();
            ((ExtendableMessage) this.instance).extensions.addRepeatedField(extensionLite.descriptor, extensionLite.singularToFieldSetType(value));
            return this;
        }

        public final <Type> BuilderType clearExtension(ExtensionLite<MessageType, ?> extension) {
            GeneratedExtension<MessageType, ?> extensionLite = GeneratedMessageLite.checkIsLite(extension);
            verifyExtensionContainingType(extensionLite);
            copyOnWrite();
            ((ExtendableMessage) this.instance).extensions.clearField(extensionLite.descriptor);
            return this;
        }
    }

    public static <ContainingType extends MessageLite, Type> GeneratedExtension<ContainingType, Type> newSingularGeneratedExtension(ContainingType containingTypeDefaultInstance, Type defaultValue, MessageLite messageDefaultInstance, Internal.EnumLiteMap<?> enumTypeMap, int number, WireFormat.FieldType type, Class singularType) {
        return new GeneratedExtension<>(containingTypeDefaultInstance, defaultValue, messageDefaultInstance, new ExtensionDescriptor(enumTypeMap, number, type, false, false), singularType);
    }

    public static <ContainingType extends MessageLite, Type> GeneratedExtension<ContainingType, Type> newRepeatedGeneratedExtension(ContainingType containingTypeDefaultInstance, MessageLite messageDefaultInstance, Internal.EnumLiteMap<?> enumTypeMap, int number, WireFormat.FieldType type, boolean isPacked, Class singularType) {
        return new GeneratedExtension<>(containingTypeDefaultInstance, Collections.emptyList(), messageDefaultInstance, new ExtensionDescriptor(enumTypeMap, number, type, true, isPacked), singularType);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes3.dex */
    public static final class ExtensionDescriptor implements FieldSet.FieldDescriptorLite<ExtensionDescriptor> {
        final Internal.EnumLiteMap<?> enumTypeMap;
        final boolean isPacked;
        final boolean isRepeated;
        final int number;
        final WireFormat.FieldType type;

        ExtensionDescriptor(Internal.EnumLiteMap<?> enumTypeMap, int number, WireFormat.FieldType type, boolean isRepeated, boolean isPacked) {
            this.enumTypeMap = enumTypeMap;
            this.number = number;
            this.type = type;
            this.isRepeated = isRepeated;
            this.isPacked = isPacked;
        }

        @Override // com.android.car.protobuf.FieldSet.FieldDescriptorLite
        public int getNumber() {
            return this.number;
        }

        @Override // com.android.car.protobuf.FieldSet.FieldDescriptorLite
        public WireFormat.FieldType getLiteType() {
            return this.type;
        }

        @Override // com.android.car.protobuf.FieldSet.FieldDescriptorLite
        public WireFormat.JavaType getLiteJavaType() {
            return this.type.getJavaType();
        }

        @Override // com.android.car.protobuf.FieldSet.FieldDescriptorLite
        public boolean isRepeated() {
            return this.isRepeated;
        }

        @Override // com.android.car.protobuf.FieldSet.FieldDescriptorLite
        public boolean isPacked() {
            return this.isPacked;
        }

        @Override // com.android.car.protobuf.FieldSet.FieldDescriptorLite
        public Internal.EnumLiteMap<?> getEnumType() {
            return this.enumTypeMap;
        }

        /* JADX WARN: Multi-variable type inference failed */
        @Override // com.android.car.protobuf.FieldSet.FieldDescriptorLite
        public MessageLite.Builder internalMergeFrom(MessageLite.Builder to, MessageLite from) {
            return ((Builder) to).mergeFrom((Builder) ((GeneratedMessageLite) from));
        }

        @Override // java.lang.Comparable
        public int compareTo(ExtensionDescriptor other) {
            return this.number - other.number;
        }
    }

    static Method getMethodOrDie(Class clazz, String name, Class... params) {
        try {
            return clazz.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Generated message class \"" + clazz.getName() + "\" missing method \"" + name + "\".", e);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static Object invokeOrDie(Method method, Object object, Object... params) {
        try {
            return method.invoke(object, params);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Couldn't use Java reflection to implement protocol message reflection.", e);
        } catch (InvocationTargetException e2) {
            Throwable cause = e2.getCause();
            if (cause instanceof RuntimeException) {
                throw ((RuntimeException) cause);
            }
            if (cause instanceof Error) {
                throw ((Error) cause);
            }
            throw new RuntimeException("Unexpected exception thrown by generated accessor method.", cause);
        }
    }

    /* loaded from: classes3.dex */
    public static class GeneratedExtension<ContainingType extends MessageLite, Type> extends ExtensionLite<ContainingType, Type> {
        final ContainingType containingTypeDefaultInstance;
        final Type defaultValue;
        final ExtensionDescriptor descriptor;
        final MessageLite messageDefaultInstance;

        GeneratedExtension(ContainingType containingTypeDefaultInstance, Type defaultValue, MessageLite messageDefaultInstance, ExtensionDescriptor descriptor, Class singularType) {
            if (containingTypeDefaultInstance == null) {
                throw new IllegalArgumentException("Null containingTypeDefaultInstance");
            }
            if (descriptor.getLiteType() == WireFormat.FieldType.MESSAGE && messageDefaultInstance == null) {
                throw new IllegalArgumentException("Null messageDefaultInstance");
            }
            this.containingTypeDefaultInstance = containingTypeDefaultInstance;
            this.defaultValue = defaultValue;
            this.messageDefaultInstance = messageDefaultInstance;
            this.descriptor = descriptor;
        }

        public ContainingType getContainingTypeDefaultInstance() {
            return this.containingTypeDefaultInstance;
        }

        @Override // com.android.car.protobuf.ExtensionLite
        public int getNumber() {
            return this.descriptor.getNumber();
        }

        @Override // com.android.car.protobuf.ExtensionLite
        public MessageLite getMessageDefaultInstance() {
            return this.messageDefaultInstance;
        }

        Object fromFieldSetType(Object value) {
            if (this.descriptor.isRepeated()) {
                if (this.descriptor.getLiteJavaType() == WireFormat.JavaType.ENUM) {
                    List result = new ArrayList();
                    for (Object element : (List) value) {
                        result.add(singularFromFieldSetType(element));
                    }
                    return result;
                }
                return value;
            }
            return singularFromFieldSetType(value);
        }

        Object singularFromFieldSetType(Object value) {
            if (this.descriptor.getLiteJavaType() == WireFormat.JavaType.ENUM) {
                return this.descriptor.enumTypeMap.findValueByNumber(((Integer) value).intValue());
            }
            return value;
        }

        Object toFieldSetType(Object value) {
            if (this.descriptor.isRepeated()) {
                if (this.descriptor.getLiteJavaType() == WireFormat.JavaType.ENUM) {
                    List result = new ArrayList();
                    for (Object element : (List) value) {
                        result.add(singularToFieldSetType(element));
                    }
                    return result;
                }
                return value;
            }
            return singularToFieldSetType(value);
        }

        Object singularToFieldSetType(Object value) {
            if (this.descriptor.getLiteJavaType() == WireFormat.JavaType.ENUM) {
                return Integer.valueOf(((Internal.EnumLite) value).getNumber());
            }
            return value;
        }

        @Override // com.android.car.protobuf.ExtensionLite
        public WireFormat.FieldType getLiteType() {
            return this.descriptor.getLiteType();
        }

        @Override // com.android.car.protobuf.ExtensionLite
        public boolean isRepeated() {
            return this.descriptor.isRepeated;
        }

        @Override // com.android.car.protobuf.ExtensionLite
        public Type getDefaultValue() {
            return this.defaultValue;
        }
    }

    /* loaded from: classes3.dex */
    protected static final class SerializedForm implements Serializable {
        private static final long serialVersionUID = 0;
        private final byte[] asBytes;
        private final String messageClassName;

        public static SerializedForm of(MessageLite message) {
            return new SerializedForm(message);
        }

        SerializedForm(MessageLite regularForm) {
            this.messageClassName = regularForm.getClass().getName();
            this.asBytes = regularForm.toByteArray();
        }

        protected Object readResolve() throws ObjectStreamException {
            try {
                Class<?> messageClass = Class.forName(this.messageClassName);
                Field defaultInstanceField = messageClass.getDeclaredField("DEFAULT_INSTANCE");
                defaultInstanceField.setAccessible(true);
                MessageLite defaultInstance = (MessageLite) defaultInstanceField.get(null);
                return defaultInstance.newBuilderForType().mergeFrom(this.asBytes).buildPartial();
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException("Unable to understand proto buffer", e);
            } catch (ClassNotFoundException e2) {
                throw new RuntimeException("Unable to find proto buffer class: " + this.messageClassName, e2);
            } catch (IllegalAccessException e3) {
                throw new RuntimeException("Unable to call parsePartialFrom", e3);
            } catch (NoSuchFieldException e4) {
                throw new RuntimeException("Unable to find DEFAULT_INSTANCE in " + this.messageClassName, e4);
            } catch (SecurityException e5) {
                throw new RuntimeException("Unable to call DEFAULT_INSTANCE in " + this.messageClassName, e5);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static <MessageType extends ExtendableMessage<MessageType, BuilderType>, BuilderType extends ExtendableBuilder<MessageType, BuilderType>, T> GeneratedExtension<MessageType, T> checkIsLite(ExtensionLite<MessageType, T> extension) {
        if (!extension.isLite()) {
            throw new IllegalArgumentException("Expected a lite extension.");
        }
        return (GeneratedExtension) extension;
    }

    protected static final <T extends GeneratedMessageLite<T, ?>> boolean isInitialized(T message, boolean shouldMemoize) {
        return message.dynamicMethod(MethodToInvoke.IS_INITIALIZED, Boolean.valueOf(shouldMemoize)) != null;
    }

    protected static final <T extends GeneratedMessageLite<T, ?>> void makeImmutable(T message) {
        message.dynamicMethod(MethodToInvoke.MAKE_IMMUTABLE);
    }

    protected static Internal.IntList emptyIntList() {
        return IntArrayList.emptyList();
    }

    /* JADX WARN: Type inference failed for: r1v2, types: [com.android.car.protobuf.Internal$IntList] */
    protected static Internal.IntList mutableCopy(Internal.IntList list) {
        int size = list.size();
        return list.mutableCopyWithCapacity(size == 0 ? 10 : size * 2);
    }

    protected static Internal.LongList emptyLongList() {
        return LongArrayList.emptyList();
    }

    /* JADX WARN: Type inference failed for: r1v2, types: [com.android.car.protobuf.Internal$LongList] */
    protected static Internal.LongList mutableCopy(Internal.LongList list) {
        int size = list.size();
        return list.mutableCopyWithCapacity(size == 0 ? 10 : size * 2);
    }

    protected static Internal.FloatList emptyFloatList() {
        return FloatArrayList.emptyList();
    }

    /* JADX WARN: Type inference failed for: r1v2, types: [com.android.car.protobuf.Internal$FloatList] */
    protected static Internal.FloatList mutableCopy(Internal.FloatList list) {
        int size = list.size();
        return list.mutableCopyWithCapacity(size == 0 ? 10 : size * 2);
    }

    protected static Internal.DoubleList emptyDoubleList() {
        return DoubleArrayList.emptyList();
    }

    /* JADX WARN: Type inference failed for: r1v2, types: [com.android.car.protobuf.Internal$DoubleList] */
    protected static Internal.DoubleList mutableCopy(Internal.DoubleList list) {
        int size = list.size();
        return list.mutableCopyWithCapacity(size == 0 ? 10 : size * 2);
    }

    protected static Internal.BooleanList emptyBooleanList() {
        return BooleanArrayList.emptyList();
    }

    /* JADX WARN: Type inference failed for: r1v2, types: [com.android.car.protobuf.Internal$BooleanList] */
    protected static Internal.BooleanList mutableCopy(Internal.BooleanList list) {
        int size = list.size();
        return list.mutableCopyWithCapacity(size == 0 ? 10 : size * 2);
    }

    protected static <E> Internal.ProtobufList<E> emptyProtobufList() {
        return ProtobufArrayList.emptyList();
    }

    protected static <E> Internal.ProtobufList<E> mutableCopy(Internal.ProtobufList<E> list) {
        int size = list.size();
        return list.mutableCopyWithCapacity(size == 0 ? 10 : size * 2);
    }

    /* loaded from: classes3.dex */
    protected static class DefaultInstanceBasedParser<T extends GeneratedMessageLite<T, ?>> extends AbstractParser<T> {
        private T defaultInstance;

        public DefaultInstanceBasedParser(T defaultInstance) {
            this.defaultInstance = defaultInstance;
        }

        @Override // com.android.car.protobuf.Parser
        public T parsePartialFrom(CodedInputStream input, ExtensionRegistryLite extensionRegistry) throws InvalidProtocolBufferException {
            return (T) GeneratedMessageLite.parsePartialFrom(this.defaultInstance, input, extensionRegistry);
        }
    }

    static <T extends GeneratedMessageLite<T, ?>> T parsePartialFrom(T instance, CodedInputStream input, ExtensionRegistryLite extensionRegistry) throws InvalidProtocolBufferException {
        T result = (T) instance.dynamicMethod(MethodToInvoke.NEW_MUTABLE_INSTANCE);
        try {
            result.dynamicMethod(MethodToInvoke.MERGE_FROM_STREAM, input, extensionRegistry);
            result.makeImmutable();
            return result;
        } catch (RuntimeException e) {
            if (e.getCause() instanceof InvalidProtocolBufferException) {
                throw ((InvalidProtocolBufferException) e.getCause());
            }
            throw e;
        }
    }

    protected static <T extends GeneratedMessageLite<T, ?>> T parsePartialFrom(T defaultInstance, CodedInputStream input) throws InvalidProtocolBufferException {
        return (T) parsePartialFrom(defaultInstance, input, ExtensionRegistryLite.getEmptyRegistry());
    }

    private static <T extends GeneratedMessageLite<T, ?>> T checkMessageInitialized(T message) throws InvalidProtocolBufferException {
        if (message != null && !message.isInitialized()) {
            throw message.newUninitializedMessageException().asInvalidProtocolBufferException().setUnfinishedMessage(message);
        }
        return message;
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public static <T extends GeneratedMessageLite<T, ?>> T parseFrom(T defaultInstance, ByteString data) throws InvalidProtocolBufferException {
        return (T) checkMessageInitialized(parseFrom(defaultInstance, data, ExtensionRegistryLite.getEmptyRegistry()));
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public static <T extends GeneratedMessageLite<T, ?>> T parseFrom(T defaultInstance, ByteString data, ExtensionRegistryLite extensionRegistry) throws InvalidProtocolBufferException {
        return (T) checkMessageInitialized(parsePartialFrom(defaultInstance, data, extensionRegistry));
    }

    private static <T extends GeneratedMessageLite<T, ?>> T parsePartialFrom(T defaultInstance, ByteString data, ExtensionRegistryLite extensionRegistry) throws InvalidProtocolBufferException {
        try {
            CodedInputStream input = data.newCodedInput();
            T message = (T) parsePartialFrom(defaultInstance, input, extensionRegistry);
            try {
                input.checkLastTagWas(0);
                return message;
            } catch (InvalidProtocolBufferException e) {
                throw e.setUnfinishedMessage(message);
            }
        } catch (InvalidProtocolBufferException e2) {
            throw e2;
        }
    }

    private static <T extends GeneratedMessageLite<T, ?>> T parsePartialFrom(T defaultInstance, byte[] data, ExtensionRegistryLite extensionRegistry) throws InvalidProtocolBufferException {
        try {
            CodedInputStream input = CodedInputStream.newInstance(data);
            T message = (T) parsePartialFrom(defaultInstance, input, extensionRegistry);
            try {
                input.checkLastTagWas(0);
                return message;
            } catch (InvalidProtocolBufferException e) {
                throw e.setUnfinishedMessage(message);
            }
        } catch (InvalidProtocolBufferException e2) {
            throw e2;
        }
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public static <T extends GeneratedMessageLite<T, ?>> T parseFrom(T defaultInstance, byte[] data) throws InvalidProtocolBufferException {
        return (T) checkMessageInitialized(parsePartialFrom(defaultInstance, data, ExtensionRegistryLite.getEmptyRegistry()));
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public static <T extends GeneratedMessageLite<T, ?>> T parseFrom(T defaultInstance, byte[] data, ExtensionRegistryLite extensionRegistry) throws InvalidProtocolBufferException {
        return (T) checkMessageInitialized(parsePartialFrom(defaultInstance, data, extensionRegistry));
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public static <T extends GeneratedMessageLite<T, ?>> T parseFrom(T defaultInstance, InputStream input) throws InvalidProtocolBufferException {
        return (T) checkMessageInitialized(parsePartialFrom(defaultInstance, CodedInputStream.newInstance(input), ExtensionRegistryLite.getEmptyRegistry()));
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public static <T extends GeneratedMessageLite<T, ?>> T parseFrom(T defaultInstance, InputStream input, ExtensionRegistryLite extensionRegistry) throws InvalidProtocolBufferException {
        return (T) checkMessageInitialized(parsePartialFrom(defaultInstance, CodedInputStream.newInstance(input), extensionRegistry));
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public static <T extends GeneratedMessageLite<T, ?>> T parseFrom(T defaultInstance, CodedInputStream input) throws InvalidProtocolBufferException {
        return (T) parseFrom(defaultInstance, input, ExtensionRegistryLite.getEmptyRegistry());
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public static <T extends GeneratedMessageLite<T, ?>> T parseFrom(T defaultInstance, CodedInputStream input, ExtensionRegistryLite extensionRegistry) throws InvalidProtocolBufferException {
        return (T) checkMessageInitialized(parsePartialFrom(defaultInstance, input, extensionRegistry));
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public static <T extends GeneratedMessageLite<T, ?>> T parseDelimitedFrom(T defaultInstance, InputStream input) throws InvalidProtocolBufferException {
        return (T) checkMessageInitialized(parsePartialDelimitedFrom(defaultInstance, input, ExtensionRegistryLite.getEmptyRegistry()));
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public static <T extends GeneratedMessageLite<T, ?>> T parseDelimitedFrom(T defaultInstance, InputStream input, ExtensionRegistryLite extensionRegistry) throws InvalidProtocolBufferException {
        return (T) checkMessageInitialized(parsePartialDelimitedFrom(defaultInstance, input, extensionRegistry));
    }

    private static <T extends GeneratedMessageLite<T, ?>> T parsePartialDelimitedFrom(T defaultInstance, InputStream input, ExtensionRegistryLite extensionRegistry) throws InvalidProtocolBufferException {
        try {
            int firstByte = input.read();
            if (firstByte == -1) {
                return null;
            }
            int size = CodedInputStream.readRawVarint32(firstByte, input);
            InputStream limitedInput = new AbstractMessageLite.Builder.LimitedInputStream(input, size);
            CodedInputStream codedInput = CodedInputStream.newInstance(limitedInput);
            T message = (T) parsePartialFrom(defaultInstance, codedInput, extensionRegistry);
            try {
                codedInput.checkLastTagWas(0);
                return message;
            } catch (InvalidProtocolBufferException e) {
                throw e.setUnfinishedMessage(message);
            }
        } catch (IOException e2) {
            throw new InvalidProtocolBufferException(e2.getMessage());
        }
    }

    /* loaded from: classes3.dex */
    static class EqualsVisitor implements Visitor {
        static final EqualsVisitor INSTANCE = new EqualsVisitor();
        static final NotEqualsException NOT_EQUALS = new NotEqualsException();

        /* loaded from: classes3.dex */
        static final class NotEqualsException extends RuntimeException {
            NotEqualsException() {
            }
        }

        private EqualsVisitor() {
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public boolean visitBoolean(boolean minePresent, boolean mine, boolean otherPresent, boolean other) {
            if (minePresent != otherPresent || mine != other) {
                throw NOT_EQUALS;
            }
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public int visitInt(boolean minePresent, int mine, boolean otherPresent, int other) {
            if (minePresent != otherPresent || mine != other) {
                throw NOT_EQUALS;
            }
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public double visitDouble(boolean minePresent, double mine, boolean otherPresent, double other) {
            if (minePresent != otherPresent || mine != other) {
                throw NOT_EQUALS;
            }
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public float visitFloat(boolean minePresent, float mine, boolean otherPresent, float other) {
            if (minePresent != otherPresent || mine != other) {
                throw NOT_EQUALS;
            }
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public long visitLong(boolean minePresent, long mine, boolean otherPresent, long other) {
            if (minePresent != otherPresent || mine != other) {
                throw NOT_EQUALS;
            }
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public String visitString(boolean minePresent, String mine, boolean otherPresent, String other) {
            if (minePresent != otherPresent || !mine.equals(other)) {
                throw NOT_EQUALS;
            }
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public ByteString visitByteString(boolean minePresent, ByteString mine, boolean otherPresent, ByteString other) {
            if (minePresent != otherPresent || !mine.equals(other)) {
                throw NOT_EQUALS;
            }
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Object visitOneofBoolean(boolean minePresent, Object mine, Object other) {
            if (minePresent && mine.equals(other)) {
                return mine;
            }
            throw NOT_EQUALS;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Object visitOneofInt(boolean minePresent, Object mine, Object other) {
            if (minePresent && mine.equals(other)) {
                return mine;
            }
            throw NOT_EQUALS;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Object visitOneofDouble(boolean minePresent, Object mine, Object other) {
            if (minePresent && mine.equals(other)) {
                return mine;
            }
            throw NOT_EQUALS;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Object visitOneofFloat(boolean minePresent, Object mine, Object other) {
            if (minePresent && mine.equals(other)) {
                return mine;
            }
            throw NOT_EQUALS;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Object visitOneofLong(boolean minePresent, Object mine, Object other) {
            if (minePresent && mine.equals(other)) {
                return mine;
            }
            throw NOT_EQUALS;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Object visitOneofString(boolean minePresent, Object mine, Object other) {
            if (minePresent && mine.equals(other)) {
                return mine;
            }
            throw NOT_EQUALS;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Object visitOneofByteString(boolean minePresent, Object mine, Object other) {
            if (minePresent && mine.equals(other)) {
                return mine;
            }
            throw NOT_EQUALS;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Object visitOneofLazyMessage(boolean minePresent, Object mine, Object other) {
            if (minePresent && mine.equals(other)) {
                return mine;
            }
            throw NOT_EQUALS;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Object visitOneofMessage(boolean minePresent, Object mine, Object other) {
            if (minePresent && ((GeneratedMessageLite) mine).equals(this, (MessageLite) other)) {
                return mine;
            }
            throw NOT_EQUALS;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public void visitOneofNotSet(boolean minePresent) {
            if (minePresent) {
                throw NOT_EQUALS;
            }
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public <T extends MessageLite> T visitMessage(T mine, T other) {
            if (mine == null && other == null) {
                return null;
            }
            if (mine == null || other == null) {
                throw NOT_EQUALS;
            }
            ((GeneratedMessageLite) mine).equals(this, other);
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public LazyFieldLite visitLazyMessage(boolean minePresent, LazyFieldLite mine, boolean otherPresent, LazyFieldLite other) {
            if (!minePresent && !otherPresent) {
                return mine;
            }
            if (minePresent && otherPresent && mine.equals(other)) {
                return mine;
            }
            throw NOT_EQUALS;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public <T> Internal.ProtobufList<T> visitList(Internal.ProtobufList<T> mine, Internal.ProtobufList<T> other) {
            if (!mine.equals(other)) {
                throw NOT_EQUALS;
            }
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Internal.BooleanList visitBooleanList(Internal.BooleanList mine, Internal.BooleanList other) {
            if (!mine.equals(other)) {
                throw NOT_EQUALS;
            }
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Internal.IntList visitIntList(Internal.IntList mine, Internal.IntList other) {
            if (!mine.equals(other)) {
                throw NOT_EQUALS;
            }
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Internal.DoubleList visitDoubleList(Internal.DoubleList mine, Internal.DoubleList other) {
            if (!mine.equals(other)) {
                throw NOT_EQUALS;
            }
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Internal.FloatList visitFloatList(Internal.FloatList mine, Internal.FloatList other) {
            if (!mine.equals(other)) {
                throw NOT_EQUALS;
            }
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Internal.LongList visitLongList(Internal.LongList mine, Internal.LongList other) {
            if (!mine.equals(other)) {
                throw NOT_EQUALS;
            }
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public FieldSet<ExtensionDescriptor> visitExtensions(FieldSet<ExtensionDescriptor> mine, FieldSet<ExtensionDescriptor> other) {
            if (!mine.equals(other)) {
                throw NOT_EQUALS;
            }
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public UnknownFieldSetLite visitUnknownFields(UnknownFieldSetLite mine, UnknownFieldSetLite other) {
            if (!mine.equals(other)) {
                throw NOT_EQUALS;
            }
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public <K, V> MapFieldLite<K, V> visitMap(MapFieldLite<K, V> mine, MapFieldLite<K, V> other) {
            if (!mine.equals(other)) {
                throw NOT_EQUALS;
            }
            return mine;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public static class HashCodeVisitor implements Visitor {
        private int hashCode;

        private HashCodeVisitor() {
            this.hashCode = 0;
        }

        /* synthetic */ HashCodeVisitor(AnonymousClass1 x0) {
            this();
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public boolean visitBoolean(boolean minePresent, boolean mine, boolean otherPresent, boolean other) {
            this.hashCode = (this.hashCode * 53) + Internal.hashBoolean(mine);
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public int visitInt(boolean minePresent, int mine, boolean otherPresent, int other) {
            this.hashCode = (this.hashCode * 53) + mine;
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public double visitDouble(boolean minePresent, double mine, boolean otherPresent, double other) {
            this.hashCode = (this.hashCode * 53) + Internal.hashLong(Double.doubleToLongBits(mine));
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public float visitFloat(boolean minePresent, float mine, boolean otherPresent, float other) {
            this.hashCode = (this.hashCode * 53) + Float.floatToIntBits(mine);
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public long visitLong(boolean minePresent, long mine, boolean otherPresent, long other) {
            this.hashCode = (this.hashCode * 53) + Internal.hashLong(mine);
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public String visitString(boolean minePresent, String mine, boolean otherPresent, String other) {
            this.hashCode = (this.hashCode * 53) + mine.hashCode();
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public ByteString visitByteString(boolean minePresent, ByteString mine, boolean otherPresent, ByteString other) {
            this.hashCode = (this.hashCode * 53) + mine.hashCode();
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Object visitOneofBoolean(boolean minePresent, Object mine, Object other) {
            this.hashCode = (this.hashCode * 53) + Internal.hashBoolean(((Boolean) mine).booleanValue());
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Object visitOneofInt(boolean minePresent, Object mine, Object other) {
            this.hashCode = (this.hashCode * 53) + ((Integer) mine).intValue();
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Object visitOneofDouble(boolean minePresent, Object mine, Object other) {
            this.hashCode = (this.hashCode * 53) + Internal.hashLong(Double.doubleToLongBits(((Double) mine).doubleValue()));
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Object visitOneofFloat(boolean minePresent, Object mine, Object other) {
            this.hashCode = (this.hashCode * 53) + Float.floatToIntBits(((Float) mine).floatValue());
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Object visitOneofLong(boolean minePresent, Object mine, Object other) {
            this.hashCode = (this.hashCode * 53) + Internal.hashLong(((Long) mine).longValue());
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Object visitOneofString(boolean minePresent, Object mine, Object other) {
            this.hashCode = (this.hashCode * 53) + mine.hashCode();
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Object visitOneofByteString(boolean minePresent, Object mine, Object other) {
            this.hashCode = (this.hashCode * 53) + mine.hashCode();
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Object visitOneofLazyMessage(boolean minePresent, Object mine, Object other) {
            this.hashCode = (this.hashCode * 53) + mine.hashCode();
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Object visitOneofMessage(boolean minePresent, Object mine, Object other) {
            return visitMessage((MessageLite) mine, (MessageLite) other);
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public void visitOneofNotSet(boolean minePresent) {
            if (minePresent) {
                throw new IllegalStateException();
            }
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public <T extends MessageLite> T visitMessage(T mine, T other) {
            int protoHash;
            if (mine != null) {
                if (mine instanceof GeneratedMessageLite) {
                    protoHash = ((GeneratedMessageLite) mine).hashCode(this);
                } else {
                    protoHash = mine.hashCode();
                }
            } else {
                protoHash = 37;
            }
            this.hashCode = (this.hashCode * 53) + protoHash;
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public LazyFieldLite visitLazyMessage(boolean minePresent, LazyFieldLite mine, boolean otherPresent, LazyFieldLite other) {
            this.hashCode = (this.hashCode * 53) + mine.hashCode();
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public <T> Internal.ProtobufList<T> visitList(Internal.ProtobufList<T> mine, Internal.ProtobufList<T> other) {
            this.hashCode = (this.hashCode * 53) + mine.hashCode();
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Internal.BooleanList visitBooleanList(Internal.BooleanList mine, Internal.BooleanList other) {
            this.hashCode = (this.hashCode * 53) + mine.hashCode();
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Internal.IntList visitIntList(Internal.IntList mine, Internal.IntList other) {
            this.hashCode = (this.hashCode * 53) + mine.hashCode();
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Internal.DoubleList visitDoubleList(Internal.DoubleList mine, Internal.DoubleList other) {
            this.hashCode = (this.hashCode * 53) + mine.hashCode();
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Internal.FloatList visitFloatList(Internal.FloatList mine, Internal.FloatList other) {
            this.hashCode = (this.hashCode * 53) + mine.hashCode();
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Internal.LongList visitLongList(Internal.LongList mine, Internal.LongList other) {
            this.hashCode = (this.hashCode * 53) + mine.hashCode();
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public FieldSet<ExtensionDescriptor> visitExtensions(FieldSet<ExtensionDescriptor> mine, FieldSet<ExtensionDescriptor> other) {
            this.hashCode = (this.hashCode * 53) + mine.hashCode();
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public UnknownFieldSetLite visitUnknownFields(UnknownFieldSetLite mine, UnknownFieldSetLite other) {
            this.hashCode = (this.hashCode * 53) + mine.hashCode();
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public <K, V> MapFieldLite<K, V> visitMap(MapFieldLite<K, V> mine, MapFieldLite<K, V> other) {
            this.hashCode = (this.hashCode * 53) + mine.hashCode();
            return mine;
        }
    }

    /* JADX INFO: Access modifiers changed from: protected */
    /* loaded from: classes3.dex */
    public static class MergeFromVisitor implements Visitor {
        public static final MergeFromVisitor INSTANCE = new MergeFromVisitor();

        private MergeFromVisitor() {
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public boolean visitBoolean(boolean minePresent, boolean mine, boolean otherPresent, boolean other) {
            return otherPresent ? other : mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public int visitInt(boolean minePresent, int mine, boolean otherPresent, int other) {
            return otherPresent ? other : mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public double visitDouble(boolean minePresent, double mine, boolean otherPresent, double other) {
            return otherPresent ? other : mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public float visitFloat(boolean minePresent, float mine, boolean otherPresent, float other) {
            return otherPresent ? other : mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public long visitLong(boolean minePresent, long mine, boolean otherPresent, long other) {
            return otherPresent ? other : mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public String visitString(boolean minePresent, String mine, boolean otherPresent, String other) {
            return otherPresent ? other : mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public ByteString visitByteString(boolean minePresent, ByteString mine, boolean otherPresent, ByteString other) {
            return otherPresent ? other : mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Object visitOneofBoolean(boolean minePresent, Object mine, Object other) {
            return other;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Object visitOneofInt(boolean minePresent, Object mine, Object other) {
            return other;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Object visitOneofDouble(boolean minePresent, Object mine, Object other) {
            return other;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Object visitOneofFloat(boolean minePresent, Object mine, Object other) {
            return other;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Object visitOneofLong(boolean minePresent, Object mine, Object other) {
            return other;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Object visitOneofString(boolean minePresent, Object mine, Object other) {
            return other;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Object visitOneofByteString(boolean minePresent, Object mine, Object other) {
            return other;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Object visitOneofLazyMessage(boolean minePresent, Object mine, Object other) {
            if (minePresent) {
                LazyFieldLite lazy = (LazyFieldLite) mine;
                lazy.merge((LazyFieldLite) other);
                return lazy;
            }
            return other;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Object visitOneofMessage(boolean minePresent, Object mine, Object other) {
            if (minePresent) {
                return visitMessage((MessageLite) mine, (MessageLite) other);
            }
            return other;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public void visitOneofNotSet(boolean minePresent) {
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public <T extends MessageLite> T visitMessage(T mine, T other) {
            if (mine == null || other == null) {
                return mine != null ? mine : other;
            }
            return (T) mine.toBuilder().mergeFrom(other).build();
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public LazyFieldLite visitLazyMessage(boolean minePresent, LazyFieldLite mine, boolean otherPresent, LazyFieldLite other) {
            mine.merge(other);
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public <T> Internal.ProtobufList<T> visitList(Internal.ProtobufList<T> mine, Internal.ProtobufList<T> other) {
            int size = mine.size();
            int otherSize = other.size();
            if (size > 0 && otherSize > 0) {
                if (!mine.isModifiable()) {
                    mine = mine.mutableCopyWithCapacity(size + otherSize);
                }
                mine.addAll(other);
            }
            return size > 0 ? mine : other;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Internal.BooleanList visitBooleanList(Internal.BooleanList mine, Internal.BooleanList other) {
            int size = mine.size();
            int otherSize = other.size();
            Internal.BooleanList mine2 = mine;
            mine2 = mine;
            if (size > 0 && otherSize > 0) {
                boolean isModifiable = mine.isModifiable();
                Internal.BooleanList mine3 = mine;
                if (!isModifiable) {
                    mine3 = mine.mutableCopyWithCapacity(size + otherSize);
                }
                mine3.addAll(other);
                mine2 = mine3;
            }
            return size > 0 ? mine2 : other;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Internal.IntList visitIntList(Internal.IntList mine, Internal.IntList other) {
            int size = mine.size();
            int otherSize = other.size();
            Internal.IntList mine2 = mine;
            mine2 = mine;
            if (size > 0 && otherSize > 0) {
                boolean isModifiable = mine.isModifiable();
                Internal.IntList mine3 = mine;
                if (!isModifiable) {
                    mine3 = mine.mutableCopyWithCapacity(size + otherSize);
                }
                mine3.addAll(other);
                mine2 = mine3;
            }
            return size > 0 ? mine2 : other;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Internal.DoubleList visitDoubleList(Internal.DoubleList mine, Internal.DoubleList other) {
            int size = mine.size();
            int otherSize = other.size();
            Internal.DoubleList mine2 = mine;
            mine2 = mine;
            if (size > 0 && otherSize > 0) {
                boolean isModifiable = mine.isModifiable();
                Internal.DoubleList mine3 = mine;
                if (!isModifiable) {
                    mine3 = mine.mutableCopyWithCapacity(size + otherSize);
                }
                mine3.addAll(other);
                mine2 = mine3;
            }
            return size > 0 ? mine2 : other;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Internal.FloatList visitFloatList(Internal.FloatList mine, Internal.FloatList other) {
            int size = mine.size();
            int otherSize = other.size();
            Internal.FloatList mine2 = mine;
            mine2 = mine;
            if (size > 0 && otherSize > 0) {
                boolean isModifiable = mine.isModifiable();
                Internal.FloatList mine3 = mine;
                if (!isModifiable) {
                    mine3 = mine.mutableCopyWithCapacity(size + otherSize);
                }
                mine3.addAll(other);
                mine2 = mine3;
            }
            return size > 0 ? mine2 : other;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public Internal.LongList visitLongList(Internal.LongList mine, Internal.LongList other) {
            int size = mine.size();
            int otherSize = other.size();
            Internal.LongList mine2 = mine;
            mine2 = mine;
            if (size > 0 && otherSize > 0) {
                boolean isModifiable = mine.isModifiable();
                Internal.LongList mine3 = mine;
                if (!isModifiable) {
                    mine3 = mine.mutableCopyWithCapacity(size + otherSize);
                }
                mine3.addAll(other);
                mine2 = mine3;
            }
            return size > 0 ? mine2 : other;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public FieldSet<ExtensionDescriptor> visitExtensions(FieldSet<ExtensionDescriptor> mine, FieldSet<ExtensionDescriptor> other) {
            if (mine.isImmutable()) {
                mine = mine.m9clone();
            }
            mine.mergeFrom(other);
            return mine;
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public UnknownFieldSetLite visitUnknownFields(UnknownFieldSetLite mine, UnknownFieldSetLite other) {
            return other == UnknownFieldSetLite.getDefaultInstance() ? mine : UnknownFieldSetLite.mutableCopyOf(mine, other);
        }

        @Override // com.android.car.protobuf.GeneratedMessageLite.Visitor
        public <K, V> MapFieldLite<K, V> visitMap(MapFieldLite<K, V> mine, MapFieldLite<K, V> other) {
            mine.mergeFrom(other);
            return mine;
        }
    }
}
