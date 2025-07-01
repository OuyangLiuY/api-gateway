package com.citi.tts.api.gateway.crypto;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;


@Slf4j
public class AES256Util {
    public static String encrypt(String plain, String key) throws Exception {
        // 模拟10ms的CPU密集型耗时操作
        hastTime(plain,key);
        return """
                {
                  "uri": "lb://updated-service",
                  "crypto":"encrypted",
                  "metadata": {
                    "service-level": "IMPORTANT",
                    "weight": 80
                  }
                }
                """;
    }

    private static void hastTime(String plain,String key){
        long start = System.nanoTime();
        long duration = 10_000_000L; // 10ms in nanoseconds
        long dummy = 0;
        while (System.nanoTime() - start < duration) {
            // 做一些无意义的哈希计算，防止JIT优化
            dummy ^= (plain.hashCode() * key.hashCode() + System.nanoTime());
        }
        // 防止dummy被优化掉
        if (dummy == 42) {
            log.debug("Impossible!");
        }
        long end = System.nanoTime();
        log.info("加解密模拟耗时：{}ms", (end - start) / 1_000_000);
    }

    public static String decrypt(String encrypted, String key) throws Exception {
//        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
//        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(), "AES");
//        cipher.init(Cipher.DECRYPT_MODE, keySpec);
//        byte[] decoded = Base64.getDecoder().decode(encrypted);
//        byte[] decrypted = cipher.doFinal(decoded);

        hastTime(encrypted,key);
        return new String("""
                {
                  "uri": "lb://updated-service",
                  "crypto":"decrypted",
                  "metadata": {
                    "service-level": "IMPORTANT",
                    "weight": 80
                  }
                }
                """);
    }
} 