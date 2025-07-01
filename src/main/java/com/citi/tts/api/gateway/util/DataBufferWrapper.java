package com.citi.tts.api.gateway.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * DataBuffer 包装工具类
 * 专门处理大数据量的 DataBuffer 包装问题
 */
@Slf4j
public class DataBufferWrapper {

    /**
     * 默认分块大小：1MB
     */
    private static final int DEFAULT_CHUNK_SIZE = 1024 * 1024;

    /**
     * 最大内存大小：50MB
     */
    private static final int MAX_MEMORY_SIZE = 50 * 1024 * 1024;

    /**
     * 方案1：预分配足够大小的缓冲区（推荐用于中等大小数据）
     */
    public static Mono<DataBuffer> wrapBytesWithPreAllocation(byte[] bytes, ServerWebExchange exchange) {
        if (bytes == null || bytes.length == 0) {
            return Mono.empty();
        }

        DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
        
        try {
            // 预分配足够大小的缓冲区
            DataBuffer buffer = bufferFactory.allocateBuffer(bytes.length);
            buffer.write(bytes);
            
            log.debug("Successfully wrapped {} bytes with pre-allocation", bytes.length);
            return Mono.just(buffer);
        } catch (Exception e) {
            log.error("Failed to wrap bytes with pre-allocation, size: {}", bytes.length, e);
            return Mono.error(e);
        }
    }

    /**
     * 方案2：分块处理大数据（推荐用于大文件）
     */
    public static Mono<DataBuffer> wrapLargeBytesInChunks(byte[] bytes, ServerWebExchange exchange) {
        return wrapLargeBytesInChunks(bytes, exchange, DEFAULT_CHUNK_SIZE);
    }

    /**
     * 方案2：分块处理大数据（自定义分块大小）
     */
    public static Mono<DataBuffer> wrapLargeBytesInChunks(byte[] bytes, ServerWebExchange exchange, int chunkSize) {
        if (bytes == null || bytes.length == 0) {
            return Mono.empty();
        }

        DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
        
        try {
            // 计算需要的缓冲区大小
            int totalSize = bytes.length;
            DataBuffer buffer = bufferFactory.allocateBuffer(totalSize);
            
            // 分块写入
            int offset = 0;
            while (offset < totalSize) {
                int currentChunkSize = Math.min(chunkSize, totalSize - offset);
                buffer.write(bytes, offset, currentChunkSize);
                offset += currentChunkSize;
            }
            
            log.debug("Successfully wrapped {} bytes in chunks, chunk size: {}", totalSize, chunkSize);
            return Mono.just(buffer);
        } catch (Exception e) {
            log.error("Failed to wrap bytes in chunks, size: {}, chunk size: {}", bytes.length, chunkSize, e);
            return Mono.error(e);
        }
    }

    /**
     * 方案3：流式处理超大文件（推荐用于超大文件）
     */
    public static Mono<DataBuffer> wrapLargeBytesStreaming(byte[] bytes, ServerWebExchange exchange) {
        if (bytes == null || bytes.length == 0) {
            return Mono.empty();
        }

        // 如果数据太大，使用流式处理
        if (bytes.length > MAX_MEMORY_SIZE) {
            log.warn("Data size {} exceeds max memory size {}, using streaming approach", 
                    bytes.length, MAX_MEMORY_SIZE);
            return wrapLargeBytesInChunks(bytes, exchange, DEFAULT_CHUNK_SIZE);
        }

        // 否则使用普通包装
        return wrapBytesWithPreAllocation(bytes, exchange);
    }

    /**
     * 方案4：包装字符串（自动处理编码）
     */
    public static Mono<DataBuffer> wrapString(String content, ServerWebExchange exchange) {
        if (content == null || content.isEmpty()) {
            return Mono.empty();
        }

        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return wrapBytesWithPreAllocation(bytes, exchange);
    }

    /**
     * 方案5：包装 JSON 字符串
     */
    public static Mono<DataBuffer> wrapJson(String jsonContent, ServerWebExchange exchange) {
        if (jsonContent == null || jsonContent.isEmpty()) {
            return Mono.empty();
        }

        byte[] bytes = jsonContent.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().allocateBuffer(bytes.length);
        buffer.write(bytes);
        
        // 设置 JSON 内容类型
        exchange.getResponse().getHeaders().set("Content-Type", "application/json;charset=UTF-8");
        
        log.debug("Successfully wrapped JSON content, size: {} bytes", bytes.length);
        return Mono.just(buffer);
    }

    /**
     * 方案6：错误响应包装
     */
    public static Mono<DataBuffer> wrapErrorResponse(String errorMessage, ServerWebExchange exchange) {
        String errorJson = String.format("{\"error\":\"%s\",\"timestamp\":%d,\"status\":\"error\"}", 
            errorMessage, System.currentTimeMillis());
        return wrapJson(errorJson, exchange);
    }

    /**
     * 方案7：安全包装（带大小检查）
     */
    public static Mono<DataBuffer> wrapBytesSafely(byte[] bytes, ServerWebExchange exchange) {
        if (bytes == null || bytes.length == 0) {
            return Mono.empty();
        }

        // 检查数据大小
        if (bytes.length > MAX_MEMORY_SIZE) {
            log.warn("Data size {} exceeds max memory size {}, truncating to {}", 
                    bytes.length, MAX_MEMORY_SIZE, MAX_MEMORY_SIZE);
            
            // 截断数据
            byte[] truncatedBytes = new byte[MAX_MEMORY_SIZE];
            System.arraycopy(bytes, 0, truncatedBytes, 0, MAX_MEMORY_SIZE);
            bytes = truncatedBytes;
        }

        return wrapBytesWithPreAllocation(bytes, exchange);
    }

    /**
     * 方案8：获取 DataBufferFactory
     */
    public static DataBufferFactory getBufferFactory(ServerWebExchange exchange) {
        return exchange.getResponse().bufferFactory();
    }

    /**
     * 方案9：获取 DataBufferFactory（从 ServerHttpResponse）
     */
    public static DataBufferFactory getBufferFactory(ServerHttpResponse response) {
        return response.bufferFactory();
    }

    /**
     * 方案10：释放 DataBuffer 资源
     */
    public static void releaseDataBuffer(DataBuffer buffer) {
        if (buffer != null) {
            DataBufferUtils.release(buffer);
        }
    }
} 