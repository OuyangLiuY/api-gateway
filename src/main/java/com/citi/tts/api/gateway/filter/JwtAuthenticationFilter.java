package com.citi.tts.api.gateway.filter;

import com.citi.tts.api.gateway.crypto.JwtKeyProvider;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Component
public class JwtAuthenticationFilter implements GatewayFilter, Ordered {

    @Autowired
    @Qualifier("userInfoCache")
    private LoadingCache<String, Object> userInfoCache;

    @Autowired
    private JwtKeyProvider jwtKeyProvider;

    private static final String SECRET = "mySuperSecretKeyForJWT1234567890";
    private static final String SESSION_HEADER = "X-Session";
    private static final String USER_ID_HEADER = "X-User-ID";
    private static final String TENANT_ID_HEADER = "X-Tenant-ID";

    /**
     * 从请求中提取JWT token
     */
    private String extractJwt(ServerWebExchange exchange) {
        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (StringUtils.hasText(auth) && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return null;
    }

    /**
     * 从JWT token中提取kid（Key ID）
     */
    private String extractKid(String token) {
        try {
            // 解析JWT header获取kid
            String[] parts = token.split("\\.");
            if (parts.length >= 1) {
                String header = new String(Base64.getUrlDecoder().decode(parts[0]));
                // 这里可以解析JSON header获取kid，简化实现
                if (header.contains("kid")) {
                    // 实际项目中应该解析JSON
                    return "default-kid";
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract kid from token: {}", e.getMessage());
        }
        return "default-kid";
    }

    /**
     * 从请求中提取租户ID
     */
    private String extractTenantId(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        
        // 1. 从请求头获取
        String tenantId = request.getHeaders().getFirst("X-Tenant-ID");
        if (StringUtils.hasText(tenantId)) {
            return tenantId;
        }
        
        // 2. 从查询参数获取
        tenantId = request.getQueryParams().getFirst("tenantId");
        if (StringUtils.hasText(tenantId)) {
            return tenantId;
        }
        
        // 3. 从路径中提取（例如：/api/v1/tenant/{tenantId}/users）
        String path = request.getPath().value();
        if (path.contains("/tenant/")) {
            String[] pathParts = path.split("/tenant/");
            if (pathParts.length > 1) {
                String[] remainingParts = pathParts[1].split("/");
                if (remainingParts.length > 0) {
                    return remainingParts[0];
                }
            }
        }
        
        // 4. 默认租户ID
        return "default-tenant";
    }

    /**
     * 生成会话ID
     */
    private String generateSessionId(Claims claims) {
        String userId = claims.getSubject();
        String tenantId = claims.get("tenantId", String.class);
        long timestamp = claims.getIssuedAt().getTime();
        
        // 生成基于用户信息的会话ID
        String sessionBase = String.format("%s-%s-%d", 
            userId != null ? userId : "anonymous",
            tenantId != null ? tenantId : "default",
            timestamp
        );
        
        // 添加随机性
        return UUID.nameUUIDFromBytes(sessionBase.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * 验证JWT token
     */
    private Claims validateJwtToken(String token) {
        try {
            // 使用密钥验证JWT
            SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
            
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            
            log.debug("JWT token validated successfully for user: {}", claims.getSubject());
            return claims;
            
        } catch (Exception e) {
            log.warn("JWT token validation failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 创建增强的请求
     */
    private ServerHttpRequest createEnhancedRequest(ServerWebExchange exchange, Claims claims) {
        String sessionId = generateSessionId(claims);
        String userId = claims.getSubject();
        String tenantId = claims.get("tenantId", String.class);
        
        if (tenantId == null) {
            tenantId = extractTenantId(exchange);
        }
        
        log.debug("Creating enhanced request with session: {}, user: {}, tenant: {}", 
                sessionId, userId, tenantId);
        
        return exchange.getRequest().mutate()
                .header(SESSION_HEADER, sessionId)
                .header(USER_ID_HEADER, userId != null ? userId : "anonymous")
                .header(TENANT_ID_HEADER, tenantId != null ? tenantId : "default")
                .build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String token = extractJwt(exchange);
        
        // 如果没有token，继续处理（可能是公开接口）
        if (token == null) {
            log.debug("No JWT token found, continuing with anonymous access");
            return chain.filter(exchange);
        }
        
        try {
            // 验证JWT token
            Claims claims = validateJwtToken(token);
            if (claims == null) {
                log.warn("Invalid JWT token");
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
            
            // 提取kid和tenantId（用于日志和缓存）
            String kid = extractKid(token);
            String tenantId = extractTenantId(exchange);
            
            log.debug("JWT authentication successful - kid: {}, tenant: {}, user: {}", 
                    kid, tenantId, claims.getSubject());
            
            // 创建增强的请求，添加会话信息
            ServerHttpRequest enhancedRequest = createEnhancedRequest(exchange, claims);
            
            // 缓存用户信息（可选）
            if (userInfoCache != null && claims.getSubject() != null) {
                String cacheKey = String.format("user:%s:%s", tenantId, claims.getSubject());
                userInfoCache.put(cacheKey, claims);
            }
            
            // 继续过滤器链，使用增强的请求
            return chain.filter(exchange.mutate().request(enhancedRequest).build());
            
        } catch (Exception e) {
            log.error("JWT authentication failed: {}", e.getMessage(), e);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10; // 在路由过滤器之前执行
    }
}