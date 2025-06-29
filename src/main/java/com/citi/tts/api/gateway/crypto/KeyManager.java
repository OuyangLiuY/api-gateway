package com.citi.tts.api.gateway.crypto;

import java.security.Key;

public interface KeyManager {
    Key getKey(String kid, String tenantId);
    void reloadKeys();
    void revokeKey(String kid, String tenantId);
    void revokeKeyByCertificateAlias(String alias);
    // 可扩展：审计、自动同步等
} 