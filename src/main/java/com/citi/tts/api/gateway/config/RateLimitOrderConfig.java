package com.citi.tts.api.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 限流顺序配置
 * 支持不同的限流检查顺序，适应不同的业务场景
 */
@Data
@Component
@ConfigurationProperties(prefix = "rate.limit.order")
public class RateLimitOrderConfig {

    /**
     * 限流检查顺序
     * 可选值：IP, USER, URL, API_WEIGHT
     * 默认顺序：IP -> USER -> URL -> API_WEIGHT
     */
    private String sequence = "IP,USER,URL,API_WEIGHT";

    /**
     * 预定义的限流顺序策略
     */
    public enum RateLimitStrategy {
        /**
         * 性能优先策略：从严格到宽松，快速失败
         * 顺序：IP -> USER -> URL -> API_WEIGHT
         * 适用：高并发场景，优先保护系统性能
         */
        PERFORMANCE_FIRST("IP,USER,URL,API_WEIGHT"),

        /**
         * 业务优先策略：从宽松到严格，保护业务逻辑
         * 顺序：API_WEIGHT -> URL -> USER -> IP
         * 适用：VIP用户保护，API公平性优先
         */
        BUSINESS_FIRST("API_WEIGHT,URL,USER,IP"),

        /**
         * 安全优先策略：IP和用户优先，防止恶意攻击
         * 顺序：IP -> USER -> API_WEIGHT -> URL
         * 适用：安全敏感场景，防止IP和用户滥用
         */
        SECURITY_FIRST("IP,USER,API_WEIGHT,URL"),

        /**
         * API优先策略：API权重和URL优先，保护API资源
         * 顺序：API_WEIGHT -> URL -> IP -> USER
         * 适用：API资源保护，防止热门API被滥用
         */
        API_FIRST("API_WEIGHT,URL,IP,USER"),

        /**
         * 用户优先策略：用户和API权重优先，保护用户体验
         * 顺序：USER -> API_WEIGHT -> URL -> IP
         * 适用：用户体验优先，保护VIP用户
         */
        USER_FIRST("USER,API_WEIGHT,URL,IP");

        private final String sequence;

        RateLimitStrategy(String sequence) {
            this.sequence = sequence;
        }

        public String getSequence() {
            return sequence;
        }
    }

    /**
     * 获取限流检查顺序列表
     */
    public List<String> getRateLimitSequence() {
        return Arrays.asList(sequence.split(","));
    }

    /**
     * 设置预定义策略
     */
    public void setStrategy(RateLimitStrategy strategy) {
        this.sequence = strategy.getSequence();
    }

    /**
     * 验证限流顺序是否有效
     */
    public boolean isValidSequence() {
        List<String> validTypes = Arrays.asList("IP", "USER", "URL", "API_WEIGHT");
        List<String> sequenceList = getRateLimitSequence();
        
        return sequenceList.stream()
                .map(String::trim)
                .map(String::toUpperCase)
                .allMatch(validTypes::contains);
    }

    /**
     * 获取策略说明
     */
    public String getStrategyDescription() {
        if (sequence.equals(RateLimitStrategy.PERFORMANCE_FIRST.getSequence())) {
            return "性能优先：快速失败，保护系统性能";
        } else if (sequence.equals(RateLimitStrategy.BUSINESS_FIRST.getSequence())) {
            return "业务优先：保护VIP用户，API公平性优先";
        } else if (sequence.equals(RateLimitStrategy.SECURITY_FIRST.getSequence())) {
            return "安全优先：防止IP和用户滥用";
        } else if (sequence.equals(RateLimitStrategy.API_FIRST.getSequence())) {
            return "API优先：保护API资源，防止热门API被滥用";
        } else if (sequence.equals(RateLimitStrategy.USER_FIRST.getSequence())) {
            return "用户优先：保护用户体验，VIP用户优先";
        } else {
            return "自定义顺序：" + sequence;
        }
    }
} 