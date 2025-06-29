# Redis分布式限流方案详解

## 🔍 问题分析

### **当前本地限流的问题**

#### **1. 单机限制问题**
```java
// 当前实现 - 本地缓存
private final ConcurrentHashMap<String, SlidingWindowRateLimiter> slidingWindowLimiters = new ConcurrentHashMap<>();
```

**问题**：
- ❌ **单机限制**：每个网关实例独立计数，无法实现全局限流
- ❌ **数据不一致**：不同实例间的限流状态不同步
- ❌ **扩展性差**：水平扩展时限流效果被稀释
- ❌ **故障恢复**：实例重启后限流数据丢失

#### **2. 突发流量处理不足**
- ❌ 简单计数器无法处理突发流量
- ❌ 缺少滑动窗口机制
- ❌ 令牌桶配置不完整

## 🏗️ Redis分布式限流解决方案

### **1. 技术架构**

```
客户端请求
    ↓
API网关集群 (3个实例)
    ↓
Redis集群 (主从复制 + 哨兵)
    ↓
后端服务
```

### **2. Redis限流算法实现**

#### **方案A：滑动窗口限流（推荐用于IP/用户/URL限流）**

```lua
-- Redis Lua脚本：滑动窗口限流
local key = KEYS[1]
local now = tonumber(ARGV[1])
local window_start = tonumber(ARGV[2])
local max_requests = tonumber(ARGV[3])
local burst_size = tonumber(ARGV[4])

-- 移除窗口外的数据
redis.call('ZREMRANGEBYSCORE', key, 0, window_start)

-- 获取当前窗口内的请求数
local current_count = redis.call('ZCARD', key)

-- 检查是否超过限制
if current_count >= max_requests then
    -- 检查突发流量配额
    if current_count < max_requests + burst_size then
        -- 允许突发请求
        redis.call('ZADD', key, now, now .. '-' .. math.random())
        redis.call('EXPIRE', key, window_size)
        return 1
    else
        return 0
    end
else
    -- 正常请求
    redis.call('ZADD', key, now, now .. '-' .. math.random())
    redis.call('EXPIRE', key, window_size)
    return 1
end
```

**优势**：
- ✅ 使用Redis Sorted Set实现真正的滑动窗口
- ✅ 支持突发流量处理（burst参数）
- ✅ 原子操作保证一致性
- ✅ 自动过期清理

#### **方案B：令牌桶限流（推荐用于API权重限流）**

```lua
-- Redis Lua脚本：令牌桶限流
local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local refill_interval = tonumber(ARGV[3])
local now = tonumber(ARGV[4])

-- 获取当前令牌桶状态
local bucket_data = redis.call('HMGET', key, 'tokens', 'last_refill_time')
local current_tokens = tonumber(bucket_data[1]) or capacity
local last_refill_time = tonumber(bucket_data[2]) or now

-- 计算需要补充的令牌数
local time_passed = now - last_refill_time
local intervals = math.floor(time_passed / refill_interval)
local refill_tokens = intervals * refill_rate * refill_interval / 1000

-- 更新令牌数
current_tokens = math.min(capacity, current_tokens + refill_tokens)
last_refill_time = last_refill_time + intervals * refill_interval

-- 检查是否有足够的令牌
if current_tokens >= 1 then
    current_tokens = current_tokens - 1
    redis.call('HMSET', key, 'tokens', current_tokens, 'last_refill_time', last_refill_time)
    redis.call('EXPIRE', key, 3600) -- 1小时过期
    return 1
else
    redis.call('HMSET', key, 'tokens', current_tokens, 'last_refill_time', last_refill_time)
    redis.call('EXPIRE', key, 3600)
    return 0
end
```

**优势**：
- ✅ 使用Redis Hash存储令牌桶状态
- ✅ 支持突发流量（桶容量 = burst）
- ✅ 平滑的令牌补充机制
- ✅ 数据持久化

#### **方案C：固定窗口限流（简单场景）**

```lua
-- Redis Lua脚本：固定窗口限流
local key = KEYS[1]
local max_requests = tonumber(ARGV[1])
local window_seconds = tonumber(ARGV[2])

local current_count = redis.call('INCR', key)

if current_count == 1 then
    redis.call('EXPIRE', key, window_seconds)
end

if current_count <= max_requests then
    return 1
else
    return 0
end
```

## 📊 配置参数对比

### **本地限流 vs Redis分布式限流**

| 特性 | 本地限流 | Redis分布式限流 |
|------|----------|-----------------|
| **一致性** | 单机一致 | 全局一致 |
| **扩展性** | 差（实例间独立） | 好（共享状态） |
| **故障恢复** | 数据丢失 | 数据持久化 |
| **性能** | 高（本地内存） | 中等（网络开销） |
| **复杂度** | 低 | 中等 |
| **适用场景** | 单机部署 | 集群部署 |

### **限流算法选择指南**

| 限流类型 | 推荐算法 | 原因 |
|----------|----------|------|
| **IP限流** | 滑动窗口 | 严格限制单IP请求数，防止攻击 |
| **用户限流** | 滑动窗口 | 防止单个用户过度使用 |
| **URL限流** | 滑动窗口 | 防止热点API被刷 |
| **API权重限流** | 令牌桶 | 支持突发流量，平滑处理 |
| **全局限流** | 令牌桶 | 系统整体吞吐量控制 |

## 🔧 实现方案

### **1. 依赖配置**

```gradle
// build.gradle
dependencies {
    // Redis依赖
    implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'
    
    // 连接池
    implementation 'org.apache.commons:commons-pool2'
}
```

### **2. Redis配置**

```yaml
# application.yml
spring:
  redis:
    host: localhost
    port: 6379
    password: 
    database: 0
    timeout: 2000ms
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
        max-wait: -1ms
    cluster:
      nodes:
        - 192.168.1.10:6379
        - 192.168.1.11:6379
        - 192.168.1.12:6379
      max-redirects: 3
```

### **3. 限流器配置**

```yaml
# application.yml
gateway:
  rate-limit:
    enabled: true
    redis:
      enabled: true
      fallback-to-local: true  # Redis故障时降级到本地限流
    ip-limit:
      qps: 30
      burst: 50
      window-size: 1  # 秒
    user-limit:
      qps: 20
      burst: 35
      window-size: 1
    url-limit:
      qps: 40
      burst: 60
      window-size: 1
    api-weights:
      CORE:
        qps: 60
        burst: 80
        refill-interval: 1000  # 毫秒
      NORMAL:
        qps: 25
        burst: 35
        refill-interval: 1000
      NON_CORE:
        qps: 10
        burst: 15
        refill-interval: 1000
      CRYPTO:
        qps: 15
        burst: 20
        refill-interval: 1000
```

## 🚀 性能优化

### **1. Redis优化**

#### **连接池配置**
```yaml
spring:
  redis:
    lettuce:
      pool:
        max-active: 20      # 最大连接数
        max-idle: 10        # 最大空闲连接
        min-idle: 5         # 最小空闲连接
        max-wait: 100ms     # 最大等待时间
```

#### **Redis集群配置**
```yaml
spring:
  redis:
    cluster:
      nodes:
        - 192.168.1.10:6379
        - 192.168.1.11:6379
        - 192.168.1.12:6379
      max-redirects: 3
      refresh:
        adaptive: true
        period: 30s
```

### **2. 限流器优化**

#### **本地缓存降级**
```java
@Component
public class HybridRateLimiter {
    
    @Autowired
    private RedisRateLimiter redisRateLimiter;
    
    @Autowired
    private LocalRateLimiter localRateLimiter;
    
    public Mono<Boolean> rateLimit(String key, RateLimitConfig config) {
        return redisRateLimiter.rateLimit(key, config)
            .onErrorResume(e -> {
                log.warn("Redis rate limit failed, fallback to local: {}", key);
                return localRateLimiter.rateLimit(key, config);
            });
    }
}
```

#### **批量操作优化**
```java
// 批量检查多个限流器
public Mono<Map<String, Boolean>> batchRateLimit(Map<String, RateLimitConfig> configs) {
    return Flux.fromIterable(configs.entrySet())
        .flatMap(entry -> 
            rateLimit(entry.getKey(), entry.getValue())
                .map(result -> Map.entry(entry.getKey(), result))
        )
        .collectMap(Map.Entry::getKey, Map.Entry::getValue);
}
```

## 📈 监控和运维

### **1. 监控指标**

#### **Redis限流器监控**
```java
@Component
public class RateLimitMetrics {
    
    private final MeterRegistry meterRegistry;
    
    public void recordRateLimit(String type, String key, boolean allowed) {
        Counter.builder("rate_limit_requests")
            .tag("type", type)
            .tag("key", key)
            .tag("result", allowed ? "allowed" : "rejected")
            .register(meterRegistry)
            .increment();
    }
    
    public void recordRedisLatency(String operation, long duration) {
        Timer.builder("redis_rate_limit_latency")
            .tag("operation", operation)
            .register(meterRegistry)
            .record(duration, TimeUnit.MILLISECONDS);
    }
}
```

#### **Grafana监控面板**
```json
{
  "dashboard": {
    "title": "API Gateway Rate Limiting",
    "panels": [
      {
        "title": "Rate Limit Requests",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(rate_limit_requests_total[5m])",
            "legendFormat": "{{type}} - {{result}}"
          }
        ]
      },
      {
        "title": "Redis Latency",
        "type": "graph", 
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(redis_rate_limit_latency_seconds_bucket[5m]))",
            "legendFormat": "95th percentile"
          }
        ]
      }
    ]
  }
}
```

### **2. 告警配置**

#### **Prometheus告警规则**
```yaml
groups:
  - name: rate_limit_alerts
    rules:
      - alert: HighRateLimitRejection
        expr: rate(rate_limit_requests_total{result="rejected"}[5m]) > 0.1
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "High rate limit rejection rate"
          description: "Rate limit rejection rate is {{ $value }}"
      
      - alert: RedisRateLimitError
        expr: rate(redis_rate_limit_errors_total[5m]) > 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Redis rate limit errors detected"
          description: "Redis rate limit errors: {{ $value }}"
```

## 🎯 最佳实践

### **1. 部署建议**

#### **Redis集群部署**
```bash
# 主从复制配置
# master.conf
port 6379
bind 0.0.0.0
requirepass yourpassword

# slave.conf  
port 6380
bind 0.0.0.0
requirepass yourpassword
slaveof 192.168.1.10 6379
masterauth yourpassword

# 哨兵配置
# sentinel.conf
port 26379
sentinel monitor mymaster 192.168.1.10 6379 2
sentinel down-after-milliseconds mymaster 5000
sentinel failover-timeout mymaster 10000
```

#### **应用部署**
```yaml
# docker-compose.yml
version: '3.8'
services:
  api-gateway:
    image: api-gateway:latest
    replicas: 3
    environment:
      - SPRING_REDIS_HOST=redis-cluster
      - SPRING_REDIS_PORT=6379
    depends_on:
      - redis-cluster
  
  redis-cluster:
    image: redis:7-alpine
    command: redis-server --cluster-enabled yes
    ports:
      - "6379:6379"
```

### **2. 故障处理**

#### **Redis故障降级策略**
```java
@Component
public class RateLimitFallbackStrategy {
    
    public Mono<Boolean> handleRedisFailure(String key, RateLimitConfig config) {
        // 1. 降级到本地限流
        if (config.isFallbackToLocal()) {
            return localRateLimiter.rateLimit(key, config);
        }
        
        // 2. 直接放行（宽松策略）
        if (config.isAllowOnFailure()) {
            return Mono.just(true);
        }
        
        // 3. 拒绝请求（严格策略）
        return Mono.just(false);
    }
}
```

#### **数据一致性保证**
```java
@Component
public class RateLimitConsistencyChecker {
    
    @Scheduled(fixedRate = 60000) // 每分钟检查一次
    public void checkConsistency() {
        // 检查Redis和本地限流器的一致性
        // 如果发现不一致，进行数据同步
    }
}
```

## 📝 总结

### **Redis分布式限流的优势**

1. **全局一致性**：所有网关实例共享限流状态
2. **高可用性**：Redis集群保证服务可用
3. **数据持久化**：限流数据不丢失
4. **灵活配置**：支持动态调整限流参数
5. **性能优化**：支持批量操作和连接池

### **适用场景**

- ✅ **微服务架构**：多个网关实例需要共享限流状态
- ✅ **高并发场景**：需要精确控制全局QPS
- ✅ **安全防护**：防止分布式攻击和恶意刷接口
- ✅ **业务优先级**：不同API需要不同的限流策略

### **实施建议**

1. **渐进式迁移**：先实现Redis限流，再逐步替换本地限流
2. **监控先行**：部署前先建立完善的监控体系
3. **降级策略**：确保Redis故障时系统仍能正常运行
4. **性能测试**：充分测试Redis限流的性能表现

Redis分布式限流是生产环境中的**必备方案**，能够有效解决集群部署时的限流一致性问题，提升系统的稳定性和可扩展性。 