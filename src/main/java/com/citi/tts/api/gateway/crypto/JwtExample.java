package com.citi.tts.api.gateway.crypto;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.Claims;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class JwtExample {
    public static void main(String[] args) {
        // 1. 定义密钥（生产环境请用更复杂的key）
        String secret = "mySuperSecretKeyForJWT1234567890"; // 至少32位
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());

        // 2. 构造要加密的数据
        Map<String, Object> payload = new HashMap<>();
        payload.put("\"X-Session\"", "{\"clientId\":\"188756661\"}");

        // 3. 生成JWT
        String jwt = Jwts.builder()
                .setClaims(payload)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        System.out.println("生成的JWT: " + new String(Base64.getEncoder().encode(jwt.getBytes())));
        String en = new String(Base64.getEncoder().encode(jwt.getBytes()));

        // 4. 验证并解析JWT
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(new String(Base64.getDecoder().decode(en.getBytes())))
                .getBody();

        System.out.println("解密后的数据: " + claims);
    }
}