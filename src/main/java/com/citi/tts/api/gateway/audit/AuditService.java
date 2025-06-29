package com.citi.tts.api.gateway.audit;

public interface AuditService {
    void log(String action, String message);
} 