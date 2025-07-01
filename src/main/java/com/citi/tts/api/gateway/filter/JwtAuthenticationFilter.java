package com.citi.tts.api.gateway.filter;

import com.citi.tts.api.gateway.crypto.JwtKeyProvider;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import java.security.PublicKey;


@Component
public class JwtAuthenticationFilter implements GatewayFilter {

    @Autowired
    @Qualifier("userInfoCache")
    private LoadingCache<String, Object> userInfoCache;

    private String extractJwt(ServerWebExchange exchange) {
        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return null;
    }

    private String extractKid(String token) {
        // 解析JWT header获取kid
        // ...
        return null;
    }

    private String extractTenantId(ServerWebExchange exchange) {
        // 从请求头、参数或路径中提取tenantId
        // ...
        return null;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String token = extractJwt(exchange);
        String kid = extractKid(token);
        String tenantId = extractTenantId(exchange);
//        userInfoCache.get();
//        if (publicKey == null) {
//            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
//            return exchange.getResponse().setComplete();
//        }
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey("").build().parseClaimsJws(token).getBody();
            // 可将claims注入上下文
        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }
}