package com.android.car.utils;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.Build;
import android.support.v4.media.session.PlaybackStateCompat;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.net.ssl.HttpsURLConnection;
import kotlin.jvm.internal.ByteCompanionObject;
/* loaded from: classes3.dex */
public final class FileUtils {
    private static final String LINE_SEP = System.getProperty("line.separator");
    private static final char[] HEX_DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    /* loaded from: classes3.dex */
    public interface OnReplaceListener {
        boolean onReplace(File file, File file2);
    }

    private FileUtils() {
        throw new UnsupportedOperationException("u can't instantiate me...");
    }

    public static File getFileByPath(String filePath) {
        if (isSpace(filePath)) {
            return null;
        }
        return new File(filePath);
    }

    @Deprecated
    public static boolean isFileExists(String filePath) {
        if (Build.VERSION.SDK_INT < 29) {
            return isFileExists(getFileByPath(filePath));
        }
        try {
            Uri uri = Uri.parse(filePath);
            ContentResolver cr = Utils.getApp().getContentResolver();
            AssetFileDescriptor afd = cr.openAssetFileDescriptor(uri, "r");
            if (afd == null) {
                return false;
            }
            try {
                afd.close();
                return true;
            } catch (IOException e) {
                return true;
            }
        } catch (FileNotFoundException e2) {
            return false;
        }
    }

    public static boolean isFileExists(File file) {
        return file != null && file.exists();
    }

    public static boolean rename(String filePath, String newName) {
        return rename(getFileByPath(filePath), newName);
    }

    public static boolean rename(File file, String newName) {
        if (file == null || !file.exists() || isSpace(newName)) {
            return false;
        }
        if (newName.equals(file.getName())) {
            return true;
        }
        File newFile = new File(file.getParent() + File.separator + newName);
        return !newFile.exists() && file.renameTo(newFile);
    }

    public static boolean isDir(String dirPath) {
        return isDir(getFileByPath(dirPath));
    }

    public static boolean isDir(File file) {
        return file != null && file.exists() && file.isDirectory();
    }

    public static boolean isFile(String filePath) {
        return isFile(getFileByPath(filePath));
    }

    public static boolean isFile(File file) {
        return file != null && file.exists() && file.isFile();
    }

    public static boolean createOrExistsDir(String dirPath) {
        return createOrExistsDir(getFileByPath(dirPath));
    }

    public static boolean createOrExistsDir(File file) {
        return file != null && (!file.exists() ? !file.mkdirs() : !file.isDirectory());
    }

    public static boolean createOrExistsFile(String filePath) {
        return createOrExistsFile(getFileByPath(filePath));
    }

    public static boolean createOrExistsFile(File file) {
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

    public static boolean createFileByDeleteOldFile(String filePath) {
        return createFileByDeleteOldFile(getFileByPath(filePath));
    }

    public static boolean createFileByDeleteOldFile(File file) {
        if (file == null) {
            return false;
        }
        if ((file.exists() && !file.delete()) || !createOrExistsDir(file.getParentFile())) {
            return false;
        }
        try {
            return file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean copy(String srcPath, String destPath) {
        return copy(getFileByPath(srcPath), getFileByPath(destPath), (OnReplaceListener) null);
    }

    public static boolean copy(String srcPath, String destPath, OnReplaceListener listener) {
        return copy(getFileByPath(srcPath), getFileByPath(destPath), listener);
    }

    public static boolean copy(File src, File dest) {
        return copy(src, dest, (OnReplaceListener) null);
    }

    public static boolean copy(File src, File dest, OnReplaceListener listener) {
        if (src == null) {
            return false;
        }
        if (src.isDirectory()) {
            return copyDir(src, dest, listener);
        }
        return copyFile(src, dest, listener);
    }

    private static boolean copyDir(File srcDir, File destDir, OnReplaceListener listener) {
        return copyOrMoveDir(srcDir, destDir, listener, false);
    }

    private static boolean copyFile(File srcFile, File destFile, OnReplaceListener listener) {
        return copyOrMoveFile(srcFile, destFile, listener, false);
    }

    public static boolean move(String srcPath, String destPath) {
        return move(getFileByPath(srcPath), getFileByPath(destPath), (OnReplaceListener) null);
    }

    public static boolean move(String srcPath, String destPath, OnReplaceListener listener) {
        return move(getFileByPath(srcPath), getFileByPath(destPath), listener);
    }

    public static boolean move(File src, File dest) {
        return move(src, dest, (OnReplaceListener) null);
    }

    public static boolean move(File src, File dest, OnReplaceListener listener) {
        if (src == null) {
            return false;
        }
        if (src.isDirectory()) {
            return moveDir(src, dest, listener);
        }
        return moveFile(src, dest, listener);
    }

    public static boolean moveDir(File srcDir, File destDir, OnReplaceListener listener) {
        return copyOrMoveDir(srcDir, destDir, listener, true);
    }

    public static boolean moveFile(File srcFile, File destFile, OnReplaceListener listener) {
        return copyOrMoveFile(srcFile, destFile, listener, true);
    }

    private static boolean copyOrMoveDir(File srcDir, File destDir, OnReplaceListener listener, boolean isMove) {
        if (srcDir == null || destDir == null) {
            return false;
        }
        String srcPath = srcDir.getPath() + File.separator;
        String destPath = destDir.getPath() + File.separator;
        if (destPath.contains(srcPath) || !srcDir.exists() || !srcDir.isDirectory() || !createOrExistsDir(destDir)) {
            return false;
        }
        File[] files = srcDir.listFiles();
        for (File file : files) {
            File oneDestFile = new File(destPath + file.getName());
            if (file.isFile()) {
                if (!copyOrMoveFile(file, oneDestFile, listener, isMove)) {
                    return false;
                }
            } else if (file.isDirectory() && !copyOrMoveDir(file, oneDestFile, listener, isMove)) {
                return false;
            }
        }
        return !isMove || deleteDir(srcDir);
    }

    private static boolean copyOrMoveFile(File srcFile, File destFile, OnReplaceListener listener, boolean isMove) {
        if (srcFile == null || destFile == null || srcFile.equals(destFile) || !srcFile.exists() || !srcFile.isFile()) {
            return false;
        }
        if (destFile.exists()) {
            if (listener != null && !listener.onReplace(srcFile, destFile)) {
                return true;
            }
            if (!destFile.delete()) {
                return false;
            }
        }
        if (!createOrExistsDir(destFile.getParentFile())) {
            return false;
        }
        try {
            if (writeFileFromIS(destFile, new FileInputStream(srcFile))) {
                if (isMove) {
                    if (!deleteFile(srcFile)) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean delete(String filePath) {
        return delete(getFileByPath(filePath));
    }

    public static boolean delete(File file) {
        if (file == null) {
            return false;
        }
        if (file.isDirectory()) {
            return deleteDir(file);
        }
        return deleteFile(file);
    }

    private static boolean deleteDir(File dir) {
        if (dir == null) {
            return false;
        }
        if (dir.exists()) {
            if (!dir.isDirectory()) {
                return false;
            }
            File[] files = dir.listFiles();
            if (files != null && files.length != 0) {
                for (File file : files) {
                    if (file.isFile()) {
                        if (!file.delete()) {
                            return false;
                        }
                    } else if (file.isDirectory() && !deleteDir(file)) {
                        return false;
                    }
                }
            }
            return dir.delete();
        }
        return true;
    }

    private static boolean deleteFile(File file) {
        return file != null && (!file.exists() || (file.isFile() && file.delete()));
    }

    public static boolean deleteAllInDir(String dirPath) {
        return deleteAllInDir(getFileByPath(dirPath));
    }

    public static boolean deleteAllInDir(File dir) {
        return deleteFilesInDirWithFilter(dir, new FileFilter() { // from class: com.android.car.utils.FileUtils.1
            @Override // java.io.FileFilter
            public boolean accept(File pathname) {
                return true;
            }
        });
    }

    public static boolean deleteFilesInDir(String dirPath) {
        return deleteFilesInDir(getFileByPath(dirPath));
    }

    public static boolean deleteFilesInDir(File dir) {
        return deleteFilesInDirWithFilter(dir, new FileFilter() { // from class: com.android.car.utils.FileUtils.2
            @Override // java.io.FileFilter
            public boolean accept(File pathname) {
                return pathname.isFile();
            }
        });
    }

    public static boolean deleteFilesInDirWithFilter(String dirPath, FileFilter filter) {
        return deleteFilesInDirWithFilter(getFileByPath(dirPath), filter);
    }

    public static boolean deleteFilesInDirWithFilter(File dir, FileFilter filter) {
        if (dir == null || filter == null) {
            return false;
        }
        if (dir.exists()) {
            if (!dir.isDirectory()) {
                return false;
            }
            File[] files = dir.listFiles();
            if (files != null && files.length != 0) {
                for (File file : files) {
                    if (filter.accept(file)) {
                        if (file.isFile()) {
                            if (!file.delete()) {
                                return false;
                            }
                        } else if (file.isDirectory() && !deleteDir(file)) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }
        return true;
    }

    public static List<File> listFilesInDir(String dirPath) {
        return listFilesInDir(dirPath, (Comparator<File>) null);
    }

    public static List<File> listFilesInDir(File dir) {
        return listFilesInDir(dir, (Comparator<File>) null);
    }

    public static List<File> listFilesInDir(String dirPath, Comparator<File> comparator) {
        return listFilesInDir(getFileByPath(dirPath), false);
    }

    public static List<File> listFilesInDir(File dir, Comparator<File> comparator) {
        return listFilesInDir(dir, false, comparator);
    }

    public static List<File> listFilesInDir(String dirPath, boolean isRecursive) {
        return listFilesInDir(getFileByPath(dirPath), isRecursive);
    }

    public static List<File> listFilesInDir(File dir, boolean isRecursive) {
        return listFilesInDir(dir, isRecursive, (Comparator<File>) null);
    }

    public static List<File> listFilesInDir(String dirPath, boolean isRecursive, Comparator<File> comparator) {
        return listFilesInDir(getFileByPath(dirPath), isRecursive, comparator);
    }

    public static List<File> listFilesInDir(File dir, boolean isRecursive, Comparator<File> comparator) {
        return listFilesInDirWithFilter(dir, new FileFilter() { // from class: com.android.car.utils.FileUtils.3
            @Override // java.io.FileFilter
            public boolean accept(File pathname) {
                return true;
            }
        }, isRecursive, comparator);
    }

    public static List<File> listFilesInDirWithFilter(String dirPath, FileFilter filter) {
        return listFilesInDirWithFilter(getFileByPath(dirPath), filter);
    }

    public static List<File> listFilesInDirWithFilter(File dir, FileFilter filter) {
        return listFilesInDirWithFilter(dir, filter, false, (Comparator<File>) null);
    }

    public static List<File> listFilesInDirWithFilter(String dirPath, FileFilter filter, Comparator<File> comparator) {
        return listFilesInDirWithFilter(getFileByPath(dirPath), filter, comparator);
    }

    public static List<File> listFilesInDirWithFilter(File dir, FileFilter filter, Comparator<File> comparator) {
        return listFilesInDirWithFilter(dir, filter, false, comparator);
    }

    public static List<File> listFilesInDirWithFilter(String dirPath, FileFilter filter, boolean isRecursive) {
        return listFilesInDirWithFilter(getFileByPath(dirPath), filter, isRecursive);
    }

    public static List<File> listFilesInDirWithFilter(File dir, FileFilter filter, boolean isRecursive) {
        return listFilesInDirWithFilter(dir, filter, isRecursive, (Comparator<File>) null);
    }

    public static List<File> listFilesInDirWithFilter(String dirPath, FileFilter filter, boolean isRecursive, Comparator<File> comparator) {
        return listFilesInDirWithFilter(getFileByPath(dirPath), filter, isRecursive, comparator);
    }

    public static List<File> listFilesInDirWithFilter(File dir, FileFilter filter, boolean isRecursive, Comparator<File> comparator) {
        List<File> files = listFilesInDirWithFilterInner(dir, filter, isRecursive);
        if (comparator != null) {
            Collections.sort(files, comparator);
        }
        return files;
    }

    private static List<File> listFilesInDirWithFilterInner(File dir, FileFilter filter, boolean isRecursive) {
        List<File> list = new ArrayList<>();
        if (isDir(dir)) {
            File[] files = dir.listFiles();
            if (files != null && files.length != 0) {
                for (File file : files) {
                    if (filter.accept(file)) {
                        list.add(file);
                    }
                    if (isRecursive && file.isDirectory()) {
                        list.addAll(listFilesInDirWithFilterInner(file, filter, true));
                    }
                }
            }
            return list;
        }
        return list;
    }

    public static long getFileLastModified(String filePath) {
        return getFileLastModified(getFileByPath(filePath));
    }

    public static long getFileLastModified(File file) {
        if (file == null) {
            return -1L;
        }
        return file.lastModified();
    }

    public static String getFileCharsetSimple(String filePath) {
        return getFileCharsetSimple(getFileByPath(filePath));
    }

    public static String getFileCharsetSimple(File file) {
        if (file == null) {
            return "";
        }
        if (isUtf8(file)) {
            return "UTF-8";
        }
        int p = 0;
        InputStream is = null;
        try {
            try {
                try {
                    is = new BufferedInputStream(new FileInputStream(file));
                    p = (is.read() << 8) + is.read();
                    is.close();
                } catch (Throwable th) {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    throw th;
                }
            } catch (IOException e2) {
                e2.printStackTrace();
                if (is != null) {
                    is.close();
                }
            }
        } catch (IOException e3) {
            e3.printStackTrace();
        }
        if (p != 65279) {
            if (p == 65534) {
                return "Unicode";
            }
            return "GBK";
        }
        return "UTF-16BE";
    }

    public static boolean isUtf8(String filePath) {
        return isUtf8(getFileByPath(filePath));
    }

    public static boolean isUtf8(File file) {
        if (file == null) {
            return false;
        }
        InputStream is = null;
        try {
            try {
                byte[] bytes = new byte[24];
                is = new BufferedInputStream(new FileInputStream(file));
                int read = is.read(bytes);
                if (read == -1) {
                    try {
                        is.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return false;
                }
                byte[] readArr = new byte[read];
                System.arraycopy(bytes, 0, readArr, 0, read);
                boolean z = isUtf8(readArr) == 100;
                try {
                    is.close();
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
                return z;
            } catch (IOException e3) {
                e3.printStackTrace();
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e4) {
                        e4.printStackTrace();
                    }
                }
                return false;
            }
        } catch (Throwable th) {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e5) {
                    e5.printStackTrace();
                }
            }
            throw th;
        }
    }

    private static int isUtf8(byte[] raw) {
        int utf8 = 0;
        int ascii = 0;
        if (raw.length > 3 && raw[0] == -17 && raw[1] == -69 && raw[2] == -65) {
            return 100;
        }
        int len = raw.length;
        int child = 0;
        int i = 0;
        while (i < len) {
            if ((raw[i] & (-1)) == -1 || (raw[i] & (-2)) == -2) {
                return 0;
            }
            if (child == 0) {
                if ((raw[i] & ByteCompanionObject.MAX_VALUE) == raw[i] && raw[i] != 0) {
                    ascii++;
                } else if ((raw[i] & (-64)) == -64) {
                    for (int bit = 0; bit < 8 && (((byte) (128 >> bit)) & raw[i]) == ((byte) (128 >> bit)); bit++) {
                        child = bit;
                    }
                    utf8++;
                }
                i++;
            } else {
                int child2 = raw.length - i > child ? child : raw.length - i;
                boolean currentNotUtf8 = false;
                for (int children = 0; children < child2; children++) {
                    if ((raw[i + children] & ByteCompanionObject.MIN_VALUE) != -128) {
                        if ((raw[i + children] & ByteCompanionObject.MAX_VALUE) == raw[i + children] && raw[i] != 0) {
                            ascii++;
                        }
                        currentNotUtf8 = true;
                    }
                }
                if (currentNotUtf8) {
                    utf8--;
                    i++;
                } else {
                    utf8 += child2;
                    i += child2;
                }
                child = 0;
            }
        }
        if (ascii == len) {
            return 100;
        }
        return (int) (((utf8 + ascii) / len) * 100.0f);
    }

    public static int getFileLines(String filePath) {
        return getFileLines(getFileByPath(filePath));
    }

    public static int getFileLines(File file) {
        InputStream is;
        byte[] buffer;
        int count = 1;
        InputStream is2 = null;
        try {
            try {
                try {
                    is = new BufferedInputStream(new FileInputStream(file));
                    buffer = new byte[1024];
                } catch (IOException e) {
                    e.printStackTrace();
                    if (0 != 0) {
                        is2.close();
                    }
                }
            } catch (Throwable th) {
                if (0 != 0) {
                    try {
                        is2.close();
                    } catch (IOException e2) {
                        e2.printStackTrace();
                    }
                }
                throw th;
            }
        } catch (IOException e3) {
            e3.printStackTrace();
        }
        if (LINE_SEP.endsWith("\n")) {
            while (true) {
                int readChars = is.read(buffer, 0, 1024);
                if (readChars == -1) {
                    break;
                }
                for (int i = 0; i < readChars; i++) {
                    if (buffer[i] == 10) {
                        count++;
                    }
                }
            }
            is.close();
            return count;
        }
        while (true) {
            int readChars2 = is.read(buffer, 0, 1024);
            if (readChars2 == -1) {
                break;
            }
            for (int i2 = 0; i2 < readChars2; i2++) {
                if (buffer[i2] == 13) {
                    count++;
                }
            }
        }
        is.close();
        return count;
    }

    public static String getSize(String filePath) {
        return getSize(getFileByPath(filePath));
    }

    public static String getSize(File file) {
        if (file == null) {
            return "";
        }
        if (file.isDirectory()) {
            return getDirSize(file);
        }
        return getFileSize(file);
    }

    private static String getDirSize(File dir) {
        long len = getDirLength(dir);
        return len == -1 ? "" : byte2FitMemorySize(len);
    }

    private static String getFileSize(File file) {
        long len = getFileLength(file);
        return len == -1 ? "" : byte2FitMemorySize(len);
    }

    public static long getLength(String filePath) {
        return getLength(getFileByPath(filePath));
    }

    public static long getLength(File file) {
        if (file == null) {
            return 0L;
        }
        if (file.isDirectory()) {
            return getDirLength(file);
        }
        return getFileLength(file);
    }

    private static long getDirLength(File dir) {
        long length;
        if (isDir(dir)) {
            long len = 0;
            File[] files = dir.listFiles();
            if (files != null && files.length != 0) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        length = getDirLength(file);
                    } else {
                        length = file.length();
                    }
                    len += length;
                }
            }
            return len;
        }
        return -1L;
    }

    public static long getFileLength(String filePath) {
        boolean isURL = filePath.matches("[a-zA-z]+://[^\\s]*");
        if (isURL) {
            try {
                HttpsURLConnection conn = (HttpsURLConnection) new URL(filePath).openConnection();
                conn.setRequestProperty("Accept-Encoding", "identity");
                conn.connect();
                if (conn.getResponseCode() == 200) {
                    return conn.getContentLength();
                }
                return -1L;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return getFileLength(getFileByPath(filePath));
    }

    private static long getFileLength(File file) {
        if (isFile(file)) {
            return file.length();
        }
        return -1L;
    }

    public static String getFileMD5ToString(String filePath) {
        File file = isSpace(filePath) ? null : new File(filePath);
        return getFileMD5ToString(file);
    }

    public static String getFileMD5ToString(File file) {
        return bytes2HexString(getFileMD5(file));
    }

    public static byte[] getFileMD5(String filePath) {
        return getFileMD5(getFileByPath(filePath));
    }

    public static byte[] getFileMD5(File file) {
        if (file == null) {
            return null;
        }
        DigestInputStream dis = null;
        try {
            try {
                FileInputStream fis = new FileInputStream(file);
                MessageDigest md = MessageDigest.getInstance("MD5");
                dis = new DigestInputStream(fis, md);
                byte[] buffer = new byte[262144];
                do {
                } while (dis.read(buffer) > 0);
                MessageDigest md2 = dis.getMessageDigest();
                byte[] digest = md2.digest();
                try {
                    dis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return digest;
            } catch (IOException | NoSuchAlgorithmException e2) {
                e2.printStackTrace();
                if (dis != null) {
                    try {
                        dis.close();
                    } catch (IOException e3) {
                        e3.printStackTrace();
                    }
                }
                return null;
            }
        } catch (Throwable th) {
            if (dis != null) {
                try {
                    dis.close();
                } catch (IOException e4) {
                    e4.printStackTrace();
                }
            }
            throw th;
        }
    }

    public static String getDirName(File file) {
        return file == null ? "" : getDirName(file.getAbsolutePath());
    }

    public static String getDirName(String filePath) {
        int lastSep;
        return (isSpace(filePath) || (lastSep = filePath.lastIndexOf(File.separator)) == -1) ? "" : filePath.substring(0, lastSep + 1);
    }

    public static String getFileName(File file) {
        return file == null ? "" : getFileName(file.getAbsolutePath());
    }

    public static String getFileName(String filePath) {
        if (isSpace(filePath)) {
            return "";
        }
        int lastSep = filePath.lastIndexOf(File.separator);
        return lastSep == -1 ? filePath : filePath.substring(lastSep + 1);
    }

    public static String getFileNameNoExtension(File file) {
        return file == null ? "" : getFileNameNoExtension(file.getPath());
    }

    public static String getFileNameNoExtension(String filePath) {
        if (isSpace(filePath)) {
            return "";
        }
        int lastPoi = filePath.lastIndexOf(46);
        int lastSep = filePath.lastIndexOf(File.separator);
        if (lastSep == -1) {
            return lastPoi == -1 ? filePath : filePath.substring(0, lastPoi);
        } else if (lastPoi == -1 || lastSep > lastPoi) {
            return filePath.substring(lastSep + 1);
        } else {
            return filePath.substring(lastSep + 1, lastPoi);
        }
    }

    public static String getFileExtension(File file) {
        return file == null ? "" : getFileExtension(file.getPath());
    }

    public static String getFileExtension(String filePath) {
        if (isSpace(filePath)) {
            return "";
        }
        int lastPoi = filePath.lastIndexOf(46);
        int lastSep = filePath.lastIndexOf(File.separator);
        return (lastPoi == -1 || lastSep >= lastPoi) ? "" : filePath.substring(lastPoi + 1);
    }

    public static void notifySystemToScan(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        Intent intent = new Intent("android.intent.action.MEDIA_SCANNER_SCAN_FILE");
        Uri uri = Uri.fromFile(file);
        intent.setData(uri);
    }

    public static void notifySystemToScan(String filePath) {
        notifySystemToScan(getFileByPath(filePath));
    }

    private static String bytes2HexString(byte[] bytes) {
        int len;
        if (bytes == null || (len = bytes.length) <= 0) {
            return "";
        }
        char[] ret = new char[len << 1];
        int j = 0;
        for (int i = 0; i < len; i++) {
            int j2 = j + 1;
            char[] cArr = HEX_DIGITS;
            ret[j] = cArr[(bytes[i] >> 4) & 15];
            j = j2 + 1;
            ret[j2] = cArr[bytes[i] & 15];
        }
        return new String(ret);
    }

    private static String byte2FitMemorySize(long byteNum) {
        if (byteNum < 0) {
            return "shouldn't be less than zero!";
        }
        return byteNum < PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID ? String.format(Locale.getDefault(), "%.3fB", Double.valueOf(byteNum)) : byteNum < PlaybackStateCompat.ACTION_SET_CAPTIONING_ENABLED ? String.format(Locale.getDefault(), "%.3fKB", Double.valueOf(byteNum / 1024.0d)) : byteNum < 1073741824 ? String.format(Locale.getDefault(), "%.3fMB", Double.valueOf(byteNum / 1048576.0d)) : String.format(Locale.getDefault(), "%.3fGB", Double.valueOf(byteNum / 1.073741824E9d));
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

    private static boolean writeFileFromIS(File file, InputStream is) {
        OutputStream os = null;
        try {
            try {
                os = new BufferedOutputStream(new FileOutputStream(file));
                byte[] data = new byte[8192];
                while (true) {
                    int len = is.read(data, 0, 8192);
                    if (len == -1) {
                        break;
                    }
                    os.write(data, 0, len);
                }
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
                return true;
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
            return false;
        }
    }
}
