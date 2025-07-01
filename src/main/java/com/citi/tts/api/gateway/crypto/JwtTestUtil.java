package com.citi.tts.api.gateway.crypto;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT测试工具类
 * 用于生成有效的测试token
 */
@Slf4j
@Component
public class JwtTestUtil {

    private static final String SECRET = "mySuperSecretKeyForJWT1234567890";
    private static final String ISSUER = "citi";
    private static final String AUDIENCE = "api";

    /**
     * 生成测试用的JWT token
     */
    public String generateTestToken(String userId, String tenantId, String role) {
        return generateTestToken(userId, tenantId, role, 3600); // 1小时过期
    }

    /**
     * 生成测试用的JWT token（指定过期时间）
     */
    public String generateTestToken(String userId, String tenantId, String role, long expirationSeconds) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
            
            Instant now = Instant.now();
            Instant expiration = now.plusSeconds(expirationSeconds);
            
            Map<String, Object> claims = new HashMap<>();
            claims.put("tenantId", tenantId);
            claims.put("role", role);
            
            String token = Jwts.builder()
                    .setSubject(userId)
                    .setIssuer(ISSUER)
                    .setAudience(AUDIENCE)
                    .setIssuedAt(Date.from(now))
                    .setExpiration(Date.from(expiration))
                    .addClaims(claims)
                    .signWith(key)
                    .compact();
            
            log.info("Generated test JWT token for user: {}, tenant: {}, role: {}", userId, tenantId, role);
            return token;
            
        } catch (Exception e) {
            log.error("Failed to generate test JWT token: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 生成用户token
     */
    public String generateUserToken(String userId, String tenantId) {
        return generateTestToken(userId, tenantId, "user");
    }

    /**
     * 生成管理员token
     */
    public String generateAdminToken(String userId, String tenantId) {
        return generateTestToken(userId, tenantId, "admin");
    }

    /**
     * 生成匿名用户token
     */
    public String generateAnonymousToken() {
        return generateTestToken("anonymous", "default", "anonymous");
    }

    /**
     * 生成过期的token（用于测试过期处理）
     */
    public String generateExpiredToken(String userId, String tenantId) {
        return generateTestToken(userId, tenantId, "user", -3600); // 1小时前过期
    }

    /**
     * 验证token是否有效
     */
    public boolean validateToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
            
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            
            return true;
            
        } catch (Exception e) {
            log.warn("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取token中的用户ID
     */
    public String getUserIdFromToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
            
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
            
        } catch (Exception e) {
            log.warn("Failed to extract user ID from token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取token中的租户ID
     */
    public String getTenantIdFromToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
            
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .get("tenantId", String.class);
            
        } catch (Exception e) {
            log.warn("Failed to extract tenant ID from token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 生成测试用的Authorization头部
     */
    public String generateAuthHeader(String userId, String tenantId, String role) {
        String token = generateTestToken(userId, tenantId, role);
        return token != null ? "Bearer " + token : null;
    }

    /**
     * 生成用户Authorization头部
     */
    public String generateUserAuthHeader(String userId, String tenantId) {
        return generateAuthHeader(userId, tenantId, "user");
    }

    /**
     * 生成管理员Authorization头部
     */
    public String generateAdminAuthHeader(String userId, String tenantId) {
        return generateAuthHeader(userId, tenantId, "admin");
    }
} 