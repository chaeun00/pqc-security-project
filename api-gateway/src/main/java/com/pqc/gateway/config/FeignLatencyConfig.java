package com.pqc.gateway.config;

import com.pqc.gateway.logging.FeignLatencyLogger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignLatencyConfig {

    @Bean
    public feign.Logger feignLogger() {
        return new FeignLatencyLogger();
    }
}
