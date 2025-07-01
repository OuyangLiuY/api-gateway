package com.citi.tts.api.gateway.crypto;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JWT完整示例
 * 展示Header、Payload和Signature的详细用法
 */
@Slf4j
@Component
public class JwtExample {

    private static final String SECRET = "mySuperSecretKeyForJWT1234567890";
    private static final String ISSUER = "citi-gateway";
    private static final String AUDIENCE = "api-gateway";

    /**
     * 生成完整的JWT Token（包含所有标准字段）
     */
    public String generateCompleteJwtToken(String userId, String tenantId, String role) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
            
            Instant now = Instant.now();
            Instant expiration = now.plusSeconds(3600); // 1小时过期
            
            // 构建完整的Payload
            Map<String, Object> claims = buildCompleteClaims(userId, tenantId, role, now);
            
            String token = Jwts.builder()
                    .setSubject(userId)
                    .setIssuer(ISSUER)
                    .setAudience(AUDIENCE)
                    .setIssuedAt(Date.from(now))
                    .setExpiration(Date.from(expiration))
                    .setId("jwt-" + System.currentTimeMillis()) // JTI
                    .addClaims(claims)
                    .signWith(key)
                    .compact();
            
            log.info("Generated complete JWT token for user: {}, tenant: {}, role: {}", userId, tenantId, role);
            return token;
            
        } catch (Exception e) {
            log.error("Failed to generate complete JWT token: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 构建完整的Claims
     */
    private Map<String, Object> buildCompleteClaims(String userId, String tenantId, String role, Instant now) {
        Map<String, Object> claims = new HashMap<>();
        
        // 标准Claims（已在builder中设置）
        // iss, sub, aud, exp, iat, jti
        
        // 用户基本信息
        claims.put("name", "用户" + userId);
        claims.put("email", userId + "@citi.com");
        claims.put("phone", "+86-138-0013-8000");
        
        // 权限信息
        claims.put("role", role);
        claims.put("permissions", buildPermissions(role));
        
        // 组织信息
        claims.put("tenantId", tenantId);
        claims.put("orgId", "org-" + tenantId);
        claims.put("department", "IT部门");
        
        // 会话信息
        claims.put("sessionId", "session-" + System.currentTimeMillis());
        claims.put("loginTime", now.getEpochSecond());
        claims.put("ipAddress", "192.168.1.100");
        claims.put("userAgent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        
        // 业务信息
        claims.put("userType", "premium");
        claims.put("subscriptionLevel", "gold");
        claims.put("accountStatus", "active");
        
        // 安全信息
        claims.put("securityLevel", "high");
        claims.put("mfaEnabled", true);
        claims.put("lastPasswordChange", now.getEpochSecond());
        
        // 偏好设置
        claims.put("preferences", buildPreferences());
        
        // 地理位置信息
        claims.put("location", buildLocation());
        
        // 设备信息
        claims.put("device", buildDeviceInfo());
        
        // 审计信息
        claims.put("audit", buildAuditInfo(userId, now));
        
        return claims;
    }

    /**
     * 构建权限列表
     */
    private List<String> buildPermissions(String role) {
        switch (role.toLowerCase()) {
            case "admin":
                return List.of(
                    "user:read", "user:write", "user:delete",
                    "admin:manage", "system:config", "audit:view"
                );
            case "manager":
                return List.of(
                    "user:read", "user:write",
                    "team:manage", "report:view"
                );
            case "user":
                return List.of(
                    "user:read", "user:write"
                );
            default:
                return List.of("user:read");
        }
    }

    /**
     * 构建偏好设置
     */
    private Map<String, Object> buildPreferences() {
        Map<String, Object> preferences = new HashMap<>();
        preferences.put("theme", "dark");
        preferences.put("language", "zh-CN");
        preferences.put("timezone", "Asia/Shanghai");
        
        Map<String, Boolean> notifications = new HashMap<>();
        notifications.put("email", true);
        notifications.put("sms", false);
        notifications.put("push", true);
        preferences.put("notifications", notifications);
        
        return preferences;
    }

    /**
     * 构建地理位置信息
     */
    private Map<String, String> buildLocation() {
        Map<String, String> location = new HashMap<>();
        location.put("country", "CN");
        location.put("region", "Beijing");
        location.put("city", "Beijing");
        location.put("timezone", "Asia/Shanghai");
        return location;
    }

    /**
     * 构建设备信息
     */
    private Map<String, String> buildDeviceInfo() {
        Map<String, String> device = new HashMap<>();
        device.put("deviceId", "device-" + System.currentTimeMillis());
        device.put("deviceType", "desktop");
        device.put("os", "Windows 10");
        device.put("browser", "Chrome");
        device.put("appVersion", "1.2.3");
        return device;
    }

    /**
     * 构建审计信息
     */
    private Map<String, Object> buildAuditInfo(String userId, Instant now) {
        Map<String, Object> audit = new HashMap<>();
        audit.put("createdBy", "system");
        audit.put("createdAt", now.getEpochSecond());
        audit.put("modifiedBy", userId);
        audit.put("modifiedAt", now.getEpochSecond());
        audit.put("version", "1.0");
        return audit;
    }

    /**
     * 解析JWT Token并显示所有信息
     */
    public void parseAndDisplayJwtToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
            
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            
            log.info("=== JWT Token 解析结果 ===");
            log.info("Header: {}", Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getHeader());
            log.info("Payload: {}", claims);
            
            // 显示标准Claims
            log.info("--- 标准Claims ---");
            log.info("Issuer (iss): {}", claims.getIssuer());
            log.info("Subject (sub): {}", claims.getSubject());
            log.info("Audience (aud): {}", claims.getAudience());
            log.info("Expiration (exp): {}", claims.getExpiration());
            log.info("Issued At (iat): {}", claims.getIssuedAt());
            log.info("JWT ID (jti): {}", claims.getId());
            
            // 显示自定义Claims
            log.info("--- 自定义Claims ---");
            log.info("Name: {}", claims.get("name"));
            log.info("Email: {}", claims.get("email"));
            log.info("Role: {}", claims.get("role"));
            log.info("Tenant ID: {}", claims.get("tenantId"));
            log.info("Permissions: {}", claims.get("permissions"));
            log.info("Session ID: {}", claims.get("sessionId"));
            log.info("User Type: {}", claims.get("userType"));
            log.info("Security Level: {}", claims.get("securityLevel"));
            log.info("Preferences: {}", claims.get("preferences"));
            log.info("Location: {}", claims.get("location"));
            log.info("Device: {}", claims.get("device"));
            log.info("Audit: {}", claims.get("audit"));
            
        } catch (Exception e) {
            log.error("Failed to parse JWT token: {}", e.getMessage(), e);
        }
    }

    /**
     * 生成不同类型的JWT Token
     */
    public String generateTokenByType(String userId, String tenantId, String tokenType) {
        switch (tokenType.toLowerCase()) {
            case "admin":
                return generateCompleteJwtToken(userId, tenantId, "admin");
            case "manager":
                return generateCompleteJwtToken(userId, tenantId, "manager");
            case "user":
                return generateCompleteJwtToken(userId, tenantId, "user");
            case "guest":
                return generateCompleteJwtToken(userId, tenantId, "guest");
            default:
                return generateCompleteJwtToken(userId, tenantId, "user");
        }
    }

    /**
     * 验证JWT Token的有效性
     */
    public boolean validateJwtToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
            
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            
            log.info("JWT token validation successful");
            return true;
            
        } catch (Exception e) {
            log.warn("JWT token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取JWT Token的过期时间
     */
    public Date getExpirationTime(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
            
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            
            return claims.getExpiration();
            
        } catch (Exception e) {
            log.error("Failed to get expiration time: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 检查JWT Token是否即将过期
     */
    public boolean isTokenExpiringSoon(String token, long thresholdSeconds) {
        Date expiration = getExpirationTime(token);
        if (expiration == null) {
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        long expirationTime = expiration.getTime();
        long timeUntilExpiration = expirationTime - currentTime;
        
        return timeUntilExpiration <= (thresholdSeconds * 1000);
    }
}