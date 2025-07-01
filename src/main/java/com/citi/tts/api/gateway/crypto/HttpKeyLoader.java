package com.citi.tts.api.gateway.crypto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyFactory;
import java.security.Key;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;

public class HttpKeyLoader implements RemoteKeyLoader {
    private final String endpointUrl;
    private final ObjectMapper objectMapper;

    @Autowired
    public HttpKeyLoader(String endpointUrl, ObjectMapper objectMapper) {
        this.endpointUrl = endpointUrl;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Key> loadAll() {
        Map<String, Key> keyMap = new HashMap<>();
        try {
            URL url = new URL(endpointUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            try (InputStream is = conn.getInputStream()) {
                JsonNode root = objectMapper.readTree(is);
                Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    String kid = entry.getKey();
                    String keyBase64 = entry.getValue().asText();
                    byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
                    X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
                    KeyFactory kf = KeyFactory.getInstance("RSA");
                    PublicKey publicKey = kf.generatePublic(spec);
                    keyMap.put(kid, publicKey);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return keyMap;
    }
} 