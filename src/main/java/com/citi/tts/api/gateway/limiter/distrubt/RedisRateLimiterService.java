//
//package com.citi.tts.api.gateway.limiter.distrubt;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
//import org.springframework.data.redis.core.script.RedisScript;
//import org.springframework.stereotype.Service;
//import reactor.core.publisher.Mono;
//
//import java.util.List;
//
//@Service
//public class RedisRateLimiterService {
//
//    @Autowired
//    private ReactiveStringRedisTemplate redisTemplate;
//
//    private final String LUA_SCRIPT =
//        "local tokens = redis.call('get', KEYS[1])\n" +
//        "if tokens and tonumber(tokens) >= tonumber(ARGV[1]) then\n" +
//        "  return redis.call('decrby', KEYS[1], ARGV[1])\n" +
//        "else\n" +
//        "  return -1\n" +
//        "end";
//
//    public Mono<Boolean> allowRequest(String priority) {
//        String key = "rate_limit:" + priority;
//        int weight = switch (priority) {
//            case "high" -> 1;
//            case "medium" -> 3;
//            case "low" -> 5;
//            default -> 10;
//        };
//
//        return redisTemplate.execute(RedisScript.of(LUA_SCRIPT, Long.class),
//                List.of(key), String.valueOf(weight))
//            .defaultIfEmpty(-1L)
//            .map(result -> result >= 0);
//    }
//}
