package com.android.car.protobuf;

import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.logging.Level;
import java.util.logging.Logger;
import sun.misc.Unsafe;
/* JADX INFO: Access modifiers changed from: package-private */
/* loaded from: classes3.dex */
public final class Utf8 {
    private static final long ASCII_MASK_LONG = -9187201950435737472L;
    public static final int COMPLETE = 0;
    public static final int MALFORMED = -1;
    static final int MAX_BYTES_PER_CHAR = 3;
    private static final int UNSAFE_COUNT_ASCII_THRESHOLD = 16;
    private static final Logger logger = Logger.getLogger(Utf8.class.getName());
    private static final Processor processor;

    static {
        processor = UnsafeProcessor.isAvailable() ? new UnsafeProcessor() : new SafeProcessor();
    }

    public static boolean isValidUtf8(byte[] bytes) {
        return processor.isValidUtf8(bytes, 0, bytes.length);
    }

    public static boolean isValidUtf8(byte[] bytes, int index, int limit) {
        return processor.isValidUtf8(bytes, index, limit);
    }

    public static int partialIsValidUtf8(int state, byte[] bytes, int index, int limit) {
        return processor.partialIsValidUtf8(state, bytes, index, limit);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int incompleteStateFor(int byte1) {
        if (byte1 > -12) {
            return -1;
        }
        return byte1;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int incompleteStateFor(int byte1, int byte2) {
        if (byte1 > -12 || byte2 > -65) {
            return -1;
        }
        return (byte2 << 8) ^ byte1;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int incompleteStateFor(int byte1, int byte2, int byte3) {
        if (byte1 > -12 || byte2 > -65 || byte3 > -65) {
            return -1;
        }
        return ((byte2 << 8) ^ byte1) ^ (byte3 << 16);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int incompleteStateFor(byte[] bytes, int index, int limit) {
        int byte1 = bytes[index - 1];
        int i = limit - index;
        if (i != 0) {
            if (i != 1) {
                if (i == 2) {
                    return incompleteStateFor(byte1, bytes[index], bytes[index + 1]);
                }
                throw new AssertionError();
            }
            return incompleteStateFor(byte1, bytes[index]);
        }
        return incompleteStateFor(byte1);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int incompleteStateFor(ByteBuffer buffer, int byte1, int index, int remaining) {
        if (remaining != 0) {
            if (remaining != 1) {
                if (remaining == 2) {
                    return incompleteStateFor(byte1, buffer.get(index), buffer.get(index + 1));
                }
                throw new AssertionError();
            }
            return incompleteStateFor(byte1, buffer.get(index));
        }
        return incompleteStateFor(byte1);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes3.dex */
    public static class UnpairedSurrogateException extends IllegalArgumentException {
        private UnpairedSurrogateException(int index, int length) {
            super("Unpaired surrogate at index " + index + " of " + length);
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static int encodedLength(CharSequence sequence) {
        int utf16Length = sequence.length();
        int utf8Length = utf16Length;
        int i = 0;
        while (i < utf16Length && sequence.charAt(i) < 128) {
            i++;
        }
        while (true) {
            if (i < utf16Length) {
                char c = sequence.charAt(i);
                if (c < 2048) {
                    utf8Length += (127 - c) >>> 31;
                    i++;
                } else {
                    utf8Length += encodedLengthGeneral(sequence, i);
                    break;
                }
            } else {
                break;
            }
        }
        if (utf8Length < utf16Length) {
            throw new IllegalArgumentException("UTF-8 length does not fit in int: " + (utf8Length + 4294967296L));
        }
        return utf8Length;
    }

    private static int encodedLengthGeneral(CharSequence sequence, int start) {
        int utf16Length = sequence.length();
        int utf8Length = 0;
        int i = start;
        while (i < utf16Length) {
            char c = sequence.charAt(i);
            if (c < 2048) {
                utf8Length += (127 - c) >>> 31;
            } else {
                utf8Length += 2;
                if (55296 <= c && c <= 57343) {
                    int cp = Character.codePointAt(sequence, i);
                    if (cp < 65536) {
                        throw new UnpairedSurrogateException(i, utf16Length);
                    }
                    i++;
                }
            }
            i++;
        }
        return utf8Length;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static int encode(CharSequence in, byte[] out, int offset, int length) {
        return processor.encodeUtf8(in, out, offset, length);
    }

    static boolean isValidUtf8(ByteBuffer buffer) {
        return processor.isValidUtf8(buffer, buffer.position(), buffer.remaining());
    }

    static int partialIsValidUtf8(int state, ByteBuffer buffer, int index, int limit) {
        return processor.partialIsValidUtf8(state, buffer, index, limit);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static void encodeUtf8(CharSequence in, ByteBuffer out) {
        processor.encodeUtf8(in, out);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int estimateConsecutiveAscii(ByteBuffer buffer, int index, int limit) {
        int i = index;
        int lim = limit - 7;
        while (i < lim && (buffer.getLong(i) & ASCII_MASK_LONG) == 0) {
            i += 8;
        }
        return i - index;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes3.dex */
    public static abstract class Processor {
        abstract int encodeUtf8(CharSequence charSequence, byte[] bArr, int i, int i2);

        abstract void encodeUtf8Direct(CharSequence charSequence, ByteBuffer byteBuffer);

        abstract int partialIsValidUtf8(int i, byte[] bArr, int i2, int i3);

        abstract int partialIsValidUtf8Direct(int i, ByteBuffer byteBuffer, int i2, int i3);

        Processor() {
        }

        final boolean isValidUtf8(byte[] bytes, int index, int limit) {
            return partialIsValidUtf8(0, bytes, index, limit) == 0;
        }

        final boolean isValidUtf8(ByteBuffer buffer, int index, int limit) {
            return partialIsValidUtf8(0, buffer, index, limit) == 0;
        }

        final int partialIsValidUtf8(int state, ByteBuffer buffer, int index, int limit) {
            if (buffer.hasArray()) {
                int offset = buffer.arrayOffset();
                return partialIsValidUtf8(state, buffer.array(), offset + index, offset + limit);
            } else if (buffer.isDirect()) {
                return partialIsValidUtf8Direct(state, buffer, index, limit);
            } else {
                return partialIsValidUtf8Default(state, buffer, index, limit);
            }
        }

        final int partialIsValidUtf8Default(int state, ByteBuffer buffer, int index, int limit) {
            int index2;
            if (state == 0) {
                index2 = index;
            } else if (index >= limit) {
                return state;
            } else {
                byte byte1 = (byte) state;
                if (byte1 < -32) {
                    if (byte1 >= -62) {
                        index2 = index + 1;
                        if (buffer.get(index) > -65) {
                        }
                    }
                    return -1;
                } else if (byte1 < -16) {
                    byte byte2 = (byte) (~(state >> 8));
                    if (byte2 == 0) {
                        int index3 = index + 1;
                        byte2 = buffer.get(index);
                        if (index3 >= limit) {
                            return Utf8.incompleteStateFor(byte1, byte2);
                        }
                        index = index3;
                    }
                    if (byte2 <= -65 && ((byte1 != -32 || byte2 >= -96) && (byte1 != -19 || byte2 < -96))) {
                        index2 = index + 1;
                        if (buffer.get(index) > -65) {
                        }
                    }
                    return -1;
                } else {
                    byte byte22 = (byte) (~(state >> 8));
                    byte byte3 = 0;
                    if (byte22 == 0) {
                        int index4 = index + 1;
                        byte22 = buffer.get(index);
                        if (index4 >= limit) {
                            return Utf8.incompleteStateFor(byte1, byte22);
                        }
                        index = index4;
                    } else {
                        byte3 = (byte) (state >> 16);
                    }
                    if (byte3 == 0) {
                        int index5 = index + 1;
                        byte3 = buffer.get(index);
                        if (index5 >= limit) {
                            return Utf8.incompleteStateFor(byte1, byte22, byte3);
                        }
                        index = index5;
                    }
                    if (byte22 <= -65 && (((byte1 << 28) + (byte22 + 112)) >> 30) == 0 && byte3 <= -65) {
                        int index6 = index + 1;
                        if (buffer.get(index) <= -65) {
                            index2 = index6;
                        }
                    }
                    return -1;
                }
            }
            return partialIsValidUtf8(buffer, index2, limit);
        }

        private static int partialIsValidUtf8(ByteBuffer buffer, int index, int limit) {
            int index2 = index + Utf8.estimateConsecutiveAscii(buffer, index, limit);
            while (index2 < limit) {
                int index3 = index2 + 1;
                int byte1 = buffer.get(index2);
                if (byte1 >= 0) {
                    index2 = index3;
                } else if (byte1 < -32) {
                    if (index3 >= limit) {
                        return byte1;
                    }
                    if (byte1 < -62 || buffer.get(index3) > -65) {
                        return -1;
                    }
                    index2 = index3 + 1;
                } else if (byte1 < -16) {
                    if (index3 >= limit - 1) {
                        return Utf8.incompleteStateFor(buffer, byte1, index3, limit - index3);
                    }
                    int index4 = index3 + 1;
                    byte byte2 = buffer.get(index3);
                    if (byte2 > -65 || ((byte1 == -32 && byte2 < -96) || ((byte1 == -19 && byte2 >= -96) || buffer.get(index4) > -65))) {
                        return -1;
                    }
                    index2 = index4 + 1;
                } else if (index3 >= limit - 2) {
                    return Utf8.incompleteStateFor(buffer, byte1, index3, limit - index3);
                } else {
                    int index5 = index3 + 1;
                    int byte22 = buffer.get(index3);
                    if (byte22 <= -65 && (((byte1 << 28) + (byte22 + 112)) >> 30) == 0) {
                        int index6 = index5 + 1;
                        if (buffer.get(index5) <= -65) {
                            index2 = index6 + 1;
                            if (buffer.get(index6) > -65) {
                            }
                        }
                    }
                    return -1;
                }
            }
            return 0;
        }

        final void encodeUtf8(CharSequence in, ByteBuffer out) {
            if (out.hasArray()) {
                int offset = out.arrayOffset();
                int endIndex = Utf8.encode(in, out.array(), out.position() + offset, out.remaining());
                out.position(endIndex - offset);
            } else if (out.isDirect()) {
                encodeUtf8Direct(in, out);
            } else {
                encodeUtf8Default(in, out);
            }
        }

        final void encodeUtf8Default(CharSequence in, ByteBuffer out) {
            int inLength = in.length();
            int outIx = out.position();
            int inIx = 0;
            while (inIx < inLength) {
                try {
                    char c = in.charAt(inIx);
                    if (c >= 128) {
                        break;
                    }
                    out.put(outIx + inIx, (byte) c);
                    inIx++;
                } catch (IndexOutOfBoundsException e) {
                    int badWriteIndex = out.position() + Math.max(inIx, (outIx - out.position()) + 1);
                    throw new ArrayIndexOutOfBoundsException("Failed writing " + in.charAt(inIx) + " at index " + badWriteIndex);
                }
            }
            if (inIx == inLength) {
                out.position(outIx + inIx);
                return;
            }
            int outIx2 = outIx + inIx;
            while (inIx < inLength) {
                char c2 = in.charAt(inIx);
                if (c2 < 128) {
                    out.put(outIx2, (byte) c2);
                } else if (c2 < 2048) {
                    int outIx3 = outIx2 + 1;
                    try {
                        out.put(outIx2, (byte) ((c2 >>> 6) | 192));
                        out.put(outIx3, (byte) ((c2 & '?') | 128));
                        outIx2 = outIx3;
                    } catch (IndexOutOfBoundsException e2) {
                        outIx = outIx3;
                        int badWriteIndex2 = out.position() + Math.max(inIx, (outIx - out.position()) + 1);
                        throw new ArrayIndexOutOfBoundsException("Failed writing " + in.charAt(inIx) + " at index " + badWriteIndex2);
                    }
                } else if (c2 < 55296 || 57343 < c2) {
                    int outIx4 = outIx2 + 1;
                    out.put(outIx2, (byte) ((c2 >>> '\f') | 224));
                    outIx2 = outIx4 + 1;
                    out.put(outIx4, (byte) (((c2 >>> 6) & 63) | 128));
                    out.put(outIx2, (byte) ((c2 & '?') | 128));
                } else {
                    if (inIx + 1 != inLength) {
                        inIx++;
                        char low = in.charAt(inIx);
                        if (Character.isSurrogatePair(c2, low)) {
                            int codePoint = Character.toCodePoint(c2, low);
                            int outIx5 = outIx2 + 1;
                            try {
                                out.put(outIx2, (byte) ((codePoint >>> 18) | 240));
                                int outIx6 = outIx5 + 1;
                                out.put(outIx5, (byte) (((codePoint >>> 12) & 63) | 128));
                                int outIx7 = outIx6 + 1;
                                out.put(outIx6, (byte) (((codePoint >>> 6) & 63) | 128));
                                out.put(outIx7, (byte) ((codePoint & 63) | 128));
                                outIx2 = outIx7;
                            } catch (IndexOutOfBoundsException e3) {
                                outIx = outIx5;
                                int badWriteIndex22 = out.position() + Math.max(inIx, (outIx - out.position()) + 1);
                                throw new ArrayIndexOutOfBoundsException("Failed writing " + in.charAt(inIx) + " at index " + badWriteIndex22);
                            }
                        }
                    }
                    throw new UnpairedSurrogateException(inIx, inLength);
                }
                inIx++;
                outIx2++;
            }
            out.position(outIx2);
        }
    }

    /* loaded from: classes3.dex */
    static final class SafeProcessor extends Processor {
        SafeProcessor() {
        }

        @Override // com.android.car.protobuf.Utf8.Processor
        int partialIsValidUtf8(int state, byte[] bytes, int index, int limit) {
            int index2;
            if (state == 0) {
                index2 = index;
            } else if (index >= limit) {
                return state;
            } else {
                int byte1 = (byte) state;
                if (byte1 < -32) {
                    if (byte1 >= -62) {
                        index2 = index + 1;
                        if (bytes[index] > -65) {
                        }
                    }
                    return -1;
                } else if (byte1 < -16) {
                    int byte2 = (byte) (~(state >> 8));
                    if (byte2 == 0) {
                        int index3 = index + 1;
                        byte2 = bytes[index];
                        if (index3 >= limit) {
                            return Utf8.incompleteStateFor(byte1, byte2);
                        }
                        index = index3;
                    }
                    if (byte2 <= -65 && ((byte1 != -32 || byte2 >= -96) && (byte1 != -19 || byte2 < -96))) {
                        index2 = index + 1;
                        if (bytes[index] > -65) {
                        }
                    }
                    return -1;
                } else {
                    int byte22 = (byte) (~(state >> 8));
                    int byte3 = 0;
                    if (byte22 == 0) {
                        int index4 = index + 1;
                        byte22 = bytes[index];
                        if (index4 >= limit) {
                            return Utf8.incompleteStateFor(byte1, byte22);
                        }
                        index = index4;
                    } else {
                        byte3 = (byte) (state >> 16);
                    }
                    if (byte3 == 0) {
                        int index5 = index + 1;
                        byte3 = bytes[index];
                        if (index5 >= limit) {
                            return Utf8.incompleteStateFor(byte1, byte22, byte3);
                        }
                        index = index5;
                    }
                    if (byte22 <= -65 && (((byte1 << 28) + (byte22 + 112)) >> 30) == 0 && byte3 <= -65) {
                        int index6 = index + 1;
                        if (bytes[index] <= -65) {
                            index2 = index6;
                        }
                    }
                    return -1;
                }
            }
            return partialIsValidUtf8(bytes, index2, limit);
        }

        @Override // com.android.car.protobuf.Utf8.Processor
        int partialIsValidUtf8Direct(int state, ByteBuffer buffer, int index, int limit) {
            return partialIsValidUtf8Default(state, buffer, index, limit);
        }

        /* JADX WARN: Code restructure failed: missing block: B:12:0x0023, code lost:
            return r13 + r0;
         */
        @Override // com.android.car.protobuf.Utf8.Processor
        /*
            Code decompiled incorrectly, please refer to instructions dump.
            To view partially-correct add '--show-bad-code' argument
        */
        int encodeUtf8(java.lang.CharSequence r11, byte[] r12, int r13, int r14) {
            /*
                Method dump skipped, instructions count: 266
                To view this dump add '--comments-level debug' option
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.car.protobuf.Utf8.SafeProcessor.encodeUtf8(java.lang.CharSequence, byte[], int, int):int");
        }

        @Override // com.android.car.protobuf.Utf8.Processor
        void encodeUtf8Direct(CharSequence in, ByteBuffer out) {
            encodeUtf8Default(in, out);
        }

        private static int partialIsValidUtf8(byte[] bytes, int index, int limit) {
            while (index < limit && bytes[index] >= 0) {
                index++;
            }
            if (index >= limit) {
                return 0;
            }
            return partialIsValidUtf8NonAscii(bytes, index, limit);
        }

        private static int partialIsValidUtf8NonAscii(byte[] bytes, int index, int limit) {
            while (index < limit) {
                int index2 = index + 1;
                int byte1 = bytes[index];
                if (byte1 >= 0) {
                    index = index2;
                } else if (byte1 < -32) {
                    if (index2 >= limit) {
                        return byte1;
                    }
                    if (byte1 >= -62) {
                        index = index2 + 1;
                        if (bytes[index2] > -65) {
                        }
                    }
                    return -1;
                } else if (byte1 < -16) {
                    if (index2 >= limit - 1) {
                        return Utf8.incompleteStateFor(bytes, index2, limit);
                    }
                    int index3 = index2 + 1;
                    int byte2 = bytes[index2];
                    if (byte2 <= -65 && ((byte1 != -32 || byte2 >= -96) && (byte1 != -19 || byte2 < -96))) {
                        index = index3 + 1;
                        if (bytes[index3] > -65) {
                        }
                    }
                    return -1;
                } else {
                    int index4 = limit - 2;
                    if (index2 >= index4) {
                        return Utf8.incompleteStateFor(bytes, index2, limit);
                    }
                    int index5 = index2 + 1;
                    int byte22 = bytes[index2];
                    if (byte22 <= -65 && (((byte1 << 28) + (byte22 + 112)) >> 30) == 0) {
                        int index6 = index5 + 1;
                        if (bytes[index5] <= -65) {
                            index = index6 + 1;
                            if (bytes[index6] > -65) {
                            }
                        }
                    }
                    return -1;
                }
            }
            return 0;
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* loaded from: classes3.dex */
    public static final class UnsafeProcessor extends Processor {
        private static final boolean AVAILABLE;
        private static final Unsafe UNSAFE = getUnsafe();
        private static final long BUFFER_ADDRESS_OFFSET = fieldOffset(field(Buffer.class, "address"));
        private static final int ARRAY_BASE_OFFSET = byteArrayBaseOffset();

        UnsafeProcessor() {
        }

        static {
            AVAILABLE = BUFFER_ADDRESS_OFFSET != -1 && ARRAY_BASE_OFFSET % 8 == 0;
        }

        static boolean isAvailable() {
            return AVAILABLE;
        }

        @Override // com.android.car.protobuf.Utf8.Processor
        int partialIsValidUtf8(int state, byte[] bytes, int index, int limit) {
            long offset;
            long offset2;
            long offset3;
            if ((index | limit | (bytes.length - limit)) < 0) {
                throw new ArrayIndexOutOfBoundsException(String.format("Array length=%d, index=%d, limit=%d", Integer.valueOf(bytes.length), Integer.valueOf(index), Integer.valueOf(limit)));
            }
            int i = ARRAY_BASE_OFFSET;
            long offset4 = i + index;
            long offsetLimit = i + limit;
            if (state == 0) {
                offset = offset4;
            } else if (offset4 >= offsetLimit) {
                return state;
            } else {
                int byte1 = (byte) state;
                if (byte1 < -32) {
                    if (byte1 >= -62) {
                        offset = 1 + offset4;
                        if (UNSAFE.getByte(bytes, offset4) > -65) {
                        }
                    }
                    return -1;
                } else if (byte1 < -16) {
                    int byte2 = (byte) (~(state >> 8));
                    if (byte2 != 0) {
                        offset3 = offset4;
                    } else {
                        offset3 = offset4 + 1;
                        byte2 = UNSAFE.getByte(bytes, offset4);
                        if (offset3 >= offsetLimit) {
                            return Utf8.incompleteStateFor(byte1, byte2);
                        }
                    }
                    if (byte2 <= -65 && ((byte1 != -32 || byte2 >= -96) && (byte1 != -19 || byte2 < -96))) {
                        offset = 1 + offset3;
                        if (UNSAFE.getByte(bytes, offset3) > -65) {
                        }
                    }
                    return -1;
                } else {
                    int byte22 = (byte) (~(state >> 8));
                    int byte3 = 0;
                    if (byte22 == 0) {
                        offset2 = offset4 + 1;
                        byte22 = UNSAFE.getByte(bytes, offset4);
                        if (offset2 >= offsetLimit) {
                            return Utf8.incompleteStateFor(byte1, byte22);
                        }
                    } else {
                        byte3 = (byte) (state >> 16);
                        offset2 = offset4;
                    }
                    if (byte3 == 0) {
                        long offset5 = offset2 + 1;
                        byte3 = UNSAFE.getByte(bytes, offset2);
                        if (offset5 >= offsetLimit) {
                            return Utf8.incompleteStateFor(byte1, byte22, byte3);
                        }
                        offset2 = offset5;
                    }
                    if (byte22 <= -65 && (((byte1 << 28) + (byte22 + 112)) >> 30) == 0 && byte3 <= -65) {
                        offset = 1 + offset2;
                        if (UNSAFE.getByte(bytes, offset2) > -65) {
                        }
                    }
                    return -1;
                }
            }
            return partialIsValidUtf8(bytes, offset, (int) (offsetLimit - offset));
        }

        @Override // com.android.car.protobuf.Utf8.Processor
        int partialIsValidUtf8Direct(int state, ByteBuffer buffer, int index, int limit) {
            long address;
            long address2;
            long address3;
            if ((index | limit | (buffer.limit() - limit)) < 0) {
                throw new ArrayIndexOutOfBoundsException(String.format("buffer limit=%d, index=%d, limit=%d", Integer.valueOf(buffer.limit()), Integer.valueOf(index), Integer.valueOf(limit)));
            }
            long address4 = addressOffset(buffer) + index;
            long addressLimit = (limit - index) + address4;
            if (state == 0) {
                address = address4;
            } else if (address4 >= addressLimit) {
                return state;
            } else {
                int byte1 = (byte) state;
                if (byte1 < -32) {
                    if (byte1 >= -62) {
                        address = 1 + address4;
                        if (UNSAFE.getByte(address4) > -65) {
                        }
                    }
                    return -1;
                } else if (byte1 < -16) {
                    int byte2 = (byte) (~(state >> 8));
                    if (byte2 != 0) {
                        address3 = address4;
                    } else {
                        address3 = address4 + 1;
                        byte2 = UNSAFE.getByte(address4);
                        if (address3 >= addressLimit) {
                            return Utf8.incompleteStateFor(byte1, byte2);
                        }
                    }
                    if (byte2 <= -65 && ((byte1 != -32 || byte2 >= -96) && (byte1 != -19 || byte2 < -96))) {
                        address = 1 + address3;
                        if (UNSAFE.getByte(address3) > -65) {
                        }
                    }
                    return -1;
                } else {
                    int byte22 = (byte) (~(state >> 8));
                    int byte3 = 0;
                    if (byte22 == 0) {
                        address2 = address4 + 1;
                        byte22 = UNSAFE.getByte(address4);
                        if (address2 >= addressLimit) {
                            return Utf8.incompleteStateFor(byte1, byte22);
                        }
                    } else {
                        byte3 = (byte) (state >> 16);
                        address2 = address4;
                    }
                    if (byte3 == 0) {
                        long address5 = address2 + 1;
                        byte3 = UNSAFE.getByte(address2);
                        if (address5 >= addressLimit) {
                            return Utf8.incompleteStateFor(byte1, byte22, byte3);
                        }
                        address2 = address5;
                    }
                    if (byte22 <= -65 && (((byte1 << 28) + (byte22 + 112)) >> 30) == 0 && byte3 <= -65) {
                        address = 1 + address2;
                        if (UNSAFE.getByte(address2) > -65) {
                        }
                    }
                    return -1;
                }
            }
            return partialIsValidUtf8(address, (int) (addressLimit - address));
        }

        @Override // com.android.car.protobuf.Utf8.Processor
        int encodeUtf8(CharSequence in, byte[] out, int offset, int length) {
            char c;
            long j;
            String str;
            String str2;
            long outLimit;
            char c2;
            long j2;
            char c3;
            long outIx = ARRAY_BASE_OFFSET + offset;
            long outLimit2 = length + outIx;
            int inLimit = in.length();
            String str3 = " at index ";
            String str4 = "Failed writing ";
            if (inLimit > length || out.length - length < offset) {
                throw new ArrayIndexOutOfBoundsException("Failed writing " + in.charAt(inLimit - 1) + " at index " + (offset + length));
            }
            int inIx = 0;
            while (true) {
                c = 128;
                j = 1;
                if (inIx >= inLimit || (c3 = in.charAt(inIx)) >= 128) {
                    break;
                }
                UNSAFE.putByte(out, outIx, (byte) c3);
                inIx++;
                outIx = 1 + outIx;
            }
            if (inIx == inLimit) {
                return (int) (outIx - ARRAY_BASE_OFFSET);
            }
            while (inIx < inLimit) {
                char c4 = in.charAt(inIx);
                if (c4 < c && outIx < outLimit2) {
                    UNSAFE.putByte(out, outIx, (byte) c4);
                    str = str3;
                    outIx += j;
                    j2 = 1;
                    outLimit = outLimit2;
                    str2 = str4;
                    c2 = 128;
                } else if (c4 < 2048 && outIx <= outLimit2 - 2) {
                    long outIx2 = outIx + 1;
                    UNSAFE.putByte(out, outIx, (byte) ((c4 >>> 6) | 960));
                    UNSAFE.putByte(out, outIx2, (byte) ((c4 & '?') | 128));
                    str = str3;
                    outIx = outIx2 + 1;
                    j2 = 1;
                    outLimit = outLimit2;
                    str2 = str4;
                    c2 = 128;
                } else {
                    if (c4 >= 55296 && 57343 >= c4) {
                        str = str3;
                        str2 = str4;
                    } else if (outIx > outLimit2 - 3) {
                        str = str3;
                        str2 = str4;
                    } else {
                        str = str3;
                        str2 = str4;
                        long outIx3 = outIx + 1;
                        UNSAFE.putByte(out, outIx, (byte) ((c4 >>> '\f') | 480));
                        long outIx4 = outIx3 + 1;
                        UNSAFE.putByte(out, outIx3, (byte) (((c4 >>> 6) & 63) | 128));
                        UNSAFE.putByte(out, outIx4, (byte) ((c4 & '?') | 128));
                        outLimit = outLimit2;
                        outIx = outIx4 + 1;
                        c2 = 128;
                        j2 = 1;
                    }
                    if (outIx > outLimit2 - 4) {
                        if (55296 <= c4 && c4 <= 57343 && (inIx + 1 == inLimit || !Character.isSurrogatePair(c4, in.charAt(inIx + 1)))) {
                            throw new UnpairedSurrogateException(inIx, inLimit);
                        }
                        throw new ArrayIndexOutOfBoundsException(str2 + c4 + str + outIx);
                    }
                    if (inIx + 1 != inLimit) {
                        inIx++;
                        char low = in.charAt(inIx);
                        if (Character.isSurrogatePair(c4, low)) {
                            int codePoint = Character.toCodePoint(c4, low);
                            outLimit = outLimit2;
                            long outLimit3 = outIx + 1;
                            UNSAFE.putByte(out, outIx, (byte) ((codePoint >>> 18) | 240));
                            long outIx5 = outLimit3 + 1;
                            UNSAFE.putByte(out, outLimit3, (byte) (((codePoint >>> 12) & 63) | 128));
                            long outIx6 = outIx5 + 1;
                            c2 = 128;
                            UNSAFE.putByte(out, outIx5, (byte) (((codePoint >>> 6) & 63) | 128));
                            j2 = 1;
                            UNSAFE.putByte(out, outIx6, (byte) ((codePoint & 63) | 128));
                            outIx = outIx6 + 1;
                        }
                    }
                    throw new UnpairedSurrogateException(inIx - 1, inLimit);
                }
                inIx++;
                c = c2;
                str3 = str;
                str4 = str2;
                outLimit2 = outLimit;
                j = j2;
            }
            return (int) (outIx - ARRAY_BASE_OFFSET);
        }

        @Override // com.android.car.protobuf.Utf8.Processor
        void encodeUtf8Direct(CharSequence in, ByteBuffer out) {
            char c;
            long j;
            long outIx;
            long outLimit;
            char c2;
            long outIx2;
            char c3;
            long address = addressOffset(out);
            long outIx3 = out.position() + address;
            long outLimit2 = out.limit() + address;
            int inLimit = in.length();
            if (inLimit > outLimit2 - outIx3) {
                throw new ArrayIndexOutOfBoundsException("Failed writing " + in.charAt(inLimit - 1) + " at index " + out.limit());
            }
            int inIx = 0;
            while (true) {
                c = 128;
                j = 1;
                if (inIx >= inLimit || (c3 = in.charAt(inIx)) >= 128) {
                    break;
                }
                UNSAFE.putByte(outIx3, (byte) c3);
                inIx++;
                outIx3 = 1 + outIx3;
            }
            if (inIx == inLimit) {
                out.position((int) (outIx3 - address));
                return;
            }
            while (inIx < inLimit) {
                char c4 = in.charAt(inIx);
                if (c4 < c && outIx3 < outLimit2) {
                    UNSAFE.putByte(outIx3, (byte) c4);
                    outLimit = outLimit2;
                    outIx3 += j;
                    c2 = 128;
                    outIx2 = 1;
                    outIx = address;
                } else if (c4 >= 2048 || outIx3 > outLimit2 - 2) {
                    outIx = address;
                    if ((c4 < 55296 || 57343 < c4) && outIx3 <= outLimit2 - 3) {
                        long outIx4 = outIx3 + 1;
                        UNSAFE.putByte(outIx3, (byte) ((c4 >>> '\f') | 480));
                        long outIx5 = outIx4 + 1;
                        UNSAFE.putByte(outIx4, (byte) (((c4 >>> 6) & 63) | 128));
                        UNSAFE.putByte(outIx5, (byte) ((c4 & '?') | 128));
                        outLimit = outLimit2;
                        outIx3 = outIx5 + 1;
                        c2 = 128;
                        outIx2 = 1;
                    } else if (outIx3 > outLimit2 - 4) {
                        if (55296 <= c4 && c4 <= 57343 && (inIx + 1 == inLimit || !Character.isSurrogatePair(c4, in.charAt(inIx + 1)))) {
                            throw new UnpairedSurrogateException(inIx, inLimit);
                        }
                        throw new ArrayIndexOutOfBoundsException("Failed writing " + c4 + " at index " + outIx3);
                    } else {
                        if (inIx + 1 != inLimit) {
                            inIx++;
                            char low = in.charAt(inIx);
                            if (Character.isSurrogatePair(c4, low)) {
                                int codePoint = Character.toCodePoint(c4, low);
                                outLimit = outLimit2;
                                long outLimit3 = outIx3 + 1;
                                UNSAFE.putByte(outIx3, (byte) ((codePoint >>> 18) | 240));
                                long outIx6 = outLimit3 + 1;
                                UNSAFE.putByte(outLimit3, (byte) (((codePoint >>> 12) & 63) | 128));
                                long outIx7 = outIx6 + 1;
                                c2 = 128;
                                UNSAFE.putByte(outIx6, (byte) (((codePoint >>> 6) & 63) | 128));
                                outIx2 = 1;
                                outIx3 = outIx7 + 1;
                                UNSAFE.putByte(outIx7, (byte) ((codePoint & 63) | 128));
                            }
                        }
                        throw new UnpairedSurrogateException(inIx - 1, inLimit);
                    }
                } else {
                    outIx = address;
                    long outIx8 = outIx3 + 1;
                    UNSAFE.putByte(outIx3, (byte) ((c4 >>> 6) | 960));
                    outIx3 = outIx8 + 1;
                    UNSAFE.putByte(outIx8, (byte) ((c4 & '?') | 128));
                    outLimit = outLimit2;
                    c2 = 128;
                    outIx2 = 1;
                }
                inIx++;
                c = c2;
                address = outIx;
                outLimit2 = outLimit;
                j = outIx2;
            }
            out.position((int) (outIx3 - address));
        }

        private static int unsafeEstimateConsecutiveAscii(byte[] bytes, long offset, int maxChars) {
            if (maxChars < 16) {
                return 0;
            }
            int unaligned = ((int) offset) & 7;
            int j = unaligned;
            while (j > 0) {
                long offset2 = 1 + offset;
                if (UNSAFE.getByte(bytes, offset) >= 0) {
                    j--;
                    offset = offset2;
                } else {
                    return unaligned - j;
                }
            }
            int remaining = maxChars - unaligned;
            while (remaining >= 8 && (UNSAFE.getLong(bytes, offset) & Utf8.ASCII_MASK_LONG) == 0) {
                offset += 8;
                remaining -= 8;
            }
            return maxChars - remaining;
        }

        private static int unsafeEstimateConsecutiveAscii(long address, int maxChars) {
            if (maxChars < 16) {
                return 0;
            }
            int unaligned = ((int) address) & 7;
            int j = unaligned;
            while (j > 0) {
                long address2 = 1 + address;
                if (UNSAFE.getByte(address) >= 0) {
                    j--;
                    address = address2;
                } else {
                    return unaligned - j;
                }
            }
            int remaining = maxChars - unaligned;
            while (remaining >= 8 && (UNSAFE.getLong(address) & Utf8.ASCII_MASK_LONG) == 0) {
                address += 8;
                remaining -= 8;
            }
            return maxChars - remaining;
        }

        /* JADX WARN: Code restructure failed: missing block: B:23:0x0040, code lost:
            return -1;
         */
        /* JADX WARN: Code restructure failed: missing block: B:43:0x0073, code lost:
            return -1;
         */
        /* JADX WARN: Code restructure failed: missing block: B:60:0x00ab, code lost:
            return -1;
         */
        /*
            Code decompiled incorrectly, please refer to instructions dump.
            To view partially-correct add '--show-bad-code' argument
        */
        private static int partialIsValidUtf8(byte[] r11, long r12, int r14) {
            /*
                int r0 = unsafeEstimateConsecutiveAscii(r11, r12, r14)
                int r14 = r14 - r0
                long r1 = (long) r0
                long r12 = r12 + r1
            L7:
                r1 = 0
            L8:
                r2 = 1
                if (r14 <= 0) goto L1c
                sun.misc.Unsafe r4 = com.android.car.protobuf.Utf8.UnsafeProcessor.UNSAFE
                long r5 = r12 + r2
                byte r12 = r4.getByte(r11, r12)
                r1 = r12
                if (r12 < 0) goto L1b
                int r14 = r14 + (-1)
                r12 = r5
                goto L8
            L1b:
                r12 = r5
            L1c:
                if (r14 != 0) goto L20
                r2 = 0
                return r2
            L20:
                int r14 = r14 + (-1)
                r4 = -32
                r5 = -65
                r6 = -1
                if (r1 >= r4) goto L41
                if (r14 != 0) goto L2c
                return r1
            L2c:
                int r14 = r14 + (-1)
                r4 = -62
                if (r1 < r4) goto L40
                sun.misc.Unsafe r4 = com.android.car.protobuf.Utf8.UnsafeProcessor.UNSAFE
                long r2 = r2 + r12
                byte r12 = r4.getByte(r11, r12)
                if (r12 <= r5) goto L3d
                r12 = r2
                goto L40
            L3d:
                r12 = r2
                goto La8
            L40:
                return r6
            L41:
                r7 = -16
                if (r1 >= r7) goto L74
                r7 = 2
                if (r14 >= r7) goto L4d
                int r2 = unsafeIncompleteStateFor(r11, r1, r12, r14)
                return r2
            L4d:
                int r14 = r14 + (-2)
                sun.misc.Unsafe r7 = com.android.car.protobuf.Utf8.UnsafeProcessor.UNSAFE
                long r8 = r12 + r2
                byte r12 = r7.getByte(r11, r12)
                r13 = r12
                if (r12 > r5) goto L72
                r12 = -96
                if (r1 != r4) goto L60
                if (r13 < r12) goto L72
            L60:
                r4 = -19
                if (r1 != r4) goto L66
                if (r13 >= r12) goto L72
            L66:
                sun.misc.Unsafe r12 = com.android.car.protobuf.Utf8.UnsafeProcessor.UNSAFE
                long r2 = r2 + r8
                byte r12 = r12.getByte(r11, r8)
                if (r12 <= r5) goto L70
                goto L73
            L70:
                r12 = r2
                goto La8
            L72:
                r2 = r8
            L73:
                return r6
            L74:
                r4 = 3
                if (r14 >= r4) goto L7c
                int r2 = unsafeIncompleteStateFor(r11, r1, r12, r14)
                return r2
            L7c:
                int r14 = r14 + (-3)
                sun.misc.Unsafe r4 = com.android.car.protobuf.Utf8.UnsafeProcessor.UNSAFE
                long r7 = r12 + r2
                byte r12 = r4.getByte(r11, r12)
                r13 = r12
                if (r12 > r5) goto Lab
                int r12 = r1 << 28
                int r4 = r13 + 112
                int r12 = r12 + r4
                int r12 = r12 >> 30
                if (r12 != 0) goto Lab
                sun.misc.Unsafe r12 = com.android.car.protobuf.Utf8.UnsafeProcessor.UNSAFE
                long r9 = r7 + r2
                byte r12 = r12.getByte(r11, r7)
                if (r12 > r5) goto Laa
                sun.misc.Unsafe r12 = com.android.car.protobuf.Utf8.UnsafeProcessor.UNSAFE
                long r7 = r9 + r2
                byte r12 = r12.getByte(r11, r9)
                if (r12 <= r5) goto La7
                goto Lab
            La7:
                r12 = r7
            La8:
                goto L7
            Laa:
                r7 = r9
            Lab:
                return r6
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.car.protobuf.Utf8.UnsafeProcessor.partialIsValidUtf8(byte[], long, int):int");
        }

        /* JADX WARN: Code restructure failed: missing block: B:23:0x0040, code lost:
            return -1;
         */
        /* JADX WARN: Code restructure failed: missing block: B:43:0x0072, code lost:
            return -1;
         */
        /* JADX WARN: Code restructure failed: missing block: B:60:0x00a9, code lost:
            return -1;
         */
        /*
            Code decompiled incorrectly, please refer to instructions dump.
            To view partially-correct add '--show-bad-code' argument
        */
        private static int partialIsValidUtf8(long r11, int r13) {
            /*
                int r0 = unsafeEstimateConsecutiveAscii(r11, r13)
                long r1 = (long) r0
                long r11 = r11 + r1
                int r13 = r13 - r0
            L7:
                r1 = 0
            L8:
                r2 = 1
                if (r13 <= 0) goto L1c
                sun.misc.Unsafe r4 = com.android.car.protobuf.Utf8.UnsafeProcessor.UNSAFE
                long r5 = r11 + r2
                byte r11 = r4.getByte(r11)
                r1 = r11
                if (r11 < 0) goto L1b
                int r13 = r13 + (-1)
                r11 = r5
                goto L8
            L1b:
                r11 = r5
            L1c:
                if (r13 != 0) goto L20
                r2 = 0
                return r2
            L20:
                int r13 = r13 + (-1)
                r4 = -32
                r5 = -65
                r6 = -1
                if (r1 >= r4) goto L41
                if (r13 != 0) goto L2c
                return r1
            L2c:
                int r13 = r13 + (-1)
                r4 = -62
                if (r1 < r4) goto L40
                sun.misc.Unsafe r4 = com.android.car.protobuf.Utf8.UnsafeProcessor.UNSAFE
                long r2 = r2 + r11
                byte r11 = r4.getByte(r11)
                if (r11 <= r5) goto L3d
                r11 = r2
                goto L40
            L3d:
                r11 = r2
                goto La6
            L40:
                return r6
            L41:
                r7 = -16
                if (r1 >= r7) goto L73
                r7 = 2
                if (r13 >= r7) goto L4d
                int r2 = unsafeIncompleteStateFor(r11, r1, r13)
                return r2
            L4d:
                int r13 = r13 + (-2)
                sun.misc.Unsafe r7 = com.android.car.protobuf.Utf8.UnsafeProcessor.UNSAFE
                long r8 = r11 + r2
                byte r11 = r7.getByte(r11)
                if (r11 > r5) goto L71
                r12 = -96
                if (r1 != r4) goto L5f
                if (r11 < r12) goto L71
            L5f:
                r4 = -19
                if (r1 != r4) goto L65
                if (r11 >= r12) goto L71
            L65:
                sun.misc.Unsafe r12 = com.android.car.protobuf.Utf8.UnsafeProcessor.UNSAFE
                long r2 = r2 + r8
                byte r12 = r12.getByte(r8)
                if (r12 <= r5) goto L6f
                goto L72
            L6f:
                r11 = r2
                goto La6
            L71:
                r2 = r8
            L72:
                return r6
            L73:
                r4 = 3
                if (r13 >= r4) goto L7b
                int r2 = unsafeIncompleteStateFor(r11, r1, r13)
                return r2
            L7b:
                int r13 = r13 + (-3)
                sun.misc.Unsafe r4 = com.android.car.protobuf.Utf8.UnsafeProcessor.UNSAFE
                long r7 = r11 + r2
                byte r11 = r4.getByte(r11)
                if (r11 > r5) goto La9
                int r12 = r1 << 28
                int r4 = r11 + 112
                int r12 = r12 + r4
                int r12 = r12 >> 30
                if (r12 != 0) goto La9
                sun.misc.Unsafe r12 = com.android.car.protobuf.Utf8.UnsafeProcessor.UNSAFE
                long r9 = r7 + r2
                byte r12 = r12.getByte(r7)
                if (r12 > r5) goto La8
                sun.misc.Unsafe r12 = com.android.car.protobuf.Utf8.UnsafeProcessor.UNSAFE
                long r7 = r9 + r2
                byte r12 = r12.getByte(r9)
                if (r12 <= r5) goto La5
                goto La9
            La5:
                r11 = r7
            La6:
                goto L7
            La8:
                r7 = r9
            La9:
                return r6
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.car.protobuf.Utf8.UnsafeProcessor.partialIsValidUtf8(long, int):int");
        }

        private static int unsafeIncompleteStateFor(byte[] bytes, int byte1, long offset, int remaining) {
            if (remaining != 0) {
                if (remaining != 1) {
                    if (remaining == 2) {
                        return Utf8.incompleteStateFor(byte1, UNSAFE.getByte(bytes, offset), UNSAFE.getByte(bytes, 1 + offset));
                    }
                    throw new AssertionError();
                }
                return Utf8.incompleteStateFor(byte1, UNSAFE.getByte(bytes, offset));
            }
            return Utf8.incompleteStateFor(byte1);
        }

        private static int unsafeIncompleteStateFor(long address, int byte1, int remaining) {
            if (remaining != 0) {
                if (remaining != 1) {
                    if (remaining == 2) {
                        return Utf8.incompleteStateFor(byte1, UNSAFE.getByte(address), UNSAFE.getByte(1 + address));
                    }
                    throw new AssertionError();
                }
                return Utf8.incompleteStateFor(byte1, UNSAFE.getByte(address));
            }
            return Utf8.incompleteStateFor(byte1);
        }

        private static Field field(Class<?> clazz, String fieldName) {
            Field field;
            try {
                field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
            } catch (Throwable th) {
                field = null;
            }
            Logger logger = Utf8.logger;
            Level level = Level.FINEST;
            Object[] objArr = new Object[3];
            objArr[0] = clazz.getName();
            objArr[1] = fieldName;
            objArr[2] = field != null ? "available" : "unavailable";
            logger.log(level, "{0}.{1}: {2}", objArr);
            return field;
        }

        private static long fieldOffset(Field field) {
            Unsafe unsafe;
            if (field == null || (unsafe = UNSAFE) == null) {
                return -1L;
            }
            return unsafe.objectFieldOffset(field);
        }

        private static <T> int byteArrayBaseOffset() {
            Unsafe unsafe = UNSAFE;
            if (unsafe == null) {
                return -1;
            }
            return unsafe.arrayBaseOffset(byte[].class);
        }

        private static long addressOffset(ByteBuffer buffer) {
            return UNSAFE.getLong(buffer, BUFFER_ADDRESS_OFFSET);
        }

        private static Unsafe getUnsafe() {
            Unsafe unsafe = null;
            try {
                unsafe = (Unsafe) AccessController.doPrivileged(new PrivilegedExceptionAction<Unsafe>() { // from class: com.android.car.protobuf.Utf8.UnsafeProcessor.1
                    @Override // java.security.PrivilegedExceptionAction
                    public Unsafe run() throws Exception {
                        Field[] declaredFields;
                        UnsafeProcessor.checkRequiredMethods(Unsafe.class);
                        for (Field f : Unsafe.class.getDeclaredFields()) {
                            f.setAccessible(true);
                            Object x = f.get(null);
                            if (Unsafe.class.isInstance(x)) {
                                return (Unsafe) Unsafe.class.cast(x);
                            }
                        }
                        return null;
                    }
                });
            } catch (Throwable th) {
            }
            Utf8.logger.log(Level.FINEST, "sun.misc.Unsafe: {}", unsafe != null ? "available" : "unavailable");
            return unsafe;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public static void checkRequiredMethods(Class<Unsafe> clazz) throws NoSuchMethodException, SecurityException {
            clazz.getMethod("arrayBaseOffset", Class.class);
            clazz.getMethod("getByte", Object.class, Long.TYPE);
            clazz.getMethod("putByte", Object.class, Long.TYPE, Byte.TYPE);
            clazz.getMethod("getLong", Object.class, Long.TYPE);
            clazz.getMethod("objectFieldOffset", Field.class);
            clazz.getMethod("getByte", Long.TYPE);
            clazz.getMethod("getLong", Object.class, Long.TYPE);
            clazz.getMethod("putByte", Long.TYPE, Byte.TYPE);
            clazz.getMethod("getLong", Long.TYPE);
        }
    }

    private Utf8() {
    }
}
