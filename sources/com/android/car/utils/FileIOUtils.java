package com.android.car.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
/* loaded from: classes3.dex */
public final class FileIOUtils {
    private static int sBufferSize = 524288;

    /* loaded from: classes3.dex */
    public interface OnProgressUpdateListener {
        void onProgressUpdate(double d);
    }

    private FileIOUtils() {
        throw new UnsupportedOperationException("u can't instantiate me...");
    }

    public static boolean writeFileFromIS(String filePath, InputStream is) {
        return writeFileFromIS(getFileByPath(filePath), is, false, (OnProgressUpdateListener) null);
    }

    public static boolean writeFileFromIS(String filePath, InputStream is, boolean append) {
        return writeFileFromIS(getFileByPath(filePath), is, append, (OnProgressUpdateListener) null);
    }

    public static boolean writeFileFromIS(File file, InputStream is) {
        return writeFileFromIS(file, is, false, (OnProgressUpdateListener) null);
    }

    public static boolean writeFileFromIS(File file, InputStream is, boolean append) {
        return writeFileFromIS(file, is, append, (OnProgressUpdateListener) null);
    }

    public static boolean writeFileFromIS(String filePath, InputStream is, OnProgressUpdateListener listener) {
        return writeFileFromIS(getFileByPath(filePath), is, false, listener);
    }

    public static boolean writeFileFromIS(String filePath, InputStream is, boolean append, OnProgressUpdateListener listener) {
        return writeFileFromIS(getFileByPath(filePath), is, append, listener);
    }

    public static boolean writeFileFromIS(File file, InputStream is, OnProgressUpdateListener listener) {
        return writeFileFromIS(file, is, false, listener);
    }

    public static boolean writeFileFromIS(File file, InputStream is, boolean append, OnProgressUpdateListener listener) {
        if (is == null || !createOrExistsFile(file)) {
            return false;
        }
        OutputStream os = null;
        try {
            try {
                OutputStream os2 = new BufferedOutputStream(new FileOutputStream(file, append), sBufferSize);
                if (listener == null) {
                    byte[] data = new byte[sBufferSize];
                    while (true) {
                        int len = is.read(data);
                        if (len == -1) {
                            break;
                        }
                        os2.write(data, 0, len);
                    }
                } else {
                    double totalSize = is.available();
                    int curSize = 0;
                    listener.onProgressUpdate(0.0d);
                    byte[] data2 = new byte[sBufferSize];
                    while (true) {
                        int len2 = is.read(data2);
                        if (len2 == -1) {
                            break;
                        }
                        os2.write(data2, 0, len2);
                        curSize += len2;
                        listener.onProgressUpdate(curSize / totalSize);
                    }
                }
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    os2.close();
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
                return true;
            } catch (IOException e3) {
                e3.printStackTrace();
                try {
                    is.close();
                } catch (IOException e4) {
                    e4.printStackTrace();
                }
                if (0 != 0) {
                    try {
                        os.close();
                    } catch (IOException e5) {
                        e5.printStackTrace();
                    }
                }
                return false;
            }
        } catch (Throwable th) {
            try {
                is.close();
            } catch (IOException e6) {
                e6.printStackTrace();
            }
            if (0 != 0) {
                try {
                    os.close();
                } catch (IOException e7) {
                    e7.printStackTrace();
                }
            }
            throw th;
        }
    }

    public static boolean writeFileFromBytesByStream(String filePath, byte[] bytes) {
        return writeFileFromBytesByStream(getFileByPath(filePath), bytes, false, (OnProgressUpdateListener) null);
    }

    public static boolean writeFileFromBytesByStream(String filePath, byte[] bytes, boolean append) {
        return writeFileFromBytesByStream(getFileByPath(filePath), bytes, append, (OnProgressUpdateListener) null);
    }

    public static boolean writeFileFromBytesByStream(File file, byte[] bytes) {
        return writeFileFromBytesByStream(file, bytes, false, (OnProgressUpdateListener) null);
    }

    public static boolean writeFileFromBytesByStream(File file, byte[] bytes, boolean append) {
        return writeFileFromBytesByStream(file, bytes, append, (OnProgressUpdateListener) null);
    }

    public static boolean writeFileFromBytesByStream(String filePath, byte[] bytes, OnProgressUpdateListener listener) {
        return writeFileFromBytesByStream(getFileByPath(filePath), bytes, false, listener);
    }

    public static boolean writeFileFromBytesByStream(String filePath, byte[] bytes, boolean append, OnProgressUpdateListener listener) {
        return writeFileFromBytesByStream(getFileByPath(filePath), bytes, append, listener);
    }

    public static boolean writeFileFromBytesByStream(File file, byte[] bytes, OnProgressUpdateListener listener) {
        return writeFileFromBytesByStream(file, bytes, false, listener);
    }

    public static boolean writeFileFromBytesByStream(File file, byte[] bytes, boolean append, OnProgressUpdateListener listener) {
        if (bytes == null) {
            return false;
        }
        return writeFileFromIS(file, new ByteArrayInputStream(bytes), append, listener);
    }

    public static boolean writeFileFromBytesByChannel(String filePath, byte[] bytes, boolean isForce) {
        return writeFileFromBytesByChannel(getFileByPath(filePath), bytes, false, isForce);
    }

    public static boolean writeFileFromBytesByChannel(String filePath, byte[] bytes, boolean append, boolean isForce) {
        return writeFileFromBytesByChannel(getFileByPath(filePath), bytes, append, isForce);
    }

    public static boolean writeFileFromBytesByChannel(File file, byte[] bytes, boolean isForce) {
        return writeFileFromBytesByChannel(file, bytes, false, isForce);
    }

    public static boolean writeFileFromBytesByChannel(File file, byte[] bytes, boolean append, boolean isForce) {
        if (bytes == null || !createOrExistsFile(file)) {
            return false;
        }
        FileChannel fc = null;
        try {
            try {
                fc = new FileOutputStream(file, append).getChannel();
                fc.position(fc.size());
                fc.write(ByteBuffer.wrap(bytes));
                if (isForce) {
                    fc.force(true);
                }
                try {
                    fc.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return true;
            } catch (IOException e2) {
                e2.printStackTrace();
                if (fc != null) {
                    try {
                        fc.close();
                    } catch (IOException e3) {
                        e3.printStackTrace();
                    }
                }
                return false;
            }
        } catch (Throwable th) {
            if (fc != null) {
                try {
                    fc.close();
                } catch (IOException e4) {
                    e4.printStackTrace();
                }
            }
            throw th;
        }
    }

    public static boolean writeFileFromBytesByMap(String filePath, byte[] bytes, boolean isForce) {
        return writeFileFromBytesByMap(filePath, bytes, false, isForce);
    }

    public static boolean writeFileFromBytesByMap(String filePath, byte[] bytes, boolean append, boolean isForce) {
        return writeFileFromBytesByMap(getFileByPath(filePath), bytes, append, isForce);
    }

    public static boolean writeFileFromBytesByMap(File file, byte[] bytes, boolean isForce) {
        return writeFileFromBytesByMap(file, bytes, false, isForce);
    }

    /* JADX WARN: Removed duplicated region for block: B:43:0x004f A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static boolean writeFileFromBytesByMap(java.io.File r9, byte[] r10, boolean r11, boolean r12) {
        /*
            r0 = 0
            if (r10 == 0) goto L5a
            boolean r1 = createOrExistsFile(r9)
            if (r1 != 0) goto La
            goto L5a
        La:
            r1 = 0
            java.io.FileOutputStream r2 = new java.io.FileOutputStream     // Catch: java.lang.Throwable -> L35 java.io.IOException -> L38
            r2.<init>(r9, r11)     // Catch: java.lang.Throwable -> L35 java.io.IOException -> L38
            java.nio.channels.FileChannel r3 = r2.getChannel()     // Catch: java.lang.Throwable -> L35 java.io.IOException -> L38
            java.nio.channels.FileChannel$MapMode r4 = java.nio.channels.FileChannel.MapMode.READ_WRITE     // Catch: java.io.IOException -> L33 java.lang.Throwable -> L4c
            long r5 = r3.size()     // Catch: java.io.IOException -> L33 java.lang.Throwable -> L4c
            int r1 = r10.length     // Catch: java.io.IOException -> L33 java.lang.Throwable -> L4c
            long r7 = (long) r1     // Catch: java.io.IOException -> L33 java.lang.Throwable -> L4c
            java.nio.MappedByteBuffer r1 = r3.map(r4, r5, r7)     // Catch: java.io.IOException -> L33 java.lang.Throwable -> L4c
            r1.put(r10)     // Catch: java.io.IOException -> L33 java.lang.Throwable -> L4c
            if (r12 == 0) goto L28
            r1.force()     // Catch: java.io.IOException -> L33 java.lang.Throwable -> L4c
        L28:
            r0 = 1
            r3.close()     // Catch: java.io.IOException -> L2e
            goto L32
        L2e:
            r2 = move-exception
            r2.printStackTrace()
        L32:
            return r0
        L33:
            r1 = move-exception
            goto L3b
        L35:
            r0 = move-exception
            r3 = r1
            goto L4d
        L38:
            r2 = move-exception
            r3 = r1
            r1 = r2
        L3b:
            r1.printStackTrace()     // Catch: java.lang.Throwable -> L4c
            if (r3 == 0) goto L4a
            r3.close()     // Catch: java.io.IOException -> L45
            goto L4a
        L45:
            r2 = move-exception
            r2.printStackTrace()
            goto L4b
        L4a:
        L4b:
            return r0
        L4c:
            r0 = move-exception
        L4d:
            if (r3 == 0) goto L58
            r3.close()     // Catch: java.io.IOException -> L53
            goto L58
        L53:
            r1 = move-exception
            r1.printStackTrace()
            goto L59
        L58:
        L59:
            throw r0
        L5a:
            return r0
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.car.utils.FileIOUtils.writeFileFromBytesByMap(java.io.File, byte[], boolean, boolean):boolean");
    }

    public static boolean writeFileFromString(String filePath, String content) {
        return writeFileFromString(getFileByPath(filePath), content, false);
    }

    public static boolean writeFileFromString(String filePath, String content, boolean append) {
        return writeFileFromString(getFileByPath(filePath), content, append);
    }

    public static boolean writeFileFromString(File file, String content) {
        return writeFileFromString(file, content, false);
    }

    public static boolean writeFileFromString(File file, String content, boolean append) {
        if (file == null || content == null || !createOrExistsFile(file)) {
            return false;
        }
        BufferedWriter bw = null;
        try {
            try {
                bw = new BufferedWriter(new FileWriter(file, append));
                bw.write(content);
                try {
                    bw.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return true;
            } catch (IOException e2) {
                e2.printStackTrace();
                if (bw != null) {
                    try {
                        bw.close();
                    } catch (IOException e3) {
                        e3.printStackTrace();
                    }
                }
                return false;
            }
        } catch (Throwable th) {
            if (bw != null) {
                try {
                    bw.close();
                } catch (IOException e4) {
                    e4.printStackTrace();
                }
            }
            throw th;
        }
    }

    public static List<String> readFile2List(String filePath) {
        return readFile2List(getFileByPath(filePath), (String) null);
    }

    public static List<String> readFile2List(String filePath, String charsetName) {
        return readFile2List(getFileByPath(filePath), charsetName);
    }

    public static List<String> readFile2List(File file) {
        return readFile2List(file, 0, Integer.MAX_VALUE, (String) null);
    }

    public static List<String> readFile2List(File file, String charsetName) {
        return readFile2List(file, 0, Integer.MAX_VALUE, charsetName);
    }

    public static List<String> readFile2List(String filePath, int st, int end) {
        return readFile2List(getFileByPath(filePath), st, end, (String) null);
    }

    public static List<String> readFile2List(String filePath, int st, int end, String charsetName) {
        return readFile2List(getFileByPath(filePath), st, end, charsetName);
    }

    public static List<String> readFile2List(File file, int st, int end) {
        return readFile2List(file, st, end, (String) null);
    }

    /* JADX WARN: Code restructure failed: missing block: B:21:0x004f, code lost:
        r0.close();
     */
    /* JADX WARN: Code restructure failed: missing block: B:23:0x0053, code lost:
        r1 = move-exception;
     */
    /* JADX WARN: Code restructure failed: missing block: B:24:0x0054, code lost:
        r1.printStackTrace();
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static java.util.List<java.lang.String> readFile2List(java.io.File r7, int r8, int r9, java.lang.String r10) {
        /*
            boolean r0 = isFileExists(r7)
            r1 = 0
            if (r0 != 0) goto L8
            return r1
        L8:
            if (r8 <= r9) goto Lb
            return r1
        Lb:
            r0 = 0
            r2 = 1
            java.util.ArrayList r3 = new java.util.ArrayList     // Catch: java.lang.Throwable -> L58 java.io.IOException -> L5a
            r3.<init>()     // Catch: java.lang.Throwable -> L58 java.io.IOException -> L5a
            boolean r4 = isSpace(r10)     // Catch: java.lang.Throwable -> L58 java.io.IOException -> L5a
            if (r4 == 0) goto L29
            java.io.BufferedReader r4 = new java.io.BufferedReader     // Catch: java.lang.Throwable -> L58 java.io.IOException -> L5a
            java.io.InputStreamReader r5 = new java.io.InputStreamReader     // Catch: java.lang.Throwable -> L58 java.io.IOException -> L5a
            java.io.FileInputStream r6 = new java.io.FileInputStream     // Catch: java.lang.Throwable -> L58 java.io.IOException -> L5a
            r6.<init>(r7)     // Catch: java.lang.Throwable -> L58 java.io.IOException -> L5a
            r5.<init>(r6)     // Catch: java.lang.Throwable -> L58 java.io.IOException -> L5a
            r4.<init>(r5)     // Catch: java.lang.Throwable -> L58 java.io.IOException -> L5a
            r0 = r4
            goto L39
        L29:
            java.io.BufferedReader r4 = new java.io.BufferedReader     // Catch: java.lang.Throwable -> L58 java.io.IOException -> L5a
            java.io.InputStreamReader r5 = new java.io.InputStreamReader     // Catch: java.lang.Throwable -> L58 java.io.IOException -> L5a
            java.io.FileInputStream r6 = new java.io.FileInputStream     // Catch: java.lang.Throwable -> L58 java.io.IOException -> L5a
            r6.<init>(r7)     // Catch: java.lang.Throwable -> L58 java.io.IOException -> L5a
            r5.<init>(r6, r10)     // Catch: java.lang.Throwable -> L58 java.io.IOException -> L5a
            r4.<init>(r5)     // Catch: java.lang.Throwable -> L58 java.io.IOException -> L5a
            r0 = r4
        L39:
            java.lang.String r4 = r0.readLine()     // Catch: java.lang.Throwable -> L58 java.io.IOException -> L5a
            r5 = r4
            if (r4 == 0) goto L4d
            if (r2 <= r9) goto L43
            goto L4d
        L43:
            if (r8 > r2) goto L4a
            if (r2 > r9) goto L4a
            r3.add(r5)     // Catch: java.lang.Throwable -> L58 java.io.IOException -> L5a
        L4a:
            int r2 = r2 + 1
            goto L39
        L4d:
            r0.close()     // Catch: java.io.IOException -> L53
            goto L57
        L53:
            r1 = move-exception
            r1.printStackTrace()
        L57:
            return r3
        L58:
            r1 = move-exception
            goto L6c
        L5a:
            r2 = move-exception
            r2.printStackTrace()     // Catch: java.lang.Throwable -> L58
            if (r0 == 0) goto L6a
            r0.close()     // Catch: java.io.IOException -> L65
            goto L6a
        L65:
            r3 = move-exception
            r3.printStackTrace()
            goto L6b
        L6a:
        L6b:
            return r1
        L6c:
            if (r0 == 0) goto L77
            r0.close()     // Catch: java.io.IOException -> L72
            goto L77
        L72:
            r2 = move-exception
            r2.printStackTrace()
            goto L78
        L77:
        L78:
            throw r1
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.car.utils.FileIOUtils.readFile2List(java.io.File, int, int, java.lang.String):java.util.List");
    }

    public static String readFile2String(String filePath) {
        return readFile2String(getFileByPath(filePath), (String) null);
    }

    public static String readFile2String(String filePath, String charsetName) {
        return readFile2String(getFileByPath(filePath), charsetName);
    }

    public static String readFile2String(File file) {
        return readFile2String(file, (String) null);
    }

    public static String readFile2String(File file, String charsetName) {
        byte[] bytes = readFile2BytesByStream(file);
        if (bytes == null) {
            return null;
        }
        if (isSpace(charsetName)) {
            return new String(bytes);
        }
        try {
            return new String(bytes, charsetName);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return "";
        }
    }

    public static byte[] readFile2BytesByStream(String filePath) {
        return readFile2BytesByStream(getFileByPath(filePath), (OnProgressUpdateListener) null);
    }

    public static byte[] readFile2BytesByStream(File file) {
        return readFile2BytesByStream(file, (OnProgressUpdateListener) null);
    }

    public static byte[] readFile2BytesByStream(String filePath, OnProgressUpdateListener listener) {
        return readFile2BytesByStream(getFileByPath(filePath));
    }

    public static byte[] readFile2BytesByStream(File file, OnProgressUpdateListener listener) {
        if (isFileExists(file)) {
            ByteArrayOutputStream os = null;
            try {
                InputStream is = new BufferedInputStream(new FileInputStream(file), sBufferSize);
                try {
                    os = new ByteArrayOutputStream();
                    byte[] b = new byte[sBufferSize];
                    if (listener != null) {
                        double totalSize = is.available();
                        int curSize = 0;
                        listener.onProgressUpdate(0.0d);
                        while (true) {
                            int len = is.read(b, 0, sBufferSize);
                            if (len == -1) {
                                break;
                            }
                            os.write(b, 0, len);
                            curSize += len;
                            listener.onProgressUpdate(curSize / totalSize);
                        }
                    } else {
                        while (true) {
                            int len2 = is.read(b, 0, sBufferSize);
                            if (len2 == -1) {
                                break;
                            }
                            os.write(b, 0, len2);
                        }
                    }
                    byte[] byteArray = os.toByteArray();
                    try {
                        is.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        os.close();
                    } catch (IOException e2) {
                        e2.printStackTrace();
                    }
                    return byteArray;
                } catch (IOException e3) {
                    e3.printStackTrace();
                    try {
                        is.close();
                    } catch (IOException e4) {
                        e4.printStackTrace();
                    }
                    if (os != null) {
                        try {
                            os.close();
                        } catch (IOException e5) {
                            e5.printStackTrace();
                        }
                    }
                    return null;
                }
            } catch (FileNotFoundException e6) {
                e6.printStackTrace();
                return null;
            }
        }
        return null;
    }

    public static byte[] readFile2BytesByChannel(String filePath) {
        return readFile2BytesByChannel(getFileByPath(filePath));
    }

    public static byte[] readFile2BytesByChannel(File file) {
        if (isFileExists(file)) {
            FileChannel fc = null;
            try {
                try {
                    fc = new RandomAccessFile(file, "r").getChannel();
                    ByteBuffer byteBuffer = ByteBuffer.allocate((int) fc.size());
                    do {
                    } while (fc.read(byteBuffer) > 0);
                    byte[] array = byteBuffer.array();
                    try {
                        fc.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return array;
                } catch (IOException e2) {
                    e2.printStackTrace();
                    if (fc != null) {
                        try {
                            fc.close();
                        } catch (IOException e3) {
                            e3.printStackTrace();
                        }
                    }
                    return null;
                }
            } catch (Throwable th) {
                if (fc != null) {
                    try {
                        fc.close();
                    } catch (IOException e4) {
                        e4.printStackTrace();
                    }
                }
                throw th;
            }
        }
        return null;
    }

    public static byte[] readFile2BytesByMap(String filePath) {
        return readFile2BytesByMap(getFileByPath(filePath));
    }

    /* JADX WARN: Removed duplicated region for block: B:40:0x0054 A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static byte[] readFile2BytesByMap(java.io.File r10) {
        /*
            boolean r0 = isFileExists(r10)
            r1 = 0
            if (r0 != 0) goto L8
            return r1
        L8:
            r0 = 0
            java.io.RandomAccessFile r2 = new java.io.RandomAccessFile     // Catch: java.lang.Throwable -> L39 java.io.IOException -> L3d
            java.lang.String r3 = "r"
            r2.<init>(r10, r3)     // Catch: java.lang.Throwable -> L39 java.io.IOException -> L3d
            java.nio.channels.FileChannel r4 = r2.getChannel()     // Catch: java.lang.Throwable -> L39 java.io.IOException -> L3d
            long r2 = r4.size()     // Catch: java.io.IOException -> L37 java.lang.Throwable -> L51
            int r0 = (int) r2     // Catch: java.io.IOException -> L37 java.lang.Throwable -> L51
            java.nio.channels.FileChannel$MapMode r5 = java.nio.channels.FileChannel.MapMode.READ_ONLY     // Catch: java.io.IOException -> L37 java.lang.Throwable -> L51
            r6 = 0
            long r8 = (long) r0     // Catch: java.io.IOException -> L37 java.lang.Throwable -> L51
            java.nio.MappedByteBuffer r2 = r4.map(r5, r6, r8)     // Catch: java.io.IOException -> L37 java.lang.Throwable -> L51
            java.nio.MappedByteBuffer r2 = r2.load()     // Catch: java.io.IOException -> L37 java.lang.Throwable -> L51
            byte[] r3 = new byte[r0]     // Catch: java.io.IOException -> L37 java.lang.Throwable -> L51
            r5 = 0
            r2.get(r3, r5, r0)     // Catch: java.io.IOException -> L37 java.lang.Throwable -> L51
            r4.close()     // Catch: java.io.IOException -> L32
            goto L36
        L32:
            r1 = move-exception
            r1.printStackTrace()
        L36:
            return r3
        L37:
            r0 = move-exception
            goto L40
        L39:
            r1 = move-exception
            r4 = r0
            r0 = r1
            goto L52
        L3d:
            r2 = move-exception
            r4 = r0
            r0 = r2
        L40:
            r0.printStackTrace()     // Catch: java.lang.Throwable -> L51
            if (r4 == 0) goto L4f
            r4.close()     // Catch: java.io.IOException -> L4a
            goto L4f
        L4a:
            r2 = move-exception
            r2.printStackTrace()
            goto L50
        L4f:
        L50:
            return r1
        L51:
            r0 = move-exception
        L52:
            if (r4 == 0) goto L5d
            r4.close()     // Catch: java.io.IOException -> L58
            goto L5d
        L58:
            r1 = move-exception
            r1.printStackTrace()
            goto L5e
        L5d:
        L5e:
            throw r0
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.car.utils.FileIOUtils.readFile2BytesByMap(java.io.File):byte[]");
    }

    public static void setBufferSize(int bufferSize) {
        sBufferSize = bufferSize;
    }

    private static File getFileByPath(String filePath) {
        if (isSpace(filePath)) {
            return null;
        }
        return new File(filePath);
    }

    private static boolean createOrExistsFile(String filePath) {
        return createOrExistsFile(getFileByPath(filePath));
    }

    private static boolean createOrExistsFile(File file) {
        if (file == null) {
            return false;
        }
        if (file.exists()) {
            return file.isFile();
        }
        if (!createOrExistsDir(file.getParentFile())) {
            return false;
        }
        try {
            return file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean createOrExistsDir(File file) {
        return file != null && (!file.exists() ? !file.mkdirs() : !file.isDirectory());
    }

    public static boolean isFileExists(String fileName) {
        File file = getFileByPath(fileName);
        return isFileExists(file);
    }

    public static boolean isFileExists(File file) {
        return file != null && file.exists();
    }

    private static boolean isSpace(String s) {
        if (s == null) {
            return true;
        }
        int len = s.length();
        for (int i = 0; i < len; i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static byte[] is2Bytes(InputStream is) {
        if (is == null) {
            return null;
        }
        ByteArrayOutputStream os = null;
        try {
            try {
                os = new ByteArrayOutputStream();
                byte[] b = new byte[sBufferSize];
                while (true) {
                    int len = is.read(b, 0, sBufferSize);
                    if (len == -1) {
                        break;
                    }
                    os.write(b, 0, len);
                }
                byte[] byteArray = os.toByteArray();
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    os.close();
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
                return byteArray;
            } catch (Throwable th) {
                try {
                    is.close();
                } catch (IOException e3) {
                    e3.printStackTrace();
                }
                if (os != null) {
                    try {
                        os.close();
                    } catch (IOException e4) {
                        e4.printStackTrace();
                    }
                }
                throw th;
            }
        } catch (IOException e5) {
            e5.printStackTrace();
            try {
                is.close();
            } catch (IOException e6) {
                e6.printStackTrace();
            }
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e7) {
                    e7.printStackTrace();
                }
            }
            return null;
        }
    }
}
