package com.android.car.utils;

import android.util.Base64;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.DigestInputStream;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
/* loaded from: classes3.dex */
public final class EncryptUtils {
    private static final char[] HEX_DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    private EncryptUtils() {
        throw new UnsupportedOperationException("u can't instantiate me...");
    }

    public static String encryptMD2ToString(String data) {
        if (data == null || data.length() == 0) {
            return "";
        }
        return encryptMD2ToString(data.getBytes());
    }

    public static String encryptMD2ToString(byte[] data) {
        return bytes2HexString(encryptMD2(data));
    }

    public static byte[] encryptMD2(byte[] data) {
        return hashTemplate(data, "MD2");
    }

    public static String encryptMD5ToString(String data) {
        if (data == null || data.length() == 0) {
            return "";
        }
        return encryptMD5ToString(data.getBytes());
    }

    public static String encryptMD5ToString(String data, String salt) {
        if (data == null && salt == null) {
            return "";
        }
        if (salt == null) {
            return bytes2HexString(encryptMD5(data.getBytes()));
        }
        if (data == null) {
            return bytes2HexString(encryptMD5(salt.getBytes()));
        }
        return bytes2HexString(encryptMD5((data + salt).getBytes()));
    }

    public static String encryptMD5ToString(byte[] data) {
        return bytes2HexString(encryptMD5(data));
    }

    public static String encryptMD5ToString(byte[] data, byte[] salt) {
        if (data == null && salt == null) {
            return "";
        }
        if (salt == null) {
            return bytes2HexString(encryptMD5(data));
        }
        if (data == null) {
            return bytes2HexString(encryptMD5(salt));
        }
        byte[] dataSalt = new byte[data.length + salt.length];
        System.arraycopy(data, 0, dataSalt, 0, data.length);
        System.arraycopy(salt, 0, dataSalt, data.length, salt.length);
        return bytes2HexString(encryptMD5(dataSalt));
    }

    public static byte[] encryptMD5(byte[] data) {
        return hashTemplate(data, "MD5");
    }

    public static String encryptMD5File2String(String filePath) {
        File file = isSpace(filePath) ? null : new File(filePath);
        return encryptMD5File2String(file);
    }

    public static byte[] encryptMD5File(String filePath) {
        File file = isSpace(filePath) ? null : new File(filePath);
        return encryptMD5File(file);
    }

    public static String encryptMD5File2String(File file) {
        return bytes2HexString(encryptMD5File(file));
    }

    public static byte[] encryptMD5File(File file) {
        if (file == null) {
            return null;
        }
        FileInputStream fis = null;
        try {
            try {
                fis = new FileInputStream(file);
                MessageDigest md = MessageDigest.getInstance("MD5");
                DigestInputStream digestInputStream = new DigestInputStream(fis, md);
                byte[] buffer = new byte[262144];
                do {
                } while (digestInputStream.read(buffer) > 0);
                MessageDigest md2 = digestInputStream.getMessageDigest();
                byte[] digest = md2.digest();
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return digest;
            } catch (IOException | NoSuchAlgorithmException e2) {
                e2.printStackTrace();
                if (fis != null) {
                    try {
                        fis.close();
                    } catch (IOException e3) {
                        e3.printStackTrace();
                    }
                }
                return null;
            }
        } catch (Throwable th) {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e4) {
                    e4.printStackTrace();
                }
            }
            throw th;
        }
    }

    public static String encryptSHA1ToString(String data) {
        if (data == null || data.length() == 0) {
            return "";
        }
        return encryptSHA1ToString(data.getBytes());
    }

    public static String encryptSHA1ToString(byte[] data) {
        return bytes2HexString(encryptSHA1(data));
    }

    public static byte[] encryptSHA1(byte[] data) {
        return hashTemplate(data, "SHA-1");
    }

    public static String encryptSHA224ToString(String data) {
        if (data == null || data.length() == 0) {
            return "";
        }
        return encryptSHA224ToString(data.getBytes());
    }

    public static String encryptSHA224ToString(byte[] data) {
        return bytes2HexString(encryptSHA224(data));
    }

    public static byte[] encryptSHA224(byte[] data) {
        return hashTemplate(data, "SHA224");
    }

    public static String encryptSHA256ToString(String data) {
        if (data == null || data.length() == 0) {
            return "";
        }
        return encryptSHA256ToString(data.getBytes());
    }

    public static String encryptSHA256ToString(byte[] data) {
        return bytes2HexString(encryptSHA256(data));
    }

    public static byte[] encryptSHA256(byte[] data) {
        return hashTemplate(data, "SHA-256");
    }

    public static String encryptSHA384ToString(String data) {
        if (data == null || data.length() == 0) {
            return "";
        }
        return encryptSHA384ToString(data.getBytes());
    }

    public static String encryptSHA384ToString(byte[] data) {
        return bytes2HexString(encryptSHA384(data));
    }

    public static byte[] encryptSHA384(byte[] data) {
        return hashTemplate(data, "SHA-384");
    }

    public static String encryptSHA512ToString(String data) {
        if (data == null || data.length() == 0) {
            return "";
        }
        return encryptSHA512ToString(data.getBytes());
    }

    public static String encryptSHA512ToString(byte[] data) {
        return bytes2HexString(encryptSHA512(data));
    }

    public static byte[] encryptSHA512(byte[] data) {
        return hashTemplate(data, "SHA-512");
    }

    private static byte[] hashTemplate(byte[] data, String algorithm) {
        if (data == null || data.length <= 0) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            md.update(data);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String encryptHmacMD5ToString(String data, String key) {
        if (data == null || data.length() == 0 || key == null || key.length() == 0) {
            return "";
        }
        return encryptHmacMD5ToString(data.getBytes(), key.getBytes());
    }

    public static String encryptHmacMD5ToString(byte[] data, byte[] key) {
        return bytes2HexString(encryptHmacMD5(data, key));
    }

    public static byte[] encryptHmacMD5(byte[] data, byte[] key) {
        return hmacTemplate(data, key, "HmacMD5");
    }

    public static String encryptHmacSHA1ToString(String data, String key) {
        if (data == null || data.length() == 0 || key == null || key.length() == 0) {
            return "";
        }
        return encryptHmacSHA1ToString(data.getBytes(), key.getBytes());
    }

    public static String encryptHmacSHA1ToString(byte[] data, byte[] key) {
        return bytes2HexString(encryptHmacSHA1(data, key));
    }

    public static byte[] encryptHmacSHA1(byte[] data, byte[] key) {
        return hmacTemplate(data, key, "HmacSHA1");
    }

    public static String encryptHmacSHA224ToString(String data, String key) {
        if (data == null || data.length() == 0 || key == null || key.length() == 0) {
            return "";
        }
        return encryptHmacSHA224ToString(data.getBytes(), key.getBytes());
    }

    public static String encryptHmacSHA224ToString(byte[] data, byte[] key) {
        return bytes2HexString(encryptHmacSHA224(data, key));
    }

    public static byte[] encryptHmacSHA224(byte[] data, byte[] key) {
        return hmacTemplate(data, key, "HmacSHA224");
    }

    public static String encryptHmacSHA256ToString(String data, String key) {
        if (data == null || data.length() == 0 || key == null || key.length() == 0) {
            return "";
        }
        return encryptHmacSHA256ToString(data.getBytes(), key.getBytes());
    }

    public static String encryptHmacSHA256ToString(byte[] data, byte[] key) {
        return bytes2HexString(encryptHmacSHA256(data, key));
    }

    public static byte[] encryptHmacSHA256(byte[] data, byte[] key) {
        return hmacTemplate(data, key, "HmacSHA256");
    }

    public static String encryptHmacSHA384ToString(String data, String key) {
        if (data == null || data.length() == 0 || key == null || key.length() == 0) {
            return "";
        }
        return encryptHmacSHA384ToString(data.getBytes(), key.getBytes());
    }

    public static String encryptHmacSHA384ToString(byte[] data, byte[] key) {
        return bytes2HexString(encryptHmacSHA384(data, key));
    }

    public static byte[] encryptHmacSHA384(byte[] data, byte[] key) {
        return hmacTemplate(data, key, "HmacSHA384");
    }

    public static String encryptHmacSHA512ToString(String data, String key) {
        if (data == null || data.length() == 0 || key == null || key.length() == 0) {
            return "";
        }
        return encryptHmacSHA512ToString(data.getBytes(), key.getBytes());
    }

    public static String encryptHmacSHA512ToString(byte[] data, byte[] key) {
        return bytes2HexString(encryptHmacSHA512(data, key));
    }

    public static byte[] encryptHmacSHA512(byte[] data, byte[] key) {
        return hmacTemplate(data, key, "HmacSHA512");
    }

    private static byte[] hmacTemplate(byte[] data, byte[] key, String algorithm) {
        if (data == null || data.length == 0 || key == null || key.length == 0) {
            return null;
        }
        try {
            SecretKeySpec secretKey = new SecretKeySpec(key, algorithm);
            Mac mac = Mac.getInstance(algorithm);
            mac.init(secretKey);
            return mac.doFinal(data);
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static byte[] encryptDES2Base64(byte[] data, byte[] key, String transformation, byte[] iv) {
        return base64Encode(encryptDES(data, key, transformation, iv));
    }

    public static String encryptDES2HexString(byte[] data, byte[] key, String transformation, byte[] iv) {
        return bytes2HexString(encryptDES(data, key, transformation, iv));
    }

    public static byte[] encryptDES(byte[] data, byte[] key, String transformation, byte[] iv) {
        return symmetricTemplate(data, key, "DES", transformation, iv, true);
    }

    public static byte[] decryptBase64DES(byte[] data, byte[] key, String transformation, byte[] iv) {
        return decryptDES(base64Decode(data), key, transformation, iv);
    }

    public static byte[] decryptHexStringDES(String data, byte[] key, String transformation, byte[] iv) {
        return decryptDES(hexString2Bytes(data), key, transformation, iv);
    }

    public static byte[] decryptDES(byte[] data, byte[] key, String transformation, byte[] iv) {
        return symmetricTemplate(data, key, "DES", transformation, iv, false);
    }

    public static byte[] encrypt3DES2Base64(byte[] data, byte[] key, String transformation, byte[] iv) {
        return base64Encode(encrypt3DES(data, key, transformation, iv));
    }

    public static String encrypt3DES2HexString(byte[] data, byte[] key, String transformation, byte[] iv) {
        return bytes2HexString(encrypt3DES(data, key, transformation, iv));
    }

    public static byte[] encrypt3DES(byte[] data, byte[] key, String transformation, byte[] iv) {
        return symmetricTemplate(data, key, "DESede", transformation, iv, true);
    }

    public static byte[] decryptBase64_3DES(byte[] data, byte[] key, String transformation, byte[] iv) {
        return decrypt3DES(base64Decode(data), key, transformation, iv);
    }

    public static byte[] decryptHexString3DES(String data, byte[] key, String transformation, byte[] iv) {
        return decrypt3DES(hexString2Bytes(data), key, transformation, iv);
    }

    public static byte[] decrypt3DES(byte[] data, byte[] key, String transformation, byte[] iv) {
        return symmetricTemplate(data, key, "DESede", transformation, iv, false);
    }

    public static byte[] encryptAES2Base64(byte[] data, byte[] key, String transformation, byte[] iv) {
        return base64Encode(encryptAES(data, key, transformation, iv));
    }

    public static String encryptAES2HexString(byte[] data, byte[] key, String transformation, byte[] iv) {
        return bytes2HexString(encryptAES(data, key, transformation, iv));
    }

    public static byte[] encryptAES(byte[] data, byte[] key, String transformation, byte[] iv) {
        return symmetricTemplate(data, key, "AES", transformation, iv, true);
    }

    public static byte[] decryptBase64AES(byte[] data, byte[] key, String transformation, byte[] iv) {
        return decryptAES(base64Decode(data), key, transformation, iv);
    }

    public static byte[] decryptHexStringAES(String data, byte[] key, String transformation, byte[] iv) {
        return decryptAES(hexString2Bytes(data), key, transformation, iv);
    }

    public static byte[] decryptAES(byte[] data, byte[] key, String transformation, byte[] iv) {
        return symmetricTemplate(data, key, "AES", transformation, iv, false);
    }

    private static byte[] symmetricTemplate(byte[] data, byte[] key, String algorithm, String transformation, byte[] iv, boolean isEncrypt) {
        SecretKey secretKey;
        if (data == null || data.length == 0 || key == null || key.length == 0) {
            return null;
        }
        try {
            if ("DES".equals(algorithm)) {
                DESKeySpec desKey = new DESKeySpec(key);
                SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(algorithm);
                secretKey = keyFactory.generateSecret(desKey);
            } else {
                secretKey = new SecretKeySpec(key, algorithm);
            }
            Cipher cipher = Cipher.getInstance(transformation);
            int i = 1;
            if (iv != null && iv.length != 0) {
                AlgorithmParameterSpec params = new IvParameterSpec(iv);
                if (!isEncrypt) {
                    i = 2;
                }
                cipher.init(i, secretKey, params);
                return cipher.doFinal(data);
            }
            i = 2;
            cipher.init(i, secretKey);
            return cipher.doFinal(data);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    public static byte[] encryptRSA2Base64(byte[] data, byte[] publicKey, int keySize, String transformation) {
        return base64Encode(encryptRSA(data, publicKey, keySize, transformation));
    }

    public static String encryptRSA2HexString(byte[] data, byte[] publicKey, int keySize, String transformation) {
        return bytes2HexString(encryptRSA(data, publicKey, keySize, transformation));
    }

    public static byte[] encryptRSA(byte[] data, byte[] publicKey, int keySize, String transformation) {
        return rsaTemplate(data, publicKey, keySize, transformation, true);
    }

    public static byte[] decryptBase64RSA(byte[] data, byte[] privateKey, int keySize, String transformation) {
        return decryptRSA(base64Decode(data), privateKey, keySize, transformation);
    }

    public static byte[] decryptHexStringRSA(String data, byte[] privateKey, int keySize, String transformation) {
        return decryptRSA(hexString2Bytes(data), privateKey, keySize, transformation);
    }

    public static byte[] decryptRSA(byte[] data, byte[] privateKey, int keySize, String transformation) {
        return rsaTemplate(data, privateKey, keySize, transformation, false);
    }

    private static byte[] rsaTemplate(byte[] data, byte[] key, int keySize, String transformation, boolean isEncrypt) {
        Key rsaKey;
        if (data == null || data.length == 0 || key == null || key.length == 0) {
            return null;
        }
        try {
            if (isEncrypt) {
                X509EncodedKeySpec keySpec = new X509EncodedKeySpec(key);
                rsaKey = KeyFactory.getInstance("RSA").generatePublic(keySpec);
            } else {
                PKCS8EncodedKeySpec keySpec2 = new PKCS8EncodedKeySpec(key);
                rsaKey = KeyFactory.getInstance("RSA").generatePrivate(keySpec2);
            }
            if (rsaKey == null) {
                return null;
            }
            Cipher cipher = Cipher.getInstance(transformation);
            cipher.init(isEncrypt ? 1 : 2, rsaKey);
            int len = data.length;
            int maxLen = keySize / 8;
            if (isEncrypt) {
                String lowerTrans = transformation.toLowerCase();
                if (lowerTrans.endsWith("pkcs1padding")) {
                    maxLen -= 11;
                }
            }
            int count = len / maxLen;
            if (count > 0) {
                byte[] ret = new byte[0];
                byte[] buff = new byte[maxLen];
                int index = 0;
                for (int i = 0; i < count; i++) {
                    System.arraycopy(data, index, buff, 0, maxLen);
                    ret = joins(ret, cipher.doFinal(buff));
                    index += maxLen;
                }
                if (index != len) {
                    int restLen = len - index;
                    byte[] buff2 = new byte[restLen];
                    System.arraycopy(data, index, buff2, 0, restLen);
                    return joins(ret, cipher.doFinal(buff2));
                }
                return ret;
            }
            return cipher.doFinal(data);
        } catch (InvalidKeyException e) {
            e.printStackTrace();
            return null;
        } catch (NoSuchAlgorithmException e2) {
            e2.printStackTrace();
            return null;
        } catch (InvalidKeySpecException e3) {
            e3.printStackTrace();
            return null;
        } catch (BadPaddingException e4) {
            e4.printStackTrace();
            return null;
        } catch (IllegalBlockSizeException e5) {
            e5.printStackTrace();
            return null;
        } catch (NoSuchPaddingException e6) {
            e6.printStackTrace();
            return null;
        }
    }

    public static byte[] rc4(byte[] data, byte[] key) {
        if (data == null || data.length == 0 || key == null) {
            return null;
        }
        if (key.length < 1 || key.length > 256) {
            throw new IllegalArgumentException("key must be between 1 and 256 bytes");
        }
        byte[] iS = new byte[256];
        byte[] iK = new byte[256];
        int keyLen = key.length;
        for (int i = 0; i < 256; i++) {
            iS[i] = (byte) i;
            iK[i] = key[i % keyLen];
        }
        int j = 0;
        for (int i2 = 0; i2 < 256; i2++) {
            j = (iS[i2] + j + iK[i2]) & 255;
            byte tmp = iS[j];
            iS[j] = iS[i2];
            iS[i2] = tmp;
        }
        byte[] ret = new byte[data.length];
        int i3 = 0;
        for (int counter = 0; counter < data.length; counter++) {
            i3 = (i3 + 1) & 255;
            j = (iS[i3] + j) & 255;
            byte tmp2 = iS[j];
            iS[j] = iS[i3];
            iS[i3] = tmp2;
            int t = (iS[i3] + iS[j]) & 255;
            int k = iS[t];
            ret[counter] = (byte) (data[counter] ^ k);
        }
        return ret;
    }

    private static byte[] joins(byte[] prefix, byte[] suffix) {
        byte[] ret = new byte[prefix.length + suffix.length];
        System.arraycopy(prefix, 0, ret, 0, prefix.length);
        System.arraycopy(suffix, 0, ret, prefix.length, suffix.length);
        return ret;
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

    private static byte[] hexString2Bytes(String hexString) {
        if (isSpace(hexString)) {
            return null;
        }
        int len = hexString.length();
        if (len % 2 != 0) {
            hexString = "0" + hexString;
            len++;
        }
        char[] hexBytes = hexString.toUpperCase().toCharArray();
        byte[] ret = new byte[len >> 1];
        for (int i = 0; i < len; i += 2) {
            ret[i >> 1] = (byte) ((hex2Dec(hexBytes[i]) << 4) | hex2Dec(hexBytes[i + 1]));
        }
        return ret;
    }

    private static int hex2Dec(char hexChar) {
        if (hexChar >= '0' && hexChar <= '9') {
            return hexChar - '0';
        }
        if (hexChar >= 'A' && hexChar <= 'F') {
            return (hexChar - 'A') + 10;
        }
        throw new IllegalArgumentException();
    }

    private static byte[] base64Encode(byte[] input) {
        return Base64.encode(input, 2);
    }

    private static byte[] base64Decode(byte[] input) {
        return Base64.decode(input, 2);
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
}
