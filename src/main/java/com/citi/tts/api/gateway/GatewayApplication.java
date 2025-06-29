package com.citi.tts.api.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GatewayApplication {

	public static final int CPU_IN = Runtime.getRuntime().availableProcessors();

	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);

		System.setProperty("reactor.netty.ioWorkerCount", String.valueOf(CPU_IN));
		System.setProperty("reactor.netty.pool.maxConnections", "100");
		System.setProperty("reactor.netty.pool.acquireTimeout", "4500");

	}

}
