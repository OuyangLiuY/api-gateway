package com.citi.tts.api.gateway.cert;

import java.security.cert.X509Certificate;

public interface CertificateManager {
    X509Certificate getCertificate(String alias);
    void reloadCertificates();
    void revokeCertificate(String alias);
} 