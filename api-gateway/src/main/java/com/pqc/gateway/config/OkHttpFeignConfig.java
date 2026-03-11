package com.pqc.gateway.config;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Feign OkHttpClient 커넥션 풀 설정.
 *
 * 부하테스트 시나리오별 비교를 위해 풀 파라미터를 환경변수로 주입:
 *   시나리오 A (기본): OKHTTP_MAX_IDLE=5,  OKHTTP_KEEP_ALIVE_SEC=30
 *   시나리오 B (확장): OKHTTP_MAX_IDLE=20, OKHTTP_KEEP_ALIVE_SEC=60
 *   시나리오 C (최소): OKHTTP_MAX_IDLE=1,  OKHTTP_KEEP_ALIVE_SEC=10
 *
 * 메트릭 확인: GET /actuator/metrics/feign.client.requests
 */
@Configuration
public class OkHttpFeignConfig {

    private static final Logger log = LoggerFactory.getLogger(OkHttpFeignConfig.class);

    @Value("${okhttp.pool.max-idle-connections:5}")
    private int maxIdleConnections;

    @Value("${okhttp.pool.keep-alive-duration-seconds:30}")
    private long keepAliveDurationSeconds;

    @Bean
    public OkHttpClient okHttpClient() {
        log.info("[OKHTTP-POOL] maxIdleConnections={} keepAliveDuration={}s",
                maxIdleConnections, keepAliveDurationSeconds);

        ConnectionPool pool = new ConnectionPool(
                maxIdleConnections,
                keepAliveDurationSeconds,
                TimeUnit.SECONDS);

        return new OkHttpClient.Builder()
                .connectionPool(pool)
                .connectTimeout(2, TimeUnit.SECONDS)   // feign connectTimeout과 동일
                .readTimeout(5, TimeUnit.SECONDS)       // feign readTimeout과 동일
                .build();
    }
}
