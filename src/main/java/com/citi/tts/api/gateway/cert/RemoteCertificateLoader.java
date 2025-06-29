package com.citi.tts.api.gateway.cert;

import java.security.cert.X509Certificate;
import java.util.Map;

public interface RemoteCertificateLoader {
    Map<String, X509Certificate> loadAll();
} 