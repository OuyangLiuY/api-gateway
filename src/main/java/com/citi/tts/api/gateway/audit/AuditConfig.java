package com.citi.tts.api.gateway.audit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 审计日志配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "audit")
public class AuditConfig {

    /**
     * 异步配置
     */
    private Async async = new Async();

    /**
     * 存储配置
     */
    private Storage storage = new Storage();

    /**
     * 异步配置
     */
    @Data
    public static class Async {
        /**
         * 是否启用异步模式
         */
        private boolean enabled = true;

        /**
         * 批次大小
         */
        private int batchSize = 100;

        /**
         * 刷新间隔（毫秒）
         */
        private long flushInterval = 5000;

        /**
         * 队列大小
         */
        private int queueSize = 10000;

        /**
         * 队列超时时间（毫秒）
         */
        private long queueTimeout = 100;

        /**
         * 工作线程数
         */
        private int workerThreads = 1;
    }

    /**
     * 存储配置
     */
    @Data
    public static class Storage {
        /**
         * 存储类型：file, database, elasticsearch, kafka
         */
        private String type = "file";

        /**
         * 文件存储配置
         */
        private File file = new File();

        /**
         * 数据库存储配置
         */
        private Database database = new Database();

        /**
         * Elasticsearch存储配置
         */
        private Elasticsearch elasticsearch = new Elasticsearch();

        /**
         * Kafka存储配置
         */
        private Kafka kafka = new Kafka();
    }

    /**
     * 文件存储配置
     */
    @Data
    public static class File {
        /**
         * 日志文件路径
         */
        private String path = "logs/audit.log";

        /**
         * 是否启用文件轮转
         */
        private boolean rotationEnabled = true;

        /**
         * 单个文件最大大小（MB）
         */
        private long maxFileSize = 100;

        /**
         * 保留文件数量
         */
        private int maxFiles = 10;
    }

    /**
     * 数据库存储配置
     */
    @Data
    public static class Database {
        /**
         * 数据源名称
         */
        private String dataSource = "auditDataSource";

        /**
         * 表名
         */
        private String tableName = "audit_logs";

        /**
         * 批量插入大小
         */
        private int batchSize = 1000;
    }

    /**
     * Elasticsearch存储配置
     */
    @Data
    public static class Elasticsearch {
        /**
         * 索引名称
         */
        private String indexName = "audit-logs";

        /**
         * 索引前缀
         */
        private String indexPrefix = "audit";

        /**
         * 索引后缀格式
         */
        private String indexSuffix = "yyyy.MM.dd";

        /**
         * 分片数
         */
        private int shards = 1;

        /**
         * 副本数
         */
        private int replicas = 0;
    }

    /**
     * Kafka存储配置
     */
    @Data
    public static class Kafka {
        /**
         * 主题名称
         */
        private String topic = "audit-logs";

        /**
         * 分区数
         */
        private int partitions = 3;

        /**
         * 副本数
         */
        private int replicas = 1;

        /**
         * 确认机制
         */
        private String acks = "1";
    }
} 