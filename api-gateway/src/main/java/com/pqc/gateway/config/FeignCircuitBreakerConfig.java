package com.pqc.gateway.config;

import org.springframework.cloud.openfeign.CircuitBreakerNameResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignCircuitBreakerConfig {

    /**
     * Feign CB 이름을 "{client}_{method}" 기본값 대신 "{client}" 단일 이름으로 통일.
     * → resilience4j.circuitbreaker.instances.crypto-engine 하나로 sign/verify 공유 관리.
     */
    @Bean
    CircuitBreakerNameResolver circuitBreakerNameResolver() {
        return (feignClientName, target, method) -> feignClientName;
    }
}
