package com.citi.tts.api.gateway.controller;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@RestController
public class PaymentController {

    @Autowired
    private ObjectMapper objectMapper;

    @PostMapping("api/payment/process")
    public Mono<String> process(@RequestBody String payload){
        try {
            Map<String,Object> pmap = objectMapper.readValue(payload, Map.class);
            log.debug("payload = ", pmap);
            return Mono.just("success");
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return  Mono.just("failed");
    }
}
