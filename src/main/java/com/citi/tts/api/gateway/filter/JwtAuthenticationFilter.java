package com.citi.tts.api.gateway.filter;

import com.citi.tts.api.gateway.crypto.JwtKeyProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import java.security.PublicKey;

public class JwtAuthenticationFilter implements WebFilter {
    private final JwtKeyProvider keyProvider;

    public JwtAuthenticationFilter(JwtKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String token = extractJwt(exchange);
        String kid = extractKid(token);
        String tenantId = extractTenantId(exchange);
        PublicKey publicKey = keyProvider.getPublicKey(kid, tenantId);
        if (publicKey == null) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(publicKey).build().parseClaimsJws(token).getBody();
            // 可将claims注入上下文
        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

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
} 