# QPS统计实现方案

## 📊 概述

基于当前API网关系统，实现了完整的QPS（Queries Per Second）统计功能，支持多维度实时监控和数据分析。

## 🏗️ 架构设计

### 核心组件

1. **QPSMetrics** - QPS统计核心组件
2. **QPSMetricsFilter** - QPS统计过滤器
3. **QPSMonitorController** - QPS监控REST API
4. **QPSCleanupScheduler** - 定时清理任务
5. **QPSTestController** - QPS测试接口

### 统计维度

- **全局QPS** - 系统整体请求量
- **API路径QPS** - 按API路径统计
- **IP QPS** - 按客户端IP统计
- **用户QPS** - 按用户ID统计
- **优先级QPS** - 按API优先级统计

## 🔧 实现细节

### 1. QPSMetrics核心组件

```java
@Component
public class QPSMetrics {
    // 全局QPS统计
    private final AtomicReference<QPSWindow> globalQPS;
    
    // 多维度QPS统计
    private final ConcurrentHashMap<String, AtomicReference<QPSWindow>> apiQPS;
    private final ConcurrentHashMap<String, AtomicReference<QPSWindow>> ipQPS;
    private final ConcurrentHashMap<String, AtomicReference<QPSWindow>> userQPS;
    private final ConcurrentHashMap<String, AtomicReference<QPSWindow>> priorityQPS;
}
```

**特性：**
- 使用滑动窗口算法，1秒窗口期
- 线程安全的并发数据结构
- 自动清理过期数据
- 支持实时QPS计算

### 2. QPSMetricsFilter过滤器

**功能：**
- 自动提取请求信息（路径、IP、用户ID、优先级）
- 记录QPS统计数据
- 支持真实IP获取（X-Forwarded-For、X-Real-IP）
- 智能优先级识别

**优先级识别规则：**
```java
// 根据路径判断优先级
if (path.startsWith("/api/core/")) return "core";
else if (path.startsWith("/api/normal/")) return "normal";
else if (path.startsWith("/api/non-core/")) return "non-core";
else if (path.startsWith("/api/payment/")) return "payment";
else if (path.startsWith("/api/user/")) return "user";
else if (path.startsWith("/api/admin/")) return "admin";
```

### 3. QPS监控API接口

#### 全局QPS
```http
GET /api/monitor/qps/global
```

#### API路径QPS
```http
GET /api/monitor/qps/api
GET /api/monitor/qps/api/{path}
```

#### IP QPS
```http
GET /api/monitor/qps/ip
GET /api/monitor/qps/ip/{ip}
```

#### 用户QPS
```http
GET /api/monitor/qps/user
GET /api/monitor/qps/user/{userId}
```

#### 优先级QPS
```http
GET /api/monitor/qps/priority
GET /api/monitor/qps/priority/{priority}
```

#### 完整统计
```http
GET /api/monitor/qps/all
GET /api/monitor/qps/summary
GET /api/monitor/qps/health
```

### 4. 定时清理任务

**清理策略：**
- 每分钟清理过期数据（1分钟过期）
- 每5分钟输出统计摘要
- 自动内存管理

## 📈 监控指标

### 实时指标
- **全局QPS** - 当前系统总请求量
- **API维度QPS** - 各API路径的请求量
- **IP维度QPS** - 各客户端IP的请求量
- **用户维度QPS** - 各用户的请求量
- **优先级维度QPS** - 各优先级的请求量

### 统计摘要
- 活跃API数量
- 活跃IP数量
- 活跃用户数量
- 活跃优先级数量
- 各维度总QPS

## 🧪 测试功能

### QPSTestController测试接口

#### 基础测试
```http
GET /api/test/qps/api/{path}
GET /api/test/qps/user/{userId}
GET /api/test/qps/priority/{priority}
```

#### 批量测试
```http
POST /api/test/qps/batch
{
    "count": 100,
    "type": "api"
}
```

#### 压力测试
```http
POST /api/test/qps/stress
{
    "duration": 60,
    "qps": 200
}
```

## 🔄 数据流程

```
请求 → QPSMetricsFilter → QPSMetrics → 滑动窗口统计
  ↓
QPSMonitorController ← 实时查询 ← 统计数据
  ↓
定时清理任务 → 内存管理
```

## 📊 性能特性

### 高并发支持
- 使用`ConcurrentHashMap`和`AtomicReference`
- 无锁设计，最小化性能影响
- 滑动窗口算法，O(1)时间复杂度

### 内存管理
- 自动清理过期数据
- 1分钟数据过期策略
- 防止内存泄漏

### 实时性
- 1秒滑动窗口
- 实时QPS计算
- 毫秒级响应

## 🚀 使用示例

### 1. 启动应用
```bash
./gradlew bootRun
```

### 2. 查看全局QPS
```bash
curl http://localhost:8080/api/monitor/qps/global
```

### 3. 查看API QPS
```bash
curl http://localhost:8080/api/monitor/qps/api
```

### 4. 压力测试
```bash
curl -X POST http://localhost:8080/api/test/qps/stress \
  -H "Content-Type: application/json" \
  -d '{"duration": 30, "qps": 100}'
```

### 5. 查看统计摘要
```bash
curl http://localhost:8080/api/monitor/qps/summary
```

## 📝 日志输出

### 实时日志
```
INFO  - Global QPS: 150
INFO  - API QPS: {/api/payment/transfer=45, /api/user/profile=30, ...}
INFO  - IP QPS: {192.168.1.100=25, 192.168.1.101=20, ...}
```

### 定时摘要
```
INFO  - === QPS Summary Report ===
INFO  - Global QPS: 150
INFO  - Active APIs: 15
INFO  - Active IPs: 25
INFO  - Active Users: 50
INFO  - Top API - /api/payment/transfer: 45 QPS
INFO  - Top IP - 192.168.1.100: 25 QPS
INFO  - === End QPS Summary Report ===
```

## 🔧 配置说明

### 应用配置
```yaml
# 启用定时任务
@EnableScheduling

# 过滤器顺序
QPSMetricsFilter: HIGHEST_PRECEDENCE + 100
```

### 性能调优
- 滑动窗口大小：1秒
- 数据过期时间：1分钟
- 清理频率：每分钟
- 摘要频率：每5分钟

## 🎯 扩展功能

### 可扩展维度
- 按服务统计QPS
- 按地域统计QPS
- 按设备类型统计QPS
- 按请求方法统计QPS

### 可扩展算法
- 支持多种时间窗口
- 支持加权QPS计算
- 支持QPS趋势分析
- 支持QPS预测

## 📋 总结

QPS统计系统提供了：

1. **多维度统计** - 支持API、IP、用户、优先级等多个维度
2. **实时监控** - 1秒滑动窗口，实时QPS计算
3. **REST API** - 完整的监控接口
4. **自动管理** - 定时清理，内存管理
5. **测试支持** - 内置测试工具
6. **高性能** - 无锁设计，最小性能影响

 