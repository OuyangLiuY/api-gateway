# 网关系统压力测试指南

## 测试环境

- **硬件配置**: 4核心6GB内存
- **目标QPS**: 1000 QPS
- **测试工具**: Apache Bench (ab)

## 测试场景

### 1. 基础压力测试
```bash
# 100 QPS, 10000请求
ab -n 10000 -c 100 http://localhost:8080/api/test/qps/api/test1
```

### 2. 并发压力测试
```bash
# 500 QPS, 50000请求
ab -n 50000 -c 500 http://localhost:8080/api/test/qps/api/test2
```

### 3. 高并发压力测试
```bash
# 1000 QPS, 100000请求
ab -n 100000 -c 1000 http://localhost:8080/api/test/qps/api/test3
```

### 4. 长时间稳定性测试
```bash
# 200 QPS, 10分钟
ab -n 1000000 -c 200 -t 600 http://localhost:8080/api/test/qps/api/test1
```

## 预期结果

### 4核心6GB配置下的预期性能

| 测试场景 | 目标QPS | 预期响应时间 | 预期错误率 |
|---------|---------|-------------|-----------|
| 基础测试 | 100 | <100ms | <0.1% |
| 并发测试 | 500 | <200ms | <0.5% |
| 高并发测试 | 1000 | <300ms | <1% |
| 稳定性测试 | 200 | <150ms | <0.2% |

## 监控指标

- **响应时间**: P50, P95, P99
- **错误率**: HTTP 5xx错误
- **QPS**: 实际处理的请求数
- **资源使用**: CPU, 内存, 网络

## 运行测试

```bash
# 运行完整测试套件
./bench/benchmark.sh

# 运行单个测试
ab -n 10000 -c 100 http://localhost:8080/api/test/qps/api/test1
```

## 结果分析

测试完成后，根据实际结果与预期结果对比，调整系统配置以优化性能。 