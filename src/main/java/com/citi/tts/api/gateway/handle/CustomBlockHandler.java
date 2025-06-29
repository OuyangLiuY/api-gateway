//
//package com.citi.tts.api.gateway.handle;
//
//import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.http.MediaType;
//import org.springframework.web.reactive.function.server.ServerResponse;
//import reactor.core.publisher.Mono;
//
//import java.util.Map;
//
//@Configuration
//public class CustomBlockHandlerConfig {
//
//    @Bean
//    public BlockRequestHandler blockRequestHandler() {
//        return (exchange, ex) -> {
//            Map<String, Object> res = Map.of("code", 429, "msg", "请求被限流/熔断");
//            return ServerResponse.status(429)
//                    .contentType(MediaType.APPLICATION_JSON)
//                    .bodyValue(res);
//        };
//    }
//}
