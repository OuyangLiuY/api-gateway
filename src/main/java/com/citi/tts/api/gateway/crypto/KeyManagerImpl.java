package com.citi.tts.api.gateway.crypto;

import com.citi.tts.api.gateway.audit.AuditService;
import org.springframework.scheduling.annotation.Scheduled;
import java.security.Key;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KeyManagerImpl implements KeyManager {
    private final ConcurrentHashMap<String, Key> keyCache = new ConcurrentHashMap<>();
    private final AuditService auditService;
    private final RemoteKeyLoader remoteLoader;

    public KeyManagerImpl(AuditService auditService, RemoteKeyLoader remoteLoader) {
        this.auditService = auditService;
        this.remoteLoader = remoteLoader;
        reloadKeys();
    }

    @Override
    public Key getKey(String kid, String tenantId) {
        return keyCache.get(kid + ":" + tenantId);
    }

    @Override
    public void reloadKeys() {
        Map<String, Key> newKeys = remoteLoader.loadAll();
        if (!newKeys.isEmpty()) {
            keyCache.clear();
            keyCache.putAll(newKeys);
            auditService.log("reloadKeys", "Reloaded all keys from remote");
        }
    }

    @Override
    public void revokeKey(String kid, String tenantId) {
        keyCache.remove(kid + ":" + tenantId);
        auditService.log("revokeKey", "Revoked key: " + kid + ", tenant: " + tenantId);
    }

    @Override
    public void revokeKeyByCertificateAlias(String alias) {
        // TODO: 根据证书alias找到相关kid并吊销
        auditService.log("revokeKeyByCertificateAlias", "Revoked keys by certificate alias: " + alias);
    }

    // 定时热加载（每5分钟）
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void scheduledReload() {
        reloadKeys();
    }
} 