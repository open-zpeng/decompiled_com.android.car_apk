package com.android.car.trust;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.util.Base64;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Calendar;
import java.util.GregorianCalendar;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
/* loaded from: classes3.dex */
public class AESKeystoreUtils {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static byte[] encryptIv;

    private static void createKey(String alias) {
        AlgorithmParameterSpec spec = null;
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES", "AndroidKeyStore");
            Calendar start = new GregorianCalendar();
            Calendar end = new GregorianCalendar();
            end.add(1, 10);
            if (Build.VERSION.SDK_INT >= 23) {
                spec = new KeyGenParameterSpec.Builder(alias, 3).setBlockModes("GCM").setEncryptionPaddings("NoPadding").setCertificateNotBefore(start.getTime()).setCertificateNotAfter(end.getTime()).build();
            }
            keyGenerator.init(spec);
            keyGenerator.generateKey();
        } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException | NoSuchProviderException e) {
            e.printStackTrace();
        }
    }

    public static String encryptData(String needEncrypt, String alias) {
        if (!isHaveKeyStore(alias)) {
            createKey(alias);
        }
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            KeyStore.SecretKeyEntry secretKeyEntry = (KeyStore.SecretKeyEntry) keyStore.getEntry(alias, null);
            SecretKey secretKey = secretKeyEntry.getSecretKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(1, secretKey);
            encryptIv = cipher.getIV();
            return Base64.encodeToString(cipher.doFinal(needEncrypt.getBytes()), 2);
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        } catch (NullPointerException e2) {
            e2.printStackTrace();
            return "空指针异常";
        } catch (InvalidKeyException e3) {
            e3.printStackTrace();
            return "";
        } catch (KeyStoreException e4) {
            e4.printStackTrace();
            return "";
        } catch (NoSuchAlgorithmException e5) {
            e5.printStackTrace();
            return "";
        } catch (UnrecoverableEntryException e6) {
            e6.printStackTrace();
            return "";
        } catch (CertificateException e7) {
            e7.printStackTrace();
            return "";
        } catch (BadPaddingException e8) {
            e8.printStackTrace();
            return "";
        } catch (IllegalBlockSizeException e9) {
            e9.printStackTrace();
            return "";
        } catch (NoSuchPaddingException e10) {
            e10.printStackTrace();
            return "";
        }
    }

    public static String decryptData(String needDecrypt, String alias) {
        if (!isHaveKeyStore(alias)) {
            createKey(alias);
        }
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            KeyStore.SecretKeyEntry secretKeyEntry = (KeyStore.SecretKeyEntry) keyStore.getEntry(alias, null);
            SecretKey secretKey = secretKeyEntry.getSecretKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, encryptIv);
            encryptIv = cipher.getIV();
            cipher.init(2, secretKey, gcmParameterSpec);
            return new String(cipher.doFinal(Base64.decode(needDecrypt, 2)));
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        } catch (InvalidAlgorithmParameterException e2) {
            e2.printStackTrace();
            return "";
        } catch (InvalidKeyException e3) {
            e3.printStackTrace();
            return "";
        } catch (KeyStoreException e4) {
            e4.printStackTrace();
            return "";
        } catch (NoSuchAlgorithmException e5) {
            e5.printStackTrace();
            return "";
        } catch (UnrecoverableEntryException e6) {
            e6.printStackTrace();
            return "";
        } catch (CertificateException e7) {
            e7.printStackTrace();
            return "";
        } catch (BadPaddingException e8) {
            e8.printStackTrace();
            return "";
        } catch (IllegalBlockSizeException e9) {
            e9.printStackTrace();
            return "";
        } catch (NoSuchPaddingException e10) {
            e10.printStackTrace();
            return "";
        }
    }

    public static void clearKeystore(String alias) {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            keyStore.deleteEntry(alias);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean isHaveKeyStore(String alias) {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            KeyStore.Entry keyentry = keyStore.getEntry(alias, null);
            if (keyentry != null) {
                return true;
            }
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } catch (KeyStoreException e2) {
            e2.printStackTrace();
            return false;
        } catch (NoSuchAlgorithmException e3) {
            e3.printStackTrace();
            return false;
        } catch (UnrecoverableEntryException e4) {
            e4.printStackTrace();
            return false;
        } catch (CertificateException e5) {
            e5.printStackTrace();
            return false;
        }
    }
}
