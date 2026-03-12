package com.pqc.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pqc.gateway.client.CryptoEngineClient;
import com.pqc.gateway.filter.JwtAuthInterceptor;
import com.pqc.gateway.filter.JwtKeyCache;
import com.pqc.gateway.filter.RateLimitInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final CryptoEngineClient cryptoEngineClient;
    private final JwtKeyCache jwtKeyCache;
    private final ObjectMapper objectMapper;

    @Value("${rate-limit.max-requests-per-second:20}")
    private int maxRequestsPerSecond;

    public WebMvcConfig(@Lazy CryptoEngineClient cryptoEngineClient, JwtKeyCache jwtKeyCache,
                        ObjectMapper objectMapper) {
        this.cryptoEngineClient = cryptoEngineClient;
        this.jwtKeyCache = jwtKeyCache;
        this.objectMapper = objectMapper;
    }

    @Bean
    public RateLimitInterceptor rateLimitInterceptor() {
        return new RateLimitInterceptor(maxRequestsPerSecond);
    }

    @Bean
    public JwtAuthInterceptor jwtAuthInterceptor() {
        return new JwtAuthInterceptor(cryptoEngineClient, jwtKeyCache, objectMapper);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Rate Limit — 전체 엔드포인트
        registry.addInterceptor(rateLimitInterceptor())
                .addPathPatterns("/**");

        // JWT 인증 — /api/auth/**, /api/health 제외 전체
        registry.addInterceptor(jwtAuthInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/api/auth/**", "/api/health", "/actuator/**", "/error");
    }
}
