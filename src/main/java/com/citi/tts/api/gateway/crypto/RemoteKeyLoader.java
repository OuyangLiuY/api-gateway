package com.citi.tts.api.gateway.crypto;

import java.security.Key;
import java.util.Map;

public interface RemoteKeyLoader {
    Map<String, Key> loadAll();
} 