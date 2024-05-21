package com.android.car.protobuf;
/* loaded from: classes3.dex */
final class TextFormatEscaper {

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes3.dex */
    public interface ByteSequence {
        byte byteAt(int i);

        int size();
    }

    private TextFormatEscaper() {
    }

    static String escapeBytes(ByteSequence input) {
        StringBuilder builder = new StringBuilder(input.size());
        for (int i = 0; i < input.size(); i++) {
            byte b = input.byteAt(i);
            if (b == 34) {
                builder.append("\\\"");
            } else if (b == 39) {
                builder.append("\\'");
            } else if (b != 92) {
                switch (b) {
                    case 7:
                        builder.append("\\a");
                        continue;
                    case 8:
                        builder.append("\\b");
                        continue;
                    case 9:
                        builder.append("\\t");
                        continue;
                    case 10:
                        builder.append("\\n");
                        continue;
                    case 11:
                        builder.append("\\v");
                        continue;
                    case 12:
                        builder.append("\\f");
                        continue;
                    case 13:
                        builder.append("\\r");
                        continue;
                    default:
                        if (b >= 32 && b <= 126) {
                            builder.append((char) b);
                            continue;
                        } else {
                            builder.append('\\');
                            builder.append((char) (((b >>> 6) & 3) + 48));
                            builder.append((char) (((b >>> 3) & 7) + 48));
                            builder.append((char) ((b & 7) + 48));
                            break;
                        }
                        break;
                }
            } else {
                builder.append("\\\\");
            }
        }
        return builder.toString();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static String escapeBytes(final ByteString input) {
        return escapeBytes(new ByteSequence() { // from class: com.android.car.protobuf.TextFormatEscaper.1
            @Override // com.android.car.protobuf.TextFormatEscaper.ByteSequence
            public int size() {
                return ByteString.this.size();
            }

            @Override // com.android.car.protobuf.TextFormatEscaper.ByteSequence
            public byte byteAt(int offset) {
                return ByteString.this.byteAt(offset);
            }
        });
    }

    static String escapeBytes(final byte[] input) {
        return escapeBytes(new ByteSequence() { // from class: com.android.car.protobuf.TextFormatEscaper.2
            @Override // com.android.car.protobuf.TextFormatEscaper.ByteSequence
            public int size() {
                return input.length;
            }

            @Override // com.android.car.protobuf.TextFormatEscaper.ByteSequence
            public byte byteAt(int offset) {
                return input[offset];
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static String escapeText(String input) {
        return escapeBytes(ByteString.copyFromUtf8(input));
    }

    static String escapeDoubleQuotesAndBackslashes(String input) {
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
