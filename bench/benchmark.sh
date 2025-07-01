#!/bin/bash

# 网关系统压力测试脚本
# 基于4核心6GB内存配置

echo "=== 网关系统压力测试 ==="
echo "配置: 4核心6GB内存"
echo "目标QPS: 1000"
echo "=========================="

# 测试参数
GATEWAY_URL="http://localhost:8080"
TEST_ENDPOINTS=(
    "/api/test/qps/api/test1"
    "/api/test/qps/api/test2"
    "/api/test/qps/api/test3"
)

# 测试场景
echo "1. 基础压力测试 (100 QPS)"
ab -n 10000 -c 100 -t 100 "${GATEWAY_URL}${TEST_ENDPOINTS[0]}"

echo "2. 并发压力测试 (500 QPS)"
ab -n 50000 -c 500 -t 100 "${GATEWAY_URL}${TEST_ENDPOINTS[1]}"

echo "3. 高并发压力测试 (1000 QPS)"
ab -n 100000 -c 1000 -t 100 "${GATEWAY_URL}${TEST_ENDPOINTS[2]}"

echo "4. 长时间稳定性测试"
ab -n 1000000 -c 200 -t 600 "${GATEWAY_URL}${TEST_ENDPOINTS[0]}"

echo "=== 测试完成 ===" 