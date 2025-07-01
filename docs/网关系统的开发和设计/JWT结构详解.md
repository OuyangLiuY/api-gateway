# JWT结构详解

## 概述

JWT（JSON Web Token）是一种开放标准（RFC 7519），用于在各方之间安全地传输信息。JWT由三部分组成，用点（.）分隔：

```
Header.Payload.Signature
```

## 1. Header（头部）

### 1.1 标准头部字段

Header包含关于Token的元数据，通常包含两个字段：

```json
{
  "alg": "HS256",     // 签名算法
  "typ": "JWT"        // Token类型
}
```

### 1.2 常用算法类型

| 算法 | 类型 | 说明 | 用途 |
|------|------|------|------|
| `HS256` | 对称加密 | HMAC SHA256 | 使用密钥签名和验证 |
| `HS384` | 对称加密 | HMAC SHA384 | 使用密钥签名和验证 |
| `HS512` | 对称加密 | HMAC SHA512 | 使用密钥签名和验证 |
| `RS256` | 非对称加密 | RSA SHA256 | 私钥签名，公钥验证 |
| `RS384` | 非对称加密 | RSA SHA384 | 私钥签名，公钥验证 |
| `RS512` | 非对称加密 | RSA SHA512 | 私钥签名，公钥验证 |
| `ES256` | 椭圆曲线 | ECDSA SHA256 | 椭圆曲线数字签名 |
| `ES384` | 椭圆曲线 | ECDSA SHA384 | 椭圆曲线数字签名 |
| `ES512` | 椭圆曲线 | ECDSA SHA512 | 椭圆曲线数字签名 |
| `none` | 无签名 | 无签名 | 仅用于测试，不安全 |

### 1.3 扩展头部字段

```json
{
  "alg": "HS256",
  "typ": "JWT",
  "kid": "key-123",                    // 密钥ID
  "x5u": "https://example.com/cert.pem", // X.509证书URL
  "x5c": "MIIDXTCCAkWgAwIBAgIJAKoK...", // X.509证书链
  "x5t": "dG9rZW4taWQ=",              // X.509证书指纹
  "cty": "application/json",          // 内容类型
  "zip": "DEF",                       // 压缩算法
  "x-version": "1.0",                 // 自定义版本
  "x-issuer": "citi-gateway"          // 自定义发行者
}
```

### 1.4 算法选择建议

#### 对称加密（HMAC）
- **适用场景**：单点认证、内部系统
- **优点**：计算速度快、实现简单
- **缺点**：密钥分发困难、不适合分布式系统
- **推荐**：HS256（安全性和性能平衡）

#### 非对称加密（RSA）
- **适用场景**：分布式系统、多服务架构
- **优点**：密钥分发容易、安全性高
- **缺点**：计算速度较慢、密钥管理复杂
- **推荐**：RS256（广泛支持）

#### 椭圆曲线（ECDSA）
- **适用场景**：资源受限环境、移动应用
- **优点**：密钥长度短、计算速度快
- **缺点**：实现复杂、兼容性较差
- **推荐**：ES256（现代标准）

## 2. Payload（载荷/主体）

### 2.1 标准Claims（注册声明）

这些是JWT标准中预定义的字段：

```json
{
  // 发行者 - 谁创建了这个Token
  "iss": "https://auth.example.com",
  
  // 主题 - Token的主体（通常是用户ID）
  "sub": "user123",
  
  // 受众 - Token的预期接收者
  "aud": ["api.example.com", "web.example.com"],
  
  // 过期时间 - Token何时过期（Unix时间戳）
  "exp": 1735689600,
  
  // 生效时间 - Token何时开始生效（Unix时间戳）
  "nbf": 1735686000,
  
  // 签发时间 - Token何时被创建（Unix时间戳）
  "iat": 1735686000,
  
  // JWT ID - Token的唯一标识符
  "jti": "jwt-123456789"
}
```

### 2.2 公共Claims（公共声明）

这些是常用的但非标准的字段：

```json
{
  // 用户信息
  "name": "John Doe",
  "given_name": "John",
  "family_name": "Doe",
  "middle_name": "Michael",
  "nickname": "Johnny",
  "preferred_username": "johndoe",
  "profile": "https://example.com/profile",
  "picture": "https://example.com/avatar.jpg",
  "website": "https://example.com",
  "email": "john.doe@example.com",
  "email_verified": true,
  "gender": "male",
  "birthdate": "1990-01-01",
  "zoneinfo": "America/New_York",
  "locale": "en-US",
  "phone_number": "+1-555-123-4567",
  "phone_number_verified": true,
  "address": {
    "formatted": "123 Main St, Anytown, ST 12345",
    "street_address": "123 Main St",
    "locality": "Anytown",
    "region": "ST",
    "postal_code": "12345",
    "country": "US"
  },
  "updated_at": 1735686000
}
```

### 2.3 私有Claims（私有声明）

这些是应用特定的自定义字段：

```json
{
  // 业务相关字段
  "userId": "user-123",
  "userType": "premium",
  "subscriptionLevel": "gold",
  "accountStatus": "active",
  "lastLoginTime": 1735686000,
  
  // 权限信息
  "role": "admin",
  "permissions": [
    "user:read",
    "user:write", 
    "user:delete",
    "admin:manage",
    "system:config"
  ],
  "scopes": ["read", "write", "admin"],
  
  // 组织信息
  "tenantId": "tenant-123",
  "orgId": "org-456",
  "department": "IT部门",
  "team": "开发团队",
  "manager": "manager-789",
  
  // 会话信息
  "sessionId": "session-789",
  "loginTime": 1735686000,
  "lastActivity": 1735689600,
  "ipAddress": "192.168.1.100",
  "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
  "deviceId": "device-456",
  
  // 地理位置
  "location": {
    "country": "CN",
    "region": "Beijing",
    "city": "Beijing",
    "timezone": "Asia/Shanghai",
    "latitude": 39.9042,
    "longitude": 116.4074
  },
  
  // 设备信息
  "device": {
    "deviceId": "device-456",
    "deviceType": "mobile",
    "os": "iOS 15.0",
    "browser": "Safari",
    "appVersion": "1.2.3",
    "screenResolution": "1920x1080"
  },
  
  // 安全信息
  "securityLevel": "high",
  "mfaEnabled": true,
  "lastPasswordChange": 1735686000,
  "failedLoginAttempts": 0,
  "accountLocked": false,
  
  // 偏好设置
  "preferences": {
    "theme": "dark",
    "language": "zh-CN",
    "timezone": "Asia/Shanghai",
    "currency": "CNY",
    "dateFormat": "YYYY-MM-DD",
    "timeFormat": "24h",
    "notifications": {
      "email": true,
      "sms": false,
      "push": true,
      "inApp": true
    }
  },
  
  // 审计信息
  "audit": {
    "createdBy": "admin",
    "createdAt": 1735686000,
    "modifiedBy": "user123",
    "modifiedAt": 1735689600,
    "version": "1.0",
    "source": "web",
    "reason": "login"
  },
  
  // 业务数据
  "businessData": {
    "customerId": "cust-123",
    "accountNumber": "ACC-456",
    "balance": 10000.00,
    "currency": "CNY",
    "riskLevel": "low",
    "kycStatus": "verified"
  },
  
  // 营销信息
  "marketing": {
    "campaignId": "camp-789",
    "source": "google",
    "medium": "cpc",
    "term": "api gateway",
    "content": "banner-1"
  }
}
```

### 2.4 实际项目中的完整示例

```json
{
  // 标准Claims
  "iss": "citi-gateway",
  "sub": "user123",
  "aud": "api-gateway",
  "exp": 1735689600,
  "iat": 1735686000,
  "jti": "jwt-123456789",
  "nbf": 1735686000,
  
  // 用户基本信息
  "name": "张三",
  "given_name": "三",
  "family_name": "张",
  "email": "zhangsan@citi.com",
  "email_verified": true,
  "phone_number": "+86-138-0013-8000",
  "phone_number_verified": true,
  "picture": "https://example.com/avatar.jpg",
  "locale": "zh-CN",
  "zoneinfo": "Asia/Shanghai",
  
  // 权限信息
  "role": "admin",
  "permissions": [
    "user:read",
    "user:write", 
    "user:delete",
    "admin:manage",
    "system:config",
    "audit:view",
    "report:generate"
  ],
  "scopes": ["read", "write", "admin", "audit"],
  
  // 组织信息
  "tenantId": "tenant-123",
  "orgId": "org-456",
  "department": "IT部门",
  "team": "网关开发团队",
  "manager": "manager-789",
  "employeeId": "EMP-123",
  "position": "高级开发工程师",
  
  // 会话信息
  "sessionId": "session-789",
  "loginTime": 1735686000,
  "lastActivity": 1735689600,
  "ipAddress": "192.168.1.100",
  "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
  "deviceId": "device-456",
  "loginMethod": "password",
  "loginLocation": "office",
  
  // 业务信息
  "userType": "premium",
  "subscriptionLevel": "gold",
  "accountStatus": "active",
  "customerId": "cust-123",
  "accountNumber": "ACC-456",
  "balance": 10000.00,
  "currency": "CNY",
  "riskLevel": "low",
  "kycStatus": "verified",
  
  // 安全信息
  "securityLevel": "high",
  "mfaEnabled": true,
  "lastPasswordChange": 1735686000,
  "failedLoginAttempts": 0,
  "accountLocked": false,
  "passwordExpiry": 1735689600,
  "lastSecurityReview": 1735686000,
  
  // 偏好设置
  "preferences": {
    "theme": "dark",
    "language": "zh-CN",
    "timezone": "Asia/Shanghai",
    "currency": "CNY",
    "dateFormat": "YYYY-MM-DD",
    "timeFormat": "24h",
    "notifications": {
      "email": true,
      "sms": false,
      "push": true,
      "inApp": true
    },
    "privacy": {
      "shareData": false,
      "marketingEmails": false,
      "analytics": true
    }
  },
  
  // 地理位置信息
  "location": {
    "country": "CN",
    "region": "Beijing",
    "city": "Beijing",
    "timezone": "Asia/Shanghai",
    "latitude": 39.9042,
    "longitude": 116.4074,
    "isp": "China Telecom",
    "connectionType": "wifi"
  },
  
  // 设备信息
  "device": {
    "deviceId": "device-456",
    "deviceType": "desktop",
    "os": "Windows 10",
    "browser": "Chrome",
    "appVersion": "1.2.3",
    "screenResolution": "1920x1080",
    "deviceModel": "ThinkPad X1",
    "deviceManufacturer": "Lenovo"
  },
  
  // 审计信息
  "audit": {
    "createdBy": "system",
    "createdAt": 1735686000,
    "modifiedBy": "user123",
    "modifiedAt": 1735689600,
    "version": "1.0",
    "source": "web",
    "reason": "login",
    "sessionCount": 5,
    "lastLogout": 1735686000
  },
  
  // 业务数据
  "businessData": {
    "customerId": "cust-123",
    "accountNumber": "ACC-456",
    "balance": 10000.00,
    "currency": "CNY",
    "riskLevel": "low",
    "kycStatus": "verified",
    "accountType": "premium",
    "creditLimit": 50000.00,
    "lastTransaction": 1735686000
  },
  
  // 营销信息
  "marketing": {
    "campaignId": "camp-789",
    "source": "google",
    "medium": "cpc",
    "term": "api gateway",
    "content": "banner-1",
    "utmSource": "google",
    "utmMedium": "cpc",
    "utmCampaign": "gateway-2024"
  }
}
```

## 3. Signature（签名）

### 3.1 签名的作用

签名用于验证JWT的完整性和真实性，防止数据被篡改。签名确保：

1. **完整性**：数据没有被修改
2. **真实性**：数据来自可信的源
3. **不可否认性**：发送者无法否认发送过数据

### 3.2 签名算法示例

#### HMAC SHA256（对称加密）
```java
// 使用密钥签名
String data = base64UrlEncode(header) + "." + base64UrlEncode(payload);
String signature = HMACSHA256(data, secret);
```

#### RSA SHA256（非对称加密）
```java
// 使用私钥签名
String data = base64UrlEncode(header) + "." + base64UrlEncode(payload);
String signature = RSASHA256(data, privateKey);
```

### 3.3 签名验证过程

1. **提取数据**：从JWT中提取header和payload部分
2. **重新计算签名**：使用相同的算法和密钥重新计算签名
3. **比较签名**：将计算的签名与JWT中的签名进行比较
4. **验证结果**：如果签名匹配，则验证成功

### 3.4 完整的JWT示例

```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.
eyJzdWIiOiJ1c2VyMTIzIiwiYXVkIjoiYXBpIiwiZXhwIjoxNzM1Njg5NjAwLCJpYXQiOjE3MzU2ODYwMDAsImlzcyI6ImNpdGkiLCJ0ZW5hbnRJZCI6InRlbmFudC0xIiwicm9sZSI6InVzZXIifQ.
SIGNATURE_HERE
```

## 4. 最佳实践

### 4.1 Header最佳实践

1. **选择合适的算法**：
   - 单点认证：使用HS256
   - 分布式系统：使用RS256
   - 资源受限环境：使用ES256

2. **包含必要信息**：
   - 总是包含`alg`和`typ`
   - 使用`kid`标识密钥
   - 添加版本信息

3. **避免敏感信息**：
   - Header是明文，不要包含敏感数据
   - 使用`kid`引用密钥，而不是直接包含密钥

### 4.2 Payload最佳实践

1. **合理使用标准Claims**：
   - 总是设置`exp`（过期时间）
   - 使用`iss`标识发行者
   - 使用`aud`限制受众
   - 使用`jti`确保唯一性

2. **控制Payload大小**：
   - 避免存储大量数据
   - 使用引用而不是完整数据
   - 考虑压缩选项

3. **数据分类**：
   - 敏感数据不要放在JWT中
   - 使用私有Claims存储业务数据
   - 遵循最小权限原则

### 4.3 安全考虑

1. **密钥管理**：
   - 使用强密钥
   - 定期轮换密钥
   - 安全存储密钥

2. **Token生命周期**：
   - 设置合理的过期时间
   - 实现Token撤销机制
   - 监控Token使用情况

3. **传输安全**：
   - 使用HTTPS传输
   - 设置适当的Cookie属性
   - 实现CSRF保护

## 5. 在项目中的应用

### 5.1 生成JWT Token

```java
@Service
public class JwtService {
    
    public String generateToken(User user) {
        return Jwts.builder()
                .setSubject(user.getId())
                .setIssuer("citi-gateway")
                .setAudience("api-gateway")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .setId("jwt-" + System.currentTimeMillis())
                .claim("name", user.getName())
                .claim("email", user.getEmail())
                .claim("role", user.getRole())
                .claim("tenantId", user.getTenantId())
                .claim("permissions", user.getPermissions())
                .signWith(getSigningKey())
                .compact();
    }
}
```

### 5.2 验证JWT Token

```java
@Component
public class JwtAuthenticationFilter implements GlobalFilter {
    
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String token = extractToken(exchange);
        
        if (token != null && validateToken(token)) {
            Claims claims = parseToken(token);
            
            // 添加用户信息到请求头
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-ID", claims.getSubject())
                    .header("X-Tenant-ID", claims.get("tenantId", String.class))
                    .header("X-Session", generateSessionId(claims))
                    .header("X-Role", claims.get("role", String.class))
                    .build();
            
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        }
        
        return chain.filter(exchange);
    }
}
```

### 5.3 监控和审计

```java
@Component
public class JwtAuditService {
    
    public void auditTokenUsage(String token, String endpoint, String ipAddress) {
        Claims claims = parseToken(token);
        
        AuditEvent event = AuditEvent.builder()
                .userId(claims.getSubject())
                .tenantId(claims.get("tenantId", String.class))
                .action("token_usage")
                .resource(endpoint)
                .ipAddress(ipAddress)
                .timestamp(System.currentTimeMillis())
                .sessionId(claims.get("sessionId", String.class))
                .build();
        
        auditService.record(event);
    }
}
```

## 6. 总结

JWT的三个部分各有其特定的作用：

- **Header**：定义Token的类型和签名算法
- **Payload**：包含实际的用户数据和权限信息
- **Signature**：确保数据的完整性和真实性

合理使用这三个部分，可以构建安全、高效的身份认证和授权系统。在实际项目中，需要根据具体需求选择合适的算法、控制Payload大小、实施安全措施，并建立完善的监控和审计机制。 