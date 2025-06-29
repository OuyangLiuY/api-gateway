//package com.citi.tts.api.gateway;
//
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
//import org.springframework.test.context.TestPropertySource;
//import org.springframework.test.web.reactive.server.WebTestClient;
//
//@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
//@TestPropertySource(properties = {
//    "spring.cloud.gateway.routes[0].id=test-payment",
//    "spring.cloud.gateway.routes[0].uri=http://localhost:8081",
//    "spring.cloud.gateway.routes[0].predicates[0]=Path=/api/payment/**",
//    "spring.cloud.gateway.routes[0].filters[0]=CryptoFilter"
//})
//public class CryptoFilterTest {
//
//    @Autowired
//    private WebTestClient webClient;
//
//    @Test
//    void testPaymentRouteWithCrypto() {
//        webClient.post()
//            .uri("/api/payment/process")
//            .header("Content-Type", "application/json")
//            .header("X-AES-Key-Id", "test-key")
//            .bodyValue("{\"data\":\"encrypted-payload\"}")
//            .exchange()
//            .expectStatus().isOk();
//    }
//}