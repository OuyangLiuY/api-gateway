package com.citi.tts.api.gateway.cert;

import com.citi.tts.api.gateway.audit.AuditService;
import com.citi.tts.api.gateway.crypto.KeyManager;
import org.springframework.scheduling.annotation.Scheduled;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CertificateManagerImpl implements CertificateManager {
    private final ConcurrentHashMap<String, X509Certificate> certCache = new ConcurrentHashMap<>();
    private final AuditService auditService;
    private final KeyManager keyManager;
    private final RemoteCertificateLoader remoteLoader;

    public CertificateManagerImpl(AuditService auditService, KeyManager keyManager, RemoteCertificateLoader remoteLoader) {
        this.auditService = auditService;
        this.keyManager = keyManager;
        this.remoteLoader = remoteLoader;
        reloadCertificates();
    }

    @Override
    public X509Certificate getCertificate(String alias) {
        return certCache.get(alias);
    }

    @Override
    public void reloadCertificates() {
        Map<String, X509Certificate> newCerts = remoteLoader.loadAll();
        if (!newCerts.isEmpty()) {
            certCache.clear();
            certCache.putAll(newCerts);
            auditService.log("reloadCertificates", "Reloaded all certificates from remote");
        }
    }

    @Override
    public void revokeCertificate(String alias) {
        certCache.remove(alias);
        auditService.log("revokeCertificate", "Revoked certificate: " + alias);
        keyManager.revokeKeyByCertificateAlias(alias);
    }

    // 定时热加载（每5分钟）
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void scheduledReload() {
        reloadCertificates();
    }
} 