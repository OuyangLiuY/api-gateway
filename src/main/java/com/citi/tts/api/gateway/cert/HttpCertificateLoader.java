package com.citi.tts.api.gateway.cert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * HTTP证书加载器
 * 使用优化的WebClient配置加载证书
 */
@Slf4j
@Component
public class HttpCertificateLoader implements RemoteCertificateLoader {
    
    private final String endpointUrl;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    /**
     * 构造函数 - 使用默认WebClient
     */
    public HttpCertificateLoader(String endpointUrl) {
        this.endpointUrl = endpointUrl;
        this.objectMapper = new ObjectMapper();
        this.webClient = WebClient.builder().build();
    }

    /**
     * 构造函数 - 使用指定的WebClient
     */
    public HttpCertificateLoader(String endpointUrl, WebClient webClient) {
        this.endpointUrl = endpointUrl;
        this.objectMapper = new ObjectMapper();
        this.webClient = webClient;
    }

    /**
     * 使用Spring注入的WebClient构造函数
     */
    @Autowired
    public HttpCertificateLoader(
            String endpointUrl, 
            @Qualifier("normalServiceWebClient") WebClient webClient,
            ObjectMapper objectMapper) {
        this.endpointUrl = endpointUrl;
        this.objectMapper = objectMapper;
        this.webClient = webClient;
    }

    @Override
    public Map<String, X509Certificate> loadAll() {
        Map<String, X509Certificate> certMap = new HashMap<>();
        
        try {
            log.info("Loading certificates from: {}", endpointUrl);
            
            // 使用优化的WebClient进行HTTP调用
            String json = webClient.get()
                    .uri(endpointUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnSuccess(result -> log.debug("Certificate data loaded successfully"))
                    .doOnError(error -> log.error("Failed to load certificate data from: {}", endpointUrl, error))
                    .block();

            if (json != null && !json.isEmpty()) {
                JsonNode root = objectMapper.readTree(json);
                Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
                
                int certCount = 0;
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    String alias = entry.getKey();
                    String certBase64 = entry.getValue().asText();
                    
                    try {
                        byte[] certBytes = java.util.Base64.getDecoder().decode(certBase64);
                        CertificateFactory cf = CertificateFactory.getInstance("X.509");
                        X509Certificate cert = (X509Certificate) cf.generateCertificate(
                            new java.io.ByteArrayInputStream(certBytes));
                        certMap.put(alias, cert);
                        certCount++;
                        
                        log.debug("Certificate loaded: {} ({} bytes)", alias, certBytes.length);
                    } catch (Exception e) {
                        log.error("Failed to parse certificate for alias: {}", alias, e);
                    }
                }
                
                log.info("Successfully loaded {} certificates from: {}", certCount, endpointUrl);
            } else {
                log.warn("No certificate data received from: {}", endpointUrl);
            }
            
        } catch (Exception e) {
            log.error("Failed to load certificates from: {}", endpointUrl, e);
        }
        
        return certMap;
    }

    /**
     * 异步加载证书
     */
    public Mono<Map<String, X509Certificate>> loadAllAsync() {
        return webClient.get()
                .uri(endpointUrl)
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> {
                    Map<String, X509Certificate> certMap = new HashMap<>();
                    
                    if (json != null && !json.isEmpty()) {
                        try {
                            JsonNode root = objectMapper.readTree(json);
                            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
                            
                            while (fields.hasNext()) {
                                Map.Entry<String, JsonNode> entry = fields.next();
                                String alias = entry.getKey();
                                String certBase64 = entry.getValue().asText();
                                
                                byte[] certBytes = java.util.Base64.getDecoder().decode(certBase64);
                                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                                X509Certificate cert = (X509Certificate) cf.generateCertificate(
                                    new java.io.ByteArrayInputStream(certBytes));
                                certMap.put(alias, cert);
                            }
                            
                            log.info("Async loaded {} certificates from: {}", certMap.size(), endpointUrl);
                        } catch (Exception e) {
                            log.error("Failed to parse certificate data", e);
                        }
                    }
                    
                    return certMap;
                })
                .doOnError(error -> log.error("Failed to load certificates asynchronously from: {}", endpointUrl, error));
    }

    /**
     * 获取WebClient信息
     */
    public String getWebClientInfo() {
        return "HttpCertificateLoader using WebClient: " + webClient.getClass().getSimpleName();
    }
} 