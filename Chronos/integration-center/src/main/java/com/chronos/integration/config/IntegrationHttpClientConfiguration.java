package com.chronos.integration.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 集成中心 HTTP 客户端配置。
 *
 * <p>集成中心同时被 MVC 应用和 WebFlux 应用复用。MVC 应用不会保证自动创建
 * {@link WebClient.Builder}，因此在领域模块内提供统一构建器，避免不同启动模块
 * 分别声明同一个基础设施 Bean。</p>
 */
@Configuration
public class IntegrationHttpClientConfiguration {

    @Bean
    public WebClient.Builder integrationWebClientBuilder() {
        return WebClient.builder();
    }
}
