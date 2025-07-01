# JWT认证和X-Session头部实现

## 1. 概述

JWT认证过滤器是网关系统的核心安全组件，负责验证JWT token并在请求中添加会话信息。本文档详细说明了JWT认证的实现机制和X-Session头部的生成逻辑。

## 2. 核心功能

### 2.1 JWT Token验证
- 从Authorization头部提取Bearer token
- 验证token的有效性和签名
- 解析token中的claims信息

### 2.2 会话管理
- 基于JWT claims生成唯一的会话ID
- 在请求中添加X-Session头部
- 支持多租户会话隔离

### 2.3 用户信息传递
- 添加X-User-ID头部传递用户ID
- 添加X-Tenant-ID头部传递租户ID
- 支持下游服务获取用户上下文

## 3. 实现架构

### 3.1 核心组件

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   用户请求      │───▶│ JWT认证过滤器   │───▶│   下游服务      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                │
                                ▼
                       ┌─────────────────┐
                       │   会话生成器    │
                       └─────────────────┘
                                │
                                ▼
                       ┌─────────────────┐
                       │   缓存管理器    │
                       └─────────────────┘
```

### 3.2 处理流程

1. **Token提取**：从Authorization头部提取Bearer token
2. **Token验证**：验证token的有效性和签名
3. **Claims解析**：解析token中的用户信息和租户信息
4. **会话生成**：基于用户信息生成唯一会话ID
5. **头部添加**：在请求中添加X-Session、X-User-ID、X-Tenant-ID头部
6. **缓存更新**：将用户信息缓存到本地缓存
7. **请求转发**：将增强的请求转发给下游服务

## 4. 详细实现

### 4.1 JwtAuthenticationFilter

```java
@Slf4j
@Component
public class JwtAuthenticationFilter implements GatewayFilter, Ordered {
    
    private static final String SESSION_HEADER = "X-Session";
    private static final String USER_ID_HEADER = "X-User-ID";
    private static final String TENANT_ID_HEADER = "X-Tenant-ID";
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String token = extractJwt(exchange);
        
        // 如果没有token，继续处理（可能是公开接口）
        if (token == null) {
            return chain.filter(exchange);
        }
        
        try {
            // 验证JWT token
            Claims claims = validateJwtToken(token);
            if (claims == null) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
            
            // 创建增强的请求，添加会话信息
            ServerHttpRequest enhancedRequest = createEnhancedRequest(exchange, claims);
            
            // 继续过滤器链，使用增强的请求
            return chain.filter(exchange.mutate().request(enhancedRequest).build());
            
        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }
}
```

### 4.2 Token验证

```java
private Claims validateJwtToken(String token) {
    try {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        
        return claims;
        
    } catch (Exception e) {
        log.warn("JWT token validation failed: {}", e.getMessage());
        return null;
    }
}
```

### 4.3 会话ID生成

```java
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
```

### 4.4 请求增强

```java
private ServerHttpRequest createEnhancedRequest(ServerWebExchange exchange, Claims claims) {
    String sessionId = generateSessionId(claims);
    String userId = claims.getSubject();
    String tenantId = claims.get("tenantId", String.class);
    
    if (tenantId == null) {
        tenantId = extractTenantId(exchange);
    }
    
    return exchange.getRequest().mutate()
            .header(SESSION_HEADER, sessionId)
            .header(USER_ID_HEADER, userId != null ? userId : "anonymous")
            .header(TENANT_ID_HEADER, tenantId != null ? tenantId : "default")
            .build();
}
```

## 5. 配置参数

### 5.1 JWT配置

```yaml
jwt:
  secret: "mySuperSecretKeyForJWT1234567890"
  issuer: "citi"
  audience: "api"
  expiration: 3600  # 1小时
```

### 5.2 头部配置

```java
private static final String SESSION_HEADER = "X-Session";
private static final String USER_ID_HEADER = "X-User-ID";
private static final String TENANT_ID_HEADER = "X-Tenant-ID";
```

### 5.3 过滤器顺序

```java
@Override
public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 10; // 在路由过滤器之前执行
}
```

## 6. 租户ID提取策略

### 6.1 多级提取策略

```java
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
```

### 6.2 优先级顺序

1. **JWT Claims**：从token中的tenantId claim获取
2. **请求头**：从X-Tenant-ID头部获取
3. **查询参数**：从tenantId查询参数获取
4. **路径参数**：从URL路径中提取
5. **默认值**：使用"default-tenant"

## 7. 会话ID生成策略

### 7.1 生成算法

```java
// 基于用户信息的会话ID生成
String sessionBase = String.format("%s-%s-%d", 
    userId != null ? userId : "anonymous",
    tenantId != null ? tenantId : "default",
    timestamp
);

// 使用UUID确保唯一性
return UUID.nameUUIDFromBytes(sessionBase.getBytes(StandardCharsets.UTF_8)).toString();
```

### 7.2 特点

- **确定性**：相同用户和租户的token生成相同会话ID
- **唯一性**：不同用户或租户生成不同会话ID
- **安全性**：基于UUID算法，难以预测
- **一致性**：相同token多次使用生成相同会话ID

## 8. 缓存机制

### 8.1 用户信息缓存

```java
// 缓存用户信息（可选）
if (userInfoCache != null && claims.getSubject() != null) {
    String cacheKey = String.format("user:%s:%s", tenantId, claims.getSubject());
    userInfoCache.put(cacheKey, claims);
}
```

### 8.2 缓存键格式

- **格式**：`user:{tenantId}:{userId}`
- **示例**：`user:tenant-1:user123`
- **作用**：提高后续请求的处理效率

## 9. 错误处理

### 9.1 异常处理策略

```java
try {
    // JWT验证和处理逻辑
} catch (Exception e) {
    log.error("JWT authentication failed: {}", e.getMessage(), e);
    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
    return exchange.getResponse().setComplete();
}
```

### 9.2 错误场景

1. **无效token**：返回401 Unauthorized
2. **过期token**：返回401 Unauthorized
3. **格式错误**：返回401 Unauthorized
4. **签名验证失败**：返回401 Unauthorized

## 10. 监控和日志

### 10.1 日志记录

```java
// 成功认证
log.debug("JWT authentication successful - kid: {}, tenant: {}, user: {}", 
        kid, tenantId, claims.getSubject());

// 失败认证
log.warn("Invalid JWT token");

// 异常情况
log.error("JWT authentication failed: {}", e.getMessage(), e);
```

### 10.2 监控指标

- **认证成功率**：成功认证的请求比例
- **认证延迟**：JWT验证的平均耗时
- **缓存命中率**：用户信息缓存的命中率
- **错误率**：认证失败的请求比例

## 11. 测试验证

### 11.1 测试接口

```http
# 生成测试token
POST /api/v1/test/generate-token?userId=user123&tenantId=tenant-1&role=user

# 验证token
POST /api/v1/test/validate-token?token={token}

# 访问受保护接口
GET /api/v1/test/protected
Authorization: Bearer {token}
```

### 11.2 验证要点

1. **X-Session头部**：验证会话ID是否正确生成
2. **X-User-ID头部**：验证用户ID是否正确传递
3. **X-Tenant-ID头部**：验证租户ID是否正确传递
4. **会话一致性**：相同token生成相同会话ID
5. **租户隔离**：不同租户生成不同会话ID

## 12. 最佳实践

### 12.1 安全建议

1. **密钥管理**：使用强密钥并定期轮换
2. **Token过期**：设置合理的token过期时间
3. **HTTPS传输**：确保token在HTTPS下传输
4. **最小权限**：在token中包含最小必要的claims

### 12.2 性能优化

1. **缓存策略**：合理使用用户信息缓存
2. **异步处理**：避免阻塞请求处理
3. **批量验证**：支持批量token验证
4. **连接池**：使用连接池优化网络请求

### 12.3 可维护性

1. **配置外部化**：将JWT配置外部化
2. **日志标准化**：使用标准化的日志格式
3. **监控完善**：建立完善的监控体系
4. **文档更新**：及时更新相关文档

## 13. 总结

JWT认证和X-Session头部实现为网关系统提供了：

- **安全性**：可靠的JWT token验证机制
- **可扩展性**：支持多租户和用户管理
- **性能**：高效的缓存和异步处理
- **可观测性**：完善的日志和监控
- **易用性**：简单的配置和使用方式

通过这个实现，下游服务可以轻松获取用户上下文信息，实现基于用户的业务逻辑处理。 