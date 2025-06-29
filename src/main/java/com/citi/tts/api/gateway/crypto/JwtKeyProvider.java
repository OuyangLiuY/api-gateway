package com.citi.tts.api.gateway.crypto;

import java.security.PublicKey;

public interface JwtKeyProvider {
    PublicKey getPublicKey(String kid, String tenantId);
    void syncKeys();
} 