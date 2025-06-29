package com.citi.tts.api.gateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger/OpenAPI配置
 * 提供API文档功能
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API Gateway")
                        .description("大型网关系统API文档")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Gateway Team")
                                .email("gateway@citi.com")
                                .url("https://github.com/citi/gateway"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("开发环境"),
                        new Server().url("https://gateway-dev.citi.com").description("测试环境"),
                        new Server().url("https://gateway.citi.com").description("生产环境")
                ));
    }
} 