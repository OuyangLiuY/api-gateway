//package com.citi.tts.api.gateway.oauth;
//
//import com.citi.tts.api.gateway.crypto.JwtKeyProvider;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import java.net.URL;
//import java.security.PublicKey;
//import java.security.interfaces.RSAPublicKey;
//import java.util.Map;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.Executors;
//import java.util.concurrent.ScheduledExecutorService;
//import java.util.concurrent.TimeUnit;
//import com.nimbusds.jose.jwk.JWK;
//import com.nimbusds.jose.jwk.JWKSet;
//import com.nimbusds.jose.jwk.RSAKey;
//
//public class OAuth2PublicKeySyncService implements JwtKeyProvider {
//    private static final Logger logger = LoggerFactory.getLogger(OAuth2PublicKeySyncService.class);
//    private final Map<String, Map<String, PublicKey>> jwkCache = new ConcurrentHashMap<>();
//    private final Map<String, String> jwksEndpoints = new ConcurrentHashMap<>();
//    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
//
//    public OAuth2PublicKeySyncService(Map<String, String> jwksEndpoints) {
//        this.jwksEndpoints.putAll(jwksEndpoints);
//        scheduler.scheduleAtFixedRate(this::syncKeys, 0, 5, TimeUnit.MINUTES);
//    }
//
//    @Override
//    public PublicKey getPublicKey(String kid, String tenantId) {
//        Map<String, PublicKey> tenantKeys = jwkCache.get(tenantId);
//        if (tenantKeys != null) {
//            return tenantKeys.get(kid);
//        }
//        return null;
//    }
//
//    @Override
//    public void syncKeys() {
//        for (Map.Entry<String, String> entry : jwksEndpoints.entrySet()) {
//            String tenantId = entry.getKey();
//            String endpoint = entry.getValue();
//            try {
//                JWKSet jwkSet = JWKSet.load(new URL(endpoint));
//                Map<String, PublicKey> keyMap = new ConcurrentHashMap<>();
//                for (JWK jwk : jwkSet.getKeys()) {
//                    if (jwk instanceof RSAKey) {
//                        RSAKey rsaKey = (RSAKey) jwk;
//                        RSAPublicKey publicKey = rsaKey.toRSAPublicKey();
//                        keyMap.put(jwk.getKeyID(), publicKey);
//                    }
//                }
//                jwkCache.put(tenantId, keyMap);
//                logger.info("[JWKs] Synced {} keys for tenant {}", keyMap.size(), tenantId);
//            } catch (Exception e) {
//                logger.error("[JWKs] Failed to sync keys for tenant {}: {}", tenantId, e.getMessage(), e);
//            }
//        }
//    }
//
//    public void manualSync(String tenantId) {
//        String endpoint = jwksEndpoints.get(tenantId);
//        if (endpoint == null) return;
//        try {
//            JWKSet jwkSet = JWKSet.load(new URL(endpoint));
//            Map<String, PublicKey> keyMap = new ConcurrentHashMap<>();
//            for (JWK jwk : jwkSet.getKeys()) {
//                if (jwk instanceof RSAKey) {
//                    RSAKey rsaKey = (RSAKey) jwk;
//                    RSAPublicKey publicKey = rsaKey.toRSAPublicKey();
//                    keyMap.put(jwk.getKeyID(), publicKey);
//                }
//            }
//            jwkCache.put(tenantId, keyMap);
//            logger.info("[JWKs] Manually synced {} keys for tenant {}", keyMap.size(), tenantId);
//        } catch (Exception e) {
//            logger.error("[JWKs] Manual sync failed for tenant {}: {}", tenantId, e.getMessage(), e);
//        }
//    }
//
//    public void addTenantJwksEndpoint(String tenantId, String endpoint) {
//        jwksEndpoints.put(tenantId, endpoint);
//        manualSync(tenantId);
//    }
//
//    public void removeTenant(String tenantId) {
//        jwksEndpoints.remove(tenantId);
//        jwkCache.remove(tenantId);
//    }
//}