package com.citi.tts.api.gateway.monitor;

import com.citi.tts.api.gateway.services.ApiRoutingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
public class MonitorController {


    @Autowired
    ApiRoutingService apiRoutingService;


    @GetMapping("/api/thread/monitor")
    public Mono<String> threadMonitor() {
        ObjectMapper objectMapper = new ObjectMapper();

        Map<String, Object> res = new HashMap<>(2);
        res.put("apiStatus", apiRoutingService.getApiStatistics());
        res.put("threadStatus", apiRoutingService.getThreadPoolStatus());
        try {
            return Mono.just(objectMapper.writeValueAsString(res));
        } catch (JsonProcessingException e) {
            log.error("write json value is error ", e);
            return Mono.just("failed");
        }
    }
}
