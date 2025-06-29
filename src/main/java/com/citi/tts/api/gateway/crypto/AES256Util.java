package com.citi.tts.api.gateway.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class AES256Util {
    public static String encrypt(String plain, String key) throws Exception {
//        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
//        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(), "AES");
//        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
//        byte[] encrypted = cipher.doFinal(plain.getBytes());
//        return Base64.getEncoder().encodeToString(encrypted);
        Thread.sleep(10);
        return plain + "[encrypt]";
    }

    public static String decrypt(String encrypted, String key) throws Exception {
//        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
//        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(), "AES");
//        cipher.init(Cipher.DECRYPT_MODE, keySpec);
//        byte[] decoded = Base64.getDecoder().decode(encrypted);
//        byte[] decrypted = cipher.doFinal(decoded);

        Thread.sleep(10);
        return new String(encrypted + "[decrypt]");
    }
} 